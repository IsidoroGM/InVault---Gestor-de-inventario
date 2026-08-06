package com.invault.inventory.units;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invault.inventory.common.exception.BadRequestException;
import com.invault.inventory.common.exception.ResourceNotFoundException;
import com.invault.inventory.units.dto.UnitRequestDTO;
import com.invault.inventory.units.dto.UnitResponseDTO;

@Service
@Transactional
public class UnitService {

    private final UnitRepository unitRepository;
    private final UnitMapper unitMapper;

    public UnitService(UnitRepository unitRepository, UnitMapper unitMapper) {
        this.unitRepository = unitRepository;
        this.unitMapper = unitMapper;
    }

    @Transactional(readOnly = true)
    public List<UnitResponseDTO> findAll() {
        List<Unit> units = unitRepository.findAllByOrderByNameAsc();
        return unitMapper.toResponseDTOList(units);
    }

    @Transactional(readOnly = true)
    public List<UnitResponseDTO> findAllActive() {
        List<Unit> units = unitRepository.findByActiveTrueOrderByNameAsc();
        return unitMapper.toResponseDTOList(units);
    }

    @Transactional(readOnly = true)
    public UnitResponseDTO findById(Long id) {
        Unit unit = findUnitEntityById(id);
        return unitMapper.toResponseDTO(unit);
    }

    public UnitResponseDTO create(UnitRequestDTO requestDTO) {
        String normalizedCode = normalizeText(requestDTO.getCode());
        String normalizedName = normalizeText(requestDTO.getName());

        validateCodeIsUnique(normalizedCode, null);
        validateNameIsUnique(normalizedName, null);

        Unit unit = unitMapper.toEntity(requestDTO);

        // Normalizamos campos importantes antes de guardar.
        unit.setCode(normalizedCode);
        unit.setName(normalizedName);
        unit.setSymbol(normalizeText(requestDTO.getSymbol()));
        unit.setDescription(normalizeText(requestDTO.getDescription()));

        if (unit.getActive() == null) {
            unit.setActive(true);
        }

        Unit savedUnit = unitRepository.save(unit);
        return unitMapper.toResponseDTO(savedUnit);
    }

    public UnitResponseDTO update(Long id, UnitRequestDTO requestDTO) {
        Unit unit = findUnitEntityById(id);

        String normalizedCode = normalizeText(requestDTO.getCode());
        String normalizedName = normalizeText(requestDTO.getName());

        validateCodeIsUnique(normalizedCode, id);
        validateNameIsUnique(normalizedName, id);

        // Actualizamos los campos editables de Unit.
        unit.setCode(normalizedCode);
        unit.setName(normalizedName);
        unit.setSymbol(normalizeText(requestDTO.getSymbol()));
        unit.setDescription(normalizeText(requestDTO.getDescription()));

        if (requestDTO.getActive() != null) {
            unit.setActive(requestDTO.getActive());
        }

        Unit updatedUnit = unitRepository.save(unit);
        return unitMapper.toResponseDTO(updatedUnit);
    }

    public void deactivate(Long id) {
        Unit unit = findUnitEntityById(id);

        if (Boolean.FALSE.equals(unit.getActive())) {
            throw new BadRequestException("Unit is already inactive.");
        }

        unit.setActive(false);
        unitRepository.save(unit);
    }

    private Unit findUnitEntityById(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found with id: " + id));
    }

    private void validateCodeIsUnique(String code, Long currentUnitId) {
        unitRepository.findByCodeIgnoreCase(code)
                .filter(existingUnit -> isDifferentUnit(existingUnit, currentUnitId))
                .ifPresent(existingUnit -> {
                    throw new BadRequestException("A unit with code '" + code + "' already exists.");
                });
    }

    private void validateNameIsUnique(String name, Long currentUnitId) {
        unitRepository.findByNameIgnoreCase(name)
                .filter(existingUnit -> isDifferentUnit(existingUnit, currentUnitId))
                .ifPresent(existingUnit -> {
                    throw new BadRequestException("A unit with name '" + name + "' already exists.");
                });
    }

    private boolean isDifferentUnit(Unit existingUnit, Long currentUnitId) {
        return currentUnitId == null || !existingUnit.getId().equals(currentUnitId);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}

/*
 * UnitService contiene la lógica de negocio para las unidades de medida.
 *
 * Esta clase no expone entidades JPA hacia fuera. Trabaja con UnitRequestDTO
 * y UnitResponseDTO, usa UnitMapper para convertir datos y utiliza excepciones
 * globales para devolver errores claros cuando una unidad no existe o cuando
 * se incumple una regla de negocio, como duplicar un código o un nombre.
 */