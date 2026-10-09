package com.creatoros.script.entity;

import com.creatoros.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the "scripts" table (V3 migration).
 *
 * ── The JSONB column trick ────────────────────────────────────────────────────
 * The 'content' column is Postgres JSONB. In Java, we store it as a String
 * (serialized JSON). @JdbcTypeCode(SqlTypes.JSON) tells Hibernate to:
 *   - On INSERT: serialize the String as JSON to the JSONB column
 *   - On SELECT: read the JSONB column back as a String
 *
 * Why String and not a Java class?
 *   Option A — String: simple, flexible, no tight coupling between DB and class structure.
 *   Option B — @Convert with Jackson: works but requires a custom AttributeConverter.
 *   Option C — Map or JsonNode: too untyped.
 *
 * We use String here and parse it into ScriptContent in the service layer using Jackson.
 * This keeps the entity simple and moves JSON logic to where it belongs.
 *
 * ── FetchType.LAZY ───────────────────────────────────────────────────────────
 * Same pattern as Project → User. Don't load the full Project on every Script fetch.
 */
@Entity
@Table(name = "scripts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String idea;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, length = 50)
    private String tone;

    @Column(nullable = false)
    private Integer targetDurationSeconds;

    /**
     * The AI-generated script stored as JSON string.
     * @JdbcTypeCode(SqlTypes.JSON) maps this to the Postgres JSONB column.
     * The actual JSON structure matches ScriptContent record.
     */
    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
