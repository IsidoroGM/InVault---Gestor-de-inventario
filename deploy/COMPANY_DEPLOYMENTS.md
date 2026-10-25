# Company deployments with independent databases

Each company receives an independent InVault deployment using the same application
code. A deployment contains its own MySQL service, persistent volume, database user,
backend, frontend, JWT signing key, administrator and public hostname. There is no
company selector or database name supplied by the browser.

| Resource | Company A | Company B |
| --- | --- | --- |
| Public URL | https://a.example.com | https://b.example.com |
| Compose project | invault-company-a | invault-company-b |
| Database | invault_company_a | invault_company_b |
| Database volume | invault-company-a_mysql-data | invault-company-b_mysql-data |
| Configuration | .env.company-company-a | .env.company-company-b |

## Create a company configuration

Use PowerShell 7.3 or later from the repository root. This command only creates a
local configuration file; it does not deploy services or expose an endpoint:

~~~powershell
.\deploy\new-company.ps1 -CompanyId company-a -Domain a.example.com -AdminEmail admin@a.example.com
~~~

The result identifies the private environment file and public URL. The generator
creates independent random database, root, JWT and temporary administrator secrets.
It refuses to overwrite an existing configuration and reserves the company ID
preview to avoid colliding with the disposable preview project.

Files named .env.company-* are ignored by Git and excluded from the Docker build
context. Keep them restricted to the deployment administrator. On Linux they are
created with mode 0600; on Windows they inherit the target folder's permissions.
Read the temporary administrator password from that protected file. Do not paste
the file into tickets or share it with application users.

Copy the file securely to the corresponding deployment host if it was generated
elsewhere. Restrict a transferred file to its owner, for example with chmod 600.

## Start an independent deployment

The existing compose.prod.yml is reused. Always supply both the correct environment
file and explicit project name, including for updates, logs, backups and shutdown.
The project name identifies the existing volumes and must remain stable:

~~~bash
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml config --quiet
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml up --detach --build
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml ps
~~~

Repeat with a different CompanyId, domain, environment file and project name for
each company. Do not copy another company's generated secrets or database volume.
Flyway creates and upgrades each company's schema independently.

Complete the first-login password change, set INVAULT_BOOTSTRAP_ADMIN_ENABLED=false,
clear the bootstrap username/email/password in that company's environment file,
and recreate its backend with the same project name. The normal users and role
assignments are stored only in that company's database.

JWT issuer, signing key, CORS and STOMP origins belong to the company's own HTTPS
origin. A company A token must not authorize an API call to company B. Use distinct
hostnames so browser session storage, service workers and PWA installations also
have separate origins.

## Hosting and HTTPS

The standalone Compose stack publishes ports 80 and 443 through its Caddy gateway.
The simplest topology is a host or VM per company. Multiple deployments may also
share one Docker host when each gateway binds to a different IP address actually
assigned to that host; use the generator's BindAddress argument.

Two gateways cannot both bind 0.0.0.0:80 and 0.0.0.0:443 on the same host. Different
Compose project names isolate networks and volumes, but do not remove this port
collision. Hosting several company domains on one IP requires a shared HTTPS
reverse proxy and an adapted gateway topology. That shared-proxy configuration is
not included in this change.

Configure DNS and HTTPS for each domain and verify both REST and WebSocket traffic.
Containers on one host share its resources and administrators; separate deployments
do not provide the same host-level isolation as separate VMs.

## Backups and updates

Use one backup schedule and off-host storage location per company, with the project
name, company ID, timestamp and release commit recorded in the backup metadata.
Use [encrypted company backups and isolated recovery](BACKUPS.md), including its
per-company systemd timer, off-host replica option and restoration exercise.
Test recovery on the chosen deployment host before using a backup operationally.

Do not change the project name or run down --volumes when updating or stopping a
company. Changing the name creates different volumes and can make an existing
database appear empty. Stopping one deployment without deleting its volumes uses:

~~~bash
docker compose --project-name invault-company-a --env-file .env.company-company-a --file compose.prod.yml down
~~~

Build and test a release, back up the company's database, then update that company
and let Flyway migrate its schema. Companies can be upgraded independently. Keep
the code/images at a recorded release so a later rebuild cannot silently select
different application sources.

## Verification before onboarding

Before starting an individual company, use the read-only
[company diagnostic](COMPANY_DIAGNOSTICS.md) with `-ConfigurationOnly`. After startup,
run it without that switch to verify container identity, volume/networks, selected
settings and database readiness. Public HTTPS and device acceptance remain separate.

Run the deployment configuration check with Docker Compose installed:

~~~powershell
.\deploy\tests\company-isolation.ps1
~~~

This renders two company configurations without starting containers. It checks
distinct database volumes, networks, credentials, origins and JWT secrets, private
service ports and refusal to overwrite existing company configuration.

The backend verification suite also includes IndependentCompanyDeploymentTests.
It starts two local application contexts with independent H2 databases and JWT
keys, verifies that catalogue data stays separate, and rejects cross-company tokens
even when the numeric administrator IDs are equal.

The `mysql-it` profile adds the equivalent boundary checks on real MySQL 8.4 with
independent database users and grants. It rejects SQL access to the other company's
database and verifies that stock, movements and audit records survive application
restart. See [real MySQL verification](MYSQL_TESTS.md) for the local runner and CI.
Production Docker volumes and proxy routing still need deployment acceptance.

Runtime acceptance still requires two deployed test companies: create a product
and user in A, verify neither appears in B, reject A's JWT at B, restart both and
confirm persistence, and restore a backup into an isolated recovery environment.
Then complete the existing PWA acceptance checklist for the chosen domains.

References: [Compose project names](https://docs.docker.com/compose/how-tos/project-name/)
and [Compose networking](https://docs.docker.com/compose/how-tos/networking/).
