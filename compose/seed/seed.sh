#!/bin/sh
set -eu

BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
MOCK_IDP_URL="${MOCK_IDP_URL:-http://mock-idp:8090}"
API_KEY="${API_KEY:-e2e-test-api-key}"

ORG_ID="org_e2e_test"
ORG_NAME="E2E Test Organization"
ORG_SLUG="e2e-test-org"

# -- Helper ----------------------------------------------------------------
check_status() {
  step="$1"
  status="$2"
  # Accept 2xx and 409 (already exists / idempotent)
  case "$status" in
    2[0-9][0-9]|409) echo "    [ok] ${step} (HTTP ${status})" ;;
    *) echo "    [FAIL] ${step} FAILED (HTTP ${status})"; exit 1 ;;
  esac
}

# -- Wait for backend ------------------------------------------------------
echo "============================================"
echo "  E2E Boot-Seed"
echo "============================================"
./wait-for-backend.sh

# -- Step 1: Provision org -------------------------------------------------
echo ""
echo "==> Step 1: Provision organization"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "${BACKEND_URL}/internal/orgs/provision" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: ${API_KEY}" \
  -d "{
    \"clerkOrgId\": \"${ORG_ID}\",
    \"orgName\": \"${ORG_NAME}\"
  }")
check_status "Provision org" "$STATUS"

# -- Step 2: Sync plan to PRO ---------------------------------------------
echo ""
echo "==> Step 2: Sync plan to PRO"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "${BACKEND_URL}/internal/orgs/plan-sync" \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: ${API_KEY}" \
  -d "{
    \"clerkOrgId\": \"${ORG_ID}\",
    \"planSlug\": \"pro\"
  }")
check_status "Plan sync" "$STATUS"

# -- Step 3: Sync members -------------------------------------------------
echo ""
echo "==> Step 3: Sync members"

sync_member() {
  user_id="$1"
  email="$2"
  name="$3"
  avatar="$4"
  role="$5"

  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${BACKEND_URL}/internal/members/sync" \
    -H "Content-Type: application/json" \
    -H "X-API-KEY: ${API_KEY}" \
    -d "{
      \"clerkOrgId\": \"${ORG_ID}\",
      \"clerkUserId\": \"${user_id}\",
      \"email\": \"${email}\",
      \"name\": \"${name}\",
      \"avatarUrl\": \"${avatar}\",
      \"orgRole\": \"${role}\"
    }")
  check_status "Sync ${name}" "$STATUS"
}

sync_member "user_e2e_alice" "alice@e2e-test.local" "Alice Owner" \
  "https://api.dicebear.com/7.x/initials/svg?seed=AO" "owner"

sync_member "user_e2e_bob" "bob@e2e-test.local" "Bob Admin" \
  "https://api.dicebear.com/7.x/initials/svg?seed=BA" "admin"

sync_member "user_e2e_carol" "carol@e2e-test.local" "Carol Member" \
  "https://api.dicebear.com/7.x/initials/svg?seed=CM" "member"

# -- Step 4: Get Alice's JWT -----------------------------------------------
echo ""
echo "==> Step 4: Get Alice's JWT from mock IDP"
TOKEN_RESPONSE=$(curl -sf -X POST "${MOCK_IDP_URL}/token" \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user_e2e_alice\",
    \"orgId\": \"${ORG_ID}\",
    \"orgSlug\": \"${ORG_SLUG}\",
    \"orgRole\": \"owner\"
  }")

ALICE_JWT=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
if [ -z "$ALICE_JWT" ] || [ "$ALICE_JWT" = "null" ]; then
  echo "    [FAIL] Failed to get Alice's JWT"
  exit 1
fi
echo "    [ok] Got Alice's JWT"

# -- Step 5: Create customer -----------------------------------------------
echo ""
echo "==> Step 5: Create customer"
CUSTOMER_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${BACKEND_URL}/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ALICE_JWT}" \
  -d '{
    "name": "Acme Corp",
    "email": "contact@acme.example.com",
    "phone": "+1-555-0100",
    "notes": "E2E seed customer"
  }')

CUSTOMER_BODY=$(echo "$CUSTOMER_RESPONSE" | sed '$d')
CUSTOMER_STATUS=$(echo "$CUSTOMER_RESPONSE" | tail -1)
check_status "Create customer" "$CUSTOMER_STATUS"

CUSTOMER_ID=$(echo "$CUSTOMER_BODY" | jq -r '.id')
echo "    Customer ID: ${CUSTOMER_ID}"

# -- Step 6: Transition customer to ACTIVE ----------------------------------
echo ""
echo "==> Step 6: Transition customer PROSPECT -> ONBOARDING -> ACTIVE"

# PROSPECT -> ONBOARDING
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "${BACKEND_URL}/api/customers/${CUSTOMER_ID}/transition" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ALICE_JWT}" \
  -d '{"targetStatus": "ONBOARDING", "notes": "E2E seed: start onboarding"}')
check_status "Transition to ONBOARDING" "$STATUS"

# Complete all checklist items (required before ACTIVE transition)
CHECKLISTS=$(curl -sf \
  "${BACKEND_URL}/api/customers/${CUSTOMER_ID}/checklists" \
  -H "Authorization: Bearer ${ALICE_JWT}")

ITEM_IDS=$(echo "$CHECKLISTS" | jq -r '
  [.[] | .items[]? | select(.status != "COMPLETED" and .status != "SKIPPED") | .id] | .[]
' 2>/dev/null)

for ITEM_ID in $ITEM_IDS; do
  S=$(curl -s -o /dev/null -w "%{http_code}" \
    -X PUT "${BACKEND_URL}/api/checklist-items/${ITEM_ID}/complete" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ALICE_JWT}" \
    -d '{"notes":"boot-seed"}')
  case "$S" in
    2[0-9][0-9]) ;;
    *) curl -s -o /dev/null -X PUT "${BACKEND_URL}/api/checklist-items/${ITEM_ID}/skip" \
         -H "Content-Type: application/json" \
         -H "Authorization: Bearer ${ALICE_JWT}" \
         -d '{"reason":"boot-seed"}' ;;
  esac
done
echo "    [ok] Checklists completed"

# Check if auto-transitioned to ACTIVE; if not, transition explicitly
CURRENT_STATUS=$(curl -sf \
  "${BACKEND_URL}/api/customers/${CUSTOMER_ID}" \
  -H "Authorization: Bearer ${ALICE_JWT}" | jq -r '.lifecycleStatus')

if [ "$CURRENT_STATUS" = "ACTIVE" ]; then
  echo "    [ok] Auto-transitioned to ACTIVE"
else
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${BACKEND_URL}/api/customers/${CUSTOMER_ID}/transition" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ALICE_JWT}" \
    -d '{"targetStatus": "ACTIVE", "notes": "E2E seed: activate customer"}')
  check_status "Transition to ACTIVE" "$STATUS"
fi

# -- Step 7: Create project -------------------------------------------------
echo ""
echo "==> Step 7: Create project"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "${BACKEND_URL}/api/projects" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ALICE_JWT}" \
  -d "{
    \"name\": \"Website Redesign\",
    \"description\": \"E2E seed project for testing\",
    \"customerId\": \"${CUSTOMER_ID}\"
  }")
check_status "Create project" "$STATUS"

echo ""
echo "============================================"
echo "  E2E Boot-Seed Complete!"
echo "============================================"
echo ""
echo "  Org:      ${ORG_NAME} (${ORG_ID})"
echo "  Members:  alice (owner), bob (admin), carol (member)"
echo "  Customer: Acme Corp (${CUSTOMER_ID})"
echo "  Project:  Website Redesign"
echo ""
