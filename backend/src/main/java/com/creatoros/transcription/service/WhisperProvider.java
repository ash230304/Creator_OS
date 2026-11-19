package com.creatoros.transcription.service;

import com.creatoros.transcription.dto.TranscriptSegment;

import java.io.File;
import java.util.List;

/**
 * WhisperProvider — the strategy interface for speech-to-text transcription.
 *
 * ── Why an interface? (Strategy Pattern) ─────────────────────────────────────
 * Mirrors the AiProvider pattern in the ai/ package.
 *
 * We can swap transcription backends by changing one env var:
 *   WHISPER_PROVIDER=openai  → calls the OpenAI Whisper REST API
 *   WHISPER_PROVIDER=local   → runs the local whisper binary via ProcessBuilder
 *
 * ScriptService → AiProvider (same pattern)
 * TranscriptionService → WhisperProvider (this interface)
 *
 * ── What this interface promises ──────────────────────────────────────────────
 * Any implementation must:
 *   1. Accept an audio File (WAV/MP3/MP4 — Whisper handles all formats)
 *   2. Return the detected language code (ISO 639-1: "en", "hi", etc.)
 *   3. Return the full transcript text
 *   4. Return a list of timestamped segments [{start, end, text}]
 *
 * These are returned as a single TranscriptionResult record to avoid
 * making multiple API calls for the same audio.
 */
public interface WhisperProvider {

    /**
     * Transcribe an audio file and return the full result.
     *
     * @param audioFile   Path to the audio file (WAV recommended for quality)
     * @return            TranscriptionResult containing text, segments, and language
     * @throws RuntimeException if the transcription fails
     */
    TranscriptionResult transcribe(File audioFile);

    /**
     * Human-readable name — used in logs and stored in model_used column.
     * e.g. "OpenAI Whisper (whisper-1)" or "Local Whisper (medium)"
     */
    String getProviderName();

    /**
     * The result of a Whisper transcription call.
     * A record bundles all outputs from a single API call together.
     */
    record TranscriptionResult(
            String fullText,
            List<TranscriptSegment> segments,
            String language,
            String modelName
    ) {}
}
