package com.invault.inventory.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class AuthenticatedWebSocketSessionRegistryTests {

    @Test
    void revocationClosesEveryOpenSessionForTheUserOnly() throws Exception {
        AuthenticatedWebSocketSessionRegistry registry =
                new AuthenticatedWebSocketSessionRegistry();
        WebSocketSession first = session("session-1", true);
        WebSocketSession second = session("session-2", true);
        WebSocketSession otherUser = session("session-3", true);

        registry.registerTransport(first);
        registry.registerTransport(second);
        registry.registerTransport(otherUser);
        registry.registerAuthentication("session-1", 7L);
        registry.registerAuthentication("session-2", 7L);
        registry.registerAuthentication("session-3", 9L);

        registry.closeUserSessions(7L);

        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(second).close(CloseStatus.POLICY_VIOLATION);
        verify(otherUser, never()).close(any(CloseStatus.class));
    }

    private WebSocketSession session(String id, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(open);
        return session;
    }
}
