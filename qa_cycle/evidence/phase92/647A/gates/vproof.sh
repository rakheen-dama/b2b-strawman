#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 647A)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-647A
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/647A-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (647A-touched classes):"
unzip -l $J/app.jar | grep -o "[^ ]*\(reconciliation/StatementLinePostingService\|reconciliation/StatementLineClassification\|transaction/TrustTransactionService\)\.class"
(cd $J && unzip -q -o app.jar '*reconciliation/StatementLinePostingService*.class' '*reconciliation/StatementLineClassification.class' '*trustaccounting/transaction/TrustTransactionService.class' '*reconciliation/TrustReconciliationController.class')
c() { LC_ALL=C grep -a -c "$2" "$(find $J -name "$1" | head -1)"; }
echo "== StatementLinePostingService.class contains 'A zero-amount statement line cannot be recorded' (647A): $(c StatementLinePostingService.class 'A zero-amount statement line cannot be recorded')"
echo "== StatementLinePostingService.class contains 'Only a line recorded from the statement is reversed' (647A): $(c StatementLinePostingService.class 'Only a line recorded from the statement is reversed')"
echo "== StatementLinePostingService\$RecordFromLineRequest.class contains 'The VAT amount has at most two decimal places' (647A review fix): $(LC_ALL=C grep -a -c 'The VAT amount has at most two decimal places' "$(find $J -name 'StatementLinePostingService$RecordFromLineRequest.class' | head -1)")"
echo "== TrustReconciliationController.class contains '/api/bank-statement-lines/{lineId}/record' (647A): $(c TrustReconciliationController.class '/api/bank-statement-lines/{lineId}/record')"
echo "== TrustTransactionService.class contains 'reverseForUnmatch' (647A): $(c TrustTransactionService.class reverseForUnmatch)"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V1[5-9][^ ]*\|db/migration/tenant/V2[0-9][^ ]*" | sort -V | tail -3
echo "== frontend container: 647A strings in .next (excluding cache):"
for s in 'Recoverable from the Fidelity Fund' 'record-from-statement-dialog' 'Unmatch and reverse' 'unmatch-recorded-dialog' 'Bank charge refunded from the business account' 'Recorded: '; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF '$s' /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 4"
docker exec e2e-postgres psql -U postgres -d app -c "select (select count(*) from $S.trust_accounts) trust_accounts, (select count(*) from $S.trust_creditor_accounts) creditor_accounts, (select count(*) from $S.trust_transactions) trust_txns, (select count(*) from $S.bank_statements) statements, (select count(*) from $S.trust_reconciliations) recons"
done
rm -rf $J
} > $OUT 2>&1
