package com.invault.inventory.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invault.security.bootstrap-admin")
public record SecurityBootstrapProperties(
        boolean enabled,
        String username,
        String email,
        String password
) {
}

/*
 * SecurityBootstrapProperties keeps first-admin credentials outside the code.
 * Values are read only when the explicit bootstrap flag is enabled.
 */
