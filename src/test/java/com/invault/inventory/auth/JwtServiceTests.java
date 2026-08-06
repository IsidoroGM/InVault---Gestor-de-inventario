package com.invault.inventory.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.config.JwtConfig;
import com.invault.inventory.roles.Role;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.users.User;

class JwtServiceTests {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void generatedTokenContainsIdentityRolesAndExpiration() {
        JwtProperties properties = new JwtProperties(
                TEST_SECRET,
                "https://api.invault.test",
                Duration.ofMinutes(5)
        );
        JwtConfig jwtConfig = new JwtConfig();
        SecretKey secretKey = jwtConfig.jwtSecretKey(properties);
        JwtEncoder encoder = jwtConfig.jwtEncoder(secretKey);
        JwtDecoder decoder = jwtConfig.jwtDecoder(secretKey, properties);
        JwtService jwtService = new JwtService(encoder, properties);

        User user = new User("warehouse.user", "warehouse@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 17L);
        user.setMustChangePassword(true);
        user.setRoles(Set.of(
                new Role(RoleName.WAREHOUSE, "Warehouse operations"),
                new Role(RoleName.READ_ONLY, "Read access")
        ));

        Instant beforeGeneration = Instant.now();
        JwtService.TokenDetails tokenDetails = jwtService.generateToken(user);
        Jwt decodedToken = decoder.decode(tokenDetails.token());

        assertEquals("warehouse.user", decodedToken.getSubject());
        assertEquals("https://api.invault.test", decodedToken.getIssuer().toString());
        assertEquals(Long.valueOf(17L), decodedToken.getClaim("userId"));
        assertEquals(java.util.List.of("READ_ONLY", "WAREHOUSE"), decodedToken.getClaimAsStringList("roles"));
        assertEquals(Boolean.TRUE, decodedToken.getClaim("mustChangePassword"));
        assertEquals(300, tokenDetails.expiresInSeconds());
        assertTrue(tokenDetails.expiresAt().isAfter(beforeGeneration));
        assertFalse(tokenDetails.token().isBlank());
    }
}

/*
 * JwtServiceTests decode generated tokens with the matching verifier to protect
 * signature, issuer, expiration and authorization claims as one contract.
 */
