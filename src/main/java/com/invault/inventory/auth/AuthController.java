package com.invault.inventory.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.invault.inventory.auth.dto.LoginRequestDTO;
import com.invault.inventory.auth.dto.LoginResponseDTO;

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
}

/*
 * AuthController exposes the public login operation and forwards the client IP
 * for audit purposes. Bearer-token validation is handled by Spring Security.
 */
