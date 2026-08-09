package com.invault.inventory.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.audit.AuditAction;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.auth.JwtService.TokenDetails;
import com.invault.inventory.auth.dto.LoginRequestDTO;
import com.invault.inventory.auth.dto.LoginResponseDTO;
import com.invault.inventory.common.exception.UnauthorizedException;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    @Test
    void successfulLoginUpdatesUserAuditsAndReturnsBearerToken() {
        LoginRequestDTO requestDTO = new LoginRequestDTO(" operator ", "correct-password");
        User user = new User("operator", "operator@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 4L);
        Instant expiresAt = Instant.now().plusSeconds(300);

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(anyAuthentication());
        when(userRepository.findByUsernameIgnoreCaseAndActiveTrue("operator")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.activeRoleNames(user)).thenReturn(List.of("WAREHOUSE"));
        when(jwtService.generateToken(user)).thenReturn(new TokenDetails("signed-token", expiresAt, 300));

        LoginResponseDTO response = authService.login(requestDTO, "127.0.0.1");

        assertEquals("signed-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(List.of("WAREHOUSE"), response.roles());
        assertEquals(4L, response.userId());
        assertNotNull(user.getLastLoginAt());
        verify(userRepository).save(user);
        verify(auditService).registerUserAction(
                4L,
                AuditAction.LOGIN_SUCCESS,
                "User",
                4L,
                "User logged in successfully.",
                "127.0.0.1"
        );
    }

    @Test
    void invalidCredentialsAreAuditedAndReturnUnauthorized() {
        LoginRequestDTO requestDTO = new LoginRequestDTO("unknown", "wrong-password");

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(requestDTO, "10.0.0.8")
        );

        assertEquals("Invalid username or password.", exception.getMessage());
        verify(auditService).registerSystemAction(
                AuditAction.LOGIN_FAILED,
                "User",
                null,
                "Failed login attempt for username 'unknown'.",
                "10.0.0.8"
        );
    }

    @Test
    void userWithoutActiveRolesCannotReceiveToken() {
        LoginRequestDTO requestDTO = new LoginRequestDTO("reader", "correct-password");
        User user = new User("reader", "reader@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 9L);

        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(anyAuthentication());
        when(userRepository.findByUsernameIgnoreCaseAndActiveTrue("reader")).thenReturn(Optional.of(user));
        when(jwtService.activeRoleNames(user)).thenReturn(List.of());

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(requestDTO, "127.0.0.1")
        );

        assertEquals("User does not have an active role.", exception.getMessage());
        verify(auditService).registerUserAction(
                9L,
                AuditAction.LOGIN_FAILED,
                "User",
                9L,
                "Login rejected because the user has no active role.",
                "127.0.0.1"
        );
    }

    @Test
    void logoutIncrementsSessionVersionAndAuditsRevocation() {
        User user = new User("operator", "operator@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 4L);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        authService.logout(4L);

        assertEquals(1L, user.getTokenVersion());
        verify(userRepository).save(user);
        verify(auditService).registerMutation(
                eq(AuditAction.LOGOUT),
                eq("User"),
                eq(4L),
                anyString(),
                any(),
                any()
        );
    }

    private Authentication anyAuthentication() {
        return org.mockito.Mockito.mock(Authentication.class);
    }
}

/*
 * AuthServiceTests protect successful, invalid-credential and missing-role login
 * paths, including audit events and last-login updates.
 */
