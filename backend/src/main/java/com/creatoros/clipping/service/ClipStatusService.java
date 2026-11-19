package com.creatoros.clipping.service;

import com.creatoros.clipping.entity.Clip;
import com.creatoros.clipping.repository.ClipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ClipStatusService — handles DB status updates for Clip entities.
 *
 * ── Why a separate service? ────────────────────────────────────────────────────
 * Spring's @Transactional relies on AOP proxies. When a method calls another
 * @Transactional method on the SAME object (self-call), the proxy is bypassed —
 * meaning the transaction is never started and DB changes are silently lost.
 *
 * ClippingService previously had saveClipPath(), completeClipCaptions(), and
 * failClipCaptions() as @Transactional protected methods called from within
 * the same class. Those calls were not actually transactional.
 *
 * By moving these to a separate @Service bean, Spring properly proxies each
 * call through a new short transaction.
 *
 * ── Pattern ───────────────────────────────────────────────────────────────────
 * Each method:
 *   1. Opens a short transaction
 *   2. Loads the Clip from DB (within the transaction)
 *   3. Mutates the managed entity (Hibernate dirty-checks on commit)
 *   4. Commits the transaction
 *
 * No explicit save() needed — Hibernate flushes dirty entities at commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipStatusService {

    private final ClipRepository clipRepository;

    /**
     * Updates clip storagePath after FFmpeg writes the raw clip file.
     * Called per-clip after each cutClip() completes.
     */
    @Transactional
    public void saveClipPath(UUID clipId, String path) {
        clipRepository.findById(clipId).ifPresent(clip -> {
            clip.setStoragePath(path);
            log.debug("Clip {} storagePath saved: {}", clipId, path);
        });
    }

    /**
     * Marks clip captions as COMPLETED and stores captionPath.
     * Called after FFmpeg burns captions into the clip.
     */
    @Transactional
    public void completeClipCaptions(UUID clipId, String captionPath) {
        clipRepository.findById(clipId).ifPresent(clip -> {
            clip.completeCaptions(captionPath);
            log.debug("Clip {} captions completed: {}", clipId, captionPath);
        });
    }

    /**
     * Marks clip captions as FAILED.
     * Called when FFmpeg caption burning throws an exception.
     */
    @Transactional
    public void failClipCaptions(UUID clipId) {
        clipRepository.findById(clipId).ifPresent(clip -> {
            clip.failCaptions();
            log.debug("Clip {} captions marked FAILED", clipId);
        });
    }
}
