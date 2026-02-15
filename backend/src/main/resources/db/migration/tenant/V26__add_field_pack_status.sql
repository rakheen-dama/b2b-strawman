-- V26: Add field_pack_status column to org_settings
-- Phase 11 — Field Pack Seeding (Epic 90A)

ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS field_pack_status JSONB;
