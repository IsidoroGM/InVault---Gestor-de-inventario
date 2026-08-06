package com.invault.inventory.units.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO used to receive data when creating or updating a Unit.
 *
 * This class represents the input sent by the client.
 * It avoids exposing the Unit JPA entity directly through the API.
 */
public class UnitRequestDTO {

    // Technical code of the unit, for example: "KG", "PCS", "L".
    @NotBlank(message = "Unit code is required.")
    @Size(max = 30, message = "Unit code cannot exceed 30 characters.")
    private String code;

    // Human-readable unit name, for example: "Kilogram", "Pieces", "Liter".
    @NotBlank(message = "Unit name is required.")
    @Size(max = 100, message = "Unit name cannot exceed 100 characters.")
    private String name;

    // Short symbol displayed in the UI, for example: "kg", "pcs", "L".
    @Size(max = 20, message = "Unit symbol cannot exceed 20 characters.")
    private String symbol;

    // Optional description for internal clarification.
    @Size(max = 255, message = "Unit description cannot exceed 255 characters.")
    private String description;

    // Optional active flag. If null, the service/entity can keep its default value.
    private Boolean active;

    public UnitRequestDTO() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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
}

/*
 * Explanation:
 * UnitRequestDTO is used as the input model for Unit operations.
 * Later, controllers will receive this DTO instead of receiving the Unit entity directly.
 * Validation annotations help reject invalid requests before reaching the service layer.
 */