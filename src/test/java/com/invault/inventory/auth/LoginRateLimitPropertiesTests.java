package com.invault.inventory.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginRateLimitPropertiesTests {
    @Test
    void acceptsBoundedWholeSecondWindowsAndBudgets() {
        assertEquals(10, new LoginRateLimitProperties(10, 50, Duration.ofMinutes(5)).accountAttempts());
        assertDoesNotThrow(() -> new LoginRateLimitProperties(1, 10000, Duration.ofSeconds(1)));
    }

    @Test
    void rejectsDisabledExcessiveOrSubsecondPolicies() {
        for (int invalid : new int[] {0, -1, 10001}) {
            assertThrows(IllegalArgumentException.class, () -> new LoginRateLimitProperties(invalid, 50, Duration.ofMinutes(5)));
            assertThrows(IllegalArgumentException.class, () -> new LoginRateLimitProperties(10, invalid, Duration.ofMinutes(5)));
        }
        for (Duration invalid : new Duration[] {null, Duration.ZERO, Duration.ofMillis(1500), Duration.ofHours(2)}) {
            assertThrows(IllegalArgumentException.class, () -> new LoginRateLimitProperties(10, 50, invalid));
        }
    }
}
