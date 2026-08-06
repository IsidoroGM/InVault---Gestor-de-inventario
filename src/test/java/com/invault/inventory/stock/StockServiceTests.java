package com.invault.inventory.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.invault.inventory.batches.Batch;
import com.invault.inventory.batches.BatchRepository;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;
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
        when(batchRepository.findById(2L)).thenReturn(Optional.of(batch));
        when(userRepository.findById(9L)).thenReturn(Optional.of(authenticatedUser));
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockMovementResponseDTO response = new StockMovementResponseDTO();
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
    }
}

/*
 * StockServiceTests verify that the authenticated JWT identity is the source of
 * movement ownership while stock quantity and traceability values stay consistent.
 */
