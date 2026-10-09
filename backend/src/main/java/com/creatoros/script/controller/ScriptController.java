package com.creatoros.script.controller;

import com.creatoros.common.response.ApiResponse;
import com.creatoros.script.dto.GenerateScriptRequest;
import com.creatoros.script.dto.ScriptResponse;
import com.creatoros.script.service.ScriptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ScriptController — REST endpoints for AI script generation.
 *
 * All endpoints require a valid JWT (configured in SecurityConfig).
 * Ownership is enforced inside ScriptService via SecurityUtils + project ownership check.
 *
 * Endpoint summary:
 *   POST /api/v1/scripts/generate              → generate a new AI script       (201)
 *   GET  /api/v1/scripts/project/{projectId}   → list all scripts for a project (200)
 *   GET  /api/v1/scripts/{id}                  → get one script by ID           (200)
 *
 * ── Why 201 for generate? ────────────────────────────────────────────────────
 * POST /generate creates a new Script resource in the DB.
 * HTTP 201 Created is semantically correct here — a new resource was persisted.
 *
 * ── Why no PUT/DELETE? ───────────────────────────────────────────────────────
 * Scripts are immutable after generation. If the creator wants a different script,
 * they generate a new one. Old scripts are kept for reference (version history).
 * This is also simpler — fewer endpoints = fewer bugs.
 */
@RestController
@RequestMapping("/api/v1/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    /**
     * POST /api/v1/scripts/generate
     *
     * Takes a content idea + platform + tone + duration.
     * Calls the LLM via AiProvider, persists the structured result.
     *
     * Request body: GenerateScriptRequest (JSON)
     * Response: ScriptResponse with hook, sections[], cta (201 Created)
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ScriptResponse>> generate(
            @Valid @RequestBody GenerateScriptRequest request) {

        ScriptResponse script = scriptService.generate(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Script generated successfully", script));
    }

    /**
     * GET /api/v1/scripts/project/{projectId}
     *
     * Lists all scripts generated for a given project.
     * Ordered newest-first (DESC by created_at).
     * Returns 404 if the project doesn't exist or belongs to someone else.
     */
    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<ScriptResponse>>> listForProject(
            @PathVariable UUID projectId) {

        List<ScriptResponse> scripts = scriptService.listForProject(projectId);
        return ResponseEntity.ok(ApiResponse.ok(scripts));
    }

    /**
     * GET /api/v1/scripts/{id}
     *
     * Fetch a single script by its UUID.
     * Returns 404 if not found or if it belongs to another user's project.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ScriptResponse>> getById(
            @PathVariable UUID id) {

        ScriptResponse script = scriptService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(script));
    }
}
