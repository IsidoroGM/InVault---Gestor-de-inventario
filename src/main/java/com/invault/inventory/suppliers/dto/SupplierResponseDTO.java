package com.invault.inventory.suppliers.dto;

import java.time.LocalDateTime;

/**
 * DTO used to return Supplier data to the client.
 *
 * This class represents the output sent by the API.
 * It allows us to control exactly which fields are exposed.
 */
public class SupplierResponseDTO {

    private Long id;
    private String name;
    private String contactName;
    private String phone;
    private String email;
    private String notes;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SupplierResponseDTO() {
    }

    public SupplierResponseDTO(
            Long id,
            String name,
            String contactName,
            String phone,
            String email,
            String notes,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.contactName = contactName;
        this.phone = phone;
        this.email = email;
        this.notes = notes;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    } 

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    } 

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    } 

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    } 

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
 * SupplierResponseDTO is used as the output model for Supplier operations.
 * It protects the API from exposing the JPA entity directly and gives us a stable response format
 * for future Angular screens, tables and forms.
 */