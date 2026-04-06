-- ============================================================
-- V88__add_project_template_pack_status.sql
-- Add project template pack status tracking to org_settings
-- ============================================================

ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS project_template_pack_status JSONB;
