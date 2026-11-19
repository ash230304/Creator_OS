package com.creatoros.transcription.entity;

import com.creatoros.video.entity.Video;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * VideoTranscript entity — maps to the "video_transcripts" table (V6 migration).
 *
 * ── Why 1-to-1 with Video? ────────────────────────────────────────────────────
 * A video has exactly one transcript. The UNIQUE(video_id) constraint in the DB
 * enforces this. Attempting to insert a second transcript for the same video
 * throws a DataIntegrityViolationException.
 *
 * ── Why store segments as JSONB (String here)? ───────────────────────────────
 * The Whisper response contains a list of timestamped segments:
 *   [{ "start": 0.0, "end": 4.2, "text": "..." }, ...]
 *
 * Storing as JSONB gives us:
 *   1. GIN-indexed queries (e.g. find segments with a keyword)
 *   2. Flexibility — segment schema can evolve without migrations
 *   3. No need for a separate transcript_segments table
 *
 * The JSON string is deserialized into List<TranscriptSegment> in
 * TranscriptionService when the clip detector needs it.
 *
 * ── @JdbcTypeCode(SqlTypes.JSON) ─────────────────────────────────────────────
 * This tells Hibernate to treat the column as JSON, not plain text.
 * Required for PostgreSQL JSONB columns with Hibernate 6+.
 */
@Entity
@Table(name = "video_transcripts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The video this transcript belongs to — 1-to-1, enforced by UNIQUE constraint. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false, unique = true, updatable = false)
    private Video video;

    /** The full concatenated transcript text — used for display and full-text search. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String fullText;

    /**
     * Timestamped Whisper segments stored as JSON string.
     * Format: [{"start": 0.0, "end": 4.2, "text": "..."}]
     * Mapped to/from JSON by the service layer using ObjectMapper.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String segments;

    /** ISO 639-1 language code detected by Whisper: "en", "hi", "es", etc. */
    @Column(length = 10, nullable = false)
    @Builder.Default
    private String language = "en";

    /** Which model produced this — "whisper-1" (API) or "medium"/"large" (local). */
    @Column(length = 100)
    private String modelUsed;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
