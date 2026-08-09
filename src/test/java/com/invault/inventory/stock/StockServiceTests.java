package com.invault.inventory.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.batches.Batch;
import com.invault.inventory.batches.BatchRepository;
import com.invault.inventory.batches.BatchStatus;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;
import com.invault.inventory.realtime.InventoryEventType;
import com.invault.inventory.realtime.StockMovementRecordedEvent;
import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;
import com.invault.inventory.suppliers.SupplierRepository;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@ExtendWith(MockitoExtension.class)
class StockServiceTests {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private StockMovementMapper stockMovementMapper;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private StockService stockService;

    @Test
    void createMovementUsesAuthenticatedUserAndUpdatesBatchTraceably() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);

        Batch batch = new Batch(product, "LOT-001", new BigDecimal("5.000"), null);
        ReflectionTestUtils.setField(batch, "id", 2L);

        User authenticatedUser = new User();
        ReflectionTestUtils.setField(authenticatedUser, "id", 9L);

        StockMovementRequestDTO request = new StockMovementRequestDTO();
        request.setProductId(1L);
        request.setBatchId(2L);
        request.setMovementType(MovementType.INBOUND);
        request.setQuantity(new BigDecimal("3.000"));
        request.setReason(" Authenticated receipt ");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(batch));
        when(userRepository.findById(9L)).thenReturn(Optional.of(authenticatedUser));
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockMovementResponseDTO response = new StockMovementResponseDTO();
        response.setId(10L);
        response.setProductId(1L);
        response.setProductSku("RAW-001");
        response.setBatchId(2L);
        response.setBatchCode("LOT-001");
        response.setMovementType(MovementType.INBOUND);
        response.setQuantity(new BigDecimal("3.000"));
        response.setPreviousBatchQuantity(new BigDecimal("5.000"));
        response.setNewBatchQuantity(new BigDecimal("8.000"));
        response.setMovementDate(LocalDateTime.of(2026, 8, 6, 19, 30));
        when(stockMovementMapper.toResponseDTO(any(StockMovement.class))).thenReturn(response);

        assertSame(response, stockService.createMovement(request, 9L));
        assertEquals(new BigDecimal("8.000"), batch.getQuantity());

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());

        StockMovement savedMovement = movementCaptor.getValue();
        assertSame(authenticatedUser, savedMovement.getUser());
        assertEquals(new BigDecimal("5.000"), savedMovement.getPreviousBatchQuantity());
        assertEquals(new BigDecimal("8.000"), savedMovement.getNewBatchQuantity());
        assertEquals("Authenticated receipt", savedMovement.getReason());
        verify(userRepository).findById(9L);

        ArgumentCaptor<StockMovementRecordedEvent> eventCaptor =
                ArgumentCaptor.forClass(StockMovementRecordedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        assertEquals(InventoryEventType.STOCK_UPDATED, eventCaptor.getValue().payload().eventType());
        assertEquals(10L, eventCaptor.getValue().payload().movementId());
        assertEquals(1L, eventCaptor.getValue().payload().productId());
        assertEquals(2L, eventCaptor.getValue().payload().batchId());
        assertEquals(new BigDecimal("8.000"), eventCaptor.getValue().payload().newBatchQuantity());
    }

    @Test
    void movementThatLeavesZeroStockMarksBatchAsConsumed() {
        Product product = product(1L);
        Batch batch = batch(product, 2L, BatchStatus.AVAILABLE, "5.000");
        User user = user(9L);
        StockMovementRequestDTO request = request(MovementType.OUTBOUND, "5.000");

        stubSuccessfulMovement(product, batch, user);

        stockService.createMovement(request, 9L);

        assertEquals(0, BigDecimal.ZERO.compareTo(batch.getQuantity()));
        assertEquals(BatchStatus.CONSUMED, batch.getStatus());
        verify(batchRepository).findByIdForUpdate(2L);
    }

    @Test
    void positiveMovementReopensConsumedBatch() {
        Product product = product(1L);
        Batch batch = batch(product, 2L, BatchStatus.CONSUMED, "0.000");
        User user = user(9L);
        StockMovementRequestDTO request = request(MovementType.POSITIVE_ADJUSTMENT, "2.500");

        stubSuccessfulMovement(product, batch, user);

        stockService.createMovement(request, 9L);

        assertEquals(new BigDecimal("2.500"), batch.getQuantity());
        assertEquals(BatchStatus.AVAILABLE, batch.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = BatchStatus.class, names = {"BLOCKED", "INACTIVE"})
    void blockedAndInactiveBatchesRejectAllMovements(BatchStatus status) {
        Product product = product(1L);
        Batch batch = batch(product, 2L, status, "5.000");
        StockMovementRequestDTO request = request(MovementType.INBOUND, "1.000");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(batch));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> stockService.createMovement(request, 9L)
        );

        assertEquals(
                status == BatchStatus.BLOCKED
                        ? "Blocked batches do not accept stock movements."
                        : "Inactive batches do not accept stock movements.",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @EnumSource(value = MovementType.class, names = {"OUTBOUND", "NEGATIVE_ADJUSTMENT"})
    void consumedBatchRejectsMovementsThatReduceStock(MovementType movementType) {
        Product product = product(1L);
        Batch batch = batch(product, 2L, BatchStatus.CONSUMED, "0.000");
        StockMovementRequestDTO request = request(movementType, "1.000");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(batch));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> stockService.createMovement(request, 9L)
        );

        assertEquals(
                "Consumed batches only accept inbound or positive adjustment movements.",
                exception.getMessage()
        );
    }

    private void stubSuccessfulMovement(Product product, Batch batch, User user) {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(batchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(batch));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementMapper.toResponseDTO(any(StockMovement.class)))
                .thenReturn(new StockMovementResponseDTO());
    }

    private Product product(Long id) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private Batch batch(Product product, Long id, BatchStatus status, String quantity) {
        Batch batch = new Batch(product, "LOT-001", new BigDecimal(quantity), null);
        ReflectionTestUtils.setField(batch, "id", id);
        batch.setStatus(status);
        return batch;
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private StockMovementRequestDTO request(MovementType movementType, String quantity) {
        StockMovementRequestDTO request = new StockMovementRequestDTO();
        request.setProductId(1L);
        request.setBatchId(2L);
        request.setMovementType(movementType);
        request.setQuantity(new BigDecimal(quantity));
        return request;
    }
}

/*
 * StockServiceTests verify that the authenticated JWT identity is the source of
 * movement ownership while stock quantity and traceability values stay consistent.
 */
