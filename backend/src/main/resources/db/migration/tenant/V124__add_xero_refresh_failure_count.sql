-- =============================================================================
-- V124: Add refresh_failure_count to accounting_xero_connection
-- Phase 71 — Epic 519A: tracks consecutive token refresh failures
-- =============================================================================

ALTER TABLE accounting_xero_connection
    ADD COLUMN IF NOT EXISTS refresh_failure_count INT NOT NULL DEFAULT 0;
