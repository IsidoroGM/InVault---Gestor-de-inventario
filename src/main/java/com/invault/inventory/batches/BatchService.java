package com.invault.inventory.batches;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.audit.AuditAction;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.batches.dto.BatchRequestDTO;
import com.invault.inventory.batches.dto.BatchResponseDTO;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;

@Service
@Transactional
public class BatchService {

    private final BatchRepository batchRepository;
    private final ProductRepository productRepository;
    private final BatchMapper batchMapper;
    private final AuditService auditService;

    public BatchService(
            BatchRepository batchRepository,
            ProductRepository productRepository,
            BatchMapper batchMapper,
            AuditService auditService) {

        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.batchMapper = batchMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BatchResponseDTO> findAll() {
        List<Batch> batches = batchRepository.findAllByOrderByBatchCodeAsc();
        return batchMapper.toResponseDTOList(batches);
    }

    @Transactional(readOnly = true)
    public List<BatchResponseDTO> findByProductId(Long productId) {
        validateProductExists(productId);

        List<Batch> batches = batchRepository.findByProductIdOrderByBatchCodeAsc(productId);
        return batchMapper.toResponseDTOList(batches);
    }

    @Transactional(readOnly = true)
    public BatchResponseDTO findById(Long id) {
        Batch batch = findBatchEntityById(id);
        return batchMapper.toResponseDTO(batch);
    }

    public BatchResponseDTO create(BatchRequestDTO requestDTO) {
        Product product = findActiveProductById(requestDTO.getProductId());

        String normalizedBatchCode = normalizeBatchCode(requestDTO.getBatchCode());

        validateBatchCodeIsUnique(normalizedBatchCode, null);

        Batch batch = batchMapper.toEntity(requestDTO, product);

        // Relacionamos el lote con el producto real cargado desde base de datos.
        batch.setProduct(product);

        // Normalizamos los campos principales antes de guardar.
        batch.setBatchCode(normalizedBatchCode);
        batch.setQuantity(BigDecimal.ZERO);
        validateNewBatchStatus(requestDTO.getStatus());
        batch.setStatus(requestDTO.getStatus());
        batch.setNotes(normalizeText(requestDTO.getNotes()));

        Batch savedBatch = batchRepository.save(batch);
        BatchResponseDTO response = batchMapper.toResponseDTO(savedBatch);
        auditService.registerMutation(
                AuditAction.CREATED, "Batch", response.getId(), "Batch created.", null, response);
        return response;
    }

    public BatchResponseDTO update(Long id, BatchRequestDTO requestDTO) {
        Batch batch = findBatchEntityForUpdate(id);
        BatchResponseDTO before = batchMapper.toResponseDTO(batch);
        Product product = findActiveProductById(requestDTO.getProductId());

        String normalizedBatchCode = normalizeBatchCode(requestDTO.getBatchCode());

        validateBatchCodeIsUnique(normalizedBatchCode, id);

        // Actualizamos los campos editables del lote.
        batch.setProduct(product);
        batch.setBatchCode(normalizedBatchCode);
        batch.setNotes(normalizeText(requestDTO.getNotes()));

        // Un estado omitido conserva el valor actual y evita reactivar el lote.
        if (requestDTO.getStatus() != null) {
            validateStatusTransition(batch, requestDTO.getStatus());
            batch.setStatus(requestDTO.getStatus());
        }

        // La cantidad existente se conserva. Solo StockService puede modificarla.

        Batch updatedBatch = batchRepository.save(batch);
        BatchResponseDTO after = batchMapper.toResponseDTO(updatedBatch);
        auditService.registerMutation(
                AuditAction.UPDATED, "Batch", after.getId(), "Batch updated.", before, after);
        return after;
    }

    private Batch findBatchEntityById(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + id));
    }

    private Batch findBatchEntityForUpdate(Long id) {
        return batchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + id));
    }

    private Product findActiveProductById(Long productId) {
        if (productId == null) {
            throw new BadRequestException("Product id is required.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (Boolean.FALSE.equals(product.getActive())) {
            throw new BadRequestException("Product with id " + productId + " is inactive.");
        }

        return product;
    }

    private void validateProductExists(Long productId) {
        if (productId == null) {
            throw new BadRequestException("Product id is required.");
        }

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
    }

    private void validateBatchCodeIsUnique(String batchCode, Long currentBatchId) {
        batchRepository.findByBatchCodeIgnoreCase(batchCode)
                .filter(existingBatch -> isDifferentBatch(existingBatch, currentBatchId))
                .ifPresent(existingBatch -> {
                    throw new BadRequestException("A batch with code '" + batchCode + "' already exists.");
                });
    }

    private boolean isDifferentBatch(Batch existingBatch, Long currentBatchId) {
        return currentBatchId == null || !existingBatch.getId().equals(currentBatchId);
    }

    private void validateStatusTransition(Batch batch, BatchStatus requestedStatus) {
        BatchStatus currentStatus = batch.getStatus();
        if (currentStatus == requestedStatus) {
            return;
        }

        if (requestedStatus == BatchStatus.CONSUMED) {
            throw new BadRequestException(
                    "A batch becomes consumed only when a stock movement reduces its quantity to zero.");
        }

        if (currentStatus == BatchStatus.CONSUMED && requestedStatus == BatchStatus.AVAILABLE) {
            throw new BadRequestException(
                    "A consumed batch becomes available only through an inbound or positive adjustment movement.");
        }

        boolean allowed = switch (currentStatus) {
            case AVAILABLE -> requestedStatus == BatchStatus.BLOCKED
                    || requestedStatus == BatchStatus.INACTIVE;
            case BLOCKED -> requestedStatus == BatchStatus.AVAILABLE
                    || requestedStatus == BatchStatus.INACTIVE;
            case CONSUMED -> requestedStatus == BatchStatus.INACTIVE;
            case INACTIVE -> requestedStatus == BatchStatus.AVAILABLE
                    || requestedStatus == BatchStatus.BLOCKED;
        };

        if (!allowed) {
            throw new BadRequestException(
                    "Batch status transition from " + currentStatus + " to " + requestedStatus + " is not allowed.");
        }
    }

    private void validateNewBatchStatus(BatchStatus requestedStatus) {
        if (requestedStatus == BatchStatus.CONSUMED) {
            throw new BadRequestException(
                    "A new batch cannot start as consumed because it has no stock movement history.");
        }
    }

    private String normalizeBatchCode(String value) {
        String normalizedValue = normalizeText(value);

        if (normalizedValue == null) {
            return null;
        }

        return normalizedValue.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}

/*
 * BatchService contiene la lógica de negocio para los lotes de inventario.
 *
 * Esta clase permite listar, consultar, crear y actualizar lotes sin exponer
 * directamente la entidad JPA Batch. También valida que el código de lote sea
 * único y que el producto relacionado exista y esté activo.
 *
 * Los lotes nuevos comienzan con cantidad cero y una actualización de sus datos
 * nunca cambia la cantidad existente. Todo cambio de stock debe pasar por
 * StockService para crear el StockMovement auditable correspondiente.
 */
