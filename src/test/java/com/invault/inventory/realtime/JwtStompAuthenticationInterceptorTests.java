package com.invault.inventory.realtime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtStompAuthenticationInterceptorTests {

    private JwtDecoder jwtDecoder;
    private JwtAuthenticationConverter authenticationConverter;
    private JwtStompAuthenticationInterceptor interceptor;
    private MessageChannel channel;
    private AuthenticatedWebSocketSessionRegistry sessionRegistry;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        authenticationConverter = mock(JwtAuthenticationConverter.class);
        sessionRegistry = mock(AuthenticatedWebSocketSessionRegistry.class);
        interceptor = new JwtStompAuthenticationInterceptor(
                jwtDecoder,
                authenticationConverter,
                sessionRegistry
        );
        channel = mock(MessageChannel.class);
    }

    @Test
    void connectAuthenticatesBearerTokenAndStoresPrincipal() {
        Jwt jwt = testJwt(false);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_READ_ONLY"))
        );

        when(jwtDecoder.decode("signed-token")).thenReturn(jwt);
        when(authenticationConverter.convert(jwt)).thenReturn(authentication);

        Message<?> message = connectMessage("Bearer signed-token");
        interceptor.preSend(message, channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        assertSame(authentication, accessor.getUser());
        verify(sessionRegistry).registerAuthentication("session-1", 99L);
    }

    @Test
    void connectRejectsMissingOrInvalidBearerTokens() {
        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(connectMessage(null), channel)
        );

        when(jwtDecoder.decode("invalid-token")).thenThrow(new JwtException("invalid"));

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(connectMessage("Bearer invalid-token"), channel)
        );
    }

    @Test
    void connectRejectsSessionThatRequiresPasswordChange() {
        Jwt jwt = testJwt(true);
        when(jwtDecoder.decode("signed-token")).thenReturn(jwt);

        assertThrows(
                BadCredentialsException.class,
                () -> interceptor.preSend(connectMessage("Bearer signed-token"), channel)
        );
    }

    private Message<byte[]> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        if (authorizationHeader != null) {
            accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt testJwt(boolean mustChangePassword) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject("realtime-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("userId", 99L)
                .claim("roles", List.of("READ_ONLY"))
                .claim("mustChangePassword", mustChangePassword)
                .build();
    }
}

/*
 * JwtStompAuthenticationInterceptorTests cover both accepted signed identities
 * and rejected missing or invalid CONNECT credentials.
 */
