package com.creatoros.transcription.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TranscriptSegment — a single timestamped segment from Whisper's response.
 *
 * ── What Whisper returns ──────────────────────────────────────────────────────
 * The Whisper API response includes a "segments" array:
 * {
 *   "text": "Full transcript...",
 *   "segments": [
 *     { "id": 0, "start": 0.0, "end": 4.2, "text": "Most students fail because..." },
 *     { "id": 1, "start": 4.2, "end": 9.8, "text": "Here's what actually works." }
 *   ],
 *   "language": "en"
 * }
 *
 * We only keep the fields relevant to clip detection: start, end, text.
 *
 * ── Why a Java record? ────────────────────────────────────────────────────────
 * Records are immutable by default — perfect for data transfer objects.
 * Jackson deserializes JSON to records automatically (Spring Boot 3 / Jackson 2.12+).
 *
 * ── @JsonProperty ─────────────────────────────────────────────────────────────
 * Whisper returns "start", "end", "text" in snake_case — these map directly.
 * The annotation is included explicitly for clarity even when names match.
 */
public record TranscriptSegment(

        @JsonProperty("start")
        double start,

        @JsonProperty("end")
        double end,

        @JsonProperty("text")
        String text
) {}
