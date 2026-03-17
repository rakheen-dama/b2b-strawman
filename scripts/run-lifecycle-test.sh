#!/bin/bash
# scripts/run-lifecycle-test.sh — Run the 90-day lifecycle test (seed + Playwright)
#
# Usage:
#   bash scripts/run-lifecycle-test.sh           # Run both seed and Playwright
#   bash scripts/run-lifecycle-test.sh --seed    # Run only the API seed
#   bash scripts/run-lifecycle-test.sh --ui      # Run only the Playwright tests
#
# Prerequisites:
#   E2E stack running: bash compose/scripts/e2e-up.sh

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Defaults
RUN_SEED=true
RUN_UI=true

while [ $# -gt 0 ]; do
  case "$1" in
    --seed) RUN_SEED=true; RUN_UI=false; shift ;;
    --ui)   RUN_SEED=false; RUN_UI=true; shift ;;
    *)      echo "Unknown flag: $1"; exit 1 ;;
  esac
done

BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3001}"
MOCK_IDP_URL="${MOCK_IDP_URL:-http://localhost:8090}"

# ── Pre-flight checks ────────────────────────────────────────────
echo ""
echo "=== Lifecycle Test Runner ==="
echo ""

echo "  Checking E2E stack health..."

if ! curl -sf "${BACKEND_URL}/actuator/health" > /dev/null 2>&1; then
  echo "  [FAIL] Backend not reachable at ${BACKEND_URL}"
  echo "  Start the E2E stack: bash compose/scripts/e2e-up.sh"
  exit 1
fi
echo "  [OK] Backend: ${BACKEND_URL}"

if ! curl -sf "${FRONTEND_URL}" > /dev/null 2>&1; then
  echo "  [FAIL] Frontend not reachable at ${FRONTEND_URL}"
  echo "  Start the E2E stack: bash compose/scripts/e2e-up.sh"
  exit 1
fi
echo "  [OK] Frontend: ${FRONTEND_URL}"

if ! curl -sf "${MOCK_IDP_URL}/.well-known/jwks.json" > /dev/null 2>&1; then
  echo "  [FAIL] Mock IDP not reachable at ${MOCK_IDP_URL}"
  echo "  Start the E2E stack: bash compose/scripts/e2e-up.sh"
  exit 1
fi
echo "  [OK] Mock IDP: ${MOCK_IDP_URL}"
echo ""

SEED_OK=0
UI_OK=0

# ── Part 1: API Seed ─────────────────────────────────────────────
if [ "$RUN_SEED" = true ]; then
  echo "=== Running API Seed Script ==="
  echo ""
  if bash "${PROJECT_ROOT}/compose/seed/lifecycle-test.sh"; then
    SEED_OK=1
    echo ""
    echo "  [PASS] API seed completed successfully"
  else
    echo ""
    echo "  [FAIL] API seed failed"
  fi
  echo ""
else
  SEED_OK=1
  echo "  [SKIP] API seed (--ui flag)"
fi

# ── Part 2: Playwright Tests ─────────────────────────────────────
if [ "$RUN_UI" = true ]; then
  echo "=== Running Playwright Tests (read-only + interactive) ==="
  echo ""
  cd "${PROJECT_ROOT}/frontend"
  if NODE_OPTIONS="" PLAYWRIGHT_BASE_URL="${FRONTEND_URL}" \
    /opt/homebrew/bin/pnpm exec playwright test \
      e2e/tests/lifecycle.spec.ts \
      e2e/tests/lifecycle-interactive.spec.ts \
      --reporter=list --config=e2e/playwright.config.ts; then
    UI_OK=1
    echo ""
    echo "  [PASS] Playwright tests passed"
  else
    echo ""
    echo "  [FAIL] Playwright tests failed"
  fi
  echo ""
else
  UI_OK=1
  echo "  [SKIP] Playwright tests (--seed flag)"
fi

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "╔═══════════════════════════════════════════════════╗"
echo "║  LIFECYCLE TEST SUMMARY                            ║"
echo "╠═══════════════════════════════════════════════════╣"
if [ "$RUN_SEED" = true ]; then
  if [ $SEED_OK -eq 1 ]; then
    echo "║  API Seed:       PASS                              ║"
  else
    echo "║  API Seed:       FAIL                              ║"
  fi
fi
if [ "$RUN_UI" = true ]; then
  if [ $UI_OK -eq 1 ]; then
    echo "║  Playwright UI:  PASS                              ║"
  else
    echo "║  Playwright UI:  FAIL                              ║"
  fi
fi
echo "╚═══════════════════════════════════════════════════╝"

if [ $SEED_OK -eq 1 ] && [ $UI_OK -eq 1 ]; then
  exit 0
else
  exit 1
fi
