package com.creatoros.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AsyncConfig — configures Spring's @Async executor for video processing.
 *
 * ── Why a custom executor instead of the default? ─────────────────────────────
 * Spring's default @Async executor is a SimpleAsyncTaskExecutor which creates
 * a new thread for EVERY async call — no pooling, no limits, no queue.
 * Under load this would create unbounded threads and exhaust the JVM.
 *
 * ThreadPoolTaskExecutor gives us:
 *   - A bounded thread pool (controlled concurrency)
 *   - A queue for waiting tasks (absorbs bursts)
 *   - Named threads (easier to debug: "video-processor-1" in logs)
 *
 * ── Settings explanation ──────────────────────────────────────────────────────
 *   corePoolSize=2   → always keep 2 threads alive for video processing
 *   maxPoolSize=5    → scale up to 5 under heavy load
 *   queueCapacity=20 → queue up to 20 jobs before rejecting new ones
 *
 * For V1 (solo/small team), 2 concurrent video jobs is plenty.
 * Video processing is CPU-bound (FFmpeg) — more threads than CPU cores = no benefit.
 *
 * ── Talking point ─────────────────────────────────────────────────────────────
 * "I configured a bounded thread pool for async processing. Video processing is
 * CPU-bound so I limited concurrent workers to avoid overwhelming the host.
 * The queue absorbs traffic spikes without dropping requests."
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "videoProcessingExecutor")
    public Executor videoProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("video-processor-");
        executor.initialize();
        return executor;
    }
}
