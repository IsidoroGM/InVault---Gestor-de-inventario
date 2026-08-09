package com.invault.inventory.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.roles.Role;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserSessionTokenValidatorTests {

    @Mock
    private UserRepository userRepository;

    private UserSessionTokenValidator validator;
    private User user;

    @BeforeEach
    void setUp() {
        validator = new UserSessionTokenValidator(userRepository);
        user = new User("operator", "operator@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        user.addRole(new Role(RoleName.WAREHOUSE, "Warehouse operations"));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void acceptsTokenThatMatchesCurrentAccountState() {
        OAuth2TokenValidatorResult result = validator.validate(token(0L, false, List.of("WAREHOUSE")));

        assertFalse(result.hasErrors());
    }

    @Test
    void rejectsTokenAfterSessionVersionChanges() {
        user.revokeActiveTokens();

        OAuth2TokenValidatorResult result = validator.validate(token(0L, false, List.of("WAREHOUSE")));

        assertTrue(result.hasErrors());
    }

    @Test
    void rejectsTokenWhenAccountStateOrRolesNoLongerMatch() {
        user.setMustChangePassword(true);

        OAuth2TokenValidatorResult stalePasswordState =
                validator.validate(token(0L, false, List.of("WAREHOUSE")));
        OAuth2TokenValidatorResult staleRoles =
                validator.validate(token(0L, true, List.of("READ_ONLY")));
        user.setActive(false);
        OAuth2TokenValidatorResult inactiveAccount =
                validator.validate(token(0L, true, List.of("WAREHOUSE")));

        assertTrue(stalePasswordState.hasErrors());
        assertTrue(staleRoles.hasErrors());
        assertTrue(inactiveAccount.hasErrors());
    }

    private Jwt token(long tokenVersion, boolean mustChangePassword, List<String> roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject("operator")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("userId", 7L)
                .claim("roles", roles)
                .claim("mustChangePassword", mustChangePassword)
                .claim("tokenVersion", tokenVersion)
                .build();
    }
}
