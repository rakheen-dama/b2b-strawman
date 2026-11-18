-- 648B OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [context: trust accounts]
select id, account_name, account_type, investment_basis, is_primary, status from tenant_7d218705360b.trust_accounts order by created_at;

-- [context: investments after the lifecycle]
select i.id, i.account_number, i.investment_basis, i.status, i.principal, i.deposit_date, c.name client, ia.account_name holding from tenant_7d218705360b.trust_investments i left join tenant_7d218705360b.customers c on c.id=i.customer_id join tenant_7d218705360b.trust_accounts ia on ia.id=i.investment_account_id order by i.created_at;

-- [context: interest entries after the lifecycle]
select e.investment_id, e.entry_date, e.gross_amount, e.fund_share_amount, e.net_amount, e.presentation, e.status, e.bank_statement_line_id is not null has_line from tenant_7d218705360b.trust_investment_interest e order by e.created_at;

-- [b0 quickcollect after the lifecycle: trust_investment_interest rows]
select e.id, e.entry_date, e.gross_amount, e.fund_share_amount, e.net_amount, e.presentation, e.status, e.bank_statement_line_id is not null has_line, e.net_transaction_id, e.fund_share_transaction_id from tenant_7d218705360b.trust_investment_interest e where e.investment_id='0e9c017b-fe4b-4b8c-9a6e-5515dd96e195' order by e.created_at;

-- [b0 quickcollect after the lifecycle: the investment's interest rows in trust_transactions]
select t.id, t.transaction_type, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.status, t.posted_at is not null posted, a.account_name, t.bank_statement_line_id is not null has_line, t.reference from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.investment_id='0e9c017b-fe4b-4b8c-9a6e-5515dd96e195' and t.transaction_type in ('INV_INTEREST','FUND_INV_SHARE') order by t.created_at;

-- [b0 quickcollect after the lifecycle: investment balance (CLIENT_INVESTMENT rows of the investment, posted)]
select coalesce(sum(t.ledger_effect),0) balance from tenant_7d218705360b.trust_transactions t where t.investment_id='0e9c017b-fe4b-4b8c-9a6e-5515dd96e195' and t.trust_account_id=(select investment_account_id from tenant_7d218705360b.trust_investments where id='0e9c017b-fe4b-4b8c-9a6e-5515dd96e195') and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null;

-- [a: the imported statement's lines]
select l.id, l.line_number, l.transaction_date, l.description, l.amount, l.match_status, l.suggested_classification, l.classification from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='18964ae9-07d3-46dc-9f3e-acadaafbc9ed' order by l.line_number;

-- [a quickcollect after GROSS 400: trust_investment_interest rows]
select e.id, e.entry_date, e.gross_amount, e.fund_share_amount, e.net_amount, e.presentation, e.status, e.bank_statement_line_id is not null has_line, e.net_transaction_id, e.fund_share_transaction_id from tenant_7d218705360b.trust_investment_interest e where e.investment_id='0e9c017b-fe4b-4b8c-9a6e-5515dd96e195' order by e.created_at;

-- [a quickcollect after GROSS 400: the investment's interest rows in trust_transactions]
select t.id, t.transaction_type, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.status, t.posted_at is not null posted, a.account_name, t.bank_statement_line_id is not null has_line, t.reference from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.investment_id='0e9c017b-fe4b-4b8c-9a6e-5515dd96e195' and t.transaction_type in ('INV_INTEREST','FUND_INV_SHARE') order by t.created_at;

-- [a quickcollect after GROSS 400: investment balance (CLIENT_INVESTMENT rows of the investment, posted)]
select coalesce(sum(t.ledger_effect),0) balance from tenant_7d218705360b.trust_transactions t where t.investment_id='0e9c017b-fe4b-4b8c-9a6e-5515dd96e195' and t.trust_account_id=(select investment_account_id from tenant_7d218705360b.trust_investments where id='0e9c017b-fe4b-4b8c-9a6e-5515dd96e195') and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null;

-- [a: the 400,00 line after recording]
select l.id, l.match_status, l.classification, l.trust_transaction_id, t.transaction_type, t.amount from tenant_7d218705360b.bank_statement_lines l left join tenant_7d218705360b.trust_transactions t on t.id=l.trust_transaction_id where l.id='56d5095a-0e34-40d1-8a17-ffbaa518997e';

-- [b1 moroka fresh NET 33,33: trust_investment_interest rows]
select e.id, e.entry_date, e.gross_amount, e.fund_share_amount, e.net_amount, e.presentation, e.status, e.bank_statement_line_id is not null has_line, e.net_transaction_id, e.fund_share_transaction_id from tenant_7d218705360b.trust_investment_interest e where e.investment_id='99d9e32d-7d2c-443a-945f-6e49bb43d52c' order by e.created_at;

-- [b1 moroka fresh NET 33,33: the investment's interest rows in trust_transactions]
select t.id, t.transaction_type, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.status, t.posted_at is not null posted, a.account_name, t.bank_statement_line_id is not null has_line, t.reference from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.trust_accounts a on a.id=t.trust_account_id where t.investment_id='99d9e32d-7d2c-443a-945f-6e49bb43d52c' and t.transaction_type in ('INV_INTEREST','FUND_INV_SHARE') order by t.created_at;

-- [b1 moroka fresh NET 33,33: investment balance (CLIENT_INVESTMENT rows of the investment, posted)]
select coalesce(sum(t.ledger_effect),0) balance from tenant_7d218705360b.trust_transactions t where t.investment_id='99d9e32d-7d2c-443a-945f-6e49bb43d52c' and t.trust_account_id=(select investment_account_id from tenant_7d218705360b.trust_investments where id='99d9e32d-7d2c-443a-945f-6e49bb43d52c') and t.ledger='CLIENT_INVESTMENT' and t.posted_at is not null;

-- [c: the imported statement's lines]
select l.id, l.line_number, l.transaction_date, l.description, l.amount, l.match_status, l.suggested_classification, l.classification from tenant_7d218705360b.bank_statement_lines l where l.bank_statement_id='78d1c9a8-37f0-4d3e-ae1f-d34ef4e79d0e' order by l.line_number;
