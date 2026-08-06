package com.invault.inventory.locations;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.invault.inventory.locations.dto.LocationRequestDTO;
import com.invault.inventory.locations.dto.LocationResponseDTO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public List<LocationResponseDTO> findAll() {
        return locationService.findAll();
    }

    @GetMapping("/active")
    public List<LocationResponseDTO> findAllActive() {
        return locationService.findAllActive();
    }

    @GetMapping("/{id}")
    public LocationResponseDTO findById(@PathVariable Long id) {
        return locationService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponseDTO create(@Valid @RequestBody LocationRequestDTO requestDTO) {
        return locationService.create(requestDTO);
    }

    @PutMapping("/{id}")
    public LocationResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody LocationRequestDTO requestDTO) {

        return locationService.update(id, requestDTO);
    }

    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        locationService.deactivate(id);
    }
}

/*
 * LocationController exposes warehouse locations as DTO-based REST resources
 * and delegates every business decision to LocationService.
 */
