# Windows installer phase

This distribution preserves Angular, Spring Boot and an independent MySQL database,
but runs native Windows binaries rather than Linux containers. A WinForms wizard
and a `ServiceBase` supervisor use the .NET Framework 4.8 included with modern
Windows installations. The choice avoids Docker/WSL prerequisites, shell policies
and a separately installed Java runtime on end-user PCs. Existing Docker deployment
and encrypted age backup workflows remain available independently.

## Build

Build-machine prerequisites: Windows x64, PowerShell 7.3+, Git, JDK 25 with jlink,
Node/npm matching `frontend/package.json`, Maven Wrapper dependencies, and the
Framework64 C# compiler installed with Windows. No .NET SDK or Visual Studio is
needed. End users need Windows x64 and .NET Framework 4.8; other runtime components
and the Microsoft Visual C++ prerequisite are embedded.

```powershell
./deploy/windows/build-installer.ps1
# With dependencies already cached:
./deploy/windows/build-installer.ps1 -Offline
```

The output is `releases/windows/InVaultSetup.exe`, `package.json`, a SHA-256
checksum, the source hash inventory and the build log. Transfer the EXE to the
target PC. The adjacent EXE.config declares the CLR requirement explicitly but
contains no payload or secrets. The runtime's configuration is embedded in the
installer with its own copy of Java, MySQL and Caddy. Nothing is downloaded during
the end-user installation.

The source build uses an allowlisted snapshot with development/local YAML and all
company configuration excluded; it verifies the packaged JAR. Maven verification,
Angular tests and the production build must succeed before packaging. Compiler
inputs come from that snapshot. Source hashes and full base Git revision distinguish
uncommitted installer work from the base application release. `PackageId` also
identifies every packaged file hash. This is a repeatable build procedure, not a
claim of byte-for-byte reproducibility or an already signed release.

Dependency downloads are pinned by SHA-256 in `dependencies.json`. The Caddy hash
was additionally checked against the official release SHA-512 inventory; the
Microsoft prerequisite's Authenticode signer is checked at build time. The MySQL
archive SHA-256 was recorded after downloading the exact version over official
HTTPS; it is not represented as a separately authenticated vendor signature.
A changed upstream download fails closed, including the rolling Microsoft URL;
review and pin a new release rather than automatically accepting a different hash.
The installer preserves the included Java legal files, MySQL license and Caddy
license. There is no signing certificate configured in this repository.

## Layout and lifecycle

- Program root: immutable version directories, maintenance executable and ownership marker.
- Data root: one stable company ID, `installation.json`, machine-DPAPI secrets,
  database, logs, backups and protected temporary files.
- `InVault-<company-id>` service: LocalService, delayed automatic startup and SCM
  failure recovery. A job object owns only its child processes. MySQL is shut down
  through mysqladmin; application processes are terminated before the database.
- Local Caddy → loopback Spring Boot → loopback MySQL. Explicit ports are checked.
- Bootstrap is cleared after the first successful readiness check and the backend
  is restarted with bootstrap disabled. The account still requires a password change.
- Updates create an authenticated encrypted dump before application migrations.
  Previous versions remain available. Automatic binary downgrade is rejected.
- Uninstall retains data and registry identity; a hidden native helper removes
  only program files identified by package manifests. Changed/unrecognized files stay.

No command string is passed to a shell. The single Windows installation is associated
with one company. A second company needs a separate Windows environment in this
edition. The visible name is authenticated deployment metadata at `/api/company`;
no browser parameter can select a database.

See [the Spanish user guide](GUIA_INSTALACION_ES.md) for folder selection,
maintenance, backup recovery, restart behavior and remote-device limitations.

## Optional Tailscale integration

The wizard offers an unchecked tablet-access option. The package contains the
unmodified official x64 MSI, pinned in `dependencies.json`; the build validates
its SHA-256 and Tailscale Inc. Authenticode signature. Its recorded hash was also
compared with the checksum served alongside the official MSI. Runtime installation
checks the extracted MSI against the package manifest. Existing clients are kept;
the optional install runs only after local application readiness and its failure
does not fail the application installation.

Manifest paths use forward slashes; `msiexec` source and log arguments are always
converted to full native Windows paths. The mixed path is accepted by .NET and
`MsiOpenDatabase` but fails in the Windows Installer client with error 1619.
Installation writes a verbose diagnostic log in the protected company logs directory.
The packaged-runtime smoke test performs a real administrative extraction of the
pinned MSI (whose administrative sequence contains only standard extraction actions)
to exercise this boundary without installing Tailscale or its VPN service.

Maintenance provides **Conectar tablets…** for interactive enrollment, opening the
official DNS/HTTPS settings, private Serve activation, checking and targeted
deactivation. Account sign-in and HTTPS authorization belong to the operator.
No auth keys, credentials or tailnet permissions are embedded or generated. The
assistant does not enable Funnel, subnet routing, an exit node or firewall/router
rules. The successful activation enables Windows unattended mode explicitly, as
described in the UI. Key expiry and access policy remain under the tailnet admin.

`TailscaleUrl` and `TailscaleNodeId` are persisted with the company configuration;
older configurations default to local-only. CORS and STOMP accept the local origin
and the exact HTTPS origin. The JWT issuer stays the stable installation identifier
(the local URL), so local and remote clients use the same application identity.
Caddy accepts the remote Host on its loopback listener and supplies the HTTPS
forwarded scheme only on the remote API/WebSocket route. The database stays local.

Activation refuses another node identity, unowned or modified port-443 listeners,
foreground conflicts and public Funnel on that port. A per-company mutex and a
stale-configuration check protect concurrent maintenance. Failures restore the
previous origins and attempt targeted cleanup of newly created routes. Deactivation
and uninstall never reset Serve globally, log out or uninstall a shared client.
The operator is warned when a disconnected/changed client prevents route cleanup.

Unit tests inject the CLI/service/network boundary to exercise conflicts, rollback,
offline deactivation and persistence without enrolling this developer PC. The
embedded-runtime smoke additionally checks real Caddy/Spring/MySQL remote-host
login, CORS, authenticated company access and WebSocket upgrade. Actual Tailscale
enrollment, ACL denial, HTTPS issuance, Android PWA behavior, roaming and unattended
Windows restart still require the physical-device acceptance checklist in
[TAILSCALE_ACCEPTANCE_ES.md](TAILSCALE_ACCEPTANCE_ES.md).

Official references: [Windows MSI](https://tailscale.com/docs/install/windows/msi),
[Serve](https://tailscale.com/docs/features/tailscale-serve),
[CLI](https://tailscale.com/docs/reference/tailscale-cli/serve),
[unattended mode](https://tailscale.com/docs/how-to/run-unattended),
[Android](https://tailscale.com/docs/install/android).

## Verification

```powershell
./deploy/windows/tests/run-tests.ps1 -Mode Unit
./deploy/windows/tests/run-tests.ps1 -Mode Render
./deploy/windows/tests/run-tests.ps1 -Mode Smoke -Installer ./releases/windows/InVaultSetup.exe
```

Unit tests cover native argument parsing, paths, identity, port collisions, DPAPI,
version policy, payload integrity, cancellation and backup authentication. Render
tests generate page images from the real WinForms controls. Smoke extracts the
embedded payload to paths containing spaces and accents and uses the actual Java,
MySQL and Caddy binaries. It checks readiness, mandatory password change, company
metadata, a real encrypted dump, extraction and persistence across process restart.
It does not register a service, edit machine registration or install a company on
the developer's PC. Test evidence is kept under ignored `releases/windows`.

An isolated Windows VM is still required for full acceptance: starting from a clean
OS, UAC, missing prerequisites, simulated interruption, actual SCM LocalService
startup, reboot, upgrades and removal from Windows Apps. A successful process smoke
alone does not certify those service-installation paths.

Official references: [MySQL Windows ZIP installation](https://dev.mysql.com/doc/refman/8.4/en/windows-choosing-package.html),
[MySQL initialization](https://dev.mysql.com/doc/refman/8.4/en/data-directory-initialization.html),
[Caddy command line](https://caddyserver.com/docs/command-line),
[.NET Framework on Windows](https://learn.microsoft.com/en-us/dotnet/framework/install/on-windows-10).
