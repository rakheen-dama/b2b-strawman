-- ============================================================
-- V47: Entity Lifecycle & Relationship Integrity
-- Phase 29 -- Adds lifecycle state machines to Task and Project
-- ============================================================

-- Task: completion and cancellation timestamps
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_by UUID;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS cancelled_by UUID;

-- Task: constrain status to valid enum values
-- Existing values (OPEN, IN_PROGRESS) are valid; DONE and CANCELLED are new
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_status_check;
ALTER TABLE tasks ADD CONSTRAINT tasks_status_check
  CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'));

-- Task: constrain priority to valid enum values
-- Existing values (LOW, MEDIUM, HIGH) are valid; URGENT is new
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_priority_check;
ALTER TABLE tasks ADD CONSTRAINT tasks_priority_check
  CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));

-- Project: lifecycle status
ALTER TABLE projects ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_status_check;
ALTER TABLE projects ADD CONSTRAINT projects_status_check
  CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED'));

-- Project: customer link (soft FK, validated at application level)
ALTER TABLE projects ADD COLUMN IF NOT EXISTS customer_id UUID;

-- Project: due date for engagement deadline tracking
ALTER TABLE projects ADD COLUMN IF NOT EXISTS due_date DATE;

-- Project: completion and archive timestamps
ALTER TABLE projects ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS completed_by UUID;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS archived_by UUID;

-- ============================================================
-- Indexes
-- ============================================================

-- Project status filtering (most queries filter by status)
CREATE INDEX IF NOT EXISTS idx_projects_status ON projects (status);

-- Project-Customer link (filter projects by customer, join queries)
CREATE INDEX IF NOT EXISTS idx_projects_customer_id ON projects (customer_id)
  WHERE customer_id IS NOT NULL;

-- Project due date (deadline views, overdue queries)
CREATE INDEX IF NOT EXISTS idx_projects_due_date ON projects (due_date)
  WHERE due_date IS NOT NULL;

-- Task status filtering (list queries filter by status, default excludes DONE/CANCELLED)
-- Note: tasks already have an index on project_id; this composite index supports
-- the common query pattern: "tasks for project X with status in (...)"
CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks (project_id, status);
