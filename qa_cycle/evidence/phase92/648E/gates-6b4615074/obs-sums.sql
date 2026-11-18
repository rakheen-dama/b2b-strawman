-- 648E OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [context: trust accounts]
select id, account_name, account_type, investment_basis, is_primary, status from tenant_7d218705360b.trust_accounts order by created_at;

-- [context: investments after the lifecycle]
select i.id, i.account_number, i.investment_basis, i.status, i.principal, c.name client, fa.account_name funding, ia.account_name holding from tenant_7d218705360b.trust_investments i left join tenant_7d218705360b.customers c on c.id=i.customer_id join tenant_7d218705360b.trust_accounts fa on fa.id=i.trust_account_id join tenant_7d218705360b.trust_accounts ia on ia.id=i.investment_account_id order by i.created_at;

-- [context: investment rows after the lifecycle]
select i.account_number, t.transaction_type, t.status, t.ledger, a.account_name, coalesce(c.name,'<no client>') client, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_investments i on i.id=t.investment_id join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id left join tenant_7d218705360b.customers c on c.id=t.customer_id order by t.created_at;

-- [a quickcollect-after-lifecycle: general (CLIENT on the account)]
select coalesce(sum(t.ledger_effect),0) general from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='66f2d61f-5a07-407d-80bf-2d35804e9fe5' and t.ledger='CLIENT' and t.posted_at is not null;

-- [a quickcollect-after-lifecycle: invested (CLIENT_INVESTMENT of s86(4) investments placed from the account)]
select coalesce(sum(t.ledger_effect),0) invested from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_investments i on i.id=t.investment_id where i.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.trust_account_id=i.investment_account_id and t.customer_id='66f2d61f-5a07-407d-80bf-2d35804e9fe5' and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null;

-- [a quickcollect-after-lifecycle: CLIENT-ledger totals by type on the account (posted)]
select t.transaction_type, count(*) n, sum(t.amount) amount, sum(t.ledger_effect) ledger_effect from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='66f2d61f-5a07-407d-80bf-2d35804e9fe5' and t.ledger='CLIENT' and t.posted_at is not null group by 1 order by 1;

-- [a quickcollect-after-lifecycle: the client's rows by account and ledger (posted)]
select a.account_name, t.ledger, count(*) n, sum(t.ledger_effect) ledger_sum from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='66f2d61f-5a07-407d-80bf-2d35804e9fe5' and t.posted_at is not null group by 1,2 order by 1,2;

-- [c moroka-baseline: general (CLIENT on the account)]
select coalesce(sum(t.ledger_effect),0) general from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT' and t.posted_at is not null;

-- [c moroka-baseline: invested (CLIENT_INVESTMENT of s86(4) investments placed from the account)]
select coalesce(sum(t.ledger_effect),0) invested from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_investments i on i.id=t.investment_id where i.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.trust_account_id=i.investment_account_id and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null;

-- [c moroka-baseline: CLIENT-ledger totals by type on the account (posted)]
select t.transaction_type, count(*) n, sum(t.amount) amount, sum(t.ledger_effect) ledger_effect from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT' and t.posted_at is not null group by 1 order by 1;

-- [c moroka-baseline: the client's rows by account and ledger (posted)]
select a.account_name, t.ledger, count(*) n, sum(t.ledger_effect) ledger_sum from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.posted_at is not null group by 1,2 order by 1,2;

-- [c: s86(3) investment rows (expect INTER_ACCOUNT, customer_id NULL)]
select i.account_number, i.customer_id is null inv_customer_null, t.transaction_type, t.ledger, t.customer_id is null tx_customer_null, t.ledger_effect, t.posted_at is not null posted from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_investments i on i.id=t.investment_id where i.investment_basis='FIRM_DISCRETION' order by t.created_at;

-- [b2 moroka-pending: general (CLIENT on the account)]
select coalesce(sum(t.ledger_effect),0) general from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT' and t.posted_at is not null;

-- [b2 moroka-pending: invested (CLIENT_INVESTMENT of s86(4) investments placed from the account)]
select coalesce(sum(t.ledger_effect),0) invested from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_investments i on i.id=t.investment_id where i.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.trust_account_id=i.investment_account_id and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null;

-- [b2 moroka-pending: CLIENT-ledger totals by type on the account (posted)]
select t.transaction_type, count(*) n, sum(t.amount) amount, sum(t.ledger_effect) ledger_effect from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT' and t.posted_at is not null group by 1 order by 1;

-- [b2 moroka-pending: the client's rows by account and ledger (posted)]
select a.account_name, t.ledger, count(*) n, sum(t.ledger_effect) ledger_sum from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.posted_at is not null group by 1,2 order by 1,2;

-- [b: the placement's rows after approval]
select t.transaction_type, t.status, t.ledger, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.investment_id='0bcba89d-70ae-42fd-b770-e9d4d720c168' order by t.created_at;

-- [b4 moroka-approved: general (CLIENT on the account)]
select coalesce(sum(t.ledger_effect),0) general from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT' and t.posted_at is not null;

-- [b4 moroka-approved: invested (CLIENT_INVESTMENT of s86(4) investments placed from the account)]
select coalesce(sum(t.ledger_effect),0) invested from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_investments i on i.id=t.investment_id where i.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.trust_account_id=i.investment_account_id and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null;

-- [b4 moroka-approved: CLIENT-ledger totals by type on the account (posted)]
select t.transaction_type, count(*) n, sum(t.amount) amount, sum(t.ledger_effect) ledger_effect from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT' and t.posted_at is not null group by 1 order by 1;

-- [b4 moroka-approved: the client's rows by account and ledger (posted)]
select a.account_name, t.ledger, count(*) n, sum(t.ledger_effect) ledger_sum from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.posted_at is not null group by 1,2 order by 1,2;

-- [b: the placement's rows after the withdrawal]
select t.transaction_type, t.status, t.ledger, a.account_name, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.investment_id='0bcba89d-70ae-42fd-b770-e9d4d720c168' order by t.created_at;

-- [b6 moroka-withdrawn: general (CLIENT on the account)]
select coalesce(sum(t.ledger_effect),0) general from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT' and t.posted_at is not null;

-- [b6 moroka-withdrawn: invested (CLIENT_INVESTMENT of s86(4) investments placed from the account)]
select coalesce(sum(t.ledger_effect),0) invested from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_investments i on i.id=t.investment_id where i.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.trust_account_id=i.investment_account_id and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null;

-- [b6 moroka-withdrawn: CLIENT-ledger totals by type on the account (posted)]
select t.transaction_type, count(*) n, sum(t.amount) amount, sum(t.ledger_effect) ledger_effect from tenant_7d218705360b.trust_transactions t where t.trust_account_id='6f25bf5d-fcd5-4584-b947-d0edc8e8ea2e' and t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.ledger='CLIENT' and t.posted_at is not null group by 1 order by 1;

-- [b6 moroka-withdrawn: the client's rows by account and ledger (posted)]
select a.account_name, t.ledger, count(*) n, sum(t.ledger_effect) ledger_sum from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.customer_id='79a8995d-de11-4e1b-a343-ced352868e20' and t.posted_at is not null group by 1,2 order by 1,2;
