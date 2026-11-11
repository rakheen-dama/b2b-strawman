#!/usr/bin/env bash
# PR #1752 lifecycle + depth-ii re-runs on new PR head 6fcd91abe (spec-only fix of day-75-85-closure:391) (coordinator, after the G1 C3 cascade):
#  F = fresh stack, functional legal-lifecycle with --ignore-snapshots (must be 105/0)
#  C/G = fresh capture stack + fresh scored stack (screenshot-scored lifecycle re-run)
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1752/lib.sh
BASE=$RAW
export RAW=$BASE/rerun-lifecycle W=$W/rerun-lifecycle; TL=$RAW/timeline.txt; ST=$RAW/stacks.txt; mkdir -p $RAW $W
caffeinate -dims & CPID=$!
cd $WT
echo "before checkout HEAD $(git rev-parse HEAD) tracked-dirty=$(git status --short | grep -vc '^??') $(ts)" >> $TL
git checkout -q --detach 6fcd91abed3b801bdb122b9a20a25d6901166a74 || { echo CHECKOUT-FAIL >> $TL; exit 1; }
echo "HEAD $(git rev-parse HEAD) $(ts)" >> $TL
trustdb(){ local T; T=$(tenant); {
  echo "===== $1 interest_runs $(hms)"; psql_e2e "select row_to_json(r) from $T.interest_runs r order by created_at";
  echo "===== $1 trust_reconciliations"; psql_e2e "select row_to_json(r) from $T.trust_reconciliations r";
  echo "===== $1 trust audit events (deposit / interest credit)"; psql_e2e "select event_type||'|'||entity_type||'|'||occurred_at||'|'||left(details::text,220) from $T.audit_events where event_type in ('trust_deposit.recorded','trust_interest_credit.recorded') order by occurred_at";
  echo "===== $1 backend WARN/ERROR (reconcil|interest|section|report|trust)"; docker logs e2e-backend 2>&1 | grep -E ' (WARN|ERROR) ' | grep -iE 'reconcil|interest|section|report|trust' | tail -30;
  echo "count=$(docker logs e2e-backend 2>&1 | grep -E ' (WARN|ERROR) ' | grep -ciE 'reconcil|interest|section|report|trust')"; } >> $ST; }
ptime(){ # label — G89-F66 date-window proof: project-time.spec alone + time_entries dates vs SA/UTC date
  run_pw $1-project-time frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/projects/project-time.spec.ts --reporter=list,json
  rm -rf $W/$1-project-time-test-results; cp -R $WT/frontend/test-results $W/$1-project-time-test-results
  { echo "===== $1 project-time date proof: SAST=$(TZ=Africa/Johannesburg date '+%F %T') UTC=$(date -u '+%F %T')"; psql_e2e "select date||'|'||duration_minutes||'|'||billable||'|'||left(description,40)||'|'||created_at from $(tenant).time_entries where created_at > now() - interval '15 minutes' order by created_at"; } >> $ST
}
# --- F: functional
stack_up F || { echo F-STACKFAIL >> $TL; exit 1; }
run_pw F-lifecycle-functional frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --ignore-snapshots
rm -rf $W/F-test-results; cp -R $WT/frontend/test-results $W/F-test-results
run_pw F-depth-ii-functional frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json --ignore-snapshots
rm -rf $W/F-depth-ii-test-results; cp -R $WT/frontend/test-results $W/F-depth-ii-test-results
trustdb F
ptime F
docker logs e2e-backend > $W/F-backend.log 2>&1
echo F-DONE >> $TL
# --- C/G: screenshot-scored re-run
stack_up C || { echo C-STACKFAIL >> $TL; exit 1; }
run_pw C-lifecycle frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --update-snapshots=all
mktsv $W/C-lifecycle.json $RAW/C-lifecycle.tsv; cp $W/C-lifecycle.txt $RAW/C-lifecycle.txt
cp $WT/frontend/e2e/screenshots/legal-lifecycle/day-30-fee-note-tariff-completed.png $RAW/C-day-30-fee-note-tariff-completed.png 2>/dev/null
run_pw C-depth-ii frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json --update-snapshots=all
mktsv $W/C-depth-ii.json $RAW/C-depth-ii.tsv; cp $W/C-depth-ii.txt $RAW/C-depth-ii.txt
stack_up G || { echo G-STACKFAIL >> $TL; exit 1; }
run_pw G-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
rm -rf $W/G-test-results; cp -R $WT/frontend/test-results $W/G-test-results
run_pw G-depth-ii frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json
rm -rf $W/G-depth-ii-test-results; cp -R $WT/frontend/test-results $W/G-depth-ii-test-results
trustdb G
ptime G
echo "PROBE-ALLDONE $(ts)" >> $TL
kill $CPID
