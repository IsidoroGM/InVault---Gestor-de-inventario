package com.invault.inventory.auth;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class MandatoryPasswordChangeFilter extends OncePerRequestFilter {

    private static final String PASSWORD_CHANGE_PATH = "/api/users/me/password";
    private static final String LOGOUT_PATH = "/api/auth/logout";
    private static final String ERROR_MESSAGE =
            "Password change is required before accessing this resource.";

    private final SecurityErrorResponseWriter errorResponseWriter;

    public MandatoryPasswordChangeFilter(SecurityErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (requiresPasswordChange(authentication) && !isAllowedDuringPasswordChange(request)) {
            writeForbiddenResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresPasswordChange(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof Jwt jwt
                && Boolean.TRUE.equals(jwt.getClaim("mustChangePassword"));
    }

    private boolean isAllowedDuringPasswordChange(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("PUT".equals(request.getMethod()) && PASSWORD_CHANGE_PATH.equals(path))
                || ("POST".equals(request.getMethod()) && LOGOUT_PATH.equals(path));
    }

    private void writeForbiddenResponse(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        errorResponseWriter.write(request, response, HttpStatus.FORBIDDEN, ERROR_MESSAGE);
    }
}
