package com.invault.inventory.realtime;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class AuthenticatedWebSocketSessionRegistry {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuthenticatedWebSocketSessionRegistry.class);

    private final ConcurrentHashMap<String, WebSocketSession> transportSessions =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> usersBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    public void registerTransport(WebSocketSession session) {
        transportSessions.put(session.getId(), session);
    }

    public void registerAuthentication(String sessionId, Long userId) {
        usersBySession.put(sessionId, userId);
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    public void remove(String sessionId) {
        transportSessions.remove(sessionId);
        Long userId = usersBySession.remove(sessionId);
        if (userId == null) {
            return;
        }

        sessionsByUser.computeIfPresent(userId, (ignored, sessionIds) -> {
            sessionIds.remove(sessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
        });
    }

    public void closeUserSessions(Long userId) {
        Set<String> sessionIds = sessionsByUser.remove(userId);
        if (sessionIds == null) {
            return;
        }

        sessionIds.forEach(sessionId -> {
            usersBySession.remove(sessionId);
            WebSocketSession session = transportSessions.remove(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException exception) {
                    LOGGER.warn("Could not close revoked WebSocket session {}", sessionId, exception);
                }
            }
        });
    }
}
