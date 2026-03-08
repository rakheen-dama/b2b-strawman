-- =============================================================================
-- V63: Billing Run Tables (Phase 40 -- Bulk Billing & Batch Operations)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. billing_runs -- batch billing context
-- -----------------------------------------------------------------------------
CREATE TABLE billing_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(300),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PREVIEW',
    period_from     DATE          NOT NULL,
    period_to       DATE          NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    include_expenses  BOOLEAN     NOT NULL DEFAULT true,
    include_retainers BOOLEAN     NOT NULL DEFAULT false,
    total_customers   INTEGER,
    total_invoices    INTEGER,
    total_amount      NUMERIC(14, 2),
    total_sent        INTEGER,
    total_failed      INTEGER,
    created_by      UUID          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_billing_runs_status ON billing_runs (status);
CREATE INDEX idx_billing_runs_created_by ON billing_runs (created_by);
CREATE INDEX idx_billing_runs_period ON billing_runs (period_from, period_to);

-- -----------------------------------------------------------------------------
-- 2. billing_run_items -- per-customer tracking within a billing run
-- -----------------------------------------------------------------------------
CREATE TABLE billing_run_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_run_id          UUID          NOT NULL REFERENCES billing_runs (id) ON DELETE CASCADE,
    customer_id             UUID          NOT NULL REFERENCES customers (id),
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    invoice_id              UUID          REFERENCES invoices (id),
    unbilled_time_amount    NUMERIC(14, 2),
    unbilled_expense_amount NUMERIC(14, 2),
    unbilled_time_count     INTEGER,
    unbilled_expense_count  INTEGER,
    failure_reason          VARCHAR(1000),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_billing_run_items_run_customer UNIQUE (billing_run_id, customer_id)
);

CREATE INDEX idx_billing_run_items_billing_run_id ON billing_run_items (billing_run_id);
CREATE INDEX idx_billing_run_items_customer_id ON billing_run_items (customer_id);
CREATE INDEX idx_billing_run_items_status ON billing_run_items (status);

-- -----------------------------------------------------------------------------
-- 3. billing_run_entry_selections -- cherry-pick tracking
-- -----------------------------------------------------------------------------
CREATE TABLE billing_run_entry_selections (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_run_item_id     UUID          NOT NULL REFERENCES billing_run_items (id) ON DELETE CASCADE,
    entry_type              VARCHAR(20)   NOT NULL,
    entry_id                UUID          NOT NULL,
    included                BOOLEAN       NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_billing_run_entry_selection UNIQUE (billing_run_item_id, entry_type, entry_id)
);

CREATE INDEX idx_billing_run_entry_selections_item_id ON billing_run_entry_selections (billing_run_item_id);

-- -----------------------------------------------------------------------------
-- 4. Extend invoices -- link to billing run
-- -----------------------------------------------------------------------------
ALTER TABLE invoices
    ADD COLUMN billing_run_id UUID REFERENCES billing_runs (id);

CREATE INDEX idx_invoices_billing_run_id ON invoices (billing_run_id);

-- -----------------------------------------------------------------------------
-- 5. Extend org_settings -- batch billing configuration
-- -----------------------------------------------------------------------------
ALTER TABLE org_settings
    ADD COLUMN billing_batch_async_threshold   INTEGER     NOT NULL DEFAULT 50,
    ADD COLUMN billing_email_rate_limit        INTEGER     NOT NULL DEFAULT 5,
    ADD COLUMN default_billing_run_currency    VARCHAR(3);
