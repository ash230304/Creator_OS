package com.creatoros.user.dto;

import jakarta.validation.constraints.Size;

/**
 * UpdateUserRequest — request body for PUT /api/v1/users/me
 *
 * All fields are optional. Null means "don't change this field".
 * This follows the PATCH/partial-update pattern even though we use PUT here —
 * it keeps the API simple (one endpoint, not one per field).
 *
 * Validation:
 *   name     — 1-100 chars if provided (can't update to blank name)
 *   bio      — max 500 chars (short creator bio)
 *   avatarUrl — max 500 chars (URL to image)
 *
 * Email and password updates are intentionally NOT here.
 * Those are security-sensitive operations that require additional
 * verification (confirm current password, email re-verification).
 * They'd be separate endpoints in V2: PUT /users/me/password, PUT /users/me/email.
 */
public record UpdateUserRequest(

        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        String name,

        @Size(max = 500, message = "Bio must not exceed 500 characters")
        String bio,

        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        String avatarUrl

) {}
