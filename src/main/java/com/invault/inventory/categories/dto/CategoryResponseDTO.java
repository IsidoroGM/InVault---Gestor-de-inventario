package com.invault.inventory.categories.dto;

import java.time.LocalDateTime;

/**
 * DTO used to return Category data to the client.
 *
 * This class represents the output sent by the API.
 * It allows us to control exactly which fields are exposed.
 */
public class CategoryResponseDTO {

    private Long id;
    private String name;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CategoryResponseDTO() {
    }

    public CategoryResponseDTO(
            Long id,
            String name,
            String description,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
 * CategoryResponseDTO is used as the output model for Category operations.
 * It protects the API from exposing the JPA entity directly and gives us a stable response format
 * for future Angular screens, tables and forms.
 */