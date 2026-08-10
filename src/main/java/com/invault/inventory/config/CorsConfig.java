package com.invault.inventory.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Global CORS configuration for the backend.
 * Origins come from configuration so development and production can use different frontends.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(properties.allowedOrigins());

        // HTTP methods allowed from the frontend.
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        // Headers allowed in requests from Angular.
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept"
        ));

        // JWT will later be exposed through the Authorization header.
        configuration.setExposedHeaders(List.of("Authorization"));

        // We use JWT headers, not cookies, so credentials stay disabled for now.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Applies this CORS configuration to every endpoint.
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

/*
 * This class defines which frontend origins can call the InVault backend.
 * Development defaults to Angular on localhost; production requires an explicit origin list.
 */
