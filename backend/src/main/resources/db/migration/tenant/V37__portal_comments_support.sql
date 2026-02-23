-- V37: Extend comments table for portal project-level comments
-- Epic 155A — Portal contacts can post comments on projects

-- Add PROJECT to allowed entity types
ALTER TABLE comments DROP CONSTRAINT IF EXISTS chk_comment_entity_type;
ALTER TABLE comments ADD CONSTRAINT chk_comment_entity_type CHECK (entity_type IN ('TASK', 'DOCUMENT', 'PROJECT'));

-- Drop FK on author_member_id so portal contacts (not members) can author comments
ALTER TABLE comments DROP CONSTRAINT IF EXISTS comments_author_member_id_fkey;
