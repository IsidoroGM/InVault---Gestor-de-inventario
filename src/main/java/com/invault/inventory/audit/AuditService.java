package com.invault.inventory.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@Service
@Transactional
public class AuditService {

    private static final int ENTITY_NAME_MAX_LENGTH = 100;
    private static final int DETAILS_MAX_LENGTH = 500;
    private static final int CLIENT_IP_MAX_LENGTH = 60;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public void registerUserAction(
            Long userId,
            AuditAction action,
            String entityName,
            Long entityId,
            String details,
            String clientIp) {

        User user = findUserEntityById(userId);
        saveAuditLog(user, action, entityName, entityId, details, clientIp);
    }

    public void registerSystemAction(
            AuditAction action,
            String entityName,
            Long entityId,
            String details,
            String clientIp) {

        // Las acciones automáticas no tienen un usuario responsable asociado.
        saveAuditLog(null, action, entityName, entityId, details, clientIp);
    }

    private void saveAuditLog(
            User user,
            AuditAction action,
            String entityName,
            Long entityId,
            String details,
            String clientIp) {

        validateAction(action);
        String normalizedEntityName = normalizeRequiredText(
                entityName,
                "Audit entity name is required.",
                "Audit entity name cannot exceed 100 characters.",
                ENTITY_NAME_MAX_LENGTH
        );
        validateEntityId(entityId);
        String normalizedDetails = normalizeOptionalText(
                details,
                "Audit details cannot exceed 500 characters.",
                DETAILS_MAX_LENGTH
        );
        String normalizedClientIp = normalizeOptionalText(
                clientIp,
                "Client IP cannot exceed 60 characters.",
                CLIENT_IP_MAX_LENGTH
        );

        AuditLog auditLog = new AuditLog(
                user,
                action,
                normalizedEntityName,
                entityId,
                normalizedDetails,
                normalizedClientIp
        );

        auditLogRepository.save(auditLog);
    }

    private User findUserEntityById(Long userId) {
        if (userId == null) {
            throw new BadRequestException("User id is required for a user audit action.");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private void validateAction(AuditAction action) {
        if (action == null) {
            throw new BadRequestException("Audit action is required.");
        }
    }

    private void validateEntityId(Long entityId) {
        if (entityId != null && entityId <= 0) {
            throw new BadRequestException("Audit entity id must be greater than zero.");
        }
    }

    private String normalizeRequiredText(
            String value,
            String requiredMessage,
            String lengthMessage,
            int maxLength) {

        String normalizedValue = normalizeText(value);

        if (normalizedValue == null) {
            throw new BadRequestException(requiredMessage);
        }

        validateTextLength(normalizedValue, maxLength, lengthMessage);
        return normalizedValue;
    }

    private String normalizeOptionalText(String value, String lengthMessage, int maxLength) {
        String normalizedValue = normalizeText(value);

        if (normalizedValue == null) {
            return null;
        }

        validateTextLength(normalizedValue, maxLength, lengthMessage);
        return normalizedValue;
    }

    private void validateTextLength(String value, int maxLength, String message) {
        if (value.length() > maxLength) {
            throw new BadRequestException(message);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        return normalizedValue.isBlank() ? null : normalizedValue;
    }
}

/*
 * AuditService centraliza el registro de acciones relevantes de InVault.
 *
 * Permite guardar acciones realizadas por usuarios y acciones automáticas del
 * sistema, valida los datos antes de persistirlos y mantiene AuditLog dentro de
 * la capa de servicio para no exponer directamente la entidad JPA.
 */
