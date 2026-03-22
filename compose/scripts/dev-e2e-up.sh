#!/usr/bin/env bash
# dev-e2e-up.sh — Start the full dev stack for Playwright E2E tests.
# Builds all services from source, starts them, runs bootstrap, waits for health.
#
# Usage: bash compose/scripts/dev-e2e-up.sh [--clean]
#   --clean: Wipe volumes first (fresh Postgres + Keycloak)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Optional clean start
if [[ "${1:-}" == "--clean" ]]; then
  echo "Cleaning volumes first..."
  bash "$SCRIPT_DIR/dev-down.sh" --clean
fi

# Start all services (builds from source)
echo "Starting full dev stack..."
bash "$SCRIPT_DIR/dev-up.sh" --all

# Run Keycloak bootstrap (idempotent)
echo ""
echo "Running Keycloak bootstrap..."
bash "$SCRIPT_DIR/keycloak-bootstrap.sh"

echo ""
echo "=== E2E Stack Ready ==="
echo ""
echo "  Frontend:       http://localhost:3000"
echo "  Gateway:        http://localhost:8443"
echo "  Backend:        http://localhost:8080"
echo "  Keycloak:       http://localhost:8180 (admin/admin)"
echo "  Mailpit UI:     http://localhost:8025"
echo "  Mailpit API:    http://localhost:8025/api/v1/"
echo ""
echo "  Platform admin: padmin@docteams.local / password"
echo ""
echo "  Run tests:      cd frontend && npx playwright test e2e/tests/keycloak/"
echo "  Tear down:      bash compose/scripts/dev-e2e-down.sh"
echo ""
