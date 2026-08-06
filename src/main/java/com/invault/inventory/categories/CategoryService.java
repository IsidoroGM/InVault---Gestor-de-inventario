package com.invault.inventory.categories;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.categories.dto.CategoryRequestDTO;
import com.invault.inventory.categories.dto.CategoryResponseDTO;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> findAll() {
        List<Category> categories = categoryRepository.findAllByOrderByNameAsc();
        return categoryMapper.toResponseDTOList(categories);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> findAllActive() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderByNameAsc();
        return categoryMapper.toResponseDTOList(categories);
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO findById(Long id) {
        Category category = findCategoryEntityById(id);
        return categoryMapper.toResponseDTO(category);
    }

    public CategoryResponseDTO create(CategoryRequestDTO requestDTO) {
        String normalizedName = normalizeName(requestDTO.getName());

        validateNameIsUnique(normalizedName, null);

        Category category = categoryMapper.toEntity(requestDTO);

        // Normalizamos campos importantes antes de guardar.
        category.setName(normalizedName);
        category.setDescription(normalizeText(requestDTO.getDescription()));

        if (category.getActive() == null) {
            category.setActive(true);
        }

        Category savedCategory = categoryRepository.save(category);
        return categoryMapper.toResponseDTO(savedCategory);
    }

    public CategoryResponseDTO update(Long id, CategoryRequestDTO requestDTO) {
        Category category = findCategoryEntityById(id);

        String normalizedName = normalizeName(requestDTO.getName());

        validateNameIsUnique(normalizedName, id);

        // Actualizamos los campos editables de Category.
        category.setName(normalizedName);
        category.setDescription(normalizeText(requestDTO.getDescription()));

        if (requestDTO.getActive() != null) {
            category.setActive(requestDTO.getActive());
        }

        Category updatedCategory = categoryRepository.save(category);
        return categoryMapper.toResponseDTO(updatedCategory);
    }

    public void deactivate(Long id) {
        Category category = findCategoryEntityById(id);

        if (Boolean.FALSE.equals(category.getActive())) {
            throw new BadRequestException("Category is already inactive.");
        }

        category.setActive(false);
        categoryRepository.save(category);
    }

    private Category findCategoryEntityById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    private void validateNameIsUnique(String name, Long currentCategoryId) {
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existingCategory -> isDifferentCategory(existingCategory, currentCategoryId))
                .ifPresent(existingCategory -> {
                    throw new BadRequestException("A category with name '" + name + "' already exists.");
                });
    }

    private boolean isDifferentCategory(Category existingCategory, Long currentCategoryId) {
        return currentCategoryId == null || !existingCategory.getId().equals(currentCategoryId);
    }

    private String normalizeName(String value) {
        String normalizedValue = normalizeText(value);

        if (normalizedValue == null) {
            return null;
        }

        String[] words = normalizedValue.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder titleCaseName = new StringBuilder();

        for (String word : words) {
            if (!word.isBlank()) {
                titleCaseName
                        .append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return titleCaseName.toString().trim();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}

/*
 * CategoryService contiene la lógica de negocio para las categorías del inventario.
 *
 * Esta clase permite listar, consultar, crear, actualizar y desactivar categorías
 * sin exponer directamente la entidad JPA Category. Trabaja con DTOs, utiliza
 * CategoryMapper para transformar datos y valida que no existan categorías duplicadas
 * por nombre antes de guardar cambios en la base de datos.
 */