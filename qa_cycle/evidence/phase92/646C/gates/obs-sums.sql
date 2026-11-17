-- 646C OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, bank_statement_line_id from tenant_7d218705360b.trust_transactions order by transaction_date, created_at;

-- [baseline: creditor accounts]
select id, account_type, name from tenant_7d218705360b.trust_creditor_accounts;

-- [baseline: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='8c0b38dc-152b-49c4-bc50-499fdc43272b' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [baseline: outstanding as at 2026-11-17]
select
  coalesce( sum(t.cashbook_effect) filter (where t.cashbook_effect > 0),0) as outstanding_deposits,
  coalesce(-sum(t.cashbook_effect) filter (where t.cashbook_effect < 0),0) as outstanding_payments
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.bank_statement_lines l on l.id=t.bank_statement_line_id
where t.trust_account_id='8c0b38dc-152b-49c4-bc50-499fdc43272b' and t.posted_at is not null and t.cashbook_effect <> 0
  and t.transaction_date <= date '2026-11-17'
  and (t.bank_statement_line_id is null or l.transaction_date > date '2026-11-17')
  and not (t.bank_statement_line_id is null and t.reversed_by_id is not null and exists (
       select 1 from tenant_7d218705360b.trust_transactions r where r.id=t.reversed_by_id and r.posted_at is not null
          and r.bank_statement_line_id is null and r.transaction_date <= date '2026-11-17'))
  and not (t.bank_statement_line_id is null and t.reversal_of is not null and exists (
       select 1 from tenant_7d218705360b.trust_transactions o where o.id=t.reversal_of and o.bank_statement_line_id is null));

-- [baseline: statement + lines]
select s.closing_balance, s.opening_balance, s.line_count, s.matched_count from tenant_7d218705360b.bank_statements s where s.id='00000000-0000-0000-0000-000000000000';

-- [baseline: lines]
select line_number, transaction_date, reference, amount, running_balance, match_status, trust_transaction_id, match_confidence from tenant_7d218705360b.bank_statement_lines where bank_statement_id='00000000-0000-0000-0000-000000000000' order by line_number;

-- [state 01-review-opened: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='8c0b38dc-152b-49c4-bc50-499fdc43272b' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [state 01-review-opened: outstanding as at 2026-11-17]
select
  coalesce( sum(t.cashbook_effect) filter (where t.cashbook_effect > 0),0) as outstanding_deposits,
  coalesce(-sum(t.cashbook_effect) filter (where t.cashbook_effect < 0),0) as outstanding_payments
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.bank_statement_lines l on l.id=t.bank_statement_line_id
where t.trust_account_id='8c0b38dc-152b-49c4-bc50-499fdc43272b' and t.posted_at is not null and t.cashbook_effect <> 0
  and t.transaction_date <= date '2026-11-17'
  and (t.bank_statement_line_id is null or l.transaction_date > date '2026-11-17')
  and not (t.bank_statement_line_id is null and t.reversed_by_id is not null and exists (
       select 1 from tenant_7d218705360b.trust_transactions r where r.id=t.reversed_by_id and r.posted_at is not null
          and r.bank_statement_line_id is null and r.transaction_date <= date '2026-11-17'))
  and not (t.bank_statement_line_id is null and t.reversal_of is not null and exists (
       select 1 from tenant_7d218705360b.trust_transactions o where o.id=t.reversal_of and o.bank_statement_line_id is null));

-- [state 01-review-opened: statement + lines]
select s.closing_balance, s.opening_balance, s.line_count, s.matched_count from tenant_7d218705360b.bank_statements s where s.id='e4f08b79-9edb-4583-8605-7ee423c9ec2c';

-- [state 01-review-opened: lines]
select line_number, transaction_date, reference, amount, running_balance, match_status, trust_transaction_id, match_confidence from tenant_7d218705360b.bank_statement_lines where bank_statement_id='e4f08b79-9edb-4583-8605-7ee423c9ec2c' order by line_number;

-- [state 01-review-opened: stored reconciliation row]
select period_end, bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance, unidentified_deposit_total, inter_account_balance, creditor_total, difference, is_balanced, status, integrity_flags from tenant_7d218705360b.trust_reconciliations where id='d58cf725-ada0-4d92-ad30-d2ce3cf71ba3';

-- [state 02-after-manual-match: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='8c0b38dc-152b-49c4-bc50-499fdc43272b' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [state 02-after-manual-match: outstanding as at 2026-11-17]
select
  coalesce( sum(t.cashbook_effect) filter (where t.cashbook_effect > 0),0) as outstanding_deposits,
  coalesce(-sum(t.cashbook_effect) filter (where t.cashbook_effect < 0),0) as outstanding_payments
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.bank_statement_lines l on l.id=t.bank_statement_line_id
where t.trust_account_id='8c0b38dc-152b-49c4-bc50-499fdc43272b' and t.posted_at is not null and t.cashbook_effect <> 0
  and t.transaction_date <= date '2026-11-17'
  and (t.bank_statement_line_id is null or l.transaction_date > date '2026-11-17')
  and not (t.bank_statement_line_id is null and t.reversed_by_id is not null and exists (
       select 1 from tenant_7d218705360b.trust_transactions r where r.id=t.reversed_by_id and r.posted_at is not null
          and r.bank_statement_line_id is null and r.transaction_date <= date '2026-11-17'))
  and not (t.bank_statement_line_id is null and t.reversal_of is not null and exists (
       select 1 from tenant_7d218705360b.trust_transactions o where o.id=t.reversal_of and o.bank_statement_line_id is null));

-- [state 02-after-manual-match: statement + lines]
select s.closing_balance, s.opening_balance, s.line_count, s.matched_count from tenant_7d218705360b.bank_statements s where s.id='e4f08b79-9edb-4583-8605-7ee423c9ec2c';

-- [state 02-after-manual-match: lines]
select line_number, transaction_date, reference, amount, running_balance, match_status, trust_transaction_id, match_confidence from tenant_7d218705360b.bank_statement_lines where bank_statement_id='e4f08b79-9edb-4583-8605-7ee423c9ec2c' order by line_number;

-- [state 02-after-manual-match: stored reconciliation row]
select period_end, bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance, unidentified_deposit_total, inter_account_balance, creditor_total, difference, is_balanced, status, integrity_flags from tenant_7d218705360b.trust_reconciliations where id='d58cf725-ada0-4d92-ad30-d2ce3cf71ba3';

-- [state 03-after-exclude: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='8c0b38dc-152b-49c4-bc50-499fdc43272b' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [state 03-after-exclude: outstanding as at 2026-11-17]
select
  coalesce( sum(t.cashbook_effect) filter (where t.cashbook_effect > 0),0) as outstanding_deposits,
  coalesce(-sum(t.cashbook_effect) filter (where t.cashbook_effect < 0),0) as outstanding_payments
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.bank_statement_lines l on l.id=t.bank_statement_line_id
where t.trust_account_id='8c0b38dc-152b-49c4-bc50-499fdc43272b' and t.posted_at is not null and t.cashbook_effect <> 0
  and t.transaction_date <= date '2026-11-17'
  and (t.bank_statement_line_id is null or l.transaction_date > date '2026-11-17')
  and not (t.bank_statement_line_id is null and t.reversed_by_id is not null and exists (
       select 1 from tenant_7d218705360b.trust_transactions r where r.id=t.reversed_by_id and r.posted_at is not null
          and r.bank_statement_line_id is null and r.transaction_date <= date '2026-11-17'))
  and not (t.bank_statement_line_id is null and t.reversal_of is not null and exists (
       select 1 from tenant_7d218705360b.trust_transactions o where o.id=t.reversal_of and o.bank_statement_line_id is null));

-- [state 03-after-exclude: statement + lines]
select s.closing_balance, s.opening_balance, s.line_count, s.matched_count from tenant_7d218705360b.bank_statements s where s.id='e4f08b79-9edb-4583-8605-7ee423c9ec2c';

-- [state 03-after-exclude: lines]
select line_number, transaction_date, reference, amount, running_balance, match_status, trust_transaction_id, match_confidence from tenant_7d218705360b.bank_statement_lines where bank_statement_id='e4f08b79-9edb-4583-8605-7ee423c9ec2c' order by line_number;

-- [state 03-after-exclude: stored reconciliation row]
select period_end, bank_balance, outstanding_deposits, outstanding_payments, adjusted_bank_balance, cashbook_balance, client_ledger_total, investment_creditor_total, fund_ledger_balance, unidentified_deposit_total, inter_account_balance, creditor_total, difference, is_balanced, status, integrity_flags from tenant_7d218705360b.trust_reconciliations where id='d58cf725-ada0-4d92-ad30-d2ce3cf71ba3';

-- [b: line ↔ transaction links]
select l.line_number, l.reference, l.amount, l.match_status, l.excluded_reason, t.id as txn, t.transaction_type, t.status, t.reference as txn_ref, t.bank_statement_line_id = l.id as linked from tenant_7d218705360b.bank_statement_lines l left join tenant_7d218705360b.trust_transactions t on t.id = l.trust_transaction_id where l.bank_statement_id='e4f08b79-9edb-4583-8605-7ee423c9ec2c' order by l.line_number;

-- [final: reconciliations]
select id, period_end, status, is_balanced, bank_balance, adjusted_bank_balance, cashbook_balance, creditor_total, difference from tenant_7d218705360b.trust_reconciliations order by created_at;

-- [final: bank statements]
select id, file_name, period_start, period_end, closing_balance, line_count, matched_count from tenant_7d218705360b.bank_statements order by created_at;
