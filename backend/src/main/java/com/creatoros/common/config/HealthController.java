package com.creatoros.common.config;

import com.creatoros.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public health-check endpoint.
 * Used by Docker HEALTHCHECK, Railway / Render, and local smoke tests.
 *
 * GET /api/v1/health → 200 OK
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "creatoros-backend"
        )));
    }
}
