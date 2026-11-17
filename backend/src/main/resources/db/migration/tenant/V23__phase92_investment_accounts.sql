-- Phase 92 / 648A (ADR-379, ADR-383; architecture §16.2.7, the trust_investments part of §16.7.7):
-- investments sit in INVESTMENT trust accounts and stay on the books. trust_account_id stays the
-- funding trust banking account; investment_account_id is the INVESTMENT account holding the money
-- (its basis must equal the investment's — a service check, as is "funding account is not an
-- investment account"). A section 86(4) investment (CLIENT_INSTRUCTION) names its client and
-- matter; a section 86(3) investment (FIRM_DISCRETION) names neither.
--
-- Legacy trust_investments rows are DELETED (ruling #12): under the old model a placement was a
-- PAYMENT that took the money off the books, and no investment account exists to carry it to. The
-- legacy placement PAYMENT and withdrawal DEPOSIT rows stay as the ordinary payments and deposits
-- they were posted as, so every balance is unchanged. No pre-phase trust row carries an
-- investment_id (V18), so the delete violates no foreign key. The delete is guarded by the absence
-- of investment_account_id, so a re-run can never delete new-shape rows.
--
-- deposit_transaction_id becomes nullable: the investment is saved before its placement row (which
-- must carry investment_id), and the column is set before commit (§16.3.7 insert order).
--
-- 648C found.md F-1: chk_trust_invest_client_by_basis names investment_basis IS NOT NULL
-- explicitly and tests every column with IS [NOT] NULL — a CHECK passes on NULL.
--
-- Idempotent; every catalog guard scoped to current_schema() (BASELINE.md §5).
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                  WHERE table_schema = current_schema() AND table_name = 'trust_investments'
                    AND column_name = 'investment_account_id') THEN
    DELETE FROM trust_investments;
    ALTER TABLE trust_investments ADD COLUMN investment_account_id uuid NOT NULL;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'trust_investments_investment_account_id_fkey'
                    AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_investments ADD CONSTRAINT trust_investments_investment_account_id_fkey
      FOREIGN KEY (investment_account_id) REFERENCES trust_accounts(id);
  END IF;
END $$;

ALTER TABLE trust_investments ADD COLUMN IF NOT EXISTS project_id uuid;
ALTER TABLE trust_investments ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE trust_investments ALTER COLUMN deposit_transaction_id DROP NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'trust_investments_project_id_fkey'
                    AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_investments ADD CONSTRAINT trust_investments_project_id_fkey
      FOREIGN KEY (project_id) REFERENCES projects(id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_invest_client_by_basis'
                    AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_investments ADD CONSTRAINT chk_trust_invest_client_by_basis CHECK (
      investment_basis IS NOT NULL AND (
           (investment_basis = 'CLIENT_INSTRUCTION' AND customer_id IS NOT NULL
              AND project_id IS NOT NULL)
        OR (investment_basis = 'FIRM_DISCRETION' AND customer_id IS NULL AND project_id IS NULL)));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_trust_invest_investment_account
    ON trust_investments (investment_account_id, status);
