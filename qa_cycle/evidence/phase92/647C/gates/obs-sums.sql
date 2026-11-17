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
  from tenant_7d218705360b.trust_transactions t where t.creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and t.ledger='NON_CLIENT' and t.posted_at is not null)
select t.id, t.transaction_date, t.transaction_type, t.status, t.amount, t.ledger_effect, p.running_balance, t.reference, coalesce(t.charge_recoverable::text,'') as charge_recoverable, coalesce(t.charge_vat_amount::text,'') as charge_vat_amount, coalesce(t.reversal_of::text,'') as reversal_of, t.created_at
from tenant_7d218705360b.trust_transactions t left join posted p on p.id=t.id
where t.creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and t.ledger='NON_CLIENT'
order by t.transaction_date desc, t.created_at desc, t.id desc;

-- [00-baseline: Fund creditor balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [a: lines at import (before auto-match)]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='16dae0a0-9823-41a8-a89b-ee5920dbcae6' order by l.line_number;

-- [b: lines after auto-match]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='16dae0a0-9823-41a8-a89b-ee5920dbcae6' order by l.line_number;

-- [b: auto-match target row after auto-match]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.id='4d43bdd4-7136-4aa7-895e-62301505d6ce' order by t.created_at;

-- [c: rows linked to OBS lines after the look pass (expect none)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id in (select id from tenant_7d218705360b.bank_statement_lines where bank_statement_id='16dae0a0-9823-41a8-a89b-ee5920dbcae6') and t.id <> '4d43bdd4-7136-4aa7-895e-62301505d6ce' order by t.created_at;

-- [c: line 1 rows before Record (expect 0)]
select count(*) as rows_for_line from tenant_7d218705360b.trust_transactions where bank_statement_line_id='f097eaf0-b678-44cb-848f-b06457c7bff9';

-- [c: row posted for line 1 (FUND_INTEREST)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='f097eaf0-b678-44cb-848f-b06457c7bff9' order by t.created_at;

-- [c: line 2 rows before Record (expect 0)]
select count(*) as rows_for_line from tenant_7d218705360b.trust_transactions where bank_statement_line_id='8ee724fd-4c00-4da9-bde2-37eb6aa42d7e';

-- [c: row posted for line 2 (FUND_SWEEP)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='8ee724fd-4c00-4da9-bde2-37eb6aa42d7e' order by t.created_at;

-- [c: line 3 rows before Record (expect 0)]
select count(*) as rows_for_line from tenant_7d218705360b.trust_transactions where bank_statement_line_id='f4b2b1e2-3920-499c-aa5e-8bb83a556b7f';

-- [c: row posted for line 3 (BANK_CHARGE)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='f4b2b1e2-3920-499c-aa5e-8bb83a556b7f' order by t.created_at;

-- [c: line 4 rows before Record (expect 0)]
select count(*) as rows_for_line from tenant_7d218705360b.trust_transactions where bank_statement_line_id='0bec1438-90c0-4ad5-911b-824a4b47f9b6';

-- [c: row posted for line 4 (CHARGE_REFUND)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.bank_statement_line_id='0bec1438-90c0-4ad5-911b-824a4b47f9b6' order by t.created_at;

-- [d-after-record: Fund ledger rows + window running balance]
with posted as (
  select t.id, sum(t.ledger_effect) over (order by t.transaction_date, t.created_at, t.id rows between unbounded preceding and current row) as running_balance
  from tenant_7d218705360b.trust_transactions t where t.creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and t.ledger='NON_CLIENT' and t.posted_at is not null)
select t.id, t.transaction_date, t.transaction_type, t.status, t.amount, t.ledger_effect, p.running_balance, t.reference, coalesce(t.charge_recoverable::text,'') as charge_recoverable, coalesce(t.charge_vat_amount::text,'') as charge_vat_amount, coalesce(t.reversal_of::text,'') as reversal_of, t.created_at
from tenant_7d218705360b.trust_transactions t left join posted p on p.id=t.id
where t.creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and t.ledger='NON_CLIENT'
order by t.transaction_date desc, t.created_at desc, t.id desc;

-- [d-after-record: Fund creditor balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [f: original after unmatch]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.id='d13f6191-06df-4f5a-8676-96fb1011e0d2' order by t.created_at;

-- [f: contra]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.reversal_of='d13f6191-06df-4f5a-8676-96fb1011e0d2' order by t.created_at;

-- [f-after-unmatch: Fund ledger rows + window running balance]
with posted as (
  select t.id, sum(t.ledger_effect) over (order by t.transaction_date, t.created_at, t.id rows between unbounded preceding and current row) as running_balance
  from tenant_7d218705360b.trust_transactions t where t.creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and t.ledger='NON_CLIENT' and t.posted_at is not null)
select t.id, t.transaction_date, t.transaction_type, t.status, t.amount, t.ledger_effect, p.running_balance, t.reference, coalesce(t.charge_recoverable::text,'') as charge_recoverable, coalesce(t.charge_vat_amount::text,'') as charge_vat_amount, coalesce(t.reversal_of::text,'') as reversal_of, t.created_at
from tenant_7d218705360b.trust_transactions t left join posted p on p.id=t.id
where t.creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and t.ledger='NON_CLIENT'
order by t.transaction_date desc, t.created_at desc, t.id desc;

-- [f-after-unmatch: Fund creditor balance as at today]
select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17';

-- [final: OBS statement lines]
select l.line_number, l.id, l.transaction_date, l.description, l.reference, l.amount, l.match_status, l.suggested_classification, l.classification, l.trust_transaction_id, l.match_confidence, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='16dae0a0-9823-41a8-a89b-ee5920dbcae6' order by l.line_number;

-- [final: OBS rows]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.created_at from tenant_7d218705360b.trust_transactions t where t.reference like '%/OBS647C/QP4KZ' or t.reversal_of in (select id from tenant_7d218705360b.trust_transactions where reference like '%/OBS647C/QP4KZ') order by t.created_at;

-- [final: EQ-2 cashbook vs creditors as at today]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='9c678aa3-e2b6-4921-9a73-74053f8acee0' and posted_at is not null and transaction_date <= date '2026-11-17') cashbook, (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where trust_account_id='9c678aa3-e2b6-4921-9a73-74053f8acee0' and posted_at is not null and transaction_date <= date '2026-11-17') all_creditors, (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where creditor_account_id='a01987bd-3424-475a-8878-f8f096fc151a' and ledger='NON_CLIENT' and posted_at is not null and transaction_date <= date '2026-11-17') fund_ledger;
