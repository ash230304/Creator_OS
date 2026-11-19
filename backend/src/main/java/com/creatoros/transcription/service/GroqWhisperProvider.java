package com.creatoros.transcription.service;

import com.creatoros.transcription.dto.TranscriptSegment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * GroqWhisperProvider — calls the Groq Whisper transcription API.
 *
 * ── API used ─────────────────────────────────────────────────────────────────
 * POST https://api.groq.com/openai/v1/audio/transcriptions
 * Content-Type: multipart/form-data
 *
 * Groq's Whisper endpoint is API-compatible with OpenAI Whisper (same schema),
 * so we use the same multipart request shape and verbose_json response format.
 *
 * Supported models:
 *   whisper-large-v3         ← highest accuracy
 *   whisper-large-v3-turbo   ← fast + accurate (recommended)
 *   distil-whisper-large-v3-en ← English-only, fastest
 *
 * ── Why Groq instead of OpenAI? ──────────────────────────────────────────────
 * Groq offers Whisper for FREE (within rate limits).
 * For development and testing this is ideal — no per-minute cost.
 *
 * ── Activation ───────────────────────────────────────────────────────────────
 * Set in application-local.yml:
 *   whisper:
 *     provider: groq
 *     groq:
 *       api-key: gsk_...
 *       model: whisper-large-v3-turbo
 */
@Slf4j
@Component("groqWhisperProvider")
@ConditionalOnProperty(name = "whisper.provider", havingValue = "groq")
public class GroqWhisperProvider implements WhisperProvider {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;
    private final String       model;

    public GroqWhisperProvider(
            @Value("${whisper.groq.api-key:}") String apiKey,
            @Value("${whisper.groq.model:whisper-large-v3-turbo}") String model,
            ObjectMapper objectMapper
    ) {
        this.model        = model;
        this.objectMapper = objectMapper;

        this.restClient = RestClient.builder()
                .baseUrl(GROQ_API_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        log.info("Whisper provider initialised: Groq ({})", model);
    }

    @Override
    public TranscriptionResult transcribe(File audioFile) {
        log.info("Sending {} ({} bytes) to Groq Whisper ({})",
                audioFile.getName(), audioFile.length(), model);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file",            new FileSystemResource(audioFile));
        body.add("model",           model);
        body.add("response_format", "verbose_json");
        body.add("timestamp_granularities[]", "segment");

        String rawResponse = restClient.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        return parseResponse(rawResponse);
    }

    private TranscriptionResult parseResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);

            String fullText = root.path("text").asText("").trim();
            String language = normaliseLanguage(root.path("language").asText("en"));

            List<TranscriptSegment> segments = new ArrayList<>();
            JsonNode segmentsNode = root.path("segments");
            if (segmentsNode.isArray()) {
                for (JsonNode seg : segmentsNode) {
                    segments.add(new TranscriptSegment(
                            seg.path("start").asDouble(0.0),
                            seg.path("end").asDouble(0.0),
                            seg.path("text").asText("").trim()
                    ));
                }
            }

            log.info("Groq Whisper transcription complete: {} chars, {} segments, lang={}",
                    fullText.length(), segments.size(), language);

            return new TranscriptionResult(fullText, segments, language, model);

        } catch (Exception e) {
            log.error("Failed to parse Groq Whisper response: {}", rawJson, e);
            throw new RuntimeException("Failed to parse Groq Whisper API response: " + e.getMessage(), e);
        }
    }

    private String normaliseLanguage(String whisperLanguage) {
        return switch (whisperLanguage.toLowerCase()) {
            case "english"    -> "en";
            case "hindi"      -> "hi";
            case "spanish"    -> "es";
            case "french"     -> "fr";
            case "german"     -> "de";
            case "portuguese" -> "pt";
            case "japanese"   -> "ja";
            case "chinese"    -> "zh";
            case "arabic"     -> "ar";
            default           -> whisperLanguage.length() > 10
                                     ? whisperLanguage.substring(0, 2)
                                     : whisperLanguage;
        };
    }

    @Override
    public String getProviderName() {
        return "Groq Whisper (" + model + ")";
    }
}
