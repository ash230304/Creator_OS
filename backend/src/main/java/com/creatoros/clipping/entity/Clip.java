package com.creatoros.clipping.entity;

import com.creatoros.video.entity.Video;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Clip entity — maps to the "clips" table (V5 migration).
 *
 * A clip is one highlight segment extracted from a video by FFmpeg.
 * It has:
 *   - a score (0.0–1.0) assigned by the clip detection algorithm
 *   - a rank (1 = best clip for this video)
 *   - a storage_path (where FFmpeg wrote the clip file)
 *   - a caption_path (where FFmpeg wrote the captioned version)
 *   - a caption_status (PENDING → COMPLETED | FAILED)
 *
 * ── Why BigDecimal for times and score? ──────────────────────────────────────
 * FFmpeg timestamps are floats: 42.500 seconds, 67.250 seconds.
 * BigDecimal gives exact representation — Float/Double can drift in rounding.
 * The DB uses DECIMAL(10,3) — 3 decimal places (millisecond precision).
 *
 * ── duration_seconds ─────────────────────────────────────────────────────────
 * This is a GENERATED ALWAYS AS column in PostgreSQL:
 *   duration_seconds = end_time - start_time
 * Hibernate reads it (insertable=false, updatable=false).
 * Never set this field manually — the DB computes it.
 *
 * ── CaptionStatus ─────────────────────────────────────────────────────────────
 * Mirrors the CHECK constraint in V5: PENDING | COMPLETED | FAILED.
 * Starts as PENDING when the clip is cut, transitions to COMPLETED
 * after FFmpeg burns captions in.
 */
@Entity
@Table(name = "clips")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clip {

    public enum CaptionStatus { PENDING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false, updatable = false)
    private Video video;

    /** Timestamp (seconds) where this clip starts in the original video. */
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal startTime;

    /** Timestamp (seconds) where this clip ends in the original video. */
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal endTime;

    /**
     * Computed by PostgreSQL: end_time - start_time.
     * Read-only in Hibernate — never write this field.
     */
    @Column(precision = 10, scale = 3, insertable = false, updatable = false)
    private BigDecimal durationSeconds;

    /**
     * Highlight score 0.0–1.0 from the clip detection algorithm.
     * Higher = more highlight-worthy.
     * 0.9500 = very strong, 0.3200 = weak.
     */
    @Column(nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal score = BigDecimal.ZERO;

    /**
     * Rank within this video — 1 = best clip.
     * Determined by sorting clips by score descending after detection.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer rank = 1;

    /** Path to the raw FFmpeg-cut clip (no captions). */
    @Column(length = 1000)
    private String storagePath;

    /** Path to the FFmpeg-captioned version of this clip. Null until ADDING_CAPTIONS. */
    @Column(length = 1000)
    private String captionPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private CaptionStatus captionStatus = CaptionStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Set by ClippingService after FFmpeg writes the clip file. */
    public void setStoragePath(String path) {
        this.storagePath = path;
    }

    /** Set by FFmpegCaptionService after burning captions in. */
    public void completeCaptions(String captionFilePath) {
        this.captionPath    = captionFilePath;
        this.captionStatus  = CaptionStatus.COMPLETED;
    }

    public void failCaptions() {
        this.captionStatus = CaptionStatus.FAILED;
    }
}
