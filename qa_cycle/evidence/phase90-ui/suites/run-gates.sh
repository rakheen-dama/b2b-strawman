#!/usr/bin/env bash
# 644A exit-gate runner: BE-VERIFY -> fresh-stack E2E-LEGAL + E2E-DEFAULT -> FE-GATE -> PORTAL-GATE.
# One gate at a time on the machine (never overlap E2E with a Maven verify).
# usage: run-gates.sh <worktree> <outdir>
set -u
WT="$1"; OUT="$2"; mkdir -p "$OUT"
PNPM=/opt/homebrew/bin/pnpm
export NODE_OPTIONS=""
S="$OUT/summary.txt"
cd "$WT" || exit 9
echo "start $(date -u +%FT%TZ) head $(git rev-parse HEAD) node $(node -v)" > "$S"

# --- BE-VERIFY ---
pkill -f 'surefire' 2>/dev/null; sleep 2
echo "be-verify start $(date -u +%FT%TZ)" >> "$S"
( cd backend && ./mvnw -q spotless:apply > "$OUT/be-spotless.log" 2>&1; echo "spotless exit $?" >> "$S"; bash scripts/verify.sh > "$OUT/be-verify.log" 2>&1 ); BE=$?
echo "be-verify exit $BE end $(date -u +%FT%TZ)" >> "$S"
( cd backend && grep -E 'Tests run:.*Fail' "$OUT/be-verify.log" | tail -3 >> "$S"
  FAILED=$(grep -rl 'failures="[1-9]\|errors="[1-9]' target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml 2>/dev/null | sed 's|.*/TEST-||;s|\.xml||' | paste -sd, -)
  echo "be-failed-classes: ${FAILED:-none}" >> "$S"
  echo "be-testcases: $(cat target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml 2>/dev/null | grep -c '<testcase')" >> "$S"
  echo "be-skipped-testcases: $(cat target/surefire-reports/TEST-*.xml target/failsafe-reports/TEST-*.xml 2>/dev/null | grep -c '<skipped')" >> "$S" )
git status --short backend > "$OUT/be-git-status-after.txt"

# --- E2E on one fresh stack ---
echo "e2e start $(date -u +%FT%TZ)" >> "$S"
bash compose/scripts/e2e-down.sh > "$OUT/e2e-down.log" 2>&1; echo "e2e-down exit $?" >> "$S"
SHELL=/bin/bash docker builder prune -f > "$OUT/prune.log" 2>&1; echo "prune exit $?" >> "$S"
SHELL=/bin/bash bash compose/scripts/e2e-up.sh > "$OUT/e2e-up.log" 2>&1; echo "e2e-up exit $? $(date -u +%FT%TZ)" >> "$S"
docker ps --format '{{.Names}} {{.Image}} {{.CreatedAt}}' | grep e2e- > "$OUT/e2e-containers.txt"
cd frontend
$PNPM install --frozen-lockfile > "$OUT/pnpm-install.log" 2>&1; echo "pnpm install exit $?" >> "$S"
for suite in legal-lifecycle legal-depth-ii; do
  echo "$suite start $(date -u +%FT%TZ)" >> "$S"
  E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 $PNPM test:e2e:$suite > "$OUT/$suite.log" 2>&1
  echo "$suite exit $? :: $(grep -E '^\s+[0-9]+ (passed|failed|skipped|flaky|did not run)' "$OUT/$suite.log" | tr -s ' ' | paste -sd';' -) end $(date -u +%FT%TZ)" >> "$S"
done
echo "default start $(date -u +%FT%TZ)" >> "$S"
E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 $PNPM test:e2e > "$OUT/default.log" 2>&1
echo "default exit $? :: $(grep -E '^\s+[0-9]+ (passed|failed|skipped|flaky|did not run)' "$OUT/default.log" | tr -s ' ' | paste -sd';' -) end $(date -u +%FT%TZ)" >> "$S"
cd "$WT" && git checkout -- frontend/test-results documentation/screenshots 2>/dev/null; git status --short > "$OUT/git-status-after-e2e.txt"

# --- FE-GATE ---
echo "fe-gate start $(date -u +%FT%TZ)" >> "$S"
( cd frontend && rm -rf .next && $PNPM lint > "$OUT/fe-lint.log" 2>&1 && echo "fe lint ok" >> "$S" \
  && $PNPM typecheck > "$OUT/fe-typecheck.log" 2>&1 && echo "fe typecheck ok" >> "$S" \
  && $PNPM format:check > "$OUT/fe-format.log" 2>&1 && echo "fe format ok" >> "$S" \
  && $PNPM test > "$OUT/fe-test.log" 2>&1 && echo "fe test ok" >> "$S" \
  && $PNPM build > "$OUT/fe-build.log" 2>&1 && echo "fe build ok" >> "$S" ); FE=$?
echo "fe-gate exit $FE end $(date -u +%FT%TZ) :: $(grep -E 'Test Files|Tests  ' "$OUT/fe-test.log" | tr -s ' ' | paste -sd';' -)" >> "$S"

# --- PORTAL-GATE ---
echo "portal-gate start $(date -u +%FT%TZ)" >> "$S"
( cd portal && $PNPM install --frozen-lockfile > "$OUT/portal-install.log" 2>&1 && $PNPM lint > "$OUT/portal-lint.log" 2>&1 && echo "portal lint ok" >> "$S" \
  && $PNPM typecheck > "$OUT/portal-typecheck.log" 2>&1 && echo "portal typecheck ok" >> "$S" \
  && $PNPM format:check > "$OUT/portal-format.log" 2>&1 && echo "portal format ok" >> "$S" \
  && $PNPM test > "$OUT/portal-test.log" 2>&1 && echo "portal test ok" >> "$S" \
  && $PNPM build > "$OUT/portal-build.log" 2>&1 && echo "portal build ok" >> "$S" ); PO=$?
echo "portal-gate exit $PO end $(date -u +%FT%TZ) :: $(grep -E 'Test Files|Tests  ' "$OUT/portal-test.log" | tr -s ' ' | paste -sd';' -)" >> "$S"

git status --short > "$OUT/git-status-final.txt"
echo "end $(date -u +%FT%TZ)" >> "$S"
touch "$OUT/DONE"
