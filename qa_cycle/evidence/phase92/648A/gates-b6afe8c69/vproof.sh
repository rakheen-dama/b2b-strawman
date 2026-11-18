#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 648A @ b6afe8c69; adapted from gates/vproof.sh)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-648A
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/648A-jar-b6
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (648A classes, incl. TrustAmounts new in 463e1c64a):"
unzip -l $J/app.jar | grep -o "[^ ]*trustaccounting/[a-z/]*\(InvestmentPlacementHooks\|InvestmentBalanceRow\|TrustInvestmentService\|TrustTransactionService\|TrustAmounts\|TrustTransactionTypeLabels\|LedgerTotals\)\.class"
(cd $J && unzip -q -o app.jar '*InvestmentPlacementHooks.class' '*investment/TrustInvestmentService.class' '*transaction/TrustTransactionService.class' '*transaction/TrustAmounts.class' '*transaction/TrustTransactionTypeLabels.class' '*ledger/LedgerTotals.class' '*report/InvestmentRegisterQuery.class' '*event/TrustNotificationHandler.class' '*transaction/TrustTransactionRepository.class' '*db/migration/tenant/V23__*')
c() { LC_ALL=C grep -a -c "$2" "$(find $J -name "$1" | head -1)"; }
echo "== TrustAmounts.class exists: $(find $J -name 'TrustAmounts.class' | wc -l | tr -d ' ') ; 'more than 2 decimal places': $(c TrustAmounts.class 'more than 2 decimal places')"
echo "== TrustTransactionTypeLabels.class contains placementNoun: $(c TrustTransactionTypeLabels.class placementNoun) ; 'placement in a section 86(4) investment': $(c TrustTransactionTypeLabels.class 'placement in a section 86(4) investment')"
echo "== TrustNotificationHandler.class references placementNoun: $(c TrustNotificationHandler.class placementNoun)"
echo "== LedgerTotals.class contains the old D-1 throw 'has no client-ledger total yet' (expect 0): $(c LedgerTotals.class 'has no client-ledger total yet')"
echo "== TrustInvestmentService.class contains 'The deposit date cannot be in the future.': $(c TrustInvestmentService.class 'The deposit date cannot be in the future.') ; onPlacementApproving: $(c TrustInvestmentService.class onPlacementApproving) ; 'INV-WD-': $(c TrustInvestmentService.class 'INV-WD-')"
echo "== TrustTransactionService.class contains 'Withdraw the investment instead': $(c TrustTransactionService.class 'Withdraw the investment instead')"
echo "== TrustTransactionRepository.class contains CLIENT ledger filter text 'ledger': $(c TrustTransactionRepository.class "ledger = ")"
echo "== InvestmentRegisterQuery.class contains 'Firm (section 86(3))': $(c InvestmentRegisterQuery.class 'Firm (section 86(3))')"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V2[0-9][^ ]*" | sort -V | tail -3
echo "== V23 in jar contains chk_trust_invest_client_by_basis: $(LC_ALL=C grep -a -c 'chk_trust_invest_client_by_basis' "$(find $J -name 'V23__*' | head -1)")"
echo "== frontend container: 648A strings in .next (server + static):"
for s in "The withdrawal date cannot be before the investment's deposit date." 'The withdrawal date cannot be in the future.' 'The deposit date cannot be in the future.' 'investment-client-cell' 'LPFF share' 'Add investment account' 'Firm (section 86(3))' 'Returns to trust' 'create-trust-account-dialog'; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF \"$s\" /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 3"
docker exec e2e-postgres psql -U postgres -d app -c "select column_name, is_nullable from information_schema.columns where table_schema='$S' and table_name='trust_investments' and column_name in ('investment_account_id','project_id','customer_id','deposit_transaction_id') order by 1"
docker exec e2e-postgres psql -U postgres -d app -c "select account_name, account_type, investment_basis, is_primary from $S.trust_accounts order by created_at"
done
rm -rf $J
} > $OUT 2>&1
