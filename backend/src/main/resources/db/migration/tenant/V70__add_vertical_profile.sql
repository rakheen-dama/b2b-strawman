-- =============================================================================
-- V70: Add vertical_profile column to org_settings
-- Stores the vertical profile slug (e.g., "accounting-za") for pack filtering
-- =============================================================================

ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS vertical_profile VARCHAR(50);
