package com.invault.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic Spring Boot context test.
 * It checks that the application can start correctly during automated tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class InVaultBackendApplicationTests {

    @Test
    void contextLoads() {
        // If the Spring context starts, this test passes.
    }
}

/*
 * This test verifies that the InVault backend application context loads without errors.
 * It is useful to detect broken configuration, missing beans or startup problems.
 */
