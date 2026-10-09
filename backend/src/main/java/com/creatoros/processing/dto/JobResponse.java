package com.creatoros.processing.dto;

import com.creatoros.processing.entity.ProcessingJob;

import java.time.Instant;
import java.util.UUID;

/**
 * JobResponse — returned from:
 *   POST /api/v1/videos/{videoId}/process   (job created → status: QUEUED)
 *   GET  /api/v1/jobs/{jobId}               (client polls this for progress)
 *
 * The client uses progressPercent to drive a progress bar.
 * The client uses status to know when to fetch clips (COMPLETED)
 * or show an error message (FAILED).
 *
 * estimatedSeconds is null for now — V2 could calculate based on video duration
 * and average processing time from historical job data.
 */
public record JobResponse(
        UUID    jobId,
        UUID    videoId,
        String  status,
        int     progressPercent,
        String  errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
    public static JobResponse from(ProcessingJob job) {
        return new JobResponse(
                job.getId(),
                job.getVideo().getId(),
                job.getStatus().name(),
                job.getProgressPercent(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getCreatedAt()
        );
    }
}
