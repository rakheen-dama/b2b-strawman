-- V23: Create invoices, invoice_lines, invoice_counters tables
-- Phase 10 — Invoicing & Billing from Time

-- =============================================================================
-- invoices
-- =============================================================================

CREATE TABLE IF NOT EXISTS invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES customers(id),
    invoice_number      VARCHAR(20),
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    currency            VARCHAR(3) NOT NULL,
    issue_date          DATE,
    due_date            DATE,
    subtotal            DECIMAL(14,2) NOT NULL DEFAULT 0,
    tax_amount          DECIMAL(14,2) NOT NULL DEFAULT 0,
    total               DECIMAL(14,2) NOT NULL DEFAULT 0,
    notes               TEXT,
    payment_terms       VARCHAR(100),
    payment_reference   VARCHAR(255),
    paid_at             TIMESTAMP WITH TIME ZONE,
    customer_name       VARCHAR(255) NOT NULL,
    customer_email      VARCHAR(255),
    customer_address    TEXT,
    org_name            VARCHAR(255) NOT NULL,
    created_by          UUID NOT NULL REFERENCES members(id),
    approved_by         UUID REFERENCES members(id),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_invoice_currency_len CHECK (char_length(currency) = 3),
    CONSTRAINT chk_invoice_status CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'PAID', 'VOID'))
);

-- Invoice number uniqueness (per-schema)
CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_number_unique
    ON invoices (invoice_number) WHERE invoice_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_invoices_customer_status
    ON invoices (customer_id, status);

CREATE INDEX IF NOT EXISTS idx_invoices_status
    ON invoices (status);

CREATE INDEX IF NOT EXISTS idx_invoices_created_at
    ON invoices (created_at);

-- =============================================================================
-- invoice_lines
-- =============================================================================

CREATE TABLE IF NOT EXISTS invoice_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id      UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    project_id      UUID REFERENCES projects(id),
    time_entry_id   UUID REFERENCES time_entries(id),
    description     TEXT NOT NULL,
    quantity        DECIMAL(10,4) NOT NULL,
    unit_price      DECIMAL(12,2) NOT NULL,
    amount          DECIMAL(14,2) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_invoice_line_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_invoice_line_unit_price_non_negative CHECK (unit_price >= 0)
);

-- Double-billing prevention: each time entry can appear on at most one invoice
CREATE UNIQUE INDEX IF NOT EXISTS idx_invoice_lines_time_entry_unique
    ON invoice_lines (time_entry_id) WHERE time_entry_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_invoice_lines_invoice_sort
    ON invoice_lines (invoice_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_invoice_lines_project
    ON invoice_lines (project_id) WHERE project_id IS NOT NULL;

-- =============================================================================
-- invoice_counters
-- =============================================================================

CREATE TABLE IF NOT EXISTS invoice_counters (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    next_number INTEGER NOT NULL DEFAULT 1,
    singleton   BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_counter_positive CHECK (next_number > 0),
    CONSTRAINT invoice_counters_singleton UNIQUE (singleton),
    CONSTRAINT chk_singleton CHECK (singleton = TRUE)
);

-- =============================================================================
-- TimeEntry: add invoice_id column
-- =============================================================================

ALTER TABLE time_entries
    ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id);

CREATE INDEX IF NOT EXISTS idx_time_entries_invoice_id
    ON time_entries (invoice_id) WHERE invoice_id IS NOT NULL;

-- Composite index for unbilled time queries:
-- "billable = true AND invoice_id IS NULL" is the primary unbilled filter
CREATE INDEX IF NOT EXISTS idx_time_entries_unbilled
    ON time_entries (task_id, date) WHERE billable = true AND invoice_id IS NULL;
