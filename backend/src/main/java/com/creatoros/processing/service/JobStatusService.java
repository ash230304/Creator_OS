package com.creatoros.processing.service;

import com.creatoros.processing.entity.ProcessingJob;
import com.creatoros.processing.repository.ProcessingJobRepository;
import com.creatoros.video.entity.Video;
import com.creatoros.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * JobStatusService — handles DB status updates for ProcessingJob and Video entities.
 *
 * ── Why a separate service? ────────────────────────────────────────────────────
 * ProcessingService.runPipeline() is an @Async method. It previously called
 * advanceStage() and failJob() as @Transactional protected methods on itself.
 * Self-calls bypass Spring's transaction proxy — meaning those status updates
 * were NOT committed to the DB, so polling clients never saw progress updates.
 *
 * By extracting to a separate @Service, each call is properly proxied and
 * each status update runs in its own short, committed transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobStatusService {

    private final ProcessingJobRepository jobRepository;
    private final VideoRepository         videoRepository;

    /**
     * Advance the job to the next stage and update video status.
     * Each call is its own transaction — short and atomic.
     */
    @Transactional
    public void advanceStage(UUID jobId, UUID videoId,
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

    /**
     * Mark the job and video as FAILED and store the error message.
     */
    @Transactional
    public void failJob(UUID jobId, UUID videoId, String error) {
        jobRepository.findById(jobId).ifPresent(job -> job.fail(error));
        videoRepository.findById(videoId).ifPresent(v -> v.updateStatus(Video.Status.FAILED));
        log.warn("Job {} / video {} marked FAILED: {}", jobId, videoId, error);
    }
}
