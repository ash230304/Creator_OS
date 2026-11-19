package com.creatoros.clipping.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * FFmpegService — wraps all FFmpeg ProcessBuilder calls for clip generation.
 *
 * ── What this class does ──────────────────────────────────────────────────────
 * 1. cutClip()     — cuts a clip from a video using -ss and -to flags
 * 2. burnCaptions() — overlays an SRT subtitle file onto a clip
 * 3. buildSrt()    — generates an SRT subtitle string from segment text
 *
 * ── Why ProcessBuilder? ───────────────────────────────────────────────────────
 * FFmpeg is a C binary — not a Java library. We invoke it as a system process.
 * ProcessBuilder is the standard Java way to run OS commands.
 *
 * Key ProcessBuilder patterns used:
 *   .redirectErrorStream(true)  — merge stderr into stdout so we can read all output
 *   .start()                    — launch the process (non-blocking)
 *   process.getInputStream().readAllBytes() — read all output (REQUIRED to prevent blocking)
 *   process.waitFor()           — wait for the process to finish (blocking)
 *   exitCode == 0               — success (Unix convention: 0 = OK, non-zero = error)
 *
 * ── FFmpeg commands used ──────────────────────────────────────────────────────
 *
 * 1. Cut a clip (stream copy — fast, input seek for large files):
 *    ffmpeg -ss 42.5 -i input.mp4 -to 24.75 -c copy clip.mp4
 *    Note: -ss BEFORE -i = input seek (fast, keyframe-accurate).
 *    -to is then relative to the seek start, not absolute video time.
 *    For frame-perfect cuts: use -c:v libx264 instead (much slower).
 *
 * 2. Burn subtitles (requires re-encoding the video track):
 *    ffmpeg -i clip.mp4 -vf "subtitles=captions.srt" -c:a copy clip_captioned.mp4
 *    -vf is the video filter: "subtitles=path" burns text into each frame.
 *    -c:a copy keeps audio untouched (only video is re-encoded).
 *
 * ── SRT format ────────────────────────────────────────────────────────────────
 * SRT is the simplest subtitle format:
 *
 *   1
 *   00:00:00,000 --> 00:00:04,200
 *   Most students are preparing wrong.
 *
 *   2
 *   00:00:04,200 --> 00:00:09,800
 *   Here's what actually works.
 *
 * Timestamps are relative to the clip start (not the original video).
 * So if the clip starts at 42.5s in the video, its SRT starts at 00:00:00,000.
 *
 * ── Output file structure ─────────────────────────────────────────────────────
 * uploads/
 *   videos/     ← original uploaded videos
 *   clips/      ← raw FFmpeg cuts
 *   captioned/  ← clips with burned-in captions
 */
@Slf4j
@Service
public class FFmpegService {

    @Value("${storage.local.base-path:./uploads}")
    private String storagePath;

    // ── CUT CLIP ──────────────────────────────────────────────────────────────

    /**
     * Cuts a segment from a video file using FFmpeg stream copy.
     *
     * @param videoFile  The source video file
     * @param startSec   Start timestamp in seconds (e.g. 42.5)
     * @param endSec     End timestamp in seconds (e.g. 67.25)
     * @param clipId     UUID of the clip (used to name the output file)
     * @return           The output clip File
     */
    public File cutClip(File videoFile, double startSec, double endSec, UUID clipId) {
        Path clipsDir  = ensureDir(storagePath + "/clips");
        File outputFile = clipsDir.resolve(clipId + ".mp4").toFile();

        List<String> command = List.of(
                "ffmpeg",
                "-ss", String.valueOf(startSec),          // input seek — fast, no decoding before start
                "-i",  videoFile.getAbsolutePath(),
                "-to", String.valueOf(endSec - startSec), // duration from seek point (not absolute time)
                "-c",  "copy",                            // stream copy — fast
                "-y",                                     // overwrite if exists
                outputFile.getAbsolutePath()
        );

        log.debug("Cutting clip {}: {}s to {}s from {}",
                clipId, startSec, endSec, videoFile.getName());

        runFfmpeg(command, "cut clip " + clipId);

        log.info("Clip {} cut successfully ({} bytes)", clipId, outputFile.length());
        return outputFile;
    }

    // ── BURN CAPTIONS ─────────────────────────────────────────────────────────

    /**
     * Burns an SRT subtitle file into a clip file using the FFmpeg subtitles filter.
     *
     * @param clipFile   The input clip file (raw, no captions)
     * @param srtContent The SRT subtitle string (relative timestamps from clip start)
     * @param clipId     UUID of the clip (used to name the output file)
     * @return           The captioned output File
     */
    public File burnCaptions(File clipFile, String srtContent, UUID clipId) {
        Path captionedDir = ensureDir(storagePath + "/captioned");

        // Write SRT to a temp file — FFmpeg reads subtitle from disk, not stdin
        File srtFile = writeTempSrt(srtContent, clipId);

        File outputFile = captionedDir.resolve(clipId + "_captioned.mp4").toFile();

        // Escape the SRT path for FFmpeg's filter syntax (colons need escaping on Windows)
        String srtPath = srtFile.getAbsolutePath().replace("\\", "/").replace(":", "\\:");

        List<String> command = List.of(
                "ffmpeg",
                "-i",    clipFile.getAbsolutePath(),
                "-vf",   "subtitles=" + srtPath,          // burn subtitles into video
                "-c:a",  "copy",                           // keep audio untouched
                "-y",                                      // overwrite if exists
                outputFile.getAbsolutePath()
        );

        log.debug("Burning captions for clip {} from {}", clipId, srtFile.getName());

        try {
            runFfmpeg(command, "burn captions " + clipId);
            log.info("Captions burned for clip {} ({} bytes)", clipId, outputFile.length());
            return outputFile;
        } finally {
            // Clean up temp SRT file
            if (srtFile.exists()) srtFile.delete();
        }
    }

    // ── SRT GENERATION ────────────────────────────────────────────────────────

    /**
     * Generates an SRT subtitle string from a list of segments.
     *
     * Timestamps are made RELATIVE to clipStartTime so they start from 00:00:00,000.
     * Whisper gives us absolute video timestamps; SRT needs clip-relative ones.
     *
     * @param segments      Whisper segments spanning the clip
     * @param clipStartTime Start time of the clip in the original video (seconds)
     * @return              SRT-formatted string
     */
    public String buildSrt(List<SrtSegment> segments, double clipStartTime) {
        StringBuilder srt = new StringBuilder();
        int index = 1;

        for (SrtSegment seg : segments) {
            // Convert absolute → relative timestamps
            double relStart = Math.max(0, seg.startTime() - clipStartTime);
            double relEnd   = Math.max(relStart + 0.1, seg.endTime() - clipStartTime);

            srt.append(index).append("\n");
            srt.append(formatSrtTime(relStart))
               .append(" --> ")
               .append(formatSrtTime(relEnd))
               .append("\n");
            srt.append(seg.text().trim()).append("\n\n");

            index++;
        }

        return srt.toString();
    }

    /**
     * Minimal segment type for SRT generation.
     * Keeps this service independent of Whisper-specific DTOs.
     */
    public record SrtSegment(double startTime, double endTime, String text) {}

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Runs an FFmpeg command and waits for completion.
     * Throws RuntimeException if FFmpeg exits with a non-zero code.
     */
    private void runFfmpeg(List<String> command, String description) {
        log.debug("FFmpeg: {}", String.join(" ", command));
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            // Must read all output — otherwise the process can block waiting for buffer to drain
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("FFmpeg failed [{}] (exit {}): {}", description, exitCode, output);
                throw new RuntimeException(
                        "FFmpeg failed (" + description + "). Exit code: " + exitCode);
            }

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to run FFmpeg [" + description + "]: " + e.getMessage(), e);
        }
    }

    /** Creates a directory if it doesn't exist, returns its Path. */
    private Path ensureDir(String dirPath) {
        Path dir = Path.of(dirPath);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create output directory: " + dirPath, e);
        }
        return dir;
    }

    /** Writes SRT content to a temporary file for FFmpeg to read. */
    private File writeTempSrt(String srtContent, UUID clipId) {
        try {
            Path srtPath = Path.of(System.getProperty("java.io.tmpdir"))
                    .resolve("creatoros_captions_" + clipId + ".srt");
            Files.writeString(srtPath, srtContent);
            return srtPath.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write SRT file: " + e.getMessage(), e);
        }
    }

    /**
     * Formats a time in seconds to SRT timestamp format: HH:MM:SS,mmm
     *
     * Example: 67.25 → "00:01:07,250"
     */
    String formatSrtTime(double seconds) {
        int totalMs  = (int) Math.round(seconds * 1000);
        int ms       = totalMs % 1000;
        int totalSec = totalMs / 1000;
        int sec      = totalSec % 60;
        int totalMin = totalSec / 60;
        int min      = totalMin % 60;
        int hours    = totalMin / 60;

        return String.format("%02d:%02d:%02d,%03d", hours, min, sec, ms);
    }
}
