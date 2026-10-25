# Runtime readiness

`GET /api/health/readiness` reports whether InVault is accepting requests **and**
its database is usable. It reuses Spring Boot's readiness group with
`readinessState,db`, without opening other Actuator endpoints through nginx.

| Result | HTTP | Public body |
| --- | --- | --- |
| Ready with usable database | 200 | `{"status":"UP"}` |
| Database down, application refusing traffic, missing/unknown group | 503 | `{"status":"DOWN"}` |

The endpoint is public, uncached (`Cache-Control: no-store`) and includes no
database URLs, SQL errors, credentials or component details. The older text
`/api/health` remains a process-liveness check for compatibility. A live process
does not by itself establish database availability.

The backend's container healthcheck now uses readiness. Liveness stays independent
of MySQL, so a database outage does not imply the JVM should be restarted.
Docker Compose's restart policy does not automatically restart unhealthy
containers; host monitoring should observe both availability and container state.

The PWA checks readiness on dashboard entry and explicit reload. It accepts only
an `UP` JSON result, rejects proxy fallback pages, cancels prior checks and stops
waiting after eight seconds. This is a point-in-time check, not a promise that a
later movement will succeed. Existing transaction/error handling still applies.

Database connection acquisition follows the configured Hikari timeout
`DB_CONNECTION_TIMEOUT_MS` (30 seconds by default). HTTP clients and the container
probe have shorter deadlines; a timeout is unavailable, never a cached success.
When diagnosing an outage, allow for a connection attempt to time out. This does
not introduce an additional database connection pool.

## Verification

- Real MySQL tests replace only a disposable application's connection credentials,
  evict its connections, observe 503 and restore credentials without restarting.
- Application availability events verify refusal and recovery independently of DB.
- PWA tests cover UP/DOWN, malformed responses, network errors, cancellation and
  the request deadline.
- Docker CI stops the disposable company's MySQL through the real proxy chain,
  checks public 503 and process liveness, restarts MySQL, and verifies recovery
  while the backend's original start timestamp and login budget remain unchanged.

Based on [Spring Boot health groups and availability probes](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).
