-- V5: Drop UNIQUE constraint on org_schema_mapping.schema_name
-- Originally added to allow multiple Starter-tier orgs to share "tenant_shared".
-- Retained as a no-op because all orgs now get dedicated tenant_<hash> schemas
-- and the constraint was already dropped in earlier environments.
-- The clerk_org_id UNIQUE constraint remains (one mapping per org).
ALTER TABLE org_schema_mapping DROP CONSTRAINT IF EXISTS org_schema_mapping_schema_name_key;
