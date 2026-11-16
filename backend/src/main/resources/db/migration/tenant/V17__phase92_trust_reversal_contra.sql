-- Phase 92 / 649B (ADR-378, architecture §16.7.3): a reversal is a contra entry. A REVERSED original
-- keeps its effects; its posted REVERSAL row carries the negation. Net balances are unchanged for debit
-- and transfer reversals made under the old model (original 0 + reversal 0 becomes original x +
-- reversal -x); as-at history is restored (the original counts again on its own date, the contra on
-- the reversal's date).
--
-- Rows this migration changes (dev / e2e data only — no production data exists, R12 / founder decision 5):
--   1. REVERSED originals (zeroed by V16 step 3 and by 646A's postReversal) regain their registry effects.
--   2. A posted credit REVERSAL that never took effect (hypothesis H-a: approved, but the original was
--      never marked) now takes effect: its original is marked REVERSED. This can leave a dev client's
--      derived balance below zero; the integrity check (649C) reports it, nothing prevents it.
--   3. Posted REVERSAL rows take the negation of their original's effects and the original's ledger.
--   4. A posted REVERSAL with no original cannot be given an effect (none is expected): it is unposted
--      to REJECTED with rejection_reason 'Phase 92 migration: reversal with no original' rather than
--      failing the migration.
--   5. chk_trust_txn_posting is re-added in its tight form: a posted row moves its ledger by exactly
--      its amount (abs(ledger_effect) = amount).
-- Pending (AWAITING_APPROVAL) and REJECTED rows are unposted and untouched. Idempotent: on a re-run
-- steps 1-4 match nothing and step 5 re-adds the same constraint. DROP CONSTRAINT IF EXISTS is scoped to
-- the table in the current schema, so no catalog guard is needed.

-- 1. Reversed originals get their effects back (only the six pre-phase posting types exist here)
UPDATE trust_transactions o
   SET cashbook_effect = CASE
           WHEN o.transaction_type = 'DEPOSIT' THEN o.amount
           WHEN o.transaction_type IN ('PAYMENT', 'FEE_TRANSFER', 'REFUND') THEN -o.amount
           ELSE 0 END,
       ledger_effect = CASE
           WHEN o.transaction_type IN ('DEPOSIT', 'TRANSFER_IN') THEN o.amount
           ELSE -o.amount END
 WHERE o.status = 'REVERSED' AND o.transaction_type <> 'REVERSAL' AND o.ledger_effect = 0;

-- 2. A posted credit reversal that never took effect (hypothesis H-a rows): apply it now by marking
--    the original. Dev data only; this is the behaviour the approval was meant to have.
UPDATE trust_transactions o
   SET status = 'REVERSED', reversed_by_id = r.id
  FROM trust_transactions r
 WHERE r.reversal_of = o.id AND r.transaction_type = 'REVERSAL' AND r.posted_at IS NOT NULL
   AND o.status IN ('RECORDED', 'APPROVED');

-- 3. Posted REVERSAL rows carry the negation of their original
UPDATE trust_transactions r
   SET ledger = o.ledger, cashbook_effect = -o.cashbook_effect, ledger_effect = -o.ledger_effect
  FROM trust_transactions o
 WHERE r.reversal_of = o.id AND r.transaction_type = 'REVERSAL'
   AND r.posted_at IS NOT NULL AND r.ledger_effect = 0;

-- 4. A posted REVERSAL with no original cannot be given an effect; none is expected. Unpost it
--    rather than fail the migration.
UPDATE trust_transactions SET status = 'REJECTED', posted_at = NULL,
       rejection_reason = 'Phase 92 migration: reversal with no original'
 WHERE transaction_type = 'REVERSAL' AND posted_at IS NOT NULL AND ledger_effect = 0;

-- 5. Tighten the row shape: a posted row always moves its ledger by exactly its amount.
--    Drop and re-add unconditionally: no guard on how the old definition is rendered, and
--    re-running is harmless (the constraint is re-validated against rows steps 1-4 made conform).
ALTER TABLE trust_transactions DROP CONSTRAINT IF EXISTS chk_trust_txn_posting;
ALTER TABLE trust_transactions ADD CONSTRAINT chk_trust_txn_posting CHECK (
       (posted_at IS NULL AND status IN ('AWAITING_APPROVAL', 'REJECTED')
          AND cashbook_effect = 0 AND ledger_effect = 0)
    OR (posted_at IS NOT NULL AND status IN ('RECORDED', 'APPROVED', 'REVERSED')
          AND abs(ledger_effect) = amount
          AND (cashbook_effect = ledger_effect OR cashbook_effect = 0)));
