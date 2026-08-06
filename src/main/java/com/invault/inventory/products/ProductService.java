package com.invault.inventory.products;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.categories.Category;
import com.invault.inventory.categories.CategoryRepository;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.locations.Location;
import com.invault.inventory.locations.LocationRepository;
import com.invault.inventory.products.dto.ProductRequestDTO;
import com.invault.inventory.products.dto.ProductResponseDTO;
import com.invault.inventory.units.Unit;
import com.invault.inventory.units.UnitRepository;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final UnitRepository unitRepository;
    private final ProductMapper productMapper;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            UnitRepository unitRepository,
            ProductMapper productMapper) {

        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.unitRepository = unitRepository;
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findAll() {
        List<Product> products = productRepository.findAllByOrderByNameAsc();
        return productMapper.toResponseDTOList(products);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findAllActive() {
        List<Product> products = productRepository.findByActiveTrueOrderByNameAsc();
        return productMapper.toResponseDTOList(products);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO findById(Long id) {
        Product product = findProductEntityById(id);
        return productMapper.toResponseDTO(product);
    }

    public ProductResponseDTO create(ProductRequestDTO requestDTO) {
        String normalizedSku = normalizeSku(requestDTO.getSku());
        String normalizedName = normalizeText(requestDTO.getName());
        BigDecimal normalizedMinimumStock = normalizeMinimumStock(requestDTO.getMinimumStock());

        validateSkuIsUnique(normalizedSku, null);

        Category category = findActiveCategoryById(requestDTO.getCategoryId());
        Location location = findActiveLocationById(requestDTO.getLocationId());
        Unit unit = findActiveUnitById(requestDTO.getUnitId());

        Product product = new Product();

        // Product no guarda stock actual. El stock se calculará más adelante desde Batch.
        product.setSku(normalizedSku);
        product.setName(normalizedName);
        product.setDescription(normalizeText(requestDTO.getDescription()));
        product.setCategory(category);
        product.setLocation(location);
        product.setUnit(unit);
        product.setMinimumStock(normalizedMinimumStock);

        if (requestDTO.getActive() != null) {
            product.setActive(requestDTO.getActive());
        } else {
            product.setActive(true);
        }

        Product savedProduct = productRepository.save(product);
        return productMapper.toResponseDTO(savedProduct);
    }

    public ProductResponseDTO update(Long id, ProductRequestDTO requestDTO) {
        Product product = findProductEntityById(id);

        String normalizedSku = normalizeSku(requestDTO.getSku());
        String normalizedName = normalizeText(requestDTO.getName());
        BigDecimal normalizedMinimumStock = normalizeMinimumStock(requestDTO.getMinimumStock());

        validateSkuIsUnique(normalizedSku, id);

        Category category = findActiveCategoryById(requestDTO.getCategoryId());
        Location location = findActiveLocationById(requestDTO.getLocationId());
        Unit unit = findActiveUnitById(requestDTO.getUnitId());

        // Actualizamos los campos editables de Product.
        product.setSku(normalizedSku);
        product.setName(normalizedName);
        product.setDescription(normalizeText(requestDTO.getDescription()));
        product.setCategory(category);
        product.setLocation(location);
        product.setUnit(unit);
        product.setMinimumStock(normalizedMinimumStock);

        if (requestDTO.getActive() != null) {
            product.setActive(requestDTO.getActive());
        }

        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponseDTO(updatedProduct);
    }

    public void deactivate(Long id) {
        Product product = findProductEntityById(id);

        if (Boolean.FALSE.equals(product.getActive())) {
            throw new BadRequestException("Product is already inactive.");
        }

        product.setActive(false);
        productRepository.save(product);
    }

    private Product findProductEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private Category findActiveCategoryById(Long categoryId) {
        if (categoryId == null) {
            throw new BadRequestException("Category id is required.");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));

        if (Boolean.FALSE.equals(category.getActive())) {
            throw new BadRequestException("Category with id " + categoryId + " is inactive.");
        }

        return category;
    }

    private Location findActiveLocationById(Long locationId) {
        if (locationId == null) {
            throw new BadRequestException("Location id is required.");
        }

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + locationId));

        if (Boolean.FALSE.equals(location.getActive())) {
            throw new BadRequestException("Location with id " + locationId + " is inactive.");
        }

        return location;
    }

    private Unit findActiveUnitById(Long unitId) {
        if (unitId == null) {
            throw new BadRequestException("Unit id is required.");
        }

        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found with id: " + unitId));

        if (Boolean.FALSE.equals(unit.getActive())) {
            throw new BadRequestException("Unit with id " + unitId + " is inactive.");
        }

        return unit;
    }

    private void validateSkuIsUnique(String sku, Long currentProductId) {
        productRepository.findBySkuIgnoreCase(sku)
                .filter(existingProduct -> isDifferentProduct(existingProduct, currentProductId))
                .ifPresent(existingProduct -> {
                    throw new BadRequestException("A product with SKU '" + sku + "' already exists.");
                });
    }

    private boolean isDifferentProduct(Product existingProduct, Long currentProductId) {
        return currentProductId == null || !existingProduct.getId().equals(currentProductId);
    }

    private BigDecimal normalizeMinimumStock(BigDecimal minimumStock) {
        if (minimumStock == null) {
            return BigDecimal.ZERO;
        }

        if (minimumStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Minimum stock cannot be negative.");
        }

        return minimumStock;
    }

    private String normalizeSku(String value) {
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
 * ProductService contiene la lógica de negocio principal para productos.
 *
 * Esta clase permite listar, consultar, crear, actualizar y desactivar productos
 * sin exponer directamente la entidad JPA Product. También valida que el SKU sea
 * único, carga las relaciones reales con Category, Location y Unit, y evita crear
 * productos vinculados a catálogos inactivos.
 *
 * Product no almacena stock actual. En InVault el stock se calculará a partir
 * de los lotes disponibles, manteniendo la trazabilidad mediante movimientos
 * de stock en fases posteriores.
 */