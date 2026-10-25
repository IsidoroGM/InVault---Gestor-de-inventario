package com.invault.inventory.mysql;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.invault.inventory.InVaultBackendApplication;

/** Owns only randomly named disposable databases and restricted database users. */
final class MySqlTestDatabase implements AutoCloseable {
    private final String prefix = "invault_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final List<Company> companies = new ArrayList<>();
    private final String server;
    private final JdbcTemplate admin;

    MySqlTestDatabase() {
        String password = System.getenv("INVAULT_TEST_MYSQL_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("mysql-it requires INVAULT_TEST_MYSQL_PASSWORD for a disposable MySQL server. "
                    + "Use deploy/test-mysql.ps1 or the MySQL CI service; never supply production credentials.");
        }
        String host = env("INVAULT_TEST_MYSQL_HOST", "127.0.0.1");
        String port = env("INVAULT_TEST_MYSQL_PORT", "13306");
        if (!host.matches("[a-zA-Z0-9.-]+") || !port.matches("[0-9]{1,5}")) {
            throw new IllegalArgumentException("Invalid integration-test MySQL host or port.");
        }
        server = "jdbc:mysql://" + host + ":" + port + "/";
        admin = jdbc("", env("INVAULT_TEST_MYSQL_USERNAME", "root"), password);
        String version = admin.queryForObject("SELECT VERSION()", String.class);
        if (version == null || !version.startsWith("8.4.")) {
            throw new IllegalStateException("mysql-it requires MySQL 8.4 LTS; received " + version);
        }
        System.out.println("MySQL integration server: " + version + "; disposable prefix: " + prefix);
    }

    Company createCompany(String label) {
        if (!label.matches("[a-z]{1,10}")) {
            throw new IllegalArgumentException("Invalid test database label.");
        }
        String schema = prefix + "_" + label;
        String username = "iv_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String password = randomSecret();
        Company company = new Company(schema, username, password, randomSecret());
        // Register before the first DDL so partial provisioning is also cleaned up.
        companies.add(company);
        admin.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        admin.execute((Connection connection) -> {
            try (var statement = connection.prepareStatement("CREATE USER '" + username + "'@'%' IDENTIFIED BY ?")) {
                statement.setString(1, password);
                statement.execute();
            }
            return null;
        });
        admin.execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '" + username + "'@'%'");
        return company;
    }

    JdbcTemplate admin() {
        return admin;
    }

    private JdbcTemplate jdbc(String schema, String username, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(url(schema), username, password));
    }

    private String url(String schema) {
        return server + schema + "?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                + "&connectionTimeZone=UTC&connectTimeout=5000&socketTimeout=20000";
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (Company company : companies.reversed()) {
            // Names are generated internally. Never accept a database name from the environment.
            if (!company.schema.startsWith(prefix + "_") || !company.username.matches("iv_it_[a-f0-9]{20}")) {
                throw new IllegalStateException("Refusing to remove an unowned database or user.");
            }
            for (String sql : List.of("DROP DATABASE IF EXISTS `" + company.schema + "`",
                    "DROP USER IF EXISTS '" + company.username + "'@'%'")) {
                try {
                    admin.execute(sql);
                } catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    final class Company {
        final String schema;
        final String username;
        private final String password;
        private final String jwtSecret;

        private Company(String schema, String username, String password, String jwtSecret) {
            this.schema = schema;
            this.username = username;
            this.password = password;
            this.jwtSecret = jwtSecret;
        }

        JdbcTemplate jdbc() {
            return MySqlTestDatabase.this.jdbc(schema, username, password);
        }

        Connection connection() throws SQLException {
            return DriverManager.getConnection(url(schema), username, password);
        }

        ConfigurableApplicationContext start(String... overrides) {
            String origin = "https://" + schema.replace('_', '-') + ".example.test";
            var arguments = new ArrayList<>(List.of(
                    "--spring.profiles.active=prod",
                    "--server.address=127.0.0.1", "--server.port=0",
                    "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                    "--spring.datasource.url=" + url(schema),
                    "--spring.datasource.username=" + username,
                    "--spring.datasource.password=" + password,
                    "--spring.datasource.hikari.maximum-pool-size=4",
                    "--spring.datasource.hikari.minimum-idle=0",
                    "--spring.flyway.enabled=true", "--spring.jpa.hibernate.ddl-auto=validate",
                    "--invault.security.bootstrap-admin.enabled=false",
                    "--invault.security.jwt.secret=" + jwtSecret,
                    "--invault.security.jwt.issuer=" + origin,
                    "--invault.cors.allowed-origins=" + origin,
                    "--invault.realtime.allowed-origins=" + origin));
            arguments.addAll(List.of(overrides));
            return new SpringApplicationBuilder(InVaultBackendApplication.class).run(arguments.toArray(String[]::new));
        }
    }
}
