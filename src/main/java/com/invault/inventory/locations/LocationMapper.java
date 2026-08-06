package com.invault.inventory.locations;

import java.util.List;

import org.springframework.stereotype.Component;

import com.invault.inventory.locations.dto.LocationRequestDTO;
import com.invault.inventory.locations.dto.LocationResponseDTO;

/**
 * Mapper responsible for converting Location entities into DTOs and DTOs into Location entities.
 *
 * This keeps conversion logic outside controllers and services.
 */
@Component
public class LocationMapper {

    /**
     * Converts a Location entity into a LocationResponseDTO.
     *
     * @param location entity from the database
     * @return response DTO ready to be sent to the client
     */
    public LocationResponseDTO toResponseDTO(Location location) {
        if (location == null) {
            return null;
        }

        return new LocationResponseDTO(
                location.getId(),
                location.getName(),
                location.getDescription(),
                location.getActive(),
                location.getCreatedAt(),
                location.getUpdatedAt()
        );
    }

    /**
     * Creates a new Location entity from a LocationRequestDTO.
     *
     * @param requestDTO input data received from the client
     * @return new Location entity ready to be validated and saved
     */
    public Location toEntity(LocationRequestDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }

        Location location = new Location();

        location.setName(requestDTO.getName());
        location.setDescription(requestDTO.getDescription());

        // Active is optional in the request. If it is null, the entity keeps its default value.
        if (requestDTO.getActive() != null) {
            location.setActive(requestDTO.getActive());
        }

        return location;
    }

    /**
     * Updates an existing Location entity using data from a LocationRequestDTO.
     *
     * @param requestDTO input data received from the client
     * @param location existing entity from the database
     */
    public void updateEntityFromRequestDTO(LocationRequestDTO requestDTO, Location location) {
        if (requestDTO == null || location == null) {
            return;
        }

        location.setName(requestDTO.getName());
        location.setDescription(requestDTO.getDescription());

        // Active is only updated if the client sends a value.
        if (requestDTO.getActive() != null) {
            location.setActive(requestDTO.getActive());
        }
    }

    /**
     * Converts a list of Location entities into a list of LocationResponseDTO objects.
     *
     * @param locations list of entities from the database
     * @return list of response DTOs ready to be sent to the client
     */
    public List<LocationResponseDTO> toResponseDTOList(List<Location> locations) {
        if (locations == null) {
            return List.of();
        }

        return locations.stream()
                .map(this::toResponseDTO)
                .toList();
    }
}

/*
 * Explanation:
 * LocationMapper centralizes conversions between Location, LocationRequestDTO and LocationResponseDTO.
 * Later, LocationService and LocationController will use this mapper to avoid mixing API models
 * with database entities.
 */