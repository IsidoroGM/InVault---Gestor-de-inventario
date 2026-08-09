package com.invault.inventory.roles.dto;

import com.invault.inventory.roles.RoleName;

public record RoleResponseDTO(
        Long id,
        RoleName name,
        String description,
        boolean active) {
}
