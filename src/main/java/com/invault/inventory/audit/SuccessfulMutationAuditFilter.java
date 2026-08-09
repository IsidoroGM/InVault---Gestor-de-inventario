package com.invault.inventory.audit;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SuccessfulMutationAuditFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuccessfulMutationAuditFilter.class);
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditService auditService;

    public SuccessfulMutationAuditFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        filterChain.doFilter(request, response);

        if (!MUTATING_METHODS.contains(request.getMethod())
                || response.getStatus() < 200
                || response.getStatus() >= 300) {
            return;
        }

        AuditDescriptor descriptor = descriptorFor(request);
        Long userId = authenticatedUserId();
        if (descriptor == null || userId == null) {
            return;
        }

        try {
            auditService.registerUserAction(
                    userId,
                    descriptor.action(),
                    descriptor.entityName(),
                    descriptor.entityId(),
                    request.getMethod() + " " + request.getRequestURI(),
                    request.getRemoteAddr()
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Could not persist audit entry for {} {}",
                    request.getMethod(), request.getRequestURI(), exception);
        }
    }

    private AuditDescriptor descriptorFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/auth/")) {
            return null;
        }

        String[] segments = path.substring("/api/".length()).split("/");
        if (segments.length == 0) {
            return null;
        }

        String entityName = switch (segments[0]) {
            case "units" -> "Unit";
            case "categories" -> "Category";
            case "locations" -> "Location";
            case "suppliers" -> "Supplier";
            case "products" -> "Product";
            case "batches" -> "Batch";
            case "users" -> "User";
            case "stock" -> "StockMovement";
            default -> null;
        };
        if (entityName == null) {
            return null;
        }

        AuditAction action = actionFor(request.getMethod(), path, entityName);
        return new AuditDescriptor(action, entityName, numericIdentifier(segments));
    }

    private AuditAction actionFor(String method, String path, String entityName) {
        if (path.contains("password")) {
            return AuditAction.PASSWORD_CHANGED;
        }
        if ("StockMovement".equals(entityName)) {
            return AuditAction.STOCK_MOVEMENT_CREATED;
        }
        if (path.endsWith("/deactivate")) {
            return AuditAction.DEACTIVATED;
        }
        if ("POST".equals(method)) {
            return AuditAction.CREATED;
        }
        if ("DELETE".equals(method)) {
            return AuditAction.DELETED;
        }
        return AuditAction.UPDATED;
    }

    private Long numericIdentifier(String[] segments) {
        for (int index = 1; index < segments.length; index++) {
            try {
                long value = Long.parseLong(segments[index]);
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                // Continue until a path identifier is found.
            }
        }
        return null;
    }

    private Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        Number userId = jwt.getClaim("userId");
        return userId != null && userId.longValue() > 0 ? userId.longValue() : null;
    }

    private record AuditDescriptor(AuditAction action, String entityName, Long entityId) {
    }
}

/*
 * SuccessfulMutationAuditFilter records only authenticated, successful API writes.
 * Audit failures are logged without changing an already completed business response.
 */
