#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 646D)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-646D
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/646D-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (646D-touched classes):"
unzip -l $J/app.jar | grep -o "[^ ]*\(mcp/dto/McpTrustBalanceDto\|mcp/tool/TrustTools\|transaction/TrustTransactionService\|ledger/ClientLedgerService\)\.class"
(cd $J && unzip -q -o app.jar '*mcp/dto/McpTrustBalanceDto.class' '*mcp/tool/TrustTools.class' '*trustaccounting/transaction/TrustTransactionService.class' '*trustaccounting/ledger/ClientLedgerService*.class')
c() { LC_ALL=C grep -a -c "$2" $(find $J -name "$1" | head -1); }
echo "== McpTrustBalanceDto.class contains 'totalTrustCreditorsMinor' (646D): $(c McpTrustBalanceDto.class totalTrustCreditorsMinor)"
echo "== McpTrustBalanceDto.class contains 'cashbookMinor' (646D): $(c McpTrustBalanceDto.class cashbookMinor)"
echo "== TrustTools.class contains 'trust account or client' (646D review fix CR-1): $(c TrustTools.class 'trust account or client')"
echo "== TrustTransactionService.class contains 'The fee transfer date cannot be in the future' (646D): $(c TrustTransactionService.class 'The fee transfer date cannot be in the future')"
echo "== ClientLedgerService\$TotalBalanceResponse.class contains 'creditorBreakdown' (646D): $(LC_ALL=C grep -a -c creditorBreakdown "$(find $J -name 'ClientLedgerService$TotalBalanceResponse.class' | head -1)")"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V1[5-9][^ ]*\|db/migration/tenant/V2[0-9][^ ]*" | sort -V | tail -3
echo "== frontend container: 646D strings in .next (excluding cache):"
for s in 'Fee transfer date (optional)' 'trust-position-creditors' 'fee-transfer-date-input' 'The fee transfer date cannot be in the future' 'Trust creditors'; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF '$s' /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "-- old label 'Client ledgers' still in the trend-chart / widget chunks? (expect only recon breakdown chunks):"
docker exec e2e-frontend sh -c "grep -rlF 'Client ledgers' /app/frontend/.next/static 2>/dev/null | head -6"
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 4"
docker exec e2e-postgres psql -U postgres -d app -c "select (select count(*) from $S.trust_accounts) trust_accounts, (select count(*) from $S.trust_creditor_accounts) creditor_accounts, (select count(*) from $S.trust_transactions) trust_txns, (select count(*) from $S.trust_reconciliations) recons"
done
rm -rf $J
} > $OUT 2>&1
