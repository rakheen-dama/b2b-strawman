#!/bin/bash
# usage: vproof.sh <outfile> <buildlog>
OUT=$1; LOG=$2
WT=/Users/rakheendama/Projects/2026/worktree-epic-649B
{
echo "== worktree: $WT @ $(git -C $WT rev-parse --short HEAD)"
echo "== build log source lines:"; grep -m3 -i 'Building from source\|worktree-epic' $LOG
MIG=backend/src/main/resources/db/migration/tenant/V17__phase92_trust_reversal_contra.sql
WSHA=$(shasum $WT/$MIG | cut -d' ' -f1)
rm -rf /tmp/649B-jar && mkdir -p /tmp/649B-jar && docker cp e2e-backend:/app/app.jar /tmp/649B-jar/app.jar >/dev/null && (cd /tmp/649B-jar && unzip -q -o app.jar "db/migration/tenant/V17*")
JSHA=$(shasum /tmp/649B-jar/db/migration/tenant/V17__phase92_trust_reversal_contra.sql | cut -d" " -f1)
LBL=$(unzip -l /tmp/649B-jar/app.jar | grep -o "io/.*TrustTransactionTypeLabels.class")
echo "== worktree V17 sha1: $WSHA"
echo "== jar V17 sha1 (docker cp /app/app.jar): $JSHA -> $( [ "$JSHA" = "$WSHA" ] && echo IDENTICAL || echo DIFFERENT )"; echo "== jar contains (new in 649B): $LBL"
echo "== health: $(curl -s http://localhost:8081/actuator/health)  frontend :3001 HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
TS=$(docker exec e2e-postgres psql -U postgres -d app -Atc "select schema_name from information_schema.schemata where schema_name like 'tenant_%'")
echo "== tenant schemas: $TS"
for S in $TS; do
docker exec e2e-postgres psql -U postgres -d app -c "select version, description, success, installed_on from $S.flyway_schema_history order by installed_rank desc limit 4"
docker exec e2e-postgres psql -U postgres -d app -c "select conname, pg_get_constraintdef(oid) from pg_constraint where conrelid='$S.trust_transactions'::regclass and conname like 'chk_trust_txn%' order by conname"
docker exec e2e-postgres psql -U postgres -d app -c "\d+ $S.trust_transactions" | sed -n '/Check constraints/,/Foreign-key/p'
done
} > $OUT 2>&1
