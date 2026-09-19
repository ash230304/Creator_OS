package com.creatoros.project.controller;

import com.creatoros.common.response.ApiResponse;
import com.creatoros.project.dto.CreateProjectRequest;
import com.creatoros.project.dto.ProjectResponse;
import com.creatoros.project.dto.UpdateProjectRequest;
import com.creatoros.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ProjectController — REST endpoints for project management.
 *
 * All endpoints require a valid JWT (configured in SecurityConfig).
 * The current user is extracted from the token in the service layer via SecurityUtils.
 *
 * Endpoint summary:
 *   POST   /api/v1/projects          → create a project       (201)
 *   GET    /api/v1/projects          → list my projects        (200)
 *   GET    /api/v1/projects/{id}     → get one project         (200)
 *   PATCH  /api/v1/projects/{id}     → update name/desc/plat   (200)
 *   DELETE /api/v1/projects/{id}     → delete (cascade all)    (204)
 *
 * HTTP method semantics:
 *   POST   = create a new resource
 *   GET    = read (never changes anything)
 *   PATCH  = partial update (only sent fields change)
 *   DELETE = remove the resource, return 204 No Content (no body)
 *
 * @PathVariable UUID id:
 *   Spring automatically converts the {id} string in the URL to a UUID.
 *   If the client sends a malformed UUID, Spring returns 400 Bad Request.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @Valid @RequestBody CreateProjectRequest request) {

        ProjectResponse project = projectService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Project created", project));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> list() {
        List<ProjectResponse> projects = projectService.listForCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(projects));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getById(
            @PathVariable UUID id) {

        ProjectResponse project = projectService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(project));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @PathVariable UUID id,
            @RequestBody UpdateProjectRequest request) {
        // No @Valid here — all fields are optional on PATCH
        ProjectResponse project = projectService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Project updated", project));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        projectService.delete(id);
        // 204 No Content — successful delete, no response body
        return ResponseEntity.noContent().build();
    }
}
