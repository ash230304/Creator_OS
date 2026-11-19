package com.creatoros.clipping.service;

import com.creatoros.transcription.dto.TranscriptSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ClipDetectionService — scores transcript segments for highlight potential.
 *
 * ── What this does ────────────────────────────────────────────────────────────
 * Takes a list of Whisper segments [{start, end, text}] and assigns each
 * segment a score between 0.0 and 1.0 indicating how highlight-worthy it is.
 *
 * High-scoring segments become clips. Low-scoring segments are discarded.
 *
 * ── Detection strategy (V1 — heuristic) ──────────────────────────────────────
 * We use a multi-factor heuristic scoring model. No LLM calls — fast and free.
 *
 * Factors scored:
 *
 *   1. ENERGY (speech rate):
 *      Short sentences spoken quickly = high energy = likely good clip material.
 *      Measured as: words / duration_seconds.
 *      Target: 2.5–4.5 words/sec for natural speech.
 *
 *   2. KEYWORD DENSITY:
 *      Presence of high-impact words lifts the score.
 *      Two tiers:
 *        - HOOK keywords: "secret", "never", "always", "truth", "wrong", "mistake"
 *          These are strong hook signals → +0.25 per match (capped at 0.5)
 *        - ACTION keywords: "here's", "this is", "remember", "key", "biggest",
 *          "you need", "why", "how to"
 *          Weaker signal → +0.1 per match (capped at 0.3)
 *
 *   3. QUESTION SIGNAL:
 *      Segments ending with "?" are likely engaging / interactive.
 *      → +0.15 bonus
 *
 *   4. SENTENCE COMPLETENESS:
 *      Complete sentences (ending in . ? !) score slightly higher than mid-sentence cuts.
 *      → +0.1 bonus for complete sentence
 *
 *   5. DURATION SWEET SPOT:
 *      Clips that are 5–20 seconds are ideal for short-form content.
 *      Too short (< 3s): not enough content.
 *      Too long (> 30s): too slow for short-form.
 *      → bonus curve peaks at 10–15 seconds.
 *
 * Final score = weighted sum of all factors, clamped to [0.0, 1.0].
 *
 * ── V2 improvement ────────────────────────────────────────────────────────────
 * Replace heuristic with an LLM call:
 *   prompt: "Score this transcript segment 0.0-1.0 for viral highlight potential: {text}"
 * The interface is already designed for this — just inject AiProvider here.
 *
 * ── Merging segments ─────────────────────────────────────────────────────────
 * Whisper produces many short segments (1–3 seconds each).
 * A single "clip" should be a contiguous block of 5–30 seconds.
 *
 * Algorithm:
 * 1. Score every individual Whisper segment
 * 2. Group consecutive segments where avg score > threshold (0.35)
 * 3. Each group becomes one clip candidate
 * 4. Apply min/max duration guards
 * 5. Rank candidates by score descending
 * 6. Return top N (default: 5 clips)
 */
@Slf4j
@Service
public class ClipDetectionService {

    // ── Tunable constants ─────────────────────────────────────────────────────

    /** Minimum score for a segment to contribute to a clip group. */
    private static final double SCORE_THRESHOLD   = 0.30;

    /** Minimum clip duration in seconds — clips shorter than this are dropped. */
    private static final double MIN_CLIP_DURATION = 4.0;

    /** Maximum clip duration in seconds — merge stops when this is exceeded. */
    private static final double MAX_CLIP_DURATION = 60.0;

    /** Maximum number of clips to generate from a single video. */
    private static final int    MAX_CLIPS         = 5;

    // Hook-level keywords — strongest highlight signal
    private static final List<String> HOOK_KEYWORDS = List.of(
            "secret", "never", "always", "truth", "wrong", "mistake", "actually",
            "nobody", "everybody", "exactly", "critical", "dangerous", "shocking",
            "biggest", "worst", "best", "number one", "stop", "don't"
    );

    // Action-level keywords — moderate highlight signal
    private static final List<String> ACTION_KEYWORDS = List.of(
            "here's", "this is", "remember", "key", "important", "you need",
            "why", "how to", "the reason", "what most", "most people",
            "the problem", "the solution", "think about", "the truth",
            "step", "tip", "hack", "rule"
    );

    // ── PUBLIC API ────────────────────────────────────────────────────────────

    /**
     * Detects and ranks highlight clips from a list of Whisper segments.
     *
     * @param segments  Whisper transcript segments (sorted by start time)
     * @return          Ranked clip candidates, best first
     */
    public List<ClipCandidate> detectClips(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            log.warn("No segments provided to clip detector");
            return List.of();
        }

        log.info("Scoring {} transcript segments for clip potential", segments.size());

        // 1. Score every individual segment
        List<ScoredSegment> scored = segments.stream()
                .map(s -> new ScoredSegment(s, scoreSegment(s)))
                .toList();

        // 2. Merge consecutive high-scoring segments into clip candidates
        List<ClipCandidate> candidates = mergeIntoClips(scored);

        // 3. Sort by score descending (best clip first)
        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));

        // 4. Keep top N
        List<ClipCandidate> topClips = candidates.stream()
                .limit(MAX_CLIPS)
                .toList();

        // 5. Assign ranks (1 = best)
        List<ClipCandidate> ranked = new ArrayList<>();
        for (int i = 0; i < topClips.size(); i++) {
            ClipCandidate c = topClips.get(i);
            ranked.add(new ClipCandidate(c.startTime(), c.endTime(), c.score(), i + 1, c.text()));
        }

        log.info("Clip detection complete: {} candidates from {} segments", ranked.size(), segments.size());
        ranked.forEach(c -> log.debug("  Clip {}: {:.2f}s–{:.2f}s score={:.4f} rank={}",
                c.rank(), c.startTime(), c.endTime(), c.score(), c.rank()));

        return ranked;
    }

    // ── SCORING ───────────────────────────────────────────────────────────────

    /**
     * Scores a single Whisper segment on a scale of 0.0–1.0.
     *
     * Each factor contributes independently to the final score.
     * All contributions are additive, then clamped to [0.0, 1.0].
     */
    double scoreSegment(TranscriptSegment segment) {
        String text     = segment.text().trim().toLowerCase();
        double duration = segment.end() - segment.start();

        if (text.isBlank() || duration <= 0) return 0.0;

        double score = 0.0;

        // Factor 1: Keyword density
        score += hookKeywordScore(text);
        score += actionKeywordScore(text);

        // Factor 2: Question signal
        if (text.endsWith("?")) score += 0.15;

        // Factor 3: Sentence completeness (ends with punctuation)
        if (text.endsWith(".") || text.endsWith("!") || text.endsWith("?")) score += 0.10;

        // Factor 4: Energy (speech rate)
        score += speechRateScore(text, duration);

        // Factor 5: Duration sweet spot
        score += durationScore(duration);

        // Clamp to [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, score));
    }

    private double hookKeywordScore(String text) {
        long matches = HOOK_KEYWORDS.stream().filter(text::contains).count();
        return Math.min(0.50, matches * 0.25);
    }

    private double actionKeywordScore(String text) {
        long matches = ACTION_KEYWORDS.stream().filter(text::contains).count();
        return Math.min(0.30, matches * 0.10);
    }

    private double speechRateScore(String text, double duration) {
        int words = text.split("\\s+").length;
        double wordsPerSec = words / duration;

        // Ideal: 2.5–4.5 words/sec — natural conversational speech
        if (wordsPerSec >= 2.5 && wordsPerSec <= 4.5) return 0.15;
        if (wordsPerSec >= 1.5 && wordsPerSec < 2.5)  return 0.05;
        if (wordsPerSec > 4.5 && wordsPerSec <= 6.0)  return 0.08; // fast but not frantic
        return 0.0; // too slow or too fast
    }

    private double durationScore(double duration) {
        // Sweet spot: 7–20 seconds → ideal short-form clip length
        if (duration >= 7  && duration <= 20)  return 0.10;
        if (duration >= 3  && duration < 7)    return 0.05;
        if (duration > 20  && duration <= 30)  return 0.05;
        return 0.0; // < 3s or > 30s: not ideal
    }

    // ── MERGING ───────────────────────────────────────────────────────────────

    /**
     * Merges consecutive high-scoring segments into clip candidates.
     *
     * Algorithm:
     *   - Walk through scored segments in order
     *   - When a segment's score >= threshold: start (or extend) a clip group
     *   - When score drops below threshold: close the group (if long enough)
     *   - Also close the group if it would exceed MAX_CLIP_DURATION
     */
    private List<ClipCandidate> mergeIntoClips(List<ScoredSegment> segments) {
        List<ClipCandidate> clips = new ArrayList<>();

        if (segments.isEmpty()) return clips;

        // Group builder state
        double groupStart    = -1;
        double groupEnd      = -1;
        double groupScoreSum = 0;
        int    groupCount    = 0;
        StringBuilder groupText = new StringBuilder();

        for (ScoredSegment ss : segments) {
            double segStart = ss.segment().start();
            double segEnd   = ss.segment().end();
            double segScore = ss.score();
            double duration = segEnd - segStart;

            boolean aboveThreshold  = segScore >= SCORE_THRESHOLD;
            boolean groupInProgress = groupStart >= 0;
            boolean wouldExceedMax  = groupInProgress
                    && (segEnd - groupStart) > MAX_CLIP_DURATION;

            if (aboveThreshold && !wouldExceedMax) {
                // Extend (or start) the current group
                if (!groupInProgress) {
                    groupStart = segStart;
                }
                groupEnd       = segEnd;
                groupScoreSum += segScore;
                groupCount++;
                groupText.append(" ").append(ss.segment().text());

            } else {
                // Close the current group if it's long enough
                if (groupInProgress) {
                    double groupDuration = groupEnd - groupStart;
                    if (groupDuration >= MIN_CLIP_DURATION) {
                        double avgScore = groupScoreSum / groupCount;
                        clips.add(new ClipCandidate(groupStart, groupEnd, avgScore, 0, groupText.toString().trim()));
                    }
                    // Reset group
                    groupStart    = -1;
                    groupEnd      = -1;
                    groupScoreSum = 0;
                    groupCount    = 0;
                    groupText.setLength(0);
                }

                // If this segment is still above threshold, start a new group
                if (aboveThreshold) {
                    groupStart    = segStart;
                    groupEnd      = segEnd;
                    groupScoreSum = segScore;
                    groupCount    = 1;
                    groupText.append(ss.segment().text());
                }
            }
        }

        // Close any open group at end of segments
        if (groupStart >= 0) {
            double groupDuration = groupEnd - groupStart;
            if (groupDuration >= MIN_CLIP_DURATION) {
                double avgScore = groupScoreSum / groupCount;
                clips.add(new ClipCandidate(groupStart, groupEnd, avgScore, 0, groupText.toString().trim()));
            }
        }

        return clips;
    }

    // ── Internal types ─────────────────────────────────────────────────────────

    /** A Whisper segment with its computed highlight score. */
    record ScoredSegment(TranscriptSegment segment, double score) {}

    /**
     * A detected clip candidate — a merged group of high-scoring segments.
     *
     * @param startTime  Start time in the original video (seconds)
     * @param endTime    End time in the original video (seconds)
     * @param score      Average highlight score (0.0–1.0)
     * @param rank       1 = best clip (assigned after sorting)
     * @param text       The combined transcript text of all merged segments
     */
    public record ClipCandidate(
            double startTime,
            double endTime,
            double score,
            int    rank,
            String text
    ) {}
}
