-- V7: Add tenant_id column for shared-schema row-level isolation (ADR-012)
-- Applied to ALL tenant schemas (shared + dedicated). In dedicated schemas,
-- tenant_id stays NULL and is never read. In tenant_shared, it carries the
-- Clerk org ID for Hibernate @Filter and Postgres RLS row filtering.

-- 1. Add nullable tenant_id column to each tenant table
ALTER TABLE projects ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE members ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE project_members ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);

-- 2. Create indexes for efficient row-level filtering
CREATE INDEX IF NOT EXISTS idx_projects_tenant_id ON projects (tenant_id);
CREATE INDEX IF NOT EXISTS idx_documents_tenant_id ON documents (tenant_id);
CREATE INDEX IF NOT EXISTS idx_members_tenant_id ON members (tenant_id);
CREATE INDEX IF NOT EXISTS idx_project_members_tenant_id ON project_members (tenant_id);

-- 3. Enable Row-Level Security on all tenant tables
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE members ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_members ENABLE ROW LEVEL SECURITY;

-- 4. Create RLS policies using current_setting('app.current_tenant', true).
-- The OR tenant_id IS NULL guard allows Pro schemas (where tenant_id is always
-- NULL) to function without any filtering. In tenant_shared, tenant_id is
-- always populated by TenantAwareEntityListener.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_projects') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_projects ON projects
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_documents') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_documents ON documents
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_members') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_members ON members
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_project_members') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_project_members ON project_members
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
