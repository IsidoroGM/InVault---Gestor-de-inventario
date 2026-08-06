package com.invault.inventory.batches;

import java.util.List;

import org.springframework.stereotype.Component;

import com.invault.inventory.batches.dto.BatchRequestDTO;
import com.invault.inventory.batches.dto.BatchResponseDTO;
import com.invault.inventory.products.Product;

/**
 * Mapper responsible for converting Batch entities into DTOs and DTOs into Batch entities.
 *
 * Batch has a relationship with Product, so this mapper receives the Product entity
 * when creating or updating a Batch.
 */
@Component
public class BatchMapper {

    /**
     * Converts a Batch entity into a BatchResponseDTO.
     *
     * @param batch entity from the database
     * @return response DTO ready to be sent to the client
     */
    public BatchResponseDTO toResponseDTO(Batch batch) {
        if (batch == null) {
            return null;
        }

        Product product = batch.getProduct();

        return new BatchResponseDTO(
                batch.getId(),

                product != null ? product.getId() : null,
                product != null ? product.getSku() : null,
                product != null ? product.getName() : null,

                batch.getBatchCode(),
                batch.getQuantity(),
                batch.getStatus(),
                batch.getNotes(),
                batch.getCreatedAt(),
                batch.getUpdatedAt()
        );
    }

    /**
     * Creates a new Batch entity from a BatchRequestDTO and its related Product.
     *
     * @param requestDTO input data received from the client
     * @param product product loaded from database
     * @return new Batch entity ready to be validated and saved
     */
    public Batch toEntity(BatchRequestDTO requestDTO, Product product) {
        if (requestDTO == null) {
            return null;
        }

        Batch batch = new Batch();

        batch.setProduct(product);
        batch.setBatchCode(requestDTO.getBatchCode());
        batch.setNotes(requestDTO.getNotes());

        // Quantity keeps the entity default. Stock can only change through StockService.

        // Status is optional in the request. If it is null, the entity keeps its default value.
        if (requestDTO.getStatus() != null) {
            batch.setStatus(requestDTO.getStatus());
        }

        return batch;
    }

    /**
     * Updates an existing Batch entity using data from a BatchRequestDTO and its related Product.
     *
     * @param requestDTO input data received from the client
     * @param batch existing entity from the database
     * @param product product loaded from database
     */
    public void updateEntityFromRequestDTO(
            BatchRequestDTO requestDTO,
            Batch batch,
            Product product
    ) {
        if (requestDTO == null || batch == null) {
            return;
        }

        batch.setProduct(product);
        batch.setBatchCode(requestDTO.getBatchCode());
        batch.setNotes(requestDTO.getNotes());

        // Existing quantity is preserved. Stock can only change through StockService.

        // Status is only updated if the client sends a value.
        if (requestDTO.getStatus() != null) {
            batch.setStatus(requestDTO.getStatus());
        }
    }

    /**
     * Converts a list of Batch entities into a list of BatchResponseDTO objects.
     *
     * @param batches list of entities from the database
     * @return list of response DTOs ready to be sent to the client
     */
    public List<BatchResponseDTO> toResponseDTOList(List<Batch> batches) {
        if (batches == null) {
            return List.of();
        }

        return batches.stream()
                .map(this::toResponseDTO)
                .toList();
    }
}

/*
 * Explanation:
 * BatchMapper centralizes conversions between Batch, BatchRequestDTO and BatchResponseDTO.
 * It does not search Product in the database. That responsibility will belong to BatchService.
 * The mapper only receives an already loaded Product and assigns it to the Batch entity.
 * It never maps quantity from requests because stock changes require StockService.
 */
