package com.invault.inventory.suppliers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO used to receive data when creating or updating a Supplier.
 *
 * This class represents the input sent by the client.
 * It avoids exposing the Supplier JPA entity directly through the API.
 */
public class SupplierRequestDTO {

    // Supplier company or business name.
    @NotBlank(message = "Supplier name is required.")
    @Size(max = 150, message = "Supplier name cannot exceed 150 characters.")
    private String name;

    // Optional contact person name.
    @Size(max = 120, message = "Contact name cannot exceed 120 characters.")
    private String contactName;

    // Optional supplier phone number.
    @Size(max = 30, message = "Phone cannot exceed 30 characters.")
    private String phone;

    // Optional supplier email.
    @Email(message = "Email must have a valid format.")
    @Size(max = 120, message = "Email cannot exceed 120 characters.")
    private String email;

    // Optional internal notes.
    @Size(max = 255, message = "Notes cannot exceed 255 characters.")
    private String notes;

    // Optional active flag. If null, the entity can keep its default value.
    private Boolean active;

    public SupplierRequestDTO() {
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

/*
 * Explanation:
 * SupplierRequestDTO is used as the input model for Supplier operations.
 * Later, controllers will receive this DTO instead of receiving the Supplier entity directly.
 * Validation annotations help reject invalid requests before reaching the service layer.
 */