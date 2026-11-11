#!/usr/bin/env bash
# PR #1757 FINAL gate — failure re-run (rule 1: re-run once) on a FRESH stack R1 at d7ef98978 of legal-lifecycle
# (G1: day-75-complex:224 time summary lacked the saved 6h entry + serial cascade day-90-review:83) and default test:e2e
# (G1: invoice-lifecycle:112 Record Payment not shown though INV-0006 SENT in DB; general-settings:65 brand colour clobbered).
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1757-final/lib.sh
source $W/obs.sh
unset E2E_VISUAL
cd $WT
echo "R1 HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ') E2E_VISUAL=${E2E_VISUAL:-unset} load=$(uptime | sed 's/.*averages: //')" >> $TL
caffeinate -dims & echo $! > $W/caffeinate-r1.pid
stack_up R1 || { echo R1-STACKFAIL >> $TL; kill $(cat $W/caffeinate-r1.pid); echo R1-ALLDONE-FAIL >> $TL; exit 1; }
flyway_probe R1-start
run_g R1-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
echo "R1-lifecycle load-after=$(uptime | sed 's/.*averages: //')" >> $TL
run_g R1-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
echo "R1-default load-after=$(uptime | sed 's/.*averages: //')" >> $TL
docker logs e2e-backend > $W/R1-backend.log 2>&1
echo "R1-ALLDONE $(ts)" >> $TL
kill $(cat $W/caffeinate-r1.pid) 2>/dev/null
