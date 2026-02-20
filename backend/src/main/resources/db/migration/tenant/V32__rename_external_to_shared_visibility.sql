-- V32: Rename EXTERNAL visibility to SHARED in comments
-- Fix: frontend already uses SHARED but backend/DB still referenced EXTERNAL

-- Update existing rows
UPDATE comments SET visibility = 'SHARED' WHERE visibility = 'EXTERNAL';

-- Replace the check constraint
ALTER TABLE comments DROP CONSTRAINT IF EXISTS chk_comment_visibility;
ALTER TABLE comments ADD CONSTRAINT chk_comment_visibility CHECK (visibility IN ('INTERNAL', 'SHARED'));
