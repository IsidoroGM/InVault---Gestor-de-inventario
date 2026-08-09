package com.invault.inventory.audit.dto;

import java.time.LocalDateTime;

import com.invault.inventory.audit.AuditAction;

public record AuditLogResponseDTO(
        Long id,
        Long userId,
        String username,
        AuditAction action,
        String entityName,
        Long entityId,
        String details,
        String clientIp,
        LocalDateTime createdAt) {
}
