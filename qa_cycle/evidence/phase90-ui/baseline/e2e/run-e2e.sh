#!/usr/bin/env bash
# usage: run-e2e.sh <worktree> <label> [default]
set -u
WT="$1"; LABEL="$2"; WITH_DEFAULT="${3:-}"
OUT=/tmp/637A-e2e/$LABEL; mkdir -p "$OUT"
cd "$WT" || exit 9
echo "start $(date -u +%FT%TZ) head $(git rev-parse --short HEAD)" > "$OUT/summary.txt"
bash compose/scripts/e2e-down.sh > "$OUT/down.log" 2>&1
SHELL=/bin/bash docker builder prune -f > "$OUT/prune.log" 2>&1
SHELL=/bin/bash bash compose/scripts/e2e-up.sh > "$OUT/up.log" 2>&1; echo "e2e-up exit $?" >> "$OUT/summary.txt"
cd frontend
for suite in legal-lifecycle legal-depth-ii; do
  E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" /opt/homebrew/bin/pnpm test:e2e:$suite > "$OUT/$suite.log" 2>&1
  echo "$suite exit $? :: $(grep -E '^\s+[0-9]+ (passed|failed|skipped|flaky|did not run)' "$OUT/$suite.log" | tr -s ' ' | paste -sd';' -)" >> "$OUT/summary.txt"
done
if [ "$WITH_DEFAULT" = "default" ]; then
  E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" /opt/homebrew/bin/pnpm test:e2e > "$OUT/default.log" 2>&1
  echo "default exit $? :: $(grep -E '^\s+[0-9]+ (passed|failed|skipped|flaky|did not run)' "$OUT/default.log" | tr -s ' ' | paste -sd';' -)" >> "$OUT/summary.txt"
fi
cd "$WT" && git checkout -- frontend/test-results documentation/screenshots 2>/dev/null; git status --short > "$OUT/git-status-after.txt"
echo "end $(date -u +%FT%TZ)" >> "$OUT/summary.txt"
touch "$OUT/DONE"
