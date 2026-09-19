package com.creatoros.project.entity;

import com.creatoros.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the "projects" table (V2 migration).
 *
 * Key relationship:
 *   Many Projects belong to one User.
 *   @ManyToOne → "many projects, one user"
 *   @JoinColumn → the FK column in the projects table = "user_id"
 *
 * Why @ManyToOne(fetch = LAZY)?
 *   EAGER (the default for @ManyToOne) would load the full User object
 *   every time you load a Project — even when you don't need it.
 *   LAZY = only load the User when you explicitly call project.getUser().
 *   This avoids unnecessary DB joins and N+1 query problems.
 *
 * @Enumerated(EnumType.STRING):
 *   Stores the enum as its name ("DRAFT", "SCRIPTED"...) not its ordinal (0, 1, 2...).
 *   ALWAYS use STRING. Ordinal breaks if you ever add enum values in the middle.
 */
@Entity
@Table(name = "projects")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * The owner of this project.
     * Stored as "user_id" UUID column in the DB (not the full User object).
     * FetchType.LAZY = User is loaded from DB only if you call getUser().
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Target social platform.
     * Stored as VARCHAR in DB via @Enumerated(STRING).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Platform platform;

    /**
     * Current lifecycle status of the project.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Status status = Status.DRAFT;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // ── Enums ────────────────────────────────────────────────────────────────

    public enum Platform {
        INSTAGRAM_REEL,
        YOUTUBE_SHORT,
        TIKTOK,
        GENERAL
    }

    public enum Status {
        DRAFT,      // just created
        SCRIPTED,   // AI script generated
        UPLOADED,   // video uploaded
        PROCESSED,  // clips ready
        ARCHIVED    // hidden from dashboard
    }

    // ── Mutation methods (instead of setters) ─────────────────────────────────
    // We expose specific update methods rather than open setters.
    // This documents intent: "you can update name, description, platform"
    // and makes the entity self-documenting about what's mutable.

    public void updateDetails(String name, String description, Platform platform) {
        if (name != null && !name.isBlank()) this.name = name.trim();
        if (description != null)             this.description = description;
        if (platform != null)                this.platform = platform;
    }

    public void updateStatus(Status newStatus) {
        this.status = newStatus;
    }
}
