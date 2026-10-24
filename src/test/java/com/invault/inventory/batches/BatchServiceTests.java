package com.invault.inventory.batches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.batches.dto.BatchRequestDTO;
import com.invault.inventory.batches.dto.BatchResponseDTO;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;
import com.invault.inventory.realtime.BatchChangedEvent;
import com.invault.inventory.realtime.InventoryEventType;

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BatchService batchService;

    @ParameterizedTest
    @ValueSource(strings = {"0.000", "18.500"})
    void updateCannotReassignAnExistingBatchToAnotherProduct(String quantity) {
        BatchRequestDTO request = createRequestDTO();
        request.setProductId(2L);
        Batch batch = existingBatch();
        batch.setQuantity(new BigDecimal(quantity));
        Product originalProduct = batch.getProduct();
        when(batchRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(batch));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> batchService.update(7L, request));

        assertEquals("A batch's product cannot be changed. Create a new batch for the other product.", error.getMessage());
        assertSame(originalProduct, batch.getProduct());
        assertEquals(new BigDecimal(quantity), batch.getQuantity());
        verify(batchRepository, never()).save(any());
        verifyNoInteractions(productRepository, batchMapper, auditService, eventPublisher);
    }

    @Test
    void createAlwaysInitializesBatchWithZeroQuantity() {
        BatchRequestDTO requestDTO = createRequestDTO();
        Product product = existingProduct();
        Batch batch = existingBatch();
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
        Product product = existingProduct();
        Batch batch = existingBatch();
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

        Product product = existingProduct();
        Batch batch = existingBatch();
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
        Product product = existingProduct();
        Batch batch = existingBatch();

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
        Product product = existingProduct();
        Batch batch = existingBatch();
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
        Product product = existingProduct();
        Batch batch = existingBatch();
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
        Product product = existingProduct();
        Batch batch = existingBatch();
        batch.setStatus(BatchStatus.BLOCKED);
        BatchResponseDTO responseDTO = new BatchResponseDTO();
        responseDTO.setId(7L);
        responseDTO.setProductId(1L);
        responseDTO.setQuantity(new BigDecimal("4.000"));

        when(batchRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(batch));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByBatchCodeIgnoreCase("LOT-001")).thenReturn(Optional.empty());
        when(batchRepository.save(batch)).thenReturn(batch);
        when(batchMapper.toResponseDTO(batch)).thenReturn(responseDTO);

        assertSame(responseDTO, batchService.update(7L, requestDTO));
        assertEquals(BatchStatus.AVAILABLE, batch.getStatus());
        ArgumentCaptor<BatchChangedEvent> event = ArgumentCaptor.forClass(BatchChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(InventoryEventType.BATCH_CHANGED, event.getValue().payload().eventType());
        assertEquals(7L, event.getValue().payload().batchId());
        assertEquals(new BigDecimal("4.000"), event.getValue().payload().newBatchQuantity());
    }

    private Product existingProduct() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        return product;
    }

    private Batch existingBatch() {
        Batch batch = new Batch();
        batch.setProduct(existingProduct());
        return batch;
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
