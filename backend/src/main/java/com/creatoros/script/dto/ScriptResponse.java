package com.creatoros.script.dto;

import com.creatoros.script.entity.Script;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a script — returned from generate and list endpoints.
 *
 * Note: 'content' here is ScriptContent (parsed Java object), not the raw JSON string.
 * The service layer deserializes the stored JSON string → ScriptContent before building this.
 * The client receives a clean, typed object — not a JSON-inside-JSON blob.
 */
public record ScriptResponse(
        UUID          id,
        UUID          projectId,
        String        idea,
        String        platform,
        String        tone,
        int           targetDurationSeconds,
        ScriptContent content,
        Instant       createdAt
) {
    public static ScriptResponse from(Script script, ScriptContent content) {
        return new ScriptResponse(
                script.getId(),
                script.getProject().getId(),
                script.getIdea(),
                script.getPlatform(),
                script.getTone(),
                script.getTargetDurationSeconds(),
                content,
                script.getCreatedAt()
        );
    }
}
