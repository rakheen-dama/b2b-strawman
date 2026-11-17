-- 646E OBS read-only evidence queries (schema tenant_7d218705360b); run order = step order

-- [baseline: customers]
select id, name from tenant_7d218705360b.customers order by created_at;

-- [baseline: all trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, reference from tenant_7d218705360b.trust_transactions where true order by created_at;

-- [c: Apex deposit + payment + contra]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, reference from tenant_7d218705360b.trust_transactions where id in ('f6b95509-d6d5-4e60-930a-990fe6840336','22b2848a-d6e6-4f9b-b6a5-a8fccc07cd5c','f291ded5-ee2b-4204-98d2-72b8ae4ccb89') order by created_at;

-- [d: QC deposit + pending contra]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, reference from tenant_7d218705360b.trust_transactions where id in ('1a220bef-bb32-4e83-8598-aebef177f315','3726a838-f357-4f28-b26b-b040756ab0a4') order by created_at;

-- [g: statement-equivalent rows (posted, CLIENT, Apex, from..today)]
select transaction_date, transaction_type, status, ledger_effect, reference from tenant_7d218705360b.trust_transactions where ledger='CLIENT' and posted_at is not null and customer_id='d4b1aaa9-5003-4637-babf-3fcbe12fd781' and transaction_date between '2026-11-12' and '2026-11-17' order by transaction_date, created_at;

-- [g: receipts-payments-equivalent rows (from..today)]
select transaction_date, transaction_type, status, cashbook_effect, reference from tenant_7d218705360b.trust_transactions where transaction_date between '2026-11-12' and '2026-11-17' order by transaction_date, created_at;

-- [final: EQ-2]
select (select coalesce(sum(cashbook_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null) as cashbook,
            (select coalesce(sum(ledger_effect),0) from tenant_7d218705360b.trust_transactions where posted_at is not null and ledger='CLIENT') as sum_client_ledgers;

-- [final: all trust transactions]
select id, transaction_type, amount, status, transaction_date, posted_at is not null as posted, ledger, ledger_effect,
            cashbook_effect, reversal_of, reversed_by_id, reference from tenant_7d218705360b.trust_transactions where true order by created_at;
