-- Phase 70 Stage 1 — AI Specialist Invocations + per-call LLM telemetry.
-- Schema-per-tenant (ADR-T001): no tenant_id column, tenant migration only.

CREATE TABLE IF NOT EXISTS ai_specialist_invocations (
    id UUID PRIMARY KEY,
    specialist_id VARCHAR(40) NOT NULL,
    invoked_by VARCHAR(20) NOT NULL,
    actor_id UUID NOT NULL,
    automation_action_execution_id UUID NULL
        REFERENCES action_executions(id) ON DELETE SET NULL,
    context_entity_type VARCHAR(50) NOT NULL,
    context_entity_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    proposed_output JSONB NULL,
    applied_output JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ NULL,
    reviewed_by_id UUID NULL,
    reject_reason TEXT NULL,
    error_message VARCHAR(2000) NULL,
    prompt_version VARCHAR(40) NULL,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ai_llm_calls (
    id UUID PRIMARY KEY,
    invocation_id UUID NOT NULL
        REFERENCES ai_specialist_invocations(id) ON DELETE CASCADE,
    model VARCHAR(80) NOT NULL,
    prompt_version VARCHAR(40) NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    cache_read_input_tokens INT NOT NULL DEFAULT 0,
    cache_creation_input_tokens INT NOT NULL DEFAULT 0,
    request_id VARCHAR(100) NULL,
    stop_reason VARCHAR(40) NULL,
    latency_ms INT NULL,
    was_vision BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_llm_calls_invocation
    ON ai_llm_calls (invocation_id);

CREATE INDEX IF NOT EXISTS idx_invocation_status_created
    ON ai_specialist_invocations (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_invocation_context
    ON ai_specialist_invocations (context_entity_type, context_entity_id);

CREATE INDEX IF NOT EXISTS idx_invocation_action_execution
    ON ai_specialist_invocations (automation_action_execution_id);

CREATE INDEX IF NOT EXISTS idx_invocation_specialist_status
    ON ai_specialist_invocations (specialist_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_invocation_actor_created
    ON ai_specialist_invocations (actor_id, created_at DESC);

ALTER TABLE automation_rules
    ADD COLUMN IF NOT EXISTS last_run_at TIMESTAMPTZ NULL;
