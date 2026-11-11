#!/usr/bin/env bash
# PR #1755 e2e gate, part 2 at 1d2918ea8: G2 (fresh stack) portal 90-day, responsive, portal-data-isolation,
# login-bare — spaced ≥330 s with an e2e-backend restart before each — then one manual weekly-digest fire.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1755/lib.sh
source $W/obs.sh
unset E2E_VISUAL
cd $WT
echo "G2 HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ') E2E_VISUAL=${E2E_VISUAL:-unset} load=$(uptime | sed 's/.*averages: //')" >> $TL
caffeinate -dims & echo $! > $W/caffeinate-g2.pid
stack_up G2 || { echo G2-STACKFAIL >> $TL; kill $(cat $W/caffeinate-g2.pid); echo G2-ALLDONE-FAIL >> $TL; exit 1; }
T=$(tenant)
{ echo "legal-za pack_version G2-start: $(psql_e2e "select pack_version from $T.pack_install where pack_id='legal-za'")"; } >> $ST
pack_probe G2-start
echo "G2-90day pre: tokens|age=$(psql_e2e "select count(*)||'|'||coalesce(extract(epoch from now()-max(created_at))::int,0) from $T.magic_link_tokens")" >> $ST
run_g G2-90day portal score env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:portal-client-90day --reporter=list,json
exch G2-90day
spacing G2-responsive
run_g G2-responsive portal score env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:responsive --reporter=list,json
exch G2-responsive
spacing G2-isolation
run_g G2-isolation frontend score env "${FE_ENV[@]}" KGOSI_EMAIL=contact@acme.example.com /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/portal/portal-data-isolation.spec.ts --reporter=list,json
exch G2-isolation
spacing G2-login-bare
run_g G2-login-bare portal score env NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 /opt/homebrew/bin/pnpm test:e2e:login-bare --reporter=list,json
exch G2-login-bare
docker logs e2e-backend > $W/G2-backend-scored.log 2>&1
{ echo "===== G2 backend WARN/ERROR (scored runs)"; echo "ERROR count=$(grep -c '"level":"ERROR"' $W/G2-backend-scored.log)"; } >> $ST
echo "G2 digest-probe start $(ts)" >> $TL
digest_probe G2
echo "G2 digest-probe end $(ts)" >> $TL
docker logs e2e-backend > $W/G2-backend.log 2>&1
( cd $WT; git status --porcelain --untracked-files=all > $RAW/G2-final.dirty.txt; echo "G2 final git-status-lines=$(wc -l < $RAW/G2-final.dirty.txt | tr -d ' ')" >> $ST )
echo "G2-ALLDONE $(ts)" >> $TL
kill $(cat $W/caffeinate-g2.pid) 2>/dev/null
