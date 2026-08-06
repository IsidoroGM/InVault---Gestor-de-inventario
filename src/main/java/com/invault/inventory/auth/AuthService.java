package com.invault.inventory.auth;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.invault.inventory.audit.AuditAction;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.auth.JwtService.TokenDetails;
import com.invault.inventory.auth.dto.LoginRequestDTO;
import com.invault.inventory.auth.dto.LoginResponseDTO;
import com.invault.inventory.common.exception.UnauthorizedException;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password.";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            AuditService auditService) {

        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    public LoginResponseDTO login(LoginRequestDTO requestDTO, String clientIp) {
        String username = requestDTO.username().trim();

        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, requestDTO.password())
            );
        } catch (AuthenticationException exception) {
            auditService.registerSystemAction(
                    AuditAction.LOGIN_FAILED,
                    "User",
                    null,
                    "Failed login attempt for username '" + username + "'.",
                    clientIp
            );

            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        User user = userRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        List<String> roles = jwtService.activeRoleNames(user);

        if (roles.isEmpty()) {
            auditService.registerUserAction(
                    user.getId(),
                    AuditAction.LOGIN_FAILED,
                    "User",
                    user.getId(),
                    "Login rejected because the user has no active role.",
                    clientIp
            );

            throw new UnauthorizedException("User does not have an active role.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        TokenDetails tokenDetails = jwtService.generateToken(user);

        auditService.registerUserAction(
                user.getId(),
                AuditAction.LOGIN_SUCCESS,
                "User",
                user.getId(),
                "User logged in successfully.",
                clientIp
        );

        return new LoginResponseDTO(
                tokenDetails.token(),
                "Bearer",
                tokenDetails.expiresInSeconds(),
                tokenDetails.expiresAt(),
                user.getId(),
                user.getUsername(),
                roles,
                Boolean.TRUE.equals(user.getMustChangePassword())
        );
    }
}

/*
 * AuthService authenticates credentials, records login attempts, updates the
 * last-login timestamp and returns a signed token for successful sessions.
 */
