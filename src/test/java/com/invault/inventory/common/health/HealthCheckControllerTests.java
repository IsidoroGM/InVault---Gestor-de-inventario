package com.invault.inventory.common.health;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;

class HealthCheckControllerTests {
    @Test
    void unknownOrMissingReadinessNeverClaimsAvailability() {
        var health = mock(HealthEndpoint.class);
        var controller = new HealthCheckController(health);
        assertEquals(503, controller.readiness().getStatusCode().value());
        var descriptor = mock(IndicatedHealthDescriptor.class);
        when(health.healthForPath("readiness")).thenReturn(descriptor);
        for (Status status : new Status[] {Status.UNKNOWN, Status.DOWN, Status.OUT_OF_SERVICE}) {
            when(descriptor.getStatus()).thenReturn(status);
            var response = controller.readiness();
            assertEquals(503, response.getStatusCode().value());
            assertEquals("DOWN", response.getBody().status());
        }
    }
}
