package com.creatoros.video.controller;

import com.creatoros.common.response.ApiResponse;
import com.creatoros.video.dto.VideoResponse;
import com.creatoros.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * VideoController — REST endpoints for video upload and management.
 *
 * All endpoints require a valid JWT.
 *
 * Endpoint summary:
 *   POST   /api/v1/videos                   → upload a video file           (201)
 *   GET    /api/v1/videos/project/{projId}  → list videos in a project      (200)
 *   GET    /api/v1/videos/{id}              → get one video by ID           (200)
 *   DELETE /api/v1/videos/{id}             → delete video + file on disk   (204)
 *
 * ── Why multipart/form-data and not JSON? ─────────────────────────────────────
 * Files cannot be sent as JSON — they're binary data.
 * multipart/form-data is the HTTP standard for mixed text + binary uploads.
 * The @RequestParam("file") MultipartFile annotation handles the parsing.
 * Spring's MultipartAutoConfiguration configures the max file size (application.yml).
 *
 * ── File size limit ───────────────────────────────────────────────────────────
 * Configured in application.yml:
 *   spring.servlet.multipart.max-file-size=500MB
 *   spring.servlet.multipart.max-request-size=500MB
 * Exceeding this → Spring throws MaxUploadSizeExceededException → 413.
 */
@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    /**
     * POST /api/v1/videos
     * Content-Type: multipart/form-data
     *
     * Form fields:
     *   projectId  — UUID of the project this video belongs to
     *   file       — the video file (mp4, mov, avi, mkv, webm)
     *
     * Returns: { videoId, status: "UPLOADED", originalFilename, fileSizeBytes, ... }
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VideoResponse>> upload(
            @RequestParam UUID projectId,
            @RequestParam MultipartFile file) {

        VideoResponse video = videoService.upload(projectId, file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Video uploaded successfully", video));
    }

    /**
     * GET /api/v1/videos/project/{projectId}
     * Lists all videos uploaded to a project, newest first.
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<VideoResponse>>> listForProject(
            @PathVariable UUID projectId) {

        List<VideoResponse> videos = videoService.listForProject(projectId);
        return ResponseEntity.ok(ApiResponse.ok(videos));
    }

    /**
     * GET /api/v1/videos/{id}
     * Returns metadata for a single video (not the file itself).
     * Returns 404 if the video doesn't exist or belongs to another user.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VideoResponse>> getById(
            @PathVariable UUID id) {

        VideoResponse video = videoService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(video));
    }

    /**
     * DELETE /api/v1/videos/{id}
     * Deletes the video metadata from DB and the file from disk.
     * Cascade: also deletes all clips and processing_jobs for this video.
     * Returns 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        videoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
