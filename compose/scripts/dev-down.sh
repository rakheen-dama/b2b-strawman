#!/usr/bin/env bash
# dev-down.sh — Stop the local development stack.
# By default preserves volumes (database data, S3 objects).
# Use --clean to also wipe all volumes for a fresh start.
#
# Usage: bash compose/scripts/dev-down.sh [--clean]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.yml"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose.yml not found at $COMPOSE_FILE"
  exit 1
fi

if [[ "${1:-}" == "--clean" ]]; then
  echo "Stopping dev stack and wiping all volumes..."
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans
  echo ""
  echo "Stack stopped. All containers and volumes removed."
else
  echo "Stopping dev stack (preserving data)..."
  docker compose -f "$COMPOSE_FILE" down --remove-orphans
  echo ""
  echo "Stack stopped. Volumes preserved — data will persist on next start."
  echo "Use --clean to also wipe volumes."
fi
