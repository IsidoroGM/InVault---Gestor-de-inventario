package com.invault.inventory.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class WebSocketSessionRevocationListenerTests {

    @Test
    void committedRevocationClosesTheUsersRegisteredSessions() {
        AuthenticatedWebSocketSessionRegistry registry =
                mock(AuthenticatedWebSocketSessionRegistry.class);
        WebSocketSessionRevocationListener listener =
                new WebSocketSessionRevocationListener(registry);

        listener.onSessionsRevoked(new UserSessionsRevokedEvent(7L));

        verify(registry).closeUserSessions(7L);
    }

    @Test
    void listenerIsExplicitlyBoundToSuccessfulCommit() throws Exception {
        Method listener = WebSocketSessionRevocationListener.class.getMethod(
                "onSessionsRevoked",
                UserSessionsRevokedEvent.class
        );
        TransactionalEventListener annotation =
                listener.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }
}
