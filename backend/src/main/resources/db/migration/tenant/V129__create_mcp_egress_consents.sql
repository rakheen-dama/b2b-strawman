-- V129: POPIA data-egress consent history for the MCP connector (Phase 78).
-- Append-only: GRANTED / REVOKED rows form the audit trail of firm consent decisions.
-- Tenant-scoped: runs per-schema at provisioning AND at startup. Idempotent.
CREATE TABLE IF NOT EXISTS mcp_egress_consents (
    id              uuid         PRIMARY KEY,
    consented_by    uuid         NOT NULL,             -- Member id (no cross-module FK, house style)
    consented_at    timestamptz  NOT NULL,
    consent_version text         NOT NULL,             -- e.g. 'popia-egress-v1'
    action          text         NOT NULL CHECK (action IN ('GRANTED', 'REVOKED')),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

-- Latest-decision lookup ("current consent state" = newest row by consented_at, tie-broken by
-- created_at). Matches the repository ordering findTopByOrderByConsentedAtDescCreatedAtDesc().
CREATE INDEX IF NOT EXISTS idx_mcp_egress_consents_consented_at
    ON mcp_egress_consents (consented_at DESC, created_at DESC);

-- Per-member consent history (who consented, audit/POPIA reporting).
CREATE INDEX IF NOT EXISTS idx_mcp_egress_consents_member
    ON mcp_egress_consents (consented_by, consented_at DESC);
