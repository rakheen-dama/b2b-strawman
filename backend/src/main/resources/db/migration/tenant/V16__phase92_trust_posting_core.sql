-- Phase 92 / 646A (ADR-378): every posted trust transaction carries its own effect; client ledger
-- balances are derived. Idempotent; catalog guards scoped to current_schema().
--
-- Rows that cannot be carried into the new model are deleted (R12, founder decision 5 — no
-- production data): the calculated interest run's postings (INTEREST_CREDIT, INTEREST_LPFF) and
-- their reversals. Their links (allocation lines, allocations, statement lines) are unlinked first
-- and a POSTED run returns to APPROVED. No ON DELETE CASCADE (house rule).

-- 1. Remove postings made by the calculated interest run (INTEREST_CREDIT, INTEREST_LPFF) and their
--    reversals. No real tenant has posted a run (founder decision 5); the guard (645A) stops new
--    ones. INTEREST_LPFF has a cashbook effect and no ledger, so it cannot satisfy the posting CHECK.
UPDATE interest_allocation_lines SET trust_transaction_id = NULL WHERE trust_transaction_id IS NOT NULL;
UPDATE interest_allocations      SET trust_transaction_id = NULL WHERE trust_transaction_id IS NOT NULL;
UPDATE interest_runs SET status = 'APPROVED', posted_at = NULL WHERE status = 'POSTED';

UPDATE bank_statement_lines l
   SET match_status = 'UNMATCHED', trust_transaction_id = NULL, match_confidence = NULL
 WHERE l.trust_transaction_id IN (
         SELECT t.id FROM trust_transactions t
          WHERE t.transaction_type IN ('INTEREST_CREDIT', 'INTEREST_LPFF')
             OR t.reversal_of IN (SELECT i.id FROM trust_transactions i
                                   WHERE i.transaction_type IN ('INTEREST_CREDIT', 'INTEREST_LPFF')));
UPDATE bank_statements s
   SET matched_count = (SELECT count(*) FROM bank_statement_lines l
                         WHERE l.bank_statement_id = s.id
                           AND l.match_status IN ('AUTO_MATCHED', 'MANUALLY_MATCHED'));

UPDATE trust_transactions SET reversed_by_id = NULL
 WHERE transaction_type IN ('INTEREST_CREDIT', 'INTEREST_LPFF') AND reversed_by_id IS NOT NULL;
DELETE FROM trust_transactions
 WHERE reversal_of IN (SELECT i.id FROM trust_transactions i
                        WHERE i.transaction_type IN ('INTEREST_CREDIT', 'INTEREST_LPFF'));
DELETE FROM trust_transactions WHERE transaction_type IN ('INTEREST_CREDIT', 'INTEREST_LPFF');

-- 2. Effect columns
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS ledger varchar(20) NOT NULL DEFAULT 'CLIENT';
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS cashbook_effect numeric(15,2) NOT NULL DEFAULT 0;
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS ledger_effect   numeric(15,2) NOT NULL DEFAULT 0;
ALTER TABLE trust_transactions ADD COLUMN IF NOT EXISTS posted_at timestamptz;

-- 3. Backfill with HEAD semantics: RECORDED / APPROVED rows count; a REVERSED original and every
--    REVERSAL row count 0 (649B changes that). posted_at IS NULL makes this run once.
UPDATE trust_transactions t
   SET posted_at = COALESCE(t.second_approved_at, t.approved_at, t.created_at),
       cashbook_effect = CASE
           WHEN t.status = 'REVERSED' THEN 0
           WHEN t.transaction_type = 'DEPOSIT' THEN t.amount
           WHEN t.transaction_type IN ('PAYMENT', 'FEE_TRANSFER', 'REFUND') THEN -t.amount
           ELSE 0 END,
       ledger_effect = CASE
           WHEN t.status = 'REVERSED' THEN 0
           WHEN t.transaction_type IN ('DEPOSIT', 'TRANSFER_IN') THEN t.amount
           WHEN t.transaction_type IN ('PAYMENT', 'FEE_TRANSFER', 'REFUND', 'TRANSFER_OUT') THEN -t.amount
           ELSE 0 END
 WHERE t.posted_at IS NULL AND t.status IN ('RECORDED', 'APPROVED', 'REVERSED');

-- 4. Row shape (first form; tightened in the 649B migration)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_posting' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_posting CHECK (
         (posted_at IS NULL AND status IN ('AWAITING_APPROVAL', 'REJECTED')
            AND cashbook_effect = 0 AND ledger_effect = 0)
      OR (posted_at IS NOT NULL AND status IN ('RECORDED', 'APPROVED', 'REVERSED')
            AND abs(ledger_effect) IN (0, amount)
            AND (cashbook_effect = ledger_effect OR cashbook_effect = 0)));
  END IF;
END $$;

-- 5. The two interest types leave the type CHECK (V15 swap pattern)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
              WHERE c.conname = 'chk_trust_txn_type' AND n.nspname = current_schema()
                AND pg_get_constraintdef(c.oid) LIKE '%INTEREST_LPFF%') THEN
    ALTER TABLE trust_transactions DROP CONSTRAINT chk_trust_txn_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
                  WHERE c.conname = 'chk_trust_txn_type' AND n.nspname = current_schema()) THEN
    ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_type CHECK (transaction_type IN (
      'DEPOSIT', 'PAYMENT', 'TRANSFER_IN', 'TRANSFER_OUT', 'FEE_TRANSFER', 'REFUND', 'REVERSAL'));
  END IF;
END $$;

-- 6. client_ledger_cards becomes the per-client lock anchor: stored balance and totals go.
--    Dropping a column drops the CHECKs that mention only that column.
DROP INDEX IF EXISTS idx_client_ledger_balance;
ALTER TABLE client_ledger_cards
  DROP COLUMN IF EXISTS balance,
  DROP COLUMN IF EXISTS total_deposits,
  DROP COLUMN IF EXISTS total_payments,
  DROP COLUMN IF EXISTS total_fee_transfers,
  DROP COLUMN IF EXISTS total_interest_credited,
  DROP COLUMN IF EXISTS last_transaction_date;

-- A lock anchor for every client that has rows but no card (demo-seeder rows, inventory §0.2)
INSERT INTO client_ledger_cards (trust_account_id, customer_id)
SELECT DISTINCT t.trust_account_id, t.customer_id
  FROM trust_transactions t WHERE t.customer_id IS NOT NULL
ON CONFLICT (trust_account_id, customer_id) DO NOTHING;

-- 7. Index for every derived balance
CREATE INDEX IF NOT EXISTS idx_trust_txn_ledger_sum
    ON trust_transactions (trust_account_id, ledger, customer_id, transaction_date)
    INCLUDE (cashbook_effect, ledger_effect, project_id)
    WHERE posted_at IS NOT NULL;
