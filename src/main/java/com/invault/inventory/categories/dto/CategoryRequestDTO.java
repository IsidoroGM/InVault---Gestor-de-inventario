package com.invault.inventory.categories.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO used to receive data when creating or updating a Category.
 *
 * This class represents the input sent by the client.
 * It avoids exposing the Category JPA entity directly through the API.
 */
public class CategoryRequestDTO {

    // Human-readable category name, for example: "Raw Material", "Spare Parts", "Packaging".
    @NotBlank(message = "Category name is required.")
    @Size(max = 100, message = "Category name cannot exceed 100 characters.")
    private String name;

    // Optional description for internal clarification.
    @Size(max = 255, message = "Category description cannot exceed 255 characters.")
    private String description;

    // Optional active flag. If null, the service/entity can keep its default value.
    private Boolean active;

    public CategoryRequestDTO() {
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

/*
 * Explanation:
 * CategoryRequestDTO is used as the input model for Category operations.
 * Later, controllers will receive this DTO instead of receiving the Category entity directly.
 * Validation annotations help reject invalid requests before reaching the service layer.
 */