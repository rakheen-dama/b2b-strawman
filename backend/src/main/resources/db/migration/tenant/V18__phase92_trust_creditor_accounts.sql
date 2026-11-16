-- Phase 92 / 646B (ADR-376, architecture §16.7.4 minus the trust_reconciliations block, which is
-- 646C's): the non-client trust creditor ledger — typed creditor accounts per trust account (the
-- Legal Practitioners' Fidelity Fund interest-and-charges ledger and the unidentified-deposits
-- ledger), both opened for every existing trust account, open or closed; the client-required
-- constraint becomes a ledger-target constraint (four ledger cases, §16.2.2); the full Phase 92
-- type list (25 names, all <= 20 characters; R5). Idempotent; catalog guards scoped to
-- current_schema(). No rows are changed or deleted: every existing row is ledger = 'CLIENT' with a
-- client (V16 removed the only client-less rows), so chk_trust_txn_ledger_target validates.

CREATE TABLE IF NOT EXISTS trust_creditor_accounts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    trust_account_id uuid NOT NULL,
    account_type varchar(40) NOT NULL,          -- validated by TrustCreditorAccountType; no CHECK on purpose
    name varchar(200) NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT trust_creditor_accounts_pkey PRIMARY KEY (id),
    CONSTRAINT trust_creditor_accounts_trust_account_id_fkey
        FOREIGN KEY (trust_account_id) REFERENCES trust_accounts(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_trust_creditor_account_singleton
    ON trust_creditor_accounts (trust_account_id, account_type)
    WHERE account_type IN ('FUND_INTEREST_AND_CHARGES', 'UNIDENTIFIED_DEPOSITS');

-- Both ledgers for every existing trust account, open or closed
INSERT INTO trust_creditor_accounts (trust_account_id, account_type, name)
SELECT a.id, v.account_type, v.name
  FROM trust_accounts a
 CROSS JOIN (VALUES
   ('FUND_INTEREST_AND_CHARGES', 'Legal Practitioners'' Fidelity Fund — interest and charges'),
   ('UNIDENTIFIED_DEPOSITS',     'Unidentified deposits')) AS v(account_type, name)
ON CONFLICT DO NOTHING;

ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS creditor_account_id uuid;
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS investment_id uuid;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'trust_transactions_creditor_account_id_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT trust_transactions_creditor_account_id_fkey
      FOREIGN KEY (creditor_account_id) REFERENCES trust_creditor_accounts(id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'trust_transactions_investment_id_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT trust_transactions_investment_id_fkey
      FOREIGN KEY (investment_id) REFERENCES trust_investments(id);
  END IF;

  -- "client or non-client creditor account, never both" (plus the inter-account case, §16.2.2)
  ALTER TABLE trust_transactions DROP CONSTRAINT IF EXISTS chk_trust_txn_customer_required;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_ledger_target' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_ledger_target CHECK (
         (ledger = 'CLIENT' AND customer_id IS NOT NULL AND creditor_account_id IS NULL)
      OR (ledger = 'CLIENT_INVESTMENT' AND customer_id IS NOT NULL AND investment_id IS NOT NULL
            AND creditor_account_id IS NULL)
      OR (ledger = 'NON_CLIENT' AND creditor_account_id IS NOT NULL
            AND customer_id IS NULL AND project_id IS NULL)
      OR (ledger = 'INTER_ACCOUNT' AND investment_id IS NOT NULL AND customer_id IS NULL
            AND project_id IS NULL AND creditor_account_id IS NULL));
  END IF;

  -- The full Phase 92 type list (all names <= 20 characters; R5)
  IF EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
              WHERE c.conname = 'chk_trust_txn_type' AND n.nspname = current_schema()
                AND pg_get_constraintdef(c.oid) NOT LIKE '%FUND_INTEREST%') THEN
    ALTER TABLE trust_transactions DROP CONSTRAINT chk_trust_txn_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_type' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_type CHECK (transaction_type IN (
      'DEPOSIT', 'PAYMENT', 'TRANSFER_IN', 'TRANSFER_OUT', 'FEE_TRANSFER', 'REFUND', 'REVERSAL',
      'FUND_INTEREST', 'FUND_SWEEP', 'BANK_CHARGE', 'CHARGE_REFUND',
      'UNIDENTIFIED_DEPOSIT', 'UNIDENT_ALLOC_OUT', 'UNIDENT_ALLOC_IN', 'UNIDENT_FUND_PAY',
      'INV_PLACE', 'INV_RECEIVE', 'INV_INTEREST', 'FUND_INV_SHARE', 'INV_WITHDRAW', 'INV_RETURN',
      'FIRM_INV_PLACE', 'FIRM_INV_RECEIVE', 'FIRM_INV_WITHDRAW', 'FIRM_INV_RETURN'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_trust_txn_creditor_account
    ON trust_transactions (creditor_account_id, transaction_date)
    WHERE creditor_account_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trust_txn_investment
    ON trust_transactions (investment_id) WHERE investment_id IS NOT NULL;
