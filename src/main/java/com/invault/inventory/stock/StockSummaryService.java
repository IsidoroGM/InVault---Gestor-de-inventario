package com.invault.inventory.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.batches.BatchRepository;
import com.invault.inventory.batches.ProductStockProjection;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;
import com.invault.inventory.stock.dto.ProductStockResponseDTO;

@Service
@Transactional(readOnly = true)
public class StockSummaryService {

    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;

    public StockSummaryService(ProductRepository productRepository, BatchRepository batchRepository) {
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    public List<ProductStockResponseDTO> findAllActiveProductStock() {
        Map<Long, BigDecimal> stockByProduct = batchRepository.calculateAvailableStockByProduct()
                .stream()
                .collect(Collectors.toMap(
                        ProductStockProjection::getProductId,
                        projection -> normalizeStock(projection.getTotalStock())
                ));

        return productRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(product -> toResponseDTO(
                        product,
                        stockByProduct.getOrDefault(product.getId(), BigDecimal.ZERO)
                ))
                .toList();
    }

    public ProductStockResponseDTO findByProductId(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        return toResponseDTO(product, batchRepository.calculateAvailableStock(productId));
    }

    public List<ProductStockResponseDTO> findLowStockProducts() {
        return findAllActiveProductStock().stream()
                .filter(ProductStockResponseDTO::lowStock)
                .toList();
    }

    private ProductStockResponseDTO toResponseDTO(Product product, BigDecimal currentStock) {
        BigDecimal normalizedStock = normalizeStock(currentStock);
        BigDecimal minimumStock = product.getMinimumStock() != null
                ? product.getMinimumStock()
                : BigDecimal.ZERO;

        return new ProductStockResponseDTO(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getUnit() != null ? product.getUnit().getCode() : null,
                minimumStock,
                normalizedStock,
                normalizedStock.compareTo(minimumStock) < 0
        );
    }

    private BigDecimal normalizeStock(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}

/*
 * StockSummaryService derives current stock from AVAILABLE batches; Product never
 * stores a second mutable stock value that could diverge from movement history.
 */
