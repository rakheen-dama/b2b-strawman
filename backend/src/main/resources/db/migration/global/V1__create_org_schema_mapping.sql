CREATE TABLE IF NOT EXISTS org_schema_mapping (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_org_id  VARCHAR(255) NOT NULL UNIQUE,
    schema_name   VARCHAR(255) NOT NULL UNIQUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_org_schema_mapping_clerk_org_id
    ON org_schema_mapping (clerk_org_id);
