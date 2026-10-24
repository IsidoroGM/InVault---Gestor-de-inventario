package com.invault.inventory.stock;

import java.time.LocalDateTime;

import com.invault.inventory.users.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Schema mapping also used by Hibernate-managed development databases. Writes use JDBC locking. */
@Entity
@Table(name = "stock_movement_requests")
public class MovementRequestRecord {
    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movement_id", unique = true)
    private StockMovement movement;

    @Column(name = "response_body", length = 4000)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MovementRequestRecord() {}
}
