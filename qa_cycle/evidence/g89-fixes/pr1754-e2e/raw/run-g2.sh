#!/usr/bin/env bash
# PR #1754 e2e gate, part 2 at 80931ef69: G2 (fresh stack) portal 90-day, responsive, portal-data-isolation,
# login-bare — spaced ≥330 s with an e2e-backend restart before each — then the G89-F71 deadline probe.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1754/lib.sh
unset E2E_VISUAL
cd $WT
pdv(){ { echo "== portal_deadline_view $1 $(ts)"; psql_e2e "select source_entity||'|'||due_date||'|'||status||'|'||label from portal.portal_deadline_view order by due_date"; echo "rows=$(psql_e2e 'select count(*) from portal.portal_deadline_view')"; } >> $ST; }
echo "G2 HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ') E2E_VISUAL=${E2E_VISUAL:-unset}" >> $TL
caffeinate -dims & echo $! > $W/caffeinate-g2.pid
stack_up G2 || { echo G2-STACKFAIL >> $TL; kill $(cat $W/caffeinate-g2.pid); echo G2-ALLDONE-FAIL >> $TL; exit 1; }
T=$(tenant)
pdv G2-start
echo "G2-90day pre: tokens|age=$(psql_e2e "select count(*)||'|'||coalesce(extract(epoch from now()-max(created_at))::int,0) from $T.magic_link_tokens")" >> $ST
run_g G2-90day portal score env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:portal-client-90day --reporter=list,json
exch G2-90day; pdv after-G2-90day
spacing G2-responsive
run_g G2-responsive portal score env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:responsive --reporter=list,json
exch G2-responsive; pdv after-G2-responsive
spacing G2-isolation
run_g G2-isolation frontend score env "${FE_ENV[@]}" KGOSI_EMAIL=contact@acme.example.com /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/portal/portal-data-isolation.spec.ts --reporter=list,json
exch G2-isolation
spacing G2-login-bare
run_g G2-login-bare portal score env NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 /opt/homebrew/bin/pnpm test:e2e:login-bare --reporter=list,json
exch G2-login-bare
docker logs e2e-backend > $W/G2-backend-scored.log 2>&1
{ echo "===== G2 backend WARN/ERROR (scored runs)"; echo "ERROR count=$(grep -c '"level":"ERROR"' $W/G2-backend-scored.log) (plain ' ERROR ' lines=$(grep -cE ' ERROR ' $W/G2-backend-scored.log))"; } >> $ST
# --- G89-F71 observation (after the scored runs; spaced so the probe's magic link never 429s)
spacing F71-probe
echo "F71-probe start $(ts)" >> $TL
source $W/deadline-probe.sh
echo "F71-probe end $(ts)" >> $TL
docker logs e2e-backend > $W/G2-backend.log 2>&1
( cd $WT; git status --porcelain --untracked-files=all > $RAW/G2-final.dirty.txt; echo "G2 final git-status-lines=$(wc -l < $RAW/G2-final.dirty.txt | tr -d ' ')" >> $ST )
echo "G2-ALLDONE $(ts)" >> $TL
kill $(cat $W/caffeinate-g2.pid) 2>/dev/null
