package com.invault.inventory.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.invault.inventory.units.UnitRepository;
import com.invault.inventory.units.UnitService;
import com.invault.inventory.units.dto.UnitRequestDTO;
import com.invault.inventory.units.dto.UnitResponseDTO;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:invault_audit_tx;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class TransactionalMutationAuditTests {

    @Autowired
    private UnitService unitService;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    private Long actorId;

    @BeforeEach
    void setUpActor() {
        unitRepository.deleteAll();
        userRepository.deleteAll();

        User actor = userRepository.save(new User("auditor", "auditor@example.com", "hash"));
        actorId = actor.getId();
        authenticate(actorId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createdEntityIdAndSnapshotReachTheAuditRecord() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UnitResponseDTO response = unitService.create(unitRequest("KG", "Kilogram"));

        org.mockito.ArgumentCaptor<AuditLog> captor = org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog auditLog = captor.getValue();

        assertNotNull(response.getId());
        assertEquals(response.getId(), auditLog.getEntityId());
        assertEquals(actorId, auditLog.getUser().getId());
        assertNull(auditLog.getBeforeData());
        assertNotNull(auditLog.getAfterData());
        assertTrue(auditLog.getAfterData().contains("\"code\":\"KG\""));
    }

    @Test
    void auditFailureRollsBackTheBusinessMutation() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new IllegalStateException("Simulated audit storage failure"));

        assertThrows(
                IllegalStateException.class,
                () -> unitService.create(unitRequest("L", "Liter"))
        );

        assertFalse(unitRepository.findByCodeIgnoreCase("L").isPresent());
    }

    private UnitRequestDTO unitRequest(String code, String name) {
        UnitRequestDTO request = new UnitRequestDTO();
        request.setCode(code);
        request.setName(name);
        request.setSymbol(code.toLowerCase());
        request.setActive(true);
        return request;
    }

    private void authenticate(Long userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("auditor")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("userId", userId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
