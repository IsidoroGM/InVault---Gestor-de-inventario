package com.invault.inventory.audit;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.common.dto.PageResponseDTO;
import com.invault.inventory.audit.dto.AuditLogResponseDTO;
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

    @Transactional(readOnly = true)
    public PageResponseDTO<AuditLogResponseDTO> search(
            AuditAction action,
            String entityName,
            Long userId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size) {

        validateDateRange(fromDate, toDate);
        PageRequest pageable = pageRequest(page, size);
        Page<AuditLogResponseDTO> result = auditLogRepository.search(
                action,
                normalizeText(entityName),
                userId,
                fromDate,
                toDate,
                pageable
        ).map(this::toResponseDTO);

        return PageResponseDTO.from(result);
    }

    @Transactional(readOnly = true)
    public AuditLogResponseDTO findById(Long id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found with id: " + id));
        return toResponseDTO(auditLog);
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

    private void validateDateRange(LocalDateTime fromDate, LocalDateTime toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("Audit start date cannot be after end date.");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Page index cannot be negative.");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("Page size must be between 1 and 100.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private AuditLogResponseDTO toResponseDTO(AuditLog auditLog) {
        User user = auditLog.getUser();
        return new AuditLogResponseDTO(
                auditLog.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getUsername() : null,
                auditLog.getAction(),
                auditLog.getEntityName(),
                auditLog.getEntityId(),
                auditLog.getDetails(),
                auditLog.getClientIp(),
                auditLog.getCreatedAt()
        );
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
