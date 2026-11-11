#!/usr/bin/env bash
# PR #1757 e2e gate, part 1 at d7ef98978 (FINAL gate): install, portal vitest, G1 (fresh stack) scored legal-lifecycle,
# legal-depth-ii, default test:e2e — E2E_VISUAL unset, single scored run each (no capture-then-score);
# then the Flyway probe, the F28/F36 firm+portal API probe, the D39 transfer probe and the investment (F86/F87) probe.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1757-final/lib.sh
source $W/obs.sh
unset E2E_VISUAL
cd $WT
echo "HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ') E2E_VISUAL=${E2E_VISUAL:-unset} load=$(uptime | sed 's/.*averages: //')" >> $TL
caffeinate -dims & echo $! > $W/caffeinate-g1.pid
echo "pnpm-install start $(ts) node=$(node -v)" >> $TL
/opt/homebrew/bin/pnpm install --frozen-lockfile > $RAW/pnpm-install.txt 2>&1
echo "pnpm-install rc=$? end $(ts)" >> $TL
echo "portal-vitest start $(ts) node=$(cd $WT/portal && /opt/homebrew/bin/pnpm exec node -v) envnode=$(env node -v)" >> $TL
( cd $WT/portal && NODE_OPTIONS= /opt/homebrew/bin/pnpm test ) > $RAW/portal-vitest.txt 2>&1
echo "portal-vitest rc=$? end $(ts)" >> $TL
echo "dirty after install+vitest: $(git status --porcelain --untracked-files=all | wc -l | tr -d ' ')" >> $TL
stack_up G1 || { echo G1-STACKFAIL >> $TL; kill $(cat $W/caffeinate-g1.pid); echo G1-ALLDONE-FAIL >> $TL; exit 1; }
flyway_probe G1-start
run_g G1-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
echo "G1-lifecycle load-after=$(uptime | sed 's/.*averages: //')" >> $TL
run_g G1-depth-ii frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json
echo "G1-depth-ii load-after=$(uptime | sed 's/.*averages: //')" >> $TL
run_g G1-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
echo "G1-default load-after=$(uptime | sed 's/.*averages: //')" >> $TL
echo "G1 f28-probe start $(ts)" >> $TL
f28_probe G1
echo "G1 f28-probe end $(ts)" >> $TL
echo "G1 d39-probe start $(ts)" >> $TL
d39_probe G1
echo "G1 d39-probe end $(ts)" >> $TL
echo "G1 inv-probe start $(ts)" >> $TL
inv_probe G1
echo "G1 inv-probe end $(ts)" >> $TL
flyway_probe G1-end
T=$(tenant)
{ echo "===== G1 backend WARN/ERROR"; echo "ERROR count=$(docker logs e2e-backend 2>&1 | grep -c '"level":"ERROR"')"; } >> $ST
docker logs e2e-backend > $W/G1-backend.log 2>&1
echo "G1-ALLDONE $(ts)" >> $TL
kill $(cat $W/caffeinate-g1.pid) 2>/dev/null
