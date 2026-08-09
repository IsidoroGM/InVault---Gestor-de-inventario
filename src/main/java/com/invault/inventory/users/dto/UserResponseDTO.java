package com.invault.inventory.users.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.invault.inventory.roles.RoleName;

public record UserResponseDTO(
        Long id,
        String username,
        String email,
        boolean active,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<RoleName> roles) {
}
