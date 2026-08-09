package com.invault.inventory.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.invault.inventory.auth.dto.LoginRequestDTO;
import com.invault.inventory.auth.dto.LoginResponseDTO;
import com.invault.inventory.common.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponseDTO login(
            @Valid @RequestBody LoginRequestDTO requestDTO,
            HttpServletRequest request) {

        return authService.login(requestDTO, request.getRemoteAddr());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        authService.logout(authenticatedUserId(authentication));
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("Authenticated user is required.");
        }

        Number userId = jwt.getClaim("userId");
        if (userId == null || userId.longValue() <= 0) {
            throw new UnauthorizedException("Authenticated user id is missing.");
        }
        return userId.longValue();
    }
}

/*
 * AuthController exposes the public login operation and forwards the client IP
 * for audit purposes. Bearer-token validation is handled by Spring Security.
 */
