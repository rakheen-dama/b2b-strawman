#!/usr/bin/env bash
set -euo pipefail

# ──────────────────────────────────────────────────────────────
# smoke-test.sh — Verify all public-facing services are healthy
#
# Usage:
#   ./infra/scripts/smoke-test.sh --env staging
#   ./infra/scripts/smoke-test.sh --env production
# ──────────────────────────────────────────────────────────────

# ── Colors ───────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# ── Argument Parsing ─────────────────────────────────────────
ENV=""

usage() {
  echo "Usage: $0 --env staging|production"
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)
      shift
      ENV="${1:-}"
      shift
      ;;
    *)
      echo "Unknown argument: $1"
      usage
      ;;
  esac
done

if [[ -z "$ENV" ]]; then
  echo "Error: --env is required"
  usage
fi

if [[ "$ENV" != "staging" && "$ENV" != "production" ]]; then
  echo "Error: --env must be 'staging' or 'production', got '$ENV'"
  usage
fi

# ── URL Construction ─────────────────────────────────────────
if [[ "$ENV" == "staging" ]]; then
  PREFIX="staging-"
else
  PREFIX=""
fi

APP_URL="https://${PREFIX}app.heykazi.com/"
GATEWAY_URL="https://${PREFIX}app.heykazi.com/bff/me"
KEYCLOAK_URL="https://${PREFIX}auth.heykazi.com/health/ready"
PORTAL_URL="https://${PREFIX}portal.heykazi.com/"

# ── Checks ───────────────────────────────────────────────────
PASS=0
FAIL=0
RESULTS=()

check_http() {
  local name="$1"
  local url="$2"
  local expected="$3"  # comma-separated list of acceptable HTTP codes

  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "$url" 2>/dev/null) || true
  http_code="${http_code:-000}"

  # Check if the returned code is in the acceptable list
  local ok=false
  IFS=',' read -ra CODES <<< "$expected"
  for code in "${CODES[@]}"; do
    if [[ "$http_code" == "$code" ]]; then
      ok=true
      break
    fi
  done

  if $ok; then
    RESULTS+=("${GREEN}PASS${NC}  ${name}  (HTTP ${http_code})  ${url}")
    PASS=$((PASS + 1))
  else
    RESULTS+=("${RED}FAIL${NC}  ${name}  (HTTP ${http_code}, expected ${expected})  ${url}")
    FAIL=$((FAIL + 1))
  fi
}

echo ""
echo -e "${BOLD}Smoke Test — ${ENV}${NC}"
echo "────────────────────────────────────────────────"

check_http "Frontend " "$APP_URL"      "200"
check_http "Gateway  " "$GATEWAY_URL"  "200,401"
check_http "Keycloak " "$KEYCLOAK_URL" "200"
check_http "Portal   " "$PORTAL_URL"   "200"

# ── Summary ──────────────────────────────────────────────────
echo ""
for result in "${RESULTS[@]}"; do
  echo -e "  $result"
done
echo ""
echo "────────────────────────────────────────────────"

if [[ $FAIL -gt 0 ]]; then
  echo -e "  ${RED}${FAIL} FAILED${NC}, ${GREEN}${PASS} passed${NC}"
  echo ""
  exit 1
else
  echo -e "  ${GREEN}All ${PASS} checks passed${NC}"
  echo ""
  exit 0
fi
