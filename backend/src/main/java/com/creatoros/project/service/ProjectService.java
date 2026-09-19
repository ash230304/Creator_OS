package com.creatoros.project.service;

import com.creatoros.auth.entity.User;
import com.creatoros.common.exception.ResourceNotFoundException;
import com.creatoros.common.security.SecurityUtils;
import com.creatoros.project.dto.CreateProjectRequest;
import com.creatoros.project.dto.ProjectResponse;
import com.creatoros.project.dto.UpdateProjectRequest;
import com.creatoros.project.entity.Project;
import com.creatoros.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ProjectService — all business logic for project management.
 *
 * ── Ownership pattern ────────────────────────────────────────────────────────
 * Every operation that touches a specific project uses findByIdAndUserId().
 * This means:
 *   - User A cannot read, update, or delete User B's projects
 *   - Even if User A knows the project UUID (UUIDs are guessable by collision,
 *     but unpredictable in practice), they get a 404, not the data
 *
 * ── @Transactional ───────────────────────────────────────────────────────────
 * @Transactional on a method = Spring wraps it in a DB transaction.
 * Why is it on update/delete but not on the read (list/getById)?
 *   - Reads use @Transactional(readOnly = true) — tells Spring/Hibernate
 *     "this is read-only", which skips dirty-checking (performance win).
 *   - Writes need a regular transaction so changes are committed atomically.
 *
 * ── Entity → DTO mapping ─────────────────────────────────────────────────────
 * All methods return ProjectResponse, never the Project entity.
 * The mapping happens via ProjectResponse.from(project).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        User currentUser = SecurityUtils.getCurrentUser();

        Project project = Project.builder()
                .user(currentUser)
                .name(request.name().trim())
                .description(request.description())
                .platform(request.platform())
                .build();   // status defaults to DRAFT via @Builder.Default

        // saveAndFlush: flush forces the INSERT immediately so Hibernate populates
        // @CreationTimestamp / @UpdateTimestamp before we map to the DTO.
        // save() alone buffers the write — timestamps may be null before flush.
        Project saved = projectRepository.saveAndFlush(project);
        log.info("Project created: {} by user {}", saved.getId(), currentUser.getId());

        return ProjectResponse.from(saved);
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectResponse> listForCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();

        return projectRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ProjectResponse::from)   // method reference = p -> ProjectResponse.from(p)
                .toList();                     // Java 16+ immutable list
    }

    // ── GET BY ID ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectResponse getById(UUID projectId) {
        UUID userId = SecurityUtils.getCurrentUserId();

        Project project = findOwnedProjectOrThrow(projectId, userId);
        return ProjectResponse.from(project);
    }

    // ── UPDATE (PATCH) ───────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse update(UUID projectId, UpdateProjectRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();

        Project project = findOwnedProjectOrThrow(projectId, userId);

        // updateDetails is a mutation method on the entity — only updates non-null fields
        project.updateDetails(request.name(), request.description(), request.platform());

        // No explicit save() call needed here!
        // @Transactional + JPA "dirty checking":
        // Hibernate tracks changes to managed entities within a transaction.
        // When the transaction commits, Hibernate generates UPDATE SQL for changed fields.
        // This is called the "Unit of Work" pattern.
        log.info("Project updated: {} by user {}", projectId, userId);

        return ProjectResponse.from(project);
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID projectId) {
        UUID userId = SecurityUtils.getCurrentUserId();

        Project project = findOwnedProjectOrThrow(projectId, userId);
        projectRepository.delete(project);

        // Cascade: DB's ON DELETE CASCADE will also delete:
        //   scripts, videos, clips, transcripts, processing_jobs
        log.info("Project deleted: {} by user {}", projectId, userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Load a project by ID scoped to the current user.
     * Throws ResourceNotFoundException if not found OR if it belongs to someone else.
     * Intentionally doesn't distinguish the two cases — don't leak resource existence.
     */
    private Project findOwnedProjectOrThrow(UUID projectId, UUID userId) {
        return projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + projectId));
    }
}
