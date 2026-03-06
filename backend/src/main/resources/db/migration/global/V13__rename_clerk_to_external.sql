-- V13: Rename clerk_org_id to external_org_id in org_schema_mapping and organizations
-- Decouples the schema from Clerk-specific naming to support multiple auth providers.

-- org_schema_mapping
ALTER TABLE org_schema_mapping
    RENAME COLUMN clerk_org_id TO external_org_id;

ALTER INDEX IF EXISTS idx_org_schema_mapping_clerk_org_id
    RENAME TO idx_org_schema_mapping_external_org_id;

-- organizations
ALTER TABLE organizations
    RENAME COLUMN clerk_org_id TO external_org_id;

ALTER INDEX IF EXISTS idx_organizations_clerk_org_id
    RENAME TO idx_organizations_external_org_id;
