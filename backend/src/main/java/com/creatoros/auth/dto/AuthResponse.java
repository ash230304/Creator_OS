package com.creatoros.auth.dto;

import java.util.UUID;

/**
 * Response body returned from both /register and /login.
 *
 * What gets sent back to the client:
 * {
 *   "success": true,
 *   "data": {
 *     "token":  "eyJhbGci...",   ← the JWT the client stores and sends on every request
 *     "userId": "550e8400-...",  ← so the client knows who it logged in as
 *     "name":   "Ash",          ← display name (avoids a second API call to /me)
 *     "email":  "ash@..."
 *   }
 * }
 *
 * Note: We do NOT return the password hash, bio, avatar, or any sensitive fields.
 * This is why we use a DTO for the response — the controller maps
 * the User entity → AuthResponse, not the entity directly.
 */
public record AuthResponse(
        String token,
        UUID   userId,
        String name,
        String email
) {}
