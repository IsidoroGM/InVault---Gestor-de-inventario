package com.invault.inventory.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WebSocketSessionRevocationListener {

    private final AuthenticatedWebSocketSessionRegistry sessionRegistry;

    public WebSocketSessionRevocationListener(
            AuthenticatedWebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionsRevoked(UserSessionsRevokedEvent event) {
        sessionRegistry.closeUserSessions(event.userId());
    }
}
