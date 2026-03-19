-- V76: Data protection compliance foundation
-- Phase 50: jurisdiction config, ANONYMIZED lifecycle status, retention extensions,
-- processing register, DSAR extensions

-- ============================================================
-- 1. OrgSettings — data protection columns
-- ============================================================

ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS data_protection_jurisdiction VARCHAR(10),
    ADD COLUMN IF NOT EXISTS retention_policy_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS default_retention_months INTEGER,
    ADD COLUMN IF NOT EXISTS financial_retention_months INTEGER DEFAULT 60,
    ADD COLUMN IF NOT EXISTS information_officer_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS information_officer_email VARCHAR(255);

-- ============================================================
-- 2. Customer — ANONYMIZED lifecycle status
-- ============================================================

-- Drop and recreate the CHECK constraint to include ANONYMIZED
ALTER TABLE customers DROP CONSTRAINT IF EXISTS chk_customers_lifecycle_status;
ALTER TABLE customers ADD CONSTRAINT chk_customers_lifecycle_status
    CHECK (lifecycle_status IN (
        'PROSPECT', 'ONBOARDING', 'ACTIVE', 'DORMANT',
        'OFFBOARDING', 'OFFBOARDED', 'ANONYMIZED'
    ));

-- ============================================================
-- 3. RetentionPolicy — new columns (for later epics)
-- ============================================================

ALTER TABLE retention_policies
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS last_evaluated_at TIMESTAMP WITH TIME ZONE;

-- ============================================================
-- 4. DataSubjectRequest — new columns (for later epics)
-- ============================================================

ALTER TABLE data_subject_requests
    ADD COLUMN IF NOT EXISTS subject_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS subject_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resolution_notes TEXT;

-- ============================================================
-- 5. ProcessingActivity — new table (for later epics)
-- ============================================================

CREATE TABLE IF NOT EXISTS processing_activities (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category          VARCHAR(100) NOT NULL,
    description       TEXT NOT NULL,
    legal_basis       VARCHAR(50) NOT NULL,
    data_subjects     VARCHAR(255) NOT NULL,
    retention_period  VARCHAR(100) NOT NULL,
    recipients        VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Index: list by category for settings UI
CREATE INDEX IF NOT EXISTS idx_processing_activities_category
    ON processing_activities (category);
