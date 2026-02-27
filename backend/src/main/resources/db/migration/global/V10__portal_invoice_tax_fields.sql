-- Add tax breakdown and configuration fields to portal invoice read-model

ALTER TABLE portal.portal_invoices
    ADD COLUMN IF NOT EXISTS tax_breakdown_json TEXT,
    ADD COLUMN IF NOT EXISTS tax_registration_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS tax_registration_label VARCHAR(30),
    ADD COLUMN IF NOT EXISTS tax_label VARCHAR(20),
    ADD COLUMN IF NOT EXISTS tax_inclusive BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS has_per_line_tax BOOLEAN NOT NULL DEFAULT false;

-- Add tax fields to portal invoice lines for per-line tax display
ALTER TABLE portal.portal_invoice_lines
    ADD COLUMN IF NOT EXISTS tax_rate_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tax_rate_percent DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(14,2),
    ADD COLUMN IF NOT EXISTS tax_exempt BOOLEAN NOT NULL DEFAULT false;
