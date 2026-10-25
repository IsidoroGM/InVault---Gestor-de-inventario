package com.invault.inventory.products;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.invault.inventory.categories.Category;
import com.invault.inventory.locations.Location;
import com.invault.inventory.units.Unit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entity that represents an inventory product.
 *
 * Products are the main items managed by InVault.
 * Each product has an internal SKU, a category, a main location and a unit of measurement.
 */
@Entity
@Table(name = "products")
public class Product {
    @Column(name = "unit_locked", nullable = false)
    private boolean unitLocked;

    public boolean isUnitLocked() {
        return unitLocked;
    }

    public void lockUnit() {
        unitLocked = true;
    }

    // Primary key generated automatically by the database.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Internal product code.
     *
     * This SKU is created and managed by the company.
     * It must be unique because it identifies the product inside InVault.
     */
    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    /*
     * Product name shown in the application.
     *
     * Example:
     * - M8 Screw
     * - Industrial Oil
     * - Medium Cardboard Box
     */
    @Column(nullable = false, length = 150)
    private String name;

    // Optional product description with extra information.
    @Column(length = 500)
    private String description;

    /*
     * Product category.
     *
     * Example:
     * - Raw Material
     * - Spare Part
     * - Packaging
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /*
     * Main product location.
     *
     * This represents the default or principal place where the product is stored.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    /*
     * Unit of measurement used by this product.
     *
     * Example:
     * - PCS
     * - KG
     * - L
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    /*
     * Minimum desired stock level.
     *
     * This value will be used later to detect low stock situations.
     */
    @Column(name = "minimum_stock", nullable = false, precision = 15, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    /*
     * Allows disabling a product without deleting it physically from the database.
     *
     * This is important because products can be linked to batches and stock movements.
     */
    @Column(nullable = false)
    private Boolean active = true;

    // Timestamp automatically assigned when the product is first created.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Timestamp automatically updated whenever the product changes.
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Product() {
        // Empty constructor required by JPA.
    }

    public Product(
            String sku,
            String name,
            String description,
            Category category,
            Location location,
            Unit unit,
            BigDecimal minimumStock
    ) {
        this.sku = normalizeSku(sku);
        this.name = normalizeName(name);
        this.description = description;
        this.category = category;
        this.location = location;
        this.unit = unit;
        this.minimumStock = minimumStock != null ? minimumStock : BigDecimal.ZERO;
        this.active = true;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();

        // Defensive default in case the entity is created without active value.
        if (this.active == null) {
            this.active = true;
        }

        // Defensive default in case the product is created without minimum stock.
        if (this.minimumStock == null) {
            this.minimumStock = BigDecimal.ZERO;
        }

        // Keeps product data consistent before inserting into the database.
        this.sku = normalizeSku(this.sku);
        this.name = normalizeName(this.name);
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();

        // Defensive default in case minimum stock is accidentally set to null.
        if (this.minimumStock == null) {
            this.minimumStock = BigDecimal.ZERO;
        }

        // Keeps product data consistent before updating the database.
        this.sku = normalizeSku(this.sku);
        this.name = normalizeName(this.name);
    }

    /*
     * Normalizes the SKU to avoid duplicated formats.
     *
     * Example:
     * " sku-001 " becomes "SKU-001".
     */
    private String normalizeSku(String sku) {
        return sku == null ? null : sku.trim().toUpperCase();
    }

    /*
     * Normalizes the product name by removing unnecessary spaces.
     *
     * Example:
     * "  M8 Screw  " becomes "M8 Screw".
     */
    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = normalizeSku(sku);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = normalizeName(name);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public BigDecimal getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(BigDecimal minimumStock) {
        this.minimumStock = minimumStock != null ? minimumStock : BigDecimal.ZERO;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

/*
 * The Product entity represents an inventory item managed by InVault.
 * It stores the product identity, classification, main location, unit of measurement
 * and minimum stock configuration. The real available stock will be calculated later
 * from the product batches instead of being stored directly in this entity.
 */
