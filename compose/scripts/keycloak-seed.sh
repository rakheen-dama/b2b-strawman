#!/usr/bin/env bash
# keycloak-seed.sh — Seed Keycloak with development data.
# Creates an organization, users, and assigns roles.
#
# Prerequisites:
#   - Keycloak running on localhost:8180 with realm "docteams" imported
#   - Admin credentials: admin/admin (default dev setup)
#
# Usage: bash compose/scripts/keycloak-seed.sh
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM="docteams"

# Use kcadm.sh from Docker container
KCADM="docker exec b2b-keycloak /opt/keycloak/bin/kcadm.sh"

echo "=== Keycloak Seed ==="
echo ""

# ---- Wait for Keycloak to be ready ----
echo "[1/5] Waiting for Keycloak..."
MAX_WAIT=120
ELAPSED=0
while [[ $ELAPSED -lt $MAX_WAIT ]]; do
  if curl -sf "${KEYCLOAK_URL}/realms/${REALM}" > /dev/null 2>&1; then
    echo "  Keycloak realm '${REALM}' is ready."
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done
if [[ $ELAPSED -ge $MAX_WAIT ]]; then
  echo "  ERROR: Keycloak not ready after ${MAX_WAIT}s"
  exit 1
fi

# ---- Authenticate admin ----
echo "[2/5] Authenticating admin..."
$KCADM config credentials \
  --server "${KEYCLOAK_URL}" \
  --realm master \
  --user "${KEYCLOAK_ADMIN}" \
  --password "${KEYCLOAK_ADMIN_PASSWORD}"

# ---- Create Organization ----
echo "[3/5] Creating organization 'acme-corp'..."
ORG_ID=$($KCADM create organizations \
  -r "${REALM}" \
  -s name="Acme Corp" \
  -s alias="acme-corp" \
  -s enabled=true \
  -i 2>/dev/null || true)

if [[ -z "$ORG_ID" ]]; then
  # Organization may already exist — look it up
  ORG_ID=$($KCADM get organizations -r "${REALM}" --fields id,alias \
    | python3 -c "import sys,json; orgs=json.load(sys.stdin); print(next((o['id'] for o in orgs if o['alias']=='acme-corp'), ''))" 2>/dev/null || true)
fi

if [[ -z "$ORG_ID" ]]; then
  echo "  ERROR: Could not create or find organization 'acme-corp'"
  exit 1
fi
echo "  Organization ID: ${ORG_ID}"

# ---- Create Users ----
echo "[4/5] Creating users..."

create_user() {
  local username="$1"
  local email="$2"
  local first="$3"
  local last="$4"
  local password="$5"

  local USER_ID
  USER_ID=$($KCADM create users \
    -r "${REALM}" \
    -s username="${username}" \
    -s email="${email}" \
    -s firstName="${first}" \
    -s lastName="${last}" \
    -s enabled=true \
    -s emailVerified=true \
    -i 2>/dev/null || true)

  if [[ -z "$USER_ID" ]]; then
    # User may already exist — look up by username
    USER_ID=$($KCADM get "users?username=${username}&exact=true" -r "${REALM}" \
      | python3 -c "import sys,json; users=json.load(sys.stdin); print(users[0]['id'] if users else '')" 2>/dev/null || true)
  fi

  if [[ -n "$USER_ID" ]]; then
    # Set password
    $KCADM set-password -r "${REALM}" --userid "${USER_ID}" --new-password "${password}" 2>/dev/null || true
    echo "  ${first} ${last} (${email}): ${USER_ID}"
  else
    echo "  WARNING: Could not create or find user ${username}"
  fi

  echo "${USER_ID}"
}

ALICE_ID=$(create_user "alice" "alice@example.com" "Alice" "Owner" "password")
BOB_ID=$(create_user "bob" "bob@example.com" "Bob" "Admin" "password")
CAROL_ID=$(create_user "carol" "carol@example.com" "Carol" "Member" "password")

# ---- Assign Organization Membership & Roles ----
echo "[5/5] Assigning organization membership and roles..."

assign_org_role() {
  local user_id="$1"
  local role="$2"
  local user_name="$3"

  # Add user to organization
  $KCADM create "organizations/${ORG_ID}/members" \
    -r "${REALM}" \
    -s userId="${user_id}" 2>/dev/null || true

  # Grant organization role
  # In Keycloak 26.5, org roles are assigned via:
  #   PUT /admin/realms/{realm}/orgs/{orgId}/members/{userId}/roles/{roleName}
  # or via kcadm:
  $KCADM create "organizations/${ORG_ID}/members/${user_id}/organizations/${ORG_ID}/roles/${role}" \
    -r "${REALM}" 2>/dev/null || true

  echo "  ${user_name}: ${role} in acme-corp"
}

assign_org_role "${ALICE_ID}" "owner" "Alice"
assign_org_role "${BOB_ID}" "admin" "Bob"
assign_org_role "${CAROL_ID}" "member" "Carol"

echo ""
echo "=== Keycloak Seed Complete ==="
echo ""
echo "  Realm:        ${REALM}"
echo "  Organization: acme-corp (${ORG_ID})"
echo "  Users:"
echo "    alice@example.com / password (owner)"
echo "    bob@example.com   / password (admin)"
echo "    carol@example.com / password (member)"
echo ""
echo "  Admin Console: ${KEYCLOAK_URL}/admin/master/console/"
echo "  Account:       ${KEYCLOAK_URL}/realms/${REALM}/account/"
echo ""
