package com.invault.inventory.stock;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // Devuelve los movimientos asociados a un producto concreto.
    List<StockMovement> findByProductId(Long productId);

    // Devuelve los movimientos asociados a un lote concreto.
    List<StockMovement> findByBatchId(Long batchId);
}

/*
 * StockMovementRepository centraliza el acceso a datos de StockMovement.
 *
 * Además de las operaciones CRUD heredadas de JpaRepository, añade consultas
 * para recuperar movimientos por producto o por lote. El orden final lo
 * gestionará StockService para mantener simple el repositorio en esta fase.
 */