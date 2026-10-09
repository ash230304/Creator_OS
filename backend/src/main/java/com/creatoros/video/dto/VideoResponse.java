package com.creatoros.video.dto;

import com.creatoros.video.entity.Video;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * VideoResponse — returned from all video endpoints.
 *
 * Intentionally excludes storage_path — that's an internal server path,
 * not something the client needs. The client gets a download URL separately.
 *
 * Fields:
 *   id               — UUID to reference this video in subsequent API calls
 *   projectId        — which project this video belongs to
 *   originalFilename — display name (what the user uploaded)
 *   fileSizeBytes    — for UI display ("245 MB")
 *   durationSeconds  — float seconds, e.g. 142.750
 *   mimeType         — video/mp4, etc.
 *   status           — current lifecycle state (UPLOADED / QUEUED / PROCESSING / COMPLETED / FAILED)
 *   createdAt        — upload timestamp
 */
public record VideoResponse(
        UUID       id,
        UUID       projectId,
        String     originalFilename,
        Long       fileSizeBytes,
        BigDecimal durationSeconds,
        String     mimeType,
        String     status,
        Instant    createdAt
) {
    public static VideoResponse from(Video video) {
        return new VideoResponse(
                video.getId(),
                video.getProject().getId(),
                video.getOriginalFilename(),
                video.getFileSizeBytes(),
                video.getDurationSeconds(),
                video.getMimeType(),
                video.getStatus().name(),
                video.getCreatedAt()
        );
    }
}
