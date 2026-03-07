-- V60__create_automation_tables.sql
-- Phase 37: Workflow Automations v1

-- 1. Automation rules
CREATE TABLE IF NOT EXISTS automation_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(1000),
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    trigger_type    VARCHAR(50)     NOT NULL,
    trigger_config  JSONB           NOT NULL,
    conditions      JSONB,
    source          VARCHAR(20)     NOT NULL DEFAULT 'CUSTOM',
    template_slug   VARCHAR(100),
    created_by      UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 2. Automation actions (ordered list per rule)
CREATE TABLE IF NOT EXISTS automation_actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         UUID            NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    sort_order      INTEGER         NOT NULL,
    action_type     VARCHAR(30)     NOT NULL,
    action_config   JSONB           NOT NULL,
    delay_duration  INTEGER,
    delay_unit      VARCHAR(10),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_action_rule_sort UNIQUE (rule_id, sort_order),
    CONSTRAINT chk_delay_both_or_neither CHECK (
        (delay_duration IS NULL AND delay_unit IS NULL) OR
        (delay_duration IS NOT NULL AND delay_unit IS NOT NULL)
    )
);

-- 3. Automation executions (rule-level audit trail)
CREATE TABLE IF NOT EXISTS automation_executions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id             UUID            NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    trigger_event_type  VARCHAR(100)    NOT NULL,
    trigger_event_data  JSONB           NOT NULL,
    conditions_met      BOOLEAN         NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    started_at          TIMESTAMPTZ     NOT NULL,
    completed_at        TIMESTAMPTZ,
    error_message       VARCHAR(2000),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- 4. Action executions (per-action audit trail)
CREATE TABLE IF NOT EXISTS action_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID            NOT NULL REFERENCES automation_executions(id) ON DELETE CASCADE,
    action_id       UUID            REFERENCES automation_actions(id) ON DELETE SET NULL,
    status          VARCHAR(20)     NOT NULL,
    scheduled_for   TIMESTAMPTZ,
    executed_at     TIMESTAMPTZ,
    result_data     JSONB,
    error_message   VARCHAR(2000),
    error_detail    TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Indexes: rule lookup by enabled + trigger type (the hot query path)
CREATE INDEX IF NOT EXISTS idx_automation_rules_enabled_trigger
    ON automation_rules (enabled, trigger_type)
    WHERE enabled = true;

-- Indexes: execution log queries
CREATE INDEX IF NOT EXISTS idx_automation_executions_rule_status
    ON automation_executions (rule_id, status);

CREATE INDEX IF NOT EXISTS idx_automation_executions_started_at
    ON automation_executions (started_at DESC);

-- Indexes: delayed action scheduler query
CREATE INDEX IF NOT EXISTS idx_action_executions_scheduled
    ON action_executions (scheduled_for, status)
    WHERE status = 'SCHEDULED';
