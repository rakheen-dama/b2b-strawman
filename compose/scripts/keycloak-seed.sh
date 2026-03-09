#!/usr/bin/env bash
# keycloak-seed.sh — Seed Keycloak with development data.
# Creates an organization, users, assigns roles, and configures platform-admin group.
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
echo "[1/9] Waiting for Keycloak..."
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
echo "[2/9] Authenticating admin..."
$KCADM config credentials \
  --server "${KEYCLOAK_URL}" \
  --realm master \
  --user "${KEYCLOAK_ADMIN}" \
  --password "${KEYCLOAK_ADMIN_PASSWORD}"

# ---- Create Organization ----
echo "[3/9] Creating organization 'acme-corp'..."
ORG_ID=$($KCADM create organizations \
  -r "${REALM}" \
  -s name="Acme Corp" \
  -s alias="acme-corp" \
  -s enabled=true \
  -i 2>/dev/null || true)

if [[ -z "$ORG_ID" ]]; then
  # Organization may already exist — look it up
  ORG_ID=$($KCADM get organizations -r "${REALM}" --fields id,alias \
    | jq -r '.[] | select(.alias=="acme-corp") | .id' 2>/dev/null || true)
fi

if [[ -z "$ORG_ID" ]]; then
  echo "  ERROR: Could not create or find organization 'acme-corp'"
  exit 1
fi
echo "  Organization ID: ${ORG_ID}"

# ---- Create Users ----
echo "[4/9] Creating users..."

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
      | jq -r '.[0].id // empty' 2>/dev/null || true)
  fi

  if [[ -n "$USER_ID" ]]; then
    # Set password
    $KCADM set-password -r "${REALM}" --userid "${USER_ID}" --new-password "${password}" 2>/dev/null || true
    echo "  ${first} ${last} (${email}): ${USER_ID}" >&2
  else
    echo "  WARNING: Could not create or find user ${username}" >&2
  fi

  echo "${USER_ID}"
}

ALICE_ID=$(create_user "alice" "alice@example.com" "Alice" "Owner" "password")
BOB_ID=$(create_user "bob" "bob@example.com" "Bob" "Admin" "password")
CAROL_ID=$(create_user "carol" "carol@example.com" "Carol" "Member" "password")

# ---- Assign Organization Membership & Roles ----
echo "[5/9] Assigning organization membership and roles..."

assign_org_member() {
  local user_id="$1"
  local role_label="$2"
  local user_name="$3"

  # Add user to organization
  # Note: kcadm requires the userId as the request body (not -s flag)
  $KCADM create "organizations/${ORG_ID}/members" \
    -r "${REALM}" \
    -b "${user_id}" 2>/dev/null || true

  # TODO: Keycloak 26.x does not yet expose organization-level role assignment
  # via the Admin REST API. Organization members are added without roles.
  # Org role management will be addressed when the Keycloak Organizations feature
  # matures (tracked upstream: https://github.com/keycloak/keycloak/issues/30180).
  # For now, the built-in oidc-organization-membership-mapper emits org membership
  # in the JWT. Role-based authorization will use realm roles or a custom SPI.

  echo "  ${user_name}: member of acme-corp (intended role: ${role_label})"
}

assign_org_member "${ALICE_ID}" "owner" "Alice"
assign_org_member "${BOB_ID}" "admin" "Bob"
assign_org_member "${CAROL_ID}" "member" "Carol"

# ---- Create platform-admins group ----
echo "[6/9] Creating 'platform-admins' group..."
GROUP_ID=$($KCADM create groups \
  -r "${REALM}" \
  -s name="platform-admins" \
  -i 2>/dev/null || true)

if [[ -z "$GROUP_ID" ]]; then
  # Group may already exist — look it up
  GROUP_ID=$($KCADM get groups -r "${REALM}" --fields id,name \
    | jq -r '.[] | select(.name=="platform-admins") | .id' 2>/dev/null || true)
fi

if [[ -z "$GROUP_ID" ]]; then
  echo "  ERROR: Could not create or find group 'platform-admins'"
  exit 1
fi
echo "  Group ID: ${GROUP_ID}"

# ---- Add Group Membership Mapper to gateway-bff client ----
echo "[7/9] Adding Group Membership Mapper to gateway-bff client..."

# Find the gateway-bff client ID (Keycloak internal UUID, not the clientId string)
CLIENT_KC_ID=$($KCADM get clients -r "${REALM}" --fields id,clientId \
  | jq -r '.[] | select(.clientId=="gateway-bff") | .id' 2>/dev/null || true)

if [[ -z "$CLIENT_KC_ID" ]]; then
  echo "  ERROR: Could not find client 'gateway-bff'"
  exit 1
fi

# Add protocol mapper directly to the client.
# The mapper adds a "groups" claim (flat group names) to ID, access, and userinfo tokens.
$KCADM create "clients/${CLIENT_KC_ID}/protocol-mappers/models" \
  -r "${REALM}" \
  -s name="groups" \
  -s protocol="openid-connect" \
  -s protocolMapper="oidc-group-membership-mapper" \
  -s 'config."claim.name"=groups' \
  -s 'config."full.path"=false' \
  -s 'config."id.token.claim"=true' \
  -s 'config."access.token.claim"=true' \
  -s 'config."userinfo.token.claim"=true' \
  2>/dev/null || true

# Verify the mapper exists (idempotency check, same pattern as Step 6 group creation)
MAPPER_EXISTS=$($KCADM get "clients/${CLIENT_KC_ID}/protocol-mappers/models" -r "${REALM}" \
  | jq -r '.[] | select(.name=="groups") | .name' 2>/dev/null || true)

if [[ "$MAPPER_EXISTS" == "groups" ]]; then
  echo "  Group Membership Mapper present on gateway-bff client."
else
  echo "  ERROR: Could not create or find 'groups' protocol mapper on gateway-bff client"
  exit 1
fi

# ---- Create platform admin user (separate from tenant users) ----
echo "[8/9] Creating platform admin user..."
PADMIN_ID=$(create_user "padmin" "padmin@docteams.local" "Platform" "Admin" "password")

# ---- Assign platform admin to platform-admins group ----
echo "[9/9] Assigning platform admin to 'platform-admins' group..."
if [[ -z "$PADMIN_ID" ]]; then
  echo "  ERROR: PADMIN_ID not set — skipping group assignment"
else
  $KCADM update "users/${PADMIN_ID}/groups/${GROUP_ID}" \
    -r "${REALM}" \
    -s realm="${REALM}" \
    -s userId="${PADMIN_ID}" \
    -s groupId="${GROUP_ID}" \
    -n 2>/dev/null || true
  echo "  Platform Admin assigned to platform-admins group."
fi

echo ""
echo "=== Keycloak Seed Complete ==="
echo ""
echo "  Realm:        ${REALM}"
echo "  Organization: acme-corp (${ORG_ID})"
echo "  Group:        platform-admins (${GROUP_ID})"
echo "  Users:"
echo "    padmin@docteams.local / password (platform-admin — NOT a tenant member)"
echo "    alice@example.com     / password (owner)"
echo "    bob@example.com       / password (admin)"
echo "    carol@example.com     / password (member)"
echo ""
echo "  Admin Console: ${KEYCLOAK_URL}/admin/master/console/"
echo "  Account:       ${KEYCLOAK_URL}/realms/${REALM}/account/"
echo ""
