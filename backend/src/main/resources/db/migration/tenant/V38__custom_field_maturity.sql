-- =============================================================================
-- V38: Custom Field Maturity & Data Integrity
-- Phase 23 -- adds auto-apply, dependencies, conditional visibility,
--            invoice custom fields, and template required fields
-- =============================================================================

-- 1. FieldGroup: add auto_apply flag
ALTER TABLE field_groups
    ADD COLUMN auto_apply BOOLEAN NOT NULL DEFAULT false;

-- 2. FieldGroup: add depends_on (list of group UUIDs)
ALTER TABLE field_groups
    ADD COLUMN depends_on JSONB;

-- 3. FieldDefinition: add visibility_condition
ALTER TABLE field_definitions
    ADD COLUMN visibility_condition JSONB;

-- 4. DocumentTemplate: add required_context_fields
ALTER TABLE document_templates
    ADD COLUMN required_context_fields JSONB;

-- 5. Invoice: add custom_fields JSONB
ALTER TABLE invoices
    ADD COLUMN custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

-- 6. Invoice: add applied_field_groups JSONB
ALTER TABLE invoices
    ADD COLUMN applied_field_groups JSONB NOT NULL DEFAULT '[]'::jsonb;

-- 7. GIN index on invoices.custom_fields for JSONB containment queries
CREATE INDEX idx_invoices_custom_fields ON invoices USING GIN (custom_fields);

-- 8. Index on field_groups.auto_apply for efficient lookup during entity creation
CREATE INDEX idx_field_groups_auto_apply ON field_groups (entity_type, auto_apply) WHERE auto_apply = true AND active = true;
