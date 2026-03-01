#!/usr/bin/env bash
# e2e-reseed.sh — Re-run the seed container to reset E2E data.
# Does NOT rebuild images — just re-provisions the tenant, members, and sample data.
# The seed script is idempotent (accepts 409 for already-existing resources).
#
# Usage: bash compose/scripts/e2e-reseed.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.e2e.yml"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose.e2e.yml not found at $COMPOSE_FILE"
  exit 1
fi

# Check that the stack is running
if ! curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
  echo "ERROR: E2E backend not running on port 8081."
  echo "Start the stack first: bash compose/scripts/e2e-up.sh"
  exit 1
fi

echo "Re-running seed container..."

# Remove the old seed container (it exited after first run)
docker compose -f "$COMPOSE_FILE" rm -f seed 2>/dev/null || true

# Run seed again
docker compose -f "$COMPOSE_FILE" up seed

echo ""
echo "Reseed complete."
