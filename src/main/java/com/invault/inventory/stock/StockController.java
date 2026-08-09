package com.invault.inventory.stock;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;

import com.invault.inventory.common.dto.PageResponseDTO;
import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;
import com.invault.inventory.common.exception.UnauthorizedException;

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

    @GetMapping("/search")
    public PageResponseDTO<StockMovementResponseDTO> search(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        return stockService.search(productId, batchId, movementType, from, to, page, size);
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
            @Valid @RequestBody StockMovementRequestDTO requestDTO,
            Authentication authentication) {

        return stockService.createMovement(requestDTO, authenticatedUserId(authentication));
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("Authenticated user is required.");
        }

        Number userId = jwt.getClaim("userId");
        if (userId == null || userId.longValue() <= 0) {
            throw new UnauthorizedException("Authenticated user id is missing.");
        }

        return userId.longValue();
    }
}

/*
 * StockController is the only HTTP entry point that changes batch quantity. It
 * obtains the responsible user from the signed JWT and delegates the auditable
 * stock calculation and persistence to StockService.
 */
