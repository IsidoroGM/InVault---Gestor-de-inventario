# InVault production deployment

## One database per company

Use an independent deployment for each company, with a unique Compose project,
database volume, credentials, JWT key and domain. The company configuration generator,
commands and hosting constraints are documented in [COMPANY_DEPLOYMENTS.md](COMPANY_DEPLOYMENTS.md).

## HTTPS preview without a domain

For tablet and desktop acceptance testing, Windows with Docker Desktop can expose the
complete stack through a temporary Cloudflare Quick Tunnel. It does not require a
Cloudflare account, a domain, inbound ports or router configuration.

From PowerShell at the repository root, run:

```powershell
.\deploy\start-preview.ps1 -AcceptPublicExposure
```

The script creates the ignored `.env.preview` file with random local secrets, builds
the application, starts MySQL/backend/frontend, obtains a random
`https://*.trycloudflare.com` URL and configures CORS, JWT and STOMP for that exact
origin. It then prints the URL and initial administrator credentials. Change the
administrator password at first login.

The confirmation switch is required because the generated URL is reachable from the
public Internet. The preview uses its own Docker project and database volume; only
enter disposable test data and do not share the URL or credentials.

Open the printed URL on the Windows computer and on each physical device, then follow
[`PWA_ACCEPTANCE.md`](PWA_ACCEPTANCE.md). Docker Desktop and the `cloudflared`
container must remain running during the test. The public hostname may change after
the tunnel container is recreated; rerunning the script updates the backend origin.

Stop the preview without deleting its MySQL volume or secrets:

```powershell
.\deploy\stop-preview.ps1
```

Quick Tunnels are disposable development infrastructure, not production hosting.
They provide no uptime guarantee and must not be used for real inventory data.

This deployment targets one Linux host with Docker Engine and the Docker Compose plugin. It runs four isolated services:

- `gateway`: Caddy 2, the only public service; obtains and renews TLS certificates and proxies WebSocket traffic.
- `frontend`: Angular PWA static assets and the internal Nginx reverse proxy.
- `backend`: Spring Boot with the `prod` profile; reachable only through Nginx.
- `database`: MySQL 8.4 LTS with a persistent named volume; reachable only by the backend.

The host must accept inbound TCP traffic on ports 80 and 443 and UDP traffic on port 443. DNS for `APP_DOMAIN` must resolve to the host before the first start so Caddy can obtain the certificate. HTTP is redirected to HTTPS automatically.

## First deployment

For candidate `v0.1.0-rc.1`, start with the
[candidate handover](RELEASE_CANDIDATE_ES.md) and [release notes](../CHANGELOG.md).
The tag identifies application sources; container images are built separately
with the identified release helper.

1. Install Docker Engine and the Docker Compose plugin on the target host.
2. Clone the repository and check out the release commit.
3. Install PowerShell 7.3+ and generate the company's private configuration:

   ```powershell
   $company = ./deploy/new-company.ps1 -CompanyId company-a -Domain inventory.example.com -AdminEmail admin@inventory.example.com
   ```

4. Build an identified release from the clean committed checkout. See [RELEASES.md](RELEASES.md).

   ```powershell
   $release = ./deploy/build-release.ps1
   ./deploy/check-company.ps1 -CompanyId company-a -EnvironmentFile $company.EnvironmentFile -ConfigurationOnly
   docker compose --project-name $company.ProjectName --env-file $company.EnvironmentFile --file compose.prod.yml --file $release.ComposeFile up --detach --no-build --wait
   ./deploy/check-company.ps1 -CompanyId company-a -EnvironmentFile $company.EnvironmentFile -ExpectedRevision $release.Revision
   ```

5. Keep bootstrap enabled only for the initial start. Read its temporary password
   from the protected configuration and replace it at first login.
6. Verify public HTTPS and install the PWA. Disable and clear bootstrap settings,
   then repeat `up` with the same project, environment file and release overlay.
   Configure the company's backups and monitoring.

## PWA installation and updates

- On Windows and Android, use the browser installation action shown by InVault or the browser menu.
- On iPadOS, open the HTTPS URL in Safari and choose **Add to Home Screen**.
- The PWA caches only the application shell and static assets. REST API responses, credentials and inventory records are not configured for offline caching.
- When the device is offline, InVault blocks every HTTP mutation. Stock movements, catalogue changes and user changes always require the server.
- A newly deployed frontend is downloaded in the background. InVault displays an explicit update action and reloads only after the user accepts it.
- Complete [`PWA_ACCEPTANCE.md`](PWA_ACCEPTANCE.md) on Windows, Android and iPad before promoting a release.

## Login protection

Each company's database persists account and source-IP login budgets. Defaults are
10 and 50 attempts in five minutes; both successful and failed attempts count.
The PWA shows the server's retry delay. See [`LOGIN_SECURITY.md`](LOGIN_SECURITY.md)
for tuning, audit events, proxy trust and the disposable full-stack smoke test.

## Measurement identity

Units used by products and units assigned to products with batches are protected
against relabelling. See [`MEASUREMENT_IDENTITY.md`](MEASUREMENT_IDENTITY.md) for
permitted edits, permanent flags, concurrent creation and the V6 rollout.

## Backup supervision

Use [`check-backups.ps1`](check-backups.ps1) to report absent/stale company backups
and optional replica hash mismatches. JSON and exit codes support host monitoring;
an hourly systemd template is provided. See [`BACKUP_MONITORING.md`](BACKUP_MONITORING.md)
for configuration and the distinction between freshness checks and recovery tests.

## Updates

Build and record the desired release images, back up the running company database,
then use the release overlay with `up --no-build --wait` for that same project.
Flyway applies pending migrations before the backend becomes healthy.
Using a [generated company configuration](COMPANY_DEPLOYMENTS.md):

```bash
pwsh -NoProfile -NonInteractive -File ./deploy/backup-company.ps1 \
  -CompanyId company-a -EnvironmentFile .env.company-company-a \
  -RecipientFile /etc/invault/backup-recipients.txt \
  -OutputDirectory /var/backups/invault/company-a
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml --file releases/invault-RELEASE_SHA.compose.json up --detach --no-build --wait
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml ps
```

Do not use `docker compose down --volumes` in production: it deletes the MySQL volume. Use [encrypted per-company backups and isolated recovery](BACKUPS.md), store copies outside the host and periodically test restoration.

## Operations

Check `https://your-domain.example/api/health/readiness` for HTTP 200/`UP` before
using the company. HTTP 503/`DOWN` includes a database outage; `/healthz` only
checks static serving and `/api/health` only checks Java liveness. See
[`READINESS.md`](READINESS.md) for deadlines, PWA display and recovery tests.

Run [`check-company.ps1`](check-company.ps1) with the company ID and its environment
file for a JSON diagnostic of configuration, isolation, containers and readiness.
Use `-ConfigurationOnly` before the first start, or include backup/replica paths
during maintenance. See [`COMPANY_DIAGNOSTICS.md`](COMPANY_DIAGNOSTICS.md).

```bash
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml logs --follow gateway backend frontend
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml restart backend
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml down
```

The final `down` command stops and removes containers and networks but preserves the named database volume.

## Production boundaries

- The Compose database connection disables TLS and permits MySQL public-key retrieval because it stays on a private, internal Docker network. External managed MySQL deployments should omit both overrides and use the profile defaults `DB_SSL_MODE=REQUIRED` and `DB_ALLOW_PUBLIC_KEY_RETRIEVAL=false`.
- Horizontal backend scaling is not supported while the in-memory STOMP broker is in use.
- Image publication, remote host credentials, DNS and TLS certificates are environment-specific and intentionally remain outside the repository.
- Caddy terminates TLS, forwards `Host`, `X-Forwarded-For` and `X-Forwarded-Proto`, supports the STOMP WebSocket upgrade and adds HSTS.
- If a platform-managed TLS proxy replaces Caddy, keep the frontend private and forward the same headers and WebSocket upgrade semantics.
