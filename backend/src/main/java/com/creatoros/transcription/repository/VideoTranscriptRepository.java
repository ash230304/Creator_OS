package com.creatoros.transcription.repository;

import com.creatoros.transcription.entity.VideoTranscript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * VideoTranscriptRepository — data access for Whisper transcription results.
 *
 * ── Ownership pattern ──────────────────────────────────────────────────────────
 * Like all other repositories, lookups go through the ownership chain:
 *   transcript → video → project → user
 *
 * findByVideoIdAndVideoProjectUserId is the ownership-scoped equivalent of
 * "find transcript for this video, but only if it belongs to the current user."
 *
 * ── Why Optional? ─────────────────────────────────────────────────────────────
 * A video may not have a transcript yet (not processed, or processing failed).
 * Optional forces the caller to handle the "not yet transcribed" case explicitly
 * rather than risking a NullPointerException.
 */
@Repository
public interface VideoTranscriptRepository extends JpaRepository<VideoTranscript, UUID> {

    /**
     * Find the transcript for a video — simple lookup, no ownership check.
     * Used internally by TranscriptionService when saving or fetching for pipeline steps.
     * SELECT * FROM video_transcripts WHERE video_id = ?
     */
    Optional<VideoTranscript> findByVideoId(UUID videoId);

    /**
     * Ownership-scoped lookup: transcript → video → project → user.
     * Returns empty if transcript doesn't exist OR video belongs to another user.
     * Used by TranscriptionController to safely expose results.
     */
    Optional<VideoTranscript> findByVideoIdAndVideoProjectUserId(UUID videoId, UUID userId);
}
