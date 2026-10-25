using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;

namespace InVault.Windows {
    public sealed class InstallConfig {
        public int Format = 1;
        public string CompanyId, CompanyName, AdminEmail, AppRoot, DataRoot, Release;
        public string Version, PackageId, MySqlVersion;
        public int HttpPort = 18740, ApiPort = 18741, DbPort = 18742;
        public string State = "pending";
        public bool TailscaleRequested;
        public string TailscaleUrl, TailscaleNodeId;
        public string ServiceName { get { return "InVault-" + CompanyId; } }
        public string Url { get { return "http://localhost:" + HttpPort; } }
        public string AllowedOrigins { get { return Url + (String.IsNullOrEmpty(TailscaleUrl) ? "" : "," + TailscaleUrl); } }
        public string ReleaseRoot { get { return Path.Combine(AppRoot, "versions", Release); } }
        public string FilePath { get { return Path.Combine(DataRoot, "installation.json"); } }
        public void Save() { Store.WriteJson(FilePath, this); }
        public static InstallConfig Load(string path) {
            var c = Store.ReadJson<InstallConfig>(path);
            c.Validate();
            if (!String.Equals(Path.GetFullPath(path), c.FilePath, StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("La configuración no pertenece a este directorio.");
            return c;
        }
        public void Validate() {
            RemoteAccess.ValidateOrigin(TailscaleUrl);
            if (!String.IsNullOrEmpty(TailscaleUrl) && String.IsNullOrWhiteSpace(TailscaleNodeId))
                throw new InvalidDataException("Falta la identidad del PC vinculado a Tailscale.");
            if (Format != 1 || !Regex.IsMatch(CompanyId ?? "", "^[a-z][a-z0-9-]{1,23}$"))
                throw new InvalidDataException("Identificador de empresa no válido.");
            Rules.Company(CompanyName);
            Rules.DirectoryPath(AppRoot); Rules.DirectoryPath(DataRoot);
            if (Rules.ContainsPath(AppRoot, DataRoot) || Rules.ContainsPath(DataRoot, AppRoot))
                throw new InvalidDataException("Programa y datos necesitan carpetas independientes.");
            if (!Regex.IsMatch(Release ?? "", "^[a-f0-9]{20}$")) throw new InvalidDataException("Versión de paquete no válida.");
            if (new[] { HttpPort, ApiPort, DbPort }.Distinct().Count() != 3 ||
                new[] { HttpPort, ApiPort, DbPort }.Any(p => p < 1024 || p > 65535))
                throw new InvalidDataException("Selecciona tres puertos diferentes entre 1024 y 65535.");
        }
    }
    public sealed class Secrets {
        public string RootPassword, DbPassword, JwtSecret, BootstrapPassword;
        public static Secrets Create(string password) {
            return new Secrets { RootPassword = Store.Random(32), DbPassword = Store.Random(32),
                JwtSecret = Convert.ToBase64String(Store.RandomBytes(32)), BootstrapPassword = password };
        }
        public void Save(InstallConfig c) {
            Store.AtomicWrite(Path.Combine(c.DataRoot, "secrets.bin"), ProtectedData.Protect(
                Encoding.UTF8.GetBytes(Store.Json.Serialize(this)), Encoding.UTF8.GetBytes(c.CompanyId), DataProtectionScope.LocalMachine));
        }
        public static Secrets Load(InstallConfig c) {
            return Store.Json.Deserialize<Secrets>(Encoding.UTF8.GetString(ProtectedData.Unprotect(
                File.ReadAllBytes(Path.Combine(c.DataRoot, "secrets.bin")), Encoding.UTF8.GetBytes(c.CompanyId), DataProtectionScope.LocalMachine)));
        }
        public string Redact(string text) {
            foreach (var secret in new[] { RootPassword, DbPassword, JwtSecret, BootstrapPassword })
                if (!String.IsNullOrEmpty(secret)) text = text.Replace(secret, "[protegido]");
            return text;
        }
    }
    public static class Store {
        public static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = 16 * 1024 * 1024 };
        public static byte[] RandomBytes(int count) { var b = new byte[count]; using (var r = RandomNumberGenerator.Create()) r.GetBytes(b); return b; }
        public static string Random(int count) { return Convert.ToBase64String(RandomBytes(count)).TrimEnd('=').Replace('+', '-').Replace('/', '_'); }
        public static string Hash(Stream s) { using (var h = SHA256.Create()) return BitConverter.ToString(h.ComputeHash(s)).Replace("-", "").ToLowerInvariant(); }
        public static string HashFile(string p) { using (var s = File.OpenRead(p)) return Hash(s); }
        public static T ReadJson<T>(string p) { return Json.Deserialize<T>(File.ReadAllText(p, Encoding.UTF8)); }
        public static void WriteJson(string p, object value) { AtomicWrite(p, Encoding.UTF8.GetBytes(Json.Serialize(value))); }
        public static void AtomicWrite(string p, byte[] b) {
            string temp = p + "." + Guid.NewGuid().ToString("N") + ".part";
            try { using (var f = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None)) { f.Write(b, 0, b.Length); f.Flush(true); }
                if (File.Exists(p)) File.Replace(temp, p, null); else File.Move(temp, p);
            } finally { if (File.Exists(temp)) File.Delete(temp); }
        }
        public static void ProtectDirectory(string path, bool program) {
            ProtectDirectory(path, program, false);
        }
        public static void ProtectRecoveryDirectory(string path) {
            ProtectDirectory(path, false, true);
        }
        static void ProtectDirectory(string path, bool program, bool recovery) {
            Directory.CreateDirectory(path);
            var acl = new DirectorySecurity(); acl.SetAccessRuleProtection(true, false);
            foreach (var sid in new[] { WellKnownSidType.BuiltinAdministratorsSid, WellKnownSidType.LocalSystemSid })
                acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(sid, null), FileSystemRights.FullControl,
                    InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            if (!recovery) acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(WellKnownSidType.LocalServiceSid, null),
                program ? FileSystemRights.ReadAndExecute : FileSystemRights.Modify,
                InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            else acl.AddAccessRule(new FileSystemAccessRule(WindowsIdentity.GetCurrent().User, FileSystemRights.FullControl,
                InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            if (program) acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(WellKnownSidType.BuiltinUsersSid, null),
                FileSystemRights.ReadAndExecute, InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            Directory.SetAccessControl(path, acl);
        }
    }
    public static class Rules {
        public static void Company(string value) {
            if (String.IsNullOrWhiteSpace(value) || value.Trim().Length > 120 || value.Any(Char.IsControl))
                throw new ArgumentException("Escribe un nombre de empresa de 1 a 120 caracteres, sin saltos de línea.");
        }
        public static string NewCompanyId(string name) {
            Company(name);
            var normalized = new string(name.Normalize(NormalizationForm.FormD).Where(c => CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark).ToArray()).ToLowerInvariant();
            string prefix = Regex.Replace(normalized, "[^a-z0-9]+", "-").Trim('-');
            if (prefix.Length == 0 || !Char.IsLetter(prefix[0])) prefix = "empresa-" + prefix;
            prefix = prefix.Substring(0, Math.Min(prefix.Length, 10)).TrimEnd('-');
            return prefix + "-" + Guid.NewGuid().ToString("N").Substring(0, 12);
        }
        public static void DirectoryPath(string path) {
            if (String.IsNullOrWhiteSpace(path) || !Regex.IsMatch(path, @"^[A-Za-z]:[\\/]") || path.StartsWith(@"\\") || path.Any(Char.IsControl) ||
                path.IndexOfAny(new[] { '"', '%', '{', '}', '`' }) >= 0)
                throw new ArgumentException("Selecciona una carpeta local absoluta; no uses rutas de red, comillas, %, llaves ni acentos graves.");
            string full = Path.GetFullPath(path).TrimEnd(Path.DirectorySeparatorChar);
            if (full.Length < 5 || full.Length > 160 || full == Path.GetPathRoot(full).TrimEnd('\\'))
                throw new ArgumentException("Selecciona una subcarpeta con una ruta de menos de 160 caracteres.");
            NoReparse(full);
        }
        public static void NoReparse(string path) {
            string full = Path.GetFullPath(path);
            for (string p = full; p != null; p = Path.GetDirectoryName(p))
                if (Directory.Exists(p) && (File.GetAttributes(p) & FileAttributes.ReparsePoint) != 0) throw new ArgumentException("No se admiten carpetas con enlaces o redirecciones.");
        }
        public static bool ContainsPath(string parent, string child) {
            string p = Path.GetFullPath(parent).TrimEnd('\\') + "\\";
            string c = Path.GetFullPath(child).TrimEnd('\\') + "\\";
            return c.StartsWith(p, StringComparison.OrdinalIgnoreCase);
        }
        public static string SafeChild(string root, string relative) {
            if (String.IsNullOrEmpty(relative) || Path.IsPathRooted(relative) || relative.Contains(":")) throw new InvalidDataException("Ruta de paquete no válida.");
            string p = Path.GetFullPath(Path.Combine(root, relative.Replace('/', Path.DirectorySeparatorChar)));
            if (!ContainsPath(root, p) || String.Equals(p.TrimEnd('\\'), root.TrimEnd('\\'), StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("El paquete intenta salir de su directorio.");
            return p;
        }
        // Windows CommandLineToArgvW quoting; never invoke cmd.exe or PowerShell.
        public static string Quote(string value) {
            var b = new StringBuilder("\""); int slashes = 0;
            foreach (char ch in value) {
                if (ch == '\\') { slashes++; continue; }
                if (ch == '"') b.Append('\\', slashes * 2 + 1); else b.Append('\\', slashes);
                slashes = 0; b.Append(ch);
            }
            b.Append('\\', slashes * 2); return b.Append('"').ToString();
        }
        public static void FreePort(int port) {
            var l = new TcpListener(IPAddress.Loopback, port);
            l.Server.ExclusiveAddressUse = true;
            try { l.Start(); } catch (SocketException) { throw new IOException("El puerto " + port + " está ocupado. Selecciona otro puerto o detén la aplicación que lo usa."); }
            finally { l.Stop(); }
        }
        public static void FreeSpace(string root, long bytes) {
            if (new DriveInfo(Path.GetPathRoot(root)).AvailableFreeSpace < bytes)
                throw new IOException("No hay espacio suficiente en " + Path.GetPathRoot(root) + ". Libera al menos " + (bytes / 1024 / 1024) + " MB.");
        }
    }
    public static class Log {
        static readonly object Gate = new object();
        public static string DirectoryPath;
        public static Secrets Secrets;
        public static void Write(string message) {
            lock (Gate) {
                if (DirectoryPath == null) return;
                Directory.CreateDirectory(DirectoryPath);
                string path = Path.Combine(DirectoryPath, "invault.log");
                if (File.Exists(path) && new FileInfo(path).Length > 5 * 1024 * 1024) {
                    if (File.Exists(path + ".1")) File.Delete(path + ".1"); File.Move(path, path + ".1");
                }
                File.AppendAllText(path, DateTime.UtcNow.ToString("o") + " " + (Secrets == null ? message : Secrets.Redact(message)) + Environment.NewLine, Encoding.UTF8);
            }
        }
    }
    public static class Commands {
        public static Process Start(string file, IEnumerable<string> args, IDictionary<string, string> env, string input, bool logOutput) {
            var pi = new ProcessStartInfo(file, String.Join(" ", args.Select(Rules.Quote))) {
                UseShellExecute = false, CreateNoWindow = true, WindowStyle = ProcessWindowStyle.Hidden,
                RedirectStandardOutput = true, RedirectStandardError = true, RedirectStandardInput = true,
                StandardOutputEncoding = Encoding.UTF8, StandardErrorEncoding = Encoding.UTF8
            };
            if (env != null) foreach (var pair in env) pi.EnvironmentVariables[pair.Key] = pair.Value;
            var p = new Process { StartInfo = pi };
            if (logOutput) {
                p.OutputDataReceived += (s, e) => { if (e.Data != null) Log.Write(e.Data); };
                p.ErrorDataReceived += (s, e) => { if (e.Data != null) Log.Write(e.Data); };
            }
            p.Start();
            if (logOutput) { p.BeginOutputReadLine(); p.BeginErrorReadLine(); }
            if (input != null) {
                byte[] bytes = new UTF8Encoding(false).GetBytes(input);
                p.StandardInput.BaseStream.Write(bytes, 0, bytes.Length);
                p.StandardInput.BaseStream.Flush();
            }
            p.StandardInput.Close();
            return p;
        }
        public static string Run(string file, IEnumerable<string> args, IDictionary<string, string> env, string input, int timeout) {
            using (var p = Start(file, args, env, input, false)) {
                var output = p.StandardOutput.ReadToEndAsync(); var error = p.StandardError.ReadToEndAsync();
                if (!p.WaitForExit(timeout)) { p.Kill(); p.WaitForExit(); throw new TimeoutException("La operación tardó demasiado. Revisa el diagnóstico y vuelve a intentarlo."); }
                var text = output.Result;
                // Native SQL and command errors can contain confidential input; do not publish them.
                if (p.ExitCode != 0) throw new IOException(Path.GetFileName(file) + " no pudo completar la operación (código " + p.ExitCode + ").");
                return text;
            }
        }
    }
    public sealed class PackageManifest {
        public int Format;
        public string Version, PackageId, BaseRevision, SourceDigest, MySqlVersion, JavaVersion, CaddyVersion, TailscaleVersion;
        public Dictionary<string, string> Files;
        public void Validate() {
            if (Format != 1 || !Regex.IsMatch(PackageId ?? "", "^[a-f0-9]{20}$") || !Regex.IsMatch(BaseRevision ?? "", "^[a-f0-9]{40}$") ||
                !Regex.IsMatch(SourceDigest ?? "", "^[a-f0-9]{64}$") || Files == null || Files.Count == 0)
                throw new InvalidDataException("El instalador no contiene una identidad de versión válida.");
            foreach (var item in Files) if (!Regex.IsMatch(item.Value, "^[a-f0-9]{64}$")) throw new InvalidDataException("Huella de paquete no válida.");
        }
        public void Extract(Stream stream, string destination, CancellationToken token, Action<string> progress) {
            Validate();
            using (var zip = new ZipArchive(stream, ZipArchiveMode.Read)) {
                var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                foreach (var e in zip.Entries) {
                    token.ThrowIfCancellationRequested();
                    if (e.FullName.EndsWith("/")) continue;
                    string expected;
                    if (!seen.Add(e.FullName) || !Files.TryGetValue(e.FullName, out expected)) throw new InvalidDataException("Archivo de paquete inesperado.");
                    string path = Rules.SafeChild(destination, e.FullName);
                    Directory.CreateDirectory(Path.GetDirectoryName(path));
                    using (var input = e.Open()) using (var output = new FileStream(path, FileMode.CreateNew, FileAccess.Write)) input.CopyTo(output);
                    if (Store.HashFile(path) != expected) throw new InvalidDataException("Un componente está dañado. Obtén de nuevo el instalador.");
                }
                if (seen.Count != Files.Count) throw new InvalidDataException("El instalador está incompleto.");
            }
            progress("Integridad de los componentes verificada.");
        }
    }
}
