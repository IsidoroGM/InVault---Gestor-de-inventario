package com.invault.inventory.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void registerUserActionSavesNormalizedAuditLog() {
        User user = new User();
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));

        auditService.registerUserAction(
                12L,
                AuditAction.UPDATED,
                "  Product  ",
                34L,
                "  Product data updated  ",
                "  192.168.1.20  "
        );

        AuditLog savedAuditLog = captureSavedAuditLog();

        assertSame(user, savedAuditLog.getUser());
        assertEquals(AuditAction.UPDATED, savedAuditLog.getAction());
        assertEquals("Product", savedAuditLog.getEntityName());
        assertEquals(34L, savedAuditLog.getEntityId());
        assertEquals("Product data updated", savedAuditLog.getDetails());
        assertEquals("192.168.1.20", savedAuditLog.getClientIp());
    }

    @Test
    void registerSystemActionSavesAuditLogWithoutUser() {
        auditService.registerSystemAction(
                AuditAction.LOGIN_FAILED,
                "Authentication",
                null,
                "  Invalid credentials  ",
                "  "
        );

        AuditLog savedAuditLog = captureSavedAuditLog();

        assertNull(savedAuditLog.getUser());
        assertEquals(AuditAction.LOGIN_FAILED, savedAuditLog.getAction());
        assertEquals("Authentication", savedAuditLog.getEntityName());
        assertNull(savedAuditLog.getEntityId());
        assertEquals("Invalid credentials", savedAuditLog.getDetails());
        assertNull(savedAuditLog.getClientIp());
        verifyNoInteractions(userRepository);
    }

    @Test
    void registerUserActionRejectsMissingUserId() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> auditService.registerUserAction(
                        null,
                        AuditAction.CREATED,
                        "Product",
                        1L,
                        null,
                        null
                )
        );

        assertEquals("User id is required for a user audit action.", exception.getMessage());
        verifyNoInteractions(userRepository, auditLogRepository);
    }

    @Test
    void registerUserActionRejectsUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> auditService.registerUserAction(
                        99L,
                        AuditAction.CREATED,
                        "Product",
                        1L,
                        null,
                        null
                )
        );

        assertEquals("User not found with id: 99", exception.getMessage());
        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any(AuditLog.class));
    }

    @Test
    void registerSystemActionRejectsMissingAction() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> auditService.registerSystemAction(
                        null,
                        "Product",
                        1L,
                        null,
                        null
                )
        );

        assertEquals("Audit action is required.", exception.getMessage());
        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any(AuditLog.class));
    }

    @Test
    void registerSystemActionRejectsBlankEntityName() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> auditService.registerSystemAction(
                        AuditAction.CREATED,
                        "  ",
                        1L,
                        null,
                        null
                )
        );

        assertEquals("Audit entity name is required.", exception.getMessage());
        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any(AuditLog.class));
    }

    @Test
    void registerSystemActionRejectsNonPositiveEntityId() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> auditService.registerSystemAction(
                        AuditAction.CREATED,
                        "Product",
                        0L,
                        null,
                        null
                )
        );

        assertEquals("Audit entity id must be greater than zero.", exception.getMessage());
        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any(AuditLog.class));
    }

    private AuditLog captureSavedAuditLog() {
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());
        return auditLogCaptor.getValue();
    }
}

/*
 * AuditServiceTests verifica el registro de acciones de usuario y del sistema,
 * la normalización de textos y las validaciones que protegen la trazabilidad.
 */
