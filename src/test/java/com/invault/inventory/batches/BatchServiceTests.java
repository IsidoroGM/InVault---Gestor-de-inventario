package com.invault.inventory.batches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.invault.inventory.batches.dto.BatchRequestDTO;
import com.invault.inventory.batches.dto.BatchResponseDTO;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;

@ExtendWith(MockitoExtension.class)
class BatchServiceTests {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BatchMapper batchMapper;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private BatchService batchService;

    @Test
    void createAlwaysInitializesBatchWithZeroQuantity() {
        BatchRequestDTO requestDTO = createRequestDTO();
        Product product = new Product();
        Batch batch = new Batch();
        batch.setQuantity(new BigDecimal("25.000"));
        BatchResponseDTO responseDTO = new BatchResponseDTO();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCodeIgnoreCase("LOT-001")).thenReturn(Optional.empty());
        when(batchMapper.toEntity(requestDTO, product)).thenReturn(batch);
        when(batchRepository.save(batch)).thenReturn(batch);
        when(batchMapper.toResponseDTO(batch)).thenReturn(responseDTO);

        BatchResponseDTO result = batchService.create(requestDTO);

        assertSame(responseDTO, result);
        assertEquals(0, BigDecimal.ZERO.compareTo(batch.getQuantity()));
        verify(batchRepository).save(batch);
    }

    @Test
    void updatePreservesExistingBatchQuantity() {
        BatchRequestDTO requestDTO = createRequestDTO();
        Product product = new Product();
        Batch batch = new Batch();
        batch.setQuantity(new BigDecimal("18.500"));
        BatchResponseDTO responseDTO = new BatchResponseDTO();

        when(batchRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(batch));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCodeIgnoreCase("LOT-001")).thenReturn(Optional.empty());
        when(batchRepository.save(batch)).thenReturn(batch);
        when(batchMapper.toResponseDTO(batch)).thenReturn(responseDTO);

        BatchResponseDTO result = batchService.update(7L, requestDTO);

        assertSame(responseDTO, result);
        assertEquals(0, new BigDecimal("18.500").compareTo(batch.getQuantity()));
        verify(batchRepository).save(batch);
    }

    @Test
    void updatePreservesExistingStatusWhenRequestOmitsIt() {
        BatchRequestDTO requestDTO = createRequestDTO();
        requestDTO.setStatus(null);

        Product product = new Product();
        Batch batch = new Batch();
        batch.setStatus(BatchStatus.BLOCKED);
        BatchResponseDTO responseDTO = new BatchResponseDTO();

        when(batchRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(batch));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCodeIgnoreCase("LOT-001")).thenReturn(Optional.empty());
        when(batchRepository.save(batch)).thenReturn(batch);
        when(batchMapper.toResponseDTO(batch)).thenReturn(responseDTO);

        BatchResponseDTO result = batchService.update(7L, requestDTO);

        assertSame(responseDTO, result);
        assertEquals(BatchStatus.BLOCKED, batch.getStatus());
        verify(batchRepository).save(batch);
    }

    @Test
    void createCannotStartBatchAsConsumed() {
        BatchRequestDTO requestDTO = createRequestDTO();
        requestDTO.setStatus(BatchStatus.CONSUMED);
        Product product = new Product();
        Batch batch = new Batch();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCodeIgnoreCase("LOT-001")).thenReturn(Optional.empty());
        when(batchMapper.toEntity(requestDTO, product)).thenReturn(batch);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> batchService.create(requestDTO)
        );

        assertEquals(
                "A new batch cannot start as consumed because it has no stock movement history.",
                exception.getMessage()
        );
    }

    @Test
    void updateCannotMarkBatchAsConsumedWithoutStockMovement() {
        BatchRequestDTO requestDTO = createRequestDTO();
        requestDTO.setStatus(BatchStatus.CONSUMED);
        Product product = new Product();
        Batch batch = new Batch();
        batch.setQuantity(new BigDecimal("4.000"));
        batch.setStatus(BatchStatus.AVAILABLE);

        when(batchRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(batch));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCodeIgnoreCase("LOT-001")).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> batchService.update(7L, requestDTO)
        );

        assertEquals(
                "A batch becomes consumed only when a stock movement reduces its quantity to zero.",
                exception.getMessage()
        );
    }

    @Test
    void updateCannotReactivateConsumedBatchWithoutPositiveMovement() {
        BatchRequestDTO requestDTO = createRequestDTO();
        requestDTO.setStatus(BatchStatus.AVAILABLE);
        Product product = new Product();
        Batch batch = new Batch();
        batch.setQuantity(BigDecimal.ZERO);
        batch.setStatus(BatchStatus.CONSUMED);

        when(batchRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(batch));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCodeIgnoreCase("LOT-001")).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> batchService.update(7L, requestDTO)
        );

        assertEquals(
                "A consumed batch becomes available only through an inbound or positive adjustment movement.",
                exception.getMessage()
        );
    }

    @Test
    void updateAllowsBlockedBatchToBecomeAvailable() {
        BatchRequestDTO requestDTO = createRequestDTO();
        requestDTO.setStatus(BatchStatus.AVAILABLE);
        Product product = new Product();
        Batch batch = new Batch();
        batch.setStatus(BatchStatus.BLOCKED);
        BatchResponseDTO responseDTO = new BatchResponseDTO();

        when(batchRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(batch));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCodeIgnoreCase("LOT-001")).thenReturn(Optional.empty());
        when(batchRepository.save(batch)).thenReturn(batch);
        when(batchMapper.toResponseDTO(batch)).thenReturn(responseDTO);

        assertSame(responseDTO, batchService.update(7L, requestDTO));
        assertEquals(BatchStatus.AVAILABLE, batch.getStatus());
    }

    private BatchRequestDTO createRequestDTO() {
        BatchRequestDTO requestDTO = new BatchRequestDTO();
        requestDTO.setProductId(1L);
        requestDTO.setBatchCode(" lot-001 ");
        requestDTO.setStatus(BatchStatus.AVAILABLE);
        requestDTO.setNotes(" Reviewed batch ");
        return requestDTO;
    }
}

/*
 * BatchServiceTests protege la regla principal de stock de InVault: los lotes
 * comienzan en cero y su cantidad solo puede cambiar mediante StockService.
 * Tambien evita que una actualizacion parcial reactive un lote por accidente.
 */
