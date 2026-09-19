-- ══════════════════════════════════════════════════════════════════
-- V5 — CLIPS
-- Individual highlight clips generated from a video by FFmpeg.
-- Each clip is a short segment with start/end times, a score, and
-- its own output file path.
--
-- Design decisions:
--   - start_time / end_time DECIMAL(10,3):
--     FFmpeg works with float timestamps (seconds + milliseconds).
--     e.g. start_time = 42.500, end_time = 67.250
--
--   - score DECIMAL(5,4): a float between 0.0 and 1.0.
--     Represents how "highlight-worthy" this segment is.
--     Computed by the clip detection algorithm (heuristic or LLM).
--     0.9500 = very strong highlight, 0.3200 = weak.
--
--   - storage_path: path to the FFmpeg-generated clip file.
--
--   - caption_path: path to the version with burned-in captions.
--     NULL until Week 6 (caption step).
--
--   - caption_status:
--     PENDING   = clip generated, captions not started
--     COMPLETED = captions burned in, clip is final
--     FAILED    = caption generation failed
--
--   - rank INT: ordering within a video. Clip 1 is the strongest
--     highlight, clip 2 is second, etc.
--     Determined by the clip detection service when scoring.
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE clips (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id        UUID          NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    start_time      DECIMAL(10,3) NOT NULL,
    end_time        DECIMAL(10,3) NOT NULL,
    duration_seconds DECIMAL(10,3) GENERATED ALWAYS AS (end_time - start_time) STORED,
    score           DECIMAL(5,4)  NOT NULL DEFAULT 0.0000,
    rank            INT           NOT NULL DEFAULT 1,
    storage_path    VARCHAR(1000),
    caption_path    VARCHAR(1000),
    caption_status  VARCHAR(50)   NOT NULL DEFAULT 'PENDING'
                        CHECK (caption_status IN ('PENDING', 'COMPLETED', 'FAILED')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- A clip's end must be after its start
    CONSTRAINT chk_clip_times CHECK (end_time > start_time)
);

-- Index: "show me all clips for this video, ordered by rank"
CREATE INDEX idx_clips_video_id ON clips(video_id);

-- Index: "show me the best clips first" (dashboard ordering)
CREATE INDEX idx_clips_score ON clips(score DESC);

COMMENT ON TABLE  clips                   IS 'FFmpeg-generated highlight clips from a video';
COMMENT ON COLUMN clips.score             IS 'Highlight score 0.0-1.0 from detection algorithm';
COMMENT ON COLUMN clips.duration_seconds  IS 'Computed column: end_time - start_time';
COMMENT ON COLUMN clips.caption_status    IS 'PENDING | COMPLETED | FAILED';
