package com.invault.inventory.mysql;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.invault.inventory.batches.BatchRepository;
import com.invault.inventory.batches.BatchService;
import com.invault.inventory.batches.dto.BatchRequestDTO;
import com.invault.inventory.categories.Category;
import com.invault.inventory.categories.CategoryRepository;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.locations.Location;
import com.invault.inventory.locations.LocationRepository;
import com.invault.inventory.products.ProductRepository;
import com.invault.inventory.products.ProductService;
import com.invault.inventory.products.dto.ProductRequestDTO;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.stock.MovementType;
import com.invault.inventory.stock.StockService;
import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.units.Unit;
import com.invault.inventory.units.UnitRepository;
import com.invault.inventory.units.UnitService;
import com.invault.inventory.units.dto.UnitRequestDTO;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@Timeout(120)
class MySqlMeasurementIdentityIT {
    private static MySqlTestDatabase database;

    @BeforeAll
    static void connect() { database = new MySqlTestDatabase(); }

    @AfterAll
    static void cleanUp() { if (database != null) { database.close(); } }

    @Test
    void migrationBackfillsExistingReferencesIncludingZeroQuantityBatches() {
        var company = database.createCompany("oldunits");
        Flyway.configure().dataSource(company.jdbc().getDataSource()).target("5").load().migrate();
        var jdbc = company.jdbc();
        jdbc.update("INSERT INTO units (id,code,name,active,created_at) VALUES (1,'KG','Kilogram',TRUE,NOW(6))");
        jdbc.update("INSERT INTO categories (id,name,active,created_at) VALUES (1,'Materials',TRUE,NOW(6))");
        jdbc.update("INSERT INTO locations (id,name,active,created_at) VALUES (1,'Warehouse',TRUE,NOW(6))");
        jdbc.update("INSERT INTO products (id,sku,name,category_id,location_id,unit_id,minimum_stock,active,created_at) VALUES (1,'OLD','Old product',1,1,1,0,TRUE,NOW(6))");
        jdbc.update("INSERT INTO batches (id,product_id,batch_code,quantity,status,created_at) VALUES (1,1,'EMPTY',0,'AVAILABLE',NOW(6))");
        assertEquals(1, Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate().migrationsExecuted);
        assertEquals(1, jdbc.queryForObject("SELECT identity_locked FROM units WHERE id = 1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT unit_locked FROM products WHERE id = 1", Integer.class));
        try (var app = company.start()) {
            assertTrue(app.getBean(ProductService.class).findById(1L).isUnitLocked());
            assertTrue(app.getBean(UnitService.class).findById(1L).isIdentityLocked());
        }
    }

    @Test
    void unusedIdentityCanBeCorrectedAndProductWithoutBatchesCanChooseAnotherUnit() {
        var company = database.createCompany("unused");
        try (var app = company.start()) {
            Fixture fixture = seed(app);
            asUser(fixture.user(), () -> {
                UnitRequestDTO corrected = unitRequest("KILO", "Kilogramme", "kg", "Corrected before use");
                assertFalse(app.getBean(UnitService.class).update(fixture.unitId(), corrected).isIdentityLocked());
                var request = productRequest(fixture, fixture.unitId());
                var product = app.getBean(ProductService.class).create(request);
                assertTrue(app.getBean(UnitService.class).findById(fixture.unitId()).isIdentityLocked());
                request.setUnitId(fixture.otherUnitId());
                assertEquals(fixture.otherUnitId(), app.getBean(ProductService.class).update(product.getId(), request).getUnitId());
                // The original unit stays fixed even after the product selects another unused measurement.
                assertThrows(BadRequestException.class, () -> app.getBean(UnitService.class).update(fixture.unitId(), unitRequest("KG2", "Other", "x", null)));
                return true;
            });
        }
    }

    @Test
    void usedUnitsAndConsumedProductsKeepIdentityButDescriptionAndStatusRemainEditable() {
        var company = database.createCompany("history");
        Long productId;
        try (var app = company.start()) {
            Fixture fixture = seed(app);
            productId = asUser(fixture.user(), () -> {
                var service = app.getBean(ProductService.class);
                var request = productRequest(fixture, fixture.unitId());
                var product = service.create(request);
                var batch = app.getBean(BatchService.class).create(batchRequest(product.getId()));
                var stock = app.getBean(StockService.class);
                stock.createMovement(movement(product.getId(), batch.getId(), MovementType.INBOUND), fixture.user().getId());
                stock.createMovement(movement(product.getId(), batch.getId(), MovementType.OUTBOUND), fixture.user().getId());
                assertEquals(0, app.getBean(BatchRepository.class).findById(batch.getId()).orElseThrow().getQuantity().compareTo(BigDecimal.ZERO));
                request.setUnitId(fixture.otherUnitId());
                assertThrows(BadRequestException.class, () -> service.update(product.getId(), request));
                var unitService = app.getBean(UnitService.class);
                for (var changed : new UnitRequestDTO[] {unitRequest("OTHER", "Kilogram", "kg", null),
                        unitRequest("KG", "Litre", "kg", null), unitRequest("KG", "Kilogram", "L", null)}) {
                    assertThrows(BadRequestException.class, () -> unitService.update(fixture.unitId(), changed));
                }
                var description = unitRequest("KG", "Kilogram", "kg", "Clarified measurement");
                description.setActive(false);
                assertTrue(unitService.update(fixture.unitId(), description).isIdentityLocked());
                request.setUnitId(fixture.unitId());
                request.setName("Renamed product");
                assertTrue(service.update(product.getId(), request).isUnitLocked());
                service.deactivate(product.getId());
                assertTrue(service.findById(product.getId()).isUnitLocked());
                return product.getId();
            });
        }
        try (var restarted = company.start()) {
            assertTrue(restarted.getBean(ProductService.class).findById(productId).isUnitLocked());
            assertFalse(restarted.getBean(ProductService.class).findById(productId).isActive());
        }
    }

    @Test
    void productCreationAndUnitRenameSerializeOnTheUnitRow() throws Exception {
        var company = database.createCompany("unitrace");
        try (var app = company.start()) {
            Fixture fixture = seed(app);
            races(app, () -> asUser(fixture.user(), () -> app.getBean(ProductService.class).create(productRequest(fixture, fixture.unitId()))),
                    () -> asUser(fixture.user(), () -> app.getBean(UnitService.class).update(fixture.unitId(), unitRequest("L", "Litre", "L", null))));
            assertEquals("KG", company.jdbc().queryForObject("SELECT code FROM units WHERE id = ?", String.class, fixture.unitId()));
            assertEquals(1, company.jdbc().queryForObject("SELECT identity_locked FROM units WHERE id = ?", Integer.class, fixture.unitId()));
        }
    }

    @Test
    void firstBatchAndProductUnitChangeSerializeEvenWhenTheBatchIsEmpty() throws Exception {
        var company = database.createCompany("batchrace");
        try (var app = company.start()) {
            Fixture fixture = seed(app);
            Long productId = asUser(fixture.user(), () -> app.getBean(ProductService.class).create(productRequest(fixture, fixture.unitId())).getId());
            var changed = productRequest(fixture, fixture.otherUnitId());
            races(app, () -> asUser(fixture.user(), () -> app.getBean(BatchService.class).create(batchRequest(productId))),
                    () -> asUser(fixture.user(), () -> app.getBean(ProductService.class).update(productId, changed)));
            assertEquals(fixture.unitId(), company.jdbc().queryForObject("SELECT unit_id FROM products WHERE id = ?", Long.class, productId));
            assertEquals(1, company.jdbc().queryForObject("SELECT unit_locked FROM products WHERE id = ?", Integer.class, productId));
            assertEquals(new BigDecimal("0.000"), company.jdbc().queryForObject("SELECT quantity FROM batches", BigDecimal.class));
        }
    }

    @Test
    void auditFailureRollsBackIdentityLocksTogetherWithProductAndBatchCreation() {
        var company = database.createCompany("unitroll");
        try (var app = company.start()) {
            Fixture fixture = seed(app);
            String trigger = "`" + company.schema + "`.reject_measurement_audit";
            database.admin().execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON `" + company.schema
                    + "`.audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'measurement audit rejected'");
            try {
                assertThrows(RuntimeException.class, () -> asUser(fixture.user(), () -> app.getBean(ProductService.class).create(productRequest(fixture, fixture.unitId()))));
                assertEquals(0, company.jdbc().queryForObject("SELECT COUNT(*) FROM products", Integer.class));
                assertEquals(0, company.jdbc().queryForObject("SELECT identity_locked FROM units WHERE id = ?", Integer.class, fixture.unitId()));
            } finally { database.admin().execute("DROP TRIGGER " + trigger); }
            Long productId = asUser(fixture.user(), () -> app.getBean(ProductService.class).create(productRequest(fixture, fixture.unitId())).getId());
            database.admin().execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON `" + company.schema
                    + "`.audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'measurement audit rejected'");
            try {
                assertThrows(RuntimeException.class, () -> asUser(fixture.user(), () -> app.getBean(BatchService.class).create(batchRequest(productId))));
                assertEquals(0, company.jdbc().queryForObject("SELECT COUNT(*) FROM batches", Integer.class));
                assertEquals(0, company.jdbc().queryForObject("SELECT unit_locked FROM products WHERE id = ?", Integer.class, productId));
            } finally { database.admin().execute("DROP TRIGGER " + trigger); }
        }
    }

    @Test
    void concurrentDeactivationCannotEraseNewIdentityFlags() throws Exception {
        var company = database.createCompany("deactrace");
        try (var app = company.start()) {
            Fixture fixture = seed(app);
            AtomicLong productId = new AtomicLong();
            races(app, () -> asUser(fixture.user(), () -> {
                var created = app.getBean(ProductService.class).create(productRequest(fixture, fixture.unitId()));
                productId.set(created.getId());
                return created;
            }), () -> asUser(fixture.user(), () -> {
                app.getBean(UnitService.class).deactivate(fixture.unitId());
                return true;
            }), false);
            assertEquals(1, company.jdbc().queryForObject("SELECT identity_locked FROM units WHERE id = ?", Integer.class, fixture.unitId()));
            assertEquals(0, company.jdbc().queryForObject("SELECT active FROM units WHERE id = ?", Integer.class, fixture.unitId()));
            races(app, () -> asUser(fixture.user(), () -> app.getBean(BatchService.class).create(batchRequest(productId.get()))),
                    () -> asUser(fixture.user(), () -> {
                        app.getBean(ProductService.class).deactivate(productId.get());
                        return true;
                    }), false);
            assertEquals(1, company.jdbc().queryForObject("SELECT unit_locked FROM products WHERE id = ?", Integer.class, productId.get()));
            assertEquals(0, company.jdbc().queryForObject("SELECT active FROM products WHERE id = ?", Integer.class, productId.get()));
        }
    }

    private void races(ConfigurableApplicationContext app, Supplier<?> firstAction, Supplier<?> secondAction) throws Exception {
        races(app, firstAction, secondAction, true);
    }

    private void races(ConfigurableApplicationContext app, Supplier<?> firstAction, Supplier<?> secondAction, boolean rejectSecond) throws Exception {
        var tx = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong connection = new AtomicLong();
        try {
            var first = pool.submit(() -> tx.execute(status -> {
                firstAction.get();
                locked.countDown();
                await(release);
                return true;
            }));
            assertTrue(locked.await(15, TimeUnit.SECONDS));
            var second = pool.submit(() -> {
                Runnable execute = () -> tx.execute(status -> {
                    connection.set(app.getBean(JdbcTemplate.class).queryForObject("SELECT CONNECTION_ID()", Long.class));
                    return secondAction.get();
                });
                if (rejectSecond) { assertThrows(BadRequestException.class, execute::run); }
                else { execute.run(); }
                return true;
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean waiting = false;
            while (System.nanoTime() < deadline) {
                waiting = connection.get() != 0 && database.admin().queryForObject("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id = ? AND trx_state = 'LOCK WAIT'", Integer.class, connection.get()) == 1;
                if (waiting) { break; }
                Thread.sleep(50);
            }
            assertTrue(waiting, "The competing update did not wait on the real InnoDB lock.");
            release.countDown();
            assertTrue(first.get(20, TimeUnit.SECONDS));
            assertTrue(second.get(20, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private static Fixture seed(ConfigurableApplicationContext app) {
        User user = new User("manager", "manager@example.test", "unused-hash");
        user.addRole(app.getBean(RoleRepository.class).findByName(RoleName.ADMIN).orElseThrow());
        user = app.getBean(UserRepository.class).saveAndFlush(user);
        var category = app.getBean(CategoryRepository.class).saveAndFlush(new Category("Materials", null));
        var location = app.getBean(LocationRepository.class).saveAndFlush(new Location("Warehouse", null));
        var unit = app.getBean(UnitRepository.class).saveAndFlush(new Unit("KG", "Kilogram", "kg", null));
        var other = app.getBean(UnitRepository.class).saveAndFlush(new Unit("L", "Litre", "L", null));
        return new Fixture(user, category.getId(), location.getId(), unit.getId(), other.getId());
    }

    private static ProductRequestDTO productRequest(Fixture fixture, Long unitId) {
        var request = new ProductRequestDTO();
        request.setSku("MEASURE-001"); request.setName("Material");
        request.setCategoryId(fixture.categoryId()); request.setLocationId(fixture.locationId());
        request.setUnitId(unitId); request.setMinimumStock(BigDecimal.ZERO); request.setActive(true);
        return request;
    }

    private static UnitRequestDTO unitRequest(String code, String name, String symbol, String description) {
        var request = new UnitRequestDTO();
        request.setCode(code); request.setName(name); request.setSymbol(symbol); request.setDescription(description); request.setActive(true);
        return request;
    }

    private static BatchRequestDTO batchRequest(Long productId) {
        var request = new BatchRequestDTO(); request.setProductId(productId); request.setBatchCode("MEASURE-BATCH"); return request;
    }

    private static StockMovementRequestDTO movement(Long productId, Long batchId, MovementType type) {
        var request = new StockMovementRequestDTO(); request.setProductId(productId); request.setBatchId(batchId); request.setMovementType(type); request.setQuantity(BigDecimal.ONE); return request;
    }

    private static <T> T asUser(User user, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        var jwt = Jwt.withTokenValue("measurement-test").header("alg", "HS256").subject(user.getUsername())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("userId", user.getId()).build();
        context.setAuthentication(new JwtAuthenticationToken(jwt)); SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(20, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }

    private record Fixture(User user, Long categoryId, Long locationId, Long unitId, Long otherUnitId) {}
}
