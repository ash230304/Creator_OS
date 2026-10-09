package com.creatoros.script.dto;

import com.creatoros.project.entity.Project;
import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Request body for POST /api/v1/scripts/generate
 *
 * The creator sends:
 *   - projectId: which project this script belongs to
 *   - idea:      the raw content idea (1-2 sentences)
 *   - platform:  which platform to optimise for (affects script length)
 *   - tone:      style of delivery
 *   - targetDurationSeconds: how long the final video should be
 *
 * These parameters are injected into the AI prompt to guide generation.
 */
public record GenerateScriptRequest(

        @NotNull(message = "Project ID is required")
        UUID projectId,

        @NotBlank(message = "Content idea is required")
        @Size(min = 10, max = 1000, message = "Idea must be between 10 and 1000 characters")
        String idea,

        @NotNull(message = "Platform is required")
        Project.Platform platform,

        Project.Tone tone,   // optional — defaults to NEUTRAL if null

        @Min(value = 15,  message = "Minimum script duration is 15 seconds")
        @Max(value = 600, message = "Maximum script duration is 600 seconds (10 minutes)")
        Integer targetDurationSeconds  // optional — defaults to 60

) {}
