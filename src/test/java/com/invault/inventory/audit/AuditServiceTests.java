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
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.invault.inventory.audit.dto.AuditLogResponseDTO;
import com.invault.inventory.common.dto.PageResponseDTO;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @InjectMocks
    private AuditService auditService;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

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
    void registerMutationCapturesAuthenticatedActorIpAndSnapshots() {
        User user = new User();
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        authenticate(12L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.registerMutation(
                AuditAction.UPDATED,
                "Product",
                34L,
                "Product updated.",
                Map.of("name", "Old product"),
                Map.of("name", "New product")
        );

        AuditLog savedAuditLog = captureSavedAuditLog();
        assertSame(user, savedAuditLog.getUser());
        assertEquals(34L, savedAuditLog.getEntityId());
        assertEquals("{\"name\":\"Old product\"}", savedAuditLog.getBeforeData());
        assertEquals("{\"name\":\"New product\"}", savedAuditLog.getAfterData());
        assertEquals("10.0.0.8", savedAuditLog.getClientIp());
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

    @Test
    void searchReturnsAStablePagedAuditContract() {
        User user = new User("operator", "operator@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 12L);
        AuditLog auditLog = new AuditLog(
                user,
                AuditAction.UPDATED,
                "Product",
                34L,
                "Product data updated",
                "192.168.1.20"
        );
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 6, 12, 0);
        ReflectionTestUtils.setField(auditLog, "id", 50L);
        ReflectionTestUtils.setField(auditLog, "createdAt", createdAt);

        when(auditLogRepository.search(
                org.mockito.ArgumentMatchers.eq(AuditAction.UPDATED),
                org.mockito.ArgumentMatchers.eq("Product"),
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(java.util.List.of(auditLog)));

        PageResponseDTO<AuditLogResponseDTO> result = auditService.search(
                AuditAction.UPDATED,
                "  Product  ",
                12L,
                null,
                null,
                0,
                25
        );

        assertEquals(1, result.totalElements());
        assertEquals(50L, result.content().getFirst().id());
        assertEquals("operator", result.content().getFirst().username());
        assertEquals(createdAt, result.content().getFirst().createdAt());
    }

    private AuditLog captureSavedAuditLog() {
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());
        return auditLogCaptor.getValue();
    }

    private void authenticate(Long userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("audit-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("userId", userId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}

/*
 * AuditServiceTests verifica el registro de acciones de usuario y del sistema,
 * la normalización de textos y las validaciones que protegen la trazabilidad.
 */
