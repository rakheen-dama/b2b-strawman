#!/usr/bin/env bash
# Switch auth mode by symlinking .env to the correct config file.
#
# Usage:
#   bash compose/scripts/auth-mode.sh keycloak
#   bash compose/scripts/auth-mode.sh clerk
#
# After switching, rebuild containers that bake in build args:
#   bash compose/scripts/dev-down.sh --clean
#   bash compose/scripts/dev-up.sh

set -euo pipefail

COMPOSE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-}"

if [[ -z "$MODE" ]]; then
  # Show current mode
  if [[ -L "$COMPOSE_DIR/.env" ]]; then
    TARGET=$(readlink "$COMPOSE_DIR/.env")
    echo "Current mode: $(basename "$TARGET" | sed 's/^\.env\.//')"
  elif [[ -f "$COMPOSE_DIR/.env" ]]; then
    MODE_VAL=$(grep '^AUTH_MODE=' "$COMPOSE_DIR/.env" 2>/dev/null | cut -d= -f2)
    echo "Current mode: ${MODE_VAL:-unknown} (plain file, not symlinked)"
  else
    echo "No .env file found. Run: bash compose/scripts/auth-mode.sh keycloak"
  fi
  exit 0
fi

if [[ "$MODE" != "keycloak" && "$MODE" != "clerk" ]]; then
  echo "Usage: bash compose/scripts/auth-mode.sh [keycloak|clerk]"
  echo "  No argument = show current mode"
  exit 1
fi

ENV_FILE="$COMPOSE_DIR/.env.$MODE"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: $ENV_FILE not found"
  exit 1
fi

# Remove existing .env (file or symlink)
rm -f "$COMPOSE_DIR/.env"

# Create symlink
ln -s ".env.$MODE" "$COMPOSE_DIR/.env"

echo "Switched to $MODE mode (.env -> .env.$MODE)"
echo ""
echo "Next steps:"
echo "  1. bash compose/scripts/dev-down.sh --clean   # wipe old containers + volumes"
echo "  2. bash compose/scripts/dev-up.sh             # start with new config"
