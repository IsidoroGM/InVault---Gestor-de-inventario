package com.invault.inventory.locations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO used to receive data when creating or updating a Location.
 *
 * This class represents the input sent by the client.
 * It avoids exposing the Location JPA entity directly through the API.
 */
public class LocationRequestDTO {

    // Human-readable location name, for example: "Main Warehouse", "Shelf A1", "Production Area".
    @NotBlank(message = "Location name is required.")
    @Size(max = 100, message = "Location name cannot exceed 100 characters.")
    private String name;

    // Optional description for internal clarification.
    @Size(max = 255, message = "Location description cannot exceed 255 characters.")
    private String description;

    // Optional active flag. If null, the service/entity can keep its default value.
    private Boolean active;

    public LocationRequestDTO() {
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
 * LocationRequestDTO is used as the input model for Location operations.
 * Later, controllers will receive this DTO instead of receiving the Location entity directly.
 * Validation annotations help reject invalid requests before reaching the service layer.
 */