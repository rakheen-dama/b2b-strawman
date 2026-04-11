-- V90: Add contingency fee model support to proposals.
-- Fix for GAP-S5-01 — CONTINGENCY fee model for legal-za vertical (LPC Rule 59,
-- Contingency Fees Act 66 of 1997, 25% cap on contingency fees).

-- 1. Add contingency-specific columns.
ALTER TABLE proposals
    ADD COLUMN IF NOT EXISTS contingency_percent NUMERIC(5, 2);

ALTER TABLE proposals
    ADD COLUMN IF NOT EXISTS contingency_cap_percent NUMERIC(5, 2);

ALTER TABLE proposals
    ADD COLUMN IF NOT EXISTS contingency_description VARCHAR(500);

-- 2. Broaden the fee_model CHECK constraint to allow CONTINGENCY.
-- V51 created `proposals_fee_model_check` restricted to FIXED/HOURLY/RETAINER.
ALTER TABLE proposals
    DROP CONSTRAINT IF EXISTS proposals_fee_model_check;

ALTER TABLE proposals
    ADD CONSTRAINT proposals_fee_model_check
        CHECK (fee_model IN ('FIXED', 'HOURLY', 'RETAINER', 'CONTINGENCY'));
