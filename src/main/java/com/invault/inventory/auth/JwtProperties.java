package com.invault.inventory.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invault.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration expiration
) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret is required.");
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer is required.");
        }

        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("JWT expiration must be greater than zero.");
        }
    }
}

/*
 * JwtProperties binds the external JWT settings. The signing secret is supplied
 * by an environment-specific profile and is never embedded in production code.
 */
