-- Phase 92 / 648B (ADR-379; architecture §16.2.8, the tenant part of §16.7.8): per-investment
-- interest entries replace the typed running total. One trust_investment_interest row each time
-- the bank credits interest on a section 86(4) investment: the gross, the Legal Practitioners'
-- Fidelity Fund's 5% and the client's 95%, the date, the presentation (GROSS: the bank credited the
-- gross and debits the 5% separately; NET: the 5% went to the Fund at source and never reached the
-- account — assumption A2) and a snapshot of the client's tax reference where held (A3). The rows
-- an entry posts (INV_INTEREST, and FUND_INV_SHARE for GROSS) are ordinary trust_transactions.
--
-- The 5% is deliberately NOT a CHECK: the service enforces it with the constant
-- TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT and a R0,01 tolerance for the bank's own
-- rounding (OL-5); a CHECK would either forbid the tolerance or hold a second copy of the rate.
--
-- trust_investments.interest_earned goes (its CHECK chk_trust_invest_earned is dropped with the
-- column): it was a hand-typed figure with no posting behind it, replaced by the entries. Existing
-- values are not carried into entries — no real tenant holds an investment (ADR-383, R12; V23
-- deleted every legacy investment) and a 648A-era dev value never moved a balance. No row is
-- deleted and no balance moves.
--
-- Idempotent: CREATE TABLE / INDEX IF NOT EXISTS (the inline constraints are created with the
-- table only, so they need no catalog guard) and DROP COLUMN IF EXISTS. No ON DELETE CASCADE.
CREATE TABLE IF NOT EXISTS trust_investment_interest (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    investment_id uuid NOT NULL,
    entry_date date NOT NULL,
    gross_amount numeric(15,2) NOT NULL,
    fund_share_amount numeric(15,2) NOT NULL,
    net_amount numeric(15,2) NOT NULL,
    presentation varchar(10) NOT NULL,
    net_transaction_id uuid NOT NULL,
    fund_share_transaction_id uuid,
    bank_statement_line_id uuid,
    client_tax_reference varchar(100),
    status varchar(20) DEFAULT 'POSTED' NOT NULL,
    recorded_by uuid NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT trust_investment_interest_pkey PRIMARY KEY (id),
    CONSTRAINT chk_invest_interest_amounts CHECK (
        gross_amount > 0 AND fund_share_amount >= 0 AND net_amount > 0
        AND gross_amount = fund_share_amount + net_amount),
    CONSTRAINT chk_invest_interest_presentation CHECK (presentation IN ('GROSS', 'NET')),
    CONSTRAINT chk_invest_interest_fund_txn CHECK (
        (presentation = 'GROSS') = (fund_share_transaction_id IS NOT NULL)),
    CONSTRAINT chk_invest_interest_status CHECK (status IN ('POSTED', 'REVERSED')),
    CONSTRAINT trust_investment_interest_investment_fkey
        FOREIGN KEY (investment_id) REFERENCES trust_investments(id),
    CONSTRAINT trust_investment_interest_net_txn_fkey
        FOREIGN KEY (net_transaction_id) REFERENCES trust_transactions(id),
    CONSTRAINT trust_investment_interest_fund_txn_fkey
        FOREIGN KEY (fund_share_transaction_id) REFERENCES trust_transactions(id),
    CONSTRAINT trust_investment_interest_line_fkey
        FOREIGN KEY (bank_statement_line_id) REFERENCES bank_statement_lines(id),
    CONSTRAINT trust_investment_interest_recorded_by_fkey
        FOREIGN KEY (recorded_by) REFERENCES members(id)
);
CREATE INDEX IF NOT EXISTS idx_invest_interest_investment
    ON trust_investment_interest (investment_id, entry_date);
-- At most one POSTED entry per statement line — the database backstop behind the service's
-- match-status check (a REVERSED entry drops out, so the line can be recorded again after an
-- unmatch). It also serves the unmatch hook's read (findPostedIdByBankStatementLineId).
CREATE UNIQUE INDEX IF NOT EXISTS idx_invest_interest_posted_line
    ON trust_investment_interest (bank_statement_line_id)
    WHERE status = 'POSTED' AND bank_statement_line_id IS NOT NULL;

-- The hand-typed running total goes (its CHECK chk_trust_invest_earned goes with the column)
ALTER TABLE trust_investments DROP COLUMN IF EXISTS interest_earned;
