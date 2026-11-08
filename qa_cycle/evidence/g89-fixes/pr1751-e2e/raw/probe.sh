#!/usr/bin/env bash
# PR #1751 gate — clean-window lifecycle probe for the day-30 fee-note-tariff frame:
# phase M = main 4f3323525 (C capture lifecycle + G scored lifecycle), phase P = PR head f3c191bb7 (same).
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/lib.sh
BASE=/Users/rakheendama/Projects/2026/b2b-strawman-g89-docs/qa_cycle/evidence/g89-fixes/pr1751-e2e/raw
caffeinate -dims & CPID=$!
cycle(){ # tag sha
  local TAG=$1 SHA=$2
  export RAW=$BASE/$TAG W=/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/$TAG; TL=$RAW/timeline.txt; ST=$RAW/stacks.txt; mkdir -p $RAW $W
  cd $WT
  git status --short --untracked-files=all | awk '{print $2}' > $W/dirty-before.txt
  if [ -s $W/dirty-before.txt ]; then tar czf $W/dirty-before.tgz -T $W/dirty-before.txt; xargs rm -f < $W/dirty-before.txt; fi
  git checkout -q --detach $SHA
  echo "HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --short | wc -l)" >> $TL
  stack_up C || { echo C-STACKFAIL >> $TL; return 1; }
  run_pw C-lifecycle frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --update-snapshots=all
  mktsv $W/C-lifecycle.json $RAW/C-lifecycle.tsv; cp $W/C-lifecycle.txt $RAW/C-lifecycle.txt
  cp $WT/frontend/e2e/screenshots/legal-lifecycle/day-30-fee-note-tariff-completed.png $RAW/C-day-30-fee-note-tariff-completed.png 2>/dev/null
  stack_up G || { echo G-STACKFAIL >> $TL; return 1; }
  run_pw G-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
  rm -rf $W/G-lifecycle-test-results; cp -R $WT/frontend/test-results $W/G-lifecycle-test-results
  echo "$TAG-DONE $(ts)" >> $TL
}
cycle main-4f3323525-clean 4f3323525
cycle attempt4-pr-clean f3c191bb7
cd $WT; git status --short --untracked-files=all | awk '{print $2}' > /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/a4-dirty.txt; [ -s /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/a4-dirty.txt ] && tar czf /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/a4-baselines.tgz -T /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/a4-dirty.txt && xargs rm -f < /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/a4-dirty.txt
git checkout -q --detach f3c191bb7
echo "PROBE-ALLDONE $(ts) HEAD=$(git rev-parse --short HEAD) dirty=$(git status --short | wc -l)" >> /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/probe.status
kill $CPID
