#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 648A)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-648A
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/648A-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (648A-new classes):"
unzip -l $J/app.jar | grep -o "[^ ]*trustaccounting/[a-z/]*\(InvestmentPlacementHooks\|InvestmentBalanceRow\|TrustInvestmentService\|TrustTransactionService\)\.class"
(cd $J && unzip -q -o app.jar '*InvestmentPlacementHooks.class' '*investment/TrustInvestmentService.class' '*transaction/TrustTransactionService.class' '*report/InvestmentRegisterQuery.class' '*db/migration/tenant/V23__*')
c() { LC_ALL=C grep -a -c "$2" "$(find $J -name "$1" | head -1)"; }
echo "== InvestmentPlacementHooks.class exists: $(find $J -name 'InvestmentPlacementHooks.class' | wc -l | tr -d ' ') ; contains onPlacementApproving: $(c InvestmentPlacementHooks.class onPlacementApproving)"
echo "== TrustInvestmentService.class contains onPlacementApproving: $(c TrustInvestmentService.class onPlacementApproving) ; 'INV-WD-': $(c TrustInvestmentService.class 'INV-WD-')"
echo "== TrustTransactionService.class contains 'Withdraw the investment instead': $(c TrustTransactionService.class 'Withdraw the investment instead') ; onPlacementApproving: $(c TrustTransactionService.class onPlacementApproving)"
echo "== InvestmentRegisterQuery.class contains 'Firm (section 86(3))': $(c InvestmentRegisterQuery.class 'Firm (section 86(3))')"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V2[0-9][^ ]*" | sort -V | tail -3
echo "== V23 in jar contains chk_trust_invest_client_by_basis: $(LC_ALL=C grep -a -c 'chk_trust_invest_client_by_basis' "$(find $J -name 'V23__*' | head -1)")"
echo "== frontend container: 648A strings in .next (excluding cache):"
for s in 'Add investment account' 'Add Investment Account' 'Firm (section 86(3))' 'Returns to trust' 'Select the investment account' 'Awaiting approval' 'create-trust-account-dialog'; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF \"$s\" /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 3"
docker exec e2e-postgres psql -U postgres -d app -c "select column_name, is_nullable from information_schema.columns where table_schema='$S' and table_name='trust_investments' and column_name in ('investment_account_id','project_id','customer_id','deposit_transaction_id') order by 1"
docker exec e2e-postgres psql -U postgres -d app -c "select conname, pg_get_constraintdef(c.oid) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where n.nspname='$S' and conname='chk_trust_invest_client_by_basis'"
docker exec e2e-postgres psql -U postgres -d app -c "select account_name, account_type, investment_basis, is_primary from $S.trust_accounts order by created_at"
done
rm -rf $J
} > $OUT 2>&1
