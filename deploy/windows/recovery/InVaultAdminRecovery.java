import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Properties;

import org.springframework.security.crypto.bcrypt.BCrypt;

/** Offline local-owner recovery. Credentials are read from stdin, never command arguments. */
public final class InVaultAdminRecovery {
    public static void main(String[] args) {
        try {
            if (args.length != 2 || !args[0].matches("invault_[a-z][a-z0-9_]{1,23}")) {
                throw new IllegalArgumentException();
            }
            int port = Integer.parseInt(args[1]);
            if (port < 1024 || port > 65535) throw new IllegalArgumentException();
            var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String rootPassword = reader.readLine();
            String password = reader.readLine();
            String actor = reader.readLine();
            if (rootPassword == null || rootPassword.isBlank() || password == null || password.length() < 12
                    || password.getBytes(StandardCharsets.UTF_8).length > 72 || !password.equals(password.strip())
                    || password.chars().anyMatch(Character::isISOControl) || actor == null || actor.length() > 200) {
                throw new IllegalArgumentException();
            }
            String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
            var properties = new Properties();
            properties.setProperty("user", "root");
            properties.setProperty("password", rootPassword);
            properties.setProperty("sslMode", "DISABLED");
            properties.setProperty("allowPublicKeyRetrieval", "true");
            properties.setProperty("connectTimeout", "5000");
            properties.setProperty("socketTimeout", "15000");
            properties.setProperty("connectionTimeZone", "UTC");
            DriverManager.setLoginTimeout(10);
            String url = "jdbc:mysql://127.0.0.1:" + port + "/" + args[0];
            try (Connection connection = DriverManager.getConnection(url, properties)) {
                connection.setAutoCommit(false);
                try {
                    long id, version;
                    boolean mustChange;
                    try (PreparedStatement query = statement(connection, """
                            SELECT u.id, u.token_version, u.must_change_password FROM users u
                            WHERE u.username = 'admin' AND u.active = TRUE AND EXISTS (
                                SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                                WHERE ur.user_id = u.id AND r.name = 'ADMIN' AND r.active = TRUE
                            ) FOR UPDATE
                            """); ResultSet result = query.executeQuery()) {
                        if (!result.next()) throw new SQLException("No active administrator", "45000");
                        id = result.getLong(1);
                        version = result.getLong(2);
                        mustChange = result.getBoolean(3);
                        if (result.next()) throw new SQLException("Ambiguous administrator", "45000");
                    }
                    long nextVersion = Math.incrementExact(version);
                    try (PreparedStatement update = statement(connection, """
                            UPDATE users SET password_hash = ?, must_change_password = TRUE,
                                token_version = ?, updated_at = UTC_TIMESTAMP(6)
                            WHERE id = ? AND token_version = ?
                            """)) {
                        update.setString(1, hash);
                        update.setLong(2, nextVersion);
                        update.setLong(3, id);
                        update.setLong(4, version);
                        if (update.executeUpdate() != 1) throw new SQLException("Administrator changed", "45000");
                    }
                    try (PreparedStatement audit = statement(connection, """
                            INSERT INTO audit_logs (user_id, action, entity_name, entity_id, details,
                                before_data, after_data, client_ip, created_at)
                            VALUES (NULL, 'PASSWORD_RESET', 'User', ?, ?, ?, ?, NULL, UTC_TIMESTAMP(6))
                            """)) {
                        audit.setLong(1, id);
                        audit.setString(2, "Local Windows administrator recovery. Windows actor: " + actor);
                        audit.setString(3, snapshot(id, version, mustChange));
                        audit.setString(4, snapshot(id, nextVersion, true));
                        if (audit.executeUpdate() != 1) throw new SQLException("Audit failed", "45000");
                    }
                    try (PreparedStatement counters = statement(connection,
                            "DELETE FROM login_rate_limits WHERE id IN (?, ?, ?)")) {
                        counters.setString(1, "account:" + digest("user:" + id));
                        counters.setString(2, "ip:" + digest("127.0.0.1"));
                        counters.setString(3, "ip:" + digest("::1"));
                        counters.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            }
            System.out.println("INVAULT_RECOVERY_OK");
        } catch (Exception failure) {
            // Never print JDBC exceptions, SQL, the hash or either input password.
            System.err.println("INVAULT_RECOVERY_FAILED");
            System.exit(20);
        }
    }

    private static PreparedStatement statement(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(15);
        return statement;
    }

    private static String snapshot(long id, long version, boolean mustChange) {
        return "{\"id\":" + id + ",\"tokenVersion\":" + version + ",\"mustChangePassword\":" + mustChange + "}";
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
