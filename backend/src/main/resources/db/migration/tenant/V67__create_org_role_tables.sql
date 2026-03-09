-- =============================================================================
-- V67: OrgRole Tables (Phase 42 -- Custom Roles & Capabilities)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. org_roles -- role definitions per tenant
-- -----------------------------------------------------------------------------
CREATE TABLE org_roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100)  NOT NULL,
    slug        VARCHAR(100)  NOT NULL,
    description VARCHAR(500),
    is_system   BOOLEAN       NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_roles_slug UNIQUE (slug)
);

CREATE INDEX idx_org_roles_slug ON org_roles (slug);
CREATE INDEX idx_org_roles_is_system ON org_roles (is_system);

-- -----------------------------------------------------------------------------
-- 2. org_role_capabilities -- capabilities assigned to a role
-- -----------------------------------------------------------------------------
CREATE TABLE org_role_capabilities (
    org_role_id UUID        NOT NULL REFERENCES org_roles (id) ON DELETE CASCADE,
    capability  VARCHAR(50) NOT NULL,
    PRIMARY KEY (org_role_id, capability)
);

-- -----------------------------------------------------------------------------
-- 3. member_capability_overrides -- per-member capability overrides (+CAP / -CAP)
-- -----------------------------------------------------------------------------
CREATE TABLE member_capability_overrides (
    member_id      UUID        NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    override_value VARCHAR(60) NOT NULL,
    PRIMARY KEY (member_id, override_value)
);

-- -----------------------------------------------------------------------------
-- 4. Add org_role_id FK to members (nullable initially for backfill)
-- -----------------------------------------------------------------------------
ALTER TABLE members ADD COLUMN org_role_id UUID REFERENCES org_roles (id);

-- -----------------------------------------------------------------------------
-- 5. Seed system roles
-- -----------------------------------------------------------------------------
INSERT INTO org_roles (id, name, slug, description, is_system)
VALUES
    (gen_random_uuid(), 'Owner',  'owner',  'Full access to all capabilities', true),
    (gen_random_uuid(), 'Admin',  'admin',  'Administrative access to all capabilities', true),
    (gen_random_uuid(), 'Member', 'member', 'Basic member with no default capabilities', true);

-- Owner capabilities (all 7)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, cap
FROM org_roles, unnest(ARRAY[
    'FINANCIAL_VISIBILITY', 'INVOICING', 'PROJECT_MANAGEMENT',
    'TEAM_OVERSIGHT', 'CUSTOMER_MANAGEMENT', 'AUTOMATIONS', 'RESOURCE_PLANNING'
]) AS cap
WHERE slug = 'owner';

-- Admin capabilities (all 7)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, cap
FROM org_roles, unnest(ARRAY[
    'FINANCIAL_VISIBILITY', 'INVOICING', 'PROJECT_MANAGEMENT',
    'TEAM_OVERSIGHT', 'CUSTOMER_MANAGEMENT', 'AUTOMATIONS', 'RESOURCE_PLANNING'
]) AS cap
WHERE slug = 'admin';

-- Member: no capabilities (empty set)

-- -----------------------------------------------------------------------------
-- 6. Backfill existing members with matching org_role_id
-- -----------------------------------------------------------------------------
UPDATE members SET org_role_id = (SELECT id FROM org_roles WHERE slug = 'owner')
WHERE org_role = 'owner';

UPDATE members SET org_role_id = (SELECT id FROM org_roles WHERE slug = 'admin')
WHERE org_role = 'admin';

UPDATE members SET org_role_id = (SELECT id FROM org_roles WHERE slug = 'member')
WHERE org_role NOT IN ('owner', 'admin');

-- NOTE: org_role_id intentionally remains nullable. Epic 313A will extend
-- MemberFilter to assign a default role on member creation, after which
-- a follow-up migration can add the NOT NULL constraint.

CREATE INDEX idx_members_org_role_id ON members (org_role_id);
