package com.creatoros.clipping.controller;

import com.creatoros.clipping.dto.ClipResponse;
import com.creatoros.clipping.service.ClippingService;
import com.creatoros.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ClipController — REST endpoints for generated clips.
 *
 * All endpoints require a valid JWT (enforced by SecurityConfig).
 *
 * Endpoint summary:
 *   GET /api/v1/videos/{videoId}/clips     → list all clips for a video (rank order)
 *   GET /api/v1/clips/{clipId}             → get a single clip by ID
 *
 * ── Why two endpoint shapes? ─────────────────────────────────────────────────
 * GET /videos/{id}/clips  — resource-oriented: "clips that belong to this video".
 *   Used by the dashboard to show all clips after processing completes.
 *
 * GET /clips/{id}         — direct clip access by ID.
 *   Used when the client wants a single clip (e.g. after following a deep link).
 *
 * ── No POST endpoint here ────────────────────────────────────────────────────
 * Clips are NOT created via REST. They are created by the async processing
 * pipeline (ProcessingService → ClippingService → ClipDetectionService → FFmpeg).
 * The client triggers this via POST /api/v1/videos/{id}/process.
 */
@RestController
@RequiredArgsConstructor
public class ClipController {

    private final ClippingService clippingService;

    /**
     * GET /api/v1/videos/{videoId}/clips
     *
     * Returns all clips for a video, ordered by rank (rank 1 = best clip first).
     * Returns an empty list if processing hasn't completed yet.
     * Returns 404 if the video doesn't exist or belongs to another user.
     */
    @GetMapping("/api/v1/videos/{videoId}/clips")
    public ResponseEntity<ApiResponse<List<ClipResponse>>> listClips(
            @PathVariable UUID videoId) {

        List<ClipResponse> clips = clippingService.listClips(videoId);
        return ResponseEntity.ok(ApiResponse.ok(clips));
    }

    /**
     * GET /api/v1/clips/{clipId}
     *
     * Returns a single clip by its UUID.
     * Returns 404 if the clip doesn't exist or belongs to another user's video.
     */
    @GetMapping("/api/v1/clips/{clipId}")
    public ResponseEntity<ApiResponse<ClipResponse>> getClip(
            @PathVariable UUID clipId) {

        ClipResponse clip = clippingService.getClip(clipId);
        return ResponseEntity.ok(ApiResponse.ok(clip));
    }
}
