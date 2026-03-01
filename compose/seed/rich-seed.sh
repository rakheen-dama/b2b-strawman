#!/bin/sh
# compose/seed/rich-seed.sh — Orchestrate rich E2E data seeding
#
# Usage:
#   bash compose/seed/rich-seed.sh              # Idempotent — adds missing data
#   bash compose/seed/rich-seed.sh --reset      # Wipe + recreate everything
#   bash compose/seed/rich-seed.sh --only customers,projects  # Specific modules
#
# Runs from repo root (host) or inside Docker container.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Source shared library
. "${SCRIPT_DIR}/lib/common.sh"

# ── Parse flags ──────────────────────────────────────────────────────
RESET=false
ONLY=""

while [ $# -gt 0 ]; do
  case "$1" in
    --reset) RESET=true; shift ;;
    --only)  ONLY="$2"; shift 2 ;;
    --only=*) ONLY="${1#--only=}"; shift ;;
    *) echo "Unknown flag: $1"; exit 1 ;;
  esac
done

# Helper: check if a module should run
should_run() {
  module="$1"
  if [ -z "$ONLY" ]; then
    return 0  # Run everything
  fi
  echo ",$ONLY," | grep -q ",${module},"
}

# ── Banner ───────────────────────────────────────────────────────────
echo "============================================"
echo "  E2E Rich Seed"
echo "============================================"
echo "  Backend:  ${BACKEND_URL}"
echo "  Mock IDP: ${MOCK_IDP_URL}"
echo "  Reset:    ${RESET}"
echo "  Only:     ${ONLY:-all}"
echo "============================================"

# ── Reset (if requested) ────────────────────────────────────────────
if [ "$RESET" = true ]; then
  . "${SCRIPT_DIR}/lib/reset.sh"
  do_reset
fi

# ── Verify backend is healthy ───────────────────────────────────────
if ! curl -sf "${BACKEND_URL}/actuator/health" > /dev/null 2>&1; then
  echo "ERROR: Backend not reachable at ${BACKEND_URL}"
  echo "Start the E2E stack first: bash compose/scripts/e2e-up.sh"
  exit 1
fi

# ── Run modules in dependency order ──────────────────────────────────

if should_run "customers"; then
  . "${SCRIPT_DIR}/lib/customers.sh"
  seed_customers
fi

if should_run "projects"; then
  . "${SCRIPT_DIR}/lib/projects.sh"
  seed_projects
fi

if should_run "tasks"; then
  . "${SCRIPT_DIR}/lib/tasks.sh"
  seed_tasks
fi

if should_run "time-entries"; then
  . "${SCRIPT_DIR}/lib/time-entries.sh"
  seed_time_entries
fi

if should_run "rates-budgets"; then
  . "${SCRIPT_DIR}/lib/rates-budgets.sh"
  seed_rates_budgets
fi

if should_run "invoices"; then
  . "${SCRIPT_DIR}/lib/invoices.sh"
  seed_invoices
fi

if should_run "retainers"; then
  . "${SCRIPT_DIR}/lib/retainers.sh"
  seed_retainers
fi

if should_run "documents"; then
  . "${SCRIPT_DIR}/lib/documents.sh"
  seed_documents
fi

if should_run "comments"; then
  . "${SCRIPT_DIR}/lib/comments.sh"
  seed_comments
fi

if should_run "proposals"; then
  . "${SCRIPT_DIR}/lib/proposals.sh"
  seed_proposals
fi

# ── Summary ──────────────────────────────────────────────────────────
echo ""
echo "============================================"
echo "  E2E Rich Seed Complete!"
echo "============================================"
echo ""
echo "  Customers:    4 (Acme, Bright, Carlos, Dormant)"
echo "  Projects:     6"
echo "  Tasks:        20"
echo "  Time entries: 16"
echo "  Invoices:     2"
echo "  Retainers:    1"
echo "  Documents:    3"
echo "  Comments:     5"
echo "  Proposals:    1"
echo ""
echo "  Login: http://localhost:3001/mock-login"
echo ""
