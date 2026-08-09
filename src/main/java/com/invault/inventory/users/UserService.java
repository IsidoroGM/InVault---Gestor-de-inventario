package com.invault.inventory.users;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.audit.AuditAction;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.roles.Role;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.users.dto.PasswordChangeRequestDTO;
import com.invault.inventory.users.dto.PasswordResetRequestDTO;
import com.invault.inventory.users.dto.UserCreateRequestDTO;
import com.invault.inventory.users.dto.UserResponseDTO;
import com.invault.inventory.users.dto.UserUpdateRequestDTO;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuditService auditService;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            AuditService auditService) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userMapper.toResponseDTOList(userRepository.findAllByOrderByUsernameAsc());
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAllActive() {
        return userMapper.toResponseDTOList(userRepository.findByActiveTrueOrderByUsernameAsc());
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findById(Long id) {
        return userMapper.toResponseDTO(findUserById(id));
    }

    public UserResponseDTO create(UserCreateRequestDTO requestDTO) {
        String username = normalizeUsername(requestDTO.username());
        String email = normalizeEmail(requestDTO.email());

        validateUsernameIsUnique(username, null);
        validateEmailIsUnique(email, null);

        User user = new User(username, email, passwordEncoder.encode(requestDTO.temporaryPassword()));
        user.setMustChangePassword(true);
        user.setRoles(loadActiveRoles(requestDTO.roles()));

        User savedUser = userRepository.save(user);
        UserResponseDTO response = userMapper.toResponseDTO(savedUser);
        auditService.registerMutation(
                AuditAction.CREATED, "User", savedUser.getId(), "User created.", null, response);
        return response;
    }

    public UserResponseDTO update(Long id, UserUpdateRequestDTO requestDTO) {
        User user = findUserById(id);
        UserResponseDTO before = userMapper.toResponseDTO(user);
        String email = normalizeEmail(requestDTO.email());
        Set<Role> roles = loadActiveRoles(requestDTO.roles());

        validateEmailIsUnique(email, id);
        protectLastAdministrator(user, roles, true);

        user.setEmail(email);
        user.setRoles(roles);
        user.revokeActiveTokens();
        User updatedUser = userRepository.save(user);
        UserResponseDTO after = userMapper.toResponseDTO(updatedUser);
        auditService.registerMutation(
                AuditAction.UPDATED, "User", id, "User updated.", before, after);
        return after;
    }

    public void deactivate(Long id, Long authenticatedUserId) {
        User user = findUserById(id);
        UserResponseDTO before = userMapper.toResponseDTO(user);

        if (id.equals(authenticatedUserId)) {
            throw new BadRequestException("You cannot deactivate your own account.");
        }
        if (Boolean.FALSE.equals(user.getActive())) {
            throw new BadRequestException("User is already inactive.");
        }

        protectLastAdministrator(user, user.getRoles(), false);
        user.setActive(false);
        user.revokeActiveTokens();
        userRepository.save(user);
        UserResponseDTO after = userMapper.toResponseDTO(user);
        auditService.registerMutation(
                AuditAction.DEACTIVATED, "User", id, "User deactivated.", before, after);
    }

    public void activate(Long id) {
        User user = findUserById(id);
        UserResponseDTO before = userMapper.toResponseDTO(user);
        if (Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("User is already active.");
        }

        user.setActive(true);
        user.revokeActiveTokens();
        userRepository.save(user);
        UserResponseDTO after = userMapper.toResponseDTO(user);
        auditService.registerMutation(
                AuditAction.ACTIVATED, "User", id, "User activated.", before, after);
    }

    public void changeOwnPassword(Long authenticatedUserId, PasswordChangeRequestDTO requestDTO) {
        User user = findUserById(authenticatedUserId);
        UserResponseDTO before = userMapper.toResponseDTO(user);

        if (!passwordEncoder.matches(requestDTO.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (passwordEncoder.matches(requestDTO.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(requestDTO.newPassword()));
        user.setMustChangePassword(false);
        user.revokeActiveTokens();
        userRepository.save(user);
        UserResponseDTO after = userMapper.toResponseDTO(user);
        auditService.registerMutation(
                AuditAction.PASSWORD_CHANGED,
                "User",
                authenticatedUserId,
                "User changed own password.",
                before,
                after
        );
    }

    public void resetPassword(Long id, PasswordResetRequestDTO requestDTO) {
        User user = findUserById(id);
        UserResponseDTO before = userMapper.toResponseDTO(user);
        user.setPasswordHash(passwordEncoder.encode(requestDTO.temporaryPassword()));
        user.setMustChangePassword(true);
        user.revokeActiveTokens();
        userRepository.save(user);
        UserResponseDTO after = userMapper.toResponseDTO(user);
        auditService.registerMutation(
                AuditAction.PASSWORD_RESET, "User", id, "User password reset.", before, after);
    }

    private User findUserById(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("User id must be greater than zero.");
        }

        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private Set<Role> loadActiveRoles(Set<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BadRequestException("At least one role is required.");
        }

        EnumSet<RoleName> uniqueRoleNames = EnumSet.copyOf(roleNames);
        Set<Role> roles = uniqueRoleNames.stream()
                .map(roleName -> roleRepository.findByNameAndActiveTrue(roleName)
                        .orElseThrow(() -> new BadRequestException(
                                "Active role is not available: " + roleName
                        )))
                .collect(Collectors.toSet());

        if (roles.size() != uniqueRoleNames.size()) {
            throw new BadRequestException("Every selected role must be active.");
        }
        return roles;
    }

    private void protectLastAdministrator(User user, Set<Role> resultingRoles, boolean remainsActive) {
        boolean currentlyAdmin = hasRole(user.getRoles(), RoleName.ADMIN);
        boolean remainsAdmin = remainsActive && hasRole(resultingRoles, RoleName.ADMIN);

        if (Boolean.TRUE.equals(user.getActive())
                && currentlyAdmin
                && !remainsAdmin
                && userRepository.countActiveUsersByRole(RoleName.ADMIN) <= 1) {
            throw new BadRequestException("The last active administrator cannot be removed or deactivated.");
        }
    }

    private boolean hasRole(Set<Role> roles, RoleName roleName) {
        return roles != null && roles.stream()
                .anyMatch(role -> roleName.equals(role.getName()) && Boolean.TRUE.equals(role.getActive()));
    }

    private void validateUsernameIsUnique(String username, Long currentUserId) {
        userRepository.findByUsernameIgnoreCase(username)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new BadRequestException("Username is already registered.");
                });
    }

    private void validateEmailIsUnique(String email, Long currentUserId) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> currentUserId == null || !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new BadRequestException("Email is already registered.");
                });
    }

    private String normalizeUsername(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}

/*
 * UserService owns account lifecycle, role assignment and password rules while
 * protecting InVault from losing its final active administrator.
 */
