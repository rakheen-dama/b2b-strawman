-- 648C OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: trust accounts]
select account_name, account_type, coalesce(investment_basis,'<null>') investment_basis, is_primary, status, created_at from tenant_7d218705360b.trust_accounts order by created_at;

-- [a: trust accounts after create]
select account_name, account_type, coalesce(investment_basis,'<null>') investment_basis, is_primary, status, created_at from tenant_7d218705360b.trust_accounts order by created_at;

-- [b: trust accounts after create]
select account_name, account_type, coalesce(investment_basis,'<null>') investment_basis, is_primary, status, created_at from tenant_7d218705360b.trust_accounts order by created_at;

-- [c: trust accounts after the General create]
select account_name, account_type, coalesce(investment_basis,'<null>') investment_basis, is_primary, status, created_at from tenant_7d218705360b.trust_accounts order by created_at;

-- [d: trust accounts after the refusal probes]
select account_name, account_type, coalesce(investment_basis,'<null>') investment_basis, is_primary, status, created_at from tenant_7d218705360b.trust_accounts order by created_at;

-- [final: audit trust_account events]
select event_type, entity_id, details, occurred_at from tenant_7d218705360b.audit_events where entity_type='trust_account' order by occurred_at desc limit 6;
