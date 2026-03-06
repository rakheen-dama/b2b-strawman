-- V56: Add request pack status and default reminder days to org_settings
-- Phase 34: Client Information Requests (Epic 252B)

ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS request_pack_status JSONB;
ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS default_request_reminder_days INTEGER DEFAULT 5;
