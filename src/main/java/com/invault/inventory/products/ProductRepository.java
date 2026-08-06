package com.invault.inventory.products;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Busca un producto por SKU ignorando mayúsculas/minúsculas.
    Optional<Product> findBySkuIgnoreCase(String sku);

    // Devuelve solo productos activos ordenados por nombre.
    List<Product> findByActiveTrueOrderByNameAsc();

    // Devuelve todos los productos ordenados por nombre.
    List<Product> findAllByOrderByNameAsc();
}

/*
 * ProductRepository centraliza el acceso a datos de Product.
 * Además de las operaciones CRUD heredadas de JpaRepository,
 * añade consultas específicas para validar SKU duplicado
 * y listar productos de forma ordenada.
 */