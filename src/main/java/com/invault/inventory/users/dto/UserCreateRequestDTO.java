package com.invault.inventory.users.dto;

import java.util.Set;

import com.invault.inventory.roles.RoleName;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UserCreateRequestDTO(
        @NotBlank(message = "Username is required.")
        @Size(max = 50, message = "Username cannot exceed 50 characters.")
        String username,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email format is invalid.")
        @Size(max = 120, message = "Email cannot exceed 120 characters.")
        String email,

        @NotBlank(message = "Temporary password is required.")
        @Size(min = 12, max = 72, message = "Temporary password must contain between 12 and 72 characters.")
        String temporaryPassword,

        @NotEmpty(message = "At least one role is required.")
        Set<RoleName> roles) {
}
