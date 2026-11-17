-- 647C OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: creditor accounts]
select id, trust_account_id, account_type, name from tenant_7d218705360b.trust_creditor_accounts;

-- [baseline: trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, bank_statement_line_id from tenant_7d218705360b.trust_transactions order by transaction_date, created_at;

-- [baseline: statements / recons]
select (select count(*) from tenant_7d218705360b.bank_statements) statements, (select count(*) from tenant_7d218705360b.bank_statement_lines) lines, (select count(*) from tenant_7d218705360b.trust_reconciliations) recons;

-- [00-baseline: Fund ledger rows + window running balance]
with posted as (
  select t.id, sum(t.ledger_effect) over (order by t.transaction_date, t.created_at, t.id rows between unbounded preceding and current row) as running_balance
  from tenant_7d218705360b.trust_transactions t where t.creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and t.ledger='NON_CLIENT' and t.posted_at is not null)
select t.id, t.transaction_date, t.transaction_type, t.status, t.amount, t.ledger_effect, p.running_balance, t.reference, coalesce(t.charge_recoverable::text,'') as charge_recoverable, coalesce(t.charge_vat_amount::text,'') as charge_vat_amount, coalesce(t.reversal_of::text,'') as reversal_of, t.created_at
from tenant_7d218705360b.trust_transactions t left join posted p on p.id=t.id
where t.creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and t.ledger='NON_CLIENT'
order by t.transaction_date desc, t.created_at desc, t.id desc;

-- [00-baseline: Fund creditor balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [a: lines at import (before auto-match)]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='cbf17f02-bbb2-4b61-bca0-effead8e4b1d' order by l.line_number;

-- [b: lines after auto-match]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='cbf17f02-bbb2-4b61-bca0-effead8e4b1d' order by l.line_number;

-- [b: auto-match target row after auto-match]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.id='8d65a67a-75d5-481b-b29b-d52b8daf315f' order by t.created_at;

-- [c: rows linked to OBS lines after the look pass (expect none)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id in (select id from tenant_7d218705360b.bank_statement_lines where bank_statement_id='cbf17f02-bbb2-4b61-bca0-effead8e4b1d') and t.id <> '8d65a67a-75d5-481b-b29b-d52b8daf315f' order by t.created_at;

-- [c: line 1 rows before Record (expect 0)]
select count(*) as rows_for_line from tenant_7d218705360b.trust_transactions where bank_statement_line_id='4c27bbf7-3d43-4206-bb41-1582073c3e8a';

-- [c: row posted for line 1 (FUND_INTEREST)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='4c27bbf7-3d43-4206-bb41-1582073c3e8a' order by t.created_at;

-- [c: line 2 rows before Record (expect 0)]
select count(*) as rows_for_line from tenant_7d218705360b.trust_transactions where bank_statement_line_id='38b0d1e8-3bca-45ee-a1d5-328e69abcd59';

-- [c: row posted for line 2 (FUND_SWEEP)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='38b0d1e8-3bca-45ee-a1d5-328e69abcd59' order by t.created_at;

-- [c: line 3 rows before Record (expect 0)]
select count(*) as rows_for_line from tenant_7d218705360b.trust_transactions where bank_statement_line_id='e2b5ea0d-361f-49df-88f5-ce003b925e82';

-- [c: row posted for line 3 (BANK_CHARGE)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='e2b5ea0d-361f-49df-88f5-ce003b925e82' order by t.created_at;

-- [c: line 4 rows before Record (expect 0)]
select count(*) as rows_for_line from tenant_7d218705360b.trust_transactions where bank_statement_line_id='add5fa6b-1d35-488e-9049-f1539b449c1f';

-- [c: row posted for line 4 (CHARGE_REFUND)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='add5fa6b-1d35-488e-9049-f1539b449c1f' order by t.created_at;

-- [d-after-record: Fund ledger rows + window running balance]
with posted as (
  select t.id, sum(t.ledger_effect) over (order by t.transaction_date, t.created_at, t.id rows between unbounded preceding and current row) as running_balance
  from tenant_7d218705360b.trust_transactions t where t.creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and t.ledger='NON_CLIENT' and t.posted_at is not null)
select t.id, t.transaction_date, t.transaction_type, t.status, t.amount, t.ledger_effect, p.running_balance, t.reference, coalesce(t.charge_recoverable::text,'') as charge_recoverable, coalesce(t.charge_vat_amount::text,'') as charge_vat_amount, coalesce(t.reversal_of::text,'') as reversal_of, t.created_at
from tenant_7d218705360b.trust_transactions t left join posted p on p.id=t.id
where t.creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and t.ledger='NON_CLIENT'
order by t.transaction_date desc, t.created_at desc, t.id desc;

-- [d-after-record: Fund creditor balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [f: original after unmatch]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.id='7931fd5b-92bb-41af-b497-cc1cfc0dec46' order by t.created_at;

-- [f: contra]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.reversal_of='7931fd5b-92bb-41af-b497-cc1cfc0dec46' order by t.created_at;

-- [f-after-unmatch: Fund ledger rows + window running balance]
with posted as (
  select t.id, sum(t.ledger_effect) over (order by t.transaction_date, t.created_at, t.id rows between unbounded preceding and current row) as running_balance
  from tenant_7d218705360b.trust_transactions t where t.creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and t.ledger='NON_CLIENT' and t.posted_at is not null)
select t.id, t.transaction_date, t.transaction_type, t.status, t.amount, t.ledger_effect, p.running_balance, t.reference, coalesce(t.charge_recoverable::text,'') as charge_recoverable, coalesce(t.charge_vat_amount::text,'') as charge_vat_amount, coalesce(t.reversal_of::text,'') as reversal_of, t.created_at
from tenant_7d218705360b.trust_transactions t left join posted p on p.id=t.id
where t.creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and t.ledger='NON_CLIENT'
order by t.transaction_date desc, t.created_at desc, t.id desc;

-- [f-after-unmatch: Fund creditor balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [final: OBS statement lines]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='cbf17f02-bbb2-4b61-bca0-effead8e4b1d' order by l.line_number;

-- [final: OBS rows]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.reference like '%/OBS647C/RQH2F' or t.reversal_of in (select id from tenant_7d218705360b.trust_transactions where reference like '%/OBS647C/RQH2F') order by t.created_at;

-- [final: EQ-2 cashbook vs creditors as at today]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='9428d0a8-ff3e-4e83-9641-fc70925cb2f7' and posted_at is not null and transaction_date <= date '2026-11-17') cashbook, (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='9428d0a8-ff3e-4e83-9641-fc70925cb2f7' and posted_at is not null and transaction_date <= date '2026-11-17') all_creditors, (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='6272042b-ff3b-4e5d-9f26-868f0b65711f' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17') fund_ledger;
