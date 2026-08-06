package com.invault.inventory.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.invault.inventory.auth.JwtProperties;
import com.invault.inventory.auth.SecurityBootstrapProperties;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, SecurityBootstrapProperties.class})
public class JwtConfig {

    private static final int MINIMUM_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] decodedSecret;

        try {
            decodedSecret = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT secret must be valid Base64.", exception);
        }

        if (decodedSecret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes.");
        }

        return new SecretKeySpec(decodedSecret, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(properties.issuer())
        ));

        return decoder;
    }
}

/*
 * JwtConfig creates matching HS256 encoder and decoder beans from one external
 * secret and validates issuer, lifetime and signature on incoming bearer tokens.
 */
