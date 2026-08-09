package com.invault.inventory.stock.dto;

import java.math.BigDecimal;

public record ProductStockResponseDTO(
        Long productId,
        String sku,
        String productName,
        String unitCode,
        BigDecimal minimumStock,
        BigDecimal currentStock,
        boolean lowStock) {
}
