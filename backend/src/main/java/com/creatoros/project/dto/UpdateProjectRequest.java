package com.creatoros.project.dto;

import com.creatoros.project.entity.Project;

/**
 * Request body for PATCH /api/v1/projects/{id} (partial update).
 *
 * All fields are optional — PATCH means "update only what you send".
 * vs PUT = "replace the entire resource".
 *
 * Why a Java Record for PATCH with all-optional fields?
 *   Records work fine for this. If the client doesn't send a field, Jackson
 *   sets it to null. Our service checks for null before updating:
 *     if (req.name() != null) project.setName(req.name())
 *
 * No @NotBlank here — fields are optional on PATCH.
 * We do validate in the service layer to prevent empty-string updates.
 */
public record UpdateProjectRequest(
        String           name,
        String           description,
        Project.Platform platform
) {}
