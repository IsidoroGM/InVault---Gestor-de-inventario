package com.invault.inventory.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.audit.AuditAction;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.roles.Role;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class SecurityBootstrapServiceTests {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @BeforeEach
    void configureRoleRepository() {
        for (RoleName roleName : RoleName.values()) {
            when(roleRepository.findByName(roleName)).thenReturn(Optional.empty());
        }

        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enabledBootstrapCreatesRolesAndEncryptedInitialAdmin() {
        SecurityBootstrapProperties properties = new SecurityBootstrapProperties(
                true,
                " initial.admin ",
                " admin@invault.local ",
                "temporary-password"
        );
        SecurityBootstrapService service = createService(properties);

        when(userRepository.existsByUsernameIgnoreCase("initial.admin")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("admin@invault.local")).thenReturn(false);
        when(passwordEncoder.encode("temporary-password")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 21L);
            return user;
        });

        service.initializeSecurityData();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("initial.admin", savedUser.getUsername());
        assertEquals("admin@invault.local", savedUser.getEmail());
        assertEquals("bcrypt-hash", savedUser.getPasswordHash());
        assertTrue(savedUser.getMustChangePassword());
        assertEquals(RoleName.ADMIN, savedUser.getRoles().iterator().next().getName());
        verify(roleRepository, times(RoleName.values().length)).save(any(Role.class));
        verify(auditService).registerSystemAction(
                AuditAction.CREATED,
                "User",
                21L,
                "Initial administrator account created by secure bootstrap.",
                null
        );
    }

    @Test
    void disabledBootstrapCreatesOnlyOfficialRoles() {
        SecurityBootstrapService service = createService(
                new SecurityBootstrapProperties(false, "", "", "")
        );

        service.initializeSecurityData();

        verify(roleRepository, times(RoleName.values().length)).save(any(Role.class));
        verifyNoInteractions(userRepository, passwordEncoder, auditService);
    }

    @Test
    void existingBootstrapUsernameIsNeverReset() {
        SecurityBootstrapProperties properties = new SecurityBootstrapProperties(
                true,
                "admin",
                "admin@invault.local",
                "temporary-password"
        );
        SecurityBootstrapService service = createService(properties);

        when(userRepository.existsByUsernameIgnoreCase("admin")).thenReturn(true);

        service.initializeSecurityData();

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(auditService);
    }

    private SecurityBootstrapService createService(SecurityBootstrapProperties properties) {
        return new SecurityBootstrapService(
                roleRepository,
                userRepository,
                passwordEncoder,
                properties,
                auditService
        );
    }
}

/*
 * SecurityBootstrapServiceTests protect role seeding, external credential use,
 * password hashing and the guarantee that existing accounts are never reset.
 */
