-- 647B OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: creditor accounts]
select id, trust_account_id, account_type, name from tenant_7d218705360b.trust_creditor_accounts;

-- [baseline: trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, bank_statement_line_id, unidentified_deposit_id from tenant_7d218705360b.trust_transactions order by transaction_date, created_at;

-- [baseline: statements / recons / items]
select (select count(*) from tenant_7d218705360b.bank_statements) statements, (select count(*) from tenant_7d218705360b.bank_statement_lines) lines, (select count(*) from tenant_7d218705360b.trust_reconciliations) recons, (select count(*) from tenant_7d218705360b.unidentified_deposits) items;

-- [baseline: EQ-2 as at today]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and posted_at is not null and transaction_date <= date '2026-11-17') cashbook, (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and posted_at is not null and transaction_date <= date '2026-11-17') all_creditors, (select coalesce(sum(t.ledger_effect),0) from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_creditor_accounts c on c.id=t.creditor_account_id where t.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and c.account_type='UNIDENTIFIED_DEPOSITS' and t.posted_at is not null and t.transaction_date <= date '2026-11-17') unidentified_ledger, (select coalesce(sum(t.ledger_effect),0) from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_creditor_accounts c on c.id=t.creditor_account_id where t.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and c.account_type='FUND_INTEREST_AND_CHARGES' and t.posted_at is not null and t.transaction_date <= date '2026-11-17') fund_ledger;

-- [00-baseline: unidentified_deposits rows]
select d.id, d.trust_account_id, d.creditor_account_id, d.deposit_transaction_id, d.bank_statement_line_id, d.received_date, d.bank_description, d.bank_reference, d.amount, d.status, d.resolved_date, d.resolution_transaction_id, d.notes, d.created_at from tenant_7d218705360b.unidentified_deposits d where d.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' order by d.received_date, d.created_at, d.id;

-- [00-baseline: unidentified ledger balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='867d7e71-7e73-4b94-add4-ef8931091437' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [a: lines at import]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='f863753f-67e6-42bb-9833-3156a67cf567' order by l.line_number;

-- [a: lines after auto-match]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='f863753f-67e6-42bb-9833-3156a67cf567' order by l.line_number;

-- [c: line 1 after record]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='f863753f-67e6-42bb-9833-3156a67cf567' order by l.line_number;

-- [d: the item]
select d.id, d.trust_account_id, d.creditor_account_id, d.deposit_transaction_id, d.bank_statement_line_id, d.received_date, d.bank_description, d.bank_reference, d.amount, d.status, d.resolved_date, d.resolution_transaction_id, d.notes, d.created_at from tenant_7d218705360b.unidentified_deposits d where d.bank_statement_line_id='97d1f906-2349-45e0-9c26-50c238486464' order by d.received_date, d.created_at, d.id;

-- [d: the deposit row]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.unidentified_deposit_id, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='97d1f906-2349-45e0-9c26-50c238486464' order by t.created_at;

-- [d-after-record: unidentified_deposits rows]
select d.id, d.trust_account_id, d.creditor_account_id, d.deposit_transaction_id, d.bank_statement_line_id, d.received_date, d.bank_description, d.bank_reference, d.amount, d.status, d.resolved_date, d.resolution_transaction_id, d.notes, d.created_at from tenant_7d218705360b.unidentified_deposits d where d.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' order by d.received_date, d.created_at, d.id;

-- [d-after-record: unidentified ledger balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='867d7e71-7e73-4b94-add4-ef8931091437' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [d: EQ-2 as at today]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and posted_at is not null and transaction_date <= date '2026-11-17') cashbook, (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and posted_at is not null and transaction_date <= date '2026-11-17') all_creditors, (select coalesce(sum(t.ledger_effect),0) from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_creditor_accounts c on c.id=t.creditor_account_id where t.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and c.account_type='UNIDENTIFIED_DEPOSITS' and t.posted_at is not null and t.transaction_date <= date '2026-11-17') unidentified_ledger, (select coalesce(sum(t.ledger_effect),0) from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_creditor_accounts c on c.id=t.creditor_account_id where t.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and c.account_type='FUND_INTEREST_AND_CHARGES' and t.posted_at is not null and t.transaction_date <= date '2026-11-17') fund_ledger;

-- [e: the item after unmatch]
select d.id, d.trust_account_id, d.creditor_account_id, d.deposit_transaction_id, d.bank_statement_line_id, d.received_date, d.bank_description, d.bank_reference, d.amount, d.status, d.resolved_date, d.resolution_transaction_id, d.notes, d.created_at from tenant_7d218705360b.unidentified_deposits d where d.id='142ca912-2ee2-414d-9b25-db5ca3318ad7' order by d.received_date, d.created_at, d.id;

-- [e: original after unmatch]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.unidentified_deposit_id, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.id='6a15a693-2857-40fb-81d6-0043aeb5301f' order by t.created_at;

-- [e: contra]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.unidentified_deposit_id, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.reversal_of='6a15a693-2857-40fb-81d6-0043aeb5301f' order by t.created_at;

-- [e: every row naming the item]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.unidentified_deposit_id, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.unidentified_deposit_id='142ca912-2ee2-414d-9b25-db5ca3318ad7' order by t.created_at;

-- [e: lines after unmatch]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='f863753f-67e6-42bb-9833-3156a67cf567' order by l.line_number;

-- [e-after-unmatch: unidentified_deposits rows]
select d.id, d.trust_account_id, d.creditor_account_id, d.deposit_transaction_id, d.bank_statement_line_id, d.received_date, d.bank_description, d.bank_reference, d.amount, d.status, d.resolved_date, d.resolution_transaction_id, d.notes, d.created_at from tenant_7d218705360b.unidentified_deposits d where d.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' order by d.received_date, d.created_at, d.id;

-- [e-after-unmatch: unidentified ledger balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='867d7e71-7e73-4b94-add4-ef8931091437' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [e: EQ-2 as at today]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and posted_at is not null and transaction_date <= date '2026-11-17') cashbook, (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and posted_at is not null and transaction_date <= date '2026-11-17') all_creditors, (select coalesce(sum(t.ledger_effect),0) from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_creditor_accounts c on c.id=t.creditor_account_id where t.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and c.account_type='UNIDENTIFIED_DEPOSITS' and t.posted_at is not null and t.transaction_date <= date '2026-11-17') unidentified_ledger, (select coalesce(sum(t.ledger_effect),0) from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_creditor_accounts c on c.id=t.creditor_account_id where t.trust_account_id='24986ae5-030e-447b-9a99-8da5d43f3e51' and c.account_type='FUND_INTEREST_AND_CHARGES' and t.posted_at is not null and t.transaction_date <= date '2026-11-17') fund_ledger;
