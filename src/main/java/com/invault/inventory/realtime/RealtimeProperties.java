package com.invault.inventory.realtime;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invault.realtime")
public record RealtimeProperties(List<String> allowedOrigins) {

    public RealtimeProperties {
        if (allowedOrigins == null) {
            throw new IllegalStateException("At least one realtime allowed origin is required.");
        }

        allowedOrigins = allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException("At least one realtime allowed origin is required.");
        }
    }
}

/*
 * RealtimeProperties keeps browser origins outside the compiled application so
 * every environment can explicitly decide which frontend may open WebSockets.
 */
