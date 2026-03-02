-- V13: Rename clerk_org_id to external_org_id for auth provider abstraction

-- org_schema_mapping table
ALTER TABLE org_schema_mapping RENAME COLUMN clerk_org_id TO external_org_id;
DROP INDEX IF EXISTS idx_org_schema_mapping_clerk_org_id;
CREATE INDEX IF NOT EXISTS idx_org_schema_mapping_external_org_id
    ON org_schema_mapping (external_org_id);

-- organizations table
ALTER TABLE organizations RENAME COLUMN clerk_org_id TO external_org_id;
DROP INDEX IF EXISTS idx_organizations_clerk_org_id;
CREATE INDEX IF NOT EXISTS idx_organizations_external_org_id
    ON organizations (external_org_id);
