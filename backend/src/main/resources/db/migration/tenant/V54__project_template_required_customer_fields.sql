-- V54: Add required customer field IDs to project templates
-- Epic 243A: Engagement Prerequisites — Template Extension

-- 1. Add JSONB column for required customer field definition IDs
ALTER TABLE project_templates
    ADD COLUMN IF NOT EXISTS required_customer_field_ids JSONB NOT NULL DEFAULT '[]';

-- 2. Partial GIN index on non-empty arrays for efficient @> queries
CREATE INDEX IF NOT EXISTS idx_project_templates_required_customer_fields
    ON project_templates USING GIN (required_customer_field_ids)
    WHERE required_customer_field_ids != '[]'::jsonb;
