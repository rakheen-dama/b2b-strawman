#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 647D)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-647D
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/647D-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar entries (647D-new / 647D-touched classes):"
unzip -l $J/app.jar | grep -o "[^ ]*\(creditor/UnidentifiedDeposit[A-Za-z$]*\|transaction/TrustClientMatterRule\|transaction/UnidentifiedResolutionHooks\|transaction/TrustPostingService\)\.class"
(cd $J && unzip -q -o app.jar '*creditor/UnidentifiedDeposit*.class' '*transaction/TrustClientMatterRule.class' '*transaction/UnidentifiedResolutionHooks.class' '*transaction/TrustTransactionService.class' '*transaction/TrustTransactionRepository.class' '*transaction/TrustPostingService.class' '*ledger/LedgerTotals.class' '*db/migration/tenant/V21__*')
c() { LC_ALL=C grep -a -c "$2" "$(find $J -name "$1" | head -1)"; }
echo "== TrustClientMatterRule.class exists (647D new): $(find $J -name 'TrustClientMatterRule.class' | wc -l | tr -d ' ')"
echo "== UnidentifiedResolutionHooks.class exists (647D new): $(find $J -name 'UnidentifiedResolutionHooks.class' | wc -l | tr -d ' ')"
echo "== UnidentifiedDepositService.class contains 'onResolutionApproved': $(c UnidentifiedDepositService.class onResolutionApproved) ; 'existsPostedResolutionOfItem' (fix cycle 1, F-647D-1): $(c UnidentifiedDepositService.class existsPostedResolutionOfItem) ; 'findLatestReopeningDate' (R2): $(c UnidentifiedDepositService.class findLatestReopeningDate) ; 'Deposit received ': $(c UnidentifiedDepositService.class 'Deposit received ')"
echo "== UnidentifiedDepositService.class contains 'can no longer be unmatched' (F-647D-1 refusal text): $(c UnidentifiedDepositService.class 'can no longer be unmatched')"
echo "== UnidentifiedDepositController.class contains '/allocate': $(c UnidentifiedDepositController.class '/allocate') ; '/pay-to-fund': $(c UnidentifiedDepositController.class '/pay-to-fund')"
echo "== TrustTransactionService.class contains 'reverseUnidentifiedAllocation' (D6): $(c TrustTransactionService.class reverseUnidentifiedAllocation)"
echo "== TrustPostingService.class contains 'recordWithPair' (D3): $(c TrustPostingService.class recordWithPair)"
echo "== TrustTransactionRepository.class contains 'existsPostedResolutionOfItem': $(c TrustTransactionRepository.class existsPostedResolutionOfItem)"
echo "== jar migrations (latest tenant V*):"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V1[5-9][^ ]*\|db/migration/tenant/V2[0-9][^ ]*" | sort -V | tail -3
echo "== frontend container: 647D strings in .next (excluding cache):"
for s in 'allocate-unidentified-dialog' 'pay-to-fund-dialog' 'pay-to-fund-under-one-year-warning' 'unidentified-deposit-pending' 'unidentified-deposit-allocated-to' 'awaiting approval' 'UNIDENT_FUND_PAY'; do
  echo "-- '$s':"; docker exec e2e-frontend sh -c "grep -rlF '$s' /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | head -4"
done
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 3"
docker exec e2e-postgres psql -U postgres -d app -c "select (select count(*) from $S.trust_accounts) trust_accounts, (select count(*) from $S.trust_creditor_accounts) creditor_accounts, (select count(*) from $S.trust_transactions) trust_txns, (select count(*) from $S.bank_statements) statements, (select count(*) from $S.trust_reconciliations) recons, (select count(*) from $S.unidentified_deposits) unidentified_deposits"
done
rm -rf $J
} > $OUT 2>&1
