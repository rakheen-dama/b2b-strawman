-- 648D OBS read-only evidence queries (tenant schema tenant_7d218705360b)

-- [context: trust accounts before]
select id, account_name, account_type, investment_basis, is_primary, require_dual_approval, status from tenant_7d218705360b.trust_accounts order by created_at;

-- [context: portal read-model before]
select customer_id, matter_id, current_balance, invested_balance, last_transaction_at from portal.portal_trust_balance where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b';

-- [a: investment account]
select id, account_name, account_type, investment_basis, status from tenant_7d218705360b.trust_accounts where id='d6bee85a-6e5f-4d79-9144-6f41a5ac0ae1';

-- [b: deposit]
select id, transaction_type, status, amount, ledger, ledger_effect, posted_at is not null posted from tenant_7d218705360b.trust_transactions where reference='OBS648D-DEP';

-- [context: trust accounts before]
select id, account_name, account_type, investment_basis, is_primary, require_dual_approval, status from tenant_7d218705360b.trust_accounts order by created_at;

-- [context: portal read-model before]
select customer_id, matter_id, current_balance, invested_balance, last_transaction_at from portal.portal_trust_balance where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b';

-- [a: investment account]
select id, account_name, account_type, investment_basis, status from tenant_7d218705360b.trust_accounts where id='d6bee85a-6e5f-4d79-9144-6f41a5ac0ae1';

-- [b: deposit]
select id, transaction_type, status, amount, ledger, ledger_effect, posted_at is not null posted from tenant_7d218705360b.trust_transactions where reference='OBS648D-DEP';

-- [p0: portal balance before placement]
select current_balance, invested_balance from portal.portal_trust_balance where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b' and matter_id='350afd4f-466c-4274-98dc-1951763341f4';

-- [context: trust accounts before]
select id, account_name, account_type, investment_basis, is_primary, require_dual_approval, status from tenant_7d218705360b.trust_accounts order by created_at;

-- [context: portal read-model before]
select customer_id, matter_id, current_balance, invested_balance, last_transaction_at from portal.portal_trust_balance where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b';

-- [a: investment account]
select id, account_name, account_type, investment_basis, status from tenant_7d218705360b.trust_accounts where id='d6bee85a-6e5f-4d79-9144-6f41a5ac0ae1';

-- [b: deposit]
select id, transaction_type, status, amount, ledger, ledger_effect, posted_at is not null posted from tenant_7d218705360b.trust_transactions where reference='OBS648D-DEP';

-- [p0: portal balance before placement]
select current_balance, invested_balance from portal.portal_trust_balance where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b' and matter_id='350afd4f-466c-4274-98dc-1951763341f4';

-- [c: placement rows (all ledgers) for the investment]
select t.transaction_type, t.status, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.posted_at is not null posted, t.trust_account_id, t.customer_id is not null has_customer, t.project_id is not null has_matter, t.recorded_by, t.approved_by from tenant_7d218705360b.trust_transactions t where t.investment_id='0c195b9d-4564-4201-b665-d242bfe4304b' or t.id='28cd8482-a811-4a7e-bfe2-c6abd392b39c' order by t.created_at;

-- [p1: portal balance after placement]
select current_balance, invested_balance from portal.portal_trust_balance where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b' and matter_id='350afd4f-466c-4274-98dc-1951763341f4';

-- [d: interest entry]
select entry_date, gross_amount, fund_share_amount, net_amount, presentation, status, net_transaction_id, fund_share_transaction_id from tenant_7d218705360b.trust_investment_interest where investment_id='0c195b9d-4564-4201-b665-d242bfe4304b';

-- [d: all trust rows for the investment after interest]
select t.transaction_type, t.status, t.amount, t.ledger, t.ledger_effect, t.cashbook_effect, t.customer_id is not null has_customer, t.project_id is not null has_matter, t.trust_account_id from tenant_7d218705360b.trust_transactions t where t.investment_id='0c195b9d-4564-4201-b665-d242bfe4304b' or t.id='28cd8482-a811-4a7e-bfe2-c6abd392b39c' order by t.created_at;

-- [p2: portal.portal_trust_balance]
select customer_id, matter_id, current_balance, invested_balance, last_transaction_at from portal.portal_trust_balance where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b';

-- [p2: portal.portal_trust_transaction]
select transaction_type, ledger, amount, running_balance, transaction_date, description, reference from portal.portal_trust_transaction where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b' and matter_id='350afd4f-466c-4274-98dc-1951763341f4' order by occurred_at, id;

-- [p2: tenant posted sums for Acme by ledger (firm truth)]
select ledger, sum(ledger_effect) from tenant_7d218705360b.trust_transactions where customer_id='8bcbf386-8786-4ec3-99ce-abb54178781b' and posted_at is not null group by ledger order by ledger;
