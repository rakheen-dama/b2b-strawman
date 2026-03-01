#!/usr/bin/env bash
# e2e-down.sh — Tear down the E2E mock-auth stack and wipe all data.
# Removes containers and volumes for a clean slate next startup.
#
# Usage: bash compose/scripts/e2e-down.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.e2e.yml"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose.e2e.yml not found at $COMPOSE_FILE"
  exit 1
fi

echo "Stopping E2E mock-auth stack..."
docker compose -f "$COMPOSE_FILE" down -v --remove-orphans

echo ""
echo "Stack stopped. All containers and volumes removed."
echo "Run 'bash compose/scripts/e2e-up.sh' to start fresh."
