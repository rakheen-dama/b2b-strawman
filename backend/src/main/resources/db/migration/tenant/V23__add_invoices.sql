-- V23: Create invoices, invoice_lines, invoice_number_seq tables
-- Phase 9 — Invoicing & Billing

-- =============================================================================
-- invoices
-- =============================================================================

CREATE TABLE IF NOT EXISTS invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES customers(id),
    invoice_number      VARCHAR(50),
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
    created_by          UUID NOT NULL,
    approved_by         UUID,
    tenant_id           VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_invoices_status CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'PAID', 'VOID')),
    CONSTRAINT chk_invoices_currency_len CHECK (char_length(currency) = 3)
);

CREATE INDEX IF NOT EXISTS idx_invoices_customer_status
    ON invoices (customer_id, status);

CREATE INDEX IF NOT EXISTS idx_invoices_status
    ON invoices (status);

CREATE INDEX IF NOT EXISTS idx_invoices_created_at
    ON invoices (created_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_tenant_number
    ON invoices (tenant_id, invoice_number) WHERE invoice_number IS NOT NULL;

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
    invoice_id      UUID NOT NULL REFERENCES invoices(id),
    project_id      UUID REFERENCES projects(id),
    time_entry_id   UUID REFERENCES time_entries(id),
    description     TEXT NOT NULL,
    quantity        DECIMAL(10,4) NOT NULL,
    unit_price      DECIMAL(12,2) NOT NULL,
    amount          DECIMAL(14,2) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invoice_lines_invoice_sort
    ON invoice_lines (invoice_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_invoice_lines_project
    ON invoice_lines (project_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_invoice_lines_time_entry_unique
    ON invoice_lines (time_entry_id) WHERE time_entry_id IS NOT NULL;

ALTER TABLE invoice_lines ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_invoice_lines') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_invoice_lines ON invoice_lines
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- =============================================================================
-- invoice_number_seq
-- =============================================================================

CREATE TABLE IF NOT EXISTS invoice_number_seq (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(255) UNIQUE,
    next_value  INTEGER NOT NULL DEFAULT 1
);

ALTER TABLE invoice_number_seq ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_invoice_number_seq') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_invoice_number_seq ON invoice_number_seq
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- =============================================================================
-- ALTER existing tables
-- =============================================================================

ALTER TABLE customers ADD COLUMN IF NOT EXISTS address TEXT;

ALTER TABLE time_entries ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id);

CREATE INDEX IF NOT EXISTS idx_time_entries_invoice
    ON time_entries (invoice_id);
