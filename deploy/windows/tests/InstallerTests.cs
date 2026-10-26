using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using InVault.Windows;

public static class InstallerTests {
    static int count;
    static string root;
    static readonly List<string> passed = new List<string>();
    static void Check(string name, bool value) { if (!value) throw new Exception("FAIL: " + name); count++; passed.Add(name); Console.WriteLine("PASS " + name); }
    static void Reject(string name, Action action) { bool rejected = false; try { action(); } catch (Exception) { rejected = true; } Check(name, rejected); }
    static int Port() { var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start(); int p = ((IPEndPoint)listener.LocalEndpoint).Port; listener.Stop(); return p; }
    [STAThread] public static int Main(string[] args) {
        Console.OutputEncoding = new UTF8Encoding(false);
        if (args.Length > 0 && args[0] == "--echo") { Console.Write(Store.Json.Serialize(args.Skip(1).ToArray())); return 0; }
        root = Path.GetFullPath(args.Length > 1 ? args[1] : Path.Combine("releases", "windows", "tests-" + Guid.NewGuid().ToString("N")));
        Directory.CreateDirectory(root);
        try {
            if (args.Length > 0 && args[0] == "--smoke") Smoke(args[2]);
            else if (args.Length > 0 && args[0] == "--render") Render();
            else Unit();
            Store.WriteJson(Path.Combine(root, "results.json"), new { Count = count, Passed = passed, Result = "passed", Utc = DateTime.UtcNow.ToString("o") });
            Console.WriteLine("RESULT " + count + " passed"); return 0;
        } catch (Exception e) {
            Console.Error.WriteLine(e.ToString());
            Store.WriteJson(Path.Combine(root, "results.json"), new { Count = count, Passed = passed, Result = "failed", Error = e.Message }); return 1;
        }
    }
    static void Unit() {
        Check("native_runtime_targets_net48", AppDomain.CurrentDomain.SetupInformation.TargetFrameworkName == ".NETFramework,Version=v4.8");
        Check("https_uses_system_tls", ServicePointManager.SecurityProtocol == SecurityProtocolType.SystemDefault);
        Rules.Company("Almacén José & Hijos"); Check("company_unicode", true);
        Reject("company_empty", () => Rules.Company(" ")); Reject("company_control", () => Rules.Company("Empresa\nInjected"));
        string a = Rules.NewCompanyId("Almacén José & Hijos"), b = Rules.NewCompanyId("Almacén José & Hijos");
        Check("independent_company_ids", a != b && a.Length <= 24 && System.Text.RegularExpressions.Regex.IsMatch(a, "^[a-z][a-z0-9-]+$"));
        Check("non_latin_company", Rules.NewCompanyId("会社 123").StartsWith("empresa"));
        Reject("path_unc", () => Rules.DirectoryPath(@"\\server\share\InVault"));
        Reject("path_root", () => Rules.DirectoryPath(@"C:\"));
        Reject("path_relative", () => Rules.DirectoryPath(@"relative\program"));
        Reject("path_drive_relative", () => Rules.DirectoryPath(@"C:relative"));
        Reject("zip_traversal", () => Rules.SafeChild(root, "../outside"));
        Reject("zip_absolute", () => Rules.SafeChild(root, @"C:\outside"));
        Reject("zip_stream", () => Rules.SafeChild(root, "file:stream"));
        Check("sibling_is_not_child", !Rules.ContainsPath(root, root + "-other"));
        var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start();
        try { Reject("occupied_port", () => Rules.FreePort(((IPEndPoint)listener.LocalEndpoint).Port)); } finally { listener.Stop(); }
        var special = new[] { "spaces and tildes á", @"C:\Final space\", "quote\"inside", @"backslash\before\" + "\"", "$(never-execute); & | < >", "" };
        string echo = Commands.Run(Assembly.GetExecutingAssembly().Location, new[] { "--echo" }.Concat(special), null, null, 10000);
        Check("native_argument_roundtrip", Store.Json.Deserialize<string[]>(echo).SequenceEqual(special));
        var c = new InstallConfig { CompanyId = a, CompanyName = "Almacén José", AppRoot = Path.Combine(root, "Programa José"), DataRoot = Path.Combine(root, "Datos empresa"),
            Release = "0123456789abcdef0123", Version = "0.1.0-rc.2", PackageId = "0123456789abcdef0123", MySqlVersion = "8.4.13", AdminEmail = "admin@example.local" };
        Directory.CreateDirectory(c.DataRoot); c.Validate(); c.Save();
        Check("unicode_config_roundtrip", InstallConfig.Load(c.FilePath).CompanyName == c.CompanyName);
        var s = Secrets.Create("Temporary-test-123"); s.Save(c);
        Check("dpapi_roundtrip", Secrets.Load(c).DbPassword == s.DbPassword);
        Check("dpapi_at_rest", !Encoding.UTF8.GetString(File.ReadAllBytes(Path.Combine(c.DataRoot, "secrets.bin"))).Contains(s.DbPassword));
        Check("redaction", !s.Redact(s.RootPassword + s.DbPassword + s.JwtSecret + s.BootstrapPassword).Contains(s.DbPassword));
        Check("version_release_after_rc", Installation.CompareVersions("0.1.0", "0.1.0-rc.2") > 0);
        Check("version_rc_numeric", Installation.CompareVersions("0.1.0-rc.10", "0.1.0-rc.2") > 0);
        Check("downgrade_detected", Installation.CompareVersions("0.1.0-rc.1", "0.1.0-rc.2") < 0);
        Reject("ambiguous_version", () => Installation.CompareVersions("dev", "0.1.0"));
        string plain = Path.Combine(root, "plain.txt"), encrypted = Path.Combine(root, "encrypted.ivbackup");
        File.WriteAllText(plain, "Inventario privado José"); Backups.Encrypt(plain, encrypted, "Recovery-test-passphrase-123");
        Backups.Verify(encrypted, "Recovery-test-passphrase-123"); Check("encrypted_backup_authenticated", true);
        Reject("wrong_backup_phrase", () => Backups.Verify(encrypted, "Wrong-passphrase-123"));
        byte[] corrupt = File.ReadAllBytes(encrypted); corrupt[60] ^= 1; File.WriteAllBytes(encrypted, corrupt);
        Reject("backup_corruption", () => Backups.Verify(encrypted, "Recovery-test-passphrase-123"));
        Reject("backup_auth_before_extraction", () => Backups.Extract(encrypted, "Recovery-test-passphrase-123", Path.Combine(root, "should-not-exist")));
        Check("no_unauthenticated_plaintext", !Directory.Exists(Path.Combine(root, "should-not-exist")));
        var manifest = new PackageManifest { Format = 1, Version = "0.1.0-rc.2", PackageId = "0123456789abcdef0123", BaseRevision = new string('a', 40), SourceDigest = new string('b', 64),
            Files = new Dictionary<string, string> { { "safe.txt", Store.HashFile(plain) } } };
        byte[] zipBytes;
        using (var memory = new MemoryStream()) {
            using (var zip = new ZipArchive(memory, ZipArchiveMode.Create, true)) zip.CreateEntryFromFile(plain, "safe.txt"); zipBytes = memory.ToArray();
        }
        using (var memory = new MemoryStream(zipBytes)) manifest.Extract(memory, Path.Combine(root, "extract"), CancellationToken.None, x => { });
        Check("verified_payload_extraction", File.ReadAllText(Path.Combine(root, "extract", "safe.txt")) == File.ReadAllText(plain));
        var cancel = new CancellationTokenSource(); cancel.Cancel();
        Reject("cancel_before_extraction", () => { using (var memory = new MemoryStream(zipBytes)) manifest.Extract(memory, Path.Combine(root, "cancelled"), cancel.Token, x => { }); });
        Check("cancel_has_no_files", !Directory.Exists(Path.Combine(root, "cancelled")));
        manifest.Files["safe.txt"] = new string('0', 64);
        Reject("payload_integrity", () => { using (var memory = new MemoryStream(zipBytes)) manifest.Extract(memory, Path.Combine(root, "tampered"), CancellationToken.None, x => { }); });
        Check("gateway_loopback", Engine.CaddyConfiguration(c).Contains("bind 127.0.0.1"));
        Check("gateway_websocket", Engine.CaddyConfiguration(c).Contains("/ws"));
        RemoteTests(c);
        MsiCommandTests();
        // Exercise the actual uninstall cleanup policy with disposable files, no registry or services.
        Directory.CreateDirectory(c.ReleaseRoot);
        string installedFile = Path.Combine(c.ReleaseRoot, "installed.txt"); File.WriteAllText(installedFile, "owned component");
        string changedFile = Path.Combine(c.ReleaseRoot, "changed.txt"); File.WriteAllText(changedFile, "original");
        var cleanupManifest = new PackageManifest { Format = 1, Version = c.Version, PackageId = c.PackageId, BaseRevision = new string('a', 40), SourceDigest = new string('b', 64),
            Files = new Dictionary<string, string> { { "installed.txt", Store.HashFile(installedFile) }, { "changed.txt", Store.HashFile(changedFile) } } };
        Store.WriteJson(Path.Combine(c.ReleaseRoot, "package.json"), cleanupManifest);
        File.WriteAllText(changedFile, "user modification");
        File.WriteAllText(Path.Combine(c.AppRoot, ".invault-owned"), c.CompanyId);
        string external = Path.Combine(root, "external-sentinel.txt"); File.WriteAllText(external, "unrelated");
        c.State = "uninstalled"; Installation.Cleanup(c, -1);
        Check("uninstall_removes_owned_files", !File.Exists(installedFile));
        Check("uninstall_preserves_modified_files", File.Exists(changedFile));
        Check("uninstall_preserves_company_data", File.Exists(c.FilePath) && File.Exists(Path.Combine(c.DataRoot, "secrets.bin")));
        Check("uninstall_preserves_other_files", File.ReadAllText(external) == "unrelated");
    }
    static void MsiCommandTests() {
        string directory = Path.Combine(root, "MSI con espacios y tildes á"); Directory.CreateDirectory(directory);
        string mixed = directory + "/tailscale.msi", log = directory + "/instalación.log";
        var command = RemoteAccess.ClientInstallerStartInfo(mixed, log);
        Check("msi_native_source_directory", command.WorkingDirectory == directory && !command.WorkingDirectory.Contains('/'));
        Check("msi_native_source_path", command.Arguments.Contains(Rules.Quote(Path.GetFullPath(mixed))) && !command.Arguments.Contains("/tailscale.msi"));
        Check("msi_native_diagnostic_log", command.Arguments.Contains("/L*v " + Rules.Quote(Path.GetFullPath(log))));
        Check("msi_no_shell_or_forced_reboot", !command.UseShellExecute && command.Arguments.Contains("/norestart"));
        // Check the actual Windows process argument boundary, without running an installer in unit tests.
        command.FileName = Assembly.GetExecutingAssembly().Location; command.Arguments = "--echo " + command.Arguments;
        command.RedirectStandardOutput = true; command.StandardOutputEncoding = Encoding.UTF8;
        using (var process = Process.Start(command)) {
            string output = process.StandardOutput.ReadToEnd(); process.WaitForExit();
            string[] args = Store.Json.Deserialize<string[]>(output);
            Check("msi_argument_roundtrip", process.ExitCode == 0 && args.SequenceEqual(new[] { "/i", Path.GetFullPath(mixed), "/passive", "/norestart", "/L*v", Path.GetFullPath(log) }));
        }
        Check("msi_1619_actionable_diagnostic", RemoteAccess.ClientInstallerFailure(1619, log).Contains("no pudo abrir") && RemoteAccess.ClientInstallerFailure(1619, log).Contains(Path.GetFullPath(log)));
        Check("msi_1618_busy_diagnostic", RemoteAccess.ClientInstallerFailure(1618, log).Contains("otra instalación en curso"));
    }
    sealed class FakeTailscale {
        public string Json = "{}", Backend = "Running", NodeId = "node-test", Host = "invault.example.ts.net";
        public bool FailServe;
        public readonly List<string> Calls = new List<string>();
        public string Run(string[] args) {
            Calls.Add(String.Join(" ", args));
            if (args.SequenceEqual(new[] { "status", "--json" }))
                return Store.Json.Serialize(new { BackendState = Backend, Self = new { ID = NodeId, DNSName = Host + "." } });
            if (args.SequenceEqual(new[] { "serve", "status", "--json" })) return Json;
            if (args.SequenceEqual(new[] { "set", "--unattended=true" })) return "";
            if (args.Length == 4 && args[0] == "serve" && args[1] == "--bg" && args[2] == "--https=443") {
                if (args[3] == "off") { Json = "{}"; return ""; }
                if (FailServe) throw new IOException("simulated serve failure");
                Json = Route(Host, args[3]); return "";
            }
            throw new Exception("Unexpected native command: " + String.Join(" ", args));
        }
    }
    static string Route(string host, string target) {
        return Store.Json.Serialize(new { TCP = new Dictionary<string, object> { { "443", new { HTTPS = true } } },
            Web = new Dictionary<string, object> { { host + ":443", new { Handlers = new Dictionary<string, object> { { "/", new { Proxy = target } } } } } } });
    }
    static void RemoteTests(InstallConfig baseline) {
        var c = Store.Json.Deserialize<InstallConfig>(Store.Json.Serialize(baseline));
        c.DataRoot = Path.Combine(root, "remote-data"); Directory.CreateDirectory(c.DataRoot); c.Save();
        Check("legacy_installation_local_only", String.IsNullOrEmpty(InstallConfig.Load(c.FilePath).TailscaleUrl) && c.AllowedOrigins == c.Url);
        foreach (string url in new[] { "http://invault.example.ts.net", "https://evil.example", "https://invault.example.ts.net/path", "https://invault.example.ts.net/", "https://invault.example.ts.net?x=y", "https://invault.example.ts.net:8443", "https://user@invault.example.ts.net", "https://*.example.ts.net", "https://a.ts.net" }) {
            string bad = url; Reject("reject_remote_origin_" + url, () => RemoteAccess.ValidateOrigin(bad));
        }
        Reject("tailscale_requires_login", () => RemoteAccess.ParseNode("{\"BackendState\":\"NeedsLogin\"}"));
        var node = RemoteAccess.ParseNode("{\"BackendState\":\"Running\",\"Self\":{\"ID\":\"node-test\",\"DNSName\":\"invault.example.ts.net.\"}}");
        Check("tailscale_canonical_dns", node.Url == "https://invault.example.ts.net");
        string ownRoute = Route("invault.example.ts.net", "http://127.0.0.1:" + c.HttpPort);
        Reject("preserve_unowned_listener", () => RemoteAccess.AssertRoute(ownRoute, c, node, false));
        Reject("reject_funnel", () => RemoteAccess.AssertRoute("{\"AllowFunnel\":{\"invault.example.ts.net:443\":true}}", c, node, false));
        Reject("reject_foreground_conflict", () => RemoteAccess.AssertRoute("{\"Foreground\":{\"session\":{\"TCP\":{\"443\":{\"HTTPS\":true}}}}}", c, node, false));
        var fake = new FakeTailscale(); int restarts = 0;
        var access = new RemoteAccess(c, fake.Run, () => restarts++, url => url == node.Url);
        access.Enable();
        Check("remote_enabled_and_persisted", c.TailscaleUrl == node.Url && InstallConfig.Load(c.FilePath).TailscaleNodeId == node.Id);
        Check("remote_exact_origins", c.AllowedOrigins == c.Url + "," + node.Url);
        Check("remote_gateway_loopback", Engine.CaddyConfiguration(c).Contains("bind 127.0.0.1") && Engine.CaddyConfiguration(c).Contains("http://invault.example.ts.net:" + c.HttpPort));
        Check("remote_scheme_preserved", Engine.CaddyConfiguration(c).Contains("header_up X-Forwarded-Proto https"));
        Check("remote_unattended_mode", fake.Calls.Contains("set --unattended=true"));
        Check("remote_status_verified", access.Status().Contains("InVault responde por HTTPS"));
        access.Enable(); Check("remote_enable_idempotent", restarts == 2);
        Reject("preserve_modified_route", () => RemoteAccess.AssertRoute(Route(fake.Host, "http://127.0.0.1:9999"), c, node, true));
        fake.NodeId = "different-node"; Reject("reject_changed_tailnet_identity", () => access.Enable()); fake.NodeId = node.Id;
        string oldCompany = c.CompanyId; access.Disable(true);
        Check("remote_disabled_and_local_preserved", String.IsNullOrEmpty(c.TailscaleUrl) && c.CompanyId == oldCompany && fake.Json == "{}" && c.AllowedOrigins == c.Url);
        Check("remote_uses_targeted_removal", fake.Calls.Contains("serve --bg --https=443 off") && !fake.Calls.Any(call => call.Contains("reset") || call.Contains("funnel") || call.Contains("logout")));
        fake.FailServe = true; Reject("remote_activation_failure", () => access.Enable()); fake.FailServe = false;
        Check("remote_failure_rolls_back_origin", String.IsNullOrEmpty(InstallConfig.Load(c.FilePath).TailscaleUrl) && fake.Json == "{}");
        var unhealthy = new RemoteAccess(c, fake.Run, () => restarts++, url => false);
        Reject("remote_https_failure", () => unhealthy.Enable());
        Check("remote_https_failure_removes_new_route", String.IsNullOrEmpty(c.TailscaleUrl) && fake.Json == "{}");
        access.Enable(); fake.Backend = "NeedsLogin"; access.Disable(true);
        Check("remote_disable_while_disconnected", String.IsNullOrEmpty(c.TailscaleUrl) && !Engine.CaddyConfiguration(c).Contains(node.Url));
        fake.Backend = "Running"; fake.Json = "{}";
        var stale = Store.Json.Deserialize<InstallConfig>(Store.Json.Serialize(c)); c.CompanyName = "Updated company"; c.Save();
        Reject("remote_stale_configuration", () => new RemoteAccess(stale, fake.Run, () => { }, url => true).Enable());
        Check("remote_stale_does_not_overwrite", InstallConfig.Load(c.FilePath).CompanyName == "Updated company");
    }
    static Dictionary<string, object> Request(string url, string method, object data, string token, string host = null, string origin = null) {
        var request = (HttpWebRequest)WebRequest.Create(url); request.Method = method; request.Timeout = 15000; request.Proxy = null;
        if (host != null) request.Host = host;
        if (origin != null) request.Headers["Origin"] = origin;
        if (token != null) request.Headers["Authorization"] = "Bearer " + token;
        if (data != null) { request.ContentType = "application/json"; byte[] bytes = Encoding.UTF8.GetBytes(Store.Json.Serialize(data)); using (var stream = request.GetRequestStream()) stream.Write(bytes, 0, bytes.Length); }
        using (var response = request.GetResponse()) using (var reader = new StreamReader(response.GetResponseStream())) {
            // A same-origin HTTPS request need not emit CORS headers. Exercise CORS separately below.
            string text = reader.ReadToEnd(); return text.Length == 0 ? new Dictionary<string, object>() : Store.Json.Deserialize<Dictionary<string, object>>(text);
        }
    }
    static void Smoke(string installer) {
        var assembly = Assembly.LoadFile(Path.GetFullPath(installer)); PackageManifest m;
        using (var s = assembly.GetManifestResourceStream("package.json")) using (var reader = new StreamReader(s)) m = Store.Json.Deserialize<PackageManifest>(reader.ReadToEnd());
        var c = new InstallConfig { CompanyId = Rules.NewCompanyId("Prueba José"), CompanyName = "Almacén José & Hijos", AdminEmail = "admin@example.local",
            AppRoot = Path.Combine(root, "Programa con tildes á"), DataRoot = Path.Combine(root, "Datos empresa ñ"), Release = m.PackageId,
            Version = m.Version, MySqlVersion = m.MySqlVersion, PackageId = m.PackageId, HttpPort = Port(), ApiPort = Port(), DbPort = Port() };
        Directory.CreateDirectory(c.DataRoot); c.Save(); Secrets.Create("Temporary-test-123").Save(c);
        using (var s = assembly.GetManifestResourceStream("payload.zip")) m.Extract(s, c.ReleaseRoot, CancellationToken.None, Console.WriteLine);
        Check("embedded_payload_integrity", true);
        // Regression for error 1619: exercise the real msiexec client path parser using
        // the pinned MSI's standard administrative extraction, without installing its VPN/service.
        // This MSI's AdminExecuteSequence was checked to contain only standard extraction actions.
        string administrativeImage = Path.Combine(root, "MSI extraído á"), msiLog = Path.Combine(root, "msi-open.log");
        var msiCommand = RemoteAccess.ClientInstallerStartInfo(c.ReleaseRoot + "/prerequisites/tailscale.msi", msiLog);
        msiCommand.Arguments = "/a " + msiCommand.Arguments.Substring(3).Replace(" /passive ", " /qn ") + " TARGETDIR=" + Rules.Quote(administrativeImage);
        using (var process = Process.Start(msiCommand)) {
            if (!process.WaitForExit(90000)) throw new IOException("La extracción administrativa MSI sigue en curso; no se interrumpe Windows Installer.");
            Check("real_msiexec_opens_packaged_tailscale", process.ExitCode == 0);
        }
        Check("real_msi_administrative_image", Directory.GetFiles(administrativeImage, "tailscale.exe", SearchOption.AllDirectories).Length == 1);
        using (var engine = new Engine(c, CancellationToken.None, Console.WriteLine)) {
            engine.Start(); Check("real_mysql_readiness", Engine.Healthy(c.Url + "/api/health/readiness"));
            var session = Request(c.Url + "/api/auth/login", "POST", new { username = "admin", password = "Temporary-test-123" }, null);
            Check("mandatory_initial_password_change", (bool)session["mustChangePassword"]);
            Request(c.Url + "/api/users/me/password", "PUT", new { currentPassword = "Temporary-test-123", newPassword = "Changed-test-password-123" }, (string)session["accessToken"]);
            session = Request(c.Url + "/api/auth/login", "POST", new { username = "admin", password = "Changed-test-password-123" }, null);
            string token = (string)session["accessToken"];
            Check("changed_password_login", !(bool)session["mustChangePassword"]);
            Check("company_name_in_api", (string)Request(c.Url + "/api/company", "GET", null, token)["name"] == c.CompanyName);
            Engine.Sql(c, Secrets.Load(c), "CREATE TABLE installer_smoke (id INT PRIMARY KEY, note VARCHAR(100)); INSERT INTO installer_smoke VALUES (1,'persistent');");
            string backup = Backups.Create(c, "Recovery-test-passphrase-123"); Backups.Verify(backup, "Recovery-test-passphrase-123"); Check("real_encrypted_mysql_backup", true);
            string recovered = Path.Combine(root, "Recuperación aislada"); Backups.Extract(backup, "Recovery-test-passphrase-123", recovered);
            Check("backup_sql_restores_content", File.ReadAllText(Path.Combine(recovered, "database.sql")).Contains("persistent"));
            var recoveryConfig = Store.Json.Deserialize<InstallConfig>(Store.Json.Serialize(c));
            recoveryConfig.CompanyId = Rules.NewCompanyId("Recuperacion"); recoveryConfig.DataRoot = Path.Combine(root, "Datos recuperados"); recoveryConfig.DbPort = Port();
            Directory.CreateDirectory(recoveryConfig.DataRoot); recoveryConfig.Save(); Secrets.Create("").Save(recoveryConfig);
            using (var recovery = new Engine(recoveryConfig, CancellationToken.None, Console.WriteLine)) {
                recovery.StartDatabase();
                Engine.Sql(recoveryConfig, Secrets.Load(recoveryConfig), File.ReadAllText(Path.Combine(recovered, "database.sql")));
                Check("backup_imports_into_isolated_mysql", Engine.Sql(recoveryConfig, Secrets.Load(recoveryConfig), "SELECT note FROM installer_smoke WHERE id=1;") == "persistent");
                Check("recovery_keeps_original_company", Engine.Sql(c, Secrets.Load(c), "SELECT note FROM installer_smoke WHERE id=1;") == "persistent");
            }
            Check("bootstrap_secret_cleared", String.IsNullOrEmpty(Secrets.Load(c).BootstrapPassword));
        }
        c.TailscaleUrl = "https://invault.test-net.ts.net"; c.TailscaleNodeId = "smoke-only-node"; c.TailscaleRequested = true; c.Save();
        using (var engine = new Engine(InstallConfig.Load(c.FilePath), CancellationToken.None, Console.WriteLine)) {
            engine.Start(); Check("data_survives_restart", Engine.Sql(c, Secrets.Load(c), "SELECT note FROM installer_smoke WHERE id=1;") == "persistent");
            Check("account_survives_restart", !(bool)Request(c.Url + "/api/auth/login", "POST", new { username = "admin", password = "Changed-test-password-123" }, null)["mustChangePassword"]);
            var preflight = (HttpWebRequest)WebRequest.Create(c.Url + "/api/auth/login");
            preflight.Proxy = null; preflight.Method = "OPTIONS"; preflight.Timeout = 10000;
            preflight.Headers["Origin"] = c.TailscaleUrl; preflight.Headers["Access-Control-Request-Method"] = "POST";
            using (var response = preflight.GetResponse()) Check("remote_origin_allowed_by_cors", response.Headers["Access-Control-Allow-Origin"] == c.TailscaleUrl);
            var denied = (HttpWebRequest)WebRequest.Create(c.Url + "/api/auth/login");
            denied.Proxy = null; denied.Method = "OPTIONS"; denied.Timeout = 10000;
            denied.Headers["Origin"] = "https://unauthorized.example"; denied.Headers["Access-Control-Request-Method"] = "POST";
            bool blocked = false;
            try { using (denied.GetResponse()) { } }
            catch (WebException ex) { using (var response = ex.Response as HttpWebResponse) blocked = response != null && response.StatusCode == HttpStatusCode.Forbidden; }
            Check("foreign_origin_rejected_by_cors", blocked);
            string host = new Uri(c.TailscaleUrl).Host;
            var remoteSession = Request(c.Url + "/api/auth/login", "POST", new { username = "admin", password = "Changed-test-password-123" }, null, host, c.TailscaleUrl);
            Check("remote_login_through_real_gateway", !(bool)remoteSession["mustChangePassword"]);
            Check("remote_company_uses_same_database", (string)Request(c.Url + "/api/company", "GET", null, (string)remoteSession["accessToken"], host, c.TailscaleUrl)["name"] == c.CompanyName);
            using (var socket = new TcpClient()) {
                socket.Connect(IPAddress.Loopback, c.HttpPort); socket.ReceiveTimeout = 10000;
                var stream = socket.GetStream();
                string handshake = "GET /ws HTTP/1.1\r\nHost: " + host + "\r\nOrigin: " + c.TailscaleUrl + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + Convert.ToBase64String(Store.RandomBytes(16)) + "\r\nSec-WebSocket-Version: 13\r\n\r\n";
                byte[] bytes = Encoding.ASCII.GetBytes(handshake); stream.Write(bytes, 0, bytes.Length);
                var reader = new StreamReader(stream); string first = reader.ReadLine();
                Check("remote_websocket_upgrade", first != null && first.Contains("101"));
            }
        }
        string logs = File.ReadAllText(Path.Combine(c.DataRoot, "logs", "invault.log"));
        var secrets = Secrets.Load(c);
        Check("logs_have_no_secrets", !logs.Contains(secrets.RootPassword) && !logs.Contains(secrets.DbPassword) && !logs.Contains(secrets.JwtSecret) && !logs.Contains("Temporary-test-123"));
        Store.WriteJson(Path.Combine(root, "test-installation.json"), c);
    }
    static void Render() {
        Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
        var m = new PackageManifest { Version = "0.1.0-rc.2", PackageId = "0123456789abcdef0123" };
        using (var wizard = new Wizard(m, false)) {
            var page = typeof(Wizard).GetField("page", BindingFlags.NonPublic | BindingFlags.Instance);
            var show = typeof(Wizard).GetMethod("ShowPage", BindingFlags.NonPublic | BindingFlags.Instance);
            var field = typeof(Wizard).GetField("company", BindingFlags.NonPublic | BindingFlags.Instance);
            ((TextBox)field.GetValue(wizard)).Text = "Almacén José & Hijos";
            wizard.ShowInTaskbar = false; wizard.StartPosition = FormStartPosition.Manual;
            wizard.Location = new Point(-10000, -10000); wizard.Show(); Application.DoEvents();
            foreach (int value in new[] { 0, 1, 2, 3 }) {
                page.SetValue(wizard, value); show.Invoke(wizard, null); Application.DoEvents();
                using (var image = new Bitmap(wizard.Width, wizard.Height)) { wizard.DrawToBitmap(image, new Rectangle(0, 0, image.Width, image.Height)); image.Save(Path.Combine(root, "wizard-" + value + ".png")); }
            }
            Check("four_wizard_pages_rendered", true);
        }
        var config = new InstallConfig { CompanyName = "Almacén José & Hijos" };
        using (var remote = new RemoteAccessForm(config)) {
            remote.ShowInTaskbar = false; remote.StartPosition = FormStartPosition.Manual; remote.Location = new Point(-10000, -10000);
            remote.Show(); Application.DoEvents();
            using (var image = new Bitmap(remote.Width, remote.Height)) { remote.DrawToBitmap(image, new Rectangle(0, 0, image.Width, image.Height)); image.Save(Path.Combine(root, "tablets.png")); }
            Check("tablet_setup_rendered", true);
        }
    }
}
