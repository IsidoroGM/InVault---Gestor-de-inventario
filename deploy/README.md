# InVault production deployment

This deployment targets one Linux host with Docker Engine and the Docker Compose plugin. It runs four isolated services:

- `gateway`: Caddy 2, the only public service; obtains and renews TLS certificates and proxies WebSocket traffic.
- `frontend`: Angular PWA static assets and the internal Nginx reverse proxy.
- `backend`: Spring Boot with the `prod` profile; reachable only through Nginx.
- `database`: MySQL 8.4 LTS with a persistent named volume; reachable only by the backend.

The host must accept inbound TCP traffic on ports 80 and 443 and UDP traffic on port 443. DNS for `APP_DOMAIN` must resolve to the host before the first start so Caddy can obtain the certificate. HTTP is redirected to HTTPS automatically.

## First deployment

1. Install Docker Engine and the Docker Compose plugin on the target host.
2. Clone the repository and check out the release commit.
3. Copy `.env.production.example` to `.env`, set `APP_DOMAIN` and `APP_PUBLIC_URL` to the same public HTTPS origin, replace every example credential, and restrict it to the deployment account with `chmod 600 .env`.
4. Generate the JWT secret from at least 32 random bytes:

   ```bash
   openssl rand -base64 32
   ```

5. Keep `INVAULT_BOOTSTRAP_ADMIN_ENABLED=true` only for the initial start. The bootstrap password is temporary and the administrator must replace it at first login.
6. Validate and start the stack:

   ```bash
   docker compose --env-file .env --file compose.prod.yml config --quiet
   docker compose --env-file .env --file compose.prod.yml up --detach --build
   docker compose --env-file .env --file compose.prod.yml ps
   ```

7. Confirm `https://your-domain.example/healthz`, sign in, install the PWA from the browser, change the bootstrap password, then set `INVAULT_BOOTSTRAP_ADMIN_ENABLED=false`, clear the three bootstrap identity/credential values and run the `up --detach` command again.

## PWA installation and updates

- On Windows and Android, use the browser installation action shown by InVault or the browser menu.
- On iPadOS, open the HTTPS URL in Safari and choose **Add to Home Screen**.
- The PWA caches only the application shell and static assets. REST API responses, credentials and inventory records are not configured for offline caching.
- When the device is offline, InVault blocks every HTTP mutation. Stock movements, catalogue changes and user changes always require the server.
- A newly deployed frontend is downloaded in the background. InVault displays an explicit update action and reloads only after the user accepts it.
- Complete [`PWA_ACCEPTANCE.md`](PWA_ACCEPTANCE.md) on Windows, Android and iPad before promoting a release.

## Updates

Back up the database, fetch the desired release, then rebuild. Flyway applies pending migrations before the backend becomes healthy.

```bash
docker compose --env-file .env --file compose.prod.yml exec -T database \
  sh -c 'exec mysqldump --single-transaction --user=root --password="$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  > "invault-$(date +%Y%m%d-%H%M%S).sql"
docker compose --env-file .env --file compose.prod.yml up --detach --build
docker compose --env-file .env --file compose.prod.yml ps
```

Do not use `docker compose down --volumes` in production: it deletes the MySQL volume. Store encrypted backups outside the host and periodically test restoration.

## Operations

```bash
docker compose --env-file .env --file compose.prod.yml logs --follow gateway backend frontend
docker compose --env-file .env --file compose.prod.yml restart backend
docker compose --env-file .env --file compose.prod.yml down
```

The final `down` command stops and removes containers and networks but preserves the named database volume.

## Production boundaries

- The Compose database connection disables TLS because it stays on a private, internal Docker network. External managed MySQL deployments should omit that override and use the profile default `DB_SSL_MODE=REQUIRED`.
- Horizontal backend scaling is not supported while the in-memory STOMP broker is in use.
- Image publication, remote host credentials, DNS and TLS certificates are environment-specific and intentionally remain outside the repository.
- Caddy terminates TLS, forwards `Host`, `X-Forwarded-For` and `X-Forwarded-Proto`, supports the STOMP WebSocket upgrade and adds HSTS.
- If a platform-managed TLS proxy replaces Caddy, keep the frontend private and forward the same headers and WebSocket upgrade semantics.
