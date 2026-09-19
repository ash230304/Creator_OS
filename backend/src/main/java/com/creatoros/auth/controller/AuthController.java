package com.creatoros.auth.controller;

import com.creatoros.auth.dto.AuthResponse;
import com.creatoros.auth.dto.LoginRequest;
import com.creatoros.auth.dto.RegisterRequest;
import com.creatoros.auth.service.AuthService;
import com.creatoros.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — two endpoints, no business logic.
 *
 * Controller rules enforced here:
 *   - @Valid triggers Bean Validation on the request body
 *     If @NotBlank or @Email fails → MethodArgumentNotValidException
 *     → GlobalExceptionHandler → HTTP 400 with field errors
 *   - Controller only handles HTTP in/out — delegates everything to AuthService
 *   - Returns ResponseEntity<ApiResponse<T>> for consistent response shape
 *
 * ── Endpoint 1: POST /api/v1/auth/register ───────────────────────────────────
 * Request body:
 *   { "name": "Ash", "email": "ash@example.com", "password": "secret123" }
 * Success (201 Created):
 *   { "success": true, "data": { "token": "eyJ...", "userId": "...", "name": "Ash", "email": "..." } }
 * Conflict (409):
 *   { "success": false, "message": "Email is already registered: ash@example.com" }
 * Validation error (400):
 *   { "success": false, "message": "Validation failed" }
 *
 * ── Endpoint 2: POST /api/v1/auth/login ─────────────────────────────────────
 * Request body:
 *   { "email": "ash@example.com", "password": "secret123" }
 * Success (200 OK):
 *   { "success": true, "data": { "token": "eyJ...", "userId": "...", "name": "Ash", "email": "..." } }
 * Bad credentials (401):
 *   { "success": false, "message": "Invalid email or password" }
 *
 * ── Why 201 for register but 200 for login? ───────────────────────────────────
 * HTTP 201 Created = a new resource was created (a new user was just made)
 * HTTP 200 OK      = request processed successfully, no new resource created
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity
                .ok(ApiResponse.ok("Login successful", response));
    }
}
