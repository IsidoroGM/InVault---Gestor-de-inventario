# Stock movement retries

Every `POST /api/stock/movements` requires an `Idempotency-Key` header containing a
UUID. Generate one key for a new intended movement and reuse that key and payload
until the result is confirmed. Authentication and role checks apply to every call.

For example, withdrawing 2 units from a balance of 10 produces a balance of 8. If
the response is lost and the same request is repeated, the response still identifies
the original movement and balance of 8. It does not withdraw another 2 units.

## Server behavior

- A key belongs to the authenticated user in that company's independent database.
  The same UUID used by another user is a different operation.
- V4 adds `stock_movement_requests`. Its primary key combines user ID and UUID.
  A MySQL upsert acquires the primary-key row lock, so simultaneous retries wait
  for the original transaction, including across backend processes.
- A SHA-256 fingerprint covers product, batch, supplier, type, quantity and reason.
  Equivalent decimal scales and surrounding reason whitespace are normalized.
- Reservation, stock change, movement, audit and response snapshot commit together.
  A failure rolls back all of them; the key can then be retried.
- A successful retry returns HTTP 201 and the saved original response, including
  movement ID, timestamp, names and balances. It creates no new movement, audit
  record or WebSocket event. Later catalogue renames do not change that snapshot.
- Reusing a committed key with a different payload returns HTTP 409.
- Missing/malformed keys return HTTP 400. Quantities support at most 12 integer
  digits and 3 fractional digits, matching the database's decimal precision.

Request records have no automatic expiry. Keep them with movement history and
include them in encrypted backups; dropping them would allow old keys to execute
again. The existing database dump includes this table without an extra option.

## Browser behavior

Before sending, the form saves the key and original payload in sessionStorage,
scoped to the user. Company deployments use separate origins. Double clicks are
blocked while submitting. An unconfirmed response keeps the payload read-only
and offers an explicit **Reintentar envío** action with the same key.

Closing/reopening the dialog or reloading the same tab recovers that pending
submission. Signing out hides it from another user; signing back in as the owner
can resume it. It is removed after confirmation. Confirmed validation/lookup
failures (400/404/422) allow correction and a new submission. Authentication errors,
conflicts, connection failures and server errors retain the original pending data.

No new movement is prepared when the browser reports offline, and reconnecting
does not automatically send anything. The pending online submission is a retry
record, not an offline stock queue or a cached inventory view.

Session storage ends when the browser discards the tab/session or its data is
cleared. If that loses an unconfirmed submission, reconcile the movement history
before entering a replacement. Distinct keys represent distinct intended operations,
even when their payloads are identical. The server cannot infer that two separately
entered movements were meant to be one.

## Rollout and verification

Deploy the backend and Flyway V4 first, then the frontend, and accept the PWA update.
Older clients without the required header are rejected and must update. Do not
deploy the new frontend against a backend version that ignores the header.

The real-MySQL suite verifies simultaneous duplicate requests, one notification and
audit, HTTP replay/conflict, preserved response after application restart, rollback
and same-key retry after an audit failure, and separate user scopes. Frontend tests
verify double submit, lost-response retry, frozen payload, recovery after recreation,
user separation, validation correction and offline blocking. The backup exercise
also restores the saved request response.

See [MySQL verification](MYSQL_TESTS.md) and [backup recovery](BACKUPS.md).
The locking behavior follows the
[MySQL InnoDB upsert documentation](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html).
