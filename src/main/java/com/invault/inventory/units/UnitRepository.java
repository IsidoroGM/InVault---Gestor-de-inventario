package com.invault.inventory.units;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select unit from Unit unit where unit.id = :id")
    Optional<Unit> findByIdForUpdate(@Param("id") Long id);

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
