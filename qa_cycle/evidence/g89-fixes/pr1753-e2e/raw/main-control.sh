#!/usr/bin/env bash
# PR #1753 gate: main 9c268e376 control on a fresh stack for (1) invoice-lifecycle.spec.ts:88 "DRAFT -> APPROVED"
# (G1 default failure, substring Approve locator vs the audit row "Fee note approved") and (2) the frontend body
# computed font (Plex not applied to body text on the PR head). Then back to the PR head b146c09e0.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1753/lib.sh
unset E2E_VISUAL
BASE=$RAW
export RAW=$BASE/main-9c268e376 W=$W/main-9c268e376; TL=$RAW/timeline.txt; ST=$RAW/stacks.txt; mkdir -p $RAW $W
caffeinate -dims & CPID=$!
cd $WT
echo "before checkout HEAD $(git rev-parse HEAD) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ') $(ts)" >> $TL
git checkout -q --detach 9c268e3761e62d465e00dd3ef95181293adb657e || { echo CHECKOUT-FAIL >> $TL; kill $CPID; exit 1; }
echo "HEAD $(git rev-parse HEAD) $(ts)" >> $TL
stack_up M || { echo M-STACKFAIL >> $TL; git checkout -q --detach b146c09e0b5a8b14bf393f65534ec6d09fa0a207; kill $CPID; exit 1; }
node /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1753/shot.cjs $WT/frontend frontend http://localhost:3001 $RAW/main-frontend-dashboard.png > $RAW/main-frontend-dashboard.json 2>&1
run_g M-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
run_g M-repeat10-invoice-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/invoices/invoice-lifecycle.spec.ts --repeat-each=10 --reporter=list,json
T=$(tenant); ID=$(psql_e2e "select id from $T.invoices where status='APPROVED' order by created_at desc limit 1")
node /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1753/approve-locator-probe.cjs $WT/frontend http://localhost:3001 $ID > $RAW/M-approve-locator-probe.json 2>&1
git checkout -q --detach b146c09e0b5a8b14bf393f65534ec6d09fa0a207
echo "MAIN-ALLDONE $(ts) HEAD=$(git rev-parse --short HEAD) dirty=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ')" >> $TL
kill $CPID
