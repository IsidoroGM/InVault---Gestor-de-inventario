# Login throttling and trusted client addresses

Each company's database owns its login counters. Default budgets are **10
authentication attempts per account** and **50 per source IP**, in windows of
**five minutes** starting with the first attempt for that counter. Both successful
and failed authentication attempts count. The budget limits password checking,
not only failures after an expensive BCrypt operation.

## Request and transaction flow

1. Validate the login JSON and required field lengths.
2. In a short independent transaction, lock the source counter in MySQL, check
   its expiry and consume one source attempt. If exhausted, stop before looking
   up an account or creating another account counter.
3. Resolve an existing account with the database's username comparison, then lock
   its counter. Existing users use their numeric identity, so case/accent variants
   and renaming do not acquire a new budget. Unknown usernames use a trimmed,
   lowercase name. Only SHA-256 identifiers are kept in the counter table; these
   hashes are not anonymization and should still be treated as security data.
4. If a budget is exhausted, record `LOGIN_THROTTLED` once per counter/window and
   commit. Return **HTTP 429** with a positive `Retry-After` header and the same
   `retryAfterSeconds` JSON field. Messages do not reveal whether an account exists
   or which budget was exhausted. Rejected account attempts still consume the
   source budget. A source rejection creates no further account rows.
5. For admitted requests, commit the reservation before checking credentials.
   A later authentication failure, audit failure or outer rollback cannot undo an
   already committed reservation. A database failure prevents authentication.
6. The PWA displays a Spanish explanation and countdown, prevents repeat submits,
   and waits for an explicit user submission when time expires. It stores no
   credentials or retry timer in browser storage. Reloading the page may clear the
   display, but the server's budget remains effective.

Counters use InnoDB primary-key locks and a consistent source-then-account lock
order. Concurrent backend instances share the same budgets in their company's
database. MySQL supplies the time in epoch milliseconds, so JVM clock differences
and database session time zones do not determine the window.

No request slides an existing window forward. On expiry, the next attempt starts
a fresh window. A successful login does not reset the source or account counter.
Existing JWT sessions and stock operations are unaffected by a login budget.

## Configuration and rollout

The company environment file can override:

| Variable | Default | Accepted values |
| --- | --- | --- |
| `INVAULT_LOGIN_ACCOUNT_ATTEMPTS` | `10` | 1–10000 |
| `INVAULT_LOGIN_IP_ATTEMPTS` | `50` | 1–10000 |
| `INVAULT_LOGIN_WINDOW` | `5m` | Whole seconds between 1s and 1h |

Invalid settings prevent startup. Tune the source budget for the number of staff
sharing a company NAT address. This fixed-window control can allow a burst on
either side of a window boundary. A hostile party can temporarily exhaust a known
account's budget; there is no permanent account lockout. Internet-facing deployment
also needs host/network capacity limits and operational review of audit events.

Deploy the backend and Flyway **V5** before the frontend. V5 adds the
`login_rate_limits` table without modifying business records. Existing PWA versions
still receive 429 but may display the generic server message. Do not delete or
truncate active counters as a routine unblock procedure; wait for their expiry.
Hourly maintenance removes up to 10000 counters that expired more than 24 hours
ago, using the expiry index. Audit records are retained. If an attack creates
expired rows faster than this bound, adjust maintenance after measuring database
growth; this is not an automatic security-log retention policy.

Encrypted backups include the counters and throttle audit records. Recovery keeps
the stored window expiry; an already expired window naturally resets on use.

## Proxy boundary

Production exposes Caddy only. Caddy replaces incoming `X-Forwarded-For`,
`X-Forwarded-Proto` and `X-Forwarded-Host` by default, and the deployed configuration
explicitly removes `Forwarded` and `X-Forwarded-Prefix`. Nginx removes `Forwarded`,
prefix and SSL override headers again, sets the forwarded port from the trusted
protocol and forwards the controlled address chain. Spring's production
`forward-headers-strategy: framework` can then recover the client address.

Keep backend and nginx ports private. Do not expose the production backend directly
to arbitrary clients while it trusts forwarded headers. The preview's loopback
port is for local use. Adding a CDN, another proxy or a public nginx entry point
requires reviewing this trust boundary; never enable trust of arbitrary proxies.

## Verification

`clean verify -Pmysql-it` checks real MySQL 8.4 concurrency across two application
instances, account and source budgets, known-user collation, successful/failed
authentication, generic errors for unknown names, expiry, one throttle audit per
window, independent companies, restart, outer rollback and bounded cleanup.

After building production images with the example Compose configuration:

```powershell
./deploy/tests/login-security.ps1
```

The disposable Docker test runs the actual Caddy → nginx → backend → MySQL chain.
It varies spoofed `Forwarded`/`X-Forwarded-*` headers, checks 401/429 and retry metadata,
verifies audit addresses and checks persistence after backend restart. It binds one
random loopback HTTP port, needs no DNS/ACME, and removes only its newly generated
company project and volumes. It does not test public TLS certificate issuance.
Deployment CI runs this check after building the images.

Implementation references: [OWASP authentication guidance](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html),
[Spring forwarded-header trust](https://docs.spring.io/spring-security/reference/features/exploits/http.html),
[Caddy proxy headers](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#headers),
and [MySQL 8.4 locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html).
