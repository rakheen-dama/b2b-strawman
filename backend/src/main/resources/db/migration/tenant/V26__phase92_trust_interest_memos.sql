-- Phase 92 / 650B (ADR-384; architecture §16.2.9, §16.3.13, the second block of §16.7.9): Rule
-- 54.28 figures for interest and charges that never reached the trust account. Where a bank sweeps
-- interest to the Fund at source, or shows only a net figure, nothing is posted to the cashbook
-- (the cashbook mirrors the statement); the firm records the gross interest, the gross charges and
-- the VAT included in them, with where the figures came from, as one memo per trust account and
-- month. A memo posts nothing and has no effect on any balance, the reconciliation or the monthly
-- list; it may be saved or corrected for a month whose list is locked. Read by the Rule 54.28
-- schedule and the Section 35 pack (650C), which show memo figures in their own columns.
--
-- Idempotent: CREATE TABLE IF NOT EXISTS (the inline constraints are created with the table only,
-- so they need no catalog guard). No ON DELETE CASCADE. The unique (trust_account_id,
-- period_month) is the only index needed. Nothing is back-filled.
CREATE TABLE IF NOT EXISTS trust_interest_memos (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    trust_account_id uuid NOT NULL,
    period_month date NOT NULL,
    gross_interest numeric(15,2) DEFAULT 0 NOT NULL,
    gross_charges numeric(15,2) DEFAULT 0 NOT NULL,
    charges_vat numeric(15,2) DEFAULT 0 NOT NULL,
    source_note varchar(500) NOT NULL,
    recorded_by uuid NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT trust_interest_memos_pkey PRIMARY KEY (id),
    CONSTRAINT uq_trust_interest_memo_account_month UNIQUE (trust_account_id, period_month),
    CONSTRAINT chk_trust_interest_memo_month CHECK (EXTRACT(DAY FROM period_month) = 1),
    CONSTRAINT chk_trust_interest_memo_amounts CHECK (
        gross_interest >= 0 AND gross_charges >= 0 AND charges_vat >= 0 AND charges_vat <= gross_charges),
    CONSTRAINT trust_interest_memos_trust_account_id_fkey FOREIGN KEY (trust_account_id) REFERENCES trust_accounts(id),
    CONSTRAINT trust_interest_memos_recorded_by_fkey FOREIGN KEY (recorded_by) REFERENCES members(id)
);
