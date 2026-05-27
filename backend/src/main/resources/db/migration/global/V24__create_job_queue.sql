-- V24: Job queue table for distributed scheduled work execution
-- Phase 75 -- Scalability: Job Queue Fanout

CREATE TABLE IF NOT EXISTS public.job_queue (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type        VARCHAR(100)  NOT NULL,
    tenant_id       VARCHAR(50)   NOT NULL,
    org_id          VARCHAR(100)  NOT NULL,
    shard_id        VARCHAR(50)   NOT NULL DEFAULT 'primary',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    payload         JSONB,
    priority        INT           NOT NULL DEFAULT 0,
    claimed_by      VARCHAR(100),
    claimed_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    retry_count     INT           NOT NULL DEFAULT 0,
    max_retries     INT           NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    error_message   TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_job_status CHECK (
        status IN ('PENDING', 'CLAIMED', 'COMPLETED', 'FAILED', 'DEAD_LETTER')
    ),
    CONSTRAINT chk_job_queue_non_negative CHECK (
        retry_count >= 0
        AND max_retries >= 0
    )
);

-- Claimable jobs: partial index for the worker poll query.
-- Covers: WHERE status = 'PENDING' ORDER BY priority DESC, next_attempt_at ASC.
-- The next_attempt_at <= NOW() filter is applied at query time, not in the index predicate,
-- because PostgreSQL evaluates partial index predicates once at DDL time — NOW() would
-- become a static timestamp and exclude all future rows.
-- This is the hot-path index — workers hit it every 2 seconds per pod.
CREATE INDEX IF NOT EXISTS idx_job_queue_claimable
    ON public.job_queue (priority DESC, next_attempt_at ASC)
    WHERE status = 'PENDING';

-- Dedup index: prevents double-enqueue for the same (job_type, tenant_id)
-- while a PENDING or CLAIMED job exists. The enqueuer uses
-- INSERT ... ON CONFLICT DO NOTHING on this index.
CREATE UNIQUE INDEX IF NOT EXISTS idx_job_queue_dedup
    ON public.job_queue (job_type, tenant_id)
    WHERE status IN ('PENDING', 'CLAIMED');

-- Status + type + time: supports the admin API queries
-- (list by status, filter by job_type, order by created_at).
CREATE INDEX IF NOT EXISTS idx_job_queue_status_type
    ON public.job_queue (job_type, status, created_at);

-- Stale claim detection: supports the StaleJobRecoveryTask query
-- (WHERE status = 'CLAIMED' AND claimed_at < threshold).
CREATE INDEX IF NOT EXISTS idx_job_queue_stale_claims
    ON public.job_queue (claimed_at)
    WHERE status = 'CLAIMED';

-- Register the stale job recovery task in ShedLock
-- (pre-seed so the first execution doesn't require manual setup)
INSERT INTO public.shedlock (name, lock_until, locked_at, locked_by)
VALUES ('stale_job_recovery', NOW(), NOW(), 'migration')
ON CONFLICT (name) DO NOTHING;

COMMENT ON TABLE public.job_queue IS
    'Distributed job queue for scheduled tenant-level work. Workers claim jobs via SELECT FOR UPDATE SKIP LOCKED.';
