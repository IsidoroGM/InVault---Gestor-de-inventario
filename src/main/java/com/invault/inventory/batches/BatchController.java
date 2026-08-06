package com.invault.inventory.batches;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.invault.inventory.batches.dto.BatchRequestDTO;
import com.invault.inventory.batches.dto.BatchResponseDTO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public List<BatchResponseDTO> findAll() {
        return batchService.findAll();
    }

    @GetMapping("/{id}")
    public BatchResponseDTO findById(@PathVariable Long id) {
        return batchService.findById(id);
    }

    @GetMapping("/product/{productId}")
    public List<BatchResponseDTO> findByProductId(@PathVariable Long productId) {
        return batchService.findByProductId(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BatchResponseDTO create(@Valid @RequestBody BatchRequestDTO requestDTO) {
        return batchService.create(requestDTO);
    }

    @PutMapping("/{id}")
    public BatchResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody BatchRequestDTO requestDTO) {

        return batchService.update(id, requestDTO);
    }
}

/*
 * BatchController exposes batch metadata without accepting a quantity field;
 * stock changes remain exclusively available through StockController.
 */
