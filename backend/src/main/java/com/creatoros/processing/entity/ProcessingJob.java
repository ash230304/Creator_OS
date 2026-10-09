package com.creatoros.processing.entity;

import com.creatoros.video.entity.Video;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * ProcessingJob — maps to the "processing_jobs" table (V7 migration).
 *
 * Each video processing attempt creates one job row.
 * A video can have multiple jobs (first attempt FAILED → user retries → new job).
 *
 * ── The state machine ────────────────────────────────────────────────────────
 *
 *   QUEUED
 *     ↓
 *   EXTRACTING_AUDIO     ← FFmpeg extracts WAV/MP3 from video
 *     ↓
 *   TRANSCRIBING         ← Whisper converts audio → timestamped text
 *     ↓
 *   DETECTING_CLIPS      ← Heuristic/LLM scores transcript segments
 *     ↓
 *   GENERATING_CLIPS     ← FFmpeg cuts clip files from video
 *     ↓
 *   ADDING_CAPTIONS      ← FFmpeg burns subtitles into each clip
 *     ↓
 *   COMPLETED
 *
 *   At any stage → FAILED (with error_message populated)
 *
 * ── progress_percent ─────────────────────────────────────────────────────────
 * QUEUED=0, EXTRACTING=10, TRANSCRIBING=25, DETECTING=50,
 * GENERATING=70, CAPTIONS=90, COMPLETED=100
 * Client polls GET /jobs/{id} and uses this for a progress bar.
 */
@Entity
@Table(name = "processing_jobs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingJob {

    public enum Status {
        QUEUED,
        EXTRACTING_AUDIO,
        TRANSCRIBING,
        DETECTING_CLIPS,
        GENERATING_CLIPS,
        ADDING_CAPTIONS,
        COMPLETED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false, updatable = false)
    private Video video;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.QUEUED;

    @Column(nullable = false)
    @Builder.Default
    private Integer progressPercent = 0;

    /** Populated when status = FAILED. Full error detail for debugging. */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Advance the job to the next pipeline stage.
     * Updates both status and progress in one operation.
     * Called by the async processing service as each stage completes.
     */
    public void advanceTo(Status newStatus, int progress) {
        this.status          = newStatus;
        this.progressPercent = progress;

        if (newStatus == Status.EXTRACTING_AUDIO && this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        if (newStatus == Status.COMPLETED || newStatus == Status.FAILED) {
            this.completedAt = Instant.now();
        }
    }

    /**
     * Mark the job as failed with an error message.
     * Separate from advanceTo() so the error case is explicit in the calling code.
     */
    public void fail(String error) {
        this.status        = Status.FAILED;
        this.errorMessage  = error;
        this.completedAt   = Instant.now();
    }
}
