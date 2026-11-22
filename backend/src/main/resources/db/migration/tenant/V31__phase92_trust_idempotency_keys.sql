-- Phase 92 follow-up 648B F-11 (founder ruling 2026-11-22, tasks/phase92-decisions.md): every
-- trust write carries a client-generated Idempotency-Key. One row per key, claimed before the
-- write runs (state IN_PROGRESS) and completed after it with the response the firm was sent
-- (state COMPLETED), so a retry with the same key replays that response instead of posting again.
-- Keys are per tenant (the table is per tenant schema). Rows older than 7 days are purged daily
-- (TrustIdempotencyPurgeJob). Reproduced by TrustIdempotencyRedTest (two deposits for one key).
--
-- Idempotent: CREATE TABLE / INDEX ... IF NOT EXISTS; the CHECK constraints are inline in the
-- CREATE TABLE, so no catalog lookup (and no current_schema() guard) is needed.
CREATE TABLE IF NOT EXISTS trust_idempotency_keys (
    idempotency_key   UUID        NOT NULL,
    member_id         UUID        NOT NULL,
    http_method       VARCHAR(10) NOT NULL,
    route_template    VARCHAR(255) NOT NULL,
    -- SHA-256 (hex) of the method, route template, path variables, query parameters and the
    -- normalised body (multipart: every part's name and value, and each file's name, type and
    -- the SHA-256 of its bytes).
    request_hash      VARCHAR(64) NOT NULL,
    state             VARCHAR(20) NOT NULL,
    response_status   INTEGER,
    response_body     TEXT,
    -- The replayed headers: Content-Type and Location when the response carried them.
    response_headers  JSONB,
    created_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT pk_trust_idempotency_keys PRIMARY KEY (idempotency_key),
    CONSTRAINT chk_trust_idempotency_state CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_trust_idempotency_completed CHECK (
        (state = 'IN_PROGRESS' AND response_status IS NULL AND completed_at IS NULL)
        OR (state = 'COMPLETED' AND response_status IS NOT NULL AND completed_at IS NOT NULL))
);

-- The daily purge deletes by age.
CREATE INDEX IF NOT EXISTS idx_trust_idempotency_created_at
    ON trust_idempotency_keys (created_at);
