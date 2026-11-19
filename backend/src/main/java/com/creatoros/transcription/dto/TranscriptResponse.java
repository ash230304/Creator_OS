package com.creatoros.transcription.dto;

import com.creatoros.transcription.entity.VideoTranscript;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TranscriptResponse — the API response for GET /api/v1/videos/{id}/transcript.
 *
 * ── Factory method pattern ────────────────────────────────────────────────────
 * Like all other response DTOs in this project (ScriptResponse, JobResponse),
 * we use a static factory method `from()` instead of a constructor.
 *
 * Why:
 *   - Makes the conversion logic explicit and testable
 *   - Controller stays clean: return TranscriptResponse.from(transcript, mapper)
 *   - Entities are never exposed directly to API consumers
 *
 * ── segments deserialization ──────────────────────────────────────────────────
 * The entity stores segments as a JSON string (JSONB column).
 * Here we parse it back into List<TranscriptSegment> so the client gets
 * a proper JSON array, not a raw string.
 */
public record TranscriptResponse(

        UUID id,
        UUID videoId,
        String fullText,
        List<TranscriptSegment> segments,
        String language,
        String modelUsed,
        Instant createdAt

) {
    public static TranscriptResponse from(VideoTranscript transcript, ObjectMapper objectMapper) {
        List<TranscriptSegment> parsedSegments = parseSegments(transcript.getSegments(), objectMapper);

        return new TranscriptResponse(
                transcript.getId(),
                transcript.getVideo().getId(),
                transcript.getFullText(),
                parsedSegments,
                transcript.getLanguage(),
                transcript.getModelUsed(),
                transcript.getCreatedAt()
        );
    }

    private static List<TranscriptSegment> parseSegments(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<TranscriptSegment>>() {});
        } catch (Exception e) {
            return List.of(); // return empty list rather than crashing if JSON is malformed
        }
    }
}
