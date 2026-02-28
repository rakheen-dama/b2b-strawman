-- ============================================================
-- V49: Swap TEXT content/body columns to JSONB in document_templates and clauses
-- Phase 31 -- Document System Redesign (Epic 211A)
--
-- V48 added content_json/body_json JSONB columns alongside the original TEXT columns.
-- This migration performs the column swap: drops the old TEXT columns and renames
-- the JSONB columns to content/body so they become the primary columns.
--
-- PREREQUISITE: V48 must have been applied first (adds content_json, body_json,
-- legacy_content, legacy_body columns and populates them).
-- ============================================================

-- document_templates: drop old TEXT content column, rename content_json -> content
ALTER TABLE document_templates DROP COLUMN IF EXISTS content;
ALTER TABLE document_templates RENAME COLUMN content_json TO content;

-- clauses: drop old TEXT body column, rename body_json -> body
ALTER TABLE clauses DROP COLUMN IF EXISTS body;
ALTER TABLE clauses RENAME COLUMN body_json TO body;

-- NOTE: We cannot add a blanket NOT NULL constraint here because PLATFORM templates
-- and SYSTEM clauses will have NULL content/body until the pack seeders re-run.
-- The entity annotation uses nullable=false but Hibernate uses the Java-side validation,
-- not the database constraint for these rows.
