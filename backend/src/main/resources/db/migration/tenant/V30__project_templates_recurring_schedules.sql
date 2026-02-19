-- V30__project_templates_recurring_schedules.sql

-- ============================================================
-- ProjectTemplate
-- ============================================================
CREATE TABLE project_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(300) NOT NULL,
    name_pattern    VARCHAR(300) NOT NULL,
    description     TEXT,
    billable_default BOOLEAN NOT NULL DEFAULT true,
    source          VARCHAR(20) NOT NULL,
    source_project_id UUID,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_templates_active ON project_templates (active);
CREATE INDEX idx_project_templates_created_by ON project_templates (created_by);

-- ============================================================
-- TemplateTask
-- ============================================================
CREATE TABLE template_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES project_templates(id) ON DELETE CASCADE,
    name            VARCHAR(300) NOT NULL,
    description     TEXT,
    estimated_hours DECIMAL(10,2),
    sort_order      INTEGER NOT NULL,
    billable        BOOLEAN NOT NULL DEFAULT true,
    assignee_role   VARCHAR(20) NOT NULL DEFAULT 'UNASSIGNED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_template_task_sort UNIQUE (template_id, sort_order)
);

CREATE INDEX idx_template_tasks_template_id ON template_tasks (template_id);

-- ============================================================
-- TemplateTag (join table, not a JPA entity)
-- ============================================================
CREATE TABLE template_tags (
    template_id     UUID NOT NULL REFERENCES project_templates(id) ON DELETE CASCADE,
    tag_id          UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,

    PRIMARY KEY (template_id, tag_id)
);

-- ============================================================
-- RecurringSchedule
-- ============================================================
CREATE TABLE recurring_schedules (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id             UUID NOT NULL REFERENCES project_templates(id),
    customer_id             UUID NOT NULL REFERENCES customers(id),
    name_override           VARCHAR(300),
    frequency               VARCHAR(20) NOT NULL,
    start_date              DATE NOT NULL,
    end_date                DATE,
    lead_time_days          INTEGER NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    next_execution_date     DATE,
    last_executed_at        TIMESTAMPTZ,
    execution_count         INTEGER NOT NULL DEFAULT 0,
    project_lead_member_id  UUID,
    created_by              UUID NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_schedule_template_customer_freq UNIQUE (template_id, customer_id, frequency),
    CONSTRAINT chk_lead_time_non_negative CHECK (lead_time_days >= 0),
    CONSTRAINT chk_end_after_start CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_recurring_schedules_status ON recurring_schedules (status);
CREATE INDEX idx_recurring_schedules_next_exec ON recurring_schedules (next_execution_date)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_recurring_schedules_customer ON recurring_schedules (customer_id);
CREATE INDEX idx_recurring_schedules_template ON recurring_schedules (template_id);

-- ============================================================
-- ScheduleExecution
-- ============================================================
CREATE TABLE schedule_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id     UUID NOT NULL REFERENCES recurring_schedules(id),
    project_id      UUID NOT NULL REFERENCES projects(id),
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    executed_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_execution_schedule_period UNIQUE (schedule_id, period_start)
);

CREATE INDEX idx_schedule_executions_schedule ON schedule_executions (schedule_id);
CREATE INDEX idx_schedule_executions_project ON schedule_executions (project_id);
