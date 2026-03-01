#!/usr/bin/env bash
# reseed-mock-dev.sh — Legacy wrapper. Use e2e-reseed.sh directly.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$SCRIPT_DIR/e2e-reseed.sh" "$@"
