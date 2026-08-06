package com.invault.inventory.stock;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stock/movements")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public List<StockMovementResponseDTO> findAll() {
        return stockService.findAll();
    }

    @GetMapping("/{id}")
    public StockMovementResponseDTO findById(@PathVariable Long id) {
        return stockService.findById(id);
    }

    @GetMapping("/product/{productId}")
    public List<StockMovementResponseDTO> findByProductId(@PathVariable Long productId) {
        return stockService.findByProductId(productId);
    }

    @GetMapping("/batch/{batchId}")
    public List<StockMovementResponseDTO> findByBatchId(@PathVariable Long batchId) {
        return stockService.findByBatchId(batchId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponseDTO createMovement(
            @Valid @RequestBody StockMovementRequestDTO requestDTO) {

        return stockService.createMovement(requestDTO);
    }
}

/*
 * StockController is the only HTTP entry point that changes batch quantity. It
 * delegates the auditable stock calculation and persistence to StockService.
 */
