package com.invault.inventory.suppliers;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.audit.AuditAction;
import com.invault.inventory.audit.AuditService;
import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.suppliers.dto.SupplierRequestDTO;
import com.invault.inventory.suppliers.dto.SupplierResponseDTO;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final AuditService auditService;

    public SupplierService(
            SupplierRepository supplierRepository,
            SupplierMapper supplierMapper,
            AuditService auditService) {
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponseDTO> findAll() {
        List<Supplier> suppliers = supplierRepository.findAllByOrderByNameAsc();
        return supplierMapper.toResponseDTOList(suppliers);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponseDTO> findAllActive() {
        List<Supplier> suppliers = supplierRepository.findByActiveTrueOrderByNameAsc();
        return supplierMapper.toResponseDTOList(suppliers);
    }

    @Transactional(readOnly = true)
    public SupplierResponseDTO findById(Long id) {
        Supplier supplier = findSupplierEntityById(id);
        return supplierMapper.toResponseDTO(supplier);
    }

    public SupplierResponseDTO create(SupplierRequestDTO requestDTO) {
        String normalizedName = normalizeText(requestDTO.getName());
        String normalizedEmail = normalizeEmail(requestDTO.getEmail());

        validateNameIsUnique(normalizedName, null);
        validateEmailIsUnique(normalizedEmail, null);

        Supplier supplier = supplierMapper.toEntity(requestDTO);

        // Normalizamos campos editables antes de guardar.
        supplier.setName(normalizedName);
        supplier.setContactName(normalizeText(requestDTO.getContactName()));
        supplier.setPhone(normalizeText(requestDTO.getPhone()));
        supplier.setEmail(normalizedEmail);
        supplier.setNotes(normalizeText(requestDTO.getNotes()));

        if (supplier.getActive() == null) {
            supplier.setActive(true);
        }

        Supplier savedSupplier = supplierRepository.save(supplier);
        SupplierResponseDTO response = supplierMapper.toResponseDTO(savedSupplier);
        auditService.registerMutation(
                AuditAction.CREATED, "Supplier", response.getId(), "Supplier created.", null, response);
        return response;
    }

    public SupplierResponseDTO update(Long id, SupplierRequestDTO requestDTO) {
        Supplier supplier = findSupplierEntityById(id);
        SupplierResponseDTO before = supplierMapper.toResponseDTO(supplier);

        String normalizedName = normalizeText(requestDTO.getName());
        String normalizedEmail = normalizeEmail(requestDTO.getEmail());

        validateNameIsUnique(normalizedName, id);
        validateEmailIsUnique(normalizedEmail, id);

        // Actualizamos los campos editables de Supplier.
        supplier.setName(normalizedName);
        supplier.setContactName(normalizeText(requestDTO.getContactName()));
        supplier.setPhone(normalizeText(requestDTO.getPhone()));
        supplier.setEmail(normalizedEmail);
        supplier.setNotes(normalizeText(requestDTO.getNotes()));

        if (requestDTO.getActive() != null) {
            supplier.setActive(requestDTO.getActive());
        }

        Supplier updatedSupplier = supplierRepository.save(supplier);
        SupplierResponseDTO after = supplierMapper.toResponseDTO(updatedSupplier);
        auditService.registerMutation(
                AuditAction.fromActiveChange(before.isActive(), after.isActive()),
                "Supplier", after.getId(), "Supplier updated.", before, after);
        return after;
    }

    public void deactivate(Long id) {
        Supplier supplier = findSupplierEntityById(id);
        SupplierResponseDTO before = supplierMapper.toResponseDTO(supplier);

        if (Boolean.FALSE.equals(supplier.getActive())) {
            throw new BadRequestException("Supplier is already inactive.");
        }

        supplier.setActive(false);
        supplierRepository.save(supplier);
        SupplierResponseDTO after = supplierMapper.toResponseDTO(supplier);
        auditService.registerMutation(
                AuditAction.DEACTIVATED, "Supplier", id, "Supplier deactivated.", before, after);
    }

    private Supplier findSupplierEntityById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + id));
    }

    private void validateNameIsUnique(String name, Long currentSupplierId) {
        supplierRepository.findByNameIgnoreCase(name)
                .filter(existingSupplier -> isDifferentSupplier(existingSupplier, currentSupplierId))
                .ifPresent(existingSupplier -> {
                    throw new BadRequestException("A supplier with name '" + name + "' already exists.");
                });
    }

    private void validateEmailIsUnique(String email, Long currentSupplierId) {
        if (!hasText(email)) {
            return;
        }

        supplierRepository.findByEmailIgnoreCase(email)
                .filter(existingSupplier -> isDifferentSupplier(existingSupplier, currentSupplierId))
                .ifPresent(existingSupplier -> {
                    throw new BadRequestException("A supplier with email '" + email + "' already exists.");
                });
    }

    private boolean isDifferentSupplier(Supplier existingSupplier, Long currentSupplierId) {
        return currentSupplierId == null || !existingSupplier.getId().equals(currentSupplierId);
    }

    private String normalizeEmail(String value) {
        String normalizedValue = normalizeText(value);

        if (!hasText(normalizedValue)) {
            return null;
        }

        return normalizedValue.toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

/*
 * SupplierService contiene la lógica de negocio para los proveedores del inventario.
 *
 * Esta clase permite listar, consultar, crear, actualizar y desactivar proveedores
 * sin exponer directamente la entidad JPA Supplier. Trabaja con DTOs, utiliza
 * SupplierMapper para transformar datos y valida que no existan proveedores
 * duplicados por nombre o por email antes de guardar cambios en la base de datos.
 */
