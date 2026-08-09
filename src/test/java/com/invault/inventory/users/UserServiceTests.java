package com.invault.inventory.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.audit.AuditService;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.roles.Role;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.users.dto.PasswordChangeRequestDTO;
import com.invault.inventory.users.dto.UserCreateRequestDTO;
import com.invault.inventory.users.dto.UserResponseDTO;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @Test
    void createNormalizesIdentityHashesPasswordAndRequiresItsChange() {
        UserService service = service();
        Role warehouse = new Role(RoleName.WAREHOUSE, "Warehouse operator");
        UserCreateRequestDTO request = new UserCreateRequestDTO(
                "  Operator  ",
                "  OPERATOR@EXAMPLE.COM  ",
                "Temporary-123",
                Set.of(RoleName.WAREHOUSE)
        );

        when(userRepository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("operator@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByNameAndActiveTrue(RoleName.WAREHOUSE)).thenReturn(Optional.of(warehouse));
        when(passwordEncoder.encode("Temporary-123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = service.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("operator", saved.getUsername());
        assertEquals("operator@example.com", saved.getEmail());
        assertEquals("encoded-password", saved.getPasswordHash());
        assertTrue(saved.getMustChangePassword());
        assertEquals(Set.of(RoleName.WAREHOUSE), Set.copyOf(response.roles()));
    }

    @Test
    void deactivateProtectsTheLastActiveAdministrator() {
        UserService service = service();
        User administrator = userWithId(7L, "admin", "hash");
        administrator.addRole(new Role(RoleName.ADMIN, "Administrator"));

        when(userRepository.findById(7L)).thenReturn(Optional.of(administrator));
        when(userRepository.countActiveUsersByRole(RoleName.ADMIN)).thenReturn(1L);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.deactivate(7L, 99L)
        );

        assertEquals("The last active administrator cannot be removed or deactivated.", exception.getMessage());
        assertTrue(administrator.getActive());
    }

    @Test
    void changeOwnPasswordClearsMandatoryChangeFlag() {
        UserService service = service();
        User user = userWithId(8L, "operator", "old-hash");
        user.setMustChangePassword(true);

        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Current-1234", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("New-password-123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("New-password-123")).thenReturn("new-hash");

        service.changeOwnPassword(8L, new PasswordChangeRequestDTO("Current-1234", "New-password-123"));

        assertEquals("new-hash", user.getPasswordHash());
        assertFalse(user.getMustChangePassword());
        assertEquals(1L, user.getTokenVersion());
        verify(userRepository).save(user);
    }

    private UserService service() {
        return new UserService(userRepository, roleRepository, passwordEncoder, new UserMapper(), auditService);
    }

    private User userWithId(Long id, String username, String hash) {
        User user = new User(username, username + "@example.com", hash);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
