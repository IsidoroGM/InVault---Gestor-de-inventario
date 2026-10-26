using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Security.Principal;
using System.ServiceProcess;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Win32;
using TimeoutException = System.TimeoutException;

namespace InVault.Windows {
    public static class Installation {
        public const string RegistryPath = @"SOFTWARE\InVault\Windows";
        const string UninstallPath = @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\InVault";
        public static string ExistingPath() {
            using (var key = Registry.LocalMachine.OpenSubKey(RegistryPath)) return key == null ? null : key.GetValue("Configuration") as string;
        }
        public static InstallConfig Existing() {
            string path = ExistingPath();
            if (path == null) return null;
            if (!File.Exists(path)) throw new IOException("Windows registra una instalación anterior, pero falta su configuración. Recupera esa configuración antes de crear otra empresa.");
            return InstallConfig.Load(path);
        }
        public static bool IsAdmin() { return new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator); }
        public static void Prerequisites() {
            if (!Environment.Is64BitOperatingSystem || Environment.OSVersion.Version.Major < 10) throw new IOException("InVault requiere Windows de 64 bits, versión 10 o posterior.");
            using (var key = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full"))
                if (key == null || Convert.ToInt32(key.GetValue("Release", 0)) < 528040) throw new IOException("Instala .NET Framework 4.8 desde Microsoft y vuelve a abrir el instalador.");
        }
        public static void Stop(InstallConfig c) {
            var services = ServiceController.GetServices();
            try {
                var service = services.FirstOrDefault(s => s.ServiceName == c.ServiceName);
                if (service == null) return;
                if (service.Status != ServiceControllerStatus.Stopped) { service.Stop(); service.WaitForStatus(ServiceControllerStatus.Stopped, TimeSpan.FromSeconds(180)); }
            } finally { foreach (var s in services) s.Dispose(); }
        }
        public static void Start(InstallConfig c) {
            using (var service = new ServiceController(c.ServiceName)) {
                if (service.Status == ServiceControllerStatus.Stopped) service.Start();
                service.WaitForStatus(ServiceControllerStatus.Running, TimeSpan.FromSeconds(30));
            }
        }
        public static void Ready(InstallConfig c, CancellationToken token, Action<string> progress) {
            var until = DateTime.UtcNow.AddMinutes(9); int count = 0;
            while (DateTime.UtcNow < until) {
                token.ThrowIfCancellationRequested();
                if (Engine.Healthy(c.Url + "/api/health/readiness") && Engine.PageAvailable(c.Url)) return;
                if (++count % 15 == 0) progress("Esperando a la base de datos y a las migraciones…");
                using (var service = new ServiceController(c.ServiceName)) {
                    if (service.Status == ServiceControllerStatus.Stopped) throw new IOException("El servicio se ha detenido. Revisa los registros y pulsa Reintentar.");
                }
                token.WaitHandle.WaitOne(1000);
            }
            throw new TimeoutException("La aplicación no confirmó su disponibilidad. Revisa los registros y vuelve a intentarlo.");
        }
        static void Sc(params string[] args) { Commands.Run(Path.Combine(Environment.SystemDirectory, "sc.exe"), args, null, null, 30000); }
        public static void RegisterService(InstallConfig c) {
            string command = Rules.Quote(Path.Combine(c.AppRoot, "InVault.exe")) + " --service " + Rules.Quote(c.FilePath);
            var list = ServiceController.GetServices(); bool exists;
            try { exists = list.Any(s => s.ServiceName == c.ServiceName); } finally { foreach (var s in list) s.Dispose(); }
            if (!exists) Sc("create", c.ServiceName, "binPath=", command, "start=", "delayed-auto", "obj=", @"NT AUTHORITY\LocalService", "DisplayName=", "InVault - " + c.CompanyId);
            else {
                // Never take ownership of an unrelated service with a matching name.
                using (var k = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\" + c.ServiceName))
                    if (k == null || !String.Equals(k.GetValue("ImagePath") as string, command, StringComparison.OrdinalIgnoreCase))
                        throw new IOException("Existe un servicio con el mismo identificador y otra configuración. Se ha conservado.");
                Sc("config", c.ServiceName, "start=", "delayed-auto");
            }
            Sc("failure", c.ServiceName, "reset=", "3600", "actions=", "restart/60000/restart/60000/restart/60000");
            Sc("failureflag", c.ServiceName, "1");
        }
        public static void Record(InstallConfig c) {
            using (var key = Registry.LocalMachine.CreateSubKey(RegistryPath)) {
                key.SetValue("Configuration", c.FilePath); key.SetValue("Url", c.Url); key.SetValue("AppRoot", c.AppRoot);
            }
        }
        static void Shortcuts(InstallConfig c, bool desktop) {
            string group = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonPrograms), "InVault");
            Directory.CreateDirectory(group);
            File.WriteAllText(Path.Combine(group, "InVault.url"), "[InternetShortcut]\r\nURL=" + c.Url + "\r\n", Encoding.ASCII);
            // Shell link creation uses the Windows COM API, not a script host or a shell command.
            Type type = Type.GetTypeFromProgID("WScript.Shell"); object shell = Activator.CreateInstance(type);
            object link = type.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { Path.Combine(group, "Mantenimiento de InVault.lnk") });
            try {
                link.GetType().InvokeMember("TargetPath", BindingFlags.SetProperty, null, link, new object[] { Path.Combine(c.AppRoot, "InVault.exe") });
                link.GetType().InvokeMember("Arguments", BindingFlags.SetProperty, null, link, new object[] { "--manage" });
                link.GetType().InvokeMember("Save", BindingFlags.InvokeMethod, null, link, null);
            } finally { System.Runtime.InteropServices.Marshal.FinalReleaseComObject(link); System.Runtime.InteropServices.Marshal.FinalReleaseComObject(shell); }
            if (desktop) File.WriteAllText(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory), "InVault.url"), "[InternetShortcut]\r\nURL=" + c.Url + "\r\n", Encoding.ASCII);
            using (var key = Registry.LocalMachine.CreateSubKey(UninstallPath)) {
                key.SetValue("DisplayName", "InVault - " + c.CompanyName); key.SetValue("DisplayVersion", c.Version);
                key.SetValue("Publisher", "InVault"); key.SetValue("InstallLocation", c.AppRoot);
                key.SetValue("UninstallString", Rules.Quote(Path.Combine(c.AppRoot, "InVault.exe")) + " --uninstall");
                key.SetValue("NoModify", 1); key.SetValue("NoRepair", 1); key.SetValue("EstimatedSize", 900000);
            }
        }
        public static void Apply(InstallConfig c, InstallConfig previous, string temporaryPassword, string recoveryPhrase, bool desktop, PackageManifest manifest, Stream payload,
            CancellationToken token, Action<string> progress) {
            Prerequisites(); if (!IsAdmin()) throw new UnauthorizedAccessException("Vuelve a abrir el instalador como administrador.");
            c.Validate();
            if (previous != null && previous.CompanyId != c.CompanyId) throw new IOException("La identidad de la instalación existente no coincide.");
            if (previous != null && (previous.AppRoot != c.AppRoot || previous.DataRoot != c.DataRoot)) throw new IOException("La actualización conserva las carpetas existentes. Una reubicación requiere una recuperación verificada.");
            if (previous != null && CompareVersions(manifest.Version, previous.Version) < 0) throw new IOException("No se permite instalar una versión anterior sobre una base migrada.");
            if (previous != null && previous.MySqlVersion != manifest.MySqlVersion) throw new IOException("El cambio de versión de MySQL requiere una migración verificada por separado.");
            Rules.FreeSpace(c.AppRoot, 2L * 1024 * 1024 * 1024); Rules.FreeSpace(c.DataRoot, 3L * 1024 * 1024 * 1024);
            if (previous == null) {
                foreach (string dir in new[] { c.AppRoot, c.DataRoot })
                    if (Directory.Exists(dir) && Directory.EnumerateFileSystemEntries(dir).Any()) throw new IOException("La carpeta seleccionada contiene archivos. Elige una carpeta nueva o vacía.");
            }
            bool previousStopped = false, selectedNew = false;
            string stage = Path.Combine(c.AppRoot, "versions", ".stage-" + Guid.NewGuid().ToString("N"));
            try {
                Store.ProtectDirectory(c.AppRoot, true); Store.ProtectDirectory(c.DataRoot, false);
                Log.DirectoryPath = Path.Combine(c.DataRoot, "logs");
                if (previous == null) {
                    c.Save(); Secrets.Create(temporaryPassword).Save(c); Record(c);
                    File.WriteAllText(Path.Combine(c.AppRoot, ".invault-owned"), c.CompanyId);
                }
                else {
                    string marker = Path.Combine(c.AppRoot, ".invault-owned");
                    if (File.Exists(marker) && File.ReadAllText(marker) != c.CompanyId)
                        throw new IOException("La carpeta del programa pertenece a otra instalación. Se ha conservado.");
                    if (!File.Exists(marker)) {
                        if (previous.State != "uninstalled") throw new IOException("Falta el marcador de propiedad del programa. Recupera la instalación antes de actualizar.");
                        File.WriteAllText(marker, c.CompanyId);
                    }
                }
                token.ThrowIfCancellationRequested();
                progress("Extrayendo los componentes incluidos en el instalador…");
                Directory.CreateDirectory(stage); manifest.Extract(payload, stage, token, progress);
                Store.WriteJson(Path.Combine(stage, "package.json"), manifest);
                progress("Comprobando el requisito de Microsoft Visual C++…");
                // Microsoft-signed prerequisite, offline and restart-aware.
                string vc = Path.Combine(stage, "prerequisites", "VC_redist.x64.exe");
                var start = new ProcessStartInfo(vc, "/install /quiet /norestart") { UseShellExecute = false, CreateNoWindow = true, WindowStyle = ProcessWindowStyle.Hidden };
                using (var p = Process.Start(start)) {
                    if (!p.WaitForExit(180000)) throw new IOException("Visual C++ no terminó a tiempo. Comprueba el instalador de Windows.");
                    if (p.ExitCode == 3010 || p.ExitCode == 1641) throw new IOException("Microsoft Visual C++ requiere reiniciar Windows. Reinicia y vuelve a abrir InVaultSetup.exe; se conserva la configuración.");
                    if (p.ExitCode != 0 && p.ExitCode != 1638) throw new IOException("No se pudo instalar Visual C++. Código " + p.ExitCode + ". Revisa Windows Update y vuelve a intentarlo.");
                }
                token.ThrowIfCancellationRequested();
                // Make the verified candidate available without selecting it as the live version yet.
                string candidateRoot = Path.Combine(c.AppRoot, "versions", manifest.PackageId);
                if (!Directory.Exists(candidateRoot)) Directory.Move(stage, candidateRoot);
                else {
                    foreach (var file in manifest.Files)
                        if (Store.HashFile(Rules.SafeChild(candidateRoot, file.Key)) != file.Value) throw new IOException("Una versión existente está dañada. Conserva sus datos y revisa el diagnóstico.");
                }
                if (previous != null && previous.Release != manifest.PackageId && Directory.Exists(Path.Combine(previous.DataRoot, "database", "mysql"))) {
                    progress("Verificando la instalación anterior y creando una copia cifrada…");
                    if (previous.State == "uninstalled") {
                        // Reinstall after uninstall: start only the same MySQL version, with no application migrations before the backup.
                        var backupConfig = Store.Json.Deserialize<InstallConfig>(Store.Json.Serialize(previous));
                        backupConfig.Release = manifest.PackageId;
                        using (var database = new Engine(backupConfig, token, progress)) {
                            database.StartDatabase(); Backups.Create(backupConfig, recoveryPhrase);
                        }
                    } else {
                        Start(previous); Ready(previous, token, progress); Backups.Create(previous, recoveryPhrase);
                    }
                }
                if (previous != null) { progress("Deteniendo la versión anterior de forma controlada…"); Stop(previous); previousStopped = true; }
                foreach (int port in new[] { c.HttpPort, c.ApiPort, c.DbPort }) Rules.FreePort(port);
                token.ThrowIfCancellationRequested();
                c.Release = manifest.PackageId; c.PackageId = manifest.PackageId; c.Version = manifest.Version; c.MySqlVersion = manifest.MySqlVersion;
                string release = c.ReleaseRoot;
                if (!Directory.Exists(release)) Directory.Move(stage, release);
                else {
                    var installed = Store.ReadJson<PackageManifest>(Path.Combine(release, "package.json"));
                    if (installed.PackageId != manifest.PackageId) throw new IOException("La carpeta de versión existente no coincide.");
                    foreach (var file in manifest.Files)
                        if (Store.HashFile(Rules.SafeChild(release, file.Key)) != file.Value) throw new IOException("Una versión existente está dañada. Conserva sus datos y revisa el diagnóstico.");
                }
                c.State = "pending"; c.Save(); selectedNew = true; Record(c);
                string hostExe = Path.Combine(c.AppRoot, "InVault.exe");
                var fileDeadline = DateTime.UtcNow.AddSeconds(20);
                while (File.Exists(hostExe)) {
                    try { using (File.Open(hostExe, FileMode.Open, FileAccess.ReadWrite, FileShare.None)) { } break; }
                    catch (IOException) { if (DateTime.UtcNow >= fileDeadline) throw new IOException("Cierra las ventanas de Mantenimiento de InVault y pulsa Reintentar."); Thread.Sleep(250); }
                }
                File.Copy(Path.Combine(release, "InVault.exe"), hostExe, true);
                File.Copy(Path.Combine(release, "InVault.exe.config"), Path.Combine(c.AppRoot, "InVault.exe.config"), true);
                File.Copy(Path.Combine(release, "GUIA_INSTALACION_ES.md"), Path.Combine(c.AppRoot, "GUIA_INSTALACION_ES.md"), true);
                RegisterService(c); Start(c);
                progress("Esperando al inicio completo de InVault…"); Ready(c, token, progress);
                c.State = "ready"; c.Save(); Shortcuts(c, desktop);
                progress("Instalación completada. InVault está disponible en " + c.Url);
                if (c.TailscaleRequested) {
                    try { progress("Instalando el componente opcional Tailscale…"); RemoteAccess.InstallClient(c); }
                    catch (Exception optional) { progress("InVault está preparado. Tailscale queda pendiente: " + optional.Message); }
                    progress("Continúa en Conectar tablets para vincular la cuenta y activar el acceso privado.");
                }
            } catch {
                if (selectedNew) {
                    try { Sc("config", c.ServiceName, "start=", "disabled"); } catch (Exception) { }
                    try { Stop(c); } catch (Exception) { }
                    c.State = "failed"; c.Save();
                    // Never roll back binaries/config after Flyway might have migrated data.
                } else if (previousStopped) { try { Start(previous); } catch (Exception) { } }
                throw;
            } finally {
                // Only this attempt's staging directory, checked immediately before recursive cleanup.
                if (Directory.Exists(stage) && Rules.ContainsPath(Path.Combine(c.AppRoot, "versions"), stage) &&
                    System.Text.RegularExpressions.Regex.IsMatch(Path.GetFileName(stage), "^\\.stage-[a-f0-9]{32}$")) {
                    Rules.NoReparse(stage); Directory.Delete(stage, true);
                }
            }
        }
        public static int CompareVersions(string a, string b) {
            Func<string, int[]> parts = value => {
                var match = System.Text.RegularExpressions.Regex.Match(value ?? "", @"^(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?$");
                if (!match.Success) throw new IOException("Formato de versión no reconocido; se requiere una migración revisada.");
                return new[] { Int32.Parse(match.Groups[1].Value), Int32.Parse(match.Groups[2].Value), Int32.Parse(match.Groups[3].Value), match.Groups[4].Success ? Int32.Parse(match.Groups[4].Value) : Int32.MaxValue };
            };
            var x = parts(a); var y = parts(b); for (int i = 0; i < 4; i++) if (x[i] != y[i]) return x[i].CompareTo(y[i]); return 0;
        }
        public static void Uninstall(InstallConfig c) {
            if (!String.IsNullOrEmpty(c.TailscaleUrl)) new RemoteAccess(c).Disable(false);
            Stop(c); Sc("delete", c.ServiceName);
            c.State = "uninstalled"; c.Save();
            Registry.LocalMachine.DeleteSubKeyTree(UninstallPath, false);
            string group = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonPrograms), "InVault");
            foreach (string p in new[] { Path.Combine(group, "InVault.url"), Path.Combine(group, "Mantenimiento de InVault.lnk"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory), "InVault.url") }) if (File.Exists(p)) File.Delete(p);
            if (Directory.Exists(group) && !Directory.EnumerateFileSystemEntries(group).Any()) Directory.Delete(group);
            // Self-removal is performed by a hidden copy, without a shell or recursive root deletion.
            string helper = Path.Combine(Path.GetTempPath(), "InVault-cleanup-" + Guid.NewGuid().ToString("N") + ".exe");
            File.Copy(Assembly.GetExecutingAssembly().Location, helper);
            Process.Start(new ProcessStartInfo(helper, "--cleanup " + Rules.Quote(c.FilePath) + " " + Process.GetCurrentProcess().Id) { UseShellExecute = false, CreateNoWindow = true, WindowStyle = ProcessWindowStyle.Hidden });
        }
        public static void Cleanup(InstallConfig c, int parentPid) {
            try { using (var p = Process.GetProcessById(parentPid)) if (!p.WaitForExit(60000)) return; } catch (ArgumentException) { }
            if (c.State != "uninstalled" || File.ReadAllText(Path.Combine(c.AppRoot, ".invault-owned")) != c.CompanyId) return;
            Rules.DirectoryPath(c.AppRoot);
            string versions = Path.Combine(c.AppRoot, "versions");
            if (Directory.Exists(versions)) foreach (string version in Directory.GetDirectories(versions)) {
                if (!System.Text.RegularExpressions.Regex.IsMatch(Path.GetFileName(version), "^[a-f0-9]{20}$")) continue;
                Rules.NoReparse(version);
                string descriptor = Path.Combine(version, "package.json");
                if (!File.Exists(descriptor)) continue;
                var m = Store.ReadJson<PackageManifest>(descriptor); m.Validate();
                foreach (var pair in m.Files) {
                    string p = Rules.SafeChild(version, pair.Key); Rules.NoReparse(Path.GetDirectoryName(p));
                    if (File.Exists(p) && Store.HashFile(p) == pair.Value) File.Delete(p);
                }
                File.Delete(descriptor);
                foreach (string dir in Directory.GetDirectories(version, "*", SearchOption.AllDirectories).OrderByDescending(d => d.Length)) {
                    Rules.NoReparse(dir); if (!Directory.EnumerateFileSystemEntries(dir).Any()) Directory.Delete(dir);
                }
                if (!Directory.EnumerateFileSystemEntries(version).Any()) Directory.Delete(version);
            }
            foreach (string name in new[] { "InVault.exe", "InVault.exe.config", "GUIA_INSTALACION_ES.md", ".invault-owned" }) {
                string p = Path.Combine(c.AppRoot, name); if (File.Exists(p)) File.Delete(p);
            }
            if (Directory.Exists(versions) && !Directory.EnumerateFileSystemEntries(versions).Any()) Directory.Delete(versions);
            if (!Directory.EnumerateFileSystemEntries(c.AppRoot).Any()) Directory.Delete(c.AppRoot);
        }
    }
    public sealed class Wizard : Form {
        readonly PackageManifest manifest;
        InstallConfig previous;
        InstallConfig config;
        int page;
        bool running, done;
        CancellationTokenSource cancellation;
        readonly Panel body = new Panel();
        readonly Label heading = new Label(), description = new Label();
        readonly Button back = new Button(), next = new Button(), cancel = new Button();
        readonly TextBox company = new TextBox(), app = new TextBox(), data = new TextBox(), email = new TextBox(), password = new TextBox(), phrase = new TextBox(), phraseAgain = new TextBox();
        readonly NumericUpDown http = new NumericUpDown(), api = new NumericUpDown(), db = new NumericUpDown();
        readonly CheckBox desktop = new CheckBox(), showPassword = new CheckBox(), tailscale = new CheckBox();
        readonly TextBox output = new TextBox();
        readonly Label error = new Label();
        public Wizard(PackageManifest m, bool detectExisting = true) {
            manifest = m; Text = "Instalar InVault · " + m.Version;
            Width = 820; Height = 670; MinimumSize = new Size(820, 670); StartPosition = FormStartPosition.CenterScreen;
            Font = new Font("Segoe UI", 10); BackColor = Color.White; AutoScaleMode = AutoScaleMode.Dpi;
            heading.SetBounds(28, 24, 730, 40); heading.Font = new Font("Segoe UI", 19, FontStyle.Bold); Controls.Add(heading);
            description.SetBounds(30, 72, 730, 50); Controls.Add(description);
            body.SetBounds(30, 130, 740, 380); body.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right; Controls.Add(body);
            error.SetBounds(30, 515, 740, 55); error.ForeColor = Color.Firebrick; error.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right; Controls.Add(error);
            back.Text = "Atrás"; back.SetBounds(420, 580, 100, 34); back.Click += (s, e) => { page--; ShowPage(); };
            next.Text = "Siguiente"; next.SetBounds(530, 580, 115, 34); next.Click += async (s, e) => await Advance();
            cancel.Text = "Cancelar"; cancel.SetBounds(655, 580, 115, 34); cancel.Click += (s, e) => Close();
            foreach (var b in new[] { back, next, cancel }) { b.Anchor = AnchorStyles.Right | AnchorStyles.Bottom; Controls.Add(b); }
            AcceptButton = next;
            FormClosing += (s, e) => {
                if (running) { cancellation.Cancel(); error.Text = "Cancelando en el siguiente punto seguro. Se conservarán los datos de la empresa."; e.Cancel = true; }
            };
            company.Text = "";
            app.Text = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "InVault");
            data.Text = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "InVault");
            email.Text = ""; password.Text = ""; password.UseSystemPasswordChar = true;
            phrase.UseSystemPasswordChar = phraseAgain.UseSystemPasswordChar = true;
            showPassword.Text = "Mostrar contraseña temporal"; showPassword.CheckedChanged += (s, e) => password.UseSystemPasswordChar = !showPassword.Checked;
            desktop.Text = "Crear acceso en el escritorio"; desktop.Checked = true;
            tailscale.Text = "Preparar acceso desde tablets con Tailscale (opcional)";
            http.Minimum = api.Minimum = db.Minimum = 1024; http.Maximum = api.Maximum = db.Maximum = 65535;
            http.Value = 18740; api.Value = 18741; db.Value = 18742;
            output.Multiline = true; output.ReadOnly = true; output.ScrollBars = ScrollBars.Vertical;
            try {
                previous = detectExisting ? Installation.Existing() : null;
                if (previous != null) {
                    company.Text = previous.CompanyName; app.Text = previous.AppRoot; data.Text = previous.DataRoot;
                    email.Text = previous.AdminEmail; http.Value = previous.HttpPort; api.Value = previous.ApiPort; db.Value = previous.DbPort;
                    app.ReadOnly = data.ReadOnly = true; email.ReadOnly = true;
                    http.Enabled = api.Enabled = db.Enabled = false;
                    tailscale.Checked = previous.TailscaleRequested || !String.IsNullOrEmpty(previous.TailscaleUrl);
                }
            } catch (Exception e) { error.Text = e.Message; next.Enabled = false; }
            ShowPage();
        }
        void LabelAt(string text, int y) { var l = new Label { Text = text }; l.SetBounds(0, y, 720, 26); body.Controls.Add(l); }
        void Field(string label, Control input, int y) { LabelAt(label, y); input.SetBounds(0, y + 28, 610, 30); body.Controls.Add(input); }
        void Browse(TextBox input, int y) {
            var b = new Button { Text = "Examinar…", Enabled = previous == null }; b.SetBounds(620, y + 26, 115, 32);
            b.Click += (s, e) => { using (var picker = new FolderBrowserDialog { Description = "Selecciona la carpeta de InVault", SelectedPath = input.Text }) if (picker.ShowDialog(this) == DialogResult.OK) input.Text = picker.SelectedPath; };
            body.Controls.Add(b);
        }
        void ShowPage() {
            body.Controls.Clear(); back.Enabled = page > 0 && !running && !done;
            next.Text = done ? (config.TailscaleRequested ? "Tablets…" : "Abrir InVault") : page == 4 ? "Instalar" : "Siguiente";
            switch (page) {
                case 0:
                    heading.Text = "Bienvenido a InVault";
                    description.Text = "Instalación para Windows · " + manifest.Version + " · Paquete " + manifest.PackageId;
                    LabelAt("Este ordenador alojará la aplicación y la base de datos de tu empresa.", 12);
                    LabelAt("Incluye Java, MySQL y la interfaz. La instalación local es sin conexión.", 65);
                    LabelAt("Los servicios se iniciarán con Windows, aunque nadie haya iniciado sesión.", 118);
                    LabelAt("Puedes preparar Tailscale para conectar tablets. La vinculación necesita internet.", 171);
                    if (previous != null) LabelAt("Instalación detectada: " + previous.CompanyName + ". Se conservará su identidad y sus datos.", 240);
                    break;
                case 1:
                    heading.Text = "Comprobación de requisitos"; description.Text = "El asistente comprobará Windows, permisos y componentes antes de continuar.";
                    LabelAt("Windows 10/11 x64 y .NET Framework 4.8.", 12);
                    LabelAt("Reserva al menos 2 GB para el programa y 3 GB para los datos iniciales.", 65);
                    LabelAt("El requisito Visual C++ de Microsoft está incluido; podría solicitar un reinicio.", 118);
                    LabelAt("No necesitas instalar Java, MySQL, Docker ni herramientas de desarrollo.", 171);
                    break;
                case 2:
                    heading.Text = "Empresa y directorios"; description.Text = "El nombre visible admite espacios y tildes. Los datos se conservan al desinstalar.";
                    Field("Nombre de la empresa", company, 0);
                    Field("Archivos del programa", app, 76); Browse(app, 76);
                    Field(previous == null ? "Carpeta base de datos y configuración (se creará una subcarpeta única)" : "Carpeta de datos existente", data, 152); Browse(data, 152);
                    LabelAt("MySQL guardará sus archivos en la subcarpeta database de la carpeta de empresa.", 238);
                    desktop.SetBounds(0, 286, 600, 30); body.Controls.Add(desktop);
                    break;
                case 3:
                    heading.Text = "Configuración inicial";
                    description.Text = previous == null ? "El administrador será admin. Tendrá que cambiar su contraseña al entrar." : "Se conservarán los usuarios y contraseñas existentes.";
                    if (previous == null) {
                        Field("Correo del administrador", email, 0);
                        Field("Contraseña temporal: 12 a 72 caracteres", password, 70);
                        showPassword.SetBounds(0, 132, 600, 30); body.Controls.Add(showPassword);
                    } else if (previous.Release != manifest.PackageId) {
                        Field("Frase para cifrar la copia previa (mínimo 16 caracteres). Guárdala fuera del PC.", phrase, 0);
                        Field("Repite la frase de recuperación", phraseAgain, 70);
                        LabelAt("Sin esta frase no podrás recuperar la copia en otro equipo.", 145);
                    }
                    LabelAt("Puertos locales: interfaz / API / MySQL (solo escucha en este ordenador)", 200);
                    http.SetBounds(0, 235, 130, 30); api.SetBounds(160, 235, 130, 30); db.SetBounds(320, 235, 130, 30);
                    body.Controls.AddRange(new Control[] { http, api, db });
                    tailscale.SetBounds(0, 282, 720, 30); body.Controls.Add(tailscale);
                    LabelAt("Instala el cliente oficial; después vincula tu cuenta y autoriza las tablets.", 320);
                    break;
                case 4:
                    heading.Text = "Listo para instalar"; description.Text = "Revisa los datos antes de comenzar.";
                    LabelAt("Empresa: " + config.CompanyName, 5); LabelAt("Identificador: " + config.CompanyId, 50);
                    LabelAt("Programa: " + config.AppRoot, 95); LabelAt("Datos, configuración y registros: " + config.DataRoot, 140);
                    LabelAt("Base de datos: " + Path.Combine(config.DataRoot, "database"), 185);
                    LabelAt("Copias cifradas: " + Path.Combine(config.DataRoot, "backups"), 230);
                    LabelAt("Acceso local: " + config.Url + " · una empresa por instalación Windows", 280);
                    LabelAt(config.TailscaleRequested ? "Tablets: preparar Tailscale y completar su vinculación al terminar." : "Tablets: puedes configurar el acceso más adelante desde Mantenimiento.", 325);
                    break;
                default:
                    heading.Text = done ? "InVault está preparado" : "Instalando InVault";
                    description.Text = done ? "Puedes abrir la aplicación e iniciar sesión. Se iniciará automáticamente con Windows." : "La primera puesta en marcha puede tardar varios minutos.";
                    output.SetBounds(0, 0, 735, 360); body.Controls.Add(output); break;
            }
        }
        void ValidateInputs() {
            Rules.Company(company.Text); Rules.DirectoryPath(app.Text); Rules.DirectoryPath(data.Text);
            if (previous == null) {
                var mail = new System.Net.Mail.MailAddress(email.Text);
                if (mail.Address != email.Text || email.Text.Any(Char.IsControl) || email.Text.Length > 254) throw new ArgumentException("Escribe un correo válido.");
                if (password.Text.Length < 12 || Encoding.UTF8.GetByteCount(password.Text) > 72 || password.Text.Any(Char.IsControl) || password.Text != password.Text.Trim())
                    throw new ArgumentException("La contraseña temporal necesita 12 caracteres y un máximo de 72 bytes UTF-8, sin espacios al principio/final ni saltos de línea.");
            } else if (previous.Release != manifest.PackageId && (phrase.Text.Length < 16 || phrase.Text != phraseAgain.Text))
                throw new ArgumentException("Escribe dos frases de recuperación iguales, de al menos 16 caracteres.");
            config = previous == null ? new InstallConfig { CompanyId = Rules.NewCompanyId(company.Text), AppRoot = Path.GetFullPath(app.Text),
                AdminEmail = email.Text, Release = manifest.PackageId, Version = manifest.Version, PackageId = manifest.PackageId, MySqlVersion = manifest.MySqlVersion } :
                Store.Json.Deserialize<InstallConfig>(Store.Json.Serialize(previous));
            config.CompanyName = company.Text.Trim();
            config.DataRoot = previous == null ? Path.Combine(Path.GetFullPath(data.Text), config.CompanyId) : previous.DataRoot;
            config.HttpPort = (int)http.Value; config.ApiPort = (int)api.Value; config.DbPort = (int)db.Value; config.Validate();
            config.TailscaleRequested = tailscale.Checked || !String.IsNullOrEmpty(config.TailscaleUrl);
        }
        async Task Advance() {
            error.Text = "";
            try {
                if (done) {
                    if (config.TailscaleRequested) { using (var remote = new RemoteAccessForm(config)) remote.ShowDialog(this); }
                    else Process.Start(new ProcessStartInfo(config.Url) { UseShellExecute = true });
                    Close(); return;
                }
                if (page == 1) { Installation.Prerequisites(); if (!Installation.IsAdmin()) throw new IOException("Abre InVaultSetup.exe como administrador."); }
                if (page == 3) ValidateInputs();
                if (page < 4) { page++; ShowPage(); return; }
                page = 5; running = true; next.Enabled = false; cancellation = new CancellationTokenSource(); ShowPage();
                var reporter = new Progress<string>(line => output.AppendText(line + Environment.NewLine));
                string tempPassword = password.Text, recovery = phrase.Text; bool makeDesktop = desktop.Checked;
                await Task.Run(() => {
                    using (var payload = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip")) {
                        if (payload == null) throw new IOException("Este ejecutable es de mantenimiento. Usa InVaultSetup.exe para instalar o actualizar.");
                        Installation.Apply(config, previous, tempPassword, recovery, makeDesktop, manifest, payload, cancellation.Token,
                            line => ((IProgress<string>)reporter).Report(line));
                    }
                });
                running = false; done = true; next.Enabled = true; cancel.Text = "Cerrar"; ShowPage();
                if (previous == null) output.AppendText("\r\nUsuario inicial: admin. Usa la contraseña temporal elegida y cámbiala al entrar.\r\n");
                password.Clear(); phrase.Clear(); phraseAgain.Clear();
            } catch (Exception e) {
                running = false; next.Enabled = true; error.Text = e is OperationCanceledException ? "Instalación cancelada. Los datos se han conservado; puedes reintentar." : e.Message;
                if (page == 5) { page = 4; next.Text = "Reintentar"; back.Enabled = true; }
                // A retry resumes the recorded identity, including after a prerequisite restart.
                try { previous = Installation.Existing(); } catch (Exception) { }
                if (config != null) output.AppendText("\r\nRegistros: " + Path.Combine(config.DataRoot, "logs") + "\r\n");
            }
        }
    }
    public sealed class Maintenance : Form {
        readonly InstallConfig config;
        readonly TextBox phrase = new TextBox(), status = new TextBox();
        readonly FlowLayoutPanel actions = new FlowLayoutPanel();
        public Maintenance(InstallConfig c) {
            config = c; Text = "Mantenimiento de InVault"; Width = 760; Height = 530; Font = new Font("Segoe UI", 10); StartPosition = FormStartPosition.CenterScreen;
            var title = new Label { Text = c.CompanyName + " · " + c.Version, Font = new Font("Segoe UI", 17, FontStyle.Bold) }; title.SetBounds(25, 20, 690, 40); Controls.Add(title);
            var label = new Label { Text = "Frase para crear o extraer una copia cifrada (mínimo 16 caracteres):" }; label.SetBounds(25, 80, 690, 28); Controls.Add(label);
            phrase.SetBounds(25, 110, 690, 30); phrase.UseSystemPasswordChar = true; Controls.Add(phrase);
            actions.SetBounds(25, 160, 690, 100); Controls.Add(actions);
            Add("Abrir InVault", () => { Process.Start(new ProcessStartInfo(c.Url) { UseShellExecute = true }); return Task.FromResult(0); });
            Add("Conectar tablets…", () => { using (var remote = new RemoteAccessForm(c)) remote.ShowDialog(this); return Task.FromResult(0); });
            Add("Iniciar / comprobar", () => Task.Run(() => { Installation.Start(c); Installation.Ready(c, CancellationToken.None, Report); Report("InVault está disponible en " + c.Url); }));
            Add("Detener", () => Task.Run(() => { Installation.Stop(c); Report("Servicio detenido. Se conservan los datos."); }));
            Add("Crear copia", async () => {
                string pass = phrase.Text;
                if (pass.Length < 16) throw new ArgumentException("Escribe una frase de al menos 16 caracteres y guárdala fuera del ordenador.");
                using (var save = new SaveFileDialog { Filter = "Copia cifrada de InVault|*.ivbackup", FileName = c.CompanyId + "-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".ivbackup", OverwritePrompt = true }) {
                    if (save.ShowDialog(this) != DialogResult.OK) return;
                    string target = save.FileName;
                    await Task.Run(() => { Installation.Start(c); Installation.Ready(c, CancellationToken.None, Report);
                        string file = Backups.Create(c, pass); if (!String.Equals(file, target, StringComparison.OrdinalIgnoreCase)) File.Copy(file, target, true); Report("Copia guardada y verificada: " + target); });
                }
            });
            Add("Extraer copia", async () => {
                string pass = phrase.Text;
                using (var open = new OpenFileDialog { Filter = "Copia cifrada de InVault|*.ivbackup" }) {
                    if (open.ShowDialog(this) != DialogResult.OK) return;
                    using (var picker = new FolderBrowserDialog { Description = "Selecciona la carpeta base. Se creará una recuperación aislada." }) {
                        if (picker.ShowDialog(this) != DialogResult.OK) return;
                        string destination = Path.Combine(picker.SelectedPath, "InVault-recuperacion-" + Guid.NewGuid().ToString("N")); string file = open.FileName;
                        await Task.Run(() => { Backups.Extract(file, pass, destination); Report("Copia autenticada y extraída en " + destination + ". Consulta la guía para importar en un servidor aislado; no se ha modificado la base activa."); });
                    }
                }
            });
            Add("Ver registros", () => { Process.Start(new ProcessStartInfo("explorer.exe", Rules.Quote(Path.Combine(c.DataRoot, "logs"))) { UseShellExecute = true }); return Task.FromResult(0); });
            Add("Desinstalar", () => {
                if (MessageBox.Show(this, "Se detendrá InVault y se eliminarán el servicio y los archivos del programa. Se conservarán la base de datos, configuración y copias en:\r\n" + c.DataRoot, "Desinstalar InVault", MessageBoxButtons.OKCancel, MessageBoxIcon.Information) == DialogResult.OK) {
                    Installation.Uninstall(c); Close();
                }
                return Task.FromResult(0);
            });
            status.Multiline = true; status.ReadOnly = true; status.ScrollBars = ScrollBars.Vertical; status.SetBounds(25, 280, 690, 190); Controls.Add(status);
            Report("Datos de la empresa: " + c.DataRoot + "\r\nAcceso: " + c.Url + "\r\nLa copia incluye información confidencial. Conserva su frase por separado.");
        }
        void Report(string text) { if (InvokeRequired) { BeginInvoke(new Action<string>(Report), text); return; } status.AppendText(text + Environment.NewLine); }
        void Add(string text, Func<Task> action) {
            var button = new Button { Text = text, AutoSize = true, Height = 34 }; actions.Controls.Add(button);
            button.Click += async (s, e) => { actions.Enabled = false; try { await action(); } catch (Exception ex) { Report(ex.Message); } finally { if (!IsDisposed) actions.Enabled = true; } };
        }
    }
    public static class Program {
        [STAThread] public static int Main(string[] args) {
            try {
                if (args.Length == 2 && args[0] == "--service") { ServiceBase.Run(new HostService(args[1])); return 0; }
                if (args.Length == 3 && args[0] == "--cleanup") { Installation.Cleanup(InstallConfig.Load(args[1]), Int32.Parse(args[2])); return 0; }
                Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
                if (args.Length > 0 && (args[0] == "--manage" || args[0] == "--uninstall")) {
                    if (!Installation.IsAdmin()) {
                        Process.Start(new ProcessStartInfo(Assembly.GetExecutingAssembly().Location, args[0]) { UseShellExecute = true, Verb = "runas" }); return 0;
                    }
                    var existing = Installation.Existing(); if (existing == null) throw new IOException("No hay una instalación registrada.");
                    Application.Run(new Maintenance(existing)); return 0;
                }
                using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("package.json")) {
                    if (stream == null) { Process.Start(new ProcessStartInfo(Installation.Existing().Url) { UseShellExecute = true }); return 0; }
                    using (var reader = new StreamReader(stream)) {
                        var manifest = Store.Json.Deserialize<PackageManifest>(reader.ReadToEnd()); manifest.Validate();
                        Application.Run(new Wizard(manifest));
                    }
                }
                return 0;
            } catch (Exception e) { MessageBox.Show(e.Message, "InVault", MessageBoxButtons.OK, MessageBoxIcon.Error); return 1; }
        }
    }
}
