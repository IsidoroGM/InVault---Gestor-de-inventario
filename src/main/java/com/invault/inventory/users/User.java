package com.invault.inventory.users;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.invault.inventory.roles.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entity that represents an internal application user.
 *
 * Users are authenticated people who can access InVault and perform actions
 * depending on their assigned roles.
 */
@Entity
@Table(name = "users")
public class User {

    // Primary key generated automatically by the database.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique username used to identify the user inside the application.
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // Unique email used for contact, login recovery or future notifications.
    @Column(nullable = false, unique = true, length = 120)
    private String email;

    // Encrypted password. We never store plain text passwords.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // Allows disabling a user without deleting it from the database.
    @Column(nullable = false)
    private Boolean active = true;

    // Used for the first admin user or password reset flows.
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    // Incrementing this value invalidates every JWT issued with an older version.
    @Column(name = "token_version", nullable = false)
    private Long tokenVersion = 0L;

    // Stores the last successful login date and time.
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // Timestamp automatically assigned when the user is first created.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Timestamp automatically updated whenever the user changes.
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /*
     * A user can have several roles.
     *
     * Example:
     * - One user can be ADMIN and SUPERVISOR.
     * - Another user can only be READ_ONLY.
     *
     * The intermediate table user_roles connects users with roles.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"),
        uniqueConstraints = {
            @UniqueConstraint(
                name = "uk_user_roles_user_role",
                columnNames = {"user_id", "role_id"}
            )
        }
    )
    private Set<Role> roles = new HashSet<>();

    public User() {
        // Empty constructor required by JPA.
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = true;
        this.mustChangePassword = false;
        this.tokenVersion = 0L;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();

        // Defensive defaults in case the entity is created without these values.
        if (this.active == null) {
            this.active = true;
        }

        if (this.mustChangePassword == null) {
            this.mustChangePassword = false;
        }

        if (this.tokenVersion == null) {
            this.tokenVersion = 0L;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addRole(Role role) {
        if (role != null) {
            this.roles.add(role);
        }
    }

    public void removeRole(Role role) {
        if (role != null) {
            this.roles.remove(role);
        }
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public long revokeActiveTokens() {
        long currentVersion = tokenVersion == null ? 0L : tokenVersion;
        tokenVersion = Math.incrementExact(currentVersion);
        return tokenVersion;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles != null ? roles : new HashSet<>();
    }
}

/*
 * The User entity represents an internal InVault account.
 * It stores identity data, password hash, account status, password-change state,
 * login timestamps and the roles assigned to the user through the user_roles table.
 */
