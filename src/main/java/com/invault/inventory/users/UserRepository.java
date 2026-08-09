package com.invault.inventory.users;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.invault.inventory.roles.RoleName;

/**
 * Repository for accessing User data from the database.
 *
 * Spring Data JPA automatically generates the implementation.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /*
     * Finds a user by username.
     *
     * This will be useful during login and user management.
     */
    Optional<User> findByUsernameIgnoreCase(String username);

    /*
     * Finds a user by email.
     *
     * This can be useful for account recovery or user validation.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /*
     * Finds an active user by username.
     *
     * This will help prevent disabled users from authenticating.
     */
    Optional<User> findByUsernameIgnoreCaseAndActiveTrue(String username);

    /*
     * Checks if a username is already registered.
     *
     * This prevents duplicate users.
     */
    boolean existsByUsernameIgnoreCase(String username);

    /*
     * Checks if an email is already registered.
     *
     * This prevents duplicate email accounts.
     */
    boolean existsByEmailIgnoreCase(String email);

    /*
     * Returns all active users.
     *
     * This will be useful in future administration screens.
     */
    List<User> findAllByOrderByUsernameAsc();

    List<User> findByActiveTrueOrderByUsernameAsc();

    @Query("""
            select count(distinct user)
            from User user
            join user.roles role
            where user.active = true
              and role.active = true
              and role.name = :roleName
            """)
    long countActiveUsersByRole(@Param("roleName") RoleName roleName);
}

/*
 * UserRepository gives the application access to the users table.
 * It includes basic CRUD operations inherited from JpaRepository and custom
 * finder methods for username, email and active-user checks.
 */
