#!/bin/bash
# scripts/run-regression-test.sh — Run regression tests (API + Playwright)
#
# Usage:
#   bash scripts/run-regression-test.sh           # Run both API and UI tests
#   bash scripts/run-regression-test.sh --api      # Run only API tests
#   bash scripts/run-regression-test.sh --ui       # Run only Playwright tests
#
# Prerequisites:
#   E2E stack running: bash compose/scripts/e2e-up.sh

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RUN_API=true
RUN_UI=true

while [ $# -gt 0 ]; do
  case "$1" in
    --api) RUN_API=true; RUN_UI=false; shift ;;
    --ui)  RUN_API=false; RUN_UI=true; shift ;;
    *)     echo "Unknown flag: $1"; exit 1 ;;
  esac
done

BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3001}"
MOCK_IDP_URL="${MOCK_IDP_URL:-http://localhost:8090}"

# ── Pre-flight checks ────────────────────────────────────────────
echo ""
echo "=== Regression Test Runner ==="
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
  exit 1
fi
echo "  [OK] Frontend: ${FRONTEND_URL}"

if ! curl -sf "${MOCK_IDP_URL}/.well-known/jwks.json" > /dev/null 2>&1; then
  echo "  [FAIL] Mock IDP not reachable at ${MOCK_IDP_URL}"
  exit 1
fi
echo "  [OK] Mock IDP: ${MOCK_IDP_URL}"
echo ""

API_OK=0
UI_OK=0

# ── Part 1: API Regression Tests ─────────────────────────────────
if [ "$RUN_API" = true ]; then
  echo "=== Running API Regression Tests ==="
  echo ""
  if bash "${PROJECT_ROOT}/scripts/regression-test.sh"; then
    API_OK=1
  else
    echo "  [FAIL] API regression tests failed"
  fi
  echo ""
else
  API_OK=1
  echo "  [SKIP] API tests (--ui flag)"
fi

# ── Part 2: Playwright Regression Tests ──────────────────────────
if [ "$RUN_UI" = true ]; then
  echo "=== Running Playwright Regression Tests ==="
  echo ""
  cd "${PROJECT_ROOT}/frontend"
  if NODE_OPTIONS="" PLAYWRIGHT_BASE_URL="${FRONTEND_URL}" \
    /opt/homebrew/bin/pnpm exec playwright test \
      e2e/tests/auth/ \
      e2e/tests/navigation/ \
      e2e/tests/settings/ \
      e2e/tests/portal/ \
      --reporter=list --config=e2e/playwright.config.ts; then
    UI_OK=1
    echo ""
    echo "  [PASS] Playwright regression tests passed"
  else
    echo ""
    echo "  [FAIL] Playwright regression tests failed"
  fi
  echo ""
else
  UI_OK=1
  echo "  [SKIP] Playwright tests (--api flag)"
fi

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "╔═══════════════════════════════════════════════════════╗"
echo "║  REGRESSION TEST SUMMARY                              ║"
echo "╠═══════════════════════════════════════════════════════╣"
if [ "$RUN_API" = true ]; then
  if [ $API_OK -eq 1 ]; then
    echo "║  API Tests:      PASS                                ║"
  else
    echo "║  API Tests:      FAIL                                ║"
  fi
fi
if [ "$RUN_UI" = true ]; then
  if [ $UI_OK -eq 1 ]; then
    echo "║  Playwright UI:  PASS                                ║"
  else
    echo "║  Playwright UI:  FAIL                                ║"
  fi
fi
echo "╚═══════════════════════════════════════════════════════╝"

if [ $API_OK -eq 1 ] && [ $UI_OK -eq 1 ]; then
  exit 0
else
  exit 1
fi
