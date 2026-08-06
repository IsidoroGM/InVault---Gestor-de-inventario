package com.invault.inventory.config;

import static org.springframework.messaging.simp.SimpMessageType.MESSAGE;
import static org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import com.invault.inventory.realtime.InventoryEventWebSocketPublisher;

@Configuration
public class WebSocketAuthorizationConfig {

    private static final String ADMIN = "ADMIN";
    private static final String SUPERVISOR = "SUPERVISOR";
    private static final String WAREHOUSE = "WAREHOUSE";
    private static final String READ_ONLY = "READ_ONLY";

    @Bean
    public AuthorizationManager<Message<?>> websocketAuthorizationManager() {
        MessageMatcherDelegatingAuthorizationManager.Builder messages =
                MessageMatcherDelegatingAuthorizationManager.builder();

        messages
                .nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers(InventoryEventWebSocketPublisher.INVENTORY_TOPIC)
                .hasAnyRole(ADMIN, SUPERVISOR, WAREHOUSE, READ_ONLY)
                // Clients receive server events but cannot impersonate the backend.
                .simpTypeMatchers(MESSAGE, SUBSCRIBE).denyAll()
                .anyMessage().denyAll();

        return messages.build();
    }
}

/*
 * WebSocketAuthorizationConfig grants authenticated official roles one read-only
 * subscription and denies every client publication or undeclared destination.
 */
