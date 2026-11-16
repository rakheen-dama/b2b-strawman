-- 649B OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: customers]
select id, name from tenant_7d218705360b.customers order by created_at;

-- [baseline: all trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, approved_by, reference, description
       from tenant_7d218705360b.trust_transactions where true order by created_at;

-- [baseline EQ-2]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers,
            (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null)
          - (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as eq2_difference;

-- [a: after Alice submits (pending contra)]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, approved_by, reference, description
       from tenant_7d218705360b.trust_transactions where id in ('06862ff6-33e4-495d-bb5b-bb610348deaa','7ec784af-26de-46cc-9e9e-382df8b4d6c3') order by created_at;

-- [a: pending EQ-2]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers,
            (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null)
          - (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as eq2_difference;

-- [a: after Bob approves]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, approved_by, reference, description
       from tenant_7d218705360b.trust_transactions where id in ('06862ff6-33e4-495d-bb5b-bb610348deaa','7ec784af-26de-46cc-9e9e-382df8b4d6c3') order by created_at;

-- [a: approved EQ-2]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers,
            (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null)
          - (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as eq2_difference;

-- [b: Apex deposit + payment (approved)]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, approved_by, reference, description
       from tenant_7d218705360b.trust_transactions where id in ('8efe8f70-b392-44eb-86e7-a5895a9516ff','3aae6c83-d613-493e-a608-9fe8503cc9cf') order by created_at;

-- [b: Apex payment + contra]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, approved_by, reference, description
       from tenant_7d218705360b.trust_transactions where id in ('3aae6c83-d613-493e-a608-9fe8503cc9cf','bde8f441-719f-49f4-b1b0-eb39dad79c63') order by created_at;

-- [b: after payment reversal EQ-2]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers,
            (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null)
          - (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as eq2_difference;

-- [c: Apex as-at client balances]
select 'beforeOriginal' as point, date '2026-11-05' as as_at, coalesce(sum(ledger_effect),0) as client_balance from tenant_7d218705360b.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='f51c155a-7175-4c18-9ce5-0f983d4729fe' and transaction_date <= '2026-11-05' union all select 'between' as point, date '2026-11-09' as as_at, coalesce(sum(ledger_effect),0) as client_balance from tenant_7d218705360b.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='f51c155a-7175-4c18-9ce5-0f983d4729fe' and transaction_date <= '2026-11-09' union all select 'today' as point, date '2026-11-16' as as_at, coalesce(sum(ledger_effect),0) as client_balance from tenant_7d218705360b.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='f51c155a-7175-4c18-9ce5-0f983d4729fe' and transaction_date <= '2026-11-16';

-- [c: statement-equivalent rows (posted, CLIENT, Apex, between..today)]
select transaction_date, transaction_type, status, ledger_effect, reference, description from tenant_7d218705360b.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='f51c155a-7175-4c18-9ce5-0f983d4729fe' and transaction_date between '2026-11-09' and '2026-11-16' order by transaction_date, created_at;

-- [d: portal read model for QuickCollect + Apex]
select customer_id, transaction_type, amount, running_balance, transaction_date, reference, description from portal.portal_trust_transaction where customer_id in ('72ada531-9256-446f-b8ce-bac4be1faac0','f51c155a-7175-4c18-9ce5-0f983d4729fe') order by customer_id, transaction_date, recorded_at;

-- [d: portal balances]
select customer_id, matter_id, current_balance, last_transaction_at from portal.portal_trust_balance where customer_id in ('72ada531-9256-446f-b8ce-bac4be1faac0','f51c155a-7175-4c18-9ce5-0f983d4729fe');

-- [bonus: Moroka deposit + refused contra]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, approved_by, reference, description
       from tenant_7d218705360b.trust_transactions where id in ('ccc6839d-37f6-4cb3-a7cc-4d411f9b465f','a44714eb-6b89-40b8-89a8-c585cf1f6c91') order by created_at;

-- [final: per-client derived balances]
select c.name, coalesce(sum(t.ledger_effect),0) as balance from tenant_7d218705360b.trust_transactions t join tenant_7d218705360b.customers c on c.id=t.customer_id where t.ledger='CLIENT' and t.posted_at is not null group by c.name order by c.name;

-- [final EQ-2]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers,
            (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null)
          - (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as eq2_difference;

-- [final: invariants (all expected 0)]
select
      (select count(*) from tenant_7d218705360b.trust_transactions where status in ('RECORDED','APPROVED','REVERSED') and posted_at is null) as posted_status_unposted,
      (select count(*) from tenant_7d218705360b.trust_transactions where status in ('AWAITING_APPROVAL','REJECTED') and (posted_at is not null or ledger_effect<>0 or cashbook_effect<>0)) as pending_with_effect,
      (select count(*) from tenant_7d218705360b.trust_transactions where posted_at is not null and (abs(ledger_effect)<>amount or (cashbook_effect<>ledger_effect and cashbook_effect<>0))) as bad_shape,
      (select count(*) from tenant_7d218705360b.trust_transactions o where o.status='REVERSED' and (o.reversed_by_id is null or not exists (select 1 from tenant_7d218705360b.trust_transactions r where r.id=o.reversed_by_id and r.reversal_of=o.id and r.posted_at is not null))) as reversed_without_posted_contra,
      (select count(*) from tenant_7d218705360b.trust_transactions r join tenant_7d218705360b.trust_transactions o on o.id=r.reversal_of where r.posted_at is not null and (r.ledger_effect <> -o.ledger_effect or r.cashbook_effect <> -o.cashbook_effect)) as contra_not_negation,
      (select count(*) from tenant_7d218705360b.trust_transactions r join tenant_7d218705360b.trust_transactions o on o.id=r.reversal_of where r.posted_at is not null and (o.ledger_effect + r.ledger_effect) <> 0) as pair_not_net_zero;

-- [final: all trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, approved_by, reference, description
       from tenant_7d218705360b.trust_transactions where true order by created_at;
