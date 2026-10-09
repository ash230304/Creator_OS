package com.creatoros.processing.service;

import com.creatoros.common.exception.ResourceNotFoundException;
import com.creatoros.common.security.SecurityUtils;
import com.creatoros.processing.dto.JobResponse;
import com.creatoros.processing.entity.ProcessingJob;
import com.creatoros.processing.repository.ProcessingJobRepository;
import com.creatoros.video.entity.Video;
import com.creatoros.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ProcessingService — orchestrates the async video processing pipeline.
 *
 * ── How async works here ──────────────────────────────────────────────────────
 * 1. Client calls POST /api/v1/videos/{id}/process
 * 2. createJob() runs synchronously: validates ownership, creates a job row in DB,
 *    returns jobId to client immediately (< 100ms)
 * 3. @Async runPipeline() is dispatched to Spring's thread pool — it runs
 *    in a SEPARATE thread while the HTTP response is already sent
 * 4. Client polls GET /api/v1/jobs/{jobId} to check progress
 *
 * ── Why @Async and not a message queue (Kafka/RabbitMQ)? ─────────────────────
 * For V1: @Async is simple, no infrastructure overhead, works in one JVM.
 * For V2: Replace runPipeline() with publishing a message to a Kafka topic.
 * A dedicated consumer service picks it up. The job status polling stays identical.
 * This is why the job state machine is designed this way — it's queue-ready.
 *
 * ── @Transactional and @Async ─────────────────────────────────────────────────
 * @Async methods cannot participate in the caller's transaction.
 * Each stage of the pipeline manages its own short transaction for status updates.
 *
 * ── V1 Pipeline (stub — Whisper/FFmpeg not integrated yet) ────────────────────
 * QUEUED → EXTRACTING_AUDIO → TRANSCRIBING → DETECTING_CLIPS →
 * GENERATING_CLIPS → ADDING_CAPTIONS → COMPLETED
 *
 * Each stage will be replaced with real implementations in subsequent weeks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessingService {

    private final ProcessingJobRepository jobRepository;
    private final VideoRepository         videoRepository;

    // ── CREATE JOB (synchronous — returns to client immediately) ─────────────

    /**
     * Creates a processing job for the given video and triggers the async pipeline.
     * Returns immediately with the jobId — client polls for progress.
     */
    @Transactional
    public JobResponse createJob(UUID videoId) {
        UUID userId = SecurityUtils.getCurrentUserId();

        // 1. Verify video ownership
        Video video = videoRepository.findByIdAndProjectUserId(videoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));

        // 2. Create job row in DB (status = QUEUED)
        ProcessingJob job = ProcessingJob.builder()
                .video(video)
                .status(ProcessingJob.Status.QUEUED)
                .progressPercent(0)
                .build();

        ProcessingJob saved = jobRepository.saveAndFlush(job);

        // 3. Update video status to QUEUED
        video.updateStatus(Video.Status.QUEUED);

        log.info("Processing job {} created for video {} by user {}", saved.getId(), videoId, userId);

        // 4. Dispatch async pipeline (fire-and-forget — returns jobId to client)
        runPipeline(saved.getId(), videoId);

        return JobResponse.from(saved);
    }

    // ── GET JOB STATUS (synchronous — polling endpoint) ───────────────────────

    /**
     * Returns the current state of a processing job.
     * Called repeatedly by the client to poll progress.
     * Each call is a simple SELECT — very fast.
     */
    @Transactional(readOnly = true)
    public JobResponse getJobStatus(UUID jobId) {
        ProcessingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        // TODO: add ownership check — verify job's video belongs to current user
        // For now: if you know the jobId (UUID), you can poll it.
        // V2: join through video → project → user to enforce ownership.

        return JobResponse.from(job);
    }

    // ── ASYNC PIPELINE (runs in background thread) ────────────────────────────

    /**
     * The full video processing pipeline.
     * Runs in a Spring-managed background thread (configured in AsyncConfig).
     * Each stage advances the job state and updates progress_percent.
     *
     * V1: Stages are stubbed with Thread.sleep to simulate work.
     * Real implementations to be added in Weeks 4-6:
     *   EXTRACTING_AUDIO  → FFmpegService.extractAudio(videoPath)
     *   TRANSCRIBING      → WhisperService.transcribe(audioPath)
     *   DETECTING_CLIPS   → ClipDetectionService.detect(transcript)
     *   GENERATING_CLIPS  → FFmpegService.cutClips(videoPath, segments)
     *   ADDING_CAPTIONS   → FFmpegService.burnCaptions(clipPaths)
     */
    @Async
    public void runPipeline(UUID jobId, UUID videoId) {
        log.info("Pipeline started for job {} / video {}", jobId, videoId);

        try {
            advanceStage(jobId, videoId, ProcessingJob.Status.EXTRACTING_AUDIO, 10);
            simulateWork("Extracting audio", 2000);

            advanceStage(jobId, videoId, ProcessingJob.Status.TRANSCRIBING, 25);
            simulateWork("Transcribing", 3000);
            // TODO: WhisperService.transcribe(audioPath) → save to video_transcripts

            advanceStage(jobId, videoId, ProcessingJob.Status.DETECTING_CLIPS, 50);
            simulateWork("Detecting clips", 2000);
            // TODO: ClipDetectionService.detect(transcript) → score segments

            advanceStage(jobId, videoId, ProcessingJob.Status.GENERATING_CLIPS, 70);
            simulateWork("Generating clips", 3000);
            // TODO: FFmpegService.cutClips(videoPath, segments) → save to clips

            advanceStage(jobId, videoId, ProcessingJob.Status.ADDING_CAPTIONS, 90);
            simulateWork("Adding captions", 2000);
            // TODO: FFmpegService.burnCaptions(clipPaths) → update clips.caption_path

            // Mark complete
            advanceStage(jobId, videoId, ProcessingJob.Status.COMPLETED, 100);
            log.info("Pipeline COMPLETED for job {} / video {}", jobId, videoId);

        } catch (Exception e) {
            log.error("Pipeline FAILED for job {} / video {}: {}", jobId, videoId, e.getMessage(), e);
            failJob(jobId, videoId, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Advance the job to the next stage and update video status.
     * Each call is its own transaction — short and atomic.
     */
    @Transactional
    protected void advanceStage(UUID jobId, UUID videoId,
                                ProcessingJob.Status status, int progress) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.advanceTo(status, progress);
            log.debug("Job {} → {} ({}%)", jobId, status, progress);
        });

        // Keep video status in sync
        if (status == ProcessingJob.Status.EXTRACTING_AUDIO) {
            videoRepository.findById(videoId).ifPresent(v -> v.updateStatus(Video.Status.PROCESSING));
        }
        if (status == ProcessingJob.Status.COMPLETED) {
            videoRepository.findById(videoId).ifPresent(v -> v.updateStatus(Video.Status.COMPLETED));
        }
    }

    @Transactional
    protected void failJob(UUID jobId, UUID videoId, String error) {
        jobRepository.findById(jobId).ifPresent(job -> job.fail(error));
        videoRepository.findById(videoId).ifPresent(v -> v.updateStatus(Video.Status.FAILED));
    }

    private void simulateWork(String stage, long millis) throws InterruptedException {
        log.debug("Simulating: {} ({}ms)", stage, millis);
        Thread.sleep(millis);
    }
}
