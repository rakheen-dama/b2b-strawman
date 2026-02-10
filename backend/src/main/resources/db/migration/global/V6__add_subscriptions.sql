CREATE TABLE IF NOT EXISTS subscriptions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID NOT NULL UNIQUE REFERENCES organizations(id),
    plan_slug             VARCHAR(100) NOT NULL DEFAULT 'starter',
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_period_start  TIMESTAMP WITH TIME ZONE,
    current_period_end    TIMESTAMP WITH TIME ZONE,
    cancelled_at          TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_organization_id
    ON subscriptions (organization_id);

-- Seed existing organizations with a STARTER subscription
INSERT INTO subscriptions (organization_id, plan_slug, status)
SELECT id, COALESCE(plan_slug, 'starter'), 'ACTIVE'
FROM organizations
ON CONFLICT DO NOTHING;
