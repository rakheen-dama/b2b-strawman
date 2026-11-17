#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 646C)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-646C
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/646C-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar classes (books/TrustBooksService new in 646C; TrustPositionRow, OutstandingItemsRow new in 646C):"
unzip -l $J/app.jar | grep -o "io/.*trustaccounting/\(books/TrustBooksService\|transaction/TrustPositionRow\|transaction/OutstandingItemsRow\)\.class"
echo "== jar migration resource:"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V19__phase92_trust_recon_breakdown.sql"
(cd $J && unzip -q -o app.jar 'io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustLedgerQueryRepository.class')
echo "== TrustLedgerQueryRepository.class contains 'FUND_INTEREST_AND_CHARGES' (646C position SQL): $(LC_ALL=C grep -a -c 'FUND_INTEREST_AND_CHARGES' $J/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustLedgerQueryRepository.class)"
echo "== frontend container: 646C split-pane testids in .next/server chunks:"
for s in comparison-cashbook-creditors cashbook-to-creditors-difference creditor-breakdown period-end-error; do
  echo "-- $s:"; docker exec e2e-frontend sh -c "grep -rl '$s' /app/frontend/.next/server 2>/dev/null | head -3"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 4"
docker exec e2e-postgres psql -U postgres -d app -c "select column_name, data_type from information_schema.columns where table_schema='$S' and table_name='trust_reconciliations' and column_name in ('creditor_total','difference','integrity_flags','investment_creditor_total','fund_ledger_balance','unidentified_deposit_total','inter_account_balance') order by column_name"
docker exec e2e-postgres psql -U postgres -d app -c "select (select count(*) from $S.trust_accounts) trust_accounts, (select count(*) from $S.trust_creditor_accounts) creditor_accounts, (select count(*) from $S.trust_transactions) trust_txns, (select count(*) from $S.trust_reconciliations) recons"
done
rm -rf $J
} > $OUT 2>&1
