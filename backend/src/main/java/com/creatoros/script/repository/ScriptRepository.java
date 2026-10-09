package com.creatoros.script.repository;

import com.creatoros.script.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Script repository.
 * Scripts are always accessed through their project.
 * No direct "find script by ID without project" — keeps ownership chain intact.
 */
@Repository
public interface ScriptRepository extends JpaRepository<Script, UUID> {

    /**
     * All scripts for a project, newest first.
     * A project can have multiple script iterations.
     */
    List<Script> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * Fetch a single script scoped to a project's owner.
     * Traverses the join: script → project → user.
     * Spring generates: SELECT * FROM scripts s
     *                   JOIN projects p ON s.project_id = p.id
     *                   WHERE s.id = ? AND p.user_id = ?
     *
     * This prevents a user from fetching another user's script
     * by knowing the script UUID — they get a 404, not the data.
     */
    java.util.Optional<Script> findByIdAndProjectUserId(UUID scriptId, UUID userId);
}
