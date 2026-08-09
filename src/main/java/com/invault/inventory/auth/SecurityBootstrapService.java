package com.invault.inventory.auth;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.audit.AuditAction;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.roles.Role;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@Service
@Transactional
public class SecurityBootstrapService {

    private static final int MINIMUM_BOOTSTRAP_PASSWORD_LENGTH = 12;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityBootstrapProperties properties;
    private final AuditService auditService;

    public SecurityBootstrapService(
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SecurityBootstrapProperties properties,
            AuditService auditService) {

        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.auditService = auditService;
    }

    public void initializeSecurityData() {
        Map<RoleName, Role> roles = ensureOfficialRoles();

        if (!properties.enabled()) {
            return;
        }

        String username = requiredValue(properties.username(), "Bootstrap admin username is required.");
        String email = requiredValue(properties.email(), "Bootstrap admin email is required.");
        String password = requiredValue(properties.password(), "Bootstrap admin password is required.");

        if (password.length() < MINIMUM_BOOTSTRAP_PASSWORD_LENGTH) {
            throw new IllegalStateException("Bootstrap admin password must contain at least 12 characters.");
        }

        if (!email.contains("@")) {
            throw new IllegalStateException("Bootstrap admin email must have a valid format.");
        }

        // The bootstrap is idempotent and never resets an existing account password.
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Bootstrap admin email is already assigned to another user.");
        }

        User admin = new User(username, email, passwordEncoder.encode(password));
        admin.setMustChangePassword(true);
        admin.addRole(roles.get(RoleName.ADMIN));

        User savedAdmin = userRepository.save(admin);

        auditService.registerSystemAction(
                AuditAction.CREATED,
                "User",
                savedAdmin.getId(),
                "Initial administrator account created by secure bootstrap.",
                null
        );
    }

    private Map<RoleName, Role> ensureOfficialRoles() {
        Map<RoleName, Role> roles = new EnumMap<>(RoleName.class);

        for (RoleName roleName : RoleName.values()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseGet(() -> roleRepository.save(
                            new Role(roleName, officialRoleDescription(roleName))
                    ));

            roles.put(roleName, role);
        }

        return roles;
    }

    private String officialRoleDescription(RoleName roleName) {
        return switch (roleName) {
            case ADMIN -> "Full InVault administration access.";
            case SUPERVISOR -> "Inventory catalogue and supervision access.";
            case WAREHOUSE -> "Warehouse operations and stock movement access.";
            case READ_ONLY -> "Read-only inventory access.";
        };
    }

    private String requiredValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }

        return value.trim();
    }
}

/*
 * SecurityBootstrapService guarantees the four official roles and optionally
 * creates a first administrator once, using only external credentials.
 */
