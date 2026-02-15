-- V27: Add custom_fields and applied_field_groups JSONB columns to entity tables
-- Phase 11 — Epic 87C (Custom Field Values)

-- Projects
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS custom_fields JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS applied_field_groups JSONB;

CREATE INDEX IF NOT EXISTS idx_projects_custom_fields
    ON projects USING GIN (custom_fields);

-- Tasks
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS custom_fields JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS applied_field_groups JSONB;

CREATE INDEX IF NOT EXISTS idx_tasks_custom_fields
    ON tasks USING GIN (custom_fields);

-- Customers
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS custom_fields JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS applied_field_groups JSONB;

CREATE INDEX IF NOT EXISTS idx_customers_custom_fields
    ON customers USING GIN (custom_fields);
