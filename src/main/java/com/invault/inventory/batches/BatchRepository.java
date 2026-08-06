package com.invault.inventory.batches;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {

    // Busca un lote por código ignorando mayúsculas/minúsculas.
    Optional<Batch> findByBatchCodeIgnoreCase(String batchCode);

    // Devuelve todos los lotes ordenados por código.
    List<Batch> findAllByOrderByBatchCodeAsc();

    // Devuelve los lotes de un producto concreto ordenados por código.
    List<Batch> findByProductIdOrderByBatchCodeAsc(Long productId);
}

/*
 * BatchRepository centraliza el acceso a datos de Batch.
 *
 * Además de las operaciones CRUD heredadas de JpaRepository,
 * añade consultas específicas para validar códigos de lote duplicados
 * y listar lotes por producto.
 */