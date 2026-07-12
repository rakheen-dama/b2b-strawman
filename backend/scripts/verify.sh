#!/usr/bin/env bash
# verify.sh — preflight-guarded full backend verify. Use this instead of bare `./mvnw verify`.
# Runs under caffeinate when available so an unattended verify cannot absorb
# macOS system sleep (observed: a baseline run absorbed two ~16-min sleeps).
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/verify-preflight.sh
if command -v caffeinate >/dev/null 2>&1; then
  exec caffeinate -is ./mvnw verify "$@"
else
  exec ./mvnw verify "$@"
fi
