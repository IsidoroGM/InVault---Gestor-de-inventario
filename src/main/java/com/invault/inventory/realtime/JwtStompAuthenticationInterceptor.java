package com.invault.inventory.realtime;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Component
public class JwtStompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter authenticationConverter;

    public JwtStompAuthenticationInterceptor(
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter authenticationConverter) {

        this.jwtDecoder = jwtDecoder;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
        }

        return message;
    }

    private AbstractAuthenticationToken authenticate(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BadCredentialsException("A valid bearer token is required for WebSocket connections.");
        }

        String tokenValue = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(tokenValue)) {
            throw new BadCredentialsException("A valid bearer token is required for WebSocket connections.");
        }

        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            if (Boolean.TRUE.equals(jwt.getClaim("mustChangePassword"))) {
                throw new BadCredentialsException(
                        "Password change is required before opening a WebSocket connection.");
            }
            AbstractAuthenticationToken authentication = authenticationConverter.convert(jwt);

            if (authentication == null || !authentication.isAuthenticated()) {
                throw new BadCredentialsException("WebSocket authentication failed.");
            }

            return authentication;
        } catch (JwtException exception) {
            throw new BadCredentialsException("WebSocket authentication failed.", exception);
        }
    }
}

/*
 * Browser WebSocket APIs cannot add a custom HTTP Authorization header. This
 * interceptor therefore validates the bearer JWT supplied by Angular in STOMP CONNECT.
 */
