package com.invault.inventory.locations;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    // Busca una ubicación por nombre ignorando mayúsculas/minúsculas.
    Optional<Location> findByNameIgnoreCase(String name);

    // Devuelve solo las ubicaciones activas ordenadas por nombre.
    List<Location> findByActiveTrueOrderByNameAsc();

    // Devuelve todas las ubicaciones ordenadas por nombre.
    List<Location> findAllByOrderByNameAsc();
}

/*
 * LocationRepository centraliza el acceso a datos de Location.
 * Además de las operaciones CRUD heredadas de JpaRepository,
 * añade consultas específicas para validar ubicaciones duplicadas
 * y listar ubicaciones activas.
 */