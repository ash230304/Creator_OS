-- ══════════════════════════════════════════════════════════════════
-- V7 — PROCESSING_JOBS
-- Tracks the async processing pipeline for each video.
-- When a creator triggers processing, a job row is created.
-- The async worker updates this row as it progresses through stages.
--
-- Design decisions:
--   - status — the full pipeline state machine:
--
--     QUEUED
--       ↓
--     EXTRACTING_AUDIO        ← FFmpeg extracts WAV from video
--       ↓
--     TRANSCRIBING            ← Whisper processes the audio
--       ↓
--     DETECTING_CLIPS         ← Heuristic/LLM scores transcript segments
--       ↓
--     GENERATING_CLIPS        ← FFmpeg cuts clip files
--       ↓
--     ADDING_CAPTIONS         ← FFmpeg burns subtitles into clips
--       ↓
--     COMPLETED
--
--     At any stage → FAILED (with error_message populated)
--
--   - started_at / completed_at: lets you calculate processing time.
--     This is a key metric for your case study:
--     "average processing time = completed_at - started_at"
--
--   - error_message TEXT: full error/stack trace when status = FAILED.
--     Lets you debug without checking server logs.
--
--   - progress_percent INT: 0-100 for a progress bar in the UI.
--     Each stage completion bumps this.
--     QUEUED=0, EXTRACTING=10, TRANSCRIBING=25, DETECTING=50,
--     GENERATING=70, CAPTIONS=90, COMPLETED=100
--
--   - No UNIQUE(video_id) here — a video can have multiple job
--     attempts (e.g. first attempt FAILED, user retries → new job).
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE processing_jobs (
    id               UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id         UUID    NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    status           VARCHAR(50) NOT NULL DEFAULT 'QUEUED'
                         CHECK (status IN (
                             'QUEUED',
                             'EXTRACTING_AUDIO',
                             'TRANSCRIBING',
                             'DETECTING_CLIPS',
                             'GENERATING_CLIPS',
                             'ADDING_CAPTIONS',
                             'COMPLETED',
                             'FAILED'
                         )),
    progress_percent INT         NOT NULL DEFAULT 0
                         CHECK (progress_percent BETWEEN 0 AND 100),
    error_message    TEXT,
    started_at       TIMESTAMP WITH TIME ZONE,
    completed_at     TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index: "find the latest job for this video" — polled frequently
CREATE INDEX idx_jobs_video_id ON processing_jobs(video_id);

-- Index: worker looks for QUEUED jobs to pick up
CREATE INDEX idx_jobs_status ON processing_jobs(status);

COMMENT ON TABLE  processing_jobs                IS 'Async video processing job tracker';
COMMENT ON COLUMN processing_jobs.status         IS 'QUEUED→EXTRACTING_AUDIO→TRANSCRIBING→DETECTING_CLIPS→GENERATING_CLIPS→ADDING_CAPTIONS→COMPLETED or FAILED';
COMMENT ON COLUMN processing_jobs.progress_percent IS '0-100 for UI progress bar';
COMMENT ON COLUMN processing_jobs.error_message  IS 'Populated on FAILED — full error detail for debugging';
