-- ══════════════════════════════════════════════════════════════════
-- V2 — PROJECTS
-- A project is a creator's content campaign.
-- Groups together a script + video + clips under one name.
--
-- Design decisions:
--   - user_id FK with ON DELETE CASCADE:
--     If a user is deleted, all their projects go too.
--     This cascades further: project → videos → clips.
--
--   - platform: the target social media platform.
--     Stored as VARCHAR rather than an enum because adding a new
--     platform later would require an ALTER TYPE migration.
--     We'll validate allowed values at the service layer.
--     Known values: INSTAGRAM_REEL, YOUTUBE_SHORT, TIKTOK, GENERAL
--
--   - status: lifecycle of the project.
--     DRAFT     = created, no content yet
--     SCRIPTED  = AI script generated
--     UPLOADED  = video uploaded
--     PROCESSED = clips ready
--     ARCHIVED  = hidden from dashboard
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE projects (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    platform    VARCHAR(50)  NOT NULL DEFAULT 'GENERAL',
    status      VARCHAR(50)  NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'SCRIPTED', 'UPLOADED', 'PROCESSED', 'ARCHIVED')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index: dashboard loads "all projects for a user" — this query runs
-- on every page load, must be instant.
CREATE INDEX idx_projects_user_id ON projects(user_id);

COMMENT ON TABLE  projects          IS 'Creator content projects — groups script + video + clips';
COMMENT ON COLUMN projects.platform IS 'INSTAGRAM_REEL | YOUTUBE_SHORT | TIKTOK | GENERAL';
COMMENT ON COLUMN projects.status   IS 'DRAFT | SCRIPTED | UPLOADED | PROCESSED | ARCHIVED';
