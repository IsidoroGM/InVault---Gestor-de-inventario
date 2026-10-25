# Company deployment diagnostics

`check-company.ps1` reads a production company configuration and reports JSON.
It does not create/start/restart containers, modify the database or print secrets.
It requires PowerShell 7.3+, Docker and Docker Compose on the deployment host.

## Before the first start

```powershell
./deploy/check-company.ps1 -CompanyId company-a `
  -EnvironmentFile .env.company-company-a -ConfigurationOnly
```

Compose renders the file in memory. The checker validates the requested project
and schema, matching application/database credentials, configured secrets and JWT
key length, HTTPS hostname and session origins, production profile, private service
ports, company network/volume names and the MySQL named-volume mount. It rejects
host networking, an external/foreign database volume and incomplete gateway ports.
It never echoes the rendered configuration, which contains credentials.

Secret length and absence of example placeholders do not establish random entropy;
use `new-company.ps1` for independently generated credentials. The tool does not
compare secrets against other companies or inspect database grants.

## Running company

```powershell
./deploy/check-company.ps1 -CompanyId company-a `
  -EnvironmentFile .env.company-company-a
```

After configuration validation, it checks exactly one database/backend/frontend/
gateway container with the requested company's labels. Database, backend and
frontend must be running and Docker-healthy; a gateway with no healthcheck must
be running. It verifies company networks, actual private-port bindings and the
database's named volume. Selected live database/backend environment values must
match the file, including credentials, JWT, origins, production profile and the
bootstrap flag. Thus, editing the file without recreating the backend can be detected.

If these checks pass, it makes a fresh internal GET to `/api/health/readiness`
with an eight-second deadline, accepting only `UP`. Container health alone can
lag an outage. Image IDs are returned for recording the running artifacts; this
does not certify their source revision or perform an image-signature check.

The result includes `bootstrapEnabled`: after the mandatory first password change,
disable and clear bootstrap settings and recreate the backend as documented.
This checker does not inspect the administrator's password-change state.

Public DNS, certificate validity and browser/WebSocket routing are **not** probed;
`publicHttpsChecked` remains false. Container network and selected environment
checks do not certify all gateway headers, mounted Caddy content or every runtime
option. Verify the public URL and complete the PWA acceptance checklist separately.
The diagnostic is a point-in-time observation.

## Include backup checks

```powershell
./deploy/check-company.ps1 -CompanyId company-a `
  -EnvironmentFile .env.company-company-a `
  -BackupDirectory /var/backups/invault/company-a -MaxBackupAgeHours 30 `
  -ReplicaDirectory /mnt/backup-replica/company-a
```

This includes [backup monitoring](BACKUP_MONITORING.md) in the overall result.
`backupsChecked` is false when omitted. Configuration-only mode evaluates no
runtime or backups. No private decryption key is needed; recovery drills remain
separate. No tasks or notifications are activated by this command.

Exit codes: **0** for the requested checks passing, **2** for a failed check,
**1/nonzero** for invalid input or a tool/metadata failure. Operational error output
is generic so native Compose/inspect errors cannot echo secret-bearing metadata.

## Verification

`deploy/tests/company-status.ps1` covers valid/empty configurations, wrong company,
credential/issuer drift, placeholder secrets, insecure URLs, networking, ports,
volume mounts, stopped/unhealthy/foreign containers and secret-free output.
The existing isolation test also runs it against generated, rendered companies.
Docker CI executes the command on real containers, stops MySQL to observe a
nonzero result, recovers MySQL and verifies the diagnostic becomes healthy again.
