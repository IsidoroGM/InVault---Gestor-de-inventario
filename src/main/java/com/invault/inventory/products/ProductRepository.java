package com.invault.inventory.products;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Busca un producto por SKU ignorando mayúsculas/minúsculas.
    Optional<Product> findBySkuIgnoreCase(String sku);

    // Devuelve solo productos activos ordenados por nombre.
    List<Product> findByActiveTrueOrderByNameAsc();

    // Devuelve todos los productos ordenados por nombre.
    List<Product> findAllByOrderByNameAsc();

    long countByActiveTrue();

    @Query("""
            select product
            from Product product
            where (:active is null or product.active = :active)
              and (:query is null
                   or lower(product.sku) like lower(concat('%', :query, '%'))
                   or lower(product.name) like lower(concat('%', :query, '%'))
                   or lower(product.description) like lower(concat('%', :query, '%')))
            """)
    Page<Product> search(
            @Param("query") String query,
            @Param("active") Boolean active,
            Pageable pageable
    );
}

/*
 * ProductRepository centraliza el acceso a datos de Product.
 * Además de las operaciones CRUD heredadas de JpaRepository,
 * añade consultas específicas para validar SKU duplicado
 * y listar productos de forma ordenada.
 */
