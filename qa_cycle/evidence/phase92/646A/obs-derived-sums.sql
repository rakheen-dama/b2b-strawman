-- 646A OBS: read-only derived sums vs UI (lifecycle stack, after 105/105)
SET search_path TO tenant_7d218705360b;
\echo '== accounts'
SELECT id, account_name, is_primary FROM trust_accounts;
\echo '== all trust_transactions (posting state)'
SELECT t.transaction_type, t.status, t.amount, t.ledger, t.cashbook_effect, t.ledger_effect,
       (t.posted_at IS NOT NULL) AS posted, c.name AS client, t.reference
  FROM trust_transactions t LEFT JOIN customers c ON c.id = t.customer_id
 ORDER BY t.created_at;
\echo '== per-client derived balance: SUM(ledger_effect) WHERE posted AND ledger=CLIENT'
SELECT c.name, t.customer_id, SUM(t.ledger_effect) AS derived_balance
  FROM trust_transactions t JOIN customers c ON c.id = t.customer_id
 WHERE t.posted_at IS NOT NULL AND t.ledger = 'CLIENT'
 GROUP BY c.name, t.customer_id ORDER BY derived_balance DESC;
\echo '== per-client HEAD-style sign-CASE over status IN (RECORDED, APPROVED)'
SELECT c.name, t.customer_id,
       SUM(CASE WHEN t.transaction_type IN ('DEPOSIT','TRANSFER_IN') THEN t.amount
                WHEN t.transaction_type IN ('PAYMENT','FEE_TRANSFER','REFUND','TRANSFER_OUT') THEN -t.amount
                ELSE 0 END) AS head_style_balance
  FROM trust_transactions t JOIN customers c ON c.id = t.customer_id
 WHERE t.status IN ('RECORDED','APPROVED')
 GROUP BY c.name, t.customer_id ORDER BY head_style_balance DESC;
\echo '== account cashbook: SUM(cashbook_effect) WHERE posted'
SELECT t.trust_account_id, SUM(t.cashbook_effect) AS derived_cashbook
  FROM trust_transactions t WHERE t.posted_at IS NOT NULL GROUP BY t.trust_account_id;
\echo '== account cashbook HEAD-style sign-CASE over status IN (RECORDED, APPROVED)'
SELECT t.trust_account_id,
       SUM(CASE WHEN t.transaction_type = 'DEPOSIT' THEN t.amount
                WHEN t.transaction_type IN ('PAYMENT','FEE_TRANSFER','REFUND') THEN -t.amount
                ELSE 0 END) AS head_style_cashbook
  FROM trust_transactions t WHERE t.status IN ('RECORDED','APPROVED') GROUP BY t.trust_account_id;
\echo '== EQ-2: cashbook - sum(client ledger balances)'
SELECT (SELECT COALESCE(SUM(cashbook_effect),0) FROM trust_transactions WHERE posted_at IS NOT NULL) AS cashbook,
       (SELECT COALESCE(SUM(ledger_effect),0) FROM trust_transactions WHERE posted_at IS NOT NULL AND ledger='CLIENT') AS sum_client_ledgers,
       (SELECT COALESCE(SUM(cashbook_effect),0) FROM trust_transactions WHERE posted_at IS NOT NULL)
     - (SELECT COALESCE(SUM(ledger_effect),0) FROM trust_transactions WHERE posted_at IS NOT NULL AND ledger='CLIENT') AS difference;
\echo '== invariant: posted-status rows with posted_at NULL (expect 0)'
SELECT count(*) FROM trust_transactions WHERE status IN ('RECORDED','APPROVED','REVERSED') AND posted_at IS NULL;
\echo '== invariant: unposted-status rows with posted_at set or non-zero effect (expect 0)'
SELECT count(*) FROM trust_transactions WHERE status IN ('AWAITING_APPROVAL','REJECTED')
   AND (posted_at IS NOT NULL OR cashbook_effect <> 0 OR ledger_effect <> 0);
\echo '== invariant: posted rows with abs(ledger_effect) not in (0, amount) or cashbook not in (ledger, 0) (expect 0)'
SELECT count(*) FROM trust_transactions WHERE posted_at IS NOT NULL
   AND (abs(ledger_effect) NOT IN (0, amount) OR NOT (cashbook_effect = ledger_effect OR cashbook_effect = 0));
\echo '== INTEREST_% rows (expect 0)'
SELECT count(*) FROM trust_transactions WHERE transaction_type LIKE 'INTEREST_%';
\echo '== client_ledger_cards (lock anchors)'
SELECT trust_account_id, customer_id FROM client_ledger_cards;
