-- V81__regulatory_deadlines.sql
-- Epic 381A: Foundation for Phase 51 regulatory deadlines
-- Creates filing_statuses table, extends recurring_schedules and org_settings

-- ============================================================
-- 1. filing_statuses — tracks user-entered filing status per deadline
-- ============================================================

CREATE TABLE IF NOT EXISTS filing_statuses (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id          UUID         NOT NULL,
    deadline_type_slug   VARCHAR(50)  NOT NULL,
    period_key           VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    filed_at             TIMESTAMP WITH TIME ZONE,
    filed_by             UUID,
    notes                TEXT,
    linked_project_id    UUID,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_filing_status_customer_deadline_period
        UNIQUE (customer_id, deadline_type_slug, period_key),
    CONSTRAINT chk_filing_status_status
        CHECK (status IN ('filed', 'not_applicable'))
);

CREATE INDEX IF NOT EXISTS idx_filing_statuses_customer_id
    ON filing_statuses (customer_id);

CREATE INDEX IF NOT EXISTS idx_filing_statuses_customer_slug
    ON filing_statuses (customer_id, deadline_type_slug);

CREATE INDEX IF NOT EXISTS idx_filing_statuses_status
    ON filing_statuses (status);

-- ============================================================
-- 2. recurring_schedules — post-create actions column
-- ============================================================

ALTER TABLE recurring_schedules
    ADD COLUMN IF NOT EXISTS post_create_actions JSONB;

-- ============================================================
-- 3. org_settings — pack tracking columns
-- ============================================================

ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS rate_pack_status JSONB,
    ADD COLUMN IF NOT EXISTS schedule_pack_status JSONB;
