package com.creatoros.processing.service;

import com.creatoros.clipping.service.ClippingService;
import com.creatoros.processing.entity.ProcessingJob;
import com.creatoros.transcription.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AsyncPipelineRunner — holds the @Async entry point for the video processing pipeline.
 *
 * Owns and executes the pipeline so calls from ProcessingService cross the
 * Spring @Async proxy without creating a dependency cycle between the beans.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncPipelineRunner {

    private final TranscriptionService transcriptionService;
    private final ClippingService clippingService;
    private final JobStatusService jobStatusService;

    @Async("videoProcessingExecutor")
    public void run(UUID jobId, UUID videoId) {
        log.info("Pipeline started for job {} / video {}", jobId, videoId);

        try {
            jobStatusService.advanceStage(jobId, videoId, ProcessingJob.Status.EXTRACTING_AUDIO, 10);

            jobStatusService.advanceStage(jobId, videoId, ProcessingJob.Status.TRANSCRIBING, 25);
            transcriptionService.transcribeVideo(videoId);
            log.info("Transcription complete for video {}", videoId);

            jobStatusService.advanceStage(jobId, videoId, ProcessingJob.Status.DETECTING_CLIPS, 50);
            int clipCount = clippingService.detectAndPersistClips(videoId);
            log.info("Detected {} clips for video {}", clipCount, videoId);

            jobStatusService.advanceStage(jobId, videoId, ProcessingJob.Status.GENERATING_CLIPS, 70);
            clippingService.generateClips(videoId);
            log.info("FFmpeg clip cuts complete for video {}", videoId);

            jobStatusService.advanceStage(jobId, videoId, ProcessingJob.Status.ADDING_CAPTIONS, 90);
            clippingService.addCaptions(videoId);
            log.info("Caption burning complete for video {}", videoId);

            jobStatusService.advanceStage(jobId, videoId, ProcessingJob.Status.COMPLETED, 100);
            log.info("Pipeline COMPLETED for job {} / video {}", jobId, videoId);
        } catch (Exception e) {
            log.error("Pipeline FAILED for job {} / video {}: {}", jobId, videoId, e.getMessage(), e);
            jobStatusService.failJob(jobId, videoId, e.getMessage());
        }
    }
}
