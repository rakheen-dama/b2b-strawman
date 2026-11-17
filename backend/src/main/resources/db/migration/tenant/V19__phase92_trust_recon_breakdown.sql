-- Phase 92 / 646C (ADR-377, architecture §16.2.11 and the trust_reconciliations block of §16.7.4, which
-- 646B's V18 left out): a reconciliation stores the creditor side as calculated as at its period_end —
-- investment creditors, the Fidelity Fund interest-and-charges ledger, unidentified deposits, the
-- inter-account term, the six-term creditor total and difference = adjusted bank − creditor total.
-- integrity_flags is created here and first written by 649C. Rows calculated before this migration
-- used the client ledgers as the whole creditor side, so their creditor_total is backfilled from
-- client_ledger_total, and every row's difference is backfilled as adjusted_bank_balance −
-- creditor_total (deviation from §16.7.4, orchestrator ruling O6: the architecture's single UPDATE left
-- difference = 0 on a calculated row whose client ledgers were 0 but whose adjusted bank was not).
-- No row is deleted. Idempotent (ADD COLUMN IF NOT EXISTS; the first backfill only touches rows still
-- at the default, the second only rows whose difference is not yet the computed one).

ALTER TABLE trust_reconciliations
  ADD COLUMN IF NOT EXISTS investment_creditor_total  numeric(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS fund_ledger_balance        numeric(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS unidentified_deposit_total numeric(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS inter_account_balance      numeric(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS creditor_total             numeric(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS difference                 numeric(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS integrity_flags            jsonb NOT NULL DEFAULT '[]'::jsonb;

UPDATE trust_reconciliations
   SET creditor_total = client_ledger_total
 WHERE creditor_total = 0 AND client_ledger_total <> 0;

UPDATE trust_reconciliations
   SET difference = adjusted_bank_balance - creditor_total
 WHERE adjusted_bank_balance IS NOT NULL
   AND difference IS DISTINCT FROM adjusted_bank_balance - creditor_total;
