package com.invault.inventory.dashboard.dto;

import java.util.List;

import com.invault.inventory.stock.dto.ProductStockResponseDTO;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;

public record DashboardResponseDTO(
        long activeProductCount,
        long lowStockProductCount,
        long availableBatchCount,
        List<ProductStockResponseDTO> lowStockProducts,
        List<StockMovementResponseDTO> recentMovements) {
}
