package com.invault.inventory.stock;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.batches.Batch;
import com.invault.inventory.batches.BatchRepository;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.products.Product;
import com.invault.inventory.products.ProductRepository;
import com.invault.inventory.stock.dto.StockMovementRequestDTO;
import com.invault.inventory.stock.dto.StockMovementResponseDTO;
import com.invault.inventory.suppliers.Supplier;
import com.invault.inventory.suppliers.SupplierRepository;
import com.invault.inventory.users.User;
import com.invault.inventory.users.UserRepository;

@Service
@Transactional
public class StockService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementMapper stockMovementMapper;

    public StockService(
            StockMovementRepository stockMovementRepository,
            ProductRepository productRepository,
            BatchRepository batchRepository,
            UserRepository userRepository,
            SupplierRepository supplierRepository,
            StockMovementMapper stockMovementMapper) {

        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.userRepository = userRepository;
        this.supplierRepository = supplierRepository;
        this.stockMovementMapper = stockMovementMapper;
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponseDTO> findAll() {
        List<StockMovement> movements = stockMovementRepository.findAll();

        return stockMovementMapper.toResponseDTOList(
                orderByIdDescending(movements)
        );
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponseDTO> findByProductId(Long productId) {
        validateProductExists(productId);

        List<StockMovement> movements = stockMovementRepository.findByProductId(productId);

        return stockMovementMapper.toResponseDTOList(
                orderByIdDescending(movements)
        );
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponseDTO> findByBatchId(Long batchId) {
        validateBatchExists(batchId);

        List<StockMovement> movements = stockMovementRepository.findByBatchId(batchId);

        return stockMovementMapper.toResponseDTOList(
                orderByIdDescending(movements)
        );
    }

    @Transactional(readOnly = true)
    public StockMovementResponseDTO findById(Long id) {
        StockMovement stockMovement = findStockMovementEntityById(id);
        return stockMovementMapper.toResponseDTO(stockMovement);
    }

    public StockMovementResponseDTO createMovement(StockMovementRequestDTO requestDTO) {
        Product product = findActiveProductById(requestDTO.getProductId());
        Batch batch = findBatchEntityById(requestDTO.getBatchId());
        User user = findUserEntityById(requestDTO.getUserId());
        Supplier supplier = findOptionalSupplierById(requestDTO.getSupplierId());

        validateBatchBelongsToProduct(batch, product);

        MovementType movementType = requestDTO.getMovementType();
        BigDecimal movementQuantity = normalizeMovementQuantity(requestDTO.getQuantity());
        BigDecimal previousBatchQuantity = normalizeCurrentBatchQuantity(batch.getQuantity());

        BigDecimal newBatchQuantity = calculateNewBatchQuantity(
                movementType,
                previousBatchQuantity,
                movementQuantity
        );

        validateNewBatchQuantityIsNotNegative(newBatchQuantity);

        // Actualizamos la cantidad del lote. Product no almacena stock actual.
        batch.setQuantity(newBatchQuantity);
        batchRepository.save(batch);

        /*
         * StockMovement no expone setters para sus campos principales.
         * Por eso se crea usando el constructor completo definido en la entidad.
         */
        StockMovement stockMovement = new StockMovement(
                product,
                batch,
                user,
                supplier,
                movementType,
                movementQuantity,
                previousBatchQuantity,
                newBatchQuantity,
                normalizeText(requestDTO.getReason())
        );

        StockMovement savedMovement = stockMovementRepository.save(stockMovement);
        return stockMovementMapper.toResponseDTO(savedMovement);
    }

    private StockMovement findStockMovementEntityById(Long id) {
        return stockMovementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock movement not found with id: " + id));
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

    private Batch findBatchEntityById(Long batchId) {
        if (batchId == null) {
            throw new BadRequestException("Batch id is required.");
        }

        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found with id: " + batchId));
    }

    private User findUserEntityById(Long userId) {
        if (userId == null) {
            throw new BadRequestException("User id is required.");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private Supplier findOptionalSupplierById(Long supplierId) {
        if (supplierId == null) {
            return null;
        }

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + supplierId));

        if (Boolean.FALSE.equals(supplier.getActive())) {
            throw new BadRequestException("Supplier with id " + supplierId + " is inactive.");
        }

        return supplier;
    }

    private void validateProductExists(Long productId) {
        if (productId == null) {
            throw new BadRequestException("Product id is required.");
        }

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
    }

    private void validateBatchExists(Long batchId) {
        if (batchId == null) {
            throw new BadRequestException("Batch id is required.");
        }

        if (!batchRepository.existsById(batchId)) {
            throw new ResourceNotFoundException("Batch not found with id: " + batchId);
        }
    }

    private void validateBatchBelongsToProduct(Batch batch, Product product) {
        if (batch.getProduct() == null || batch.getProduct().getId() == null) {
            throw new BadRequestException("Batch is not linked to a valid product.");
        }

        if (!batch.getProduct().getId().equals(product.getId())) {
            throw new BadRequestException("Batch does not belong to the selected product.");
        }
    }

    private BigDecimal normalizeMovementQuantity(BigDecimal quantity) {
        if (quantity == null) {
            throw new BadRequestException("Movement quantity is required.");
        }

        /*
         * En InVault la cantidad del movimiento siempre es positiva.
         * El tipo de movimiento decide si suma o resta stock.
         */
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Movement quantity must be greater than zero.");
        }

        return quantity;
    }

    private BigDecimal normalizeCurrentBatchQuantity(BigDecimal batchQuantity) {
        if (batchQuantity == null) {
            throw new BadRequestException("Current batch quantity is required.");
        }

        if (batchQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Current batch quantity cannot be negative.");
        }

        return batchQuantity;
    }

    private BigDecimal calculateNewBatchQuantity(
            MovementType movementType,
            BigDecimal previousBatchQuantity,
            BigDecimal movementQuantity) {

        if (movementType == null) {
            throw new BadRequestException("Movement type is required.");
        }

        return switch (movementType) {
            case INBOUND, POSITIVE_ADJUSTMENT -> previousBatchQuantity.add(movementQuantity);
            case OUTBOUND, NEGATIVE_ADJUSTMENT -> previousBatchQuantity.subtract(movementQuantity);
        };
    }

    private void validateNewBatchQuantityIsNotNegative(BigDecimal newBatchQuantity) {
        if (newBatchQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Stock movement would result in negative stock.");
        }
    }

    private List<StockMovement> orderByIdDescending(List<StockMovement> movements) {
        return movements.stream()
                .sorted(Comparator.comparing(StockMovement::getId).reversed())
                .toList();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}

/*
 * StockService contiene la lógica de negocio para los movimientos de stock.
 *
 * Esta clase registra entradas, salidas y ajustes de inventario de forma trazable.
 * Cada movimiento calcula la cantidad anterior y la cantidad nueva del lote,
 * actualiza Batch.quantity y guarda un StockMovement con producto, lote, usuario,
 * proveedor opcional, tipo de movimiento, cantidad y motivo.
 *
 * En InVault el stock no se modifica directamente desde Product. El stock se
 * controla mediante lotes y movimientos auditables, lo que permite reconstruir
 * el historial de cambios del inventario.
 */