package com.invault.inventory.config;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.invault.inventory.auth.ApiAccessDeniedHandler;
import com.invault.inventory.auth.ApiAuthenticationEntryPoint;
import com.invault.inventory.auth.InVaultUserDetailsService;
import com.invault.inventory.auth.MandatoryPasswordChangeFilter;
import com.invault.inventory.auth.SecurityErrorResponseWriter;


@Configuration
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String SUPERVISOR = "SUPERVISOR";
    private static final String WAREHOUSE = "WAREHOUSE";
    private static final String READ_ONLY = "READ_ONLY";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityErrorResponseWriter errorResponseWriter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) throws Exception {

        MandatoryPasswordChangeFilter mandatoryPasswordChangeFilter =
                new MandatoryPasswordChangeFilter(errorResponseWriter);

        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/health",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/auth/login",
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        // The STOMP CONNECT frame performs JWT authentication.
                        .requestMatchers("/ws", "/ws/**").permitAll()

                        // Every authenticated account can replace its own password.
                        .requestMatchers(HttpMethod.PUT, "/api/users/me/password")
                        .hasAnyRole(ADMIN, SUPERVISOR, WAREHOUSE, READ_ONLY)
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout")
                        .hasAnyRole(ADMIN, SUPERVISOR, WAREHOUSE, READ_ONLY)

                        // User and audit administration is limited to management roles.
                        .requestMatchers(HttpMethod.GET, "/api/users/**", "/api/roles/**", "/api/audit-logs/**")
                        .hasAnyRole(ADMIN, SUPERVISOR)
                        .requestMatchers(HttpMethod.POST, "/api/users")
                        .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/users/**")
                        .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/users/**")
                        .hasRole(ADMIN)

                        // Every official role can consult inventory information.
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyRole(ADMIN, SUPERVISOR, WAREHOUSE, READ_ONLY)

                        // Warehouse staff can execute auditable stock movements.
                        .requestMatchers(HttpMethod.POST, "/api/stock/movements")
                        .hasAnyRole(ADMIN, SUPERVISOR, WAREHOUSE)

                        // Catalogue and product maintenance is restricted to management roles.
                        .requestMatchers(HttpMethod.POST,
                                "/api/units",
                                "/api/categories",
                                "/api/locations",
                                "/api/suppliers",
                                "/api/products",
                                "/api/batches")
                        .hasAnyRole(ADMIN, SUPERVISOR)
                        .requestMatchers(HttpMethod.PUT,
                                "/api/units/**",
                                "/api/categories/**",
                                "/api/locations/**",
                                "/api/suppliers/**",
                                "/api/products/**",
                                "/api/batches/**")
                        .hasAnyRole(ADMIN, SUPERVISOR)
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/units/**",
                                "/api/categories/**",
                                "/api/locations/**",
                                "/api/suppliers/**",
                                "/api/products/**")
                        .hasAnyRole(ADMIN, SUPERVISOR)

                        // New endpoints must receive an explicit rule before becoming accessible.
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .addFilterAfter(
                        mandatoryPasswordChangeFilter,
                        BearerTokenAuthenticationFilter.class
                )
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            InVaultUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider authenticationProvider =
                new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwtRoleAuthoritiesConverter());
        return authenticationConverter;
    }

    private Converter<Jwt, Collection<GrantedAuthority>> jwtRoleAuthoritiesConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");

            if (roles == null) {
                return List.of();
            }

            return roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        };
    }
}

/*
 * SecurityConfig makes the REST API stateless, validates bearer JWTs and maps
 * the four official InVault roles to explicit read and write permissions.
 */
