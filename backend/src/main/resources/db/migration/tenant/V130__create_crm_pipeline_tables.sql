-- ============================================================
-- V130__create_crm_pipeline_tables.sql  (Phase 80 — CRM / Sales Pipeline)
-- Per-tenant schema. No tenant_id column (pure schema-per-tenant), no RLS.
-- ============================================================

-- Pipeline stages (org-configurable, vertical-seeded)
CREATE TABLE IF NOT EXISTS pipeline_stages (
    id                       UUID         PRIMARY KEY,
    pipeline_id              UUID,                              -- reserved: always NULL in v1 (multi-pipeline-ready)
    name                     VARCHAR(80)  NOT NULL,
    position                 INTEGER      NOT NULL,
    default_probability_pct  INTEGER      NOT NULL DEFAULT 0
                                 CHECK (default_probability_pct BETWEEN 0 AND 100),
    stage_type               VARCHAR(10)  NOT NULL
                                 CHECK (stage_type IN ('OPEN','WON','LOST')),
    archived                 BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by               UUID,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Deals (opportunities) — always linked to a Customer
CREATE TABLE IF NOT EXISTS deals (
    id                   UUID          PRIMARY KEY,
    deal_number          VARCHAR(40)   NOT NULL,
    pipeline_id          UUID,                                 -- reserved: always NULL in v1
    customer_id          UUID          NOT NULL,               -- raw UUID ref (no FK, matches codebase convention)
    title                VARCHAR(200)  NOT NULL,
    stage_id             UUID          NOT NULL,
    status               VARCHAR(10)   NOT NULL DEFAULT 'OPEN'
                             CHECK (status IN ('OPEN','WON','LOST')),
    value_amount         NUMERIC(19,2) NOT NULL DEFAULT 0,
    value_currency       VARCHAR(3)    NOT NULL,
    probability_pct      INTEGER       CHECK (probability_pct BETWEEN 0 AND 100),  -- nullable override
    expected_close_date  DATE,
    owner_id             UUID          NOT NULL,
    source               VARCHAR(40),
    won_at               TIMESTAMPTZ,
    lost_at              TIMESTAMPTZ,
    lost_reason          VARCHAR(500),
    custom_fields        JSONB,
    applied_field_groups JSONB,
    created_by           UUID          NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_deal_stage FOREIGN KEY (stage_id) REFERENCES pipeline_stages (id),
    CONSTRAINT uq_deal_number UNIQUE (deal_number)
);

-- Deal-number counter (one row per tenant; mirrors proposal_counters from V51)
CREATE TABLE IF NOT EXISTS deal_counters (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    next_number INTEGER NOT NULL DEFAULT 1,
    singleton   BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_deal_counter_positive  CHECK (next_number > 0),
    CONSTRAINT deal_counters_singleton    UNIQUE (singleton),
    CONSTRAINT chk_deal_counter_singleton CHECK (singleton = TRUE)
);
INSERT INTO deal_counters (id, next_number)
VALUES (gen_random_uuid(), 1)
ON CONFLICT DO NOTHING;

-- The deal↔proposal link: FK column on the existing proposals table (one-deal-to-many-proposals).
ALTER TABLE proposals
    ADD COLUMN IF NOT EXISTS deal_id UUID;
-- (No NOT NULL: proposals authored outside the CRM flow have deal_id = NULL.)

-- Pack-status tracking column for deal-pipeline seeding idempotency (573B.5).
ALTER TABLE org_settings
    ADD COLUMN IF NOT EXISTS deal_pipeline_pack_status JSONB;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_deals_stage         ON deals (stage_id);
CREATE INDEX IF NOT EXISTS idx_deals_customer      ON deals (customer_id);
CREATE INDEX IF NOT EXISTS idx_deals_owner         ON deals (owner_id);
CREATE INDEX IF NOT EXISTS idx_deals_status        ON deals (status);
CREATE INDEX IF NOT EXISTS idx_deals_open_by_stage ON deals (stage_id) WHERE status = 'OPEN';
CREATE INDEX IF NOT EXISTS idx_deals_won_at        ON deals (won_at)  WHERE status = 'WON';
CREATE INDEX IF NOT EXISTS idx_deals_lost_at       ON deals (lost_at) WHERE status = 'LOST';
CREATE INDEX IF NOT EXISTS idx_proposals_deal      ON proposals (deal_id) WHERE deal_id IS NOT NULL;
