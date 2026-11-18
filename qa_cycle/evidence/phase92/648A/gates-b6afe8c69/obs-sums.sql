-- 648A OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: books per account (cashbook = sum cashbook_effect; creditors = sum ledger_effect by ledger; posted rows)]
select a.account_name, a.account_type, coalesce(a.investment_basis,'-') basis, sum(t.cashbook_effect) cashbook, sum(t.ledger_effect) creditors, sum(t.ledger_effect) filter (where t.ledger='CLIENT') client, sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT') client_investment, sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT') inter_account, sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT') non_client from tenant_7d218705360b.trust_accounts a left join tenant_7d218705360b.trust_transactions t on t.trust_account_id=a.id and t.posted_at is not null group by 1,2,3 order by 2,1;

-- [baseline: client figures by ledger and account]
select a.account_name, t.ledger, sum(t.ledger_effect) ledger_sum, count(*) n from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='2a36f8ef-582a-4e44-b6bd-bf2844e32418' and t.posted_at is not null group by 1,2 order by 1,2;

-- [a: before approval: client figures by ledger and account]
select a.account_name, t.ledger, sum(t.ledger_effect) ledger_sum, count(*) n from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='2a36f8ef-582a-4e44-b6bd-bf2844e32418' and t.posted_at is not null group by 1,2 order by 1,2;

-- [j1: notifications referencing the placement row]
select n.type, n.title, n.body, n.reference_entity_type, n.is_read, n.created_at from tenant_7d218705360b.notifications n where n.reference_entity_id='74174f7f-6917-441c-bdaa-68fa3eb0491c' order by n.created_at;

-- [a: after Bob approved: client figures by ledger and account]
select a.account_name, t.ledger, sum(t.ledger_effect) ledger_sum, count(*) n from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='2a36f8ef-582a-4e44-b6bd-bf2844e32418' and t.posted_at is not null group by 1,2 order by 1,2;

-- [a: after approval: books per account (cashbook = sum cashbook_effect; creditors = sum ledger_effect by ledger; posted rows)]
select a.account_name, a.account_type, coalesce(a.investment_basis,'-') basis, sum(t.cashbook_effect) cashbook, sum(t.ledger_effect) creditors, sum(t.ledger_effect) filter (where t.ledger='CLIENT') client, sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT') client_investment, sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT') inter_account, sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT') non_client from tenant_7d218705360b.trust_accounts a left join tenant_7d218705360b.trust_transactions t on t.trust_account_id=a.id and t.posted_at is not null group by 1,2,3 order by 2,1;

-- [b: after approval: books per account (cashbook = sum cashbook_effect; creditors = sum ledger_effect by ledger; posted rows)]
select a.account_name, a.account_type, coalesce(a.investment_basis,'-') basis, sum(t.cashbook_effect) cashbook, sum(t.ledger_effect) creditors, sum(t.ledger_effect) filter (where t.ledger='CLIENT') client, sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT') client_investment, sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT') inter_account, sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT') non_client from tenant_7d218705360b.trust_accounts a left join tenant_7d218705360b.trust_transactions t on t.trust_account_id=a.id and t.posted_at is not null group by 1,2,3 order by 2,1;

-- [b: the s86(3) investment and its rows]
select i.account_number, i.investment_basis, i.customer_id is null customer_id_is_null, i.project_id is null project_id_is_null, i.status, t.transaction_type, t.status tx_status, t.ledger, t.customer_id is null tx_customer_null, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted from tenant_7d218705360b.trust_investments i join tenant_7d218705360b.trust_transactions t on t.investment_id=i.id join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where i.id='de4057bd-3536-409f-a0f3-a376d21d578e' order by t.created_at;

-- [c: the s86(4) investment's rows after the withdrawal]
select t.transaction_type, t.status, t.ledger, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted, t.transaction_date, t.reference from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.investment_id='8c856a57-222d-444f-a005-af4d084e8276' order by t.created_at;

-- [c: after the withdrawal: client figures by ledger and account]
select a.account_name, t.ledger, sum(t.ledger_effect) ledger_sum, count(*) n from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='2a36f8ef-582a-4e44-b6bd-bf2844e32418' and t.posted_at is not null group by 1,2 order by 1,2;

-- [c: after the withdrawal: books per account (cashbook = sum cashbook_effect; creditors = sum ledger_effect by ledger; posted rows)]
select a.account_name, a.account_type, coalesce(a.investment_basis,'-') basis, sum(t.cashbook_effect) cashbook, sum(t.ledger_effect) creditors, sum(t.ledger_effect) filter (where t.ledger='CLIENT') client, sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT') client_investment, sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT') inter_account, sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT') non_client from tenant_7d218705360b.trust_accounts a left join tenant_7d218705360b.trust_transactions t on t.trust_account_id=a.id and t.posted_at is not null group by 1,2,3 order by 2,1;
