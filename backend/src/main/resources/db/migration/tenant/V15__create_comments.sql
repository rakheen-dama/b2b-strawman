-- V15: Create comments table and audit_events expression index
-- Epic 59A — Comments for tasks and documents, plus activity feed index

CREATE TABLE IF NOT EXISTS comments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(20)  NOT NULL,
    entity_id           UUID         NOT NULL,
    project_id          UUID         NOT NULL,
    author_member_id    UUID         NOT NULL,
    body                TEXT         NOT NULL,
    visibility          VARCHAR(20)  NOT NULL DEFAULT 'INTERNAL',
    parent_id           UUID,
    tenant_id           VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Indexes for primary query patterns
CREATE INDEX IF NOT EXISTS idx_comments_entity
    ON comments (entity_type, entity_id, created_at);

CREATE INDEX IF NOT EXISTS idx_comments_project
    ON comments (project_id, created_at);

CREATE INDEX IF NOT EXISTS idx_comments_tenant
    ON comments (tenant_id) WHERE tenant_id IS NOT NULL;

-- Row-Level Security for shared schema (tenant_shared)
ALTER TABLE comments ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'comments_tenant_isolation') THEN
    EXECUTE 'CREATE POLICY comments_tenant_isolation ON comments
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;

-- Expression index on audit_events for activity feed project filtering
CREATE INDEX IF NOT EXISTS idx_audit_project
    ON audit_events ((details->>'project_id'));
