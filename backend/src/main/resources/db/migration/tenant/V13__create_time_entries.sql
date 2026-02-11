-- V13: Create time_entries table
-- Epic 44A — TimeEntry entity for task-scoped time tracking

CREATE TABLE IF NOT EXISTS time_entries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id           UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    member_id         UUID NOT NULL REFERENCES members(id),
    date              DATE NOT NULL,
    duration_minutes  INTEGER NOT NULL,
    billable          BOOLEAN NOT NULL DEFAULT true,
    rate_cents        INTEGER,
    description       TEXT,
    tenant_id         VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT chk_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT chk_rate_non_negative CHECK (rate_cents >= 0 OR rate_cents IS NULL)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_time_entries_task_id
    ON time_entries (task_id);

CREATE INDEX IF NOT EXISTS idx_time_entries_member_id_date
    ON time_entries (member_id, date);

CREATE INDEX IF NOT EXISTS idx_time_entries_task_id_date
    ON time_entries (task_id, date);

CREATE INDEX IF NOT EXISTS idx_time_entries_task_id_billable
    ON time_entries (task_id, billable);

CREATE INDEX IF NOT EXISTS idx_time_entries_tenant_id
    ON time_entries (tenant_id);

-- Row-Level Security
ALTER TABLE time_entries ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE policyname = 'tenant_isolation_time_entries') THEN
    EXECUTE 'CREATE POLICY tenant_isolation_time_entries ON time_entries
      USING (tenant_id = current_setting(''app.current_tenant'', true) OR tenant_id IS NULL)';
  END IF;
END $$;
