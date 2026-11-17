#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>   (read-only stack proof for 646E)
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-646E
{
echo "== worktree: $WT @ $(git -C $WT rev-parse HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
J=/tmp/646E-jar
rm -rf $J && mkdir -p $J && docker cp e2e-backend:/app/app.jar $J/app.jar >/dev/null
echo "== jar classes (TrustTransactionTypeController new in 646E, TrustTransactionDirection new in 646E):"
unzip -l $J/app.jar | grep -o "io/.*transaction/TrustTransaction\(TypeController\|Direction\)\.class"
(cd $J && unzip -q -o app.jar 'io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionTypeController.class')
echo "== controller class contains mapping literal: $(LC_ALL=C grep -a -o 'api/trust-transaction-types' $J/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionTypeController.class)"
echo "== frontend container: fetchTrustTransactionTypes / trust-transaction-types in .next server chunks:"
docker exec e2e-frontend sh -c 'grep -rl "trust-transaction-types" /app/frontend/.next/server 2>/dev/null | head -3; grep -rl "trust-transaction-types" /app/.next/server 2>/dev/null | head -3'
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
echo "== unauthenticated GET /api/trust-transaction-types: HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8081/api/trust-transaction-types) (401 = endpoint mapped behind auth)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 3"
docker exec e2e-postgres psql -U postgres -d app -c "select (select count(*) from $S.trust_accounts) trust_accounts, (select count(*) from $S.trust_creditor_accounts) creditor_accounts, (select count(*) from $S.trust_transactions) trust_txns"
done
rm -rf $J
} > $OUT 2>&1
