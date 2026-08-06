package com.invault.inventory.units;

import java.util.List;

import org.springframework.stereotype.Component;

import com.invault.inventory.units.dto.UnitRequestDTO;
import com.invault.inventory.units.dto.UnitResponseDTO;

/**
 * Mapper responsible for converting Unit entities into DTOs and DTOs into Unit entities.
 *
 * This keeps conversion logic outside controllers and services.
 */
@Component
public class UnitMapper {

    /**
     * Converts a Unit entity into a UnitResponseDTO.
     *
     * @param unit entity from the database
     * @return response DTO ready to be sent to the client
     */
    public UnitResponseDTO toResponseDTO(Unit unit) {
        if (unit == null) {
            return null;
        }

        return new UnitResponseDTO(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                unit.getSymbol(),
                unit.getDescription(),
                unit.getActive(),
                unit.getCreatedAt(),
                unit.getUpdatedAt()
        );
    }

    /**
     * Creates a new Unit entity from a UnitRequestDTO.
     *
     * @param requestDTO input data received from the client
     * @return new Unit entity ready to be validated and saved
     */
    public Unit toEntity(UnitRequestDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }

        Unit unit = new Unit();

        unit.setCode(requestDTO.getCode());
        unit.setName(requestDTO.getName());
        unit.setSymbol(requestDTO.getSymbol());
        unit.setDescription(requestDTO.getDescription());

        // Active is optional in the request. If it is null, the entity keeps its default value.
        if (requestDTO.getActive() != null) {
            unit.setActive(requestDTO.getActive());
        }

        return unit;
    }

    /**
     * Updates an existing Unit entity using data from a UnitRequestDTO.
     *
     * @param requestDTO input data received from the client
     * @param unit existing entity from the database
     */
    public void updateEntityFromRequestDTO(UnitRequestDTO requestDTO, Unit unit) {
        if (requestDTO == null || unit == null) {
            return;
        }

        unit.setCode(requestDTO.getCode());
        unit.setName(requestDTO.getName());
        unit.setSymbol(requestDTO.getSymbol());
        unit.setDescription(requestDTO.getDescription());

        // Active is only updated if the client sends a value.
        if (requestDTO.getActive() != null) {
            unit.setActive(requestDTO.getActive());
        }
    }

    /**
     * Converts a list of Unit entities into a list of UnitResponseDTO objects.
     *
     * @param units list of entities from the database
     * @return list of response DTOs ready to be sent to the client
     */
    public List<UnitResponseDTO> toResponseDTOList(List<Unit> units) {
        if (units == null) {
            return List.of();
        }

        return units.stream()
                .map(this::toResponseDTO)
                .toList();
    }
}

/*
 * Explanation:
 * UnitMapper centralizes conversions between Unit, UnitRequestDTO and UnitResponseDTO.
 * Later, UnitService and UnitController will use this mapper to avoid mixing API models
 * with database entities.
 */