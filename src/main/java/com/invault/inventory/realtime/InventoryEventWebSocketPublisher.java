package com.invault.inventory.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InventoryEventWebSocketPublisher {

    public static final String INVENTORY_TOPIC = "/topic/inventory";

    private final SimpMessagingTemplate messagingTemplate;

    public InventoryEventWebSocketPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishStockMovement(StockMovementRecordedEvent event) {
        messagingTemplate.convertAndSend(INVENTORY_TOPIC, event.payload());
    }
}

/*
 * InventoryEventWebSocketPublisher broadcasts only after the database transaction
 * commits successfully, preventing clients from observing rolled-back stock data.
 */
