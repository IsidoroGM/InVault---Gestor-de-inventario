package com.invault.inventory.auth.dto;

import java.time.Instant;
import java.util.List;

public record LoginResponseDTO(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        Long userId,
        String username,
        List<String> roles,
        boolean mustChangePassword
) {
}

/*
 * LoginResponseDTO gives Angular the bearer token and the minimum identity data
 * required to initialize the authenticated session without exposing User.
 */
