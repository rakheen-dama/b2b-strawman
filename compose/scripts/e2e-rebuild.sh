#!/usr/bin/env bash
# e2e-rebuild.sh — Rebuild and restart specific E2E stack service(s).
# Only rebuilds the named service(s); other containers stay running.
#
# Usage: bash compose/scripts/e2e-rebuild.sh <service> [service...]
# Example: bash compose/scripts/e2e-rebuild.sh backend frontend
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.e2e.yml"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose.e2e.yml not found at $COMPOSE_FILE"
  exit 1
fi

if [[ $# -eq 0 ]]; then
  echo "Usage: e2e-rebuild.sh <service> [service...]"
  echo ""
  echo "Available services:"
  docker compose -f "$COMPOSE_FILE" config --services 2>/dev/null | sed 's/^/  /'
  exit 1
fi

echo "Rebuilding: $*"
docker compose -f "$COMPOSE_FILE" up -d --build "$@"
echo ""
echo "Done. Rebuilt and restarted: $*"
