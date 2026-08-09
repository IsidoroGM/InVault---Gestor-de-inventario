package com.invault.inventory.audit;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class SuccessfulMutationAuditFilterTests {

    @Mock
    private AuditService auditService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulAuthenticatedMutationIsAudited() throws Exception {
        authenticate(42L);
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/products/15/deactivate");
        request.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SuccessfulMutationAuditFilter(auditService).doFilter(
                request,
                response,
                (ignoredRequest, servletResponse) -> ((MockHttpServletResponse) servletResponse).setStatus(204)
        );

        verify(auditService).registerUserAction(
                42L,
                AuditAction.DEACTIVATED,
                "Product",
                15L,
                "PATCH /api/products/15/deactivate",
                "10.0.0.8"
        );
    }

    @Test
    void failedMutationIsNeverAudited() throws Exception {
        authenticate(42L);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/stock/movements");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SuccessfulMutationAuditFilter(auditService).doFilter(
                request,
                response,
                (ignoredRequest, servletResponse) -> ((MockHttpServletResponse) servletResponse).setStatus(400)
        );

        verify(auditService, never()).registerUserAction(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
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
