package com.invault.inventory.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.invault.inventory.audit.AuditAction;
import com.invault.inventory.audit.AuditService;

/** A short committed reservation precedes password hashing; denied requests never extend a window. */
@Service
public class LoginRateLimitService {
    private final JdbcTemplate jdbc;
    private final LoginRateLimitProperties properties;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public LoginRateLimitService(JdbcTemplate jdbc, LoginRateLimitProperties properties,
            AuditService audit, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.audit = audit;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setTimeout(10);
    }

    public void check(String username, String clientIp) {
        long retryAfter = Objects.requireNonNull(transactions.execute(status -> reserve(username, clientIp)));
        // Throw after committing: an authentication error or an outer rollback must not reset the budget.
        if (retryAfter > 0) {
            throw new LoginRateLimitException(retryAfter);
        }
    }

    private long reserve(String username, String clientIp) {
        long now = databaseTime();
        String ipId = "ip:" + digest(clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        Counter ip = lock(ipId, now);
        if (ip.attempts() >= properties.ipAttempts()) {
            return reject(ip, now, clientIp);
        }
        increment(ipId);

        // Resolve existing accounts using the database's own username collation. Case/accent variants
        // (and a later rename) cannot acquire fresh budgets for the same real account.
        var users = jdbc.queryForList("SELECT id FROM users WHERE LOWER(username) = LOWER(?)", Long.class, username.trim());
        String account = users.isEmpty() ? "name:" + username.trim().toLowerCase(Locale.ROOT) : "user:" + users.getFirst();
        String accountId = "account:" + digest(account);
        Counter accountCounter = lock(accountId, now);
        if (accountCounter.attempts() >= properties.accountAttempts()) {
            return reject(accountCounter, now, clientIp);
        }
        increment(accountId);
        return 0;
    }

    private Counter lock(String id, long now) {
        long expires = now + properties.window().toMillis();
        jdbc.update("""
                INSERT INTO login_rate_limits (id, attempts, window_expires_at, reported) VALUES (?, 0, ?, FALSE)
                ON DUPLICATE KEY UPDATE id = id
                """, id, expires);
        Counter current = jdbc.queryForObject("""
                SELECT attempts, window_expires_at, reported FROM login_rate_limits WHERE id = ? FOR UPDATE
                """, (rs, row) -> new Counter(id, rs.getInt(1), rs.getLong(2), rs.getBoolean(3)), id);
        now = databaseTime();
        expires = now + properties.window().toMillis();
        if (current.expiresAt() <= now) {
            jdbc.update("UPDATE login_rate_limits SET attempts = 0, window_expires_at = ?, reported = FALSE WHERE id = ?", expires, id);
            return new Counter(id, 0, expires, false);
        }
        return current;
    }

    private void increment(String id) {
        jdbc.update("UPDATE login_rate_limits SET attempts = attempts + 1 WHERE id = ?", id);
    }

    private long reject(Counter counter, long now, String clientIp) {
        now = databaseTime();
        long seconds = Math.max(1, (counter.expiresAt() - now + 999) / 1000);
        if (!counter.reported()) {
            jdbc.update("UPDATE login_rate_limits SET reported = TRUE WHERE id = ?", counter.id());
            audit.registerSystemAction(AuditAction.LOGIN_THROTTLED, "User", null,
                    "Login rate limit reached. Retry after " + seconds + " seconds.", clientIp);
        }
        return seconds;
    }

    private long databaseTime() {
        // Epoch milliseconds from MySQL: independent of JVM clocks and session time zones.
        return jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)", Long.class);
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void removeExpiredCounters() {
        long cutoff = databaseTime() - Duration.ofDays(1).toMillis();
        // Bound maintenance work. Active windows are never removed; several instances may safely run it.
        for (int batch = 0; batch < 20; batch++) {
            int removed = jdbc.update("DELETE FROM login_rate_limits WHERE window_expires_at < ? ORDER BY window_expires_at LIMIT 500", cutoff);
            if (removed < 500) {
                break;
            }
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record Counter(String id, int attempts, long expiresAt, boolean reported) {}
}
