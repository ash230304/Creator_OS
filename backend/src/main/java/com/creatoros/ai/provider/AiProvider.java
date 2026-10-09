package com.creatoros.ai.provider;

/**
 * AiProvider — the strategy interface for all LLM interactions.
 *
 * ── Why an interface? (Strategy Pattern) ─────────────────────────────────────
 * We want to swap between OpenAI GPT-4o-mini and Groq's Llama without
 * changing any business logic. The service only calls:
 *
 *   String json = aiProvider.chat(systemPrompt, userPrompt);
 *
 * Which concrete provider runs is determined at startup by the
 * 'ai.provider' config value. This is the Strategy Pattern.
 *
 * Benefits:
 *   - Switch from OpenAI to Groq (10x cheaper, nearly as good) by changing one env var
 *   - Test with a mock provider without real API calls
 *   - Add Anthropic/Gemini later without touching ScriptService
 *
 * ── OpenAI API format (shared by Groq) ───────────────────────────────────────
 * Both OpenAI and Groq use the "chat completions" endpoint:
 *   POST https://api.openai.com/v1/chat/completions
 *   POST https://api.groq.com/openai/v1/chat/completions
 *
 * Request body:
 * {
 *   "model": "gpt-4o-mini",
 *   "messages": [
 *     {"role": "system", "content": "You are a script writer..."},
 *     {"role": "user",   "content": "Write a script about..."}
 *   ],
 *   "temperature": 0.7,
 *   "response_format": {"type": "json_object"}  ← forces JSON output
 * }
 *
 * The 'system' message = instructions/persona for the AI.
 * The 'user' message = the actual request.
 * Separating them is best practice — system stays constant, user changes per request.
 */
public interface AiProvider {

    /**
     * Send a chat request to the LLM and return the response text.
     *
     * @param systemPrompt Instructions for the AI (role, format, constraints)
     * @param userPrompt   The actual generation request (idea, platform, tone)
     * @return The AI's response — expected to be valid JSON for script generation
     * @throws RuntimeException if the API call fails or returns an error
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * Human-readable provider name — used in logs.
     * e.g. "OpenAI (gpt-4o-mini)" or "Groq (llama-3.1-8b-instant)"
     */
    String getProviderName();
}
