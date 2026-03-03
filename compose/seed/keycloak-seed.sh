#!/bin/sh
# keycloak-seed.sh — Create org + users in Keycloak via Admin API.
# Called from seed.sh when KEYCLOAK_URL is set.
set -eu

KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:8080}"
KC_ADMIN_CLIENT_SECRET="${KEYCLOAK_ADMIN_CLIENT_SECRET:-docteams-admin-secret}"
REALM="docteams"

ORG_NAME="$1"
ORG_SLUG="$2"

# -- Wait for Keycloak token endpoint ------------------------------------
echo "    Waiting for Keycloak token endpoint..."
KC_ELAPSED=0
KC_TIMEOUT=120
while [ "$KC_ELAPSED" -lt "$KC_TIMEOUT" ]; do
  if curl -sf "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
    -d "grant_type=client_credentials" \
    -d "client_id=docteams-admin" \
    -d "client_secret=${KC_ADMIN_CLIENT_SECRET}" > /dev/null 2>&1; then
    echo "    Keycloak token endpoint ready (${KC_ELAPSED}s)"
    break
  fi
  sleep 3
  KC_ELAPSED=$((KC_ELAPSED + 3))
done
if [ "$KC_ELAPSED" -ge "$KC_TIMEOUT" ]; then
  echo "    [FAIL] Keycloak token endpoint not available after ${KC_TIMEOUT}s"
  exit 1
fi

# -- Get admin access token ------------------------------------------------
get_admin_token() {
  TOKEN_RESP=$(curl -sf -X POST \
    "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
    -d "grant_type=client_credentials" \
    -d "client_id=docteams-admin" \
    -d "client_secret=${KC_ADMIN_CLIENT_SECRET}")
  echo "$TOKEN_RESP" | jq -r '.access_token'
}

ADMIN_TOKEN=$(get_admin_token)
if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "    [FAIL] Failed to get Keycloak admin token"
  exit 1
fi
echo "    [ok] Got Keycloak admin token"

# -- Helper: create user ---------------------------------------------------
create_kc_user() {
  email="$1"
  first_name="$2"
  last_name="$3"
  password="$4"

  # Check if user already exists
  EXISTING=$(curl -sf -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users?email=${email}&exact=true")
  EXISTING_COUNT=$(echo "$EXISTING" | jq 'length')

  if [ "$EXISTING_COUNT" -gt "0" ]; then
    USER_ID=$(echo "$EXISTING" | jq -r '.[0].id')
    echo "    [ok] User ${email} already exists (${USER_ID})"
    echo "$USER_ID"
    return
  fi

  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${email}\",
      \"email\": \"${email}\",
      \"firstName\": \"${first_name}\",
      \"lastName\": \"${last_name}\",
      \"enabled\": true,
      \"emailVerified\": true,
      \"credentials\": [{
        \"type\": \"password\",
        \"value\": \"${password}\",
        \"temporary\": false
      }]
    }")

  case "$STATUS" in
    2[0-9][0-9]) ;;
    409) ;;
    *) echo "    [FAIL] Create user ${email} failed (HTTP ${STATUS})"; exit 1 ;;
  esac

  # Fetch the user ID
  CREATED=$(curl -sf -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users?email=${email}&exact=true")
  USER_ID=$(echo "$CREATED" | jq -r '.[0].id')
  echo "    [ok] Created user ${email} (${USER_ID})"
  echo "$USER_ID"
}

# -- Create test users ------------------------------------------------------
echo ""
echo "    Creating Keycloak users..."
ALICE_KC_ID=$(create_kc_user "alice@e2e-test.local" "Alice" "Owner" "alice-e2e-pass" 2>&1 | tail -1)
BOB_KC_ID=$(create_kc_user "bob@e2e-test.local" "Bob" "Admin" "bob-e2e-pass" 2>&1 | tail -1)
CAROL_KC_ID=$(create_kc_user "carol@e2e-test.local" "Carol" "Member" "carol-e2e-pass" 2>&1 | tail -1)

# Print progress lines (the create_kc_user output goes to the ID variables above)
echo "    Alice KC ID: ${ALICE_KC_ID}"
echo "    Bob KC ID: ${BOB_KC_ID}"
echo "    Carol KC ID: ${CAROL_KC_ID}"

# -- Create organization ---------------------------------------------------
echo ""
echo "    Creating Keycloak organization..."

# Refresh token (may have expired during user creation)
ADMIN_TOKEN=$(get_admin_token)

# Check if org already exists
EXISTING_ORGS=$(curl -sf -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations?search=${ORG_SLUG}")
EXISTING_ORG_COUNT=$(echo "$EXISTING_ORGS" | jq 'length')

if [ "$EXISTING_ORG_COUNT" -gt "0" ]; then
  KC_ORG_ID=$(echo "$EXISTING_ORGS" | jq -r '.[0].id')
  echo "    [ok] Organization already exists (${KC_ORG_ID})"
else
  ORG_CREATE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"${ORG_NAME}\",
      \"alias\": \"${ORG_SLUG}\",
      \"enabled\": true,
      \"domains\": [{
        \"name\": \"e2e-test.local\",
        \"verified\": true
      }]
    }")

  case "$ORG_CREATE_STATUS" in
    2[0-9][0-9]) ;;
    409) ;;
    *) echo "    [FAIL] Create org failed (HTTP ${ORG_CREATE_STATUS})"; exit 1 ;;
  esac

  # Fetch the org ID
  ORGS=$(curl -sf -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations?search=${ORG_SLUG}")
  KC_ORG_ID=$(echo "$ORGS" | jq -r '.[0].id')
  echo "    [ok] Created organization (${KC_ORG_ID})"
fi

# -- Add users as org members -----------------------------------------------
echo ""
echo "    Adding users to organization..."

add_org_member() {
  user_kc_id="$1"
  user_name="$2"

  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations/${KC_ORG_ID}/members" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "\"${user_kc_id}\"")

  case "$STATUS" in
    2[0-9][0-9]|409) echo "    [ok] Added ${user_name} to org (HTTP ${STATUS})" ;;
    *) echo "    [FAIL] Add ${user_name} to org failed (HTTP ${STATUS})"; exit 1 ;;
  esac
}

add_org_member "$ALICE_KC_ID" "Alice"
add_org_member "$BOB_KC_ID" "Bob"
add_org_member "$CAROL_KC_ID" "Carol"

echo ""
echo "    [ok] Keycloak seeding complete"
