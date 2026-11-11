#!/usr/bin/env bash
# G89 exit re-score: re-run legal-lifecycle once on a fresh stack L at 461fb99ec (G1 lifecycle failed at
# day-07-14-work-trust:78 "Bob: Log 120min on Sipho" — Fees → Time summary showed only Carol although Bob's entry
# was committed (DB created_at 15:16:20.6Z); 5 downstream cascades + 31 skips). Same as #1754 rerun-lifecycle.sh.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/exit/lib.sh
source $W/obs.sh
unset E2E_VISUAL
caffeinate -dims & CPID=$!
cd $WT
echo "L HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ') load=$(uptime | sed 's/.*averages: //')" >> $TL
stack_up L || { echo L-STACKFAIL >> $TL; kill $CPID; echo L-ALLDONE-FAIL >> $TL; exit 1; }
pack_probe L-start
run_g L-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
echo "L-lifecycle load-after=$(uptime | sed 's/.*averages: //')" >> $TL
docker logs e2e-backend > $W/L-backend.log 2>&1
echo "L-ALLDONE $(ts)" >> $TL
kill $CPID
