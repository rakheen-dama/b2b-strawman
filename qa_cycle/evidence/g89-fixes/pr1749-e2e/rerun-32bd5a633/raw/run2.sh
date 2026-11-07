#!/usr/bin/env bash
# Repeats 4-6: same 3 specs on the same scored stack G, --ignore-snapshots so the unchanged day-05
# "list-empty" frame (fresh-stack-only; data accumulates on a reused stack) cannot abort day-05 and starve day-30-45.
source /tmp/pr1749r/lib.sh
for i in 4 5 6; do
  run_pw G-repeat-$i frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.legal-depth-ii.config.ts \
    e2e/tests/legal-depth-ii/day-05-disbursements.spec.ts e2e/tests/legal-depth-ii/day-14-conveyancing.spec.ts e2e/tests/legal-depth-ii/day-30-45-soa-writeoff.spec.ts --ignore-snapshots --reporter=list,json
  rm -rf $W/G-repeat-$i-test-results; cp -R $WT/frontend/test-results $W/G-repeat-$i-test-results
done
echo ALLDONE2 >> $TL
