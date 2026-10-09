package com.creatoros.script.service;

import com.creatoros.ai.provider.AiProvider;
import com.creatoros.common.exception.ResourceNotFoundException;
import com.creatoros.common.security.SecurityUtils;
import com.creatoros.project.entity.Project;
import com.creatoros.project.repository.ProjectRepository;
import com.creatoros.script.dto.GenerateScriptRequest;
import com.creatoros.script.dto.ScriptContent;
import com.creatoros.script.dto.ScriptResponse;
import com.creatoros.script.entity.Script;
import com.creatoros.script.repository.ScriptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ScriptService — orchestrates AI script generation and persistence.
 *
 * ── Full flow ────────────────────────────────────────────────────────────────
 * 1. Validate: does the project exist AND belong to the current user?
 * 2. Build a detailed system prompt (role, format, constraints)
 * 3. Build a user prompt (idea + platform + tone + duration)
 * 4. Call AiProvider.chat() → raw JSON string from the LLM
 * 5. Parse JSON → ScriptContent record (validates the structure)
 * 6. Re-serialize to clean JSON string → save to DB (JSONB column)
 * 7. Update project status → SCRIPTED
 * 8. Return ScriptResponse (with parsed ScriptContent, not raw JSON)
 *
 * ── Prompt Engineering ───────────────────────────────────────────────────────
 * "Prompt engineering" = writing AI instructions carefully to get consistent output.
 * Key techniques used here:
 *   - Role definition ("You are an expert short-form video script writer")
 *   - Output format specification (exact JSON schema)
 *   - Constraint injection (platform, tone, duration limits)
 *   - Few-shot examples aren't used here but could be added for V2
 *
 * ── Why ObjectMapper (Jackson)? ──────────────────────────────────────────────
 * Jackson is Spring's default JSON library. We use it to:
 *   - Deserialize the AI's JSON string → ScriptContent (validates the structure)
 *   - Re-serialize ScriptContent → clean JSON string → store in DB
 *   Both operations in one service because the entity stores String, not ScriptContent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptRepository  scriptRepository;
    private final ProjectRepository projectRepository;
    private final AiProvider        aiProvider;
    private final ObjectMapper      objectMapper;   // Spring auto-configures Jackson ObjectMapper

    // ── GENERATE ─────────────────────────────────────────────────────────────

    @Transactional
    public ScriptResponse generate(GenerateScriptRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();

        // 1. Verify project ownership
        Project project = projectRepository
                .findByIdAndUserId(request.projectId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + request.projectId()));

        // 2. Resolve defaults
        Project.Tone tone     = request.tone() != null ? request.tone() : Project.Tone.NEUTRAL;
        int          duration = request.targetDurationSeconds() != null
                                    ? request.targetDurationSeconds() : 60;

        log.info("Generating script via {} for project {} | platform={} tone={} duration={}s",
                aiProvider.getProviderName(), project.getId(),
                request.platform(), tone, duration);

        // 3. Build prompts
        String systemPrompt = buildSystemPrompt(request.platform(), tone, duration);
        String userPrompt   = buildUserPrompt(request.idea(), request.platform(), tone, duration);

        // 4. Call AI
        String rawJson = aiProvider.chat(systemPrompt, userPrompt);

        // 5. Parse + validate structure
        ScriptContent content = parseScriptContent(rawJson);

        // 6. Re-serialize to clean JSON for DB storage
        String contentJson = serializeToJson(content);

        // 7. Persist script
        Script script = Script.builder()
                .project(project)
                .idea(request.idea())
                .platform(request.platform().name())
                .tone(tone.name())
                .targetDurationSeconds(duration)
                .content(contentJson)
                .build();

        Script saved = scriptRepository.saveAndFlush(script);

        // 8. Update project status
        project.updateStatus(Project.Status.SCRIPTED);
        // Dirty-checking will flush the project update on transaction commit

        log.info("Script {} saved for project {}", saved.getId(), project.getId());
        return ScriptResponse.from(saved, content);
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScriptResponse> listForProject(UUID projectId) {
        UUID userId = SecurityUtils.getCurrentUserId();

        // Verify ownership
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        return scriptRepository
                .findAllByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(s -> ScriptResponse.from(s, parseScriptContent(s.getContent())))
                .toList();
    }

    // ── GET BY ID ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ScriptResponse getById(UUID scriptId) {
        UUID userId = SecurityUtils.getCurrentUserId();

        // Ownership-scoped lookup: traverses script → project → user
        // Returns 404 if script doesn't exist OR belongs to another user's project
        Script script = scriptRepository.findByIdAndProjectUserId(scriptId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + scriptId));

        return ScriptResponse.from(script, parseScriptContent(script.getContent()));
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    /**
     * System prompt — tells the AI its role, output format, and constraints.
     * This stays fixed for all script generation requests.
     */
    private String buildSystemPrompt(Project.Platform platform,
                                     Project.Tone tone,
                                     int durationSeconds) {
        return """
                You are an expert short-form video script writer specialising in viral %s content.
                
                Your scripts follow this proven formula:
                - HOOK (first 3 seconds): grab attention immediately — must be irresistible
                - BODY sections: each section delivers one clear idea with visual and editing guidance
                - CTA (last 5 seconds): clear call to action
                
                Tone: %s
                Total video duration: %d seconds
                
                CRITICAL: You MUST respond with ONLY valid JSON in exactly this format:
                {
                  "hook": "the opening hook line",
                  "sections": [
                    {
                      "start": 0,
                      "end": 15,
                      "dialogue": "exact words spoken",
                      "visual": "what the camera/screen shows",
                      "editing_instruction": "how to edit this section"
                    }
                  ],
                  "cta": "the closing call to action"
                }
                
                Rules:
                - Dialogue must be written for speech, not reading
                - Make every second count — no filler
                - Sections must cover 0 to %d seconds total
                - Hook must be a single punchy sentence (max 15 words)
                - Do NOT include any text outside the JSON object
                """.formatted(
                        platformLabel(platform), toneLabel(tone),
                        durationSeconds, durationSeconds);
    }

    /**
     * User prompt — carries the specific idea for this generation request.
     */
    private String buildUserPrompt(String idea, Project.Platform platform,
                                   Project.Tone tone, int durationSeconds) {
        return """
                Create a %s-second %s script about the following idea:
                
                IDEA: %s
                
                Remember: %s tone. Return only the JSON object.
                """.formatted(durationSeconds, platformLabel(platform), idea, toneLabel(tone));
    }

    private String platformLabel(Project.Platform platform) {
        return switch (platform) {
            case INSTAGRAM_REEL -> "Instagram Reel";
            case YOUTUBE_SHORT  -> "YouTube Short";
            case TIKTOK         -> "TikTok";
            case GENERAL        -> "short-form video";
        };
    }

    private String toneLabel(Project.Tone tone) {
        return switch (tone) {
            case DIRECT       -> "direct and no-nonsense";
            case EDUCATIONAL  -> "educational and step-by-step";
            case ENTERTAINING -> "fun and high-energy";
            case STORYTELLING -> "narrative and personal storytelling";
            case NEUTRAL      -> "balanced and conversational";
        };
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────

    private ScriptContent parseScriptContent(String json) {
        try {
            return objectMapper.readValue(json, ScriptContent.class);
        } catch (Exception e) {
            log.error("Failed to parse script JSON: {}", json, e);
            throw new RuntimeException("AI returned invalid script format: " + e.getMessage());
        }
    }

    private String serializeToJson(ScriptContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize script content", e);
        }
    }
}
