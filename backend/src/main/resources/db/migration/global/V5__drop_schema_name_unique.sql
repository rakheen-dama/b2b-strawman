-- V5: Drop UNIQUE constraint on org_schema_mapping.schema_name
-- Multiple Starter-tier orgs now map to the same "tenant_shared" schema.
-- The clerk_org_id UNIQUE constraint remains (one mapping per org).
ALTER TABLE org_schema_mapping DROP CONSTRAINT IF EXISTS org_schema_mapping_schema_name_key;
