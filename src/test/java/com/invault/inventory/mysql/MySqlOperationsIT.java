package com.invault.inventory.mysql;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.zaxxer.hikari.HikariDataSource;

@Timeout(120)
class MySqlOperationsIT {
    private static MySqlTestDatabase database;
    @BeforeAll
    static void connect() { database = new MySqlTestDatabase(); }
    @AfterAll
    static void cleanUp() { if (database != null) { database.close(); } }

    @Test
    void readinessDetectsLostDatabaseCredentialsAndRecoversWithoutRestarting() throws Exception {
        var company = database.createCompany("readiness");
        try (var app = company.start("--spring.datasource.hikari.connection-timeout=500")) {
            var api = MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).apply(springSecurity()).build();
            api.perform(get("/api/health/readiness")).andExpect(status().isOk())
                    .andExpect(content().json("{\"status\":\"UP\"}"));
            var pool = app.getBean(HikariDataSource.class);
            String originalPassword = pool.getPassword();
            try {
                pool.setPassword("invalid-disposable-test-password");
                pool.getHikariPoolMXBean().softEvictConnections();
                api.perform(get("/api/health/readiness")).andExpect(status().isServiceUnavailable())
                        .andExpect(content().json("{\"status\":\"DOWN\"}"))
                        .andExpect(jsonPath("$.components").doesNotExist())
                        .andExpect(jsonPath("$.details").doesNotExist());
                api.perform(get("/api/health")).andExpect(status().isOk());
                api.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
            } finally {
                pool.setPassword(originalPassword);
                pool.getHikariPoolMXBean().softEvictConnections();
            }
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            int statusCode;
            do {
                statusCode = api.perform(get("/api/health/readiness")).andReturn().getResponse().getStatus();
                if (statusCode == 200) { break; }
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            assertEquals(200, statusCode, "Readiness did not recover after restoring the restricted test credentials.");
        }
    }

    @Test
    void readinessAlsoRespectsApplicationStartupAndShutdownState() throws Exception {
        var company = database.createCompany("appstate");
        try (var app = company.start()) {
            var api = MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).apply(springSecurity()).build();
            AvailabilityChangeEvent.publish(app, ReadinessState.REFUSING_TRAFFIC);
            api.perform(get("/api/health/readiness")).andExpect(status().isServiceUnavailable());
            api.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
            AvailabilityChangeEvent.publish(app, ReadinessState.ACCEPTING_TRAFFIC);
            api.perform(get("/api/health/readiness")).andExpect(status().isOk());
        }
    }
}
