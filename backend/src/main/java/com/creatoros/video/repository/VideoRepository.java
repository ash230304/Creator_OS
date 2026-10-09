package com.creatoros.video.repository;

import com.creatoros.video.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * VideoRepository — data access for uploaded videos.
 *
 * Core queries:
 *   findAllByProjectIdOrderByCreatedAtDesc — list videos in a project (newest first)
 *   findByIdAndProjectUserId              — ownership-scoped lookup (same pattern as ScriptRepository)
 *
 * Why not a global findAll()?
 *   Videos are always accessed in the context of a project or user.
 *   A global findAll() with no filter would be a data leak risk.
 */
@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

    /**
     * All videos for a project, ordered newest first.
     * SELECT * FROM videos WHERE project_id = ? ORDER BY created_at DESC
     */
    List<Video> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * Ownership-scoped lookup: video belongs to a project that belongs to the user.
     * Traverses: video → project → user.
     * Returns empty Optional if video doesn't exist OR belongs to another user.
     */
    Optional<Video> findByIdAndProjectUserId(UUID videoId, UUID userId);
}
