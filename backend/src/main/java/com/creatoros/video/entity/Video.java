package com.creatoros.video.entity;

import com.creatoros.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Video entity — maps to the "videos" table (V4 migration).
 *
 * Lifecycle tracked via status field:
 *   UPLOADED    → file received, not yet processed
 *   QUEUED      → processing job created, waiting for worker
 *   PROCESSING  → worker is actively running the pipeline
 *   COMPLETED   → clips and captions ready
 *   FAILED      → pipeline failed (see processing_jobs for detail)
 *
 * ── Why store original_filename separately from storage_path? ─────────────────
 * original_filename: what the user called their file. Display-only.
 * storage_path: where WE saved it (UUID-based, safe from path traversal attacks).
 * Never use original_filename to construct file system paths.
 *
 * ── Why BIGINT for file_size_bytes? ──────────────────────────────────────────
 * INT maxes at ~2.1 GB. Video files can easily exceed this.
 * BIGINT (Java Long) handles up to ~9.2 EB — safe for any realistic file.
 */
@Entity
@Table(name = "videos")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Video {

    public enum Status { UPLOADED, QUEUED, PROCESSING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** Original name from the user's file system — display only, never used as a path. */
    @Column(nullable = false, length = 500)
    private String originalFilename;

    /** Where we actually saved the file: uploads/videos/{uuid}.mp4 or S3 key. */
    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column
    private Long fileSizeBytes;

    /** Duration with millisecond precision — FFmpeg works in float seconds. */
    @Column(precision = 10, scale = 3)
    private BigDecimal durationSeconds;

    /** MIME type: video/mp4, video/quicktime, etc. Used to validate and tell FFmpeg. */
    @Column(length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.UPLOADED;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Transitions the video to a new lifecycle status.
     * All status changes go through here — single point of control.
     * Hibernate dirty-checking picks up the change on transaction commit.
     */
    public void updateStatus(Status newStatus) {
        this.status = newStatus;
    }
}
