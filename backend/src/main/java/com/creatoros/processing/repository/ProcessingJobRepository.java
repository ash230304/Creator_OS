package com.creatoros.processing.repository;

import com.creatoros.processing.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ProcessingJobRepository — data access for async video processing jobs.
 *
 * Key queries:
 *   findTopByVideoIdOrderByCreatedAtDesc → latest job for a video (for status polling)
 *   findByStatus                         → worker picks up QUEUED jobs
 *   findByVideoId                        → full history of attempts for a video
 */
@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    /**
     * The most recent job for a video.
     * Used by GET /api/v1/videos/{id}/process to show current status.
     * A video can have multiple job attempts (retries after failure).
     * "Top 1" = just the newest one.
     */
    Optional<ProcessingJob> findTopByVideoIdOrderByCreatedAtDesc(UUID videoId);

    /**
     * Find all jobs in a given status.
     * Used by the processing worker to pick up QUEUED jobs.
     * SELECT * FROM processing_jobs WHERE status = 'QUEUED'
     */
    List<ProcessingJob> findByStatus(ProcessingJob.Status status);

    /**
     * Full job history for a video — all attempts including failed ones.
     * Useful for audit/debugging.
     */
    List<ProcessingJob> findAllByVideoIdOrderByCreatedAtDesc(UUID videoId);
}
