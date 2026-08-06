package com.invault.inventory.batches.dto;

import com.invault.inventory.batches.BatchStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO used to receive data when creating or updating a Batch.
 *
 * A batch represents a specific stock group linked to a Product.
 * The client sends productId instead of sending the full Product entity.
 */
public class BatchRequestDTO {

    // Product assigned to this batch.
    @NotNull(message = "Product id is required.")
    private Long productId;

    // Manual unique batch code.
    @NotBlank(message = "Batch code is required.")
    @Size(max = 100, message = "Batch code cannot exceed 100 characters.")
    private String batchCode;

    // Optional batch status. If null, the entity can keep its default value.
    private BatchStatus status;

    // Optional internal notes.
    @Size(max = 255, message = "Batch notes cannot exceed 255 characters.")
    private String notes;

    public BatchRequestDTO() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
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
}

/*
 * Explanation:
 * BatchRequestDTO is used as the input model for Batch operations.
 * It receives productId instead of a full Product object because the service layer
 * will be responsible for loading the related Product from the database. Quantity
 * is intentionally excluded because stock can only change through StockService.
 */
