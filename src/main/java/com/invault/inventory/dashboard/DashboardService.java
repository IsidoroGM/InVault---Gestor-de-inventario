package com.invault.inventory.dashboard;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.batches.BatchRepository;
import com.invault.inventory.batches.BatchStatus;
import com.invault.inventory.dashboard.dto.DashboardResponseDTO;
import com.invault.inventory.products.ProductRepository;
import com.invault.inventory.stock.StockMovementMapper;
import com.invault.inventory.stock.StockMovementRepository;
import com.invault.inventory.stock.StockSummaryService;
import com.invault.inventory.stock.dto.ProductStockResponseDTO;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockMovementMapper stockMovementMapper;
    private final StockSummaryService stockSummaryService;

    public DashboardService(
            ProductRepository productRepository,
            BatchRepository batchRepository,
            StockMovementRepository stockMovementRepository,
            StockMovementMapper stockMovementMapper,
            StockSummaryService stockSummaryService) {

        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockMovementMapper = stockMovementMapper;
        this.stockSummaryService = stockSummaryService;
    }

    public DashboardResponseDTO getDashboard() {
        List<ProductStockResponseDTO> lowStockProducts = stockSummaryService.findLowStockProducts();

        return new DashboardResponseDTO(
                productRepository.countByActiveTrue(),
                lowStockProducts.size(),
                batchRepository.countByStatus(BatchStatus.AVAILABLE),
                lowStockProducts,
                stockMovementMapper.toResponseDTOList(
                        stockMovementRepository.findTop10ByOrderByMovementDateDesc()
                )
        );
    }
}
