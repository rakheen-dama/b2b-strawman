#!/usr/bin/env bash
# PR #1753 gate: re-run the full default suite once on a fresh PR-head stack (G1 had invoice-lifecycle:88, class (c)); then tear down.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1753/lib.sh
unset E2E_VISUAL
caffeinate -dims & CPID=$!
cd $WT
echo "R HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ')" >> $TL
stack_up R || { echo R-STACKFAIL >> $TL; kill $CPID; exit 1; }
run_g R-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
( cd $WT && bash compose/scripts/e2e-down.sh ) > $RAW/e2e-down.txt 2>&1
echo "e2e-down rc=$? $(ts) e2e-containers=$(docker ps -a --filter name=e2e- -q | wc -l | tr -d ' ') volumes=$(docker volume ls -q | grep -c docteams-e2e_)" >> $TL
echo "R-ALLDONE $(ts) HEAD=$(git rev-parse --short HEAD) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ')" >> $TL
kill $CPID
