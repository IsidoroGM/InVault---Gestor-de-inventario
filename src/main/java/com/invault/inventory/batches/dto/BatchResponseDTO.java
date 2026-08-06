package com.invault.inventory.batches.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.invault.inventory.batches.BatchStatus;

/**
 * DTO used to return Batch data to the client.
 *
 * It includes batch information and lightweight Product data.
 */
public class BatchResponseDTO {

    private Long id;

    private Long productId;
    private String productSku;
    private String productName;

    private String batchCode;
    private BigDecimal quantity;
    private BatchStatus status;
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BatchResponseDTO() {
    }

    public BatchResponseDTO(
            Long id,
            Long productId,
            String productSku,
            String productName,
            String batchCode,
            BigDecimal quantity,
            BatchStatus status,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.batchCode = batchCode;
        this.quantity = quantity;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

/*
 * Explanation:
 * BatchResponseDTO is used as the output model for Batch operations.
 * It exposes the batch information and basic Product data without returning
 * the full Product JPA entity.
 */