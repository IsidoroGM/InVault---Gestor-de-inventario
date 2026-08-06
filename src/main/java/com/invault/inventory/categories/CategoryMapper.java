package com.invault.inventory.categories;

import java.util.List;

import org.springframework.stereotype.Component;

import com.invault.inventory.categories.dto.CategoryRequestDTO;
import com.invault.inventory.categories.dto.CategoryResponseDTO;

/**
 * Mapper responsible for converting Category entities into DTOs and DTOs into Category entities.
 *
 * This keeps conversion logic outside controllers and services.
 */
@Component
public class CategoryMapper {

    /**
     * Converts a Category entity into a CategoryResponseDTO.
     *
     * @param category entity from the database
     * @return response DTO ready to be sent to the client
     */
    public CategoryResponseDTO toResponseDTO(Category category) {
        if (category == null) {
            return null;
        }

        return new CategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    /**
     * Creates a new Category entity from a CategoryRequestDTO.
     *
     * @param requestDTO input data received from the client
     * @return new Category entity ready to be validated and saved
     */
    public Category toEntity(CategoryRequestDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }

        Category category = new Category();

        category.setName(requestDTO.getName());
        category.setDescription(requestDTO.getDescription());

        // Active is optional in the request. If it is null, the entity keeps its default value.
        if (requestDTO.getActive() != null) {
            category.setActive(requestDTO.getActive());
        }

        return category;
    }

    /**
     * Updates an existing Category entity using data from a CategoryRequestDTO.
     *
     * @param requestDTO input data received from the client
     * @param category existing entity from the database
     */
    public void updateEntityFromRequestDTO(CategoryRequestDTO requestDTO, Category category) {
        if (requestDTO == null || category == null) {
            return;
        }

        category.setName(requestDTO.getName());
        category.setDescription(requestDTO.getDescription());

        // Active is only updated if the client sends a value.
        if (requestDTO.getActive() != null) {
            category.setActive(requestDTO.getActive());
        }
    }

    /**
     * Converts a list of Category entities into a list of CategoryResponseDTO objects.
     *
     * @param categories list of entities from the database
     * @return list of response DTOs ready to be sent to the client
     */
    public List<CategoryResponseDTO> toResponseDTOList(List<Category> categories) {
        if (categories == null) {
            return List.of();
        }

        return categories.stream()
                .map(this::toResponseDTO)
                .toList();
    }
}

/*
 * Explanation:
 * CategoryMapper centralizes conversions between Category, CategoryRequestDTO and CategoryResponseDTO.
 * Later, CategoryService and CategoryController will use this mapper to avoid mixing API models
 * with database entities.
 */