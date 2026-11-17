#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 647B)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-647B
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/647B-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (647B-new / 647B-touched classes):"
unzip -l $J/app.jar | grep -o "[^ ]*\(creditor/UnidentifiedDeposit[A-Za-z$]*\|reconciliation/StatementLineClassification\|reconciliation/StatementLinePostingService\)\.class"
(cd $J && unzip -q -o app.jar '*creditor/UnidentifiedDeposit*.class' '*reconciliation/StatementLineClassification.class' '*reconciliation/StatementLinePostingService.class' '*db/migration/tenant/V21__*')
c() { LC_ALL=C grep -a -c "$2" "$(find $J -name "$1" | head -1)"; }
echo "== UnidentifiedDepositController.class contains '/api/trust-accounts/{accountId}/unidentified-deposits' (647B): $(c UnidentifiedDepositController.class '/api/trust-accounts/{accountId}/unidentified-deposits')"
echo "== UnidentifiedDepositService\$StatementCredit.class exists (647B): $(find $J -name 'UnidentifiedDepositService$StatementCredit.class' | wc -l | tr -d ' ')"
echo "== UnidentifiedDepositService.class contains 'lockHeldForUnmatch': $(c UnidentifiedDepositService.class lockHeldForUnmatch) ; 'markReversedByUnmatch': $(c UnidentifiedDepositService.class markReversedByUnmatch)"
echo "== StatementLineClassification.class contains 'UNIDENTIFIED_DEPOSIT' (647B): $(c StatementLineClassification.class UNIDENTIFIED_DEPOSIT)"
echo "== StatementLinePostingService.class contains 'StatementCredit' (647B): $(c StatementLinePostingService.class StatementCredit) ; 'An unidentified deposit is recorded only on a trust banking account': $(c StatementLinePostingService.class 'An unidentified deposit is recorded only on a trust banking account')"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V1[5-9][^ ]*\|db/migration/tenant/V2[0-9][^ ]*" | sort -V | tail -3
echo "== frontend container: 647B strings in .next (excluding cache):"
for s in 'unidentified-deposits-table' 'unidentified-deposit-row' 'unidentified-deposits-section' 'classification-unidentified-note' 'Unidentified deposit' 'Held on the unidentified deposits ledger'; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF '$s' /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 4"
docker exec e2e-postgres psql -U postgres -d app -c "select (select count(*) from $S.trust_accounts) trust_accounts, (select count(*) from $S.trust_creditor_accounts) creditor_accounts, (select count(*) from $S.trust_transactions) trust_txns, (select count(*) from $S.bank_statements) statements, (select count(*) from $S.trust_reconciliations) recons, (select count(*) from $S.unidentified_deposits) unidentified_deposits"
docker exec e2e-postgres psql -U postgres -d app -c "select table_name, column_name, data_type from information_schema.columns where table_schema='$S' and ((table_name='trust_transactions' and column_name='unidentified_deposit_id') or (table_name='unidentified_deposits' and column_name in ('deposit_transaction_id','status','resolved_date'))) order by 1,2"
docker exec e2e-postgres psql -U postgres -d app -c "select conname from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='$S' and conname in ('chk_trust_txn_unidentified_item','chk_unidentified_deposit_resolved','uq_unidentified_deposit_txn') order by 1"
done
rm -rf $J
} > $OUT 2>&1
