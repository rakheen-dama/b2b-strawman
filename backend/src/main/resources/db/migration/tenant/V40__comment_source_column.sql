-- Add source column to distinguish internal vs portal-originated comments
ALTER TABLE comments ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';

-- Backfill existing portal comments (created by PortalCommentService with entity_type = 'PROJECT')
UPDATE comments SET source = 'PORTAL' WHERE entity_type = 'PROJECT';
