-- =============================================================================
-- V66: Add onboarding dismissed timestamp to org_settings
-- =============================================================================

ALTER TABLE org_settings ADD COLUMN onboarding_dismissed_at TIMESTAMPTZ;
