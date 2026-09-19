package com.creatoros.project.repository;

import com.creatoros.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Project repository.
 *
 * Key queries we need:
 *   1. List all projects for a user (dashboard)
 *   2. Find a specific project by ID + userId (ownership check)
 *
 * Why findByIdAndUserId instead of just findById?
 *   If user A calls GET /projects/{id} with project ID belonging to user B,
 *   findById() would return it — a data leak / IDOR vulnerability.
 *   (IDOR = Insecure Direct Object Reference — a common API security bug)
 *
 *   findByIdAndUserId() adds the user check in SQL:
 *     SELECT * FROM projects WHERE id = ? AND user_id = ?
 *   If the project exists but belongs to someone else → returns empty → 404.
 *   The caller never knows whether the project doesn't exist or belongs to another user.
 *   This is intentional: never leak existence of other users' resources.
 *
 * Spring Data JPA method name → SQL:
 *   findAllByUserId        → SELECT * FROM projects WHERE user_id = ? ORDER BY ...
 *   findByIdAndUserId      → SELECT * FROM projects WHERE id = ? AND user_id = ?
 *   countByUserId          → SELECT COUNT(*) FROM projects WHERE user_id = ?
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * All projects for a user, newest first.
     * Used for: GET /api/v1/projects (dashboard list)
     */
    List<Project> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find a specific project that belongs to a specific user.
     * Ownership-safe: returns empty if project doesn't belong to user.
     * Used for: GET/PATCH/DELETE /api/v1/projects/{id}
     */
    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Count how many projects a user has.
     * Useful for rate-limiting or showing stats on the dashboard.
     */
    long countByUserId(UUID userId);
}
