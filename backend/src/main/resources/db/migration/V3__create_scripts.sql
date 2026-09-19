-- ══════════════════════════════════════════════════════════════════
-- V3 — SCRIPTS
-- Stores AI-generated scripts for a project.
-- One project can have multiple script iterations (creator may
-- re-generate with different tone/platform).
--
-- Design decisions:
--   - idea TEXT: the raw idea the creator typed in.
--
--   - platform / tone / target_duration_seconds:
--     The parameters used to generate this script.
--     Stored here so you can show the creator what settings produced
--     what output — useful for re-generation.
--
--   - content JSONB:
--     The full structured script from the AI, stored as JSON.
--     Why JSONB instead of separate columns?
--     The script structure has nested arrays (sections, each with
--     dialogue, b-roll, editing instructions). A relational schema
--     for this would be 3-4 extra tables with no query benefit.
--     JSONB is indexed, queryable, and flexible.
--
--     Structure stored:
--     {
--       "hook": "...",
--       "sections": [
--         {
--           "start": 0, "end": 5,
--           "dialogue": "...",
--           "visual": "...",
--           "editingInstruction": "..."
--         }
--       ],
--       "cta": "..."
--     }
--
--   - No ON DELETE CASCADE from project here intentionally.
--     If we delete a project, we delete scripts too (via project FK cascade).
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE scripts (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    idea                    TEXT        NOT NULL,
    platform                VARCHAR(50) NOT NULL,
    tone                    VARCHAR(50) NOT NULL DEFAULT 'NEUTRAL',
    target_duration_seconds INT         NOT NULL DEFAULT 60,
    content                 JSONB       NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index: "show me all scripts for this project"
CREATE INDEX idx_scripts_project_id ON scripts(project_id);

COMMENT ON TABLE  scripts         IS 'AI-generated video scripts';
COMMENT ON COLUMN scripts.content IS 'Full structured script as JSONB: {hook, sections[], cta}';
COMMENT ON COLUMN scripts.tone    IS 'DIRECT | EDUCATIONAL | ENTERTAINING | STORYTELLING | NEUTRAL';
