-- V25: Create tags and entity_tags tables
-- Phase 11 -- Tags (independent of V24 field definitions)

-- =============================================================================
-- tags
-- =============================================================================

CREATE TABLE IF NOT EXISTS tags (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(100),
    name              VARCHAR(50)  NOT NULL,
    slug              VARCHAR(50)  NOT NULL,
    color             VARCHAR(7),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_tag_tenant_slug UNIQUE (tenant_id, slug)
);

-- Row-Level Security
ALTER TABLE tags ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tags_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY tags_tenant_isolation ON tags
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- =============================================================================
-- entity_tags
-- =============================================================================

CREATE TABLE IF NOT EXISTS entity_tags (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(100),
    tag_id            UUID         NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    entity_type       VARCHAR(20)  NOT NULL,
    entity_id         UUID         NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_entity_tag UNIQUE (tag_id, entity_type, entity_id)
);

CREATE INDEX IF NOT EXISTS idx_entity_tag_entity
    ON entity_tags(tenant_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_tag_tag
    ON entity_tags(tenant_id, tag_id, entity_type);

-- Row-Level Security
ALTER TABLE entity_tags ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'entity_tags_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY entity_tags_tenant_isolation ON entity_tags
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
