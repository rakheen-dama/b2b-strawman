#!/usr/bin/env bash
# PR #1754 e2e gate, part 1 at 80931ef69: install, portal vitest, G1 (fresh stack) scored legal-lifecycle,
# legal-depth-ii, default test:e2e — E2E_VISUAL unset, single scored run each (no capture-then-score).
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1754/lib.sh
unset E2E_VISUAL
cd $WT
pdv(){ { echo "== portal_deadline_view $1 $(ts)"; psql_e2e "select source_entity||'|'||due_date||'|'||status||'|'||label from portal.portal_deadline_view order by due_date"; echo "rows=$(psql_e2e 'select count(*) from portal.portal_deadline_view')"; } >> $ST; }
echo "HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ') E2E_VISUAL=${E2E_VISUAL:-unset}" >> $TL
caffeinate -dims & echo $! > $W/caffeinate-g1.pid
echo "pnpm-install start $(ts) node=$(node -v)" >> $TL
/opt/homebrew/bin/pnpm install --frozen-lockfile > $RAW/pnpm-install.txt 2>&1
echo "pnpm-install rc=$? end $(ts)" >> $TL
echo "portal-vitest start $(ts) node=$(cd $WT/portal && /opt/homebrew/bin/pnpm exec node -v) envnode=$(env node -v)" >> $TL
( cd $WT/portal && NODE_OPTIONS= /opt/homebrew/bin/pnpm test ) > $RAW/portal-vitest.txt 2>&1
echo "portal-vitest rc=$? end $(ts)" >> $TL
echo "dirty after install+vitest: $(git status --porcelain --untracked-files=all | wc -l | tr -d ' ')" >> $TL
# --- G1 fresh stack
stack_up G1 || { echo G1-STACKFAIL >> $TL; kill $(cat $W/caffeinate-g1.pid); echo G1-ALLDONE-FAIL >> $TL; exit 1; }
pdv G1-start
run_g G1-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
pdv after-G1-lifecycle
run_g G1-depth-ii frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json
pdv after-G1-depth-ii
run_g G1-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
pdv after-G1-default
T=$(tenant)
{ echo "===== G1 projects after default"; psql_e2e "select name||'|'||status from $T.projects order by created_at";
  echo "===== G1 backend WARN/ERROR"; echo "all WARN/ERROR count=$(docker logs e2e-backend 2>&1 | grep -cE ' (WARN|ERROR) ')"; echo "ERROR count=$(docker logs e2e-backend 2>&1 | grep -cE ' ERROR ')";
  echo "portal-deadline-related ERROR/WARN lines=$(docker logs e2e-backend 2>&1 | grep -E ' (WARN|ERROR) ' | grep -ciE 'deadline')"; } >> $ST
docker logs e2e-backend > $W/G1-backend.log 2>&1
echo "G1-ALLDONE $(ts)" >> $TL
kill $(cat $W/caffeinate-g1.pid) 2>/dev/null
