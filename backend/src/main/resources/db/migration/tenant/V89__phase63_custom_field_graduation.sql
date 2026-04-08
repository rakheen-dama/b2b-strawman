-- V89__phase63_custom_field_graduation.sql
-- Phase 63: Promote ~21 custom fields to structural columns across 4 entities.
-- All columns nullable -- existing entities are unaffected.
-- No data backfill from JSONB.

-- ============================================================
-- Customer: 13 new columns
-- ============================================================
ALTER TABLE customers ADD COLUMN IF NOT EXISTS registration_number VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(255);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(255);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS state_province VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS postal_code VARCHAR(20);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS country VARCHAR(2);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS tax_number VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS contact_name VARCHAR(255);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS entity_type VARCHAR(30);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS financial_year_end DATE;

CREATE INDEX IF NOT EXISTS idx_customers_registration_number ON customers(registration_number);
CREATE INDEX IF NOT EXISTS idx_customers_tax_number ON customers(tax_number);
CREATE INDEX IF NOT EXISTS idx_customers_entity_type ON customers(entity_type);

-- ============================================================
-- Project: 3 new columns
-- ============================================================
ALTER TABLE projects ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS priority VARCHAR(20);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS work_type VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_projects_work_type ON projects(work_type);

-- ============================================================
-- Task: 1 new column
-- ============================================================
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS estimated_hours DECIMAL(8,2);

-- ============================================================
-- Invoice: 4 new columns
-- ============================================================
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS po_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tax_type VARCHAR(20);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS billing_period_start DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS billing_period_end DATE;
