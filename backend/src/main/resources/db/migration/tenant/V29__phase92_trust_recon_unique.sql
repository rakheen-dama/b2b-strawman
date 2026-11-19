-- Phase 92 / 652C (architecture §16.3.12, §16.7.11; reproduced by
-- TrustReconciliationDraftIntegrationTest): one reconciliation per trust account and period end.
-- createReconciliation becomes get-or-create; this unique index backs it.
--
-- Rows that cannot be carried (R12, founder decision 5 — no production data; §16.7.1): before this
-- migration every wizard reset wrote another DRAFT, and before V25 (650A) an account could be
-- completed twice to one month end (a mid-month period end still could). For each
-- (trust_account_id, period_end) holding more than one row ONE row is kept and the others are
-- DELETED. The row kept is, in this order of preference:
--   1. the row a Rule 54.15 monthly list references (trust_monthly_lists.reconciliation_id);
--   2. a COMPLETED row over a DRAFT;
--   3. the most recently completed, then the most recently updated, then the highest id.
-- So a duplicate DRAFT always goes, and an older COMPLETED duplicate no list references goes too
-- (dev data only). A row a monthly list references is never deleted (the NOT EXISTS below); were
-- two rows of one key both referenced — impossible through the product,
-- uq_trust_monthly_list_account_period — nothing is deleted for that key's referenced rows and
-- CREATE UNIQUE INDEX fails loudly instead.
--
-- A deleted row has no dependants: trust_monthly_lists is the only table with a foreign key into
-- trust_reconciliations (V25). Its bank statement and that statement's lines (matches, exclusions,
-- recorded entries) belong to the statement and are left as they are. Audit history keeps the
-- deleted rows' events (audit rows are never rewritten).
--
-- idx_trust_recon_account_period (trust_account_id, period_end DESC) is kept: it still serves
-- "latest first" (§16.7.11). Idempotent: the DELETE finds nothing on a second run and the index is
-- CREATE ... IF NOT EXISTS; no catalog lookup, so no current_schema() guard is needed. No CASCADE.
WITH ranked AS (
    SELECT r.id,
           row_number() OVER (
               PARTITION BY r.trust_account_id, r.period_end
               ORDER BY EXISTS (SELECT 1 FROM trust_monthly_lists m WHERE m.reconciliation_id = r.id) DESC,
                        (r.status = 'COMPLETED') DESC,
                        r.completed_at DESC NULLS LAST,
                        r.updated_at DESC,
                        r.id DESC) AS keep_rank
      FROM trust_reconciliations r
)
DELETE FROM trust_reconciliations r
 USING ranked
 WHERE r.id = ranked.id
   AND ranked.keep_rank > 1
   AND NOT EXISTS (SELECT 1 FROM trust_monthly_lists m WHERE m.reconciliation_id = r.id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_trust_recon_account_period
    ON trust_reconciliations (trust_account_id, period_end);
