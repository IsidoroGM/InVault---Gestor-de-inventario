using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Text;
using System.Threading;
using InVault.Windows;

public static class RecoveryTests {
    static int count;
    static readonly List<string> passed = new List<string>();
    static void Check(string name, bool value) { if (!value) throw new Exception("FAIL: " + name); count++; passed.Add(name); Console.WriteLine("PASS " + name); }
    static void Reject(string name, Action run) { bool rejected = false; try { run(); } catch (Exception) { rejected = true; } Check(name, rejected); }
    static string Sql(InstallConfig c, string sql) { return Engine.Sql(c, Secrets.Load(c), sql); }
    static string Snapshot(InstallConfig c) { return Sql(c, "SELECT CONCAT(password_hash,':',token_version,':',must_change_password,':',active) FROM users WHERE username='admin';"); }
    static string Inventory(InstallConfig c) { return Sql(c, "SELECT CONCAT((SELECT COUNT(*) FROM products),':',(SELECT COUNT(*) FROM batches),':',(SELECT COUNT(*) FROM stock_movements),':',(SELECT COUNT(*) FROM users));"); }
    static Dictionary<string, object> Request(string url, string token, object data) {
        var r = (HttpWebRequest)WebRequest.Create(url); r.Proxy = null; r.Timeout = 15000;
        if (token != null) r.Headers["Authorization"] = "Bearer " + token;
        if (data != null) { r.Method = "POST"; r.ContentType = "application/json"; byte[] b = Encoding.UTF8.GetBytes(Store.Json.Serialize(data)); using (var s = r.GetRequestStream()) s.Write(b, 0, b.Length); }
        using (var response = r.GetResponse()) using (var reader = new StreamReader(response.GetResponseStream())) return Store.Json.Deserialize<Dictionary<string, object>>(reader.ReadToEnd());
    }
    public static int Main(string[] args) {
        Console.OutputEncoding = new UTF8Encoding(false);
        string results = args[1];
        try {
            string path = Path.GetFullPath(args[0]);
            // This test must never select the registered/live company.
            string workspace = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", ".."));
            if (!Rules.ContainsPath(Path.Combine(workspace, "releases", "windows"), path) || !path.Contains("verification-smoke-")) throw new Exception("Select only an existing disposable smoke fixture.");
            var c = InstallConfig.Load(path);
            if (!c.CompanyId.StartsWith("prueba-") || c.AdminEmail != "admin@example.local" || c.HttpPort == 18740) throw new Exception("This is not a disposable test company.");
            Reject("short_password_rejected", () => AccountRecovery.ValidatePassword("short"));
            Reject("control_characters_rejected", () => AccountRecovery.ValidatePassword("Long-password\n123"));
            Reject("bcrypt_byte_limit_enforced", () => AccountRecovery.ValidatePassword(new string('é', 37)));
            Reject("surrounding_spaces_rejected", () => AccountRecovery.ValidatePassword(" password-long-123 "));
            string oldToken;
            using (var engine = new Engine(c, CancellationToken.None, Console.WriteLine)) {
                engine.Start();
                oldToken = (string)Request(c.Url + "/api/auth/login", null, new { username = "admin", password = "Changed-test-password-123" })["accessToken"];
            }
            const string temporary = "Recuperación-Temporal-456";
            using (var engine = new Engine(c, CancellationToken.None, Console.WriteLine)) {
                engine.StartDatabase();
                string inventory = Inventory(c);
                string before = Snapshot(c);
                Sql(c, "UPDATE users SET active=FALSE WHERE username='admin';");
                Reject("disabled_account_not_reactivated", () => AccountRecovery.ResetDatabaseAccount(c, temporary, "Synthetic test operator"));
                Check("disabled_account_unchanged", Snapshot(c) == before.Substring(0, before.Length - 1) + "0");
                Sql(c, "UPDATE users SET active=TRUE WHERE username='admin';");
                string trigger = "recovery_test_" + Guid.NewGuid().ToString("N").Substring(0, 10);
                Sql(c, "CREATE TRIGGER " + trigger + " BEFORE INSERT ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic audit failure';");
                try {
                    Reject("audit_failure_aborts_reset", () => AccountRecovery.ResetDatabaseAccount(c, temporary, "Synthetic test operator"));
                    Check("password_and_tokens_rollback", Snapshot(c) == before);
                } finally { Sql(c, "DROP TRIGGER " + trigger + ";"); }
                long version = Int64.Parse(Sql(c, "SELECT token_version FROM users WHERE username='admin';"));
                long audits = Int64.Parse(Sql(c, "SELECT COUNT(*) FROM audit_logs WHERE action='PASSWORD_RESET';"));
                Sql(c, "INSERT INTO login_rate_limits (id,attempts,window_expires_at,reported) SELECT CONCAT('account:',SHA2(CONCAT('user:',id),256)),100,9999999999999,FALSE FROM users WHERE username='admin' ON DUPLICATE KEY UPDATE attempts=100,window_expires_at=9999999999999;");
                AccountRecovery.ResetDatabaseAccount(c, temporary, "Synthetic test operator");
                Check("inventory_and_accounts_preserved", Inventory(c) == inventory);
                Check("tokens_revoked", Int64.Parse(Sql(c, "SELECT token_version FROM users WHERE username='admin';")) == version + 1);
                Check("password_change_required", Sql(c, "SELECT must_change_password FROM users WHERE username='admin';") == "1");
                Check("reset_audited_once", Int64.Parse(Sql(c, "SELECT COUNT(*) FROM audit_logs WHERE action='PASSWORD_RESET';")) == audits + 1);
                Check("account_retry_budget_reset", Sql(c, "SELECT COUNT(*) FROM login_rate_limits WHERE id=(SELECT CONCAT('account:',SHA2(CONCAT('user:',id),256)) FROM users WHERE username='admin');") == "0");
                Check("no_recovery_temp_files", Directory.GetDirectories(Path.Combine(c.DataRoot, "tmp"), "admin-recovery-*").Length == 0);
            }
            using (var engine = new Engine(c, CancellationToken.None, Console.WriteLine)) {
                engine.Start();
                var session = Request(c.Url + "/api/auth/login", null, new { username = "admin", password = temporary });
                Check("new_unicode_password_authenticates", (bool)session["mustChangePassword"]);
                bool oldPasswordRejected = false, tokenRejected = false;
                try { Request(c.Url + "/api/auth/login", null, new { username = "admin", password = "Changed-test-password-123" }); }
                catch (WebException e) { oldPasswordRejected = ((HttpWebResponse)e.Response).StatusCode == HttpStatusCode.Unauthorized; }
                Check("old_password_rejected", oldPasswordRejected);
                try { Request(c.Url + "/api/company", oldToken, null); }
                catch (WebException e) { tokenRejected = ((HttpWebResponse)e.Response).StatusCode == HttpStatusCode.Unauthorized; }
                Check("old_session_rejected", tokenRejected);
            }
            Store.WriteJson(results, new { Result = "passed", Count = count, Passed = passed });
            Console.WriteLine("RESULT " + count + " passed"); return 0;
        } catch (Exception e) { Console.Error.WriteLine(e.ToString()); Store.WriteJson(results, new { Result = "failed", Count = count, Error = e.Message, Passed = passed }); return 1; }
    }
}
