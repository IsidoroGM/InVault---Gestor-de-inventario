package com.invault.inventory.locations;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.locations.dto.LocationRequestDTO;
import com.invault.inventory.locations.dto.LocationResponseDTO;

@Service
@Transactional
public class LocationService {

    private final LocationRepository locationRepository;
    private final LocationMapper locationMapper;

    public LocationService(LocationRepository locationRepository, LocationMapper locationMapper) {
        this.locationRepository = locationRepository;
        this.locationMapper = locationMapper;
    }

    @Transactional(readOnly = true)
    public List<LocationResponseDTO> findAll() {
        List<Location> locations = locationRepository.findAllByOrderByNameAsc();
        return locationMapper.toResponseDTOList(locations);
    }

    @Transactional(readOnly = true)
    public List<LocationResponseDTO> findAllActive() {
        List<Location> locations = locationRepository.findByActiveTrueOrderByNameAsc();
        return locationMapper.toResponseDTOList(locations);
    }

    @Transactional(readOnly = true)
    public LocationResponseDTO findById(Long id) {
        Location location = findLocationEntityById(id);
        return locationMapper.toResponseDTO(location);
    }

    public LocationResponseDTO create(LocationRequestDTO requestDTO) {
        String normalizedName = normalizeText(requestDTO.getName());

        validateNameIsUnique(normalizedName, null);

        Location location = locationMapper.toEntity(requestDTO);

        // Normalizamos campos editables antes de guardar.
        location.setName(normalizedName);
        location.setDescription(normalizeText(requestDTO.getDescription()));

        if (location.getActive() == null) {
            location.setActive(true);
        }

        Location savedLocation = locationRepository.save(location);
        return locationMapper.toResponseDTO(savedLocation);
    }

    public LocationResponseDTO update(Long id, LocationRequestDTO requestDTO) {
        Location location = findLocationEntityById(id);

        String normalizedName = normalizeText(requestDTO.getName());

        validateNameIsUnique(normalizedName, id);

        // Actualizamos los campos editables de Location.
        location.setName(normalizedName);
        location.setDescription(normalizeText(requestDTO.getDescription()));

        if (requestDTO.getActive() != null) {
            location.setActive(requestDTO.getActive());
        }

        Location updatedLocation = locationRepository.save(location);
        return locationMapper.toResponseDTO(updatedLocation);
    }

    public void deactivate(Long id) {
        Location location = findLocationEntityById(id);

        if (Boolean.FALSE.equals(location.getActive())) {
            throw new BadRequestException("Location is already inactive.");
        }

        location.setActive(false);
        locationRepository.save(location);
    }

    private Location findLocationEntityById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
    }

    private void validateNameIsUnique(String name, Long currentLocationId) {
        locationRepository.findByNameIgnoreCase(name)
                .filter(existingLocation -> isDifferentLocation(existingLocation, currentLocationId))
                .ifPresent(existingLocation -> {
                    throw new BadRequestException("A location with name '" + name + "' already exists.");
                });
    }

    private boolean isDifferentLocation(Location existingLocation, Long currentLocationId) {
        return currentLocationId == null || !existingLocation.getId().equals(currentLocationId);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}

/*
 * LocationService contiene la lógica de negocio para las ubicaciones del inventario.
 *
 * Esta clase permite listar, consultar, crear, actualizar y desactivar ubicaciones
 * sin exponer directamente la entidad JPA Location. Trabaja con DTOs, utiliza
 * LocationMapper para transformar datos y valida que no existan ubicaciones
 * duplicadas por nombre antes de guardar cambios en la base de datos.
 */