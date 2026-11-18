-- 648A OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [c: the s86(4) investment's rows after the withdrawal]
select t.transaction_type, t.status, t.ledger, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted, t.transaction_date, t.reference from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.investment_id='a7e500d8-2124-453d-aa34-48ae9154f4d6' order by t.created_at;

-- [c: after the withdrawal: client figures by ledger and account]
select a.account_name, t.ledger, sum(t.ledger_effect) ledger_sum, count(*) n from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='79b27393-d9c1-46c0-9a48-46c565a92e96' and t.posted_at is not null group by 1,2 order by 1,2;

-- [c: after the withdrawal: books per account (cashbook = sum cashbook_effect; creditors = sum ledger_effect by ledger; posted rows)]
select a.account_name, a.account_type, coalesce(a.investment_basis,'-') basis, sum(t.cashbook_effect) cashbook, sum(t.ledger_effect) creditors, sum(t.ledger_effect) filter (where t.ledger='CLIENT') client, sum(t.ledger_effect) filter (where t.ledger='CLIENT_INVESTMENT') client_investment, sum(t.ledger_effect) filter (where t.ledger='INTER_ACCOUNT') inter_account, sum(t.ledger_effect) filter (where t.ledger='NON_CLIENT') non_client from tenant_7d218705360b.trust_accounts a left join tenant_7d218705360b.trust_transactions t on t.trust_account_id=a.id and t.posted_at is not null group by 1,2,3 order by 2,1;
