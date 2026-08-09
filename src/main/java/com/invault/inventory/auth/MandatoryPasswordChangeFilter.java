package com.invault.inventory.auth;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import com.invault.inventory.common.exception.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

public class MandatoryPasswordChangeFilter extends OncePerRequestFilter {

    private static final String PASSWORD_CHANGE_PATH = "/api/users/me/password";
    private static final String LOGOUT_PATH = "/api/auth/logout";
    private static final String ERROR_MESSAGE =
            "Password change is required before accessing this resource.";

    private final ObjectMapper objectMapper;

    public MandatoryPasswordChangeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ERROR_MESSAGE,
                request.getRequestURI(),
                LocalDateTime.now()
        );
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
