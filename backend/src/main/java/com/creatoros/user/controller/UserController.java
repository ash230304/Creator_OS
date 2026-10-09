package com.creatoros.user.controller;

import com.creatoros.common.response.ApiResponse;
import com.creatoros.user.dto.UpdateUserRequest;
import com.creatoros.user.dto.UserResponse;
import com.creatoros.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * UserController — profile endpoints for the authenticated user.
 *
 * All endpoints require a valid JWT.
 * Users can only see and edit their OWN profile — there's no admin endpoint
 * to view another user's data. That's a V2 concern (team features).
 *
 * Endpoint summary:
 *   GET /api/v1/users/me    → get my profile                      (200)
 *   PUT /api/v1/users/me    → update name / bio / avatarUrl        (200)
 *
 * ── Why /users/me and not /users/{id}? ───────────────────────────────────────
 * /users/{id} would require the client to know their own ID upfront.
 * /users/me uses the JWT to identify the caller — cleaner and more RESTful
 * for the "current user" concept. Twitter, GitHub, and most modern APIs use this.
 *
 * ── Why PUT and not PATCH? ────────────────────────────────────────────────────
 * Strictly, PATCH = partial update. But PUT is simpler for clients to implement.
 * We handle partial semantics in the service layer (null = keep existing value).
 * For a single-resource profile endpoint, this is an acceptable trade-off.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/v1/users/me
     *
     * Returns the full profile of the currently authenticated user.
     * Response: { id, name, email, bio, avatarUrl, createdAt }
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe() {
        UserResponse user = userService.getMe();
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    /**
     * PUT /api/v1/users/me
     *
     * Updates the current user's profile.
     * Only name, bio, and avatarUrl can be changed here.
     * Email and password require separate, security-verified flows.
     *
     * Any field not sent (null) is left unchanged.
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @Valid @RequestBody UpdateUserRequest request) {

        UserResponse updated = userService.updateMe(request);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", updated));
    }
}
