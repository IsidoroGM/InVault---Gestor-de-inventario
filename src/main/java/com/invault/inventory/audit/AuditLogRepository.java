package com.invault.inventory.audit;

import com.invault.inventory.users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository responsible for database operations related to AuditLog entities.
 *
 * An audit log stores relevant actions performed in the system,
 * such as created records, updates, stock movements, login attempts
 * or password changes.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<AuditLog> findById(Long id);

    // Returns all audit logs created by a specific user, ordered from newest to oldest.
    List<AuditLog> findByUserOrderByCreatedAtDesc(User user);

    // Returns all audit logs of a specific action type.
    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action);

    // Returns all audit logs related to a specific entity name, for example: "Product" or "Batch".
    List<AuditLog> findByEntityNameIgnoreCaseOrderByCreatedAtDesc(String entityName);

    // Returns audit logs related to one specific entity record.
    // Example: all audit entries for Product with id 15.
    List<AuditLog> findByEntityNameIgnoreCaseAndEntityIdOrderByCreatedAtDesc(
            String entityName,
            Long entityId
    );

    // Returns audit logs created between two dates.
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // Returns audit logs created by a specific user between two dates.
    List<AuditLog> findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
            User user,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // Returns audit logs of a specific action between two dates.
    List<AuditLog> findByActionAndCreatedAtBetweenOrderByCreatedAtDesc(
            AuditAction action,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // Searches audit logs by partial entity name.
    List<AuditLog> findByEntityNameContainingIgnoreCaseOrderByCreatedAtDesc(String entityName);

    // Returns the latest audit logs in the system.
    List<AuditLog> findTop50ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "user")
    @Query("""
            select audit
            from AuditLog audit
            where (:action is null or audit.action = :action)
              and (:entityName is null
                   or lower(audit.entityName) like lower(concat('%', :entityName, '%')))
              and (:userId is null or audit.user.id = :userId)
              and (:fromDate is null or audit.createdAt >= :fromDate)
              and (:toDate is null or audit.createdAt <= :toDate)
            """)
    Page<AuditLog> search(
            @Param("action") AuditAction action,
            @Param("entityName") String entityName,
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}

/*
 * Explanation:
 * This repository centralizes all database queries related to AuditLog.
 * It will be used later by AuditService to review important system activity,
 * filter actions by user, action type, entity, entity id or date range,
 * and show recent audit information to ADMIN or SUPERVISOR users.
 */
