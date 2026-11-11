#!/usr/bin/env bash
# PR #1754 gate: re-run legal-lifecycle once on a fresh PR-head stack (G1 lifecycle failed at day-07-14-work-trust:78
# "Bob: Log 120min on Sipho" — the Fees → Time summary did not show Bob's committed entry; 5 downstream cascades).
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1754/lib.sh
unset E2E_VISUAL
caffeinate -dims & CPID=$!
cd $WT
echo "L HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ') load=$(uptime | sed 's/.*averages: //')" >> $TL
stack_up L || { echo L-STACKFAIL >> $TL; kill $CPID; echo L-ALLDONE-FAIL >> $TL; exit 1; }
run_g L-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
echo "L-lifecycle load-after=$(uptime | sed 's/.*averages: //')" >> $TL
docker logs e2e-backend > $W/L-backend.log 2>&1
echo "L-ALLDONE $(ts)" >> $TL
kill $CPID
