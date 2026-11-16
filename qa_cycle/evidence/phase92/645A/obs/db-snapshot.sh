#!/bin/bash
# read-only DB snapshot for OBS 645A
S=tenant_7d218705360b; RUN=59dcfaa9-8991-4e4f-a6fe-d9488cb00746; ACC=39a2b53b-29c9-49fb-b550-163550fb2699
docker exec e2e-postgres psql -U postgres -d app -v ON_ERROR_STOP=1 \
 -c "select now() as observed_at" \
 -c "select id,status,approved_by,posted_at,total_interest,total_lpff_share,total_client_share,updated_at from $S.interest_runs where id='$RUN'" \
 -c "select count(*) as txns_referencing_run from $S.trust_transactions where reference like '%$RUN%'" \
 -c "select transaction_type,count(*) as n from $S.trust_transactions where trust_account_id='$ACC' and transaction_type in ('INTEREST_CREDIT','INTEREST_LPFF') group by 1 union all select 'INTEREST_* (account total)', count(*) from $S.trust_transactions where trust_account_id='$ACC' and transaction_type in ('INTEREST_CREDIT','INTEREST_LPFF')" \
 -c "select count(*) as account_txn_total from $S.trust_transactions where trust_account_id='$ACC'" \
 -c "select count(*) as allocations, count(trust_transaction_id) as allocations_linked_to_txn from $S.interest_allocations where interest_run_id='$RUN'" \
 -c "select count(*) as alloc_lines_linked_to_txn from $S.interest_allocation_lines l join $S.interest_allocations a on a.id=l.interest_allocation_id where a.interest_run_id='$RUN' and l.trust_transaction_id is not null"
