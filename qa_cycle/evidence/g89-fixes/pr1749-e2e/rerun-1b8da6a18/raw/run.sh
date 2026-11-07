#!/usr/bin/env bash
# PR #1749 rerun at 1b8da6a18: C capture (lifecycle -> depth-ii) then G scored (lifecycle -> depth-ii)
source /tmp/pr1749s/lib.sh
cd $WT
echo "HEAD $(git rev-parse HEAD) $(ts)" >> $TL
caffeinate -dims & echo $! > $W/caffeinate.pid
stack_up C || { echo C-STACKFAIL >> $TL; exit 1; }
run_pw C-lifecycle frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --update-snapshots=all
run_pw C-depth-ii frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json --update-snapshots=all
cp $W/C-depth-ii.txt $RAW/C-depth-ii.txt; cp $W/C-depth-ii.json $RAW/C-depth-ii.json; mktsv $W/C-depth-ii.json $RAW/C-depth-ii.tsv
echo C-ALLDONE >> $TL
stack_up G || { echo G-STACKFAIL >> $TL; exit 1; }
run_pw G-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
rm -rf $W/G-lifecycle-test-results; cp -R $WT/frontend/test-results $W/G-lifecycle-test-results
run_pw G-depth-ii frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json
rm -rf $W/G-depth-ii-test-results; cp -R $WT/frontend/test-results $W/G-depth-ii-test-results
echo ALLDONE >> $TL
