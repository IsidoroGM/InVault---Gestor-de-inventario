package com.invault.inventory.stock;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // Devuelve los movimientos asociados a un producto concreto.
    List<StockMovement> findByProductId(Long productId);

    // Devuelve los movimientos asociados a un lote concreto.
    List<StockMovement> findByBatchId(Long batchId);

    List<StockMovement> findTop10ByOrderByMovementDateDesc();

    @Query("""
            select movement
            from StockMovement movement
            where (:productId is null or movement.product.id = :productId)
              and (:batchId is null or movement.batch.id = :batchId)
              and (:movementType is null or movement.movementType = :movementType)
              and (:fromDate is null or movement.movementDate >= :fromDate)
              and (:toDate is null or movement.movementDate <= :toDate)
            """)
    Page<StockMovement> search(
            @Param("productId") Long productId,
            @Param("batchId") Long batchId,
            @Param("movementType") MovementType movementType,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}

/*
 * StockMovementRepository centraliza el acceso a datos de StockMovement.
 *
 * Además de las operaciones CRUD heredadas de JpaRepository, añade consultas
 * para recuperar movimientos por producto o por lote. El orden final lo
 * gestionará StockService para mantener simple el repositorio en esta fase.
 */
