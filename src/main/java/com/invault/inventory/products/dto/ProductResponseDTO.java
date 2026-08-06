package com.invault.inventory.products.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO used to return Product data to the client.
 *
 * This class represents the output sent by the API.
 * It includes basic information about related Category, Location and Unit.
 */
public class ProductResponseDTO {

    private Long id;
    private String sku;
    private String name;
    private String description;

    private Long categoryId;
    private String categoryName;

    private Long locationId;
    private String locationName;

    private Long unitId;
    private String unitCode;
    private String unitName;
    private String unitSymbol;

    private BigDecimal minimumStock;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProductResponseDTO() {
    }

    public ProductResponseDTO(
            Long id,
            String sku,
            String name,
            String description,
            Long categoryId,
            String categoryName,
            Long locationId,
            String locationName,
            Long unitId,
            String unitCode,
            String unitName,
            String unitSymbol,
            BigDecimal minimumStock,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.locationId = locationId;
        this.locationName = locationName;
        this.unitId = unitId;
        this.unitCode = unitCode;
        this.unitName = unitName;
        this.unitSymbol = unitSymbol;
        this.minimumStock = minimumStock;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    } 

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    } 

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    } 

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    } 

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    } 

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    } 

    public String getUnitSymbol() {
        return unitSymbol;
    }

    public void setUnitSymbol(String unitSymbol) {
        this.unitSymbol = unitSymbol;
    } 

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(BigDecimal minimumStock) {
        this.minimumStock = minimumStock;
    } 

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
 * ProductResponseDTO is used as the output model for Product operations.
 * It exposes useful product information and lightweight related data without returning
 * full JPA relationships directly to the client.
 */