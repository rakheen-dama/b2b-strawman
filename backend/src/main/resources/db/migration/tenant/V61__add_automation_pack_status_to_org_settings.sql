-- V61: Add automation_pack_status to org_settings for template seeder idempotency
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS automation_pack_status JSONB;
