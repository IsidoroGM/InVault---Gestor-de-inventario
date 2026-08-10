package com.invault.inventory.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequestDTO(
        @NotBlank(message = "Temporary password is required.")
        @Size(min = 12, max = 72, message = "Temporary password must contain between 12 and 72 characters.")
        String temporaryPassword) {
}
