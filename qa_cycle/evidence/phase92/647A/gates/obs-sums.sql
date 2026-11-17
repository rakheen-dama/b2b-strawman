-- 647A OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: creditor accounts]
select id, trust_account_id, account_type, name from tenant_7d218705360b.trust_creditor_accounts;

-- [baseline: trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference from tenant_7d218705360b.trust_transactions order by transaction_date, created_at;

-- [baseline: statements / recons]
select (select count(*) from tenant_7d218705360b.bank_statements) statements, (select count(*) from tenant_7d218705360b.bank_statement_lines) lines, (select count(*) from tenant_7d218705360b.trust_reconciliations) recons;

-- [00-baseline (today): position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [00-baseline-before: position as at 2026-11-09]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-09';

-- [00-baseline-line1: position as at 2026-11-10]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-10';

-- [00-baseline-line2: position as at 2026-11-11]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-11';

-- [00-baseline-line3: position as at 2026-11-12]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-12';

-- [00-baseline-line4: position as at 2026-11-13]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-13';

-- [00-baseline-line5: position as at 2026-11-14]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-14';

-- [00-baseline-today: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [wizard: statement]
select id, period_start, period_end, opening_balance, closing_balance, format, line_count, matched_count, status from tenant_7d218705360b.bank_statements where id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5';

-- [wizard: lines after auto-match]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [a1: before: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a1: row posted for line 1 (FUND_INTEREST)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.bank_statement_line_id='2379ecae-adff-4455-9ce6-05a4afbde70b' order by t.created_at;

-- [a1: line after record]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [a1: after: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a2: before: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a2: row posted for line 2 (FUND_SWEEP)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.bank_statement_line_id='77139b6a-9ee5-4943-81ee-bc4f8931b721' order by t.created_at;

-- [a2: line after record]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [a2: after: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a3: before: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a3: row posted for line 3 (BANK_CHARGE)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.bank_statement_line_id='2aaa8dfc-9dd6-4c79-99f8-bdf437806d73' order by t.created_at;

-- [a3: line after record]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [a3: after: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a4: before: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a4: row posted for line 4 (CHARGE_REFUND)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.bank_statement_line_id='513e1347-aa43-482b-8a69-768021abb593' order by t.created_at;

-- [a4: line after record]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [a4: after: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a5: before: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [a5: row posted for line 5 (BANK_CHARGE)]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.bank_statement_line_id='dfdc4e4d-f3ee-4bca-a530-7b2d3d8205ea' order by t.created_at;

-- [a5: line after record]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [a5: after: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [b: all OBS rows]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.reference like '%/OBS647A/MUSQT' order by t.created_at;

-- [b: lines]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [b-after-record (today): position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [b-after-record-before: position as at 2026-11-09]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-09';

-- [b-after-record-line1: position as at 2026-11-10]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-10';

-- [b-after-record-line2: position as at 2026-11-11]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-11';

-- [b-after-record-line3: position as at 2026-11-12]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-12';

-- [b-after-record-line4: position as at 2026-11-13]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-13';

-- [b-after-record-line5: position as at 2026-11-14]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-14';

-- [b-after-record-today: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [c: original after unmatch]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.id='7accbcab-08ae-4020-a324-51eb88062570' order by t.created_at;

-- [c: contra]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.reversal_of='7accbcab-08ae-4020-a324-51eb88062570' order by t.created_at;

-- [c: lines after unmatch]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [c-after-unmatch (today): position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [c-after-unmatch-line2: position as at 2026-11-11]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-11';

-- [c-after-unmatch-line3: position as at 2026-11-12]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-12';

-- [c-after-unmatch-today: position as at 2026-11-17]
select
  coalesce(sum(t.cashbook_effect),0) as cashbook,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT'),0) as client_ledgers,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT'),0) as investment,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='FUND_INTEREST_AND_CHARGES'),0) as fund_ledger,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type='UNIDENTIFIED_DEPOSITS'),0) as unidentified,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT' and ca.account_type not in ('FUND_INTEREST_AND_CHARGES','UNIDENTIFIED_DEPOSITS')),0) as other_non_client,
  coalesce(sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT'),0) as inter_account,
  coalesce(sum(t.ledger_effect),0) as all_ledgers,
  count(*) as posted_rows
from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id
where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [final: OBS rows]
select t.id, t.transaction_type, t.status, t.amount, t.transaction_date, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null as posted, t.creditor_account_id, ca.account_type as creditor_type, t.bank_statement_line_id, t.charge_recoverable, t.charge_vat_amount, t.reference, t.reversal_of, t.reversed_by_id, t.approved_by from tenant_7d218705360b.trust_transactions t left join tenant_7d218705360b.trust_creditor_accounts ca on ca.id=t.creditor_account_id where t.reference like '%/OBS647A/MUSQT' or t.reversal_of in (select id from tenant_7d218705360b.trust_transactions where reference like '%/OBS647A/MUSQT') order by t.created_at;

-- [final: lines + matched_count]
select l.line_number, l.id, l.transaction_date, l.amount, l.match_status, l.classification, l.trust_transaction_id, (select matched_count from tenant_7d218705360b.bank_statements s where s.id=l.bank_statement_id) as stmt_matched_count from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='5c691fe1-fcdb-4c21-bd6a-c0e0fe3b9bd5' order by l.line_number;

-- [final: Fund ledger as at each OBS date]
select d::date as as_at, coalesce((select sum(t.ledger_effect) from tenant_7d218705360b.trust_transactions t where t.creditor_account_id='4966f021-937f-430a-b3a0-0746c48c7c6e' and t.posted_at is not null and t.transaction_date <= d::date),0) as fund_ledger, coalesce((select sum(t.cashbook_effect) from tenant_7d218705360b.trust_transactions t where t.trust_account_id='bc212ce3-4ad0-41dc-8400-600191f1fbfb' and t.posted_at is not null and t.transaction_date <= d::date),0) as cashbook from generate_series(date '2026-11-09', date '2026-11-17', interval '1 day') d;
