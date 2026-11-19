package com.creatoros.transcription.service;

import com.creatoros.common.exception.ResourceNotFoundException;
import com.creatoros.transcription.entity.VideoTranscript;
import com.creatoros.transcription.repository.VideoTranscriptRepository;
import com.creatoros.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Persists a completed transcription using only a short database transaction. */
@Service
@RequiredArgsConstructor
public class TranscriptPersistenceService {

    private final VideoRepository videoRepository;
    private final VideoTranscriptRepository transcriptRepository;

    @Transactional
    public VideoTranscript save(UUID videoId, String fullText, String segments,
                                String language, String modelUsed) {
        var video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));
        transcriptRepository.findByVideoId(videoId).ifPresent(transcriptRepository::delete);

        return transcriptRepository.saveAndFlush(VideoTranscript.builder()
                .video(video)
                .fullText(fullText)
                .segments(segments)
                .language(language)
                .modelUsed(modelUsed)
                .build());
    }
}
