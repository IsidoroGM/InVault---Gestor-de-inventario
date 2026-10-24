# Real MySQL integration tests

The `mysql-it` Maven profile adds nine Failsafe tests to the ordinary backend
suite. It requires a disposable MySQL **8.4 LTS** server and fails if the server or
test credentials are missing. Regular `mvn verify` remains available without MySQL.

The tests exercise:

- All migrations on an empty database, InnoDB tables, decimal scale, production
  Hibernate schema validation, and repeated migration without further changes.
- Upgrade from V1 to the current schema while preserving an existing user's data.
- Two concurrent withdrawals: observe an actual InnoDB `LOCK WAIT`, commit one
  withdrawal, reject the other, and verify stock, movement history and audit count.
- A real audit insert failure induced by a MySQL trigger: roll back the stock and
  movement together and send no inventory notification. A subsequent operation
  succeeds after removing the trigger.
- Two application instances with separate databases, database users, JWT keys and
  issuers: deny cross-database SQL and cross-company tokens, isolate catalogue and
  movements, then retain stock and audit history after restarting one application.
- Duplicate movement requests: simultaneous retries have one effect, audit and
  notification; replay retains the original response after restart, changed payloads
  return 409, failed audit releases the key, and users have independent key scopes.

Each database has a random `invault_it_` name and a separate, randomly named user
with privileges on that database only. Cleanup drops only resources created by
that test run. The applications use the production profile, Flyway and
`ddl-auto=validate`; Hibernate does not create the schema.

## Windows without Docker

Download the official MySQL 8.4 Windows ZIP from
[Oracle](https://dev.mysql.com/downloads/mysql/8.4.html), verify the published
checksum/signature, and extract it. Java 25 must be available.

```powershell
.\deploy\test-mysql.ps1 -MySqlHome 'C:\tools\mysql-8.4.13-winx64'
```

The script ignores installed MySQL option files, initializes its own temporary
data directory, generates a random root password, and starts a separate process
on an unused loopback port. It runs `clean verify -Pmysql-it`, checks that test
databases/users were removed, shuts down its own process, removes its temporary
data, and restores any previous test environment variables. It does not register
a Windows service or use the installed server's data directory.

JUnit reports are in `target/surefire-reports` and `target/failsafe-reports`.
Initialization and shutdown diagnostics remain in `target/mysql-it-server`.

## Docker on Linux/macOS

Use a new disposable container, with the same image as `compose.prod.yml`:

```bash
export INVAULT_TEST_MYSQL_HOST=127.0.0.1
export INVAULT_TEST_MYSQL_PORT=13306
export INVAULT_TEST_MYSQL_USERNAME=root
export INVAULT_TEST_MYSQL_PASSWORD="$(openssl rand -hex 32)"
container="invault-mysql-it-$(date +%s)-$$"
docker run --detach --name "$container" \
  --publish 127.0.0.1:13306:3306 \
  --env MYSQL_ROOT_PASSWORD="$INVAULT_TEST_MYSQL_PASSWORD" \
  --env MYSQL_ROOT_HOST=% mysql:8.4.11
trap 'docker rm --force --volumes "$container" >/dev/null' EXIT
ready=0
for attempt in $(seq 1 60); do
  if docker exec --env MYSQL_PWD="$INVAULT_TEST_MYSQL_PASSWORD" "$container" \
      mysql --user=root --batch --skip-column-names --execute='SELECT 1' >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
test "$ready" -eq 1 || exit 1
bash ./mvnw --batch-mode --no-transfer-progress clean verify -Pmysql-it
```

Run this block in a separate shell: the exit trap removes this container and its
disposable volumes. Port 13306 must be unused. Test credentials have the ability
to create databases/users and observe InnoDB transactions; never use an account
or server containing real company data.

## CI and limits

Backend CI starts MySQL 8.4.11, matching the production Compose image, and runs
`clean verify -Pmysql-it` in the existing backend check. Service data disappears
when the job ends. Its hardcoded password belongs only to that disposable service.

The Windows runner was verified locally with MySQL 8.4.13. Both entry points
require the 8.4 LTS line; this is not a claim that CI has already run remotely.

These checks cover the database engine and application restart. Full deployment
acceptance still needs HTTPS/WebSocket routing, independent Docker volumes,
database/container restart, and restoration from an encrypted backup.

Implementation references:
[Maven Failsafe lifecycle](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html)
and [MySQL transaction lock state](https://dev.mysql.com/doc/refman/8.4/en/information-schema-innodb-trx-table.html).
