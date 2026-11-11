#!/usr/bin/env bash
# PR #1755 supplemental: observe the legal-za v14 -> v15 reconcile on boot. Fresh stack U built from main 20857c03c
# (tenant provisioned at v14), then ONLY the backend container is rebuilt from the PR worktree (1d2918ea8) on the
# same postgres volume, so the PR backend boots against an existing v14 tenant. Read-only SQL throughout.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1755/lib.sh
source $W/obs.sh
source $W/trust-probe.sh
MWT=/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp8-main
caffeinate -dims & CPID=$!
echo "U main-wt HEAD $(cd $MWT && git rev-parse HEAD) dirty=$(cd $MWT && git status --porcelain --untracked-files=all | wc -l | tr -d ' ') $(ts)" >> $TL
echo "stack-U(main) launch $(ts)" >> $TL
( cd $MWT && bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh ) > $W/stack-U-main.log 2>&1
rc=$?; echo "stack-U(main) rc=$rc end $(ts)" >> $TL; cp $W/stack-U-main.log $RAW/stack-U-main.log
[ $rc -ne 0 ] && { echo U-STACKFAIL >> $TL; kill $CPID; echo U-ALLDONE-FAIL >> $TL; exit 1; }
T=$(tenant)
{ echo "===== U before swap (main backend) $(ts)"
  echo "backend image=$(docker inspect -f '{{.Image}}' e2e-backend) wd=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' e2e-backend)"
  echo "tenant=$T flyway tenant=$(psql_e2e "select version from $T.flyway_schema_history where success order by installed_rank desc limit 1") global=$(psql_e2e "select version from public.flyway_schema_history where success order by installed_rank desc limit 1")"; } >> $RAW/obs-pack-U.txt
pack_probe U-before-swap-main
docker logs e2e-backend > $W/U-backend-main.log 2>&1
echo "stack-U swap backend -> PR $(ts)" >> $TL
( cd $WT && SHELL=/bin/bash docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps backend ) > $W/stack-U-swap.log 2>&1
rc=$?; echo "stack-U swap rc=$rc $(ts)" >> $TL; cp $W/stack-U-swap.log $RAW/stack-U-swap.log
until curl -sf http://localhost:8081/actuator/health >/dev/null; do sleep 3; done
sleep 5
{ echo "===== U after swap (PR backend) $(ts)"
  echo "backend image=$(docker inspect -f '{{.Image}}' e2e-backend) wd=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' e2e-backend)"
  echo "postgres container created=$(docker inspect -f '{{.Created}}' e2e-postgres) (unchanged => same data)"
  echo "jar has TrustTransactionTypeLabels: $(docker exec e2e-backend sh -c 'cd /tmp && (ls /app/*.jar 2>/dev/null || true)' )"; } >> $RAW/obs-pack-U.txt
docker exec e2e-backend sh -c 'for j in $(find / -name "*.jar" -path "*app*" 2>/dev/null | head -3); do unzip -l $j 2>/dev/null | grep -E "TrustTransactionTypeLabels|legal-za/pack.json"; done; find / -name "TrustTransactionTypeLabels*.class" 2>/dev/null | head -2' >> $RAW/obs-pack-U.txt 2>&1
pack_probe U-after-swap-PR
echo "U trust-probe start $(ts)" >> $TL
trust_probe U
echo "U trust-probe end $(ts)" >> $TL
docker logs e2e-backend > $W/U-backend-pr.log 2>&1
cp $W/U-backend-pr.log $RAW/U-backend-pr.log
echo "U-ALLDONE $(ts)" >> $TL
kill $CPID
