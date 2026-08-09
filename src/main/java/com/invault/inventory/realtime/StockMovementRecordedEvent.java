package com.invault.inventory.realtime;

public record StockMovementRecordedEvent(InventoryEventDTO payload) {

    public StockMovementRecordedEvent {
        if (payload == null) {
            throw new IllegalArgumentException("Inventory event payload is required.");
        }
    }
}

/*
 * StockMovementRecordedEvent crosses the transaction boundary without retaining
 * JPA entities or lazy persistence references.
 */
