-- V10: Create customer_projects junction table
-- Epic 37A — Links customers to projects (entity in 37B)

CREATE TABLE IF NOT EXISTS customer_projects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    linked_by    UUID REFERENCES members(id) ON DELETE SET NULL,
    tenant_id    VARCHAR(255),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Unique constraint: one link per customer-project pair
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_customer_projects_customer_project') THEN
    ALTER TABLE customer_projects ADD CONSTRAINT uq_customer_projects_customer_project UNIQUE (customer_id, project_id);
  END IF;
END $$;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_customer_projects_customer_id
    ON customer_projects (customer_id);

CREATE INDEX IF NOT EXISTS idx_customer_projects_project_id
    ON customer_projects (project_id);

CREATE INDEX IF NOT EXISTS idx_customer_projects_tenant_id
    ON customer_projects (tenant_id);

CREATE INDEX IF NOT EXISTS idx_customer_projects_linked_by
    ON customer_projects (linked_by);

-- Row-Level Security
ALTER TABLE customer_projects ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_customer_projects') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_customer_projects ON customer_projects
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
