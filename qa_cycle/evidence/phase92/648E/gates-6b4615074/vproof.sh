#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 648E @ 6b4615074; adapted from 648A gates-b6afe8c69/vproof.sh)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-648E
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/648E-jar-6b
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (648E classes):"
unzip -l $J/app.jar | grep -o "[^ ]*trustaccounting/[a-z/]*\(LedgerTotals\|ClientLedgerService\|ClientLedgerService\$ClientLedgerCardResponse\|TrustLedgerQueryRepository\|InvestmentRegisterQuery\|ClientBalanceRow\)\.class"
(cd $J && unzip -q -o app.jar '*ledger/LedgerTotals.class' '*ledger/ClientLedgerService.class' '*ledger/ClientLedgerService$ClientLedgerCardResponse.class' '*transaction/TrustLedgerQueryRepository.class' '*report/InvestmentRegisterQuery.class' '*mcp/tool/TrustTools.class' '*mcp/dto/McpTrustBalanceDto.class')
c() { local f; f="$(find $J -name "$1" | head -1)"; [ -z "$f" ] && { echo "MISSING($1)"; return; }; LC_ALL=C grep -a -c "$2" "$f"; }
echo "== LedgerTotals.class: withInvestmentPlacements $(c LedgerTotals.class withInvestmentPlacements) ; investmentPlacements $(c LedgerTotals.class investmentPlacements) ; investmentReturns $(c LedgerTotals.class investmentReturns)"
echo "== ClientLedgerService.class: investedBalance $(c ClientLedgerService.class investedBalance)"
echo "== ClientLedgerService\$ClientLedgerCardResponse.class: investedBalance $(c 'ClientLedgerService$ClientLedgerCardResponse.class' investedBalance) ; totalInvestmentPlacements $(c 'ClientLedgerService$ClientLedgerCardResponse.class' totalInvestmentPlacements) ; totalInvestmentReturns $(c 'ClientLedgerService$ClientLedgerCardResponse.class' totalInvestmentReturns)"
echo "== TrustLedgerQueryRepository.class: investedBalancesByClient $(c TrustLedgerQueryRepository.class investedBalancesByClient)"
echo "== InvestmentRegisterQuery.class: investmentBalances $(c InvestmentRegisterQuery.class investmentBalances)"
echo "== TrustTools.class: investedMinor $(c TrustTools.class investedMinor)"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V[0-9][^ ]*" | sort -V | tail -3
echo "== frontend container: 648E strings in .next (server + static):"
for s in 'Invested (section 86(4))' 'Total held in trust' 'Placed in section 86(4) investments' 'Returned from section 86(4) investments' 'client-summary-invested' 'ledger-total-held' 'General balance'; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF \"$s\" /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 3"
docker exec e2e-postgres psql -U postgres -d app -c "select account_name, account_type, investment_basis, is_primary from $S.trust_accounts order by created_at"
done
rm -rf $J
} > $OUT 2>&1
