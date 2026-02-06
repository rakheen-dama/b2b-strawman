-- Global schema tables (public schema)
-- These tables exist once, shared across all tenants.

CREATE TABLE IF NOT EXISTS organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_org_id    VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    provisioning_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_organizations_clerk_org_id ON organizations (clerk_org_id);

CREATE TABLE IF NOT EXISTS org_schema_mapping (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_org_id    VARCHAR(255) NOT NULL UNIQUE,
    schema_name     VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_org_schema_mapping_org
        FOREIGN KEY (clerk_org_id) REFERENCES organizations (clerk_org_id)
);

CREATE TABLE IF NOT EXISTS processed_webhooks (
    svix_id         VARCHAR(255) PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    processed_at    TIMESTAMP NOT NULL DEFAULT now()
);
