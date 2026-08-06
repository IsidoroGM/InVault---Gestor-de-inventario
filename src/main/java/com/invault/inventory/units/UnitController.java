package com.invault.inventory.units;

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

import com.invault.inventory.units.dto.UnitRequestDTO;
import com.invault.inventory.units.dto.UnitResponseDTO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    public List<UnitResponseDTO> findAll() {
        return unitService.findAll();
    }

    @GetMapping("/active")
    public List<UnitResponseDTO> findAllActive() {
        return unitService.findAllActive();
    }

    @GetMapping("/{id}")
    public UnitResponseDTO findById(@PathVariable Long id) {
        return unitService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponseDTO create(@Valid @RequestBody UnitRequestDTO requestDTO) {
        return unitService.create(requestDTO);
    }

    @PutMapping("/{id}")
    public UnitResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody UnitRequestDTO requestDTO) {

        return unitService.update(id, requestDTO);
    }

    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        unitService.deactivate(id);
    }
}

/*
 * UnitController exposes the unit catalogue through REST without leaking JPA
 * entities or duplicating the business rules implemented by UnitService.
 */
