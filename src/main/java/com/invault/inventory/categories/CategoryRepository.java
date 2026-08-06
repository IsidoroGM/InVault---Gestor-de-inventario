package com.invault.inventory.categories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Busca una categoría por nombre ignorando mayúsculas/minúsculas.
    Optional<Category> findByNameIgnoreCase(String name);

    // Devuelve solo las categorías activas ordenadas por nombre.
    List<Category> findByActiveTrueOrderByNameAsc();

    // Devuelve todas las categorías ordenadas por nombre.
    List<Category> findAllByOrderByNameAsc();
}

/*
 * CategoryRepository centraliza el acceso a datos de Category.
 * Además de las operaciones CRUD heredadas de JpaRepository,
 * añade consultas específicas para validar nombres duplicados
 * y listar categorías activas.
 */