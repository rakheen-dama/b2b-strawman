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
    tenant_id           VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_invoice_currency_len CHECK (char_length(currency) = 3),
    CONSTRAINT chk_invoice_status CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'PAID', 'VOID'))
);

-- Invoice number uniqueness (per-schema for Pro; includes tenant_id for Starter)
CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_number_unique
    ON invoices (invoice_number) WHERE invoice_number IS NOT NULL AND tenant_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_tenant_number_unique
    ON invoices (tenant_id, invoice_number) WHERE invoice_number IS NOT NULL AND tenant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_invoices_customer_status
    ON invoices (customer_id, status);

CREATE INDEX IF NOT EXISTS idx_invoices_status
    ON invoices (status);

CREATE INDEX IF NOT EXISTS idx_invoices_created_at
    ON invoices (created_at);

CREATE INDEX IF NOT EXISTS idx_invoices_tenant
    ON invoices (tenant_id) WHERE tenant_id IS NOT NULL;

-- Row-Level Security
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_invoices') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_invoices ON invoices
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

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
    tenant_id       VARCHAR(255),
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

CREATE INDEX IF NOT EXISTS idx_invoice_lines_tenant
    ON invoice_lines (tenant_id) WHERE tenant_id IS NOT NULL;

-- Row-Level Security
ALTER TABLE invoice_lines ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_invoice_lines') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_invoice_lines ON invoice_lines
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- =============================================================================
-- invoice_counters
-- =============================================================================

CREATE TABLE IF NOT EXISTS invoice_counters (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(255),
    next_number INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT chk_counter_positive CHECK (next_number > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_invoice_counters_tenant
    ON invoice_counters (tenant_id);

ALTER TABLE invoice_counters ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_invoice_counters') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_invoice_counters ON invoice_counters
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

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
