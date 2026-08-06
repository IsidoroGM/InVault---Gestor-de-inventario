package com.invault.inventory.suppliers;

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

import com.invault.inventory.suppliers.dto.SupplierRequestDTO;
import com.invault.inventory.suppliers.dto.SupplierResponseDTO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<SupplierResponseDTO> findAll() {
        return supplierService.findAll();
    }

    @GetMapping("/active")
    public List<SupplierResponseDTO> findAllActive() {
        return supplierService.findAllActive();
    }

    @GetMapping("/{id}")
    public SupplierResponseDTO findById(@PathVariable Long id) {
        return supplierService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponseDTO create(@Valid @RequestBody SupplierRequestDTO requestDTO) {
        return supplierService.create(requestDTO);
    }

    @PutMapping("/{id}")
    public SupplierResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequestDTO requestDTO) {

        return supplierService.update(id, requestDTO);
    }

    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        supplierService.deactivate(id);
    }
}

/*
 * SupplierController provides the supplier catalogue HTTP boundary while
 * SupplierService remains responsible for validation and logical deactivation.
 */
