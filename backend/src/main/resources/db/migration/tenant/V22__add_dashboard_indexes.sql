-- Performance indexes for dashboard aggregation queries (Epic 76B)

CREATE INDEX IF NOT EXISTS idx_time_entries_date ON time_entries (date);

CREATE INDEX IF NOT EXISTS idx_tasks_due_date_status ON tasks (due_date, status) WHERE due_date IS NOT NULL;
