package com.invault.inventory.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @NotBlank(message = "Username is required.")
        @Size(max = 50, message = "Username cannot exceed 50 characters.")
        String username,

        @NotBlank(message = "Password is required.")
        @Size(max = 200, message = "Password cannot exceed 200 characters.")
        String password
) {
}

/*
 * LoginRequestDTO contains only the credentials needed for authentication and
 * applies request limits before the values reach Spring Security.
 */
