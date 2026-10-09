package com.creatoros.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAiProvider — calls the OpenAI Chat Completions API.
 * Also used for Groq by changing the base URL and API key.
 * Both use the identical OpenAI-compatible HTTP API format.
 *
 * ── Spring's RestClient (Spring 6.1+) ────────────────────────────────────────
 * RestClient is the modern replacement for RestTemplate.
 * It has a fluent builder API:
 *
 *   restClient.post()          → POST request
 *     .uri("/chat/completions") → URL path
 *     .header(...)              → add headers
 *     .body(payload)            → request body (serialized to JSON by Jackson)
 *     .retrieve()               → execute the request
 *     .body(String.class)       → deserialize response as String
 *
 * Why not WebClient (reactive)?
 *   WebClient requires Project Reactor on the classpath and reactive thinking.
 *   For a straightforward blocking API call, RestClient is simpler and perfectly adequate.
 *   V2 can migrate to WebClient if we need non-blocking AI calls.
 *
 * ── Response format: JSON mode ───────────────────────────────────────────────
 * We ask the AI to respond in JSON by:
 *   1. Adding "response_format": {"type": "json_object"} in the request
 *   2. Instructing the AI in the system prompt to respond with valid JSON only
 *
 * The response comes back as: choices[0].message.content = the JSON string
 *
 * ── Error handling strategy ───────────────────────────────────────────────────
 * If the API returns a 4xx/5xx, RestClient throws RestClientException.
 * We let it propagate — GlobalExceptionHandler catches unknown exceptions → 500.
 * Future improvement: catch and wrap in a specific AiProviderException → 503.
 */
@Slf4j
@Component("openAiProvider")
public class OpenAiProvider implements AiProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String providerName;

    public OpenAiProvider(
            @Value("${ai.openai.api-key:}") String openAiKey,
            @Value("${ai.groq.api-key:}")   String groqKey,
            @Value("${ai.provider:openai}") String provider,
            @Value("${ai.openai.model:gpt-4o-mini}") String openAiModel,
            @Value("${ai.groq.model:llama-3.1-8b-instant}") String groqModel,
            @Value("${ai.groq.base-url:https://api.groq.com/openai/v1}") String groqBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;

        // Choose provider based on config
        boolean useGroq = "groq".equalsIgnoreCase(provider);

        String apiKey  = useGroq ? groqKey : openAiKey;
        String baseUrl = useGroq ? groqBaseUrl : "https://api.openai.com/v1";
        this.model     = useGroq ? groqModel  : openAiModel;
        this.providerName = useGroq
                ? "Groq (" + groqModel + ")"
                : "OpenAI (" + openAiModel + ")";

        // Build the RestClient — baseUrl and auth header are fixed for all calls
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        log.info("AI provider initialised: {}", this.providerName);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        log.debug("Calling {} with model={}", providerName, model);

        // ── Build the request payload ─────────────────────────────────────────
        // Using Map<String, Object> is fine here — Jackson serializes it to JSON.
        // We could also use a dedicated request record, but for a single use case
        // a Map keeps the code concise.
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userPrompt)
                ),
                "temperature", 0.7,                          // creativity level: 0=deterministic, 1=very creative
                "max_tokens", 2048,                          // enough for a full script
                "response_format", Map.of("type", "json_object")  // force JSON output
        );

        // ── Make the API call ─────────────────────────────────────────────────
        String rawResponse = restClient.post()
                .uri("/chat/completions")
                .body(payload)
                .retrieve()
                .body(String.class);

        // ── Extract content from choices[0].message.content ───────────────────
        // The full response structure:
        // {
        //   "choices": [
        //     {
        //       "message": {
        //         "role": "assistant",
        //         "content": "{ the actual JSON script }"
        //       }
        //     }
        //   ]
        // }
        try {
            JsonNode root    = objectMapper.readTree(rawResponse);
            String   content = root.path("choices").get(0)
                                   .path("message")
                                   .path("content")
                                   .asText();
            log.debug("AI response received ({} chars)", content.length());
            return content;
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", rawResponse, e);
            throw new RuntimeException("Invalid response from AI provider: " + e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return providerName;
    }
}
