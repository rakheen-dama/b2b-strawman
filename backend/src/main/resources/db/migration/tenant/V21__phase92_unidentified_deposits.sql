-- Phase 92 / 647B (ADR-376; architecture §16.2.5, §16.7.6): itemised unidentified deposits. A bank credit
-- whose owner is not yet known is recorded from its statement line as an UNIDENTIFIED_DEPOSIT row on the
-- account's unidentified-deposits ledger, and opens one item here (HELD). The item is saved first so the
-- row can name it (chk_trust_txn_unidentified_item), then the item records its opening row
-- (deposit_transaction_id, null only inside the creating transaction). Every row that moves the ledger
-- names its item, so "open as at a date" is derived from the item's own rows (EQ-4), never from its status.
-- 647D adds the resolution (allocation to a client and matter, payment to the Fund) — its columns ship now.
-- Existing rows need no change: no pre-phase row has one of the three item types (the demo seeder posts
-- none), so the new CHECK validates as added. No backfill, no row deleted, no ON DELETE CASCADE.
-- Idempotent: CREATE ... IF NOT EXISTS, ADD COLUMN IF NOT EXISTS, and every constraint added to an existing
-- table is guarded on pg_constraint scoped to current_schema().
CREATE TABLE IF NOT EXISTS unidentified_deposits (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    trust_account_id uuid NOT NULL,
    creditor_account_id uuid NOT NULL,
    deposit_transaction_id uuid,                -- null only inside the creating transaction
    bank_statement_line_id uuid NOT NULL,
    received_date date NOT NULL,
    bank_description varchar(500) NOT NULL,
    bank_reference varchar(200),
    amount numeric(15,2) NOT NULL,
    status varchar(20) DEFAULT 'HELD' NOT NULL,
    resolution_transaction_id uuid,
    allocation_credit_transaction_id uuid,
    allocated_customer_id uuid,
    allocated_project_id uuid,
    resolved_date date,
    notes text,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT unidentified_deposits_pkey PRIMARY KEY (id),
    CONSTRAINT uq_unidentified_deposit_txn UNIQUE (deposit_transaction_id),
    CONSTRAINT chk_unidentified_deposit_amount CHECK (amount > 0),
    CONSTRAINT chk_unidentified_deposit_status
        CHECK (status IN ('HELD', 'ALLOCATED', 'PAID_TO_FUND', 'REVERSED')),
    CONSTRAINT chk_unidentified_deposit_resolved CHECK (
        (status = 'HELD' AND resolved_date IS NULL)
     OR (status IN ('ALLOCATED', 'PAID_TO_FUND', 'REVERSED') AND resolved_date IS NOT NULL)),
        -- resolved_date = transaction_date of the row that took the item out of HELD
        -- (for REVERSED: the contra of the opening deposit, dated the statement line's date)
    CONSTRAINT chk_unidentified_deposit_allocated CHECK (
        status <> 'ALLOCATED'
     OR (allocated_customer_id IS NOT NULL AND allocated_project_id IS NOT NULL
         AND allocation_credit_transaction_id IS NOT NULL)),
    CONSTRAINT unidentified_deposits_trust_account_id_fkey FOREIGN KEY (trust_account_id) REFERENCES trust_accounts(id),
    CONSTRAINT unidentified_deposits_creditor_account_id_fkey FOREIGN KEY (creditor_account_id) REFERENCES trust_creditor_accounts(id),
    CONSTRAINT unidentified_deposits_deposit_txn_fkey FOREIGN KEY (deposit_transaction_id) REFERENCES trust_transactions(id),
    CONSTRAINT unidentified_deposits_line_fkey FOREIGN KEY (bank_statement_line_id) REFERENCES bank_statement_lines(id),
    CONSTRAINT unidentified_deposits_resolution_txn_fkey FOREIGN KEY (resolution_transaction_id) REFERENCES trust_transactions(id),
    CONSTRAINT unidentified_deposits_alloc_credit_txn_fkey FOREIGN KEY (allocation_credit_transaction_id) REFERENCES trust_transactions(id),
    CONSTRAINT unidentified_deposits_customer_fkey FOREIGN KEY (allocated_customer_id) REFERENCES customers(id),
    CONSTRAINT unidentified_deposits_project_fkey FOREIGN KEY (allocated_project_id) REFERENCES projects(id)
);
CREATE INDEX IF NOT EXISTS idx_unidentified_deposits_account_status
    ON unidentified_deposits (trust_account_id, status, received_date);

-- Every row that moves an unidentified-deposits ledger names its item, so "open as at a date"
-- is derived from the ledger itself (EQ-4), not from the item's current status.
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS unidentified_deposit_id uuid;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'trust_transactions_unidentified_deposit_id_fkey' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT trust_transactions_unidentified_deposit_id_fkey
      FOREIGN KEY (unidentified_deposit_id) REFERENCES unidentified_deposits(id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_unidentified_item' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_unidentified_item CHECK (
         transaction_type NOT IN ('UNIDENTIFIED_DEPOSIT', 'UNIDENT_ALLOC_OUT', 'UNIDENT_FUND_PAY')
      OR unidentified_deposit_id IS NOT NULL);
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_trust_txn_unidentified_deposit
    ON trust_transactions (unidentified_deposit_id, transaction_date)
    WHERE unidentified_deposit_id IS NOT NULL;
