-- ============================================================
-- V87__phase61_investment_basis_and_kyc_verification.sql
-- Phase 61: Investment basis distinction + KYC verification columns
-- ============================================================

-- ============================================================
-- 1. Track 1: Investment Basis on trust_investments
-- ============================================================

ALTER TABLE trust_investments
    ADD COLUMN IF NOT EXISTS investment_basis VARCHAR(20) NOT NULL DEFAULT 'FIRM_DISCRETION';

DO $$ BEGIN
    ALTER TABLE trust_investments
        ADD CONSTRAINT chk_investment_basis
        CHECK (investment_basis IN ('FIRM_DISCRETION', 'CLIENT_INSTRUCTION'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ============================================================
-- 2. Track 2: KYC Verification on checklist_instance_items
-- ============================================================

ALTER TABLE checklist_instance_items
    ADD COLUMN IF NOT EXISTS verification_provider VARCHAR(30);

ALTER TABLE checklist_instance_items
    ADD COLUMN IF NOT EXISTS verification_reference VARCHAR(200);

ALTER TABLE checklist_instance_items
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(20);

ALTER TABLE checklist_instance_items
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;

ALTER TABLE checklist_instance_items
    ADD COLUMN IF NOT EXISTS verification_metadata JSONB;

-- ERROR status is deliberately excluded from the DB constraint: the service never persists
-- ERROR to the checklist item (errors are returned to the caller without updating the item).
DO $$ BEGIN
    ALTER TABLE checklist_instance_items
        ADD CONSTRAINT chk_verification_status
        CHECK (verification_status IS NULL OR verification_status IN ('VERIFIED', 'NOT_VERIFIED', 'NEEDS_REVIEW'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ============================================================
-- 3. Track 1 continuation: InterestAllocation audit trail
-- ============================================================

ALTER TABLE interest_allocations
    ADD COLUMN IF NOT EXISTS lpff_rate_id UUID REFERENCES lpff_rates(id);

ALTER TABLE interest_allocations
    ADD COLUMN IF NOT EXISTS statutory_rate_applied BOOLEAN NOT NULL DEFAULT false;
