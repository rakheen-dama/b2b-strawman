#!/usr/bin/env bash
# check-playwright-port.sh — PreToolUse hook for mcp__playwright__browser_navigate.
# Blocks navigation to localhost:3000 (Clerk, will fail with CAPTCHA)
# and directs agents to use localhost:3001 (mock auth E2E stack).
#
# Exit codes:
#   0 = allow (URL is not port 3000, or not localhost)
#   2 = block with message (URL targets port 3000, or stack not running)
set -euo pipefail

INPUT=$(cat)

# Extract the URL from tool input
URL=$(echo "$INPUT" | jq -r '.tool_input.url // ""' 2>/dev/null || echo "")

if [[ -z "$URL" ]]; then
  exit 0
fi

# Check if navigating to localhost:3000
if echo "$URL" | grep -qE '(localhost|127\.0\.0\.1):3000'; then
  # In Keycloak mode, port 3000 is the correct target (no CAPTCHA)
  # Check env var OR file marker (file marker works across hook process boundaries)
  AUTH_MODE="${E2E_AUTH_MODE:-}"
  if [[ -f "/tmp/.e2e-keycloak-mode" ]]; then
    AUTH_MODE="keycloak"
  fi
  if [[ "$AUTH_MODE" == "keycloak" ]]; then
    if ! curl -sf --max-time 2 http://localhost:3000/ > /dev/null 2>&1; then
      echo "Frontend is not running on port 3000."
      echo ""
      echo "Start it with:  cd frontend && NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev"
      exit 2
    fi
    exit 0
  fi

  echo "BLOCKED: Port 3000 uses Clerk authentication with CAPTCHA — agents cannot authenticate there."
  echo ""
  echo "Use the E2E mock-auth stack on port 3001 instead:"
  echo "  1. Start the stack:  bash compose/scripts/e2e-up.sh"
  echo "  2. Navigate to:      http://localhost:3001/mock-login"
  echo "  3. Click 'Sign In' to authenticate as Alice (owner)"
  echo ""
  echo "Replace 'localhost:3000' with 'localhost:3001' in your URL."
  echo ""
  echo "Or set E2E_AUTH_MODE=keycloak to use the Keycloak dev stack on port 3000."
  exit 2
fi

# If navigating to port 3001, check that the stack is actually running
if echo "$URL" | grep -qE '(localhost|127\.0\.0\.1):3001'; then
  if ! curl -sf --max-time 2 http://localhost:3001/ > /dev/null 2>&1; then
    echo "E2E mock-auth stack is not running on port 3001."
    echo ""
    echo "Start it with:  bash compose/scripts/e2e-up.sh"
    echo ""
    echo "This builds the full stack from current source (~3-5 min first time)."
    exit 2
  fi
fi

exit 0
