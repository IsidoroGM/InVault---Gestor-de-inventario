# InVault production deployment

This deployment targets one Linux host with Docker Engine and the Docker Compose plugin. It runs three isolated services:

- `frontend`: Angular static assets and the Nginx reverse proxy; the only service bound to the host.
- `backend`: Spring Boot with the `prod` profile; reachable only through Nginx.
- `database`: MySQL 8.4 LTS with a persistent named volume; reachable only by the backend.

TLS must terminate in a host or platform reverse proxy in front of `${HTTP_BIND_ADDRESS}:${HTTP_PORT}`. Keep the default loopback bind when the proxy runs on the same host.

## First deployment

1. Install Docker Engine and the Docker Compose plugin on the target host.
2. Clone the repository and check out the release commit.
3. Copy `.env.production.example` to `.env`, replace every example credential and domain, and restrict it to the deployment account with `chmod 600 .env`.
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

7. Confirm `https://your-domain.example/healthz`, sign in, change the bootstrap password, then set `INVAULT_BOOTSTRAP_ADMIN_ENABLED=false`, clear the three bootstrap identity/credential values and run the `up --detach` command again.

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
docker compose --env-file .env --file compose.prod.yml logs --follow backend frontend
docker compose --env-file .env --file compose.prod.yml restart backend
docker compose --env-file .env --file compose.prod.yml down
```

The final `down` command stops and removes containers and networks but preserves the named database volume.

## Production boundaries

- The Compose database connection disables TLS because it stays on a private, internal Docker network. External managed MySQL deployments should omit that override and use the profile default `DB_SSL_MODE=REQUIRED`.
- Horizontal backend scaling is not supported while the in-memory STOMP broker is in use.
- Image publication, remote host credentials, DNS and TLS certificates are environment-specific and intentionally remain outside the repository.
- The outer TLS proxy must forward `Host`, `X-Forwarded-For` and `X-Forwarded-Proto`, and should add HSTS after HTTPS is confirmed end to end.
