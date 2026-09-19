package com.creatoros.auth.repository;

import com.creatoros.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for User.
 *
 * How Spring Data JPA works:
 *   - You declare an interface extending JpaRepository<EntityType, IdType>
 *   - Spring generates the implementation at startup — no SQL needed for
 *     common operations (save, findById, delete, etc.)
 *   - For custom queries, Spring parses the METHOD NAME and generates SQL:
 *
 *   findByEmail(String email)
 *       ↓ Spring generates:
 *   SELECT * FROM users WHERE email = ?
 *
 *   The naming convention is:
 *   find + By + FieldName + (optional: And/Or + MoreFields)
 *
 * Why Optional<User>?
 *   Optional forces the caller to handle the "not found" case explicitly.
 *   It's a Java 8+ pattern to avoid NullPointerException.
 *   Usage:
 *     userRepository.findByEmail(email)
 *         .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
 *
 * JpaRepository<User, UUID> gives us for free:
 *   save(user)           → INSERT or UPDATE
 *   findById(uuid)       → SELECT WHERE id = ?
 *   existsById(uuid)     → SELECT COUNT WHERE id = ?
 *   findAll()            → SELECT *
 *   delete(user)         → DELETE WHERE id = ?
 *   count()              → SELECT COUNT(*)
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by their email address.
     * Used in:
     *   - AuthService.login()   → check if email exists, then verify password
     *   - AuthService.register() → check if email already taken
     *   - JwtAuthFilter         → load user details from the JWT's sub (userId) claim
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if an email is already registered without loading the full User object.
     * More efficient than findByEmail() for duplicate-check purposes.
     *
     * Spring generates:
     *   SELECT COUNT(*) > 0 FROM users WHERE email = ?
     */
    boolean existsByEmail(String email);
}
