-- V75: Add enabled_modules and terminology_namespace to org_settings
-- Supports vertical architecture: module gating and per-tenant terminology

ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS enabled_modules JSONB DEFAULT '[]'::jsonb;

ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS terminology_namespace VARCHAR(100);

-- GIN index on enabled_modules for containment queries (@> operator)
CREATE INDEX IF NOT EXISTS idx_org_settings_enabled_modules
    ON org_settings USING GIN (enabled_modules);
