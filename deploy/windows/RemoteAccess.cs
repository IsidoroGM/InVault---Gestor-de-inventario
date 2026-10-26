using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace InVault.Windows {
    // Runs only from the interactive maintenance process; the application service never enrolls devices.
    public sealed class RemoteAccess {
        public static readonly string ClientPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Tailscale", "tailscale.exe");
        public const string AndroidUrl = "https://play.google.com/store/apps/details?id=com.tailscale.ipn";
        public const string HttpsSettingsUrl = "https://login.tailscale.com/admin/dns";
        readonly InstallConfig config;
        readonly Func<string[], string> run;
        readonly Action restart;
        readonly Func<string, bool> healthy;
        public RemoteAccess(InstallConfig c) : this(c,
            args => Commands.Run(ClientPath, args, null, null, 45000),
            () => { Installation.Stop(c); Installation.Start(c); Installation.Ready(c, CancellationToken.None, x => { }); },
            WaitForHttps) { }
        static bool WaitForHttps(string url) {
            var elapsed = Stopwatch.StartNew();
            do {
                try { if (Engine.Healthy(url + "/api/health/readiness") && Engine.PageAvailable(url)) return true; }
                catch (System.Net.WebException) { }
                catch (IOException) { }
                Thread.Sleep(500);
            } while (elapsed.Elapsed < TimeSpan.FromSeconds(45));
            return false;
        }
        // Injectable native boundary for tests: no network enrollment, services, or VPN changes in the suite.
        public RemoteAccess(InstallConfig c, Func<string[], string> command, Action restartApplication, Func<string, bool> check) {
            config = c; run = command; restart = restartApplication; healthy = check;
        }
        public static void ValidateOrigin(string url) {
            if (String.IsNullOrEmpty(url)) return;
            Uri uri;
            if (!Uri.TryCreate(url, UriKind.Absolute, out uri) || uri.Scheme != "https" || !uri.IsDefaultPort ||
                uri.UserInfo.Length != 0 || uri.Query.Length != 0 || uri.Fragment.Length != 0 || uri.AbsolutePath != "/" ||
                url != "https://" + uri.Host || !ValidHost(uri.Host))
                throw new InvalidDataException("La dirección privada debe ser el origen HTTPS exacto del PC en Tailscale.");
        }
        static bool ValidHost(string host) {
            if (host.Length > 253 || !host.EndsWith(".ts.net", StringComparison.Ordinal) || host.Split('.').Length < 4) return false;
            return host.Split('.').All(label => Regex.IsMatch(label, "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$"));
        }
        static Dictionary<string, object> Map(object value) { return value as Dictionary<string, object> ?? new Dictionary<string, object>(); }
        static Dictionary<string, object> Child(Dictionary<string, object> map, string key) { object value; return map != null && map.TryGetValue(key, out value) ? Map(value) : new Dictionary<string, object>(); }
        static string Text(Dictionary<string, object> map, string key) { object value; return map.TryGetValue(key, out value) ? value as string : null; }
        static bool Flag(Dictionary<string, object> map, string key) { object value; return map.TryGetValue(key, out value) && value is bool && (bool)value; }
        public sealed class Node { public string Url, Id; }
        public static Node ParseNode(string json) {
            var status = Store.Json.Deserialize<Dictionary<string, object>>(json);
            if (status == null || Text(status, "BackendState") != "Running")
                throw new IOException("Abre Tailscale, inicia sesión con la cuenta de tu empresa y conecta este PC antes de continuar.");
            var self = Child(status, "Self"); string dns = (Text(self, "DNSName") ?? "").TrimEnd('.').ToLowerInvariant();
            var node = new Node { Url = "https://" + dns, Id = Text(self, "ID") };
            ValidateOrigin(node.Url);
            if (String.IsNullOrWhiteSpace(node.Id)) throw new IOException("Tailscale no ha identificado este PC. Comprueba su conexión.");
            return node;
        }
        public string Status() {
            var node = ParseNode(run(new[] { "status", "--json" }));
            if (String.IsNullOrEmpty(config.TailscaleUrl)) return "PC conectado a Tailscale. Acceso de InVault pendiente de activar.";
            VerifyIdentity(node);
            AssertRoute(run(new[] { "serve", "status", "--json" }), config, node, true);
            return healthy(node.Url) ? "InVault responde por HTTPS. Dirección para las tablets: " + node.Url +
                "\r\nComprueba también el acceso desde una tablet autorizada." :
                "La ruta está configurada, pero InVault no responde por HTTPS. Comprueba HTTPS, permisos y el servicio de InVault.";
        }
        void VerifyIdentity(Node node) {
            if (!String.IsNullOrEmpty(config.TailscaleUrl) && (config.TailscaleUrl != node.Url || config.TailscaleNodeId != node.Id))
                throw new IOException("La cuenta, el nombre o la identidad del PC han cambiado. Desactiva el acceso anterior y vuelve a vincularlo.");
        }
        static string Target(InstallConfig c) { return "http://127.0.0.1:" + c.HttpPort; }
        // Refuse to replace another application's listener or inherit public Funnel exposure.
        public static void AssertRoute(string json, InstallConfig c, Node node, bool required) {
            var current = Store.Json.Deserialize<Dictionary<string, object>>(json) ?? new Dictionary<string, object>();
            AssertNoPublicListener(current);
            foreach (var entry in Child(current, "Foreground"))
                if (Child(Map(entry.Value), "TCP").ContainsKey("443")) throw new IOException("El puerto HTTPS está ocupado por una sesión de Tailscale. Se ha conservado.");
            var tcp = Child(current, "TCP"); var web = Child(current, "Web"); string endpoint = new Uri(node.Url).Host + ":443";
            bool exists = tcp.ContainsKey("443") || web.Keys.Any(key => key.EndsWith(":443", StringComparison.Ordinal));
            if (!exists) { if (required) throw new IOException("Falta la ruta privada de InVault. Pulsa Activar acceso para recuperarla."); return; }
            var port = Child(tcp, "443"); var handlers = Child(Child(web, endpoint), "Handlers"); var handler = Child(handlers, "/");
            bool owned = c.TailscaleUrl == node.Url && c.TailscaleNodeId == node.Id && Flag(port, "HTTPS") &&
                port.Count == 1 && handlers.Count == 1 && handler.Count == 1 && Text(handler, "Proxy") == Target(c) &&
                web.Keys.Count(key => key.EndsWith(":443", StringComparison.Ordinal)) == 1;
            if (!owned) throw new IOException("Ya hay otra configuración en el puerto HTTPS de Tailscale. Se ha conservado; elige otro equipo o revisa esa configuración.");
        }
        static void AssertNoPublicListener(Dictionary<string, object> current) {
            foreach (var entry in Child(current, "AllowFunnel"))
                if (entry.Key.EndsWith(":443", StringComparison.Ordinal) && entry.Value is bool && (bool)entry.Value)
                    throw new IOException("Hay acceso público Funnel en el puerto HTTPS. Desactívalo desde Tailscale antes de configurar el acceso privado de InVault.");
            foreach (var entry in Child(current, "Foreground")) AssertNoPublicListener(Map(entry.Value));
        }
        public string Enable() {
            return Exclusive(() => {
                var node = ParseNode(run(new[] { "status", "--json" })); VerifyIdentity(node);
                string before = run(new[] { "serve", "status", "--json" }); AssertRoute(before, config, node, false);
                string oldUrl = config.TailscaleUrl, oldId = config.TailscaleNodeId;
                bool routeCreated = !Child(Store.Json.Deserialize<Dictionary<string, object>>(before), "TCP").ContainsKey("443");
                config.TailscaleUrl = node.Url; config.TailscaleNodeId = node.Id; config.TailscaleRequested = true;
                config.Validate(); config.Save();
                try {
                    restart();
                    // No --yes: a concurrent change requiring a prompt must fail rather than overwrite it.
                    run(new[] { "serve", "--bg", "--https=443", Target(config) });
                    AssertRoute(run(new[] { "serve", "status", "--json" }), config, node, true);
                    if (!healthy(node.Url)) throw new IOException("No se ha podido comprobar InVault por HTTPS. Revisa HTTPS y los permisos de la red privada.");
                    run(new[] { "set", "--unattended=true" });
                    return "Acceso privado activado: " + node.Url + "\r\nInstala Tailscale en la tablet, autorízala en la red de tu empresa y abre esta dirección. InVault mantiene su inicio de sesión.";
                } catch (Exception failure) {
                    string cleanup = "";
                    if (routeCreated) {
                        try { RemoveOwnedRoute(node); } catch (Exception) { cleanup = " Revisa la ruta de Tailscale; la aplicación bloqueará ese origen al recuperar su configuración anterior."; }
                    }
                    config.TailscaleUrl = oldUrl; config.TailscaleNodeId = oldId; config.Save();
                    try { restart(); } catch (Exception) { cleanup += " Inicia / comprueba InVault desde Mantenimiento."; }
                    throw new IOException("No se ha completado el acceso privado. " + failure.Message + cleanup);
                }
            });
        }
        void RemoveOwnedRoute(Node node) {
            string json = run(new[] { "serve", "status", "--json" });
            AssertRoute(json, config, node, false);
            if (Child(Store.Json.Deserialize<Dictionary<string, object>>(json), "TCP").ContainsKey("443"))
                run(new[] { "serve", "--bg", "--https=443", "off" });
        }
        public string Disable(bool restartApplication) {
            return Exclusive(() => {
                if (String.IsNullOrEmpty(config.TailscaleUrl)) return "El acceso desde tablets está desactivado.";
                string warning = "";
                try {
                    var node = ParseNode(run(new[] { "status", "--json" })); VerifyIdentity(node); RemoveOwnedRoute(node);
                } catch (Exception) { warning = " No se pudo retirar la ruta de Tailscale: revísala en ese cliente. InVault retirará la dirección permitida."; }
                config.TailscaleUrl = null; config.TailscaleNodeId = null; config.TailscaleRequested = false; config.Save();
                if (restartApplication) restart();
                return "Acceso desde tablets desactivado. Se conserva el acceso local y la instalación de Tailscale." + warning;
            });
        }
        string Exclusive(Func<string> operation) {
            using (var mutex = new Mutex(false, @"Global\InVaultRemote-" + config.CompanyId)) {
                bool acquired;
                try { acquired = mutex.WaitOne(0); } catch (AbandonedMutexException) { acquired = true; }
                if (!acquired) throw new IOException("Ya hay una configuración de acceso en curso para esta empresa.");
                try {
                    if (Store.Json.Serialize(InstallConfig.Load(config.FilePath)) != Store.Json.Serialize(config))
                        throw new IOException("La instalación ha cambiado desde que abriste esta ventana. Cierra Mantenimiento y ábrelo de nuevo.");
                    return operation();
                } finally { mutex.ReleaseMutex(); }
            }
        }
        public static void InstallClient(InstallConfig c) {
            if (File.Exists(ClientPath)) return; // Keep existing accounts, settings, and newer clients.
            // Manifest paths use '/', but msiexec requires a native Windows source path.
            // File.Exists and even MsiOpenDatabase accept the mixed form that msiexec rejects with 1619.
            string relative = "prerequisites/tailscale.msi", package = Rules.SafeChild(c.ReleaseRoot, relative);
            var manifest = Store.ReadJson<PackageManifest>(Path.Combine(c.ReleaseRoot, "package.json")); manifest.Validate();
            string expected;
            if (!manifest.Files.TryGetValue(relative, out expected) || !File.Exists(package) || Store.HashFile(package) != expected)
                throw new IOException("El paquete de Tailscale falta o está dañado. Actualiza InVault con el instalador completo.");
            string logs = Path.Combine(c.DataRoot, "logs"); Directory.CreateDirectory(logs);
            string log = Path.Combine(logs, "tailscale-install-" + Guid.NewGuid().ToString("N") + ".log");
            using (var process = Process.Start(ClientInstallerStartInfo(package, log))) {
                // Never kill Windows Installer: allow its own rollback/completion if the wait expires.
                if (!process.WaitForExit(300000)) throw new IOException("Windows Installer sigue trabajando. Espera a que termine antes de volver a intentar la instalación.");
                if (process.ExitCode == 3010 || process.ExitCode == 1641) throw new IOException("Tailscale requiere reiniciar Windows. InVault seguirá disponible en este PC.");
                if (process.ExitCode != 0) throw new IOException(ClientInstallerFailure(process.ExitCode, log));
            }
            if (!File.Exists(ClientPath)) throw new IOException("No se ha encontrado el cliente instalado. Comprueba la instalación de Tailscale.");
        }
        public static ProcessStartInfo ClientInstallerStartInfo(string package, string log) {
            // Normalize at the native boundary too, including paths with spaces or accents.
            package = Path.GetFullPath(package); log = Path.GetFullPath(log);
            return new ProcessStartInfo(Path.Combine(Environment.SystemDirectory, "msiexec.exe"),
                "/i " + Rules.Quote(package) + " /passive /norestart /L*v " + Rules.Quote(log)) {
                UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = Path.GetDirectoryName(package)
            };
        }
        public static string ClientInstallerFailure(int code, string log) {
            string reason = code == 1619 ? "Windows Installer no pudo abrir el paquete de Tailscale (código 1619)." :
                code == 1618 ? "Hay otra instalación en curso (código 1618). Espera a que termine antes de reintentar." :
                "Tailscale no se instaló (código " + code + ").";
            return reason + " Puedes reintentarlo desde Mantenimiento. Registro: " + Path.GetFullPath(log);
        }
    }

    public sealed class RemoteAccessForm : Form {
        readonly InstallConfig config;
        readonly TextBox output = new TextBox(), address = new TextBox();
        readonly FlowLayoutPanel buttons = new FlowLayoutPanel();
        bool busy;
        public RemoteAccessForm(InstallConfig c) {
            config = c; Text = "Conectar tablets · InVault"; ClientSize = new Size(760, 580);
            Font = new Font("Segoe UI", 10); StartPosition = FormStartPosition.CenterParent; FormBorderStyle = FormBorderStyle.FixedDialog; MaximizeBox = false;
            var title = new Label { Text = "Acceso privado a " + c.CompanyName, Font = new Font("Segoe UI", 16, FontStyle.Bold) };
            title.SetBounds(24, 18, 710, 38); Controls.Add(title);
            var steps = new Label { Text = "1. Instala Tailscale y abre su icono junto al reloj. Inicia sesión con la cuenta de tu empresa.\r\n" +
                "2. En su panel DNS, activa MagicDNS y HTTPS. Revisa los dispositivos autorizados.\r\n" +
                "3. Activa el acceso. Se permitirá InVault a tu red privada y Tailscale seguirá conectado\r\n" +
                "    al cerrar sesión en Windows. Cada tablet necesita Tailscale y un usuario de InVault." };
            steps.SetBounds(24, 66, 710, 105); Controls.Add(steps);
            buttons.SetBounds(24, 184, 710, 105); Controls.Add(buttons);
            Add("1 · Instalar Tailscale", () => Task.Run(() => { RemoteAccess.InstallClient(c); return "Tailscale está instalado. Abre su icono junto al reloj e inicia sesión."; }));
            Add("Abrir Tailscale", () => {
                string gui = Path.Combine(Path.GetDirectoryName(RemoteAccess.ClientPath), "tailscale-ipn.exe");
                if (!File.Exists(gui)) throw new IOException("Instala Tailscale antes de abrirlo.");
                Process.Start(new ProcessStartInfo(gui) { UseShellExecute = true });
                return Task.FromResult("Completa el inicio de sesión desde Tailscale. Después vuelve a esta ventana.");
            });
            Add("2 · Configurar HTTPS", () => { Open(RemoteAccess.HttpsSettingsUrl); return Task.FromResult("Activa MagicDNS y HTTPS en el panel de tu empresa. El proveedor mostrará los avisos de sus certificados."); });
            Add("3 · Activar acceso", () => Task.Run(() => new RemoteAccess(c).Enable()));
            Add("Comprobar conexión", () => Task.Run(() => new RemoteAccess(c).Status()));
            Add("Desactivar acceso", () => Task.Run(() => new RemoteAccess(c).Disable(true)));
            var label = new Label { Text = "Dirección para las tablets autorizadas:" }; label.SetBounds(24, 303, 700, 26); Controls.Add(label);
            address.ReadOnly = true; address.SetBounds(24, 333, 585, 30); Controls.Add(address);
            var copy = new Button { Text = "Copiar", Width = 112, Height = 30 }; copy.SetBounds(621, 332, 112, 32);
            copy.Click += (s, e) => { if (!String.IsNullOrEmpty(config.TailscaleUrl)) Clipboard.SetText(config.TailscaleUrl); }; Controls.Add(copy);
            output.Multiline = true; output.ReadOnly = true; output.ScrollBars = ScrollBars.Vertical; output.SetBounds(24, 382, 710, 129); Controls.Add(output);
            var android = new LinkLabel { Text = "Instalar Tailscale en Android" }; android.SetBounds(24, 532, 280, 25); android.LinkClicked += (s, e) => Open(RemoteAccess.AndroidUrl); Controls.Add(android);
            var close = new Button { Text = "Cerrar" }; close.SetBounds(621, 529, 112, 32); close.Click += (s, e) => Close(); Controls.Add(close);
            FormClosing += (s, e) => { if (busy) e.Cancel = true; };
            address.Text = config.TailscaleUrl ?? "";
            output.Text = "La conexión utiliza la red privada de Tailscale. Los permisos de esa red determinan qué dispositivos pueden llegar a InVault.\r\n" +
                "Al cambiar de cuenta o renombrar el PC, revisa la dirección e instala de nuevo el acceso en la tablet.";
        }
        static void Open(string url) { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); }
        void Add(string text, Func<Task<string>> action) {
            var button = new Button { Text = text, AutoSize = true, Height = 34 }; buttons.Controls.Add(button);
            button.Click += async (s, e) => {
                busy = true; buttons.Enabled = false; output.Text = "Procesando… Espera a que termine la comprobación o instalación.";
                try { output.Text = await action(); } catch (Exception failure) { output.Text = failure.Message; }
                finally { busy = false; buttons.Enabled = true; address.Text = config.TailscaleUrl ?? ""; }
            };
        }
    }
}
