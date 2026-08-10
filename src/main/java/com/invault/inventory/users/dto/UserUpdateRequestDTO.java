package com.invault.inventory.users.dto;

import java.util.Set;

import com.invault.inventory.roles.RoleName;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UserUpdateRequestDTO(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(max = 120, message = "Email cannot exceed 120 characters.")
        String email,

        @NotEmpty(message = "At least one role is required.")
        Set<RoleName> roles) {
}
