#!/usr/bin/env bash
# keycloak-seed.sh — Create test org and users in Keycloak for local dev.
# Requires: Keycloak running at localhost:9090 with docteams realm.
# Idempotent — safe to run multiple times.
#
# Usage: bash compose/scripts/keycloak-seed.sh
set -euo pipefail

KC_URL="${KEYCLOAK_URL:-http://localhost:9090}"
KC_REALM="docteams"
KC_ADMIN_USER="admin"
KC_ADMIN_PASS="admin"

echo "=== Keycloak Dev Seed ==="
echo ""

# 1. Get admin token from master realm
echo "[1/5] Getting admin token..."
ADMIN_TOKEN=$(curl -sf -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=${KC_ADMIN_USER}" \
  -d "password=${KC_ADMIN_PASS}" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

if [[ -z "$ADMIN_TOKEN" ]]; then
  echo "ERROR: Failed to get admin token"
  exit 1
fi
echo "  Got admin token"

AUTH_HEADER="Authorization: Bearer ${ADMIN_TOKEN}"

# 2. Create test organization
echo "[2/5] Creating test organization..."
ORG_PAYLOAD='{"name": "Dev Test Org", "alias": "dev-test-org", "enabled": true}'
ORG_RESPONSE=$(curl -sf -w "\n%{http_code}" -X POST "${KC_URL}/admin/realms/${KC_REALM}/organizations" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d "${ORG_PAYLOAD}" 2>/dev/null || true)

ORG_HTTP_CODE=$(echo "$ORG_RESPONSE" | tail -1)
if [[ "$ORG_HTTP_CODE" == "201" ]]; then
  echo "  Created organization: dev-test-org"
elif [[ "$ORG_HTTP_CODE" == "409" ]]; then
  echo "  Organization already exists (skipping)"
else
  echo "  WARNING: Unexpected response ($ORG_HTTP_CODE), continuing..."
fi

# Get org ID
ORG_ID=$(curl -sf "${KC_URL}/admin/realms/${KC_REALM}/organizations" \
  -H "${AUTH_HEADER}" | python3 -c "
import sys, json
orgs = json.load(sys.stdin)
for org in orgs:
    if org.get('alias') == 'dev-test-org':
        print(org['id'])
        break
")

if [[ -z "$ORG_ID" ]]; then
  echo "ERROR: Could not find org ID for dev-test-org"
  exit 1
fi
echo "  Org ID: ${ORG_ID}"

# 2.5. Enable unmanaged user attributes for org role attributes
# Keycloak 26+ declarative user profile silently drops attributes not in the profile config.
# We need ADMIN_EDIT to allow the seed script to set org:<id>:role user attributes.
echo "  Enabling unmanaged attribute policy..."
PROFILE_JSON=$(curl -sf "${KC_URL}/admin/realms/${KC_REALM}/users/profile" \
  -H "${AUTH_HEADER}" 2>/dev/null || echo "")
if [[ -n "$PROFILE_JSON" ]]; then
  UPDATED_PROFILE=$(echo "$PROFILE_JSON" | python3 -c "
import sys, json
profile = json.load(sys.stdin)
profile['unmanagedAttributePolicy'] = 'ADMIN_EDIT'
json.dump(profile, sys.stdout)
")
  curl -sf -X PUT "${KC_URL}/admin/realms/${KC_REALM}/users/profile" \
    -H "${AUTH_HEADER}" \
    -H "Content-Type: application/json" \
    -d "${UPDATED_PROFILE}" > /dev/null 2>&1 || true
  echo "  User profile updated (ADMIN_EDIT)"
fi

# 3. Create test users
echo "[3/5] Creating test users..."

create_user() {
  local username="$1"
  local email="$2"
  local first="$3"
  local last="$4"
  local password="$5"
  local role="$6"

  USER_PAYLOAD=$(cat <<USERJSON
{
  "username": "${username}",
  "email": "${email}",
  "firstName": "${first}",
  "lastName": "${last}",
  "enabled": true,
  "emailVerified": true,
  "credentials": [{"type": "password", "value": "${password}", "temporary": false}],
  "attributes": {
    "org:${ORG_ID}:role": ["${role}"]
  }
}
USERJSON
)

  USER_RESPONSE=$(curl -sf -w "\n%{http_code}" -X POST "${KC_URL}/admin/realms/${KC_REALM}/users" \
    -H "${AUTH_HEADER}" \
    -H "Content-Type: application/json" \
    -d "${USER_PAYLOAD}" 2>/dev/null || true)

  USER_HTTP_CODE=$(echo "$USER_RESPONSE" | tail -1)
  if [[ "$USER_HTTP_CODE" == "201" ]]; then
    echo "  Created user: ${username} (${role})"
  elif [[ "$USER_HTTP_CODE" == "409" ]]; then
    echo "  User ${username} already exists (skipping creation)"
  else
    echo "  WARNING: Unexpected response for ${username} ($USER_HTTP_CODE)"
  fi

  # Get user ID
  USER_ID=$(curl -sf "${KC_URL}/admin/realms/${KC_REALM}/users?username=${username}&exact=true" \
    -H "${AUTH_HEADER}" | python3 -c "import sys,json; users=json.load(sys.stdin); print(users[0]['id'] if users else '')")

  if [[ -n "$USER_ID" ]]; then
    # Update user with full representation (email, name, attributes, password).
    # PUT is a full replace in Keycloak Admin API, so we must include all fields.
    curl -sf -X PUT "${KC_URL}/admin/realms/${KC_REALM}/users/${USER_ID}" \
      -H "${AUTH_HEADER}" \
      -H "Content-Type: application/json" \
      -d "{
        \"email\": \"${email}\",
        \"firstName\": \"${first}\",
        \"lastName\": \"${last}\",
        \"emailVerified\": true,
        \"enabled\": true,
        \"credentials\": [{\"type\": \"password\", \"value\": \"${password}\", \"temporary\": false}],
        \"attributes\": {\"org:${ORG_ID}:role\": [\"${role}\"]}
      }" > /dev/null 2>&1 || true

    # Add user to organization
    curl -sf -w "%{http_code}" -X POST "${KC_URL}/admin/realms/${KC_REALM}/organizations/${ORG_ID}/members" \
      -H "${AUTH_HEADER}" \
      -H "Content-Type: application/json" \
      -d "\"${USER_ID}\"" > /dev/null 2>&1 || true
    echo "  Added ${username} to org as ${role}"
  fi
}

create_user "alice" "alice@docteams.local" "Alice" "Owner" "password" "owner"
create_user "bob" "bob@docteams.local" "Bob" "Admin" "password" "admin"
create_user "carol" "carol@docteams.local" "Carol" "Member" "password" "member"

# 4. Grant service account realm-management roles
echo "[4/5] Granting service account roles..."

# Get service account user for docteams-admin client
ADMIN_CLIENT_ID=$(curl -sf "${KC_URL}/admin/realms/${KC_REALM}/clients?clientId=docteams-admin" \
  -H "${AUTH_HEADER}" | python3 -c "import sys,json; clients=json.load(sys.stdin); print(clients[0]['id'] if clients else '')")

if [[ -n "$ADMIN_CLIENT_ID" ]]; then
  SA_USER_ID=$(curl -sf "${KC_URL}/admin/realms/${KC_REALM}/clients/${ADMIN_CLIENT_ID}/service-account-user" \
    -H "${AUTH_HEADER}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))")

  if [[ -n "$SA_USER_ID" ]]; then
    # Get realm-management client ID
    RM_CLIENT_ID=$(curl -sf "${KC_URL}/admin/realms/${KC_REALM}/clients?clientId=realm-management" \
      -H "${AUTH_HEADER}" | python3 -c "import sys,json; clients=json.load(sys.stdin); print(clients[0]['id'] if clients else '')")

    if [[ -n "$RM_CLIENT_ID" ]]; then
      # Get realm-admin role
      REALM_ADMIN_ROLE=$(curl -sf "${KC_URL}/admin/realms/${KC_REALM}/clients/${RM_CLIENT_ID}/roles/realm-admin" \
        -H "${AUTH_HEADER}" 2>/dev/null || echo "")

      if [[ -n "$REALM_ADMIN_ROLE" ]]; then
        curl -sf -X POST "${KC_URL}/admin/realms/${KC_REALM}/users/${SA_USER_ID}/role-mappings/clients/${RM_CLIENT_ID}" \
          -H "${AUTH_HEADER}" \
          -H "Content-Type: application/json" \
          -d "[${REALM_ADMIN_ROLE}]" > /dev/null 2>&1 || true
        echo "  Granted realm-admin role to docteams-admin service account"
      fi
    fi
  fi
fi

# 5. Summary
echo ""
echo "[5/5] Done!"
echo ""
echo "=== Keycloak Dev Seed Complete ==="
echo ""
echo "  Realm:         ${KC_REALM}"
echo "  Organization:  Dev Test Org (dev-test-org)"
echo "  Org ID:        ${ORG_ID}"
echo ""
echo "  Users (password: 'password' for all):"
echo "    alice@docteams.local  — owner"
echo "    bob@docteams.local    — admin"
echo "    carol@docteams.local  — member"
echo ""
echo "  Admin Console: ${KC_URL}/admin"
echo "  Login:         admin / admin"
echo ""
