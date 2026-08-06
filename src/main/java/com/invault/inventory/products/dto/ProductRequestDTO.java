package com.invault.inventory.products.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO used to receive data when creating or updating a Product.
 *
 * This class represents the input sent by the client.
 * It avoids exposing the Product JPA entity directly through the API.
 */
public class ProductRequestDTO {

    // Internal unique SKU used to identify the product inside InVault.
    @NotBlank(message = "Product SKU is required.")
    @Size(max = 80, message = "Product SKU cannot exceed 80 characters.")
    private String sku;

    // Human-readable product name.
    @NotBlank(message = "Product name is required.")
    @Size(max = 150, message = "Product name cannot exceed 150 characters.")
    private String name;

    // Optional product description.
    @Size(max = 500, message = "Product description cannot exceed 500 characters.")
    private String description;

    // Category assigned to the product.
    @NotNull(message = "Category id is required.")
    private Long categoryId;

    // Main location assigned to the product.
    @NotNull(message = "Location id is required.")
    private Long locationId;

    // Unit of measure assigned to the product.
    @NotNull(message = "Unit id is required.")
    private Long unitId;

    // Minimum stock used later to detect low stock situations.
    @NotNull(message = "Minimum stock is required.")
    @DecimalMin(value = "0.000", message = "Minimum stock cannot be negative.")
    private BigDecimal minimumStock;

    // Optional active flag. If null, the entity can keep its default value.
    private Boolean active;

    public ProductRequestDTO() {
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    } 

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    } 

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    } 

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    } 

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    } 

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(BigDecimal minimumStock) {
        this.minimumStock = minimumStock;
    } 

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

/*
 * Explanation:
 * ProductRequestDTO is used as the input model for Product operations.
 * Instead of receiving full Category, Location or Unit objects, it receives their ids.
 * Later, ProductService will use those ids to load the related entities from the database.
 */