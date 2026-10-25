package com.invault.inventory.common.health;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {
    private final HealthEndpoint health;

    public HealthCheckController(HealthEndpoint health) {
        this.health = health;
    }

    @GetMapping("/api/health")
    public String healthCheck() {
        return "InVault backend is running";
    }

    @GetMapping("/api/health/readiness")
    public ResponseEntity<ReadinessResponse> readiness() {
        var descriptor = health.healthForPath("readiness");
        boolean ready = descriptor != null && Status.UP.equals(descriptor.getStatus());
        return ResponseEntity.status(ready ? 200 : 503).cacheControl(CacheControl.noStore())
                .body(new ReadinessResponse(ready ? "UP" : "DOWN"));
    }

    // Public probes deliberately omit contributor details, database URLs and exception messages.
    public record ReadinessResponse(String status) {}
}
