package com.creatoros.project.dto;

import com.creatoros.project.entity.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/projects (create).
 *
 * @NotNull on platform: the client must send one of the Platform enum values.
 * If they send an invalid string (e.g. "FACEBOOK"), Jackson throws
 * HttpMessageNotReadableException → GlobalExceptionHandler → 400.
 */
public record CreateProjectRequest(

        @NotBlank(message = "Project name is required")
        @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        String name,

        String description,   // optional

        @NotNull(message = "Platform is required (INSTAGRAM_REEL | YOUTUBE_SHORT | TIKTOK | GENERAL)")
        Project.Platform platform

) {}
