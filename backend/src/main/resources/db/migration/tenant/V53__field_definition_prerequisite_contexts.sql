-- V53: Extend field_definitions with prerequisite contexts
-- Phase 33: Data Completeness & Prerequisite Enforcement

-- 1. Add requiredForContexts to field_definitions
ALTER TABLE field_definitions
    ADD COLUMN required_for_contexts JSONB NOT NULL DEFAULT '[]';

-- 2. Index for querying field definitions by context
-- GIN index on JSONB array enables efficient @> (contains) queries
CREATE INDEX IF NOT EXISTS idx_field_definitions_required_contexts
    ON field_definitions USING GIN (required_for_contexts);

-- 3. Seed default contexts for existing common-customer pack fields.
-- These UPDATE statements set defaults for fields that were seeded by FieldPackSeeder.
-- Only updates fields with pack_id = 'common-customer' that still have empty contexts.

UPDATE field_definitions
SET required_for_contexts = '["INVOICE_GENERATION", "PROPOSAL_SEND"]'::jsonb
WHERE pack_id = 'common-customer'
  AND slug = 'address_line1'
  AND required_for_contexts = '[]'::jsonb;

UPDATE field_definitions
SET required_for_contexts = '["INVOICE_GENERATION"]'::jsonb
WHERE pack_id = 'common-customer'
  AND slug IN ('city', 'country', 'tax_number')
  AND required_for_contexts = '[]'::jsonb;
