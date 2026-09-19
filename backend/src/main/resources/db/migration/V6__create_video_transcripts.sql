-- ══════════════════════════════════════════════════════════════════
-- V6 — VIDEO_TRANSCRIPTS
-- Stores Whisper's transcription output for a video.
-- One video → one transcript (1-to-1 relationship).
--
-- Design decisions:
--   - full_text TEXT: the entire transcript as a single string.
--     Used for full-text search and display in the UI.
--
--   - segments JSONB: the timestamped segments array from Whisper.
--     This is the key column — the clip detection algorithm reads
--     this to find highlight moments.
--
--     Structure:
--     [
--       { "start": 0.0, "end": 4.2,  "text": "Most students fail because..." },
--       { "start": 4.2, "end": 9.8,  "text": "Here's what actually works." },
--       { "start": 9.8, "end": 15.1, "text": "First, build in public." }
--     ]
--
--   - language VARCHAR(10): detected language code e.g. "en", "hi", "es".
--     Whisper detects this automatically.
--
--   - model_used: which Whisper model produced this.
--     "whisper-1" (API) or "medium", "large" (local).
--     Stored for reproducibility — different models give different quality.
--
--   - UNIQUE(video_id): enforces the 1-to-1 relationship at DB level.
--     Attempting to insert a second transcript for the same video
--     will throw a constraint violation.
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE video_transcripts (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id    UUID    NOT NULL UNIQUE REFERENCES videos(id) ON DELETE CASCADE,
    full_text   TEXT    NOT NULL,
    segments    JSONB   NOT NULL DEFAULT '[]'::jsonb,
    language    VARCHAR(10) NOT NULL DEFAULT 'en',
    model_used  VARCHAR(100),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- GIN index on segments: enables fast JSONB queries.
-- Example: find segments containing the word "viral"
-- SELECT * FROM video_transcripts WHERE segments @> '[{"text": "viral"}]';
CREATE INDEX idx_transcripts_segments_gin ON video_transcripts USING GIN (segments);

-- Full-text search index on the transcript text
CREATE INDEX idx_transcripts_full_text ON video_transcripts USING GIN (to_tsvector('english', full_text));

COMMENT ON TABLE  video_transcripts          IS 'Whisper transcription output — 1-to-1 with videos';
COMMENT ON COLUMN video_transcripts.segments IS 'Timestamped segments: [{start, end, text}] from Whisper';
COMMENT ON COLUMN video_transcripts.language IS 'ISO 639-1 language code detected by Whisper';
