package com.invault.inventory.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invault.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .distinct()
                        .toList();

        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("At least one InVault CORS origin must be configured.");
        }
    }
}
