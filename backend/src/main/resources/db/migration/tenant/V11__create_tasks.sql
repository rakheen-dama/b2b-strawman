-- V11: Create tasks table
-- Epic 39A — Task entity for project-scoped task management

CREATE TABLE IF NOT EXISTS tasks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title        VARCHAR(500) NOT NULL,
    description  TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority     VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    type         VARCHAR(100),
    assignee_id  UUID REFERENCES members(id) ON DELETE SET NULL,
    created_by   UUID NOT NULL REFERENCES members(id),
    due_date     DATE,
    version      INTEGER NOT NULL DEFAULT 0,
    tenant_id    VARCHAR(255),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_tasks_project_id
    ON tasks (project_id);

CREATE INDEX IF NOT EXISTS idx_tasks_assignee_id
    ON tasks (assignee_id);

CREATE INDEX IF NOT EXISTS idx_tasks_project_id_status
    ON tasks (project_id, status);

CREATE INDEX IF NOT EXISTS idx_tasks_project_id_assignee_id
    ON tasks (project_id, assignee_id);

CREATE INDEX IF NOT EXISTS idx_tasks_tenant_id
    ON tasks (tenant_id);

-- Row-Level Security
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_tasks') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_tasks ON tasks
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
