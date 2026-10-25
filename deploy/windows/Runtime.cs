using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.ServiceProcess;
using System.Text;
using System.Threading;
using TimeoutException = System.TimeoutException;

namespace InVault.Windows {
    // A job closes only this supervisor's child processes, including after a crash.
    public sealed class ChildJob : IDisposable {
        IntPtr handle;
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] static extern IntPtr CreateJobObject(IntPtr attr, string name);
        [DllImport("kernel32.dll")] static extern bool SetInformationJobObject(IntPtr job, int info, IntPtr data, uint length);
        [DllImport("kernel32.dll")] static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr value);
        [StructLayout(LayoutKind.Sequential)] struct Basic {
            public long ProcessTime, JobTime; public uint Flags; public UIntPtr Min, Max; public uint Active; public UIntPtr Affinity; public uint Priority, Scheduling;
        }
        [StructLayout(LayoutKind.Sequential)] struct Io { public ulong A, B, C, D, E, F; }
        [StructLayout(LayoutKind.Sequential)] struct Extended { public Basic Basic; public Io Io; public UIntPtr A, B, C, D; }
        public ChildJob() {
            handle = CreateJobObject(IntPtr.Zero, null);
            var info = new Extended(); info.Basic.Flags = 0x2000;
            IntPtr memory = Marshal.AllocHGlobal(Marshal.SizeOf(info));
            try {
                Marshal.StructureToPtr(info, memory, false);
                if (handle == IntPtr.Zero || !SetInformationJobObject(handle, 9, memory, (uint)Marshal.SizeOf(info)))
                    throw new IOException("Windows no pudo crear el supervisor de procesos.");
            } finally { Marshal.FreeHGlobal(memory); }
        }
        public void Add(Process p) { if (!AssignProcessToJobObject(handle, p.Handle)) { p.Kill(); throw new IOException("No se pudo supervisar un proceso de InVault."); } }
        public void Dispose() { if (handle != IntPtr.Zero) { CloseHandle(handle); handle = IntPtr.Zero; } }
    }
    public sealed class Engine : IDisposable {
        readonly InstallConfig config;
        readonly Secrets secrets;
        readonly ChildJob job = new ChildJob();
        readonly List<Process> children = new List<Process>();
        readonly CancellationToken token;
        readonly Action<string> progress;
        Process database, api, gateway;
        public Engine(InstallConfig c, CancellationToken t, Action<string> p) {
            config = c; token = t; progress = p; secrets = Secrets.Load(c);
            Log.DirectoryPath = Path.Combine(c.DataRoot, "logs"); Log.Secrets = secrets;
        }
        public static string MySqlRoot(InstallConfig c) { return Path.Combine(c.ReleaseRoot, "mysql"); }
        public static string MySqlBin(InstallConfig c, string name) { return Path.Combine(MySqlRoot(c), "bin", name + ".exe"); }
        public static string Sql(InstallConfig c, Secrets s, string query) {
            return Commands.Run(MySqlBin(c, "mysql"), new[] { "--no-defaults", "--protocol=TCP", "--host=127.0.0.1", "--port=" + c.DbPort,
                "--user=root", "--connect-timeout=3", "--batch", "--skip-column-names", "--default-character-set=utf8mb4", "--database=invault_" + c.CompanyId.Replace('-', '_') },
                new Dictionary<string, string> { { "MYSQL_PWD", s.RootPassword } }, query + "\n", 15000).Trim();
        }
        public static void WriteMySqlConfig(InstallConfig c) {
            // MySQL's Windows option-file parser uses a different path encoding from
            // CreateProcessW. Keep this file ASCII and pass every filesystem path as
            // a separately quoted Unicode process argument (also used during init).
            File.WriteAllText(Path.Combine(c.DataRoot, "mysql.ini"), "[mysqld]\nbind-address=127.0.0.1\nport=" + c.DbPort +
                "\nmysqlx=OFF\nskip-log-bin\ncharacter-set-server=utf8mb4\ncollation-server=utf8mb4_0900_ai_ci\n" +
                "innodb-buffer-pool-size=128M\nmax-connections=60\nlocal-infile=OFF\nsecure-file-priv=NULL\n", Encoding.ASCII);
        }
        public void StartDatabase() {
            config.Validate(); token.ThrowIfCancellationRequested();
            foreach (var folder in new[] { "logs", "tmp", "backups" }) Directory.CreateDirectory(Path.Combine(config.DataRoot, folder));
            WriteMySqlConfig(config);
            string dbdir = Path.Combine(config.DataRoot, "database");
            if (!Directory.Exists(Path.Combine(dbdir, "mysql"))) {
                if (Directory.Exists(dbdir) && Directory.EnumerateFileSystemEntries(dbdir).Any())
                    throw new IOException("La inicialización anterior de MySQL quedó incompleta. Se han conservado sus archivos; revisa el diagnóstico antes de recuperarlos.");
                progress("Preparando la base de datos privada de la empresa…");
                Commands.Run(MySqlBin(config, "mysqld"), new[] { "--no-defaults", "--initialize-insecure", "--basedir=" + MySqlRoot(config), "--datadir=" + dbdir,
                    "--log-error=" + Path.Combine(config.DataRoot, "logs", "initialize.log") }, null, null, 180000);
            }
            token.ThrowIfCancellationRequested();
            string provision = Path.Combine(config.DataRoot, "provisioned");
            string init = Path.Combine(config.DataRoot, "tmp", "initialize.sql");
            var args = new List<string> { "--defaults-file=" + Path.Combine(config.DataRoot, "mysql.ini"), "--console", "--no-monitor",
                "--basedir=" + MySqlRoot(config), "--datadir=" + dbdir, "--log-error=" + Path.Combine(config.DataRoot, "logs", "mysql.log") };
            if (!File.Exists(provision)) {
                string db = "invault_" + config.CompanyId.Replace('-', '_');
                File.WriteAllText(init, "ALTER USER 'root'@'localhost' IDENTIFIED BY '" + secrets.RootPassword + "';\n" +
                    "CREATE DATABASE IF NOT EXISTS `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n" +
                    "CREATE USER IF NOT EXISTS 'invault'@'localhost' IDENTIFIED BY '" + secrets.DbPassword + "';\n" +
                    "GRANT ALL PRIVILEGES ON `" + db + "`.* TO 'invault'@'localhost';\n", new UTF8Encoding(false));
                args.Add("--init-file=" + init);
            }
            progress("Iniciando MySQL y comprobando credenciales…");
            database = Spawn(MySqlBin(config, "mysqld"), args, null);
            Wait(() => Sql(config, secrets, "SELECT 1;") == "1", 90, "MySQL no está disponible.");
            if (File.Exists(init)) File.Delete(init);
            if (!File.Exists(provision)) File.WriteAllText(provision, config.CompanyId);
        }
        public void Start() {
            StartDatabase();
            progress("Iniciando InVault y aplicando sus migraciones…");
            StartApi();
            Wait(() => Healthy("http://127.0.0.1:" + config.ApiPort + "/api/health/readiness"), 180, "El backend no confirmó la disponibilidad de la base de datos.");
            if (!String.IsNullOrEmpty(secrets.BootstrapPassword)) {
                // Bootstrap is never enabled again after a successful first start.
                secrets.BootstrapPassword = ""; secrets.Save(config);
                StopProcess(api); children.Remove(api); api.Dispose(); StartApi();
                Wait(() => Healthy("http://127.0.0.1:" + config.ApiPort + "/api/health/readiness"), 180, "InVault no pudo reiniciar después del alta del administrador.");
            }
            progress("Iniciando la interfaz y comprobando el acceso local…");
            string caddy = Path.Combine(config.DataRoot, "Caddyfile");
            File.WriteAllText(caddy, CaddyConfiguration(config), new UTF8Encoding(false));
            gateway = Spawn(Path.Combine(config.ReleaseRoot, "caddy", "caddy.exe"), new[] { "run", "--config", caddy, "--adapter", "caddyfile" },
                new Dictionary<string, string> { { "XDG_DATA_HOME", Path.Combine(config.DataRoot, "tmp") }, { "XDG_CONFIG_HOME", Path.Combine(config.DataRoot, "tmp") } });
            Wait(() => Healthy(config.Url + "/api/health/readiness") && PageAvailable(config.Url), 30, "La interfaz no está disponible.");
            progress("Interfaz, backend y base de datos disponibles.");
        }
        void StartApi() {
            var env = new Dictionary<string, string> {
                { "SPRING_PROFILES_ACTIVE", "prod" }, { "SERVER_ADDRESS", "127.0.0.1" }, { "SERVER_PORT", config.ApiPort.ToString() },
                { "DB_HOST", "127.0.0.1" }, { "DB_PORT", config.DbPort.ToString() }, { "DB_NAME", "invault_" + config.CompanyId.Replace('-', '_') },
                { "DB_USERNAME", "invault" }, { "DB_PASSWORD", secrets.DbPassword }, { "DB_SSL_MODE", "DISABLED" }, { "DB_ALLOW_PUBLIC_KEY_RETRIEVAL", "true" },
                { "DB_POOL_MAX_SIZE", "10" }, { "INVAULT_JWT_SECRET", secrets.JwtSecret }, { "INVAULT_JWT_ISSUER", config.Url },
                { "INVAULT_CORS_ALLOWED_ORIGINS", config.AllowedOrigins }, { "INVAULT_REALTIME_ALLOWED_ORIGINS", config.AllowedOrigins },
                { "INVAULT_COMPANY_NAME", config.CompanyName }, { "INVAULT_BOOTSTRAP_ADMIN_ENABLED", String.IsNullOrEmpty(secrets.BootstrapPassword) ? "false" : "true" },
                { "INVAULT_BOOTSTRAP_ADMIN_USERNAME", "admin" }, { "INVAULT_BOOTSTRAP_ADMIN_EMAIL", config.AdminEmail },
                { "INVAULT_BOOTSTRAP_ADMIN_PASSWORD", secrets.BootstrapPassword ?? "" }
            };
            api = Spawn(Path.Combine(config.ReleaseRoot, "java", "bin", "java.exe"), new[] { "-Xms128m", "-Xmx768m", "-Dfile.encoding=UTF-8",
                "-Djava.io.tmpdir=" + Path.Combine(config.DataRoot, "tmp"), "-jar", Path.Combine(config.ReleaseRoot, "backend.jar") }, env);
        }
        Process Spawn(string file, IEnumerable<string> args, IDictionary<string, string> env) {
            var p = Commands.Start(file, args, env, null, true); job.Add(p); children.Add(p); return p;
        }
        void Wait(Func<bool> test, int seconds, string failure) {
            var deadline = DateTime.UtcNow.AddSeconds(seconds);
            while (DateTime.UtcNow < deadline) {
                token.ThrowIfCancellationRequested();
                if (children.Any(p => p != api && p.HasExited) || (api != null && api.HasExited)) throw new IOException(failure + " Revisa los registros.");
                try { if (test()) return; } catch (WebException) {} catch (IOException) {}
                token.WaitHandle.WaitOne(500);
            }
            throw new TimeoutException(failure + " Revisa los registros y vuelve a intentarlo.");
        }
        public void Monitor() {
            while (!token.WaitHandle.WaitOne(3000)) {
                if (database.HasExited || api.HasExited || gateway.HasExited) throw new IOException("Un componente se detuvo; Windows reiniciará el servicio.");
            }
        }
        public static bool Healthy(string url) {
            try { var obj = Store.Json.Deserialize<Dictionary<string, object>>(Get(url)); return obj.ContainsKey("status") && (string)obj["status"] == "UP"; }
            catch (WebException) { return false; }
            catch (ArgumentException) { return false; }
            catch (InvalidCastException) { return false; }
        }
        public static string Get(string url) {
            var r = (HttpWebRequest)WebRequest.Create(url); r.Timeout = 4000; r.ReadWriteTimeout = 4000; r.Proxy = null;
            using (var response = r.GetResponse()) using (var stream = new StreamReader(response.GetResponseStream())) return stream.ReadToEnd();
        }
        public static bool PageAvailable(string url) { return Get(url).Contains("<app-root>"); }
        public static string CaddyConfiguration(InstallConfig c) {
            c.Validate();
            string sites = "http://localhost:" + c.HttpPort;
            if (!String.IsNullOrEmpty(c.TailscaleUrl)) sites += ", http://" + new Uri(c.TailscaleUrl).Host + ":" + c.HttpPort;
            return "{\n admin off\n auto_https off\n}\n" + sites + " {\n bind 127.0.0.1\n" +
                " root * \"" + Path.Combine(c.ReleaseRoot, "frontend").Replace('\\', '/') + "\"\n" +
                " header {\n  X-Content-Type-Options nosniff\n  X-Frame-Options DENY\n  Referrer-Policy strict-origin-when-cross-origin\n" +
                "  Content-Security-Policy \"default-src 'self'; connect-src 'self' ws: wss:; worker-src 'self'; manifest-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'\"\n }\n" +
                " @private path /.git* /.env*\n respond @private 404\n" +
                (String.IsNullOrEmpty(c.TailscaleUrl) ? "" :
                " @remoteApi {\n  host " + new Uri(c.TailscaleUrl).Host + "\n  path /api/* /ws /ws/*\n }\n handle @remoteApi {\n  reverse_proxy 127.0.0.1:" + c.ApiPort + " {\n" +
                "   header_up -Forwarded\n   header_up -X-Forwarded-Prefix\n   header_up -X-Forwarded-Ssl\n   header_up X-Forwarded-Proto https\n  }\n }\n") +
                " @api path /api/* /ws /ws/*\n handle @api {\n  reverse_proxy 127.0.0.1:" + c.ApiPort + " {\n" +
                "   header_up -Forwarded\n   header_up -X-Forwarded-Prefix\n   header_up -X-Forwarded-Ssl\n" +
                "  }\n }\n" +
                " handle {\n  header Cache-Control no-cache\n  try_files {path} /index.html\n  file_server\n }\n}\n";
        }
        static void StopProcess(Process p) { if (p != null && !p.HasExited) { p.Kill(); p.WaitForExit(15000); } }
        public void Dispose() {
            StopProcess(gateway); StopProcess(api);
            if (database != null && !database.HasExited) {
                try { Commands.Run(MySqlBin(config, "mysqladmin"), new[] { "--no-defaults", "--host=127.0.0.1", "--port=" + config.DbPort, "--user=root", "--connect-timeout=3", "shutdown" },
                    new Dictionary<string, string> { { "MYSQL_PWD", secrets.RootPassword } }, null, 30000); database.WaitForExit(30000); }
                catch (Exception) { Log.Write("MySQL no confirmó la parada ordenada; se conserva su directorio de datos."); }
            }
            job.Dispose(); foreach (var p in children) p.Dispose();
        }
    }
    public sealed class HostService : ServiceBase {
        readonly string configPath;
        CancellationTokenSource cancellation;
        Thread thread;
        public HostService(string path) { configPath = path; ServiceName = InstallConfig.Load(path).ServiceName; CanShutdown = true; }
        protected override void OnStart(string[] args) {
            cancellation = new CancellationTokenSource();
            thread = new Thread(() => {
                try { using (var engine = new Engine(InstallConfig.Load(configPath), cancellation.Token, Log.Write)) { engine.Start(); engine.Monitor(); } }
                catch (OperationCanceledException) { }
                catch (Exception e) { Log.Write("Servicio: " + e.Message); Environment.Exit(1); }
            }); thread.IsBackground = true; thread.Start();
        }
        protected override void OnStop() {
            cancellation.Cancel(); RequestAdditionalTime(180000);
            if (!thread.Join(170000)) throw new TimeoutException("InVault sigue terminando una operación de base de datos.");
        }
        protected override void OnShutdown() { OnStop(); }
    }
    public static class Backups {
        static readonly byte[] Magic = Encoding.ASCII.GetBytes("IVB1");
        public static string Create(InstallConfig c, string password) {
            if (password == null || password.Length < 16) throw new ArgumentException("La frase de recuperación debe tener al menos 16 caracteres.");
            string folder = Path.Combine(c.DataRoot, "backups"); Directory.CreateDirectory(folder);
            string id = DateTime.UtcNow.ToString("yyyyMMddTHHmmssZ") + "-" + Guid.NewGuid().ToString("N");
            string sql = Path.Combine(folder, id + ".sql.part"), zip = Path.Combine(folder, id + ".zip.part");
            string output = Path.Combine(folder, c.CompanyId + "-" + id + ".ivbackup");
            try {
                var s = Secrets.Load(c);
                Commands.Run(Engine.MySqlBin(c, "mysqldump"), new[] { "--no-defaults", "--host=127.0.0.1", "--port=" + c.DbPort, "--user=root", "--single-transaction",
                    "--routines", "--triggers", "--events", "--hex-blob", "--set-gtid-purged=OFF", "--default-character-set=utf8mb4", "--result-file=" + sql,
                    "invault_" + c.CompanyId.Replace('-', '_') }, new Dictionary<string, string> { { "MYSQL_PWD", s.RootPassword } }, null, 300000);
                if (new FileInfo(sql).Length == 0) throw new IOException("La copia SQL está vacía.");
                using (var archive = ZipFile.Open(zip, ZipArchiveMode.Create)) {
                    archive.CreateEntryFromFile(sql, "database.sql", CompressionLevel.Optimal);
                    WriteEntry(archive, "installation.json", Store.Json.Serialize(c));
                    // These secrets are protected by the portable archive encryption, not machine DPAPI.
                    WriteEntry(archive, "secrets.json", Store.Json.Serialize(s));
                    WriteEntry(archive, "manifest.json", Store.Json.Serialize(new { Format = 1, CompanyId = c.CompanyId, Version = c.Version,
                        PackageId = c.PackageId, MySqlVersion = c.MySqlVersion, SqlSha256 = Store.HashFile(sql), CreatedUtc = DateTime.UtcNow.ToString("o") }));
                }
                Encrypt(zip, output + ".part", password);
                Verify(output + ".part", password); File.Move(output + ".part", output);
                Log.Write("Copia cifrada verificada: " + Path.GetFileName(output)); return output;
            } finally {
                foreach (var p in new[] { sql, zip, output + ".part" }) if (File.Exists(p)) File.Delete(p);
            }
        }
        static void WriteEntry(ZipArchive zip, string name, string value) { using (var w = new StreamWriter(zip.CreateEntry(name).Open(), new UTF8Encoding(false))) w.Write(value); }
        static byte[] Keys(string password, byte[] salt) { using (var d = new Rfc2898DeriveBytes(password, salt, 200000, HashAlgorithmName.SHA256)) return d.GetBytes(64); }
        public static void Encrypt(string input, string output, string password) {
            byte[] salt = Store.RandomBytes(32), iv = Store.RandomBytes(16), keys = Keys(password, salt);
            using (var f = new FileStream(output, FileMode.CreateNew, FileAccess.ReadWrite)) {
                f.Write(Magic, 0, 4); f.Write(salt, 0, 32); f.Write(iv, 0, 16);
                using (var aes = Aes.Create()) { aes.Key = keys.Take(32).ToArray(); aes.IV = iv;
                    using (var crypto = new CryptoStream(f, aes.CreateEncryptor(), CryptoStreamMode.Write, true)) using (var source = File.OpenRead(input)) source.CopyTo(crypto);
                }
                f.Position = 0; byte[] tag; using (var h = new HMACSHA256(keys.Skip(32).ToArray())) tag = h.ComputeHash(f);
                f.Write(tag, 0, tag.Length); f.Flush(true);
            }
            Array.Clear(keys, 0, keys.Length);
        }
        public static void Verify(string file, string password) {
            using (var f = File.OpenRead(file)) {
                byte[] header = new byte[52]; if (f.Read(header, 0, 52) != 52 || !header.Take(4).SequenceEqual(Magic) || f.Length < 100)
                    throw new InvalidDataException("La copia no es válida.");
                byte[] keys = Keys(password, header.Skip(4).Take(32).ToArray());
                f.Position = 0;
                using (var h = new HMACSHA256(keys.Skip(32).ToArray())) {
                    byte[] block = new byte[65536]; long left = f.Length - 32;
                    while (left > 0) { int read = f.Read(block, 0, (int)Math.Min(left, block.Length)); if (read == 0) throw new EndOfStreamException(); h.TransformBlock(block, 0, read, block, 0); left -= read; }
                    h.TransformFinalBlock(new byte[0], 0, 0);
                    byte[] actual = new byte[32]; if (f.Read(actual, 0, 32) != 32) throw new InvalidDataException("Copia incompleta.");
                    int difference = 0; for (int i = 0; i < 32; i++) difference |= actual[i] ^ h.Hash[i];
                    if (difference != 0) throw new InvalidDataException("La frase de recuperación es incorrecta o la copia está dañada.");
                }
                Array.Clear(keys, 0, keys.Length);
            }
        }
        // Authenticate the complete stream before writing any plaintext. Never import into the live database.
        public static void Extract(string file, string password, string destination) {
            Verify(file, password);
            if (Directory.Exists(destination) || File.Exists(destination)) throw new IOException("Elige una carpeta nueva para la recuperación aislada.");
            Rules.DirectoryPath(destination); Store.ProtectRecoveryDirectory(destination);
            string zip = Path.Combine(destination, "archive.part");
            try {
                using (var f = File.OpenRead(file)) {
                    byte[] header = new byte[52]; f.Read(header, 0, 52);
                    byte[] keys = Keys(password, header.Skip(4).Take(32).ToArray());
                    using (var aes = Aes.Create()) { aes.Key = keys.Take(32).ToArray(); aes.IV = header.Skip(36).ToArray();
                        using (var output = File.Create(zip)) using (var crypto = new CryptoStream(output, aes.CreateDecryptor(), CryptoStreamMode.Write)) {
                            byte[] block = new byte[65536]; long left = f.Length - 32 - 52;
                            while (left > 0) { int read = f.Read(block, 0, (int)Math.Min(left, block.Length)); if (read == 0) throw new EndOfStreamException(); crypto.Write(block, 0, read); left -= read; }
                        }
                    }
                    Array.Clear(keys, 0, keys.Length);
                }
                using (var archive = ZipFile.OpenRead(zip)) {
                    var allowed = new HashSet<string> { "database.sql", "installation.json", "secrets.json", "manifest.json" };
                    foreach (var entry in archive.Entries) {
                        if (!allowed.Remove(entry.FullName)) throw new InvalidDataException("Contenido de copia no válido.");
                        entry.ExtractToFile(Rules.SafeChild(destination, entry.FullName));
                    }
                    if (allowed.Count != 0) throw new InvalidDataException("Copia incompleta.");
                }
                var manifest = Store.ReadJson<Dictionary<string, object>>(Path.Combine(destination, "manifest.json"));
                if (Store.HashFile(Path.Combine(destination, "database.sql")) != (string)manifest["SqlSha256"]) throw new InvalidDataException("La copia SQL no coincide con su manifiesto.");
            } finally { if (File.Exists(zip)) File.Delete(zip); }
        }
    }
}
