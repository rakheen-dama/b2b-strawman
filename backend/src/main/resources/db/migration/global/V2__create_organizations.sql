CREATE TABLE IF NOT EXISTS organizations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_org_id          VARCHAR(255) NOT NULL UNIQUE,
    name                  VARCHAR(255) NOT NULL,
    provisioning_status   VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_organizations_clerk_org_id
    ON organizations (clerk_org_id);

CREATE INDEX IF NOT EXISTS idx_organizations_provisioning_status
    ON organizations (provisioning_status);
