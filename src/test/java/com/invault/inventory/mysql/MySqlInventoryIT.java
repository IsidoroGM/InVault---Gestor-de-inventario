package com.invault.inventory.mysql;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.invault.inventory.auth.JwtService;
import com.invault.inventory.batches.BatchRepository;
import com.invault.inventory.batches.BatchService;
import com.invault.inventory.batches.dto.BatchRequestDTO;
import com.invault.inventory.categories.Category;
import com.invault.inventory.categories.CategoryRepository;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.locations.Location;
import com.invault.inventory.locations.LocationRepository;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.stock.MovementType;
import com.invault.inventory.stock.StockService;
import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.units.Unit;
import com.invault.inventory.units.UnitRepository;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

/** Runs only via -Pmysql-it, against a real disposable MySQL 8.4 server. */
@Timeout(120)
class MySqlInventoryIT {
    private static MySqlTestDatabase database;

    @BeforeAll
    static void connect() {
        database = new MySqlTestDatabase();
    }

    @AfterAll
    static void cleanUp() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void migratesEmptyDatabaseAndValidatesProductionMappings() {
        var company = database.createCompany("fresh");
        try (var app = company.start()) {
            JdbcTemplate jdbc = company.jdbc();
            assertEquals(3, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
            assertEquals(11, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = ? AND table_name <> 'flyway_schema_history' AND engine = 'InnoDB'
                    """, Integer.class, company.schema));
            assertEquals(3, jdbc.queryForObject("""
                    SELECT numeric_scale FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = 'batches' AND column_name = 'quantity'
                    """, Integer.class, company.schema));
            assertEquals(0, app.getBean(Flyway.class).migrate().migrationsExecuted);
            app.getBean(Flyway.class).validate();
        }
    }

    @Test
    void upgradesVersionOneWithoutLosingExistingData() {
        var company = database.createCompany("upgrade");
        Flyway v1 = Flyway.configure().dataSource(company.jdbc().getDataSource()).target("1").load();
        assertEquals(1, v1.migrate().migrationsExecuted);
        JdbcTemplate jdbc = company.jdbc();
        jdbc.update("""
                INSERT INTO users (username, email, password_hash, active, must_change_password, created_at)
                VALUES ('legacy', 'legacy@example.test', 'legacy-hash', TRUE, FALSE, CURRENT_TIMESTAMP(6))
                """);
        Flyway current = Flyway.configure().dataSource(jdbc.getDataSource()).load();
        assertEquals(2, current.migrate().migrationsExecuted);
        assertEquals("legacy-hash", jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE username = 'legacy'", String.class));
        assertEquals(0L, jdbc.queryForObject("SELECT token_version FROM users WHERE username = 'legacy'", Long.class));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ?
                AND table_name = 'audit_logs' AND column_name IN ('before_data', 'after_data')
                """, Integer.class, company.schema));
        current.validate();
        assertEquals(0, current.migrate().migrationsExecuted);
        try (var app = company.start()) {
            assertTrue(app.getBean(UserRepository.class).findByUsernameIgnoreCase("legacy").isPresent());
        }
    }

    @Test
    void concurrentWithdrawalsWaitForInnoDbLockAndCannotOverdraw() throws Exception {
        var company = database.createCompany("concurrent");
        try (var app = company.start()) {
            Fixture stock = seed(app);
            JdbcTemplate jdbc = company.jdbc();
            long audits = count(jdbc, "audit_logs");
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicLong secondConnection = new AtomicLong();
            var pool = Executors.newFixedThreadPool(2);
            TransactionTemplate transactions = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            StockService service = app.getBean(StockService.class);
            try {
                var first = pool.submit(() -> asUser(stock.user(), () -> transactions.execute(status -> {
                    app.getBean(BatchRepository.class).findByIdForUpdate(stock.batchId()).orElseThrow();
                    locked.countDown();
                    await(release);
                    service.createMovement(request(stock, MovementType.OUTBOUND, "7.001"), stock.user().getId());
                    return true;
                })));
                assertTrue(locked.await(15, TimeUnit.SECONDS), "First transaction did not acquire its lock.");
                var second = pool.submit(() -> asUser(stock.user(), () -> {
                    try {
                        return transactions.execute(status -> {
                            // This JdbcTemplate uses the application's transaction-bound connection.
                            secondConnection.set(app.getBean(JdbcTemplate.class).queryForObject("SELECT CONNECTION_ID()", Long.class));
                            service.createMovement(request(stock, MovementType.OUTBOUND, "7.001"), stock.user().getId());
                            return true;
                        });
                    } catch (BadRequestException expected) {
                        assertTrue(expected.getMessage().contains("negative stock"));
                        return false;
                    }
                }));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = secondConnection.get() != 0 && database.admin().queryForObject("""
                            SELECT COUNT(*) FROM information_schema.innodb_trx
                            WHERE trx_mysql_thread_id = ? AND trx_state = 'LOCK WAIT'
                            """, Integer.class, secondConnection.get()) == 1;
                    if (waiting) {
                        break;
                    }
                    Thread.sleep(50);
                }
                assertTrue(waiting, "Second transaction never waited on the real InnoDB row lock.");
                release.countDown();
                assertTrue(first.get(20, TimeUnit.SECONDS));
                assertFalse(second.get(20, TimeUnit.SECONDS), "Only one withdrawal can fit in the available stock.");
                assertEquals(new BigDecimal("3.124"), quantity(jdbc, stock.batchId()));
                assertEquals(2, count(jdbc, "stock_movements"));
                assertEquals(audits + 1, count(jdbc, "audit_logs"));
                assertEquals(new BigDecimal("10.125"), jdbc.queryForObject(
                        "SELECT previous_batch_quantity FROM stock_movements WHERE movement_type = 'OUTBOUND'", BigDecimal.class));
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(25, TimeUnit.SECONDS), "Concurrent workers did not terminate.");
            }
        }
    }

    @Test
    void databaseAuditFailureRollsBackStockMovementAndSendsNoNotification() {
        var company = database.createCompany("rollback");
        try (var app = company.start()) {
            Fixture stock = seed(app);
            JdbcTemplate jdbc = company.jdbc();
            long audits = count(jdbc, "audit_logs");
            AtomicInteger notifications = new AtomicInteger();
            SubscribableChannel broker = app.getBean("brokerChannel", SubscribableChannel.class);
            MessageHandler observer = message -> {
                if ("/topic/inventory".equals(SimpMessageHeaderAccessor.getDestination(message.getHeaders()))) {
                    notifications.incrementAndGet();
                }
            };
            broker.subscribe(observer);
            // Force an actual MySQL insert failure, rather than replacing AuditService with a mock.
            database.admin().execute("CREATE TRIGGER `" + company.schema + "`.reject_audit BEFORE INSERT ON `"
                    + company.schema + "`.audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' "
                    + "SET MESSAGE_TEXT = 'integration audit rejected'");
            try {
                RuntimeException failure = assertThrows(RuntimeException.class, () -> asUser(stock.user(), () ->
                        app.getBean(StockService.class).createMovement(
                                request(stock, MovementType.OUTBOUND, "1.001"), stock.user().getId())));
                Throwable cause = failure;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                assertTrue(cause.getMessage().contains("integration audit rejected"), "Failure must come from the audit trigger.");
                assertEquals(new BigDecimal("10.125"), quantity(jdbc, stock.batchId()));
                assertEquals(1, count(jdbc, "stock_movements"));
                assertEquals(audits, count(jdbc, "audit_logs"));
                assertEquals(0, notifications.get());
                database.admin().execute("DROP TRIGGER `" + company.schema + "`.reject_audit");
                asUser(stock.user(), () -> app.getBean(StockService.class).createMovement(
                        request(stock, MovementType.OUTBOUND, "1.001"), stock.user().getId()));
                assertEquals(new BigDecimal("9.124"), quantity(jdbc, stock.batchId()));
                assertEquals(audits + 1, count(jdbc, "audit_logs"));
                assertEquals(1, notifications.get(), "Committed movements must reach the same broker observer.");
            } finally {
                database.admin().execute("DROP TRIGGER IF EXISTS `" + company.schema + "`.reject_audit");
                broker.unsubscribe(observer);
            }
        }
    }

    @Test
    void companyCredentialsAndTokensAreIsolatedAndDataSurvivesApplicationRestart() throws Exception {
        var first = database.createCompany("companya");
        var second = database.createCompany("companyb");
        String token;
        Long batchId;
        try (var appA = first.start(); var appB = second.start()) {
            Fixture stock = seed(appA);
            batchId = stock.batchId();
            User adminB = admin(appB, "admin-b");
            assertEquals(stock.user().getId(), adminB.getId(), "Exercise colliding user IDs in separate databases.");
            token = "Bearer " + appA.getBean(JwtService.class).generateToken(stock.user()).token();
            String tokenB = "Bearer " + appB.getBean(JwtService.class).generateToken(adminB).token();
            MockMvc apiA = api(appA);
            MockMvc apiB = api(appB);
            apiA.perform(get("/api/units").header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("KG"));
            apiB.perform(get("/api/units").header("Authorization", tokenB))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
            apiB.perform(get("/api/units").header("Authorization", token)).andExpect(status().isUnauthorized());
            apiA.perform(get("/api/units").header("Authorization", tokenB)).andExpect(status().isUnauthorized());
            apiB.perform(post("/api/units").header("Authorization", tokenB).contentType("application/json")
                            .content("{\"code\":\"ONLY_B\",\"name\":\"Company B unit\",\"symbol\":\"b\",\"active\":true}"))
                    .andExpect(status().isCreated());
            apiA.perform(get("/api/units").header("Authorization", token))
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].code").value("KG"));
            assertEquals(0, count(second.jdbc(), "stock_movements"));
            assertDatabaseAccessDenied(first, second.schema);
            assertDatabaseAccessDenied(second, first.schema);
        }
        try (var restarted = first.start()) {
            api(restarted).perform(get("/api/units").header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("KG"));
            assertEquals(new BigDecimal("10.125"), quantity(first.jdbc(), batchId));
            assertEquals(1, count(first.jdbc(), "stock_movements"));
            assertEquals(2, count(first.jdbc(), "audit_logs"));
            assertEquals("ONLY_B", second.jdbc().queryForObject("SELECT code FROM units", String.class));
        }
    }

    private static void assertDatabaseAccessDenied(MySqlTestDatabase.Company company, String otherSchema) throws Exception {
        try (var connection = company.connection(); var statement = connection.createStatement()) {
            SQLException denied = assertThrows(SQLException.class,
                    () -> statement.executeQuery("SELECT COUNT(*) FROM `" + otherSchema + "`.users"));
            assertEquals(1142, denied.getErrorCode(), "SELECT on the other company must be denied by MySQL grants.");
        }
    }

    private static Fixture seed(ConfigurableApplicationContext app) {
        User user = admin(app, "admin-a");
        Category category = app.getBean(CategoryRepository.class).saveAndFlush(new Category("Raw materials", null));
        Location location = app.getBean(LocationRepository.class).saveAndFlush(new Location("Warehouse", null));
        Unit unit = app.getBean(UnitRepository.class).saveAndFlush(new Unit("KG", "Kilogram", "kg", null));
        Product product = app.getBean(ProductRepository.class).saveAndFlush(
                new Product("MYSQL-001", "MySQL product", null, category, location, unit, BigDecimal.ONE));
        return asUser(user, () -> {
            BatchRequestDTO batch = new BatchRequestDTO();
            batch.setProductId(product.getId());
            batch.setBatchCode("MYSQL-LOT-001");
            Long batchId = app.getBean(BatchService.class).create(batch).getId();
            Fixture fixture = new Fixture(user, product.getId(), batchId);
            app.getBean(StockService.class).createMovement(request(fixture, MovementType.INBOUND, "10.125"), user.getId());
            return fixture;
        });
    }

    private static User admin(ConfigurableApplicationContext app, String username) {
        User user = new User(username, username + "@example.test", "unused-test-hash");
        user.setMustChangePassword(false);
        user.addRole(app.getBean(RoleRepository.class).findByName(RoleName.ADMIN).orElseThrow());
        return app.getBean(UserRepository.class).saveAndFlush(user);
    }

    private static StockMovementRequestDTO request(Fixture stock, MovementType type, String quantity) {
        StockMovementRequestDTO request = new StockMovementRequestDTO();
        request.setProductId(stock.productId());
        request.setBatchId(stock.batchId());
        request.setMovementType(type);
        request.setQuantity(new BigDecimal(quantity));
        request.setReason("MySQL integration test");
        return request;
    }

    private static <T> T asUser(User user, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("integration-test").header("alg", "HS256").subject(user.getUsername())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("userId", user.getId()).build();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static long count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static BigDecimal quantity(JdbcTemplate jdbc, Long batchId) {
        return jdbc.queryForObject("SELECT quantity FROM batches WHERE id = ?", BigDecimal.class, batchId);
    }

    private static MockMvc api(ConfigurableApplicationContext app) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).apply(springSecurity()).build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test transaction release.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted test transaction.", exception);
        }
    }

    private record Fixture(User user, Long productId, Long batchId) {}
}
