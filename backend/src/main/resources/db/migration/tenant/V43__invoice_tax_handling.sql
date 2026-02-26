-- V43__invoice_tax_handling.sql
-- Phase 26: Add tax rate entity, per-line tax fields, org tax settings

-- 1. OrgSettings tax configuration fields
ALTER TABLE org_settings ADD COLUMN tax_registration_number VARCHAR(50);
ALTER TABLE org_settings ADD COLUMN tax_registration_label VARCHAR(30) DEFAULT 'Tax Number';
ALTER TABLE org_settings ADD COLUMN tax_label VARCHAR(20) DEFAULT 'Tax';
ALTER TABLE org_settings ADD COLUMN tax_inclusive BOOLEAN NOT NULL DEFAULT false;

-- 2. TaxRate entity
CREATE TABLE tax_rates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    rate            DECIMAL(5,2) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    is_exempt       BOOLEAN NOT NULL DEFAULT false,
    active          BOOLEAN NOT NULL DEFAULT true,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_tax_rate_range CHECK (rate >= 0 AND rate <= 99.99),
    CONSTRAINT chk_exempt_zero CHECK (NOT is_exempt OR rate = 0)
);

CREATE INDEX idx_tax_rates_active_sort ON tax_rates (active, sort_order);
CREATE UNIQUE INDEX idx_tax_rates_single_default ON tax_rates ((1)) WHERE is_default = true;

-- 3. InvoiceLine tax fields
ALTER TABLE invoice_lines ADD COLUMN tax_rate_id UUID REFERENCES tax_rates(id);
ALTER TABLE invoice_lines ADD COLUMN tax_rate_name VARCHAR(100);
ALTER TABLE invoice_lines ADD COLUMN tax_rate_percent DECIMAL(5,2);
ALTER TABLE invoice_lines ADD COLUMN tax_amount DECIMAL(14,2);
ALTER TABLE invoice_lines ADD COLUMN tax_exempt BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_invoice_lines_tax_rate ON invoice_lines (tax_rate_id);

-- 4. Seed default tax rates
INSERT INTO tax_rates (name, rate, is_default, is_exempt, active, sort_order)
VALUES
    ('Standard', 15.00, true, false, true, 0),
    ('Zero-rated', 0.00, false, false, true, 1),
    ('Exempt', 0.00, false, true, true, 2);
