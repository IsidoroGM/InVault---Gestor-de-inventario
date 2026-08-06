package com.invault.inventory.realtime;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.invault.inventory.stock.MovementType;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;

public record InventoryEventDTO(
        InventoryEventType eventType,
        LocalDateTime occurredAt,
        Long movementId,
        Long productId,
        String productSku,
        Long batchId,
        String batchCode,
        MovementType movementType,
        BigDecimal quantity,
        BigDecimal previousBatchQuantity,
        BigDecimal newBatchQuantity) {

    public static InventoryEventDTO stockUpdated(StockMovementResponseDTO movement) {
        if (movement == null) {
            throw new IllegalArgumentException("Stock movement response is required.");
        }

        return new InventoryEventDTO(
                InventoryEventType.STOCK_UPDATED,
                movement.getMovementDate(),
                movement.getId(),
                movement.getProductId(),
                movement.getProductSku(),
                movement.getBatchId(),
                movement.getBatchCode(),
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getPreviousBatchQuantity(),
                movement.getNewBatchQuantity()
        );
    }
}

/*
 * InventoryEventDTO is an immutable, entity-free payload. It contains only the
 * inventory data clients need to refresh their product, batch and dashboard views.
 */
