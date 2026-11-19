package com.creatoros.transcription.service;

import com.creatoros.common.exception.BusinessException;
import com.creatoros.common.exception.ResourceNotFoundException;
import com.creatoros.common.security.SecurityUtils;
import com.creatoros.transcription.dto.TranscriptResponse;
import com.creatoros.transcription.entity.VideoTranscript;
import com.creatoros.transcription.repository.VideoTranscriptRepository;
import com.creatoros.video.entity.Video;
import com.creatoros.video.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * TranscriptionService — orchestrates audio extraction and speech-to-text.
 *
 * ── Full flow ────────────────────────────────────────────────────────────────
 *
 *   Video file (MP4)
 *       │
 *       ▼  (1) FFmpeg extracts audio
 *   WAV file (temp dir)
 *       │
 *       ▼  (2) WhisperProvider sends WAV to API
 *   TranscriptionResult { fullText, segments, language }
 *       │
 *       ▼  (3) Segments serialized to JSON string
 *   VideoTranscript entity → video_transcripts table
 *       │
 *       ▼  (4) Temp WAV file deleted
 *   Done
 *
 * ── Why extract audio first? ─────────────────────────────────────────────────
 * Whisper can accept video files directly (it extracts audio internally).
 * However:
 *   - Extracting audio first dramatically reduces upload size
 *   - WAV is the lossless format Whisper prefers for accuracy
 *   - Separating the steps gives us a clean retry point
 *
 * ── Why ProcessBuilder for FFmpeg? ───────────────────────────────────────────
 * FFmpeg is a system binary — we invoke it via ProcessBuilder.
 * ProcessBuilder is Java's way of running OS-level commands.
 *
 * The call is BLOCKING — this is intentional.
 * TranscriptionService is always called from a background @Async thread
 * (via ProcessingService.runPipeline()), so blocking is fine.
 *
 * ── FFmpeg command breakdown ──────────────────────────────────────────────────
 * ffmpeg -i input.mp4          ← input file
 *        -vn                   ← no video (audio only)
 *        -acodec pcm_s16le     ← 16-bit PCM WAV (optimal for speech recognition)
 *        -ar 16000             ← 16kHz sample rate (Whisper's preferred input)
 *        -ac 1                 ← mono channel (speech is mono — smaller file)
 *        output.wav            ← output file
 *
 * ── Temp file lifecycle ───────────────────────────────────────────────────────
 * WAV files can be large (10min video ≈ 100MB WAV).
 * We create temp files in the OS temp dir and ALWAYS delete them in a finally block.
 * Even if Whisper fails, we don't leave temp files behind.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final VideoRepository            videoRepository;
    private final VideoTranscriptRepository  transcriptRepository;
    private final WhisperProvider            whisperProvider;
    private final ObjectMapper               objectMapper;
    private final TranscriptPersistenceService transcriptPersistenceService;

    @Value("${storage.local.base-path:./uploads}")
    private String storagePath;

    // ── TRANSCRIBE (called from ProcessingService pipeline) ───────────────────

    /**
     * Extracts audio from the video, sends it to Whisper, and saves the result.
     * Called from ProcessingService.runPipeline() in a background thread.
     *
     * @param videoId  UUID of the video to transcribe
     * @return         The saved VideoTranscript entity (for pipeline to continue)
     * @throws RuntimeException if audio extraction or transcription fails
     */
    public VideoTranscript transcribeVideo(UUID videoId) {
        // 1. Load video
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));

        log.info("Starting transcription for video {} ({})", videoId, video.getOriginalFilename());

        // 2. Locate the video file on disk
        Path videoFilePath = resolveVideoPath(video.getStoragePath());
        if (!videoFilePath.toFile().exists()) {
            throw new BusinessException("Video file not found on disk: " + videoFilePath);
        }

        // 3. Extract audio to a temp WAV file
        File wavFile = null;
        try {
            wavFile = extractAudioToWav(videoFilePath.toFile());

            // 4. Send WAV to Whisper
            WhisperProvider.TranscriptionResult result = whisperProvider.transcribe(wavFile);

            // 5. Serialize segments list to JSON string for JSONB storage
            String segmentsJson = serializeSegments(result.segments());

            // 6. Persist in a short transaction, after FFmpeg and Whisper have finished.
            VideoTranscript saved = transcriptPersistenceService.save(
                    videoId, result.fullText(), segmentsJson, result.language(), result.modelName());
            log.info("Transcript saved for video {} — {} chars, {} segments",
                    videoId, result.fullText().length(), result.segments().size());

            return saved;

        } finally {
            // Always clean up temp WAV — even on failure
            if (wavFile != null && wavFile.exists()) {
                boolean deleted = wavFile.delete();
                if (!deleted) {
                    log.warn("Failed to delete temp WAV file: {}", wavFile.getAbsolutePath());
                }
            }
        }
    }

    // ── GET TRANSCRIPT (public API endpoint) ─────────────────────────────────

    /**
     * Returns the transcript for a video.
     * Ownership-scoped: only the video's owner can fetch the transcript.
     *
     * @param videoId  The video to fetch transcript for
     * @return         TranscriptResponse DTO
     * @throws ResourceNotFoundException if video not found or not yet transcribed
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public TranscriptResponse getTranscript(UUID videoId) {
        UUID userId = SecurityUtils.getCurrentUserId();

        VideoTranscript transcript = transcriptRepository
                .findByVideoIdAndVideoProjectUserId(videoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transcript not found for video: " + videoId +
                        " (video may not exist, or transcription may not have completed yet)"));

        return TranscriptResponse.from(transcript, objectMapper);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts audio from the video file into a temporary WAV file.
     * Uses FFmpeg via ProcessBuilder — blocks until extraction completes.
     *
     * FFmpeg command:
     *   ffmpeg -i <input> -vn -acodec pcm_s16le -ar 16000 -ac 1 <output.wav>
     *
     * Exit code 0 = success, anything else = failure (stderr logged).
     */
    private File extractAudioToWav(File videoFile) {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        File wavFile = tempDir.resolve("creatoros_audio_" + UUID.randomUUID() + ".wav").toFile();

        List<String> command = List.of(
                "ffmpeg",
                "-i",           videoFile.getAbsolutePath(),
                "-vn",                      // no video stream
                "-acodec",      "pcm_s16le", // 16-bit signed little-endian PCM
                "-ar",          "16000",     // 16kHz — Whisper's sweet spot
                "-ac",          "1",         // mono
                "-y",                        // overwrite output if exists
                wavFile.getAbsolutePath()
        );

        log.debug("Running FFmpeg: {}", String.join(" ", command));

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)  // merge stdout + stderr
                    .start();

            // Read output — required to prevent the process from blocking
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("FFmpeg audio extraction failed (exit {}): {}", exitCode, output);
                throw new RuntimeException("FFmpeg audio extraction failed. Exit code: " + exitCode);
            }

            log.info("Audio extracted to {} ({} bytes)", wavFile.getName(), wavFile.length());
            return wavFile;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to run FFmpeg: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the full path to a video file.
     * storage_path in DB is stored as a relative path from the base storage dir,
     * or an absolute path if configured so. We try absolute first, then relative.
     */
    private Path resolveVideoPath(String storagePath) {
        Path absolute = Path.of(storagePath);
        if (absolute.isAbsolute()) return absolute;

        // Older uploads are stored relative to the process working directory
        // (for example uploads/videos/...), while newer deployments may keep
        // paths relative to the configured storage root. Prefer an existing path.
        Path workingDirectoryPath = absolute.normalize();
        if (Files.exists(workingDirectoryPath)) return workingDirectoryPath;

        // Relative path — resolve against configured storage root
        return Path.of(this.storagePath).resolve(storagePath).normalize();
    }

    private String serializeSegments(Object segments) {
        try {
            return objectMapper.writeValueAsString(segments);
        } catch (Exception e) {
            log.warn("Failed to serialize segments, storing empty array: {}", e.getMessage());
            return "[]";
        }
    }
}
