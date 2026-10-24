package com.invault.inventory.realtime;

/** An immutable notification for batch changes that can affect inventory availability. */
public record BatchChangedEvent(InventoryEventDTO payload) {

    public BatchChangedEvent {
        if (payload == null) {
            throw new IllegalArgumentException("Inventory event payload is required.");
        }
    }
}
