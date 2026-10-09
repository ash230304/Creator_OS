package com.creatoros.processing.controller;

import com.creatoros.common.response.ApiResponse;
import com.creatoros.processing.dto.JobResponse;
import com.creatoros.processing.service.ProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ProcessingController — endpoints to trigger and monitor video processing.
 *
 * All endpoints require a valid JWT.
 *
 * Endpoint summary:
 *   POST /api/v1/videos/{videoId}/process   → start the processing pipeline  (202)
 *   GET  /api/v1/jobs/{jobId}               → poll job status and progress   (200)
 *
 * ── Why 202 Accepted for POST /process? ──────────────────────────────────────
 * HTTP 201 Created = a new resource is immediately ready.
 * HTTP 202 Accepted = the request is accepted, work is happening asynchronously.
 * Processing takes minutes — 202 tells the client "accepted, come back and check".
 *
 * ── Polling vs WebSocket ──────────────────────────────────────────────────────
 * V1 uses polling: client hits GET /jobs/{jobId} every 3-5 seconds.
 * Simple to implement, easy to debug, no persistent connection needed.
 * V2 trade-off: WebSocket push for real-time updates without repeated requests.
 * For a creator tool where processing takes 2-5 minutes, polling every 5 seconds
 * is 24-60 requests total — perfectly acceptable load.
 */
@RestController
@RequiredArgsConstructor
public class ProcessingController {

    private final ProcessingService processingService;

    /**
     * POST /api/v1/videos/{videoId}/process
     *
     * Triggers the async pipeline for the given video.
     * Returns a jobId immediately — the pipeline runs in the background.
     *
     * Response: { jobId, videoId, status: "QUEUED", progressPercent: 0 }
     * HTTP 202 Accepted (not 201 — resource isn't ready yet).
     */
    @PostMapping("/api/v1/videos/{videoId}/process")
    public ResponseEntity<ApiResponse<JobResponse>> startProcessing(
            @PathVariable UUID videoId) {

        JobResponse job = processingService.createJob(videoId);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Processing started", job));
    }

    /**
     * GET /api/v1/jobs/{jobId}
     *
     * Polling endpoint. Client calls this every few seconds to check progress.
     * Returns status, progressPercent (0-100), and error details if FAILED.
     *
     * When status = COMPLETED → client calls GET /videos/{videoId}/clips
     * When status = FAILED    → client shows errorMessage to user
     */
    @GetMapping("/api/v1/jobs/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> getJobStatus(
            @PathVariable UUID jobId) {

        JobResponse job = processingService.getJobStatus(jobId);
        return ResponseEntity.ok(ApiResponse.ok(job));
    }
}
