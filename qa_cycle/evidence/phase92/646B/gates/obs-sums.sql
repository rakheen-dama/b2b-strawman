-- 646B OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [d1: trust accounts]
select id, account_name, account_type, is_primary, status, created_at from tenant_7d218705360b.trust_accounts order by created_at;

-- [d2: creditor accounts per trust account]
select c.trust_account_id, c.account_type, c.name, c.id, c.created_at from tenant_7d218705360b.trust_creditor_accounts c order by c.trust_account_id, c.account_type;

-- [d3: creditor-account count per trust account (expected 2, one of each type)]
select a.id as trust_account_id, a.account_name, count(c.id) as creditor_accounts, count(distinct c.account_type) as distinct_types from tenant_7d218705360b.trust_accounts a left join tenant_7d218705360b.trust_creditor_accounts c on c.trust_account_id=a.id group by a.id, a.account_name order by a.account_name;

-- [d4: ledger-target / customer-required constraints]
select n.nspname, c.conname, pg_get_constraintdef(c.oid) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='tenant_7d218705360b' and c.conname in ('chk_trust_txn_ledger_target','chk_trust_txn_customer_required') order by c.conname;

-- [d5: chk_trust_txn_type names]
select count(*) as type_count, string_agg(m[1], ', ' order by ord) as names from pg_constraint c join pg_namespace n on n.oid=c.connamespace, lateral regexp_matches(pg_get_constraintdef(c.oid), '''([A-Z_]+)''::character varying', 'g') with ordinality as x(m, ord) where n.nspname='tenant_7d218705360b' and c.conname='chk_trust_txn_type';

-- [d6: trust_transactions creditor_account_id / investment_id / ledger]
select count(*) as total, count(*) filter (where creditor_account_id is null) as creditor_null, count(*) filter (where investment_id is null) as investment_null, string_agg(distinct ledger, ',') as ledgers from tenant_7d218705360b.trust_transactions;

-- [d7: EQ-2]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers,
            (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null)
          - (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as eq2_difference;

-- [d8: derived creditor-account balances (expected 0.00 — no NON_CLIENT rows yet)]
select c.account_type, coalesce(sum(t.ledger_effect) filter (where t.posted_at is not null),0) as balance from tenant_7d218705360b.trust_creditor_accounts c left join tenant_7d218705360b.trust_transactions t on t.creditor_account_id=c.id group by c.account_type order by 1;

-- [d9: audit events for trust_creditor_account]
select a.event_type, a.entity_type, a.entity_id, a.occurred_at, a.details from tenant_7d218705360b.audit_events a where a.entity_type in ('trust_creditor_account','trust_account') order by a.occurred_at;

-- [d10: flyway v18]
select version, description, success, installed_on from tenant_7d218705360b.flyway_schema_history where version='18';

-- [c: role capability matrix (trust capabilities)]
select r.slug, array_agg(c.capability order by c.capability) filter (where c.capability like '%TRUST%') as trust_caps from tenant_7d218705360b.org_roles r left join tenant_7d218705360b.org_role_capabilities c on c.org_role_id=r.id group by r.slug order by r.slug;
