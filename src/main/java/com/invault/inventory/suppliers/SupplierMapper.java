package com.invault.inventory.suppliers;

import java.util.List;

import org.springframework.stereotype.Component;

import com.invault.inventory.suppliers.dto.SupplierRequestDTO;
import com.invault.inventory.suppliers.dto.SupplierResponseDTO;

/**
 * Mapper responsible for converting Supplier entities into DTOs and DTOs into Supplier entities.
 *
 * This keeps conversion logic outside controllers and services.
 */
@Component
public class SupplierMapper {

    /**
     * Converts a Supplier entity into a SupplierResponseDTO.
     *
     * @param supplier entity from the database
     * @return response DTO ready to be sent to the client
     */
    public SupplierResponseDTO toResponseDTO(Supplier supplier) {
        if (supplier == null) {
            return null;
        }

        return new SupplierResponseDTO(
                supplier.getId(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getNotes(),
                supplier.getActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }

    /**
     * Creates a new Supplier entity from a SupplierRequestDTO.
     *
     * @param requestDTO input data received from the client
     * @return new Supplier entity ready to be validated and saved
     */
    public Supplier toEntity(SupplierRequestDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }

        Supplier supplier = new Supplier();

        supplier.setName(requestDTO.getName());
        supplier.setContactName(requestDTO.getContactName());
        supplier.setPhone(requestDTO.getPhone());
        supplier.setEmail(requestDTO.getEmail());
        supplier.setNotes(requestDTO.getNotes());

        // Active is optional in the request. If it is null, the entity keeps its default value.
        if (requestDTO.getActive() != null) {
            supplier.setActive(requestDTO.getActive());
        }

        return supplier;
    }

    /**
     * Updates an existing Supplier entity using data from a SupplierRequestDTO.
     *
     * @param requestDTO input data received from the client
     * @param supplier existing entity from the database
     */
    public void updateEntityFromRequestDTO(SupplierRequestDTO requestDTO, Supplier supplier) {
        if (requestDTO == null || supplier == null) {
            return;
        }

        supplier.setName(requestDTO.getName());
        supplier.setContactName(requestDTO.getContactName());
        supplier.setPhone(requestDTO.getPhone());
        supplier.setEmail(requestDTO.getEmail());
        supplier.setNotes(requestDTO.getNotes());

        // Active is only updated if the client sends a value.
        if (requestDTO.getActive() != null) {
            supplier.setActive(requestDTO.getActive());
        }
    }

    /**
     * Converts a list of Supplier entities into a list of SupplierResponseDTO objects.
     *
     * @param suppliers list of entities from the database
     * @return list of response DTOs ready to be sent to the client
     */
    public List<SupplierResponseDTO> toResponseDTOList(List<Supplier> suppliers) {
        if (suppliers == null) {
            return List.of();
        }

        return suppliers.stream()
                .map(this::toResponseDTO)
                .toList();
    }
}

/*
 * Explanation:
 * SupplierMapper centralizes conversions between Supplier, SupplierRequestDTO and SupplierResponseDTO.
 * Later, SupplierService and SupplierController will use this mapper to avoid mixing API models
 * with database entities.
 */