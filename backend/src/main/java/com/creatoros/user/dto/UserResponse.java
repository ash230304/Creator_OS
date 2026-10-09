package com.creatoros.user.dto;

import com.creatoros.auth.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * UserResponse — the DTO returned from GET /users/me and PUT /users/me.
 *
 * ── Why not return the User entity directly? ─────────────────────────────────
 * The User entity contains passwordHash. Serializing the entity directly
 * would expose the hash over the API — a security mistake.
 * DTOs give you full control over what is exposed.
 *
 * ── Static factory pattern ───────────────────────────────────────────────────
 * UserResponse.from(user) converts an entity to a DTO in one place.
 * If the User entity changes, you only update the mapping here,
 * not in every controller/service that builds the response.
 *
 * Fields exposed:
 *   id        — the user's UUID (frontend needs this to scope API calls)
 *   name      — display name
 *   email     — shown in profile UI
 *   bio       — optional creator bio
 *   avatarUrl — optional profile picture URL
 *   createdAt — "member since" date
 */
public record UserResponse(
        UUID    id,
        String  name,
        String  email,
        String  bio,
        String  avatarUrl,
        Instant createdAt
) {
    /**
     * Maps a User entity to a UserResponse DTO.
     * Call this everywhere instead of manually constructing the record.
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getBio(),
                user.getAvatarUrl(),
                user.getCreatedAt()
        );
    }
}
