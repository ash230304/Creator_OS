package com.creatoros.transcription.service;

import com.creatoros.transcription.dto.TranscriptSegment;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * OpenAiWhisperProvider — calls the OpenAI Whisper transcription API.
 *
 * ── API used ─────────────────────────────────────────────────────────────────
 * POST https://api.openai.com/v1/audio/transcriptions
 * Content-Type: multipart/form-data
 *
 * Request fields:
 *   file       → the audio file (WAV/MP3/MP4/M4A — Whisper handles all)
 *   model      → "whisper-1"
 *   response_format → "verbose_json" (includes segments with timestamps)
 *   language   → "en" (optional — speeds up detection if known)
 *
 * Response (verbose_json):
 * {
 *   "text": "Full transcript...",
 *   "language": "english",
 *   "segments": [
 *     {
 *       "id": 0, "start": 0.0, "end": 4.2,
 *       "text": " Most students fail because..."
 *     }
 *   ]
 * }
 *
 * ── Multipart upload (not JSON) ───────────────────────────────────────────────
 * Unlike the chat completions endpoint (JSON body), Whisper requires
 * multipart/form-data because we're uploading a binary file.
 *
 * Spring's MultiValueMap + FileSystemResource handle this:
 *   - FileSystemResource wraps a File for Spring's HTTP client
 *   - LinkedMultiValueMap is Spring's multipart body builder
 *
 * ── @ConditionalOnProperty ────────────────────────────────────────────────────
 * This bean is only registered when whisper.provider=openai (the default).
 * If whisper.provider=local, this bean is skipped and LocalWhisperProvider
 * is used instead. Spring picks the right implementation automatically.
 *
 * ── Cost consideration ─────────────────────────────────────────────────────────
 * OpenAI Whisper costs $0.006/minute. A 10-minute video = $0.06.
 * For V1 with a handful of videos, this is negligible.
 * The local provider option allows switching to free local inference later.
 */
@Slf4j
@Component("openAiWhisperProvider")
@ConditionalOnProperty(name = "whisper.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiWhisperProvider implements WhisperProvider {

    private static final String WHISPER_API_URL = "https://api.openai.com/v1";

    private final RestClient   restClient;
    private final ObjectMapper objectMapper;
    private final String       model;

    public OpenAiWhisperProvider(
            @Value("${whisper.openai.api-key:}") String apiKey,
            @Value("${whisper.openai.model:whisper-1}") String model,
            ObjectMapper objectMapper
    ) {
        this.model        = model;
        this.objectMapper = objectMapper;

        this.restClient = RestClient.builder()
                .baseUrl(WHISPER_API_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        log.info("Whisper provider initialised: OpenAI ({})", model);
    }

    @Override
    public TranscriptionResult transcribe(File audioFile) {
        log.info("Sending {} ({} bytes) to OpenAI Whisper ({})",
                audioFile.getName(), audioFile.length(), model);

        // ── Build multipart form-data body ────────────────────────────────────
        // multipart/form-data is how browsers upload files; we replicate it here.
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file",            new FileSystemResource(audioFile));
        body.add("model",           model);
        body.add("response_format", "verbose_json");  // includes timestamps
        body.add("timestamp_granularities[]", "segment"); // segment-level timestamps

        // ── Make the API call ─────────────────────────────────────────────────
        String rawResponse = restClient.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);

        // ── Parse the response ────────────────────────────────────────────────
        return parseResponse(rawResponse);
    }

    private TranscriptionResult parseResponse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);

            String fullText = root.path("text").asText("").trim();
            String language = normaliseLanguage(root.path("language").asText("en"));

            // Parse segments array — Whisper returns [{id, start, end, text}, ...]
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

            log.info("Whisper transcription complete: {} chars, {} segments, lang={}",
                    fullText.length(), segments.size(), language);

            return new TranscriptionResult(fullText, segments, language, model);

        } catch (Exception e) {
            log.error("Failed to parse Whisper response: {}", rawJson, e);
            throw new RuntimeException("Failed to parse Whisper API response: " + e.getMessage(), e);
        }
    }

    /**
     * Whisper returns full language names ("english") but we store ISO codes ("en").
     * Only common languages are mapped — unknown strings are passed through as-is.
     */
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
        return "OpenAI Whisper (" + model + ")";
    }
}
