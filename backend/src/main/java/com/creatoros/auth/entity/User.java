package com.creatoros.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapped to the "users" table created by V1 migration.
 *
 * JPA / Hibernate rules:
 *   @Entity  — tells Hibernate this class maps to a DB table
 *   @Table   — specifies the exact table name (default = class name lowercased)
 *   @Id      — marks the primary key field
 *   @GeneratedValue — how to generate PK values
 *     strategy = AUTO  = let Hibernate decide (uses sequences for Postgres)
 *   @Column  — maps a field to a column, lets you set constraints
 *   @CreationTimestamp — Hibernate sets this once when the row is inserted
 *   @UpdateTimestamp   — Hibernate updates this on every save
 *
 * Why no @Setter?
 *   We use @Builder to construct User objects. Once created, fields shouldn't
 *   be changed arbitrarily — only through explicit service methods.
 *   This prevents accidental mutations.
 */
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash of the password. NEVER store the raw password.
     * BCrypt output is always 60 chars — VARCHAR(255) is more than enough.
     */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 500)
    private String avatarUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
