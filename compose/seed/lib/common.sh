#!/bin/sh
# compose/seed/lib/common.sh — Shared helpers for seed scripts
# Source this file; do not execute directly.

# ── Environment detection ───────────────────────────────────────────
if curl -sf http://backend:8080/actuator/health > /dev/null 2>&1; then
  BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
  MOCK_IDP_URL="${MOCK_IDP_URL:-http://mock-idp:8090}"
else
  BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
  MOCK_IDP_URL="${MOCK_IDP_URL:-http://localhost:8090}"
fi

API_KEY="${API_KEY:-e2e-test-api-key}"
ORG_ID="org_e2e_test"
ORG_SLUG="e2e-test-org"

# Temp file for API responses (cleaned up on exit)
_API_TMPFILE=$(mktemp)
trap 'rm -f "$_API_TMPFILE" "$_API_TMPFILE.body"' EXIT

# ── JWT cache ───────────────────────────────────────────────────────
_ALICE_JWT=""
_BOB_JWT=""
_CAROL_JWT=""

get_jwt() {
  user_id="$1"
  org_role="$2"

  case "$user_id" in
    user_e2e_alice) [ -n "$_ALICE_JWT" ] && echo "$_ALICE_JWT" && return ;;
    user_e2e_bob)   [ -n "$_BOB_JWT" ]   && echo "$_BOB_JWT"   && return ;;
    user_e2e_carol) [ -n "$_CAROL_JWT" ] && echo "$_CAROL_JWT" && return ;;
  esac

  token=$(curl -sf -X POST "${MOCK_IDP_URL}/token" \
    -H "Content-Type: application/json" \
    -d "{\"userId\":\"${user_id}\",\"orgId\":\"${ORG_ID}\",\"orgSlug\":\"${ORG_SLUG}\",\"orgRole\":\"${org_role}\"}" \
    | jq -r '.access_token')

  if [ -z "$token" ] || [ "$token" = "null" ]; then
    echo "FATAL: Failed to get JWT for ${user_id}" >&2
    exit 1
  fi

  case "$user_id" in
    user_e2e_alice) _ALICE_JWT="$token" ;;
    user_e2e_bob)   _BOB_JWT="$token" ;;
    user_e2e_carol) _CAROL_JWT="$token" ;;
  esac

  echo "$token"
}

# ── API helpers ─────────────────────────────────────────────────────
# Write HTTP status to $_API_TMPFILE, body to stdout.
# Read status after call with: last_status

api_post() {
  _p="$1"; _b="$2"; _j="${3:-}"
  if [ -n "$_j" ]; then
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X POST "${BACKEND_URL}${_p}" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${_j}" \
      -d "$_b" > "$_API_TMPFILE"
  else
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X POST "${BACKEND_URL}${_p}" \
      -H "Content-Type: application/json" \
      -d "$_b" > "$_API_TMPFILE"
  fi
  cat "$_API_TMPFILE.body"
}

api_get() {
  _p="$1"; _j="${2:-}"
  if [ -n "$_j" ]; then
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X GET "${BACKEND_URL}${_p}" \
      -H "Authorization: Bearer ${_j}" > "$_API_TMPFILE"
  else
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X GET "${BACKEND_URL}${_p}" > "$_API_TMPFILE"
  fi
  cat "$_API_TMPFILE.body"
}

api_put() {
  _p="$1"; _b="$2"; _j="${3:-}"
  if [ -n "$_j" ]; then
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X PUT "${BACKEND_URL}${_p}" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${_j}" \
      -d "$_b" > "$_API_TMPFILE"
  else
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X PUT "${BACKEND_URL}${_p}" \
      -H "Content-Type: application/json" \
      -d "$_b" > "$_API_TMPFILE"
  fi
  cat "$_API_TMPFILE.body"
}

last_status() {
  cat "$_API_TMPFILE"
}

# ── Status check (always to stderr so $() capture isn't polluted) ───

check_status() {
  step="$1"
  status="${2:-$(last_status)}"
  case "$status" in
    2[0-9][0-9]|409) echo "    [ok] ${step} (HTTP ${status})" >&2 ;;
    *) echo "    [FAIL] ${step} (HTTP ${status})" >&2; return 1 ;;
  esac
}

# ── Idempotency helper ──────────────────────────────────────────────

# find_existing <list_path> <jq_filter> [jwt]
# Returns the ID if found, empty string if not.
find_existing() {
  _fe_path="$1"
  _fe_filter="$2"
  _fe_jwt="${3:-}"

  api_get "${_fe_path}?size=200" "$_fe_jwt" | jq -r "$_fe_filter | .id" 2>/dev/null
}

# complete_checklists <customer_id> <jwt>
# Completes all checklist items for a customer to allow ONBOARDING -> ACTIVE transition.
complete_checklists() {
  _cc_customer_id="$1"
  _cc_jwt="$2"

  checklists=$(api_get "/api/customers/${_cc_customer_id}/checklists" "$_cc_jwt")

  item_ids=$(echo "$checklists" | jq -r '
    [.[] | .items[]? | select(.status != "COMPLETED" and .status != "SKIPPED") | .id] | .[]
  ' 2>/dev/null)

  for item_id in $item_ids; do
    api_put "/api/checklist-items/${item_id}/complete" '{"notes":"seed"}' "$_cc_jwt" > /dev/null
    _s=$(last_status)
    case "$_s" in
      2[0-9][0-9]) ;;
      *) api_put "/api/checklist-items/${item_id}/skip" '{"reason":"seed"}' "$_cc_jwt" > /dev/null ;;
    esac
  done
  echo "    [ok] Checklists completed for customer ${_cc_customer_id}" >&2
}
