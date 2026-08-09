package com.invault.inventory.batches;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    // Busca un lote por código ignorando mayúsculas/minúsculas.
    Optional<Batch> findByBatchCodeIgnoreCase(String batchCode);

    // Devuelve todos los lotes ordenados por código.
    List<Batch> findAllByOrderByBatchCodeAsc();

    // Devuelve los lotes de un producto concreto ordenados por código.
    List<Batch> findByProductIdOrderByBatchCodeAsc(Long productId);

    long countByStatus(BatchStatus status);

    @Query("""
            select coalesce(sum(batch.quantity), 0)
            from Batch batch
            where batch.product.id = :productId
              and batch.status = com.invault.inventory.batches.BatchStatus.AVAILABLE
            """)
    java.math.BigDecimal calculateAvailableStock(@Param("productId") Long productId);

    @Query("""
            select batch.product.id as productId, sum(batch.quantity) as totalStock
            from Batch batch
            where batch.status = com.invault.inventory.batches.BatchStatus.AVAILABLE
            group by batch.product.id
            """)
    List<ProductStockProjection> calculateAvailableStockByProduct();
}

/*
 * BatchRepository centraliza el acceso a datos de Batch.
 *
 * Además de las operaciones CRUD heredadas de JpaRepository,
 * añade consultas específicas para validar códigos de lote duplicados
 * y listar lotes por producto.
 */
