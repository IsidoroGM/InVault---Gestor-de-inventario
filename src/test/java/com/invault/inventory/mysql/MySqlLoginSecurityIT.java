package com.invault.inventory.mysql;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.invault.inventory.auth.LoginRateLimitException;
import com.invault.inventory.auth.LoginRateLimitService;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@Timeout(120)
class MySqlLoginSecurityIT {
    private static MySqlTestDatabase database;
    private static final String[] POLICY = {"--invault.security.login-rate-limit.account-attempts=2",
            "--invault.security.login-rate-limit.ip-attempts=5"};

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
    void successfulAndFailedRequestsShareRealAccountBudgetAcrossCollationVariants() throws Exception {
        var company = database.createCompany("login");
        try (var app = company.start(POLICY)) {
            User user = new User("Opérator", "operator@example.test", app.getBean(PasswordEncoder.class).encode("correct-password"));
            user.setMustChangePassword(false);
            user.addRole(app.getBean(RoleRepository.class).findByName(RoleName.WAREHOUSE).orElseThrow());
            app.getBean(UserRepository.class).saveAndFlush(user);
            MockMvc api = api(app);
            login(api, "operator", "wrong-password").andExpect(status().isUnauthorized());
            login(api, "OPÉRATOR", "correct-password").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
            login(api, "Opérator", "correct-password").andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After")).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
            assertEquals(1, company.jdbc().queryForObject("SELECT COUNT(*) FROM login_rate_limits WHERE id LIKE 'account:%'", Integer.class));
            assertEquals(2, company.jdbc().queryForObject("SELECT attempts FROM login_rate_limits WHERE id LIKE 'account:%'", Integer.class));
            assertEquals(3, company.jdbc().queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action IN ('LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGIN_THROTTLED')", Integer.class));
        }
    }

    @Test
    void unknownAccountGetsSameLimitAndExpiryDoesNotSlideOnRepeatedRejection() throws Exception {
        var company = database.createCompany("unknown");
        try (var app = company.start(POLICY)) {
            MockMvc api = api(app);
            login(api, "unknown", "wrong-password").andExpect(status().isUnauthorized());
            login(api, " UNKNOWN ", "wrong-password").andExpect(status().isUnauthorized());
            long expires = company.jdbc().queryForObject("SELECT window_expires_at FROM login_rate_limits WHERE id LIKE 'account:%'", Long.class);
            for (int i = 0; i < 3; i++) {
                login(api, "unknown", "wrong-password").andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.message").value("Too many login attempts. Please wait before trying again."));
            }
            assertEquals(expires, company.jdbc().queryForObject("SELECT window_expires_at FROM login_rate_limits WHERE id LIKE 'account:%'", Long.class));
            assertEquals(1, company.jdbc().queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN_THROTTLED'", Integer.class));
            company.jdbc().update("UPDATE login_rate_limits SET window_expires_at = 0");
            login(api, "unknown", "wrong-password").andExpect(status().isUnauthorized());
            assertEquals(1, company.jdbc().queryForObject("SELECT attempts FROM login_rate_limits WHERE id LIKE 'account:%'", Integer.class));
        }
    }

    @Test
    void twoBackendInstancesCannotExceedSharedIpBudgetOrCreateRowsAfterIpIsBlocked() throws Exception {
        var company = database.createCompany("ipbudget");
        String[] policy = {"--invault.security.login-rate-limit.account-attempts=10", "--invault.security.login-rate-limit.ip-attempts=4"};
        try (var first = company.start(policy); var second = company.start(policy)) {
            var services = List.of(first.getBean(LoginRateLimitService.class), second.getBean(LoginRateLimitService.class));
            var pool = Executors.newFixedThreadPool(8);
            CountDownLatch ready = new CountDownLatch(8);
            CountDownLatch go = new CountDownLatch(1);
            try {
                var requests = java.util.stream.IntStream.range(0, 8).mapToObj(index -> pool.submit(() -> {
                    ready.countDown();
                    assertTrue(go.await(10, TimeUnit.SECONDS));
                    try {
                        services.get(index % 2).check("random-" + index, "198.51.100.8");
                        return true;
                    } catch (LoginRateLimitException expected) {
                        return false;
                    }
                })).toList();
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                go.countDown();
                int accepted = 0;
                for (var request : requests) {
                    if (request.get(20, TimeUnit.SECONDS)) { accepted++; }
                }
                assertEquals(4, accepted);
                assertEquals(4, company.jdbc().queryForObject("SELECT attempts FROM login_rate_limits WHERE id LIKE 'ip:%'", Integer.class));
                assertEquals(5, company.jdbc().queryForObject("SELECT COUNT(*) FROM login_rate_limits", Integer.class));
                assertEquals(1, company.jdbc().queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN_THROTTLED'", Integer.class));
            } finally {
                go.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void differentSourcesCannotBypassAccountBudgetAndAnotherAccountCanStillLogin() throws Exception {
        var company = database.createCompany("acctbudget");
        try (var app = company.start(POLICY)) {
            var service = app.getBean(LoginRateLimitService.class);
            var pool = Executors.newFixedThreadPool(6);
            CountDownLatch go = new CountDownLatch(1);
            try {
                var requests = java.util.stream.IntStream.range(0, 6).mapToObj(index -> pool.submit(() -> {
                    assertTrue(go.await(10, TimeUnit.SECONDS));
                    try {
                        service.check(index % 2 == 0 ? "operator" : "OPERATOR", "198.51.100." + index);
                        return true;
                    } catch (LoginRateLimitException expected) { return false; }
                })).toList();
                go.countDown();
                int accepted = 0;
                for (var request : requests) { if (request.get(20, TimeUnit.SECONDS)) { accepted++; } }
                assertEquals(2, accepted);
                assertDoesNotThrow(() -> service.check("other-user", "198.51.100.1"));
            } finally {
                go.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void reservationsSurviveOuterRollbackRestartAndRemainIndependentPerCompany() {
        var firstCompany = database.createCompany("persist");
        var otherCompany = database.createCompany("separate");
        try (var first = firstCompany.start(POLICY)) {
            TransactionTemplate outer = new TransactionTemplate(first.getBean(PlatformTransactionManager.class));
            assertThrows(IllegalStateException.class, () -> outer.execute(status -> {
                first.getBean(LoginRateLimitService.class).check("operator", "198.51.100.8");
                throw new IllegalStateException("Outer business transaction rolled back.");
            }));
            first.getBean(LoginRateLimitService.class).check("operator", "198.51.100.8");
        }
        try (var restarted = firstCompany.start(POLICY); var other = otherCompany.start(POLICY)) {
            assertThrows(LoginRateLimitException.class, () -> restarted.getBean(LoginRateLimitService.class).check("operator", "198.51.100.8"));
            assertDoesNotThrow(() -> other.getBean(LoginRateLimitService.class).check("operator", "198.51.100.8"));
        }
    }

    @Test
    void maintenanceRemovesOnlyLongExpiredCountersAndPreservesHistory() {
        var company = database.createCompany("pruning");
        try (var app = company.start(POLICY)) {
            var service = app.getBean(LoginRateLimitService.class);
            service.check("operator", "198.51.100.8");
            service.check("operator", "198.51.100.8");
            assertThrows(LoginRateLimitException.class, () -> service.check("operator", "198.51.100.8"));
            company.jdbc().update("UPDATE login_rate_limits SET window_expires_at = 0 WHERE id LIKE 'account:%'");
            service.removeExpiredCounters();
            assertEquals(1, company.jdbc().queryForObject("SELECT COUNT(*) FROM login_rate_limits", Integer.class));
            assertEquals(1, company.jdbc().queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN_THROTTLED'", Integer.class));
        }
    }

    private static org.springframework.test.web.servlet.ResultActions login(MockMvc api, String username, String password) throws Exception {
        return api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private static MockMvc api(ConfigurableApplicationContext app) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).apply(springSecurity()).build();
    }
}
