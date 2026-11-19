package com.creatoros.common.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Path;

/**
 * FileController — serves uploaded and generated files (clips, captioned clips).
 *
 * Endpoint: GET /files/**
 * Maps the path after /files/ to the local uploads directory.
 *
 * Example: GET /files/clips/abc123.mp4
 *   → serves ./uploads/clips/abc123.mp4
 *
 * Security note: Path traversal is prevented by resolving against the
 * configured base path and verifying the resolved path is within it.
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FileController {

    @Value("${storage.local.base-path:./uploads}")
    private String storagePath;

    @GetMapping("/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        // Strip the /files/ prefix to get the relative path
        String relativePath = requestUri.replaceFirst("^/files/", "");

        // Resolve against the storage base, normalizing to prevent traversal
        Path base    = Path.of(storagePath).toAbsolutePath().normalize();
        Path target  = base.resolve(relativePath).normalize();

        // Security: ensure the resolved path is within the base directory
        if (!target.startsWith(base)) {
            log.warn("Path traversal attempt blocked: {}", relativePath);
            return ResponseEntity.badRequest().build();
        }

        File file = target.toFile();
        if (!file.exists() || !file.isFile()) {
            log.warn("Requested file not found: {}", target);
            return ResponseEntity.notFound().build();
        }

        // Detect content type
        String contentType = "application/octet-stream";
        String name = file.getName().toLowerCase();
        if (name.endsWith(".mp4")) contentType = "video/mp4";
        else if (name.endsWith(".webm")) contentType = "video/webm";
        else if (name.endsWith(".mov")) contentType = "video/quicktime";

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}
