-- 646A R-8 EXPLAIN: 50 000 synthetic posted DEPOSIT rows inside ONE transaction, then ROLLBACK.
\set ON_ERROR_STOP on
SET search_path TO tenant_7d218705360b;
\echo '== BEFORE counts'
SELECT (SELECT count(*) FROM trust_transactions) AS txns, (SELECT count(*) FROM customers) AS customers;
BEGIN;
-- 200 synthetic customers (FK target for customer_id)
INSERT INTO customers (id, name, email, created_by, customer_type, lifecycle_status)
SELECT ('00000000-0000-4000-8000-' || lpad(g::text, 12, '0'))::uuid,
       'R8 Synthetic Client ' || g, 'r8-synthetic-' || g || '@example.invalid',
       (SELECT id FROM members ORDER BY created_at LIMIT 1), 'INDIVIDUAL', 'ACTIVE'
  FROM generate_series(1, 200) g;
-- 50 000 posted DEPOSIT rows on the primary account, round-robin over the 200 customers,
-- transaction_date spread over ~2 years. Satisfies chk_trust_txn_posting (posted, RECORDED,
-- |ledger_effect| = amount, cashbook_effect = ledger_effect), amount > 0, type, customer_required.
INSERT INTO trust_transactions (trust_account_id, transaction_type, amount, customer_id, reference,
                                transaction_date, status, recorded_by, created_at,
                                ledger, cashbook_effect, ledger_effect, posted_at)
SELECT '5c1a152f-df92-49b3-a0e1-d636b03febba'::uuid, 'DEPOSIT', a.amt,
       ('00000000-0000-4000-8000-' || lpad(((g % 200) + 1)::text, 12, '0'))::uuid,
       'R8/SYN/' || g, DATE '2025-01-01' + (g % 700), 'RECORDED',
       (SELECT id FROM members ORDER BY created_at LIMIT 1), now(),
       'CLIENT', a.amt, a.amt, now()
  FROM generate_series(1, 50000) g
  CROSS JOIN LATERAL (SELECT ((g % 997) + 1) * 10.00::numeric(15,2) AS amt) a;
ANALYZE trust_transactions;
\echo '== index validity'
SELECT c.relname, i.indisvalid, i.indisready, pg_get_expr(i.indpred, i.indrelid) AS predicate
  FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
 WHERE c.relname = 'idx_trust_txn_ledger_sum' AND c.relnamespace = 'tenant_7d218705360b'::regnamespace;
\echo '== SUPPLEMENTARY-2: enable_seqscan = off, enable_bitmapscan = off'
SET LOCAL enable_seqscan = off;
SET LOCAL enable_bitmapscan = off;
\echo '######## S2-Q4 clientBalancePage(page 0, size 20)'
EXPLAIN (ANALYZE, BUFFERS)
SELECT t.customer_id AS customerId, COALESCE(SUM(t.ledger_effect), 0) AS balance,
       MAX(t.transaction_date) AS lastTransactionDate
  FROM trust_transactions t
 WHERE t.trust_account_id = '5c1a152f-df92-49b3-a0e1-d636b03febba' AND t.ledger = 'CLIENT'
   AND t.customer_id IS NOT NULL AND t.posted_at IS NOT NULL
 GROUP BY t.customer_id
 ORDER BY balance DESC, t.customer_id
 OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY;
ROLLBACK;
\echo '== AFTER ROLLBACK counts (must equal BEFORE)'
SELECT (SELECT count(*) FROM trust_transactions) AS txns, (SELECT count(*) FROM customers) AS customers,
       (SELECT count(*) FROM trust_transactions WHERE reference LIKE 'R8/SYN/%') AS synthetic_txns_left,
       (SELECT count(*) FROM customers WHERE email LIKE 'r8-synthetic-%') AS synthetic_customers_left;
