package com.invault.inventory.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.batches.BatchRepository;
import com.invault.inventory.batches.ProductStockProjection;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;
import com.invault.inventory.stock.dto.ProductStockResponseDTO;
import com.invault.inventory.units.Unit;

@ExtendWith(MockitoExtension.class)
class StockSummaryServiceTests {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BatchRepository batchRepository;

    @Test
    void derivesAvailableStockAndDetectsProductsBelowTheirMinimum() {
        Product screws = product(1L, "SKU-1", "Screws", "PCS", "10.000");
        Product oil = product(2L, "SKU-2", "Oil", "L", "5.000");
        ProductStockProjection screwsStock = projection(1L, new BigDecimal("7.500"));

        when(productRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(oil, screws));
        when(batchRepository.calculateAvailableStockByProduct()).thenReturn(List.of(screwsStock));

        List<ProductStockResponseDTO> result = new StockSummaryService(productRepository, batchRepository)
                .findAllActiveProductStock();

        assertEquals(2, result.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).currentStock()));
        assertTrue(result.get(0).lowStock());
        assertEquals(0, new BigDecimal("7.500").compareTo(result.get(1).currentStock()));
        assertTrue(result.get(1).lowStock());
    }

    @Test
    void productAtItsMinimumIsNotLowStock() {
        Product product = product(4L, "SKU-4", "Boxes", "BOX", "3.000");
        when(productRepository.findById(4L)).thenReturn(java.util.Optional.of(product));
        when(batchRepository.calculateAvailableStock(4L)).thenReturn(new BigDecimal("3.000"));

        ProductStockResponseDTO result = new StockSummaryService(productRepository, batchRepository)
                .findByProductId(4L);

        assertFalse(result.lowStock());
    }

    private Product product(Long id, String sku, String name, String unitCode, String minimum) {
        Unit unit = new Unit(unitCode, unitCode, unitCode.toLowerCase(), null);
        Product product = new Product(sku, name, null, null, null, unit, new BigDecimal(minimum));
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private ProductStockProjection projection(Long productId, BigDecimal totalStock) {
        return new ProductStockProjection() {
            @Override
            public Long getProductId() {
                return productId;
            }

            @Override
            public BigDecimal getTotalStock() {
                return totalStock;
            }
        };
    }
}
