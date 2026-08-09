package com.invault.inventory.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.invault.inventory.roles.Role;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.audit.AuditLogRepository;
import com.invault.inventory.units.UnitService;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:invault_jwt_lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtLifecycleIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private UnitService unitService;

    private User user;

    @BeforeEach
    void createActiveUser() {
        Role readOnly = roleRepository.findByName(RoleName.READ_ONLY).orElseThrow();
        user = new User("jwt.lifecycle", "jwt.lifecycle@example.com", "hash");
        user.addRole(readOnly);
        user = userRepository.saveAndFlush(user);
        when(unitService.findAll()).thenReturn(List.of());
    }

    @AfterEach
    void deleteUser() {
        auditLogRepository.deleteAll();
        userRepository.deleteById(user.getId());
    }

    @Test
    void logoutImmediatelyRevokesPreviouslyIssuedToken() throws Exception {
        String bearerToken = "Bearer " + jwtService.generateToken(user).token();

        mockMvc.perform(get("/api/units").header("Authorization", bearerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/units").header("Authorization", bearerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentMandatoryPasswordStateRestrictsInventoryAccess() throws Exception {
        user.setMustChangePassword(true);
        userRepository.saveAndFlush(user);
        String bearerToken = "Bearer " + jwtService.generateToken(user).token();

        mockMvc.perform(get("/api/units").header("Authorization", bearerToken))
                .andExpect(status().isForbidden());
    }
}
