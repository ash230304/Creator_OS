-- ══════════════════════════════════════════════════════════════════
-- V1 — USERS
-- The foundation table. Every other table references this.
--
-- Design decisions:
--   - UUID primary key (not auto-increment integer).
--     Why? UUIDs are globally unique across tables and services.
--     You can generate them in Java before the insert, which is
--     useful when you need the ID before the DB call returns.
--     gen_random_uuid() is built into Postgres 13+.
--
--   - password_hash — bcrypt hash, NEVER the raw password.
--     BCrypt output is always 60 chars. VARCHAR(255) is fine.
--
--   - email — UNIQUE constraint means Postgres auto-creates an index.
--     Duplicate email registration → constraint violation → our
--     ConflictException handler catches it.
--
--   - updated_at — we'll use a trigger (in a later migration) or
--     handle this in the service layer.
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE users (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    name           VARCHAR(100) NOT NULL,
    bio            TEXT,
    avatar_url     VARCHAR(500),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index: email lookups happen on every login — must be fast.
-- The UNIQUE constraint already creates an index, so no extra needed.

COMMENT ON TABLE  users              IS 'Creator accounts';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash — raw password never stored';
