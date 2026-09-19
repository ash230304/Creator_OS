-- ══════════════════════════════════════════════════════════════════
-- V4 — VIDEOS
-- Tracks uploaded video files and their processing lifecycle.
--
-- Design decisions:
--   - original_filename: what the user named their file on upload.
--     Stored for display — we never use this as a filesystem path
--     (security risk: path traversal attacks).
--
--   - storage_path: where we actually saved the file.
--     For local storage: "uploads/videos/{uuid}.mp4"
--     For S3: "videos/{uuid}/original.mp4"
--     The storage abstraction layer determines the format.
--
--   - file_size_bytes BIGINT: video files can exceed 2GB.
--     INT maxes at ~2.1 billion bytes (~2GB). BIGINT handles ~9.2 EB.
--
--   - duration_seconds DECIMAL(10,3): supports millisecond precision.
--     e.g. 142.750 seconds (2 min 22.75 sec).
--
--   - mime_type: e.g. "video/mp4", "video/quicktime".
--     Used to validate uploads and tell FFmpeg the format.
--
--   - status — video lifecycle:
--     UPLOADED    = file received, nothing else done
--     QUEUED      = processing job created
--     PROCESSING  = job is actively running
--     COMPLETED   = clips + captions ready
--     FAILED      = something went wrong (see processing_jobs for detail)
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE videos (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        UUID          NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    original_filename VARCHAR(500)  NOT NULL,
    storage_path      VARCHAR(1000) NOT NULL,
    file_size_bytes   BIGINT,
    duration_seconds  DECIMAL(10,3),
    mime_type         VARCHAR(100),
    status            VARCHAR(50)   NOT NULL DEFAULT 'UPLOADED'
                          CHECK (status IN ('UPLOADED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index: "show me all videos in this project"
CREATE INDEX idx_videos_project_id ON videos(project_id);

-- Index: polling jobs often filter by status ("find all QUEUED videos")
CREATE INDEX idx_videos_status ON videos(status);

COMMENT ON TABLE  videos              IS 'Uploaded video files and their processing status';
COMMENT ON COLUMN videos.storage_path IS 'Relative path — local: uploads/videos/{id}.mp4 | S3: videos/{id}/original.mp4';
COMMENT ON COLUMN videos.status       IS 'UPLOADED | QUEUED | PROCESSING | COMPLETED | FAILED';
