package com.invault.inventory.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "invault.security.login-rate-limit")
public record LoginRateLimitProperties(
        @DefaultValue("10") int accountAttempts,
        @DefaultValue("50") int ipAttempts,
        @DefaultValue("5m") Duration window) {
    public LoginRateLimitProperties {
        if (accountAttempts < 1 || accountAttempts > 10000 || ipAttempts < 1 || ipAttempts > 10000) {
            throw new IllegalArgumentException("Login attempt limits must be between 1 and 10000.");
        }
        if (window == null || window.compareTo(Duration.ofSeconds(1)) < 0
                || window.compareTo(Duration.ofHours(1)) > 0 || window.getNano() != 0) {
            throw new IllegalArgumentException("Login rate-limit window must be 1 to 3600 whole seconds.");
        }
    }
}
