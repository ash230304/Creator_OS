package com.creatoros.transcription.controller;

import com.creatoros.common.response.ApiResponse;
import com.creatoros.transcription.dto.TranscriptResponse;
import com.creatoros.transcription.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * TranscriptionController — exposes the transcription result for a video.
 *
 * All endpoints require a valid JWT.
 *
 * Endpoint summary:
 *   GET /api/v1/videos/{videoId}/transcript  → fetch the transcript for a video (200)
 *
 * ── Why under /videos/{id}/transcript? ───────────────────────────────────────
 * A transcript is a sub-resource of a video — not a top-level resource.
 * This URL design makes the relationship clear:
 *   /api/v1/videos/{id}            → the video
 *   /api/v1/videos/{id}/transcript → the transcript of that video
 *
 * If in the future we add:
 *   /api/v1/videos/{id}/clips → clips for that video
 * The pattern stays consistent.
 *
 * ── What about POST /transcript? ─────────────────────────────────────────────
 * Transcription is triggered automatically by:
 *   POST /api/v1/videos/{id}/process
 * This fires the async pipeline which includes the TRANSCRIBING stage.
 *
 * There is no separate manual "trigger transcription" endpoint in V1.
 * The transcript is a RESULT of processing — not something the user POSTs.
 */
@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    /**
     * GET /api/v1/videos/{videoId}/transcript
     *
     * Returns the full transcript for the given video, including:
     *   - fullText: complete transcript as a single string
     *   - segments: [{start, end, text}] — Whisper's timestamped segments
     *   - language: ISO 639-1 code ("en", "hi", etc.)
     *   - modelUsed: which Whisper model produced this result
     *
     * Returns 404 if:
     *   - The video doesn't exist
     *   - The video belongs to another user
     *   - Transcription has not yet completed (video is still being processed)
     *
     * The client should poll GET /api/v1/jobs/{jobId} first to confirm
     * that the processing pipeline has completed before calling this endpoint.
     */
    @GetMapping("/{videoId}/transcript")
    public ResponseEntity<ApiResponse<TranscriptResponse>> getTranscript(
            @PathVariable UUID videoId) {

        TranscriptResponse transcript = transcriptionService.getTranscript(videoId);
        return ResponseEntity.ok(ApiResponse.ok(transcript));
    }
}
