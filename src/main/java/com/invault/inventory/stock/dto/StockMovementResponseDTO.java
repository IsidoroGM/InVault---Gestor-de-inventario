package com.invault.inventory.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.invault.inventory.stock.MovementType;

/**
 * DTO used to return StockMovement data to the client.
 *
 * It exposes the movement information and lightweight data from related entities.
 */
public class StockMovementResponseDTO {

    private Long id;

    private Long productId;
    private String productSku;
    private String productName;

    private Long batchId;
    private String batchCode;

    private Long userId;
    private String username;

    private Long supplierId;
    private String supplierName;

    private MovementType movementType;
    private BigDecimal quantity;
    private BigDecimal previousBatchQuantity;
    private BigDecimal newBatchQuantity;
    private String reason;
    private LocalDateTime movementDate;

    public StockMovementResponseDTO() {
    }

    public StockMovementResponseDTO(
            Long id,
            Long productId,
            String productSku,
            String productName,
            Long batchId,
            String batchCode,
            Long userId,
            String username,
            Long supplierId,
            String supplierName,
            MovementType movementType,
            BigDecimal quantity,
            BigDecimal previousBatchQuantity,
            BigDecimal newBatchQuantity,
            String reason,
            LocalDateTime movementDate
    ) {
        this.id = id;
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.batchId = batchId;
        this.batchCode = batchCode;
        this.userId = userId;
        this.username = username;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.movementType = movementType;
        this.quantity = quantity;
        this.previousBatchQuantity = previousBatchQuantity;
        this.newBatchQuantity = newBatchQuantity;
        this.reason = reason;
        this.movementDate = movementDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
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

    public BigDecimal getPreviousBatchQuantity() {
        return previousBatchQuantity;
    }

    public void setPreviousBatchQuantity(BigDecimal previousBatchQuantity) {
        this.previousBatchQuantity = previousBatchQuantity;
    }

    public BigDecimal getNewBatchQuantity() {
        return newBatchQuantity;
    }

    public void setNewBatchQuantity(BigDecimal newBatchQuantity) {
        this.newBatchQuantity = newBatchQuantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getMovementDate() {
        return movementDate;
    }

    public void setMovementDate(LocalDateTime movementDate) {
        this.movementDate = movementDate;
    }
}

/*
 * Explanation:
 * StockMovementResponseDTO is used as the output model for stock movement operations.
 * It exposes traceability data such as previous quantity, new quantity, user, product,
 * batch, supplier and movement date without returning full JPA entities.
 */