package com.invault.inventory.stock;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.invault.inventory.stock.dto.ProductStockResponseDTO;

@RestController
@RequestMapping("/api/stock/summary")
public class StockSummaryController {

    private final StockSummaryService stockSummaryService;

    public StockSummaryController(StockSummaryService stockSummaryService) {
        this.stockSummaryService = stockSummaryService;
    }

    @GetMapping("/products")
    public List<ProductStockResponseDTO> findAllActiveProductStock() {
        return stockSummaryService.findAllActiveProductStock();
    }

    @GetMapping("/products/{productId}")
    public ProductStockResponseDTO findByProductId(@PathVariable Long productId) {
        return stockSummaryService.findByProductId(productId);
    }

    @GetMapping("/low-stock")
    public List<ProductStockResponseDTO> findLowStockProducts() {
        return stockSummaryService.findLowStockProducts();
    }
}
