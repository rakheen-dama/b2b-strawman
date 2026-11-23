-- Phase 93 / 657A (architecture §17.2.8–17.2.10, §17.7.3, ADR-386, ADR-387): trust opening entries.
-- Tenant V33 (guide V31/V32; V31 = phase92 idempotency keys, V32 = 653A take-on core; next free).
-- Adds columns with defaults/nulls; every existing row satisfies the new CHECKs (take_on_batch_id IS
-- NULL, cleared_at_cutover = false, every unidentified item has a line). No data change. No CASCADE.
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS take_on_batch_id uuid;
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS cleared_at_cutover boolean NOT NULL DEFAULT false;
ALTER TABLE trust_accounts ADD COLUMN IF NOT EXISTS take_on_cutover_date date;
ALTER TABLE trust_accounts ADD COLUMN IF NOT EXISTS take_on_batch_id uuid;
ALTER TABLE unidentified_deposits ADD COLUMN IF NOT EXISTS take_on_batch_id uuid;
ALTER TABLE unidentified_deposits ALTER COLUMN bank_statement_line_id DROP NOT NULL;
ALTER TABLE trust_investments ADD COLUMN IF NOT EXISTS instruction_reference varchar(200);
ALTER TABLE trust_investments ADD COLUMN IF NOT EXISTS instruction_date date;
ALTER TABLE trust_investments ADD COLUMN IF NOT EXISTS endorsement_confirmed boolean NOT NULL DEFAULT false;
ALTER TABLE trust_investments ADD COLUMN IF NOT EXISTS accrued_interest_at_take_on numeric(15,2);
ALTER TABLE trust_investments ADD COLUMN IF NOT EXISTS fund_amount_at_take_on numeric(15,2);
ALTER TABLE trust_investments ADD COLUMN IF NOT EXISTS take_on_batch_id uuid;

DO $$
BEGIN
  -- The type list (V18 defined the 25 Phase 92 names): drop it only if it lacks the take-on types.
  IF EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
              WHERE c.conname = 'chk_trust_txn_type' AND n.nspname = current_schema()
                AND pg_get_constraintdef(c.oid) NOT LIKE '%TAKEON_DEP_OFFSET%') THEN
    ALTER TABLE trust_transactions DROP CONSTRAINT chk_trust_txn_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_type' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_type CHECK (transaction_type IN (
      'DEPOSIT', 'PAYMENT', 'TRANSFER_IN', 'TRANSFER_OUT', 'FEE_TRANSFER', 'REFUND', 'REVERSAL',
      'FUND_INTEREST', 'FUND_SWEEP', 'BANK_CHARGE', 'CHARGE_REFUND',
      'UNIDENTIFIED_DEPOSIT', 'UNIDENT_ALLOC_OUT', 'UNIDENT_ALLOC_IN', 'UNIDENT_FUND_PAY',
      'INV_PLACE', 'INV_RECEIVE', 'INV_INTEREST', 'FUND_INV_SHARE', 'INV_WITHDRAW', 'INV_RETURN',
      'FIRM_INV_PLACE', 'FIRM_INV_RECEIVE', 'FIRM_INV_WITHDRAW', 'FIRM_INV_RETURN',
      'TAKEON_CLIENT', 'TAKEON_FUND', 'TAKEON_FUND_DEBIT', 'TAKEON_UNIDENTIFIED', 'TAKEON_INVESTMENT',
      'TAKEON_FIRM_INV_OUT', 'TAKEON_FIRM_INV_IN', 'TAKEON_PAY_UNPRES', 'TAKEON_PAY_OFFSET',
      'TAKEON_DEP_TRANSIT', 'TAKEON_DEP_OFFSET'));
  END IF;

  -- TAKEON_UNIDENTIFIED rows name their item, like UNIDENTIFIED_DEPOSIT (EQ-4 (a)); V21 defined 3 types.
  IF EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
              WHERE c.conname = 'chk_trust_txn_unidentified_item' AND n.nspname = current_schema()
                AND pg_get_constraintdef(c.oid) NOT LIKE '%TAKEON_UNIDENTIFIED%') THEN
    ALTER TABLE trust_transactions DROP CONSTRAINT chk_trust_txn_unidentified_item;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_unidentified_item' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_unidentified_item CHECK (
         transaction_type NOT IN ('UNIDENTIFIED_DEPOSIT', 'UNIDENT_ALLOC_OUT', 'UNIDENT_FUND_PAY', 'TAKEON_UNIDENTIFIED')
      OR unidentified_deposit_id IS NOT NULL);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_take_on_batch' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_take_on_batch CHECK (
      (left(transaction_type, 7) = 'TAKEON_') = (take_on_batch_id IS NOT NULL));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_cutover_cleared' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_cutover_cleared CHECK (
      NOT cleared_at_cutover OR (take_on_batch_id IS NOT NULL AND bank_statement_line_id IS NULL));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'trust_transactions_take_on_batch_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT trust_transactions_take_on_batch_fkey
      FOREIGN KEY (take_on_batch_id) REFERENCES take_on_batches(id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_account_take_on' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_accounts ADD CONSTRAINT chk_trust_account_take_on CHECK (
      (take_on_cutover_date IS NULL) = (take_on_batch_id IS NULL));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'trust_accounts_take_on_batch_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_accounts ADD CONSTRAINT trust_accounts_take_on_batch_fkey
      FOREIGN KEY (take_on_batch_id) REFERENCES take_on_batches(id);
  END IF;

  -- An item comes from exactly one of: a statement line, a take-on.
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_unidentified_deposit_origin' AND n.nspname = current_schema()) THEN
    ALTER TABLE unidentified_deposits ADD CONSTRAINT chk_unidentified_deposit_origin CHECK (
      (bank_statement_line_id IS NULL) = (take_on_batch_id IS NOT NULL));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'unidentified_deposits_take_on_batch_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE unidentified_deposits ADD CONSTRAINT unidentified_deposits_take_on_batch_fkey
      FOREIGN KEY (take_on_batch_id) REFERENCES take_on_batches(id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_invest_take_on_instruction' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_investments ADD CONSTRAINT chk_trust_invest_take_on_instruction CHECK (
         take_on_batch_id IS NULL OR investment_basis <> 'CLIENT_INSTRUCTION'
      OR (instruction_reference IS NOT NULL AND instruction_date IS NOT NULL AND endorsement_confirmed));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_invest_take_on_amounts' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_investments ADD CONSTRAINT chk_trust_invest_take_on_amounts CHECK (
      coalesce(accrued_interest_at_take_on, 0) >= 0 AND coalesce(fund_amount_at_take_on, 0) >= 0);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'trust_investments_take_on_batch_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_investments ADD CONSTRAINT trust_investments_take_on_batch_fkey
      FOREIGN KEY (take_on_batch_id) REFERENCES take_on_batches(id);
  END IF;
END $$;

-- X5: one brought-forward balance per client and matter per account.
CREATE UNIQUE INDEX IF NOT EXISTS uq_trust_txn_takeon_client_matter
    ON trust_transactions (trust_account_id, customer_id, project_id) WHERE transaction_type = 'TAKEON_CLIENT';
-- Auto-match pool: unmatched take-on outstanding items, regardless of date (§17.3.11).
CREATE INDEX IF NOT EXISTS idx_trust_txn_take_on_open_items
    ON trust_transactions (trust_account_id)
    WHERE take_on_batch_id IS NOT NULL AND NOT cleared_at_cutover AND bank_statement_line_id IS NULL;
