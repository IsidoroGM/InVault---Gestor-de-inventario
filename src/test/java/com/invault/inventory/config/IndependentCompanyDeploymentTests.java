package com.invault.inventory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.invault.inventory.InVaultBackendApplication;
import com.invault.inventory.auth.JwtService;
import com.invault.inventory.roles.RoleName;
import com.invault.inventory.roles.RoleRepository;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

/** Exercises the real application security and persistence boundaries of two company deployments. */
class IndependentCompanyDeploymentTests {

    @Test
    void companyDataAndTokensRemainIsolatedEvenWhenUserIdsAreEqual() throws Exception {
        try (ConfigurableApplicationContext first = startCompany("a");
                ConfigurableApplicationContext second = startCompany("b")) {
            User firstAdmin = createAdmin(first, "admin-a");
            User secondAdmin = createAdmin(second, "admin-b");
            assertEquals(firstAdmin.getId(), secondAdmin.getId(),
                    "The test must exercise colliding user IDs across independent databases.");

            String firstToken = "Bearer " + first.getBean(JwtService.class).generateToken(firstAdmin).token();
            String secondToken = "Bearer " + second.getBean(JwtService.class).generateToken(secondAdmin).token();
            MockMvc firstApi = api(first);
            MockMvc secondApi = api(second);

            firstApi.perform(post("/api/units").header("Authorization", firstToken)
                            .contentType("application/json")
                            .content("{\"code\":\"ONLY_A\",\"name\":\"Company A unit\",\"symbol\":\"a\",\"active\":true}"))
                    .andExpect(status().isCreated());

            secondApi.perform(get("/api/units").header("Authorization", secondToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            secondApi.perform(get("/api/units/1").header("Authorization", secondToken))
                    .andExpect(status().isNotFound());

            secondApi.perform(get("/api/units").header("Authorization", firstToken))
                    .andExpect(status().isUnauthorized());
            firstApi.perform(get("/api/units").header("Authorization", secondToken))
                    .andExpect(status().isUnauthorized());

            secondApi.perform(post("/api/units").header("Authorization", secondToken)
                            .contentType("application/json")
                            .content("{\"code\":\"ONLY_B\",\"name\":\"Company B unit\",\"symbol\":\"b\",\"active\":true}"))
                    .andExpect(status().isCreated());

            firstApi.perform(get("/api/units").header("Authorization", firstToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].code").value("ONLY_A"));
            secondApi.perform(get("/api/units").header("Authorization", secondToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].code").value("ONLY_B"));
        }
    }

    private ConfigurableApplicationContext startCompany(String company) {
        String database = "company_" + company + "_" + UUID.randomUUID().toString().replace("-", "");
        String origin = "https://company-" + company + ".example.test";
        String secret = Base64.getEncoder().encodeToString(
                company.repeat(32).getBytes(StandardCharsets.UTF_8));

        return new SpringApplicationBuilder(InVaultBackendApplication.class).run(
                "--spring.profiles.active=test",
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--logging.level.root=ERROR",
                "--spring.datasource.url=jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--invault.security.bootstrap-admin.enabled=false",
                "--invault.security.jwt.secret=" + secret,
                "--invault.security.jwt.issuer=" + origin,
                "--invault.cors.allowed-origins=" + origin,
                "--invault.realtime.allowed-origins=" + origin,
                "--springdoc.api-docs.enabled=false",
                "--springdoc.swagger-ui.enabled=false");
    }

    private User createAdmin(ConfigurableApplicationContext context, String username) {
        User admin = new User(username, username + "@example.test", "unused-test-password-hash");
        admin.setMustChangePassword(false);
        admin.addRole(context.getBean(RoleRepository.class).findByName(RoleName.ADMIN).orElseThrow());
        return context.getBean(UserRepository.class).saveAndFlush(admin);
    }

    private MockMvc api(ConfigurableApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(springSecurity())
                .build();
    }
}
