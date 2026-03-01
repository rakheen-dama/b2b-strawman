-- V51__create_proposal_tables.sql
-- Phase 32: Proposal → Engagement Pipeline

-- Proposal counter (one row per tenant, follows InvoiceCounter pattern)
CREATE TABLE IF NOT EXISTS proposal_counters (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    next_number INTEGER NOT NULL DEFAULT 1,
    singleton   BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_proposal_counter_positive CHECK (next_number > 0),
    CONSTRAINT proposal_counters_singleton UNIQUE (singleton),
    CONSTRAINT chk_proposal_counter_singleton CHECK (singleton = TRUE)
);

-- Seed the counter with a single row (idempotent)
INSERT INTO proposal_counters (id, next_number)
VALUES (gen_random_uuid(), 1)
ON CONFLICT DO NOTHING;

-- Proposal entity
CREATE TABLE IF NOT EXISTS proposals (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_number         VARCHAR(20) NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    customer_id             UUID NOT NULL REFERENCES customers(id),
    portal_contact_id       UUID REFERENCES portal_contacts(id) ON DELETE SET NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    fee_model               VARCHAR(20) NOT NULL,

    -- Fee configuration (varies by fee_model)
    fixed_fee_amount        NUMERIC(12,2),
    fixed_fee_currency      VARCHAR(3),
    hourly_rate_note        VARCHAR(500),
    retainer_amount         NUMERIC(12,2),
    retainer_currency       VARCHAR(3),
    retainer_hours_included NUMERIC(6,1),

    -- Document content (Tiptap JSON)
    content_json            JSONB NOT NULL DEFAULT '{}',

    -- Orchestration references
    project_template_id     UUID,

    -- Lifecycle timestamps
    sent_at                 TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    accepted_at             TIMESTAMPTZ,
    declined_at             TIMESTAMPTZ,
    decline_reason          VARCHAR(500),

    -- Result references (set after acceptance orchestration)
    created_project_id      UUID,
    created_retainer_id     UUID,

    -- Metadata
    created_by_id           UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT proposals_status_check
        CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    CONSTRAINT proposals_fee_model_check
        CHECK (fee_model IN ('FIXED', 'HOURLY', 'RETAINER')),
    CONSTRAINT proposals_number_unique UNIQUE (proposal_number)
);

-- Primary query patterns: list by customer, filter by status, filter by creator
CREATE INDEX IF NOT EXISTS idx_proposals_customer_id ON proposals(customer_id);
CREATE INDEX IF NOT EXISTS idx_proposals_status ON proposals(status);
CREATE INDEX IF NOT EXISTS idx_proposals_created_by ON proposals(created_by_id);

-- Expiry processor: find SENT proposals past their expiry date
CREATE INDEX IF NOT EXISTS idx_proposals_expires_at ON proposals(expires_at)
    WHERE status = 'SENT' AND expires_at IS NOT NULL;

-- Proposal milestones (fixed-fee milestone billing schedule)
CREATE TABLE IF NOT EXISTS proposal_milestones (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id       UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    description       VARCHAR(200) NOT NULL,
    percentage        NUMERIC(5,2) NOT NULL,
    relative_due_days INTEGER NOT NULL DEFAULT 0,
    sort_order        INTEGER NOT NULL DEFAULT 0,
    invoice_id        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT proposal_milestones_percentage_check CHECK (percentage > 0 AND percentage <= 100)
);

-- Load milestones by proposal
CREATE INDEX IF NOT EXISTS idx_proposal_milestones_proposal ON proposal_milestones(proposal_id);

-- Proposal team members (assigned to project on acceptance)
CREATE TABLE IF NOT EXISTS proposal_team_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    member_id   UUID NOT NULL,
    role        VARCHAR(100),
    sort_order  INTEGER NOT NULL DEFAULT 0
);

-- Load team by proposal
CREATE INDEX IF NOT EXISTS idx_proposal_team_proposal ON proposal_team_members(proposal_id);
