package com.invault.inventory.suppliers;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    // Busca un proveedor por nombre ignorando mayúsculas/minúsculas.
    Optional<Supplier> findByNameIgnoreCase(String name);

    // Busca un proveedor por email ignorando mayúsculas/minúsculas.
    Optional<Supplier> findByEmailIgnoreCase(String email);

    // Devuelve solo los proveedores activos ordenados por nombre.
    List<Supplier> findByActiveTrueOrderByNameAsc();

    // Devuelve todos los proveedores ordenados por nombre.
    List<Supplier> findAllByOrderByNameAsc();
}

/*
 * SupplierRepository centraliza el acceso a datos de Supplier.
 * Además de las operaciones CRUD heredadas de JpaRepository,
 * añade consultas específicas para validar proveedores duplicados
 * y listar proveedores activos.
 */