package com.creatoros.video.service;

import com.creatoros.common.exception.ResourceNotFoundException;
import com.creatoros.common.security.SecurityUtils;
import com.creatoros.project.entity.Project;
import com.creatoros.project.repository.ProjectRepository;
import com.creatoros.video.dto.VideoResponse;
import com.creatoros.video.entity.Video;
import com.creatoros.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * VideoService — handles video upload, storage, and retrieval.
 *
 * ── Storage abstraction ────────────────────────────────────────────────────────
 * V1 stores files on local disk at: uploads/videos/{uuid}.{ext}
 * The storagePath saved in DB is relative (not absolute), so moving to S3
 * later only requires changing the read/write implementation, not the schema.
 *
 * ── Why UUID for storage path? ────────────────────────────────────────────────
 * User-supplied filenames can contain: path traversal (../../etc/passwd),
 * special characters, duplicates, and encoding issues.
 * We generate a UUID for the actual file path and store the original name
 * in original_filename (display only). This is a standard security pattern.
 *
 * ── File validation ──────────────────────────────────────────────────────────
 * We check the MIME type reported by the client. Note: clients can spoof MIME types.
 * A more robust V2 implementation would use Apache Tika to detect MIME from bytes.
 * For V1, content-type validation is sufficient.
 *
 * ── TODO (V2): Replace local file storage with S3 ───────────────────────────
 * Extract a StorageService interface with:
 *   store(UUID id, InputStream data, String mimeType) → String storagePath
 *   retrieve(String storagePath) → InputStream
 * LocalStorageService and S3StorageService implement it.
 * VideoService only calls StorageService, never touches the filesystem directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository   videoRepository;
    private final ProjectRepository projectRepository;

    private static final String UPLOAD_DIR = "uploads/videos/";
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "video/mp4", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/webm"
    );

    // ── UPLOAD ───────────────────────────────────────────────────────────────

    /**
     * Accept a video file upload for a given project.
     * Validates ownership, validates MIME type, saves to disk, persists metadata.
     *
     * @param projectId  which project this video belongs to
     * @param file       the multipart file from the HTTP request
     * @return VideoResponse with id and status=UPLOADED
     */
    @Transactional
    public VideoResponse upload(UUID projectId, MultipartFile file) {
        UUID userId = SecurityUtils.getCurrentUserId();

        // 1. Verify project ownership
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        // 2. Validate MIME type
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new com.creatoros.common.exception.BusinessException(
                    "Unsupported file type: " + mimeType +
                    ". Allowed: mp4, mov, avi, mkv, webm");
        }

        // 3. Derive safe storage path (UUID-based, no user-supplied filename in path)
        String extension   = getExtension(file.getOriginalFilename());
        String storageName = UUID.randomUUID() + "." + extension;
        String storagePath = UPLOAD_DIR + storageName;

        // 4. Save file to disk
        saveFileToDisk(file, storagePath);

        // 5. Persist video metadata
        Video video = Video.builder()
                .project(project)
                .originalFilename(file.getOriginalFilename())
                .storagePath(storagePath)
                .fileSizeBytes(file.getSize())
                .mimeType(mimeType)
                .status(Video.Status.UPLOADED)
                .build();

        Video saved = videoRepository.saveAndFlush(video);
        log.info("Video {} uploaded for project {} by user {}", saved.getId(), projectId, userId);

        return VideoResponse.from(saved);
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VideoResponse> listForProject(UUID projectId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        return videoRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(VideoResponse::from)
                .toList();
    }

    // ── GET BY ID ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VideoResponse getById(UUID videoId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Video video = videoRepository.findByIdAndProjectUserId(videoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));
        return VideoResponse.from(video);
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID videoId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Video video = videoRepository.findByIdAndProjectUserId(videoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));

        // Delete from filesystem (best-effort — don't fail if file missing)
        try {
            Files.deleteIfExists(Paths.get(video.getStoragePath()));
        } catch (IOException e) {
            log.warn("Could not delete video file {}: {}", video.getStoragePath(), e.getMessage());
        }

        videoRepository.delete(video);
        log.info("Video {} deleted by user {}", videoId, userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void saveFileToDisk(MultipartFile file, String storagePath) {
        try {
            Path destination = Paths.get(storagePath);
            Files.createDirectories(destination.getParent());
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save uploaded file to {}: {}", storagePath, e.getMessage());
            throw new RuntimeException("File storage failed: " + e.getMessage(), e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "mp4";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
