-- Phase 92 / 650A (ADR-382; architecture §16.2.10, §16.3.10, the first block of §16.7.9): the
-- Rule 54.15 list of one trust account at one month end, frozen. Completing a reconciliation whose
-- period end is the last day of a calendar month writes one trust_monthly_lists row in the same
-- transaction: the money side as reconciled, the cashbook, each creditor-side total, every
-- creditor by name (lines, jsonb), the integrity flags raised at completion, who locked it and
-- when. A posting dated on or before period_end and posted after locked_at is recorded normally and
-- shown against the list; it never changes the row.
--
-- Nothing is back-filled for reconciliations completed before this migration: their figures were
-- calculated as at now, not as at their period end, so freezing them would freeze a wrong
-- comparison (§16.7.9). Rows are never updated or deleted (A5: either retention period holds).
--
-- Idempotent: CREATE TABLE IF NOT EXISTS (the inline constraints are created with the table only,
-- so they need no catalog guard). No ON DELETE CASCADE. The unique (trust_account_id, period_end)
-- is the only index needed (lists by account, newest first).
CREATE TABLE IF NOT EXISTS trust_monthly_lists (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    trust_account_id uuid NOT NULL,
    period_end date NOT NULL,
    reconciliation_id uuid NOT NULL,
    bank_balance numeric(15,2) NOT NULL,
    outstanding_deposits numeric(15,2) NOT NULL,
    outstanding_payments numeric(15,2) NOT NULL,
    adjusted_bank_balance numeric(15,2) NOT NULL,
    cashbook_balance numeric(15,2) NOT NULL,
    client_ledger_total numeric(15,2) NOT NULL,
    investment_creditor_total numeric(15,2) NOT NULL,
    fund_ledger_balance numeric(15,2) NOT NULL,
    unidentified_deposit_total numeric(15,2) NOT NULL,
    inter_account_balance numeric(15,2) NOT NULL,
    creditor_total numeric(15,2) NOT NULL,
    difference numeric(15,2) NOT NULL,
    lines jsonb NOT NULL,
    integrity_flags jsonb DEFAULT '[]'::jsonb NOT NULL,
    locked_by uuid NOT NULL,
    locked_at timestamptz NOT NULL,
    CONSTRAINT trust_monthly_lists_pkey PRIMARY KEY (id),
    CONSTRAINT uq_trust_monthly_list_account_period UNIQUE (trust_account_id, period_end),
    CONSTRAINT uq_trust_monthly_list_reconciliation UNIQUE (reconciliation_id),
    CONSTRAINT chk_trust_monthly_list_month_end
        CHECK (period_end = (date_trunc('month', period_end) + interval '1 month - 1 day')::date),
    CONSTRAINT chk_trust_monthly_list_balanced CHECK (difference = 0),
    CONSTRAINT trust_monthly_lists_trust_account_id_fkey FOREIGN KEY (trust_account_id) REFERENCES trust_accounts(id),
    CONSTRAINT trust_monthly_lists_reconciliation_id_fkey FOREIGN KEY (reconciliation_id) REFERENCES trust_reconciliations(id),
    CONSTRAINT trust_monthly_lists_locked_by_fkey FOREIGN KEY (locked_by) REFERENCES members(id)
);
