package com.invault.inventory.audit;

import java.time.LocalDateTime;

import com.invault.inventory.users.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity that represents an audit log entry.
 *
 * Audit logs store relevant system actions so InVault can keep traceability
 * of who did what, when and over which entity.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    // Primary key generated automatically by the database.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * User who performed the action.
     *
     * This field is optional because some future system actions could be executed
     * automatically without a direct authenticated user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /*
     * Type of action performed.
     *
     * Examples:
     * - CREATED
     * - UPDATED
     * - STOCK_MOVEMENT_CREATED
     * - LOGIN_FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    /*
     * Name of the affected entity.
     *
     * Examples:
     * - Product
     * - Batch
     * - StockMovement
     * - User
     */
    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    /*
     * Identifier of the affected entity.
     *
     * It can be null for actions that do not affect a specific database row,
     * such as a failed login attempt.
     */
    @Column(name = "entity_id")
    private Long entityId;

    /*
     * Optional readable explanation of what happened.
     *
     * Example:
     * "Product SKU-001 was deactivated by supervisor user."
     */
    @Column(length = 500)
    private String details;

    @Column(name = "before_data", length = 4000)
    private String beforeData;

    @Column(name = "after_data", length = 4000)
    private String afterData;

    /*
     * Optional client IP address.
     *
     * This can help identify where an action came from.
     */
    @Column(name = "client_ip", length = 60)
    private String clientIp;

    // Timestamp automatically assigned when the audit entry is created.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuditLog() {
        // Empty constructor required by JPA.
    }

    public AuditLog(
            User user,
            AuditAction action,
            String entityName,
            Long entityId,
            String details,
            String clientIp
    ) {
        this(user, action, entityName, entityId, details, null, null, clientIp);
    }

    public AuditLog(
            User user,
            AuditAction action,
            String entityName,
            Long entityId,
            String details,
            String beforeData,
            String afterData,
            String clientIp
    ) {
        this.user = user;
        this.action = action;
        this.entityName = normalizeEntityName(entityName);
        this.entityId = entityId;
        this.details = normalizeText(details);
        this.beforeData = normalizeText(beforeData);
        this.afterData = normalizeText(afterData);
        this.clientIp = normalizeText(clientIp);
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();

        // Keeps audit data clean before inserting into the database.
        this.entityName = normalizeEntityName(this.entityName);
        this.details = normalizeText(this.details);
        this.beforeData = normalizeText(this.beforeData);
        this.afterData = normalizeText(this.afterData);
        this.clientIp = normalizeText(this.clientIp);

        validateAuditData();
    }

    /*
     * Normalizes the entity name by removing unnecessary spaces.
     *
     * Example:
     * " Product " becomes "Product".
     */
    private String normalizeEntityName(String entityName) {
        return entityName == null ? null : entityName.trim();
    }

    /*
     * Normalizes optional text fields by removing unnecessary spaces.
     */
    private String normalizeText(String text) {
        return text == null ? null : text.trim();
    }

    /*
     * Validates the minimum required audit information.
     *
     * The audit service will create these records, but this prevents saving
     * incomplete audit entries by mistake.
     */
    private void validateAuditData() {
        if (this.action == null) {
            throw new IllegalArgumentException("Audit action is required.");
        }

        if (this.entityName == null || this.entityName.isBlank()) {
            throw new IllegalArgumentException("Audit entity name is required.");
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityName() {
        return entityName;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }

    public String getBeforeData() {
        return beforeData;
    }

    public String getAfterData() {
        return afterData;
    }

    public String getClientIp() {
        return clientIp;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

/*
 * The AuditLog entity represents a historical record of relevant system actions.
 * It stores the responsible user when available, the performed action, the affected
 * entity, optional details, client IP and creation date. These records should not
 * be freely edited because they are used for traceability.
 */
