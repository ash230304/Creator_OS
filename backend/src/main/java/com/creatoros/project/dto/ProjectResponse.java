package com.creatoros.project.dto;

import com.creatoros.project.entity.Project;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single project.
 *
 * Why not return the Project entity directly?
 *   1. Serialization safety: the entity has a @ManyToOne User field.
 *      Jackson would try to serialize the full User → which has no @JsonIgnore
 *      → would serialize passwordHash to the API response. Security disaster.
 *   2. Shape control: we can add computed fields (e.g. videoCount) without
 *      adding them to the entity.
 *   3. Decoupling: API contract is independent of DB schema changes.
 *
 * ProjectResponse.from(project) — static factory method:
 *   Maps entity → DTO in one place. If the mapping changes, update it here.
 *   Don't scatter entity → DTO logic across controllers/services.
 */
public record ProjectResponse(
        UUID             id,
        String           name,
        String           description,
        Project.Platform platform,
        Project.Status   status,
        Instant          createdAt,
        Instant          updatedAt
) {
    /**
     * Static factory: Project entity → ProjectResponse DTO.
     * Called in service: return ProjectResponse.from(project);
     */
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getPlatform(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
