package com.creatoros.clipping.dto;

import com.creatoros.clipping.entity.Clip;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ClipResponse — the API shape for a generated clip.
 *
 * Returned by:
 *   GET /api/v1/videos/{videoId}/clips     → list of ClipResponse
 *   GET /api/v1/clips/{clipId}             → single ClipResponse
 *
 * ── What the frontend uses this for ──────────────────────────────────────────
 * - Display a ranked list of clips on the dashboard
 * - Show score as a "highlight strength" indicator
 * - Provide the download URL for the clip (captionPath if done, else storagePath)
 * - Show captionStatus to know if the clip is ready to download
 *
 * ── Why expose both storagePath and captionPath? ─────────────────────────────
 * If captions have been burned (COMPLETED), the client should use captionPath.
 * If captions are still PENDING, the client can still play/download the raw clip
 * from storagePath. This lets users access clips before captions are done.
 */
public record ClipResponse(

        UUID id,
        UUID videoId,

        /** Clip start time in original video (seconds, 3 decimal places). */
        BigDecimal startTime,

        /** Clip end time in original video (seconds, 3 decimal places). */
        BigDecimal endTime,

        /** Duration = end - start. Computed by DB — always accurate. */
        BigDecimal durationSeconds,

        /** Highlight score 0.0–1.0. Higher = more highlight-worthy. */
        BigDecimal score,

        /** 1 = best clip for this video. Used for UI ordering. */
        Integer rank,

        /** Path to the raw cut clip (always present once GENERATING_CLIPS is done). */
        String storagePath,

        /** Path to the captioned clip. Null until ADDING_CAPTIONS completes. */
        String captionPath,

        /** PENDING | COMPLETED | FAILED */
        Clip.CaptionStatus captionStatus,

        Instant createdAt

) {
    public static ClipResponse from(Clip clip) {
        return new ClipResponse(
                clip.getId(),
                clip.getVideo().getId(),
                clip.getStartTime(),
                clip.getEndTime(),
                clip.getDurationSeconds(),
                clip.getScore(),
                clip.getRank(),
                clip.getStoragePath(),
                clip.getCaptionPath(),
                clip.getCaptionStatus(),
                clip.getCreatedAt()
        );
    }
}
