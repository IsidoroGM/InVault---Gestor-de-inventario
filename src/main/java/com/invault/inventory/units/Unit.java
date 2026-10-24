package com.invault.inventory.units;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entity that represents a product unit of measurement.
 *
 * Units define how product quantities are measured inside InVault.
 * Examples: piece, kilogram, liter, meter or box.
 */
@Entity
@Table(name = "units")
public class Unit {
    @Column(name = "identity_locked", nullable = false)
    private boolean identityLocked;

    public boolean isIdentityLocked() {
        return identityLocked;
    }

    public void lockIdentity() {
        identityLocked = true;
    }

    // Primary key generated automatically by the database.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Short technical code for the unit.
     *
     * Examples:
     * - PCS
     * - KG
     * - L
     * - M
     * - BOX
     */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    /*
     * Human-readable unit name.
     *
     * Examples:
     * - Piece
     * - Kilogram
     * - Liter
     */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /*
     * Short visual symbol shown in the interface.
     *
     * Examples:
     * - pcs
     * - kg
     * - L
     * - m
     */
    @Column(length = 20)
    private String symbol;

    // Optional explanation about how or when this unit should be used.
    @Column(length = 255)
    private String description;

    // Allows disabling a unit without deleting it physically from the database.
    @Column(nullable = false)
    private Boolean active = true;

    // Timestamp automatically assigned when the unit is first created.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Timestamp automatically updated whenever the unit changes.
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Unit() {
        // Empty constructor required by JPA.
    }

    public Unit(String code, String name, String symbol, String description) {
        this.code = normalizeCode(code);
        this.name = name;
        this.symbol = symbol;
        this.description = description;
        this.active = true;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();

        // Defensive default in case the entity is created without active value.
        if (this.active == null) {
            this.active = true;
        }

        // Keeps unit codes consistent before inserting into the database.
        this.code = normalizeCode(this.code);
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();

        // Keeps unit codes consistent before updating the database.
        this.code = normalizeCode(this.code);
    }

    /*
     * Normalizes the unit code to avoid duplicated formats.
     *
     * Example:
     * " kg " becomes "KG".
     */
    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = normalizeCode(code);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
 * The Unit entity represents a measurement unit that can be assigned to products.
 * It is stored in the database instead of being an enum so the system can add,
 * disable or modify units without changing the Java source code.
 */
