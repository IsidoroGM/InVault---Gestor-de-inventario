package com.invault.inventory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FlywayMigrationTests {

    @Test
    void initialMigrationCreatesTheCompleteInventorySchema() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:invault_migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer businessTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('roles', 'users', 'user_roles', 'categories', 'locations',
                                     'units', 'suppliers', 'products', 'batches',
                                     'stock_movements', 'audit_logs')
                """,
                Integer.class
        );

        assertEquals(11, businessTables);

        Integer auditSnapshotColumns = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'audit_logs'
                  AND column_name IN ('before_data', 'after_data')
                """,
                Integer.class
        );

        assertEquals(2, auditSnapshotColumns);
    }
}
