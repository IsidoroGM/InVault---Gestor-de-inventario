package com.invault.inventory.stock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ConflictException;
import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;

import tools.jackson.databind.ObjectMapper;

@Service
public class StockMovementCommandService {
    private final JdbcTemplate jdbc;
    private final StockService stockService;
    private final ObjectMapper json;

    public StockMovementCommandService(JdbcTemplate jdbc, StockService stockService, ObjectMapper json) {
        this.jdbc = jdbc;
        this.stockService = stockService;
        this.json = json;
    }

    @Transactional
    public StockMovementResponseDTO execute(StockMovementRequestDTO request, Long userId, String requestKey) {
        if (requestKey == null || !requestKey.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new BadRequestException("Idempotency-Key must be a UUID. Update the application and retry.");
        }
        if (userId == null || userId <= 0) {
            throw new BadRequestException("Authenticated user id is required.");
        }
        String id = userId + ":" + requestKey.toLowerCase(Locale.ROOT);
        String hash = fingerprint(request);
        // A duplicate INSERT takes the existing primary-key row's exclusive InnoDB lock.
        // Reservation, business changes, audit and saved response share the same transaction.
        jdbc.update("""
                INSERT INTO stock_movement_requests (id, user_id, request_hash, created_at)
                VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE id = id
                """, id, userId, hash, LocalDateTime.now(ZoneOffset.UTC));
        StoredRequest stored = jdbc.queryForObject("""
                SELECT request_hash, response_body FROM stock_movement_requests WHERE id = ? FOR UPDATE
                """, (row, number) -> new StoredRequest(row.getString(1), row.getString(2)), id);
        if (!hash.equals(stored.hash())) {
            throw new ConflictException("This Idempotency-Key was already used for a different stock movement.");
        }
        if (stored.response() != null) {
            return json.readValue(stored.response(), StockMovementResponseDTO.class);
        }
        StockMovementResponseDTO response = stockService.createMovement(request, userId);
        jdbc.update("UPDATE stock_movement_requests SET movement_id = ?, response_body = ? WHERE id = ?",
                response.getId(), json.writeValueAsString(response), id);
        return response;
    }

    private String fingerprint(StockMovementRequestDTO request) {
        if (request.getQuantity() == null) {
            throw new BadRequestException("Movement quantity is required.");
        }
        String canonical = json.writeValueAsString(new RequestPayload(
                request.getProductId(), request.getBatchId(), request.getSupplierId(), request.getMovementType(),
                request.getQuantity().stripTrailingZeros().toPlainString(),
                request.getReason() == null ? null : request.getReason().trim()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record StoredRequest(String hash, String response) {}
    private record RequestPayload(Long productId, Long batchId, Long supplierId, MovementType movementType,
            String quantity, String reason) {}
}
