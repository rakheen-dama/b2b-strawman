-- V31__create_retainer_tables.sql
-- Phase 17: Retainer Agreements & Billing

-- 1. RetainerAgreement
CREATE TABLE IF NOT EXISTS retainer_agreements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL REFERENCES customers(id),
    schedule_id     UUID REFERENCES recurring_schedules(id),
    name            VARCHAR(300) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    frequency       VARCHAR(20) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE,
    allocated_hours DECIMAL(10,2),
    period_fee      DECIMAL(12,2),
    rollover_policy VARCHAR(20) NOT NULL DEFAULT 'FORFEIT',
    rollover_cap_hours DECIMAL(10,2),
    notes           TEXT,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_retainer_type CHECK (type IN ('HOUR_BANK', 'FIXED_FEE')),
    CONSTRAINT chk_retainer_status CHECK (status IN ('ACTIVE', 'PAUSED', 'TERMINATED')),
    CONSTRAINT chk_retainer_frequency CHECK (frequency IN (
        'WEEKLY', 'FORTNIGHTLY', 'MONTHLY', 'QUARTERLY', 'SEMI_ANNUALLY', 'ANNUALLY'
    )),
    CONSTRAINT chk_rollover_policy CHECK (rollover_policy IN ('FORFEIT', 'CARRY_FORWARD', 'CARRY_CAPPED'))
);

CREATE INDEX IF NOT EXISTS idx_retainer_agreements_customer_id ON retainer_agreements(customer_id);
CREATE INDEX IF NOT EXISTS idx_retainer_agreements_status ON retainer_agreements(status);

-- 2. RetainerPeriod
CREATE TABLE IF NOT EXISTS retainer_periods (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agreement_id         UUID NOT NULL REFERENCES retainer_agreements(id),
    period_start         DATE NOT NULL,
    period_end           DATE NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    allocated_hours      DECIMAL(10,2),
    base_allocated_hours DECIMAL(10,2),
    rollover_hours_in    DECIMAL(10,2) NOT NULL DEFAULT 0,
    consumed_hours       DECIMAL(10,2) NOT NULL DEFAULT 0,
    overage_hours        DECIMAL(10,2) NOT NULL DEFAULT 0,
    remaining_hours      DECIMAL(10,2) NOT NULL DEFAULT 0,
    rollover_hours_out   DECIMAL(10,2) NOT NULL DEFAULT 0,
    invoice_id           UUID REFERENCES invoices(id),
    closed_at            TIMESTAMPTZ,
    closed_by            UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_period_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT uq_retainer_period_start UNIQUE (agreement_id, period_start)
);

CREATE INDEX IF NOT EXISTS idx_retainer_periods_agreement_id ON retainer_periods(agreement_id);
CREATE INDEX IF NOT EXISTS idx_retainer_periods_status ON retainer_periods(status);
CREATE INDEX IF NOT EXISTS idx_retainer_periods_invoice_id ON retainer_periods(invoice_id);

-- 3. Add retainer_period_id to invoice_lines for traceability
ALTER TABLE invoice_lines
    ADD COLUMN IF NOT EXISTS retainer_period_id UUID REFERENCES retainer_periods(id);

CREATE INDEX IF NOT EXISTS idx_invoice_lines_retainer_period_id
    ON invoice_lines(retainer_period_id) WHERE retainer_period_id IS NOT NULL;
