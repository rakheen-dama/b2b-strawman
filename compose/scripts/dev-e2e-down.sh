#!/usr/bin/env bash
# dev-e2e-down.sh — Tear down dev stack and wipe all data.
# Always cleans volumes for a fresh state on next test run.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_DIR/dev-down.sh" --clean
