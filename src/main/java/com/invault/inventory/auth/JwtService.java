package com.invault.inventory.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.invault.inventory.users.User;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public TokenDetails generateToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A persisted user is required to generate a JWT.");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.expiration());
        List<String> roles = activeRoleNames(user);

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("roles", roles)
                .claim("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()))
                .claim("tokenVersion", user.getTokenVersion())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new TokenDetails(token, expiresAt, jwtProperties.expiration().toSeconds());
    }

    public List<String> activeRoleNames(User user) {
        return user.getRoles().stream()
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .map(role -> role.getName().name())
                .sorted()
                .toList();
    }

    public record TokenDetails(String token, Instant expiresAt, long expiresInSeconds) {
    }
}

/*
 * JwtService issues short-lived signed tokens containing the user id, active
 * roles and password-change state. It never places credentials in JWT claims.
 */
