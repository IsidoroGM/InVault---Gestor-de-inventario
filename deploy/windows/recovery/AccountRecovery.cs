using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Security.Principal;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace InVault.Windows {
    public static class AccountRecovery {
        public static void ValidatePassword(string password) {
            if (password == null || password.Length < 12 || Encoding.UTF8.GetByteCount(password) > 72 || password.Any(Char.IsControl) || password != password.Trim())
                throw new ArgumentException("Elige una contraseña temporal de al menos 12 caracteres y un máximo de 72 bytes UTF-8, sin saltos de línea ni espacios al principio/final.");
        }
        public static void ValidateExistingData(InstallConfig c) {
            c.Validate();
            string marker = Path.Combine(c.DataRoot, "provisioned");
            if (!Directory.Exists(Path.Combine(c.DataRoot, "database", "mysql")) || !File.Exists(marker) || File.ReadAllText(marker) != c.CompanyId)
                throw new IOException("No se ha identificado una base ya inicializada para esta empresa. Se ha conservado todo; no se creará ninguna base nueva.");
            if (!File.Exists(Path.Combine(c.ReleaseRoot, "backend.jar"))) throw new IOException("Falta el backend de la versión instalada. Repara esa versión antes de recuperar el acceso.");
        }
        // Called only after administrator authorization and while the database is exclusively owned by the recovery engine.
        internal static void ResetDatabaseAccount(InstallConfig c, string password, string actor) {
            ValidatePassword(password); ValidateExistingData(c);
            if (String.IsNullOrWhiteSpace(actor) || actor.Length > 200 || actor.Any(Char.IsControl)) throw new ArgumentException("Identidad local no válida.");
            string work = Path.Combine(c.DataRoot, "tmp", "admin-recovery-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(work);
            try {
                using (var zip = ZipFile.OpenRead(Path.Combine(c.ReleaseRoot, "backend.jar"))) {
                    foreach (string prefix in new[] { "spring-security-crypto-", "mysql-connector-j-" }) {
                        var matches = zip.Entries.Where(e => e.FullName.StartsWith("BOOT-INF/lib/" + prefix, StringComparison.Ordinal) && e.FullName.EndsWith(".jar", StringComparison.Ordinal)).ToArray();
                        if (matches.Length != 1) throw new IOException("La versión instalada no contiene una biblioteca única de recuperación compatible.");
                        matches[0].ExtractToFile(Path.Combine(work, prefix + "runtime.jar"));
                    }
                }
                using (var resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("InVaultAdminRecovery.class")) {
                    if (resource == null) throw new IOException("La herramienta de recuperación está incompleta.");
                    using (var file = File.Create(Path.Combine(work, "InVaultAdminRecovery.class"))) resource.CopyTo(file);
                }
                string classpath = String.Join(Path.PathSeparator.ToString(), new[] { work, Path.Combine(work, "spring-security-crypto-runtime.jar"), Path.Combine(work, "mysql-connector-j-runtime.jar") });
                var secrets = Secrets.Load(c);
                string result = Commands.Run(Path.Combine(c.ReleaseRoot, "java", "bin", "java.exe"),
                    new[] { "-Dfile.encoding=UTF-8", "-cp", classpath, "InVaultAdminRecovery", "invault_" + c.CompanyId.Replace('-', '_'), c.DbPort.ToString() },
                    null, secrets.RootPassword + "\n" + password + "\n" + actor + "\n", 90000);
                if (result.Trim() != "INVAULT_RECOVERY_OK") throw new IOException("No se pudo confirmar el restablecimiento. Consulta el diagnóstico sin repetir contraseñas en el chat.");
            } finally {
                if (Rules.ContainsPath(Path.Combine(c.DataRoot, "tmp"), work) && System.Text.RegularExpressions.Regex.IsMatch(Path.GetFileName(work), "^admin-recovery-[a-f0-9]{32}$")) {
                    Rules.NoReparse(work); Directory.Delete(work, true);
                }
            }
        }
        public static void ResetLocalAdministrator(InstallConfig c, string password, Action<string> progress) {
            if (!Installation.IsAdmin()) throw new UnauthorizedAccessException("Windows debe autorizar esta herramienta como administrador.");
            ValidatePassword(password); ValidateExistingData(c);
            bool stopped = false, changed = false;
            Exception resetFailure = null;
            try {
                progress("Deteniendo temporalmente InVault para cerrar las sesiones anteriores…");
                Installation.Stop(c); stopped = true;
                using (var engine = new Engine(c, CancellationToken.None, progress)) {
                    engine.StartDatabase();
                    progress("Restableciendo solo la cuenta admin y registrando la operación…");
                    ResetDatabaseAccount(c, password, WindowsIdentity.GetCurrent().Name);
                    changed = true;
                }
            } catch (Exception e) { resetFailure = e; }
            Exception restartFailure = null;
            if (stopped) {
                try { progress("Volviendo a iniciar InVault…"); Installation.Start(c); Installation.Ready(c, CancellationToken.None, progress); }
                catch (Exception e) { restartFailure = e; }
            }
            if (changed && restartFailure != null)
                throw new IOException("La contraseña de admin SÍ se ha restablecido, pero el servicio no terminó de iniciar. Usa «Mantenimiento de InVault → Iniciar / comprobar». " + restartFailure.Message);
            if (resetFailure != null) throw new IOException("No se pudo completar la recuperación. " + resetFailure.Message);
            if (!changed) throw new IOException("No se ha confirmado un cambio de contraseña.");
            progress("Acceso restablecido. Usuario: admin. Entra con la contraseña temporal elegida y cámbiala cuando InVault lo solicite.");
        }
    }
    public sealed class RecoveryForm : Form {
        readonly InstallConfig config;
        readonly TextBox password = new TextBox(), confirmation = new TextBox(), status = new TextBox();
        readonly Button recover = new Button(), open = new Button();
        readonly CheckBox show = new CheckBox();
        bool running;
        public RecoveryForm(InstallConfig c) {
            config = c; Text = "Restablecer acceso a InVault"; Width = 760; Height = 595; MinimumSize = new Size(760, 595);
            Font = new Font("Segoe UI", 10); BackColor = Color.White; StartPosition = FormStartPosition.CenterScreen;
            var heading = new Label { Text = "Restablecer la cuenta admin", Font = new Font("Segoe UI", 19, FontStyle.Bold) }; heading.SetBounds(25, 20, 695, 45); Controls.Add(heading);
            var company = new Label { Text = "Empresa: " + c.CompanyName, AutoEllipsis = true }; company.SetBounds(27, 75, 690, 28); Controls.Add(company);
            var notice = new Label { Text = "La aplicación se detendrá brevemente. Se conservarán el inventario y el resto de usuarios.\r\nLa nueva contraseña será temporal: InVault te pedirá cambiarla al entrar." };
            notice.SetBounds(27, 112, 690, 55); Controls.Add(notice);
            var first = new Label { Text = "Nueva contraseña temporal (mínimo 12 caracteres)" }; first.SetBounds(27, 180, 690, 25); Controls.Add(first);
            password.SetBounds(27, 210, 690, 30); password.UseSystemPasswordChar = true; Controls.Add(password);
            var second = new Label { Text = "Repite la nueva contraseña" }; second.SetBounds(27, 252, 690, 25); Controls.Add(second);
            confirmation.SetBounds(27, 282, 690, 30); confirmation.UseSystemPasswordChar = true; Controls.Add(confirmation);
            show.Text = "Mostrar contraseñas"; show.SetBounds(27, 322, 270, 30); show.CheckedChanged += (s, e) => password.UseSystemPasswordChar = confirmation.UseSystemPasswordChar = !show.Checked; Controls.Add(show);
            recover.Text = "Restablecer acceso"; recover.SetBounds(27, 365, 200, 36); recover.Click += async (s, e) => await Recover(); Controls.Add(recover);
            open.Text = "Abrir InVault"; open.SetBounds(245, 365, 160, 36); open.Enabled = false; open.Click += (s, e) => Process.Start(new ProcessStartInfo(config.Url) { UseShellExecute = true }); Controls.Add(open);
            status.Multiline = true; status.ReadOnly = true; status.ScrollBars = ScrollBars.Vertical; status.SetBounds(27, 420, 690, 95); Controls.Add(status);
            FormClosing += (s, e) => { if (running) { e.Cancel = true; Report("Espera a que termine la operación y vuelva a iniciarse el servicio."); } };
            AcceptButton = recover;
        }
        void Report(string text) { if (InvokeRequired) { BeginInvoke(new Action<string>(Report), text); return; } status.AppendText(text + Environment.NewLine); }
        async Task Recover() {
            try {
                AccountRecovery.ValidatePassword(password.Text);
                if (password.Text != confirmation.Text) throw new ArgumentException("Las dos contraseñas no coinciden.");
                string selected = password.Text;
                running = true; password.Enabled = confirmation.Enabled = show.Enabled = recover.Enabled = false; status.Clear();
                await Task.Run(() => AccountRecovery.ResetLocalAdministrator(config, selected, Report));
                password.Clear(); confirmation.Clear(); open.Enabled = true;
                Report("Guarda la contraseña definitiva en tu gestor de contraseñas.");
            } catch (Exception e) { Report(e.Message); recover.Enabled = password.Enabled = confirmation.Enabled = show.Enabled = true; }
            finally { running = false; }
        }
    }
    public static class RecoveryProgram {
        [STAThread] public static int Main() {
            Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false);
            try {
                if (!Installation.IsAdmin()) throw new UnauthorizedAccessException("Abre esta herramienta como administrador de Windows.");
                var config = Installation.Existing();
                if (config == null) throw new IOException("Abre la herramienta en el PC donde está instalado el servidor de InVault.");
                AccountRecovery.ValidateExistingData(config);
                Application.Run(new RecoveryForm(config)); return 0;
            } catch (Exception e) { MessageBox.Show(e.Message, "Recuperación de InVault", MessageBoxButtons.OK, MessageBoxIcon.Error); return 1; }
        }
    }
}
