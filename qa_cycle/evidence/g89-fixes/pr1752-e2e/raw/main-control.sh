#!/usr/bin/env bash
# PR #1752 gate — main 1d2b5d0e3 control for the day-30-fee-note-tariff-completed frame (C capture + G scored lifecycle), then back to PR head 6fcd91abe
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1752/lib.sh
BASE=$RAW
export RAW=$BASE/main-1d2b5d0e3 W=$W/main-1d2b5d0e3; TL=$RAW/timeline.txt; ST=$RAW/stacks.txt; mkdir -p $RAW $W
caffeinate -dims & CPID=$!
cd $WT
git status --short --untracked-files=all | awk '{print $2}' > $W/dirty-before.txt
if [ -s $W/dirty-before.txt ]; then tar czf $W/pr-head-baselines.tgz -T $W/dirty-before.txt; xargs rm -f < $W/dirty-before.txt; fi
git checkout -q --detach 1d2b5d0e3b7add48ed78aadce2a73da9dd65ebcb || { echo CHECKOUT-FAIL >> $TL; exit 1; }
echo "HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --short | wc -l)" >> $TL
stack_up C || { echo C-STACKFAIL >> $TL; exit 1; }
run_pw C-lifecycle frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --update-snapshots=all
mktsv $W/C-lifecycle.json $RAW/C-lifecycle.tsv; cp $W/C-lifecycle.txt $RAW/C-lifecycle.txt
stack_up G || { echo G-STACKFAIL >> $TL; exit 1; }
run_pw G-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
rm -rf $W/G-test-results; cp -R $WT/frontend/test-results $W/G-test-results
git status --short --untracked-files=all | awk '{print $2}' > $W/dirty-after.txt
if [ -s $W/dirty-after.txt ]; then tar czf $W/main-baselines.tgz -T $W/dirty-after.txt; xargs rm -f < $W/dirty-after.txt; fi
git checkout -q --detach 6fcd91abed3b801bdb122b9a20a25d6901166a74
echo "MAIN-ALLDONE $(ts) HEAD=$(git rev-parse --short HEAD) dirty=$(git status --short | wc -l)" >> $TL
# --- D: PR head 6fcd91abe after 02:00 SAST: functional lifecycle + depth-ii, then the default suite (clean date window)
export RAW=$BASE/pr-default-clean W=/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1752/pr-default-clean; TL=$RAW/timeline.txt; ST=$RAW/stacks.txt; mkdir -p $RAW $W
until [ "$(date -u +%F)" = "$(TZ=Africa/Johannesburg date +%F)" ] && [ "$(date -u +%H%M)" -ge 0001 ]; do sleep 20; done
echo "HEAD $(git rev-parse HEAD) $(ts) utc=$(date -u +%FT%T)" >> $TL
stack_up D || { echo D-STACKFAIL >> $TL; exit 1; }
run_pw D-lifecycle-functional frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --ignore-snapshots
run_pw D-depth-ii-functional frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json --ignore-snapshots
run_pw D-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
rm -rf $W/D-default-test-results; cp -R $WT/frontend/test-results $W/D-default-test-results
{ echo "===== D time_entries from project-time spec: SAST=$(TZ=Africa/Johannesburg date '+%F %T') UTC=$(date -u '+%F %T')"; psql_e2e "select date||'|'||billable||'|'||left(description,40)||'|'||created_at from $(tenant).time_entries where description like 'Non-billable entry%' or description like 'Rate snapshot test%' order by created_at"; } >> $ST
git status --short --untracked-files=all | awk '{print $2}' > $W/dirty-after.txt
if [ -s $W/dirty-after.txt ]; then tar czf $W/D-baselines.tgz -T $W/dirty-after.txt; xargs rm -f < $W/dirty-after.txt; fi
echo "D-ALLDONE $(ts) HEAD=$(git rev-parse --short HEAD) dirty=$(git status --short | wc -l)" >> $TL
kill $CPID
