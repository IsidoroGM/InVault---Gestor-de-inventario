package com.invault.inventory.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;

import com.invault.inventory.realtime.InventoryEventWebSocketPublisher;

class WebSocketAuthorizationConfigTests {

    private AuthorizationManager<Message<?>> authorizationManager;

    @BeforeEach
    void setUp() {
        authorizationManager = new WebSocketAuthorizationConfig()
                .websocketAuthorizationManager();
    }

    @Test
    void everyOfficialRoleCanSubscribeToInventoryEvents() {
        for (String role : new String[] {"ADMIN", "SUPERVISOR", "WAREHOUSE", "READ_ONLY"}) {
            AuthorizationResult result = authorizationManager.authorize(
                    () -> authenticated(role),
                    message(SimpMessageType.SUBSCRIBE, InventoryEventWebSocketPublisher.INVENTORY_TOPIC)
            );

            assertTrue(result.isGranted());
        }
    }

    @Test
    void unauthenticatedAndUndeclaredSubscriptionsAreDenied() {
        AuthorizationResult anonymousResult = authorizationManager.authorize(
                () -> null,
                message(SimpMessageType.SUBSCRIBE, InventoryEventWebSocketPublisher.INVENTORY_TOPIC)
        );
        AuthorizationResult otherTopicResult = authorizationManager.authorize(
                () -> authenticated("ADMIN"),
                message(SimpMessageType.SUBSCRIBE, "/topic/internal")
        );

        assertFalse(anonymousResult.isGranted());
        assertFalse(otherTopicResult.isGranted());
    }

    @Test
    void clientsCannotPublishMessagesToBrokerDestinations() {
        AuthorizationResult result = authorizationManager.authorize(
                () -> authenticated("ADMIN"),
                message(SimpMessageType.MESSAGE, InventoryEventWebSocketPublisher.INVENTORY_TOPIC)
        );

        assertFalse(result.isGranted());
    }

    private Authentication authenticated(String role) {
        return new TestingAuthenticationToken(
                "realtime-user",
                "not-used",
                "ROLE_" + role
        );
    }

    private Message<byte[]> message(SimpMessageType type, String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}

/*
 * WebSocketAuthorizationConfigTests prove that subscriptions are read-only,
 * role-protected and restricted to the declared inventory topic.
 */
