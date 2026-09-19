package com.creatoros.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO = Data Transfer Object.
 *
 * Why DTOs instead of using the Entity directly?
 *   If you accepted a User entity in your controller, a client could
 *   send ANY field including id, passwordHash, createdAt — a security hole.
 *   DTOs define exactly what the client is allowed to send.
 *
 * Java Record (Java 16+):
 *   A record is an immutable data carrier. The compiler generates:
 *     - private final fields
 *     - a constructor with all fields
 *     - getters (named same as field, no "get" prefix)
 *     - equals(), hashCode(), toString()
 *   Perfect for DTOs — they're read-only request objects.
 *
 * Validation annotations (@NotBlank, @Email, @Size):
 *   These come from jakarta.validation (Bean Validation API).
 *   They do NOTHING on their own — you must add @Valid on the
 *   controller method parameter to trigger validation.
 *   If validation fails → MethodArgumentNotValidException → our
 *   GlobalExceptionHandler returns HTTP 400 with field errors.
 *
 * @NotBlank vs @NotNull:
 *   @NotNull  — field must not be null (but "" passes)
 *   @NotBlank — field must not be null AND not empty/whitespace only
 *   Use @NotBlank for string fields.
 */
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password

) {}
