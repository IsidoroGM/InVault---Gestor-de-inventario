package com.invault.inventory.stock.dto;

import java.math.BigDecimal;

import com.invault.inventory.stock.MovementType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO used to receive data when creating a stock movement.
 *
 * The client sends only the data required to request an inventory operation.
 * The service layer will calculate previous/new quantities and will validate business rules.
 */
public class StockMovementRequestDTO {

    // Product affected by the stock movement.
    @NotNull(message = "Product id is required.")
    private Long productId;

    // Batch affected by the stock movement.
    @NotNull(message = "Batch id is required.")
    private Long batchId;

    // User responsible for the operation.
    // Later, when JWT is implemented, this can come from the authenticated user instead.
    @NotNull(message = "User id is required.")
    private Long userId;

    // Optional supplier, mainly used for INBOUND movements.
    private Long supplierId;

    // Movement type: INBOUND, OUTBOUND, POSITIVE_ADJUSTMENT or NEGATIVE_ADJUSTMENT.
    @NotNull(message = "Movement type is required.")
    private MovementType movementType;

    // Quantity moved. It must always be positive; movementType decides if stock increases or decreases.
    @NotNull(message = "Movement quantity is required.")
    @DecimalMin(value = "0.001", message = "Movement quantity must be greater than zero.")
    private BigDecimal quantity;

    // Optional explanation for the stock movement.
    @Size(max = 255, message = "Reason cannot exceed 255 characters.")
    private String reason;

    public StockMovementRequestDTO() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

/*
 * Explanation:
 * StockMovementRequestDTO is used as the input model for stock operations.
 * It does not include previousBatchQuantity, newBatchQuantity or movementDate because those values
 * must be calculated and assigned by StockService to preserve stock traceability.
 */