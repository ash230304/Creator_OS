package com.creatoros.clipping.repository;

import com.creatoros.clipping.entity.Clip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ClipRepository — data access for generated clips.
 *
 * ── Ownership pattern ──────────────────────────────────────────────────────────
 * Clips are accessed through the chain: clip → video → project → user.
 *
 * findAllByVideoIdAndVideoProjectUserIdOrderByRankAsc — the main listing query.
 * Returns all clips for a video, in rank order (rank 1 = best clip first),
 * but ONLY if the video belongs to the current user.
 *
 * ── Why order by rank? ───────────────────────────────────────────────────────
 * The client renders clips in rank order on the dashboard.
 * rank 1 is the strongest highlight — shown first.
 * Ordering in SQL is far more efficient than ordering in Java.
 */
@Repository
public interface ClipRepository extends JpaRepository<Clip, UUID> {

    /**
     * All clips for a video, ordered by rank (best first).
     * No ownership check — used internally by pipeline services.
     */
    List<Clip> findAllByVideoIdOrderByRankAsc(UUID videoId);

    /**
     * Ownership-scoped clip list for a video: clip → video → project → user.
     * Used by the ClipController to safely expose clip lists to the API.
     */
    List<Clip> findAllByVideoIdAndVideoProjectUserIdOrderByRankAsc(UUID videoId, UUID userId);

    /**
     * Ownership-scoped single clip lookup.
     * Returns empty if clip doesn't exist or belongs to another user's video.
     */
    Optional<Clip> findByIdAndVideoProjectUserId(UUID clipId, UUID userId);

    /**
     * Count of clips already generated for a video.
     * Used to check if clip generation has been run before retrying.
     */
    long countByVideoId(UUID videoId);
}
