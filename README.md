# InVault

> A full-stack inventory management system for products, batches, auditable stock movements, role-based operations, and real-time updates.

[![Backend CI](https://github.com/IsidoroGM/InVault---Gestor-de-inventario/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/IsidoroGM/InVault---Gestor-de-inventario/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/IsidoroGM/InVault---Gestor-de-inventario/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/IsidoroGM/InVault---Gestor-de-inventario/actions/workflows/frontend-ci.yml)
[![Deployment CI](https://github.com/IsidoroGM/InVault---Gestor-de-inventario/actions/workflows/deployment-ci.yml/badge.svg)](https://github.com/IsidoroGM/InVault---Gestor-de-inventario/actions/workflows/deployment-ci.yml)

InVault is a responsive web application designed for companies, factories, and warehouse teams that need a reliable view of inventory across desktop computers and tablets. It centralizes product and catalogue management, tracks stock at batch level, records every quantity change, and keeps connected clients synchronized through authenticated real-time events.

The project is implemented as a modular monolith: an Angular progressive web application communicates with a Spring Boot API, while MySQL stores the operational and audit data. REST is the source of truth for commands and queries; WebSocket/STOMP is used only to notify clients after a transaction has been committed.

The current user interface is in Spanish. Source code, API contracts, database objects, and technical names are maintained in English.

## Project status

The full-stack MVP is implemented. The repository currently includes:

- A production-oriented Spring Boot backend with JWT authentication, four official roles, transactional auditing, Flyway migrations, OpenAPI documentation in development, and authenticated STOMP events.
- A complete Angular frontend for authentication, dashboards, products, batches, stock, movements, catalogues, users, and audit records.
- An installable PWA shell for Windows, Android, and iPadOS, with responsive tablet layouts, explicit update handling, connectivity feedback, and offline mutation protection.
- A containerized production topology with Caddy, Nginx, Spring Boot, and MySQL.
- GitHub Actions quality gates for backend verification, frontend formatting/tests/build, and container/Compose validation.
- A disposable HTTPS preview workflow for testing the PWA on physical devices without owning a domain.

This is a serious MVP rather than a finished multi-site warehouse platform. The [current boundaries](#current-boundaries-and-future-work) section documents the intentionally deferred production and product capabilities.

## Core capabilities

- Secure login and logout with short-lived JWT bearer tokens.
- Immediate session revocation after logout, password reset, password change, role changes, activation, or deactivation.
- Mandatory first-login password change for bootstrap and temporary credentials.
- Role-aware navigation and backend authorization for `ADMIN`, `SUPERVISOR`, `WAREHOUSE`, and `READ_ONLY`.
- Management of products, units, categories, locations, suppliers, and batches.
- Logical deactivation instead of destructive deletion for master data.
- Auditable inbound, outbound, positive-adjustment, and negative-adjustment stock movements.
- Batch-level balances, product-level aggregated stock, minimum-stock thresholds, and low-stock indicators.
- Dashboard indicators and recent operational activity.
- Pessimistic locking of batches to protect stock integrity under concurrent operations.
- Transactional before/after audit snapshots: a business mutation is rolled back if its audit record cannot be stored.
- Authenticated `STOCK_UPDATED` events published only after the database transaction commits.
- Paginated searches and a uniform API error contract with field-level validation errors.
- Installable PWA experience without caching credentials, API responses, or inventory records for offline use.

## Inventory model and invariants

InVault deliberately separates a product definition from its physical stock:

```text
Product
  └── Batch (owns the current quantity and operational status)
        └── Stock movement (immutable explanation of each quantity change)
```

The product table does not contain an editable `currentStock` field. Current product stock is calculated by aggregating eligible batch quantities, while the movement history preserves the previous and new batch balances.

The main business rules are:

- Batch quantity cannot be changed through product or batch maintenance forms.
- Every stock change must be represented by a stock movement.
- Movement quantities are positive; the movement type determines whether stock increases or decreases.
- Stock cannot become negative.
- A batch is locked while its new balance is calculated and persisted.
- `AVAILABLE` batches accept all movement types and become `CONSUMED` at zero.
- `CONSUMED` batches can reopen through an inbound or positive-adjustment movement.
- `BLOCKED` and `INACTIVE` batches do not accept movements.
- The responsible user is taken from the signed JWT, never from the request body.
- Audit data and inventory data are committed or rolled back together.
- WebSocket events are emitted after commit, so clients are never notified about rolled-back data.

## Architecture

```text
Windows / Android / iPadOS browsers or installed PWA
                         │
                         │ HTTPS · REST · WebSocket/STOMP
                         ▼
                  Caddy TLS gateway
                         │
                         ▼
                Nginx + Angular PWA
                  │             │
          /api/** │             │ /ws
                  └──────┬──────┘
                         ▼
              Spring Boot modular monolith
        security · domain services · audit · realtime
                         │
                         ▼
                 MySQL + Flyway schema
```

In local development, Angular runs on port `4200` and talks directly to Spring Boot on port `8080`. In production, the browser uses a single HTTPS origin; Nginx serves the SPA and proxies `/api/**` and `/ws` to the private backend.

## Technology stack

| Area              | Technology                                                 | Purpose                                                                                              |
| ----------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| Frontend          | Angular 21, TypeScript 5.9, RxJS                           | Structured SPA architecture, typed forms and API models, routing, guards, and reactive state flows.  |
| UI                | Angular Material, Angular CDK, SCSS                        | Accessible components, dialogs, forms, tables, responsive layouts, and a consistent visual system.   |
| PWA               | Angular Service Worker, Web App Manifest                   | Installable app shell, controlled frontend updates, launch shortcuts, and short-outage resilience.   |
| Real time         | STOMP.js over native WebSocket                             | Authenticated inventory notifications and automatic refresh of affected views.                       |
| Backend           | Java 25, Spring Boot 4.1                                   | REST application, dependency injection, validation, configuration, and lifecycle management.         |
| HTTP API          | Spring Web MVC                                             | JSON endpoints for authentication, catalogues, inventory operations, dashboards, and administration. |
| Security          | Spring Security, OAuth2 Resource Server, JWT HS256, BCrypt | Stateless authentication, role authorization, password hashing, and token lifecycle enforcement.     |
| Persistence       | Spring Data JPA, Hibernate                                 | Domain persistence, transactions, repository queries, entity graphs, and pessimistic locking.        |
| Database          | MySQL 8.4 LTS                                              | Relational storage for identities, catalogue data, batches, movements, and audit history.            |
| Migrations        | Flyway                                                     | Reproducible and versioned production schema changes.                                                |
| API documentation | Springdoc OpenAPI 3                                        | Interactive Swagger documentation during development.                                                |
| Backend testing   | JUnit, Spring Test, H2 in MySQL mode                       | Unit, security, controller, migration, transaction, and integration-level regression tests.          |
| Frontend testing  | Vitest through Angular CLI                                 | Unit tests for services, guards, interceptors, PWA behavior, and feature components.                 |
| Build             | Maven Wrapper, npm lockfile, Angular CLI                   | Reproducible backend and frontend builds.                                                            |
| Containers        | Docker, Docker Compose                                     | Repeatable builds and orchestration of the full production stack.                                    |
| Edge              | Caddy 2, Nginx                                             | Automatic HTTPS, security headers, SPA delivery, REST proxying, and WebSocket upgrades.              |
| Automation        | GitHub Actions                                             | Backend, frontend, and deployment quality gates on `main`, `develop`, and pull requests.             |

## Roles and permissions

The frontend adapts its navigation and actions to each role, but Spring Security remains the final authority.

| Capability                                 | `ADMIN` | `SUPERVISOR` | `WAREHOUSE` | `READ_ONLY` |
| ------------------------------------------ | :-----: | :----------: | :---------: | :---------: |
| Read inventory and dashboard               |   Yes   |     Yes      |     Yes     |     Yes     |
| Receive inventory events                   |   Yes   |     Yes      |     Yes     |     Yes     |
| Create stock movements                     |   Yes   |     Yes      |     Yes     |     No      |
| Maintain products, batches, and catalogues |   Yes   |     Yes      |     No      |     No      |
| View users and roles                       |   Yes   |     Yes      |     No      |     No      |
| Create or modify users                     |   Yes   |      No      |     No      |     No      |
| View audit history                         |   Yes   |     Yes      |     No      |     No      |

Additional safeguards prevent users from deactivating themselves or removing/deactivating the last active administrator.

## Application areas

- **Dashboard:** active-product and available-batch totals, low-stock products, and recent movements.
- **Products:** paginated search, active-state filtering, creation, editing, and logical deactivation.
- **Batches:** product-linked batches, operational states, notes, and read-only quantities.
- **Stock:** product-level aggregated balances and minimum-stock status.
- **Movements:** inbound, outbound, and adjustment workflows with product, batch, supplier, reason, actor, and before/after balances.
- **Catalogues:** units, categories, locations, and suppliers in a unified maintenance area.
- **Users:** account lifecycle, official roles, temporary password resets, activation, and deactivation.
- **Audit:** paginated filters and readable before/after snapshots for traced mutations.

## Repository layout

```text
.
├── src/main/java/com/invault/inventory
│   ├── auth, users, roles
│   ├── units, categories, locations, suppliers
│   ├── products, batches, stock, dashboard
│   ├── audit, realtime, config, common
│   └── InVaultBackendApplication.java
├── src/main/resources
│   ├── db/migration                 # Flyway V1-V3
│   ├── application.yml
│   ├── application-dev.example.yml
│   └── application-prod.yml
├── src/test                         # backend tests and test configuration
├── frontend
│   ├── src/app/core                 # auth, config, errors, interceptors, PWA, realtime
│   ├── src/app/features             # operational and administration pages
│   ├── src/app/layout               # responsive application shell
│   ├── public                       # manifest, icons, and static assets
│   └── docker/nginx.conf
├── deploy                           # Caddy, deployment guide, preview scripts, PWA QA
├── .github/workflows                # backend, frontend, and deployment CI
├── compose.prod.yml
├── compose.preview.yml
├── Dockerfile                       # backend multi-stage image
└── .env.production.example
```

## Local development

### Prerequisites

- Java 25.
- MySQL 8.x.
- Node.js 24 and npm 11. The lockfile declares npm `11.17.0`.
- Git. Docker is optional for local development and required for the container workflows.

The repository includes Maven Wrapper scripts, so a global Maven installation is not required.

### 1. Create the development database

Run the following with a MySQL administrative account, replacing the example password:

```sql
CREATE DATABASE invault_inventory_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'invault_admin'@'localhost' IDENTIFIED BY 'replace-with-a-local-password';
GRANT ALL PRIVILEGES ON invault_inventory_db.* TO 'invault_admin'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Configure and start the backend

From the repository root in PowerShell:

```powershell
Copy-Item src/main/resources/application-dev.example.yml `
  src/main/resources/application-dev.yml
```

Edit the ignored `application-dev.yml` and set the local MySQL username and password. Then configure a Base64-encoded JWT secret containing at least 32 random bytes:

```powershell
$env:INVAULT_JWT_SECRET = [Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
)
```

For a new database, optionally bootstrap the first administrator:

```powershell
$env:INVAULT_BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:INVAULT_BOOTSTRAP_ADMIN_USERNAME = 'admin'
$env:INVAULT_BOOTSTRAP_ADMIN_EMAIL = 'admin@example.local'
$env:INVAULT_BOOTSTRAP_ADMIN_PASSWORD = 'replace-with-a-temporary-password'
```

The temporary password must contain 12-72 characters. Start the API:

```powershell
.\mvnw.cmd spring-boot:run
```

The bootstrap operation is idempotent and does not replace an existing account's password. After the first successful login and password change, disable the bootstrap flag for subsequent starts.

### 3. Start the frontend

In a second terminal:

```powershell
Set-Location frontend
npm ci
npm start
```

### Development URLs

| Resource            | URL                                     |
| ------------------- | --------------------------------------- |
| Angular application | <http://localhost:4200>                 |
| Backend health      | <http://localhost:8080/api/health>      |
| Actuator health     | <http://localhost:8080/actuator/health> |
| Swagger UI          | <http://localhost:8080/swagger-ui.html> |
| OpenAPI document    | <http://localhost:8080/v3/api-docs>     |
| WebSocket endpoint  | `ws://localhost:8080/ws`                |
| Inventory topic     | `/topic/inventory`                      |

## Verification

Run the same essential checks used by CI before opening a pull request.

Backend:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Frontend:

```powershell
Set-Location frontend
npm ci
npm run format:check
npm run test:ci
npm run build
```

The backend suite covers authorization, JWT lifecycle and revocation, controllers, inventory services, concurrent stock protection, transactional auditing, Flyway/schema validation, production-profile startup, and WebSocket authentication/authorization/publication. The frontend suite covers authentication state and guards, API configuration, interceptors, services, forms, and PWA connectivity/update behavior.

## API and real-time contract

Most routes require `Authorization: Bearer <accessToken>`. The main resource groups are:

| Area                    | Base route                                                          |
| ----------------------- | ------------------------------------------------------------------- |
| Authentication          | `/api/auth`                                                         |
| Users and roles         | `/api/users`, `/api/roles`                                          |
| Products and batches    | `/api/products`, `/api/batches`                                     |
| Master catalogues       | `/api/units`, `/api/categories`, `/api/locations`, `/api/suppliers` |
| Movements and summaries | `/api/stock/movements`, `/api/stock/summary`                        |
| Dashboard               | `/api/dashboard`                                                    |
| Audit                   | `/api/audit-logs`                                                   |
| Health                  | `/api/health`, `/actuator/health`                                   |

OpenAPI is the authoritative detailed endpoint reference in development. It is disabled by the production profile.

Paginated responses expose `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, and `last`. Supported page sizes are `1-100`.

API errors use a consistent JSON structure containing HTTP status, error, message, path, and timestamp. Validation failures also provide a `fieldErrors` object that the frontend maps to form controls.

The frontend connects to `/ws`, sends the bearer token in the STOMP `CONNECT` frame, and subscribes only to `/topic/inventory`. Clients are not permitted to publish messages through STOMP. The current backend uses an in-memory simple broker and publishes `STOCK_UPDATED` after a successful commit.

## Database and migrations

Production uses Flyway as the schema authority and Hibernate in validation mode:

| Migration                         | Purpose                                                                                                          |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `V1__create_inventory_schema.sql` | Creates the identity, catalogue, product, batch, stock movement, and audit tables with foreign keys and indexes. |
| `V2__add_audit_snapshots.sql`     | Adds before/after snapshots to audit records.                                                                    |
| `V3__add_user_token_version.sql`  | Adds the token version used for immediate JWT revocation.                                                        |

The business schema contains `roles`, `users`, `user_roles`, `units`, `categories`, `locations`, `suppliers`, `products`, `batches`, `stock_movements`, and `audit_logs`, plus Flyway's technical history table.

The default development profile keeps Flyway disabled and uses the ignored local configuration with `ddl-auto: update`. This is a development convenience only. Production enables Flyway and uses `ddl-auto: validate`.

## Production deployment

The supported Compose deployment targets a single Linux host with Docker Engine and the Docker Compose plugin. It runs four services:

- `gateway`: public Caddy edge with automatic TLS and HTTPS redirection.
- `frontend`: private Nginx service serving the Angular PWA and proxying API/WebSocket traffic.
- `backend`: private Spring Boot service using the `prod` profile.
- `database`: private MySQL 8.4 service with a persistent named volume.

Before deploying, point the domain to the host and allow inbound TCP `80/443` and UDP `443`.

```bash
cp .env.production.example .env
# Replace every example domain, password, and secret in .env.

docker compose --env-file .env --file compose.prod.yml config --quiet
docker compose --env-file .env --file compose.prod.yml up --detach --build
docker compose --env-file .env --file compose.prod.yml ps
```

Keep bootstrap administration enabled only for the first deployment. Change the temporary password, disable the bootstrap flag, clear its credential values, and recreate the backend service.

Do not run `docker compose down --volumes` in production: that removes the MySQL volume. Back up the database before updates and store encrypted copies away from the host.

See [deploy/README.md](deploy/README.md) for the complete first-deployment, update, backup, and operations procedure.

### Required production configuration

| Variable                                                    | Purpose                                                              |
| ----------------------------------------------------------- | -------------------------------------------------------------------- |
| `APP_DOMAIN`                                                | Public hostname used by Caddy.                                       |
| `APP_PUBLIC_URL`                                            | Exact public HTTPS origin used for CORS and WebSocket origin checks. |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` | MySQL database and credentials.                                      |
| `INVAULT_JWT_SECRET`                                        | Base64 encoding of at least 32 cryptographically random bytes.       |
| `INVAULT_JWT_ISSUER`                                        | Expected JWT issuer, normally the public application URL.            |
| `INVAULT_JWT_EXPIRATION`                                    | Access-token lifetime; defaults to `30m`.                            |
| `INVAULT_BOOTSTRAP_ADMIN_*`                                 | Optional first-administrator bootstrap values.                       |

Optional pool, bind-address, and database transport settings are documented in `.env.production.example` and `application-prod.yml`.

### HTTPS device preview without a domain

On Windows with Docker Desktop, the repository can create an isolated preview stack and a temporary Cloudflare Quick Tunnel:

```powershell
.\deploy\start-preview.ps1 -AcceptPublicExposure
```

The script generates ignored local secrets, uses its own Docker project and database volume, and prints the public URL plus initial administrator credentials. The URL is publicly reachable and the tunnel is disposable: use only non-sensitive test data and do not treat it as production hosting.

Stop it without deleting its preview database volume:

```powershell
.\deploy\stop-preview.ps1
```

## PWA behavior

- The production build is installable on supported Windows/Android browsers and through **Add to Home Screen** on iPadOS Safari.
- The service worker caches the application shell and static assets, not REST responses, credentials, or inventory records.
- When offline, the shell can reopen, but inventory data must not be treated as current.
- Every create, update, deactivate, password, and stock operation is rejected while offline.
- A new frontend version is downloaded in the background and applied only after the user accepts the update action.
- REST and STOMP reconnect to the server after connectivity returns.

Run the cross-device checklist in [deploy/PWA_ACCEPTANCE.md](deploy/PWA_ACCEPTANCE.md) before promoting a release.

## Security notes

- The API is stateless and rejects every route that has not received an explicit authorization rule.
- Passwords are hashed with BCrypt and are never returned by the API.
- JWTs include roles, password-change state, and a database-backed token version.
- Tokens are stored in browser `sessionStorage`, not `localStorage`, and are sent only to the configured API/WebSocket origin.
- Production Swagger/API docs are disabled, Actuator exposes only health without details, and the backend trusts forwarded headers through Spring's framework strategy.
- Caddy terminates TLS and Nginx applies CSP, clickjacking, MIME-sniffing, referrer, and permissions headers.
- Production containers run with reduced privileges; the frontend and backend filesystems are read-only apart from explicit temporary filesystems.
- Secrets and local environment files are ignored by Git. Never commit `application-dev.yml`, `.env`, preview credentials, database dumps, or real inventory data.

## Roadmap delivered

The project was built incrementally. The main completed milestones are:

1. **Foundation and domain model (phases 0-4.9):** Spring Boot project, modular domain packages, JPA entities, repositories, MySQL development strategy, DTOs, validation, and mappers.
2. **Business services:** catalogue, product, batch, stock, dashboard, user, and audit rules, including the rule that stock can only change through a movement.
3. **REST API:** controllers, DTO contracts, pagination, validation, and global error handling.
4. **Identity and authorization:** JWT login/logout, BCrypt passwords, official roles, endpoint permissions, mandatory password changes, and administrator safeguards.
5. **Real-time inventory:** authenticated STOMP connections, read-only topic authorization, post-commit stock events, and revoked-session disconnection.
6. **Backend stabilization:** pessimistic stock locking, batch transition rules, transactional audit snapshots, token-version revocation, environment profiles, Flyway V1-V3, expanded automated tests, and backend CI.
7. **Angular foundation:** typed API configuration, session management, interceptors, route guards, responsive shell, and role-aware navigation.
8. **Operational frontend:** products, catalogues, batches, movements, stock summaries, dashboard, live refresh, user administration, and audit explorer.
9. **Production delivery:** multi-stage container images, MySQL persistence, Nginx reverse proxy, Caddy HTTPS gateway, health checks, security restrictions, deployment documentation, and CI validation.
10. **PWA and tablet readiness:** install manifest and icons, service worker updates, responsive/touch behavior, offline mutation blocking, device acceptance checklist, and disposable HTTPS preview workflow.

## Current boundaries and future work

The following items are not part of the current implementation or remain recommended hardening work:

- Run automated integration tests against real MySQL, for example with Testcontainers, in addition to H2's MySQL compatibility mode.
- Add login rate limiting, abuse monitoring, and operational alerting.
- Add metrics, distributed tracing, richer structured logs, and correlation IDs.
- Add coverage thresholds, dependency scanning, and static application security testing.
- Replace the in-memory STOMP broker before horizontally scaling the backend.
- Introduce per-device sessions if global user token revocation is too coarse for future requirements.
- Add backup automation and periodically verified restoration procedures for production.
- Add CSV/Excel import and export, advanced reporting, notifications, and multi-warehouse support.
- Add QR/barcode workflows.
- Add explicitly designed offline data synchronization only if the business accepts its conflict and freshness model. The current PWA intentionally does not queue offline mutations.

## Git workflow

- `main` is the stable branch.
- `develop` is the integration branch.
- New work is developed on focused feature branches created from `develop`.
- Pull requests into `develop` or `main` must pass the applicable backend, frontend, and deployment checks.
- Keep commits small and descriptive, and update this README whenever setup, API contracts, deployment, or product boundaries change.

## License

No license file is currently included in this repository. Unless a license is added, the source code is not automatically granted for reuse, modification, or redistribution.
