-- V55: Rename clerk_user_id to external_user_id for auth provider abstraction

ALTER TABLE members RENAME COLUMN clerk_user_id TO external_user_id;

-- Rename constraint
ALTER TABLE members RENAME CONSTRAINT uq_members_clerk_user_id TO uq_members_external_user_id;

-- Rename index
DROP INDEX IF EXISTS idx_members_clerk_user_id;
CREATE INDEX IF NOT EXISTS idx_members_external_user_id
    ON members (external_user_id);
