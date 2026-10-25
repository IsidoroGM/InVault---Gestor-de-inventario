package com.invault.inventory.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Development schema mapping; JDBC performs the atomic counter updates. */
@Entity
@Table(name = "login_rate_limits", indexes = @Index(name = "ix_login_rate_limits_expiry", columnList = "window_expires_at"))
public class LoginRateLimitRecord {
    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "window_expires_at", nullable = false)
    private long windowExpiresAt;

    @Column(nullable = false)
    private boolean reported;

    protected LoginRateLimitRecord() {}
}
