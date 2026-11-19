package com.creatoros.clipping.service;

import com.creatoros.clipping.dto.ClipResponse;
import com.creatoros.clipping.entity.Clip;
import com.creatoros.clipping.repository.ClipRepository;
import com.creatoros.common.exception.BusinessException;
import com.creatoros.common.exception.ResourceNotFoundException;
import com.creatoros.common.security.SecurityUtils;
import com.creatoros.transcription.dto.TranscriptSegment;
import com.creatoros.transcription.entity.VideoTranscript;
import com.creatoros.transcription.repository.VideoTranscriptRepository;
import com.creatoros.video.entity.Video;
import com.creatoros.video.repository.VideoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ClippingService — orchestrates the three clip-generation stages of the pipeline:
 *
 *   Stage 3 — DETECTING_CLIPS:
 *     Reads the video's transcript segments → ClipDetectionService scores them
 *     → groups into ClipCandidates → persists Clip rows in the DB.
 *
 *   Stage 4 — GENERATING_CLIPS:
 *     For each persisted Clip, calls FFmpegService.cutClip() to cut the segment
 *     out of the original video and saves the output path.
 *
 *   Stage 5 — ADDING_CAPTIONS:
 *     For each raw clip, builds an SRT from the transcript and calls
 *     FFmpegService.burnCaptions() to burn in subtitles.
 *
 * ── Why all three stages in one service? ─────────────────────────────────────
 * They share the same dependencies (Clip entity, ClipRepository, FFmpegService)
 * and are always called in sequence by ProcessingService. Keeping them together
 * avoids a circular dependency between processing and clipping packages.
 *
 * ── Why not @Transactional on generateClips / addCaptions? ──────────────────
 * FFmpeg calls are long-running and external — holding a DB transaction open
 * across them would exhaust the connection pool. Each clip's DB update is
 * wrapped in its own short transaction instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClippingService {

    private final ClipDetectionService     detectionService;
    private final FFmpegService            ffmpegService;
    private final ClipRepository           clipRepository;
    private final VideoTranscriptRepository transcriptRepository;
    private final VideoRepository          videoRepository;
    private final ObjectMapper             objectMapper;
    private final ClipStatusService        clipStatusService;

    @Value("${storage.local.base-path:./uploads}")
    private String storagePath;

    // ── STAGE 3: DETECT CLIPS ─────────────────────────────────────────────────

    /**
     * Reads the transcript for a video, scores segments, and persists
     * Clip rows ranked by score (rank 1 = best).
     *
     * Called from ProcessingService during the DETECTING_CLIPS stage.
     *
     * @param videoId  ID of the video whose transcript to process
     * @return number of clips detected
     */
    @Transactional
    public int detectAndPersistClips(UUID videoId) {
        log.info("Detecting clips for video {}", videoId);

        // 1. Load video
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));

        // 2. Load transcript
        VideoTranscript transcript = transcriptRepository.findByVideoId(videoId)
                .orElseThrow(() -> new BusinessException(
                        "No transcript found for video " + videoId + ". Run transcription first."));

        // 3. Deserialize segments JSON → List<TranscriptSegment>
        //    VideoTranscript.segments is the JSON column (field name: segments, getter: getSegments())
        List<TranscriptSegment> segments;
        try {
            segments = objectMapper.readValue(
                    transcript.getSegments(),
                    new TypeReference<List<TranscriptSegment>>() {}
            );
        } catch (Exception e) {
            throw new BusinessException("Failed to parse transcript segments: " + e.getMessage());
        }

        if (segments.isEmpty()) {
            log.warn("Transcript for video {} has no segments — no clips will be generated", videoId);
            return 0;
        }

        // 4. Run detection algorithm (method: detectClips)
        List<ClipDetectionService.ClipCandidate> candidates = detectionService.detectClips(segments);

        if (candidates.isEmpty()) {
            log.warn("No clip candidates detected for video {} — try a longer video", videoId);
            return 0;
        }

        // 5. Persist each candidate as a Clip entity
        List<Clip> clips = new ArrayList<>();
        for (ClipDetectionService.ClipCandidate candidate : candidates) {
            Clip clip = Clip.builder()
                    .video(video)
                    .startTime(BigDecimal.valueOf(candidate.startTime()).setScale(3, RoundingMode.HALF_UP))
                    .endTime(BigDecimal.valueOf(candidate.endTime()).setScale(3, RoundingMode.HALF_UP))
                    .score(BigDecimal.valueOf(candidate.score()).setScale(4, RoundingMode.HALF_UP))
                    .rank(candidate.rank())
                    .captionStatus(Clip.CaptionStatus.PENDING)
                    .build();
            clips.add(clip);
        }

        clipRepository.saveAll(clips);
        log.info("Detected and persisted {} clips for video {}", clips.size(), videoId);
        return clips.size();
    }

    // ── STAGE 4: GENERATE CLIPS (FFmpeg cut) ─────────────────────────────────

    /**
     * Cuts each detected clip from the original video using FFmpeg.
     * Updates each Clip's storagePath once the cut is done.
     *
     * Called from ProcessingService during the GENERATING_CLIPS stage.
     * Each clip's DB update is its own transaction to avoid holding a long
     * transaction open across FFmpeg calls.
     *
     * @param videoId  ID of the video to cut clips from
     */
    public void generateClips(UUID videoId) {
        log.info("Generating (cutting) clips for video {}", videoId);

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));

        File videoFile = resolveStoredFile(video.getStoragePath());
        if (!videoFile.exists()) {
            throw new BusinessException("Video file not found on disk: " + video.getStoragePath());
        }

        List<Clip> clips = clipRepository.findAllByVideoIdOrderByRankAsc(videoId);
        if (clips.isEmpty()) {
            log.warn("No clips to cut for video {} — was detection skipped?", videoId);
            return;
        }

        for (Clip clip : clips) {
            try {
                File outputFile = ffmpegService.cutClip(
                        videoFile,
                        clip.getStartTime().doubleValue(),
                        clip.getEndTime().doubleValue(),
                        clip.getId()
                );
                clipStatusService.saveClipPath(clip.getId(), "clips/" + outputFile.getName());
                log.debug("Cut clip {} → {}", clip.getId(), outputFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to cut clip {} for video {}: {}", clip.getId(), videoId, e.getMessage());
                // Continue processing other clips — one failure doesn't abort all
            }
        }
    }

    private File resolveStoredFile(String storedPath) {
        File path = new File(storedPath);
        if (path.isAbsolute() || path.exists()) return path;
        return Path.of(storagePath).resolve(storedPath).normalize().toFile();
    }

    // ── STAGE 5: ADD CAPTIONS (FFmpeg subtitle burn) ─────────────────────────

    /**
     * Burns captions into each raw clip using FFmpeg's subtitles filter.
     * Updates each Clip's captionPath and marks captionStatus = COMPLETED.
     *
     * Called from ProcessingService during the ADDING_CAPTIONS stage.
     *
     * @param videoId  ID of the video whose clips need captions
     */
    public void addCaptions(UUID videoId) {
        log.info("Adding captions to clips for video {}", videoId);

        // Load segments for SRT generation
        List<TranscriptSegment> segments = loadSegments(videoId);
        List<Clip> clips = clipRepository.findAllByVideoIdOrderByRankAsc(videoId);

        for (Clip clip : clips) {
            if (clip.getStoragePath() == null) {
                log.warn("Clip {} has no storage path — skipping captions", clip.getId());
                continue;
            }
            try {
                // Build SRT for segments that overlap with this clip's time range
                double clipStart = clip.getStartTime().doubleValue();
                double clipEnd   = clip.getEndTime().doubleValue();

                List<FFmpegService.SrtSegment> srtSegments = segments.stream()
                        .filter(s -> s.end() > clipStart && s.start() < clipEnd)
                        .map(s -> new FFmpegService.SrtSegment(s.start(), s.end(), s.text()))
                        .toList();

                String srtContent = ffmpegService.buildSrt(srtSegments, clipStart);

                File captionedFile = ffmpegService.burnCaptions(
                        resolveStoredFile(clip.getStoragePath()),
                        srtContent,
                        clip.getId()
                );
                clipStatusService.completeClipCaptions(clip.getId(), "captioned/" + captionedFile.getName());
                log.debug("Captions burned for clip {} → {}", clip.getId(), captionedFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to burn captions for clip {}: {}", clip.getId(), e.getMessage());
                clipStatusService.failClipCaptions(clip.getId());
            }
        }
    }

    // ── READ: LIST CLIPS FOR A VIDEO ─────────────────────────────────────────

    /**
     * Returns all clips for a video in rank order (rank 1 = best clip first).
     * Ownership-scoped: only returns clips for videos owned by the current user.
     *
     * @param videoId  ID of the video
     * @return list of ClipResponse (may be empty if detection hasn't run yet)
     */
    @Transactional(readOnly = true)
    public List<ClipResponse> listClips(UUID videoId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return clipRepository
                .findAllByVideoIdAndVideoProjectUserIdOrderByRankAsc(videoId, userId)
                .stream()
                .map(ClipResponse::from)
                .toList();
    }

    // ── READ: SINGLE CLIP ─────────────────────────────────────────────────────

    /**
     * Returns a single clip by its ID.
     * Returns 404 if the clip doesn't exist or belongs to another user's video.
     *
     * @param clipId  UUID of the clip
     * @return ClipResponse
     */
    @Transactional(readOnly = true)
    public ClipResponse getClip(UUID clipId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        Clip clip = clipRepository.findByIdAndVideoProjectUserId(clipId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Clip not found: " + clipId));
        return ClipResponse.from(clip);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Loads and deserializes transcript segments for a video.
     * Returns empty list if no transcript exists (graceful degradation — clips
     * will still be cut, just without captions).
     */
    private List<TranscriptSegment> loadSegments(UUID videoId) {
        VideoTranscript transcript = transcriptRepository.findByVideoId(videoId).orElse(null);
        if (transcript == null) return List.of();
        try {
            return objectMapper.readValue(
                    transcript.getSegments(),
                    new TypeReference<List<TranscriptSegment>>() {}
            );
        } catch (Exception e) {
            log.warn("Failed to parse segments for video {} — captions will be empty: {}", videoId, e.getMessage());
            return List.of();
        }
    }

}
