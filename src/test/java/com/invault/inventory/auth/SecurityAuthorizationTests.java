package com.invault.inventory.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.invault.inventory.auth.dto.LoginRequestDTO;
import com.invault.inventory.auth.dto.LoginResponseDTO;
import com.invault.inventory.categories.CategoryService;
import com.invault.inventory.categories.dto.CategoryRequestDTO;
import com.invault.inventory.categories.dto.CategoryResponseDTO;
import com.invault.inventory.stock.StockService;
import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;
import com.invault.inventory.units.UnitService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UnitService unitService;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private StockService stockService;

    @Test
    void loginAndHealthRemainPublic() throws Exception {
        LoginResponseDTO response = new LoginResponseDTO(
                "signed-token",
                "Bearer",
                300,
                Instant.now().plusSeconds(300),
                1L,
                "admin",
                List.of("ADMIN"),
                false
        );
        when(authService.login(any(LoginRequestDTO.class), anyString())).thenReturn(response);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void inventoryReadsRequireAnyOfficialRole() throws Exception {
        when(unitService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/units"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/units")
                        .header("Authorization", bearerToken("READ_ONLY")))
                .andExpect(status().isOk());
    }

    @Test
    void readOnlyAndWarehouseRolesCannotMaintainCatalogues() throws Exception {
        String requestBody = "{\"name\":\"Raw material\"}";

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", bearerToken("READ_ONLY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", bearerToken("WAREHOUSE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void supervisorCanMaintainCatalogues() throws Exception {
        when(categoryService.create(any(CategoryRequestDTO.class)))
                .thenReturn(new CategoryResponseDTO());

        mockMvc.perform(post("/api/categories")
                        .header("Authorization", bearerToken("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Raw material\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void warehouseCanCreateStockMovementsButReadOnlyCannot() throws Exception {
        String requestBody = """
                {"productId":1,"batchId":2,"movementType":"INBOUND",
                 "quantity":10.000,"reason":"Receipt"}
                """;

        mockMvc.perform(post("/api/stock/movements")
                        .header("Authorization", bearerToken("READ_ONLY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        when(stockService.createMovement(any(StockMovementRequestDTO.class), any(Long.class)))
                .thenReturn(new StockMovementResponseDTO());

        mockMvc.perform(post("/api/stock/movements")
                        .header("Authorization", bearerToken("WAREHOUSE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    private String bearerToken(String role) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://api.invault.local")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300))
                .subject("security-test-user")
                .claim("userId", 99L)
                .claim("roles", List.of(role))
                .claim("mustChangePassword", false)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return "Bearer " + token;
    }
}

/*
 * SecurityAuthorizationTests exercise the real filter chain with signed JWTs
 * and verify public, read-only, catalogue-maintenance and stock-write rules.
 */
