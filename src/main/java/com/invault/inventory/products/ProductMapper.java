package com.invault.inventory.products;

import java.util.List;

import org.springframework.stereotype.Component;

import com.invault.inventory.categories.Category;
import com.invault.inventory.locations.Location;
import com.invault.inventory.products.dto.ProductRequestDTO;
import com.invault.inventory.products.dto.ProductResponseDTO;
import com.invault.inventory.units.Unit;

/**
 * Mapper responsible for converting Product entities into DTOs and DTOs into Product entities.
 *
 * Product has relationships with Category, Location and Unit, so this mapper receives those
 * related entities when creating or updating a Product.
 */
@Component
public class ProductMapper {

    /**
     * Converts a Product entity into a ProductResponseDTO.
     *
     * @param product entity from the database
     * @return response DTO ready to be sent to the client
     */
    public ProductResponseDTO toResponseDTO(Product product) {
        if (product == null) {
            return null;
        }

        Category category = product.getCategory();
        Location location = product.getLocation();
        Unit unit = product.getUnit();

        return new ProductResponseDTO(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),

                category != null ? category.getId() : null,
                category != null ? category.getName() : null,

                location != null ? location.getId() : null,
                location != null ? location.getName() : null,

                unit != null ? unit.getId() : null,
                unit != null ? unit.getCode() : null,
                unit != null ? unit.getName() : null,
                unit != null ? unit.getSymbol() : null,

                product.getMinimumStock(),
                product.getActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    /**
     * Creates a new Product entity from a ProductRequestDTO and its related entities.
     *
     * @param requestDTO input data received from the client
     * @param category category loaded from database
     * @param location location loaded from database
     * @param unit unit loaded from database
     * @return new Product entity ready to be validated and saved
     */
    public Product toEntity(
            ProductRequestDTO requestDTO,
            Category category,
            Location location,
            Unit unit
    ) {
        if (requestDTO == null) {
            return null;
        }

        Product product = new Product();

        product.setSku(requestDTO.getSku());
        product.setName(requestDTO.getName());
        product.setDescription(requestDTO.getDescription());
        product.setCategory(category);
        product.setLocation(location);
        product.setUnit(unit);
        product.setMinimumStock(requestDTO.getMinimumStock());

        // Active is optional in the request. If it is null, the entity keeps its default value.
        if (requestDTO.getActive() != null) {
            product.setActive(requestDTO.getActive());
        }

        return product;
    }

    /**
     * Updates an existing Product entity using data from a ProductRequestDTO and its related entities.
     *
     * @param requestDTO input data received from the client
     * @param product existing entity from the database
     * @param category category loaded from database
     * @param location location loaded from database
     * @param unit unit loaded from database
     */
    public void updateEntityFromRequestDTO(
            ProductRequestDTO requestDTO,
            Product product,
            Category category,
            Location location,
            Unit unit
    ) {
        if (requestDTO == null || product == null) {
            return;
        }

        product.setSku(requestDTO.getSku());
        product.setName(requestDTO.getName());
        product.setDescription(requestDTO.getDescription());
        product.setCategory(category);
        product.setLocation(location);
        product.setUnit(unit);
        product.setMinimumStock(requestDTO.getMinimumStock());

        // Active is only updated if the client sends a value.
        if (requestDTO.getActive() != null) {
            product.setActive(requestDTO.getActive());
        }
    }

    /**
     * Converts a list of Product entities into a list of ProductResponseDTO objects.
     *
     * @param products list of entities from the database
     * @return list of response DTOs ready to be sent to the client
     */
    public List<ProductResponseDTO> toResponseDTOList(List<Product> products) {
        if (products == null) {
            return List.of();
        }

        return products.stream()
                .map(this::toResponseDTO)
                .toList();
    }
}

/*
 * Explanation:
 * ProductMapper centralizes conversions between Product, ProductRequestDTO and ProductResponseDTO.
 * It does not search Category, Location or Unit in the database. That responsibility will belong
 * to ProductService. The mapper only receives already loaded related entities and assigns them.
 */