package com.invault.inventory.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.invault.inventory.stock.MovementType;

class InventoryEventWebSocketPublisherTests {

    @Test
    void committedMovementIsBroadcastToInventoryTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        InventoryEventWebSocketPublisher publisher =
                new InventoryEventWebSocketPublisher(messagingTemplate);
        InventoryEventDTO payload = payload();

        publisher.publishStockMovement(new StockMovementRecordedEvent(payload));

        verify(messagingTemplate).convertAndSend(
                InventoryEventWebSocketPublisher.INVENTORY_TOPIC,
                payload
        );
    }

    @Test
    void listenerIsExplicitlyBoundToSuccessfulCommit() throws Exception {
        Method listener = InventoryEventWebSocketPublisher.class.getMethod(
                "publishStockMovement",
                StockMovementRecordedEvent.class
        );
        TransactionalEventListener annotation =
                listener.getAnnotation(TransactionalEventListener.class);

        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
    }

    private InventoryEventDTO payload() {
        return new InventoryEventDTO(
                InventoryEventType.STOCK_UPDATED,
                LocalDateTime.of(2026, 8, 6, 19, 30),
                10L,
                1L,
                "RAW-001",
                2L,
                "LOT-001",
                MovementType.INBOUND,
                new BigDecimal("3.000"),
                new BigDecimal("5.000"),
                new BigDecimal("8.000")
        );
    }
}

/*
 * InventoryEventWebSocketPublisherTests verify the destination, immutable payload
 * and the required after-commit transaction phase.
 */
