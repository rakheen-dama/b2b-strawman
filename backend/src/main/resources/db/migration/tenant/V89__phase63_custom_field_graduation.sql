-- V89__phase63_custom_field_graduation.sql
-- Phase 63: Promote ~21 custom fields to structural columns across 4 entities.
-- All columns nullable -- existing entities are unaffected.
-- No data backfill from JSONB.

-- ============================================================
-- Customer: 13 new columns
-- ============================================================
ALTER TABLE customers ADD COLUMN registration_number VARCHAR(100);
ALTER TABLE customers ADD COLUMN address_line1 VARCHAR(255);
ALTER TABLE customers ADD COLUMN address_line2 VARCHAR(255);
ALTER TABLE customers ADD COLUMN city VARCHAR(100);
ALTER TABLE customers ADD COLUMN state_province VARCHAR(100);
ALTER TABLE customers ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE customers ADD COLUMN country VARCHAR(2);
ALTER TABLE customers ADD COLUMN tax_number VARCHAR(100);
ALTER TABLE customers ADD COLUMN contact_name VARCHAR(255);
ALTER TABLE customers ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE customers ADD COLUMN contact_phone VARCHAR(50);
ALTER TABLE customers ADD COLUMN entity_type VARCHAR(30);
ALTER TABLE customers ADD COLUMN financial_year_end DATE;

CREATE INDEX idx_customers_registration_number ON customers(registration_number);
CREATE INDEX idx_customers_tax_number ON customers(tax_number);
CREATE INDEX idx_customers_entity_type ON customers(entity_type);

-- ============================================================
-- Project: 3 new columns
-- ============================================================
ALTER TABLE projects ADD COLUMN reference_number VARCHAR(100);
ALTER TABLE projects ADD COLUMN priority VARCHAR(20);
ALTER TABLE projects ADD COLUMN work_type VARCHAR(50);

CREATE INDEX idx_projects_work_type ON projects(work_type);

-- ============================================================
-- Task: 1 new column
-- ============================================================
ALTER TABLE tasks ADD COLUMN estimated_hours DECIMAL(8,2);

-- ============================================================
-- Invoice: 4 new columns
-- ============================================================
ALTER TABLE invoices ADD COLUMN po_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN tax_type VARCHAR(20);
ALTER TABLE invoices ADD COLUMN billing_period_start DATE;
ALTER TABLE invoices ADD COLUMN billing_period_end DATE;
