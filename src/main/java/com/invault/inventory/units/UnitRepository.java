package com.invault.inventory.units;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    // Busca una unidad por código ignorando mayúsculas/minúsculas.
    Optional<Unit> findByCodeIgnoreCase(String code);

    // Busca una unidad por nombre ignorando mayúsculas/minúsculas.
    Optional<Unit> findByNameIgnoreCase(String name);

    // Devuelve solo las unidades activas ordenadas por nombre.
    List<Unit> findByActiveTrueOrderByNameAsc();

    // Devuelve todas las unidades ordenadas por nombre.
    List<Unit> findAllByOrderByNameAsc();
}

/*
 * UnitRepository centraliza el acceso a datos de Unit.
 * Además de las operaciones CRUD heredadas de JpaRepository,
 * añade consultas específicas para validar duplicados y listar unidades activas.
 */