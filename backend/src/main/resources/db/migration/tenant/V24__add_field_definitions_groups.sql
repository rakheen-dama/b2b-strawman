-- V24: Create field_definitions, field_groups, field_group_members tables
-- Phase 11 — Tags, Custom Fields & Views (field definitions foundation)

-- =============================================================================
-- field_definitions
-- =============================================================================

CREATE TABLE IF NOT EXISTS field_definitions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(100),
    entity_type       VARCHAR(20)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(100) NOT NULL,
    field_type        VARCHAR(20)  NOT NULL,
    description       TEXT,
    required          BOOLEAN      NOT NULL DEFAULT false,
    default_value     JSONB,
    options           JSONB,
    validation        JSONB,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    pack_id           VARCHAR(100),
    pack_field_key    VARCHAR(100),
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_field_def_tenant_type_slug UNIQUE (tenant_id, entity_type, slug)
);

CREATE INDEX IF NOT EXISTS idx_field_def_tenant_type_active
    ON field_definitions(tenant_id, entity_type, active);
CREATE INDEX IF NOT EXISTS idx_field_def_tenant_pack
    ON field_definitions(tenant_id, pack_id);

-- Row-Level Security
ALTER TABLE field_definitions ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'field_definitions_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY field_definitions_tenant_isolation ON field_definitions
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- =============================================================================
-- field_groups
-- =============================================================================

CREATE TABLE IF NOT EXISTS field_groups (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(100),
    entity_type       VARCHAR(20)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(100) NOT NULL,
    description       TEXT,
    pack_id           VARCHAR(100),
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_field_group_tenant_type_slug UNIQUE (tenant_id, entity_type, slug)
);

-- Row-Level Security
ALTER TABLE field_groups ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'field_groups_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY field_groups_tenant_isolation ON field_groups
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- =============================================================================
-- field_group_members
-- =============================================================================

CREATE TABLE IF NOT EXISTS field_group_members (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(100),
    field_group_id        UUID         NOT NULL REFERENCES field_groups(id) ON DELETE CASCADE,
    field_definition_id   UUID         NOT NULL REFERENCES field_definitions(id) ON DELETE CASCADE,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_group_member UNIQUE (field_group_id, field_definition_id)
);

-- Row-Level Security
ALTER TABLE field_group_members ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'field_group_members_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY field_group_members_tenant_isolation ON field_group_members
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
