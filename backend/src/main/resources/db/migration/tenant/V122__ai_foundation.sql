-- Phase 72 — AI Foundation + Client Intelligence
-- Schema-per-tenant (ADR-T001): no tenant_id column, tenant migration only.

-- ============================================================================
-- 1. Firm AI Profile
-- ============================================================================

CREATE TABLE IF NOT EXISTS ai_firm_profiles (
    id UUID PRIMARY KEY,
    practice_areas JSONB NOT NULL DEFAULT '[]',
    jurisdiction VARCHAR(10) NOT NULL DEFAULT 'ZA',
    risk_calibration VARCHAR(20) NOT NULL DEFAULT 'CONSERVATIVE',
    house_style_notes TEXT NULL,
    fica_requirements JSONB NULL DEFAULT '{}',
    fee_estimation_notes TEXT NULL,
    preferred_model VARCHAR(40) NOT NULL DEFAULT 'claude-sonnet-4-6',
    monthly_budget_cents BIGINT NULL,
    profile_version INT NOT NULL DEFAULT 1,
    cold_start_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL
);

-- ============================================================================
-- 2. AI Executions (skill invocation audit trail)
-- ============================================================================

CREATE TABLE IF NOT EXISTS ai_executions (
    id UUID PRIMARY KEY,
    skill_id VARCHAR(40) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    input_summary TEXT NULL,
    output_content TEXT NULL,
    model VARCHAR(40) NOT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    cache_read_input_tokens INT NOT NULL DEFAULT 0,
    cache_creation_input_tokens INT NOT NULL DEFAULT 0,
    cost_cents BIGINT NOT NULL DEFAULT 0,
    duration_ms BIGINT NULL,
    invoked_by UUID NOT NULL,
    firm_profile_version INT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_executions_skill_status
    ON ai_executions (skill_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_executions_entity
    ON ai_executions (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_ai_executions_invoked_by
    ON ai_executions (invoked_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_executions_created_at
    ON ai_executions (created_at DESC);

-- ============================================================================
-- 3. AI Execution Gates (attorney approval workflow)
-- ============================================================================

CREATE TABLE IF NOT EXISTS ai_execution_gates (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL
        REFERENCES ai_executions(id) ON DELETE CASCADE,
    gate_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    proposed_action JSONB NOT NULL,
    ai_reasoning TEXT NOT NULL,
    reviewed_by UUID NULL,
    reviewed_at TIMESTAMPTZ NULL,
    review_notes TEXT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_execution_gates_execution
    ON ai_execution_gates (execution_id);

CREATE INDEX IF NOT EXISTS idx_ai_execution_gates_status_expires
    ON ai_execution_gates (status, expires_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_ai_execution_gates_reviewed_by
    ON ai_execution_gates (reviewed_by, reviewed_at DESC)
    WHERE reviewed_by IS NOT NULL;

-- ============================================================================
-- 4. Capability seed data
-- ============================================================================

-- Note: Owner and Admin system roles get ALL capabilities automatically via
-- OrgRoleService (owner = ALL_NAMES, admin = ALL minus OWNER_ONLY).
-- Seeds below are for: (a) non-system custom roles, (b) member role for AI_EXECUTE.

-- Owner: AI_MANAGE (defensive — already auto-granted by code)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'AI_MANAGE'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'AI_MANAGE'
  );

-- Admin: AI_MANAGE (defensive — already auto-granted by code)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'AI_MANAGE'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'AI_MANAGE'
  );

-- Owner: AI_EXECUTE (defensive)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'AI_EXECUTE'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'AI_EXECUTE'
  );

-- Admin: AI_EXECUTE (defensive)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'AI_EXECUTE'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'AI_EXECUTE'
  );

-- Member: AI_EXECUTE (REQUIRED — member role does NOT auto-get capabilities)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'AI_EXECUTE'
FROM org_roles
WHERE slug = 'member'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'AI_EXECUTE'
  );

-- Owner: AI_REVIEW (defensive)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'AI_REVIEW'
FROM org_roles
WHERE slug = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'AI_REVIEW'
  );

-- Admin: AI_REVIEW (defensive)
INSERT INTO org_role_capabilities (org_role_id, capability)
SELECT id, 'AI_REVIEW'
FROM org_roles
WHERE slug = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM org_role_capabilities
    WHERE org_role_id = org_roles.id AND capability = 'AI_REVIEW'
  );
