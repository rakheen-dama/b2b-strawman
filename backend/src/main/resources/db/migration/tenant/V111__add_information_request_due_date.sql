-- ============================================================
-- V111__add_information_request_due_date.sql
-- GAP-L-41: Add nullable due_date column to information_requests
-- so the Create Information Request dialog can capture a client
-- deadline for returning the requested information.
-- ============================================================

ALTER TABLE information_requests
    ADD COLUMN IF NOT EXISTS due_date DATE;

CREATE INDEX IF NOT EXISTS idx_information_requests_due_date
    ON information_requests (due_date)
    WHERE due_date IS NOT NULL;
