#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for the 647D re-gate @ 45dd3022f)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-647D
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
echo "== build log: frontend image built in this run (not cached):"; grep -m3 -E 'Compiled successfully|Creating an optimized production build' $LOG
echo "== frontend container: transactions-page chunks containing 'transaction-type-' AND 'whitespace-normal' (the 45dd302 fix):"
docker exec e2e-frontend sh -c "for f in \$(grep -rlF 'transaction-type-' /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null); do if grep -qF 'whitespace-normal' \$f; then echo \"\$f\"; fi; done"
echo "-- the fix's className and testid inside those chunks (context excerpt):"
docker exec e2e-frontend sh -c "for f in \$(grep -rlF 'transaction-type-' /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null); do grep -oE '.{0,80}shrink whitespace-normal.{0,120}' \$f | head -2; done" | head -6
echo "-- transactions page route chunk(s) (app/(app)/org/[slug]/trust-accounting/transactions):"
docker exec e2e-frontend sh -c "ls /app/frontend/.next/server/app/\(app\)/org/\[slug\]/trust-accounting/transactions/ 2>/dev/null; grep -lF 'transaction-type-' /app/frontend/.next/server/app/\(app\)/org/\[slug\]/trust-accounting/transactions/*.js 2>/dev/null"
echo "== 647D strings still present in .next (feature build sanity):"
for s in 'allocate-unidentified-dialog' 'pay-to-fund-dialog' 'unidentified-deposit-pending' 'transaction-description-'; do
  echo "-- '$s': $(docker exec e2e-frontend sh -c "grep -rlF '$s' /app/frontend/.next/server /app/frontend/.next/static 2>/dev/null | wc -l") files"
done
J=/tmp/647D-regate-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
(cd $J && unzip -q -o app.jar '*creditor/UnidentifiedDepositService.class' '*transaction/TrustClientMatterRule.class' '*transaction/UnidentifiedResolutionHooks.class')
echo "== backend jar: TrustClientMatterRule.class $(find $J -name TrustClientMatterRule.class | wc -l | tr -d ' '), UnidentifiedResolutionHooks.class $(find $J -name UnidentifiedResolutionHooks.class | wc -l | tr -d ' '), UnidentifiedDepositService 'existsPostedResolutionOfItem' $(LC_ALL=C grep -a -c existsPostedResolutionOfItem $(find $J -name UnidentifiedDepositService.class))"
echo "== jar latest tenant migration:"; unzip -l $J/app.jar | grep -o "db/migration/tenant/V[0-9]*__[^ ]*" | sort -V | tail -1
rm -rf $J
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success from $S.flyway_schema_history order by installed_rank desc limit 1"
docker exec e2e-postgres psql -U postgres -d app -c "select (select count(*) from $S.trust_transactions) trust_txns, (select count(*) from $S.bank_statements) statements, (select count(*) from $S.trust_reconciliations) recons, (select count(*) from $S.unidentified_deposits) items"
done
} > $OUT 2>&1
