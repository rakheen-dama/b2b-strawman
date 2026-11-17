-- 646D OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, customer_id from tenant_7d218705360b.trust_transactions order by transaction_date, created_at;

-- [baseline: creditor accounts]
select id, account_type, name from tenant_7d218705360b.trust_creditor_accounts;

-- [baseline: open fee notes]
select i.id, i.invoice_number, i.status, i.total, i.amount_paid, c.name as client, i.customer_id from tenant_7d218705360b.invoices i join tenant_7d218705360b.customers c on c.id=i.customer_id where i.status in ('APPROVED','SENT','PARTIALLY_PAID') order by i.created_at;

-- [baseline: chosen client's posted rows]
select transaction_type, amount, status, transaction_date, ledger_effect, cashbook_effect, reference from tenant_7d218705360b.trust_transactions where customer_id='2f416968-3f68-4dc6-bae1-734476abc5fd' order by transaction_date, created_at;

-- [01-lifecycle-books: position as at 2026-11-17]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [01-lifecycle-books trend prev month: position as at 2026-10-31]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-10-31';

-- [deposit row]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, customer_id, project_id, invoice_id, recorded_by, approved_by from tenant_7d218705360b.trust_transactions where id='608b10d8-1dad-487e-abc3-6e8835581062';

-- [c: fee transfer recorded]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, customer_id, project_id, invoice_id, recorded_by, approved_by from tenant_7d218705360b.trust_transactions where id='a2e3cb07-3625-4090-8e58-d1c7f791c558';

-- [c: fee transfer approved]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, customer_id, project_id, invoice_id, recorded_by, approved_by from tenant_7d218705360b.trust_transactions where id='a2e3cb07-3625-4090-8e58-d1c7f791c558';

-- [c: as at dayBeforeDep: position as at 2026-11-11]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-11-11';

-- [c: as at dep: position as at 2026-11-12]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-11-12';

-- [c: as at dayBeforeBack: position as at 2026-11-13]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-11-13';

-- [c: as at back: position as at 2026-11-14]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-11-14';

-- [c: as at today: position as at 2026-11-17]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [c: fee note after approval]
select id, invoice_number, status, total, amount_paid from tenant_7d218705360b.invoices where id='b2edf074-8634-469d-b7fe-381603f0b6b1';

-- [e: default-date fee transfer approved]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect, cashbook_effect, reference, customer_id, project_id, invoice_id, recorded_by, approved_by from tenant_7d218705360b.trust_transactions where id='993db6bb-3d72-42e8-a141-74a5965ca426';

-- [e: fee note after]
select id, invoice_number, status, total, amount_paid from tenant_7d218705360b.invoices where id='b2edf074-8634-469d-b7fe-381603f0b6b1';

-- [02-after-fee-transfers: position as at 2026-11-17]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-11-17';

-- [02-after-fee-transfers trend prev month: position as at 2026-10-31]
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
where t.trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and t.posted_at is not null and t.transaction_date <= date '2026-10-31';

-- [final: OBS rows]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger_effect, cashbook_effect, reference, recorded_by, approved_by from tenant_7d218705360b.trust_transactions where reference like '%OBS646D/HT7QW%' order by created_at;

-- [final: chosen client's ledger as at each OBS date]
select d::date as as_at, coalesce((select sum(ledger_effect) from tenant_7d218705360b.trust_transactions where customer_id='2f416968-3f68-4dc6-bae1-734476abc5fd' and trust_account_id='df0ccb75-5c9e-4666-be6c-a1321a63172f' and ledger='CLIENT' and posted_at is not null and transaction_date <= d::date),0) as client_balance from generate_series(date '2026-11-11', date '2026-11-17', interval '1 day') d;
