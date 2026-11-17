-- Phase 92 / 647A (ADR-380, ADR-381; architecture §16.2.2, §16.2.6, §16.7.5): a statement line can be
-- recorded from — it gains a classification (non-null exactly when match_status = 'RECORDED', which the
-- swapped match-status CHECK now admits) and a parser suggestion column that 647C first writes; a
-- BANK_CHARGE row carries whether it is recoverable and its VAT (both non-null exactly on BANK_CHARGE,
-- 0 <= VAT < amount). Existing rows need no change: no line is RECORDED and no BANK_CHARGE row exists,
-- so all three CHECKs validate as added. No row is deleted. No new index (idx_bank_line_unmatched and
-- idx_trust_txn_reconciliation already serve the queries). Idempotent: ADD COLUMN IF NOT EXISTS, and every
-- constraint is guarded on pg_constraint scoped to current_schema().
ALTER TABLE bank_statement_lines ADD COLUMN IF NOT EXISTS classification varchar(30);
ALTER TABLE bank_statement_lines ADD COLUMN IF NOT EXISTS suggested_classification varchar(30);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
              WHERE c.conname = 'chk_bank_line_match_status' AND n.nspname = current_schema()
                AND pg_get_constraintdef(c.oid) NOT LIKE '%RECORDED%') THEN
    ALTER TABLE bank_statement_lines DROP CONSTRAINT chk_bank_line_match_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_bank_line_match_status' AND n.nspname = current_schema()) THEN
    ALTER TABLE bank_statement_lines ADD CONSTRAINT chk_bank_line_match_status
      CHECK (match_status IN ('UNMATCHED', 'AUTO_MATCHED', 'MANUALLY_MATCHED', 'EXCLUDED', 'RECORDED'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_bank_line_classification' AND n.nspname = current_schema()) THEN
    ALTER TABLE bank_statement_lines ADD CONSTRAINT chk_bank_line_classification CHECK (
          (classification IS NOT NULL) = (match_status = 'RECORDED')
      AND (classification IS NULL OR classification IN ('FUND_INTEREST', 'FUND_SWEEP', 'BANK_CHARGE',
             'CHARGE_REFUND', 'UNIDENTIFIED_DEPOSIT', 'INVESTMENT_INTEREST'))
      AND (suggested_classification IS NULL OR suggested_classification IN ('FUND_INTEREST',
             'FUND_SWEEP', 'BANK_CHARGE', 'CHARGE_REFUND', 'INVESTMENT_INTEREST')));
  END IF;
END $$;

ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS charge_recoverable boolean;
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS charge_vat_amount numeric(15,2);
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_charge' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_charge CHECK (
         (transaction_type = 'BANK_CHARGE' AND charge_recoverable IS NOT NULL
            AND charge_vat_amount IS NOT NULL AND charge_vat_amount >= 0 AND charge_vat_amount < amount)
      OR (transaction_type <> 'BANK_CHARGE' AND charge_recoverable IS NULL AND charge_vat_amount IS NULL));
  END IF;
END $$;
