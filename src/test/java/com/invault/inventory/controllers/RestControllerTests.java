package com.invault.inventory.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.invault.inventory.batches.BatchController;
import com.invault.inventory.batches.BatchService;
import com.invault.inventory.batches.dto.BatchRequestDTO;
import com.invault.inventory.batches.dto.BatchResponseDTO;
import com.invault.inventory.categories.CategoryController;
import com.invault.inventory.categories.CategoryService;
import com.invault.inventory.categories.dto.CategoryRequestDTO;
import com.invault.inventory.categories.dto.CategoryResponseDTO;
import com.invault.inventory.common.exception.GlobalExceptionHandler;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.locations.LocationController;
import com.invault.inventory.locations.LocationService;
import com.invault.inventory.locations.dto.LocationRequestDTO;
import com.invault.inventory.locations.dto.LocationResponseDTO;
import com.invault.inventory.products.ProductController;
import com.invault.inventory.products.ProductService;
import com.invault.inventory.products.dto.ProductRequestDTO;
import com.invault.inventory.products.dto.ProductResponseDTO;
import com.invault.inventory.stock.StockController;
import com.invault.inventory.stock.StockService;
import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;
import com.invault.inventory.suppliers.SupplierController;
import com.invault.inventory.suppliers.SupplierService;
import com.invault.inventory.suppliers.dto.SupplierRequestDTO;
import com.invault.inventory.suppliers.dto.SupplierResponseDTO;
import com.invault.inventory.units.UnitController;
import com.invault.inventory.units.UnitService;
import com.invault.inventory.units.dto.UnitRequestDTO;
import com.invault.inventory.units.dto.UnitResponseDTO;

class RestControllerTests {

    private UnitService unitService;
    private CategoryService categoryService;
    private LocationService locationService;
    private SupplierService supplierService;
    private ProductService productService;
    private BatchService batchService;
    private StockService stockService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        unitService = mock(UnitService.class);
        categoryService = mock(CategoryService.class);
        locationService = mock(LocationService.class);
        supplierService = mock(SupplierService.class);
        productService = mock(ProductService.class);
        batchService = mock(BatchService.class);
        stockService = mock(StockService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UnitController(unitService),
                        new CategoryController(categoryService),
                        new LocationController(locationService),
                        new SupplierController(supplierService),
                        new ProductController(productService),
                        new BatchController(batchService),
                        new StockController(stockService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listEndpointsDelegateToEveryService() throws Exception {
        when(unitService.findAll()).thenReturn(List.of());
        when(categoryService.findAll()).thenReturn(List.of());
        when(locationService.findAll()).thenReturn(List.of());
        when(supplierService.findAll()).thenReturn(List.of());
        when(productService.findAll()).thenReturn(List.of());
        when(batchService.findAll()).thenReturn(List.of());
        when(stockService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/units")).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/locations")).andExpect(status().isOk());
        mockMvc.perform(get("/api/suppliers")).andExpect(status().isOk());
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/batches")).andExpect(status().isOk());
        mockMvc.perform(get("/api/stock/movements")).andExpect(status().isOk());

        verify(unitService).findAll();
        verify(categoryService).findAll();
        verify(locationService).findAll();
        verify(supplierService).findAll();
        verify(productService).findAll();
        verify(batchService).findAll();
        verify(stockService).findAll();
    }

    @Test
    void createEndpointsReturnCreatedForValidRequests() throws Exception {
        when(unitService.create(any(UnitRequestDTO.class))).thenReturn(new UnitResponseDTO());
        when(categoryService.create(any(CategoryRequestDTO.class))).thenReturn(new CategoryResponseDTO());
        when(locationService.create(any(LocationRequestDTO.class))).thenReturn(new LocationResponseDTO());
        when(supplierService.create(any(SupplierRequestDTO.class))).thenReturn(new SupplierResponseDTO());
        when(productService.create(any(ProductRequestDTO.class))).thenReturn(new ProductResponseDTO());
        when(batchService.create(any(BatchRequestDTO.class))).thenReturn(new BatchResponseDTO());
        when(stockService.createMovement(any(StockMovementRequestDTO.class), any(Long.class)))
                .thenReturn(new StockMovementResponseDTO());

        performPost("/api/units", "{\"code\":\"KG\",\"name\":\"Kilogram\"}");
        performPost("/api/categories", "{\"name\":\"Raw material\"}");
        performPost("/api/locations", "{\"name\":\"Main warehouse\"}");
        performPost("/api/suppliers", "{\"name\":\"Factory supplier\"}");
        performPost("/api/products", """
                {"sku":"RAW-001","name":"Steel","categoryId":1,
                 "locationId":2,"unitId":3,"minimumStock":5.000}
                """);
        performPost("/api/batches", "{\"productId\":1,\"batchCode\":\"LOT-001\"}");
        mockMvc.perform(post("/api/stock/movements")
                        .principal(jwtAuthentication(3L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"batchId":2,"movementType":"INBOUND",
                                 "quantity":10.000,"reason":"Initial receipt"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void updateAndDeactivateEndpointsUseExpectedHttpStatuses() throws Exception {
        when(unitService.update(any(Long.class), any(UnitRequestDTO.class))).thenReturn(new UnitResponseDTO());
        when(categoryService.update(any(Long.class), any(CategoryRequestDTO.class)))
                .thenReturn(new CategoryResponseDTO());
        when(locationService.update(any(Long.class), any(LocationRequestDTO.class)))
                .thenReturn(new LocationResponseDTO());
        when(supplierService.update(any(Long.class), any(SupplierRequestDTO.class)))
                .thenReturn(new SupplierResponseDTO());
        when(productService.update(any(Long.class), any(ProductRequestDTO.class)))
                .thenReturn(new ProductResponseDTO());
        when(batchService.update(any(Long.class), any(BatchRequestDTO.class))).thenReturn(new BatchResponseDTO());

        performPut("/api/units/1", "{\"code\":\"KG\",\"name\":\"Kilogram\"}");
        performPut("/api/categories/1", "{\"name\":\"Raw material\"}");
        performPut("/api/locations/1", "{\"name\":\"Main warehouse\"}");
        performPut("/api/suppliers/1", "{\"name\":\"Factory supplier\"}");
        performPut("/api/products/1", """
                {"sku":"RAW-001","name":"Steel","categoryId":1,
                 "locationId":2,"unitId":3,"minimumStock":5.000}
                """);
        performPut("/api/batches/1", "{\"productId\":1,\"batchCode\":\"LOT-001\"}");

        mockMvc.perform(patch("/api/units/1/deactivate")).andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/categories/1/deactivate")).andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/locations/1/deactivate")).andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/suppliers/1/deactivate")).andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/products/1/deactivate")).andExpect(status().isNoContent());

        verify(unitService).deactivate(1L);
        verify(categoryService).deactivate(1L);
        verify(locationService).deactivate(1L);
        verify(supplierService).deactivate(1L);
        verify(productService).deactivate(1L);
    }

    @Test
    void filteredHistoryEndpointsDelegateTheirPathIdentifiers() throws Exception {
        when(batchService.findByProductId(5L)).thenReturn(List.of());
        when(stockService.findByProductId(5L)).thenReturn(List.of());
        when(stockService.findByBatchId(8L)).thenReturn(List.of());

        mockMvc.perform(get("/api/batches/product/5")).andExpect(status().isOk());
        mockMvc.perform(get("/api/stock/movements/product/5")).andExpect(status().isOk());
        mockMvc.perform(get("/api/stock/movements/batch/8")).andExpect(status().isOk());

        verify(batchService).findByProductId(5L);
        verify(stockService).findByProductId(5L);
        verify(stockService).findByBatchId(8L);
    }

    @Test
    void invalidRequestReturnsStructuredFieldErrors() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.fieldErrors.name").value("Category name is required."));

        mockMvc.perform(post("/api/stock/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"batchId":2,
                                 "movementType":"INBOUND","quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity")
                        .value("Movement quantity must be greater than zero."));
    }

    @Test
    void malformedJsonAndMissingResourcesReturnClientErrors() throws Exception {
        mockMvc.perform(post("/api/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid or malformed."));

        when(productService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"))
                .andExpect(jsonPath("$.path").value("/api/products/99"));
    }

    private void performPost(String path, String content) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated());
    }

    private void performPut(String path, String content) throws Exception {
        mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());
    }

    private JwtAuthenticationToken jwtAuthentication(Long userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("controller-test-token")
                .header("alg", "HS256")
                .subject("controller-test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("userId", userId)
                .build();

        return new JwtAuthenticationToken(jwt);
    }
}

/*
 * RestControllerTests verify the public HTTP contract, DTO validation and error
 * translation while service mocks keep persistence and business logic isolated.
 */
