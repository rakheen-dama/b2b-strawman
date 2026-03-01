#!/usr/bin/env bash
# dev-rebuild.sh — Rebuild and restart specific dev stack service(s).
# Only rebuilds the named service(s); other containers stay running.
#
# Usage: bash compose/scripts/dev-rebuild.sh <service> [service...]
# Example: bash compose/scripts/dev-rebuild.sh backend
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.yml"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose.yml not found at $COMPOSE_FILE"
  exit 1
fi

SERVICES="${*:-backend frontend}"

echo "Rebuilding: $SERVICES"
docker compose -f "$COMPOSE_FILE" up -d --build $SERVICES
echo ""
echo "Done. Rebuilt and restarted: $*"
