#!/usr/bin/env bash
# PR #1749 rerun at 32bd5a633: C capture (lifecycle -> depth-ii) then G scored (lifecycle -> depth-ii) + 3 changed-spec repeats
source /tmp/pr1749r/lib.sh
cd $WT
[ "$(git rev-parse HEAD)" = "32bd5a633"* ] || true
echo "HEAD $(git rev-parse HEAD) $(ts)" >> $TL
caffeinate -dims & echo $! > $W/caffeinate.pid
stack_up C || { echo C-STACKFAIL >> $TL; exit 1; }
run_pw C-lifecycle frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --update-snapshots=all
run_pw C-depth-ii frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json --update-snapshots=all
cp $W/C-depth-ii.txt $RAW/C-depth-ii.txt; cp $W/C-depth-ii.json $RAW/C-depth-ii.json; mktsv $W/C-depth-ii.json $RAW/C-depth-ii.tsv
echo C-ALLDONE >> $TL
stack_up G || { echo G-STACKFAIL >> $TL; exit 1; }
run_pw G-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
run_pw G-depth-ii frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json
rm -rf $W/G-depth-ii-test-results; cp -R $WT/frontend/test-results $W/G-depth-ii-test-results
echo G-SCORED-DONE >> $TL
# Changed specs alone x3 on the scored stack. day-05 is the run-id prerequisite (helpers/legal-depth-ii.ts:39-41).
for i in 1 2 3; do
  run_pw G-repeat-$i frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.legal-depth-ii.config.ts \
    e2e/tests/legal-depth-ii/day-05-disbursements.spec.ts e2e/tests/legal-depth-ii/day-14-conveyancing.spec.ts e2e/tests/legal-depth-ii/day-30-45-soa-writeoff.spec.ts --reporter=list,json
  rm -rf $W/G-repeat-$i-test-results; cp -R $WT/frontend/test-results $W/G-repeat-$i-test-results
done
echo ALLDONE >> $TL
