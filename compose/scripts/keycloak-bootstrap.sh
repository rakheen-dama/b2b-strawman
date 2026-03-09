#!/usr/bin/env bash
# keycloak-bootstrap.sh — Bootstrap Keycloak after realm import.
# Adds protocol mappers to the gateway-bff client, creates the platform admin user,
# and backfills org_role attributes on existing organization members.
# The platform-admins group is already created by the realm-export.json import.
#
# Does NOT create organizations or tenant users — those go through the product's provisioning flow.
#
# Prerequisites:
#   - Keycloak running with realm "docteams" imported (from realm-export.json)
#   - Admin credentials: admin/admin (default dev setup)
#
# Usage: bash compose/scripts/keycloak-bootstrap.sh
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM="docteams"

# Use kcadm.sh from Docker container
KCADM="docker exec b2b-keycloak /opt/keycloak/bin/kcadm.sh"

echo "=== Keycloak Bootstrap ==="
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

# ---- Register org_role in User Profile ----
echo "[3/6] Registering org_role in user profile..."

# Keycloak 26.x uses strict user profile — unregistered attributes are silently stripped.
# We must declare org_role before it can be set on any user.
TOKEN=$(curl -sf "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN}&password=${KEYCLOAK_ADMIN_PASSWORD}" \
  | jq -r '.access_token')

PROFILE=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/users/profile" \
  -H "Authorization: Bearer ${TOKEN}")

# Add org_role attribute if not already present
HAS_ORG_ROLE=$(echo "$PROFILE" | jq '[.attributes[] | select(.name=="org_role")] | length')
if [[ "$HAS_ORG_ROLE" -eq 0 ]]; then
  UPDATED=$(echo "$PROFILE" | jq '.attributes += [{
    "name": "org_role",
    "displayName": "Organization Role",
    "permissions": {"view": ["admin"], "edit": ["admin"]},
    "multivalued": false
  }]')
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users/profile" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$UPDATED")
  [[ "$HTTP_CODE" == "200" ]] && echo "  org_role attribute registered" || echo "  ERROR: failed to register org_role (HTTP ${HTTP_CODE})"
else
  echo "  org_role attribute already registered"
fi

# ---- Add Protocol Mappers to gateway-bff client ----
echo "[4/6] Configuring protocol mappers on gateway-bff client..."

CLIENT_KC_ID=$($KCADM get clients -r "${REALM}" --fields id,clientId \
  | jq -r '.[] | select(.clientId=="gateway-bff") | .id' 2>/dev/null || true)

if [[ -z "$CLIENT_KC_ID" ]]; then
  echo "  ERROR: Could not find client 'gateway-bff'"
  exit 1
fi

# Groups mapper: adds "groups" claim (flat group names) to tokens
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

# Org-role mapper: maps user attribute "org_role" to JWT claim "org_role".
# Workaround for KC 26.x not including org roles in the built-in organization claim.
# The org_role attribute is set by KeycloakAdminClient.updateMemberRole() during provisioning.
$KCADM create "clients/${CLIENT_KC_ID}/protocol-mappers/models" \
  -r "${REALM}" \
  -s name="org-role" \
  -s protocol="openid-connect" \
  -s protocolMapper="oidc-usermodel-attribute-mapper" \
  -s 'config."user.attribute"=org_role' \
  -s 'config."claim.name"=org_role' \
  -s 'config."id.token.claim"=true' \
  -s 'config."access.token.claim"=true' \
  -s 'config."userinfo.token.claim"=true' \
  -s 'config."jsonType.label"=String' \
  2>/dev/null || true

# Verify mappers
GROUPS_OK=$($KCADM get "clients/${CLIENT_KC_ID}/protocol-mappers/models" -r "${REALM}" \
  | jq -r '.[] | select(.name=="groups") | .name' 2>/dev/null || true)
ORG_ROLE_OK=$($KCADM get "clients/${CLIENT_KC_ID}/protocol-mappers/models" -r "${REALM}" \
  | jq -r '.[] | select(.name=="org-role") | .name' 2>/dev/null || true)

[[ "$GROUPS_OK" == "groups" ]] && echo "  groups mapper OK" || echo "  ERROR: groups mapper MISSING"
[[ "$ORG_ROLE_OK" == "org-role" ]] && echo "  org-role mapper OK" || echo "  ERROR: org-role mapper MISSING"

# ---- Create platform admin user and assign to existing platform-admins group ----
echo "[5/6] Creating platform admin user..."

# Look up the platform-admins group (created by realm-export.json)
GROUP_ID=$($KCADM get groups -r "${REALM}" --fields id,name \
  | jq -r '.[] | select(.name=="platform-admins") | .id' 2>/dev/null || true)

if [[ -z "$GROUP_ID" ]]; then
  echo "  ERROR: platform-admins group not found — check realm-export.json"
  exit 1
fi

PADMIN_EMAIL="padmin@docteams.local"

# Check if user already exists by email (realm has registrationEmailAsUsername=true,
# so username is set to the email, not a short alias)
PADMIN_ID=$($KCADM get "users?email=${PADMIN_EMAIL}&exact=true" -r "${REALM}" \
  | jq -r '.[0].id // empty' 2>/dev/null || true)

if [[ -z "$PADMIN_ID" ]]; then
  PADMIN_ID=$($KCADM create users \
    -r "${REALM}" \
    -s username="${PADMIN_EMAIL}" \
    -s email="${PADMIN_EMAIL}" \
    -s firstName="Platform" \
    -s lastName="Admin" \
    -s enabled=true \
    -s emailVerified=true \
    -i 2>/dev/null || true)
fi

if [[ -z "$PADMIN_ID" ]]; then
  echo "  ERROR: Could not create or find platform admin user"
  exit 1
fi

$KCADM set-password -r "${REALM}" --userid "${PADMIN_ID}" --new-password "password" 2>/dev/null || true

$KCADM update "users/${PADMIN_ID}/groups/${GROUP_ID}" \
  -r "${REALM}" \
  -s realm="${REALM}" \
  -s userId="${PADMIN_ID}" \
  -s groupId="${GROUP_ID}" \
  -n 2>/dev/null || true

echo "  ${PADMIN_EMAIL} / password -> platform-admins group"

# ---- Backfill org_role attribute on existing org members ----
echo "[6/6] Backfilling org_role for existing organization members..."

# Iterate all organizations, find the creator (stored as org attribute), set org_role=owner.
# All other org members without org_role get member as default.
ORGS_JSON=$($KCADM get organizations -r "${REALM}" 2>/dev/null || echo "[]")
ORG_COUNT=$(echo "$ORGS_JSON" | jq 'length' 2>/dev/null || echo "0")

# Re-fetch token (may have expired during earlier steps)
TOKEN=$(curl -sf "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN}&password=${KEYCLOAK_ADMIN_PASSWORD}" \
  | jq -r '.access_token')

if [[ "$ORG_COUNT" -eq 0 ]]; then
  echo "  No organizations found — nothing to backfill."
else
  for i in $(seq 0 $((ORG_COUNT - 1))); do
    ORG_ID=$(echo "$ORGS_JSON" | jq -r ".[$i].id")
    ORG_ALIAS=$(echo "$ORGS_JSON" | jq -r ".[$i].alias")

    # Get the org's creatorUserId attribute
    ORG_DETAIL=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations/${ORG_ID}" \
      -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || echo "{}")
    CREATOR_ID=$(echo "$ORG_DETAIL" | jq -r '.attributes.creatorUserId[0] // empty' 2>/dev/null || true)

    # List org members
    MEMBERS_JSON=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations/${ORG_ID}/members" \
      -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || echo "[]")
    MEMBER_COUNT=$(echo "$MEMBERS_JSON" | jq 'length' 2>/dev/null || echo "0")

    for j in $(seq 0 $((MEMBER_COUNT - 1))); do
      MEMBER_ID=$(echo "$MEMBERS_JSON" | jq -r ".[$j].id")
      MEMBER_EMAIL=$(echo "$MEMBERS_JSON" | jq -r ".[$j].email // .[$j].username")

      # Check if org_role is already set (use REST API — kcadm silently fails for custom attributes)
      EXISTING_ROLE=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${MEMBER_ID}" \
        -H "Authorization: Bearer ${TOKEN}" \
        | jq -r '.attributes.org_role[0] // empty' 2>/dev/null || true)

      if [[ -n "$EXISTING_ROLE" ]]; then
        echo "  ${MEMBER_EMAIL} (${ORG_ALIAS}): org_role=${EXISTING_ROLE} (already set)"
        continue
      fi

      # Determine role: creator gets owner, sole member gets owner, others get member
      if [[ "$MEMBER_ID" == "$CREATOR_ID" ]]; then
        ROLE="owner"
      elif [[ -z "$CREATOR_ID" && "$MEMBER_COUNT" -eq 1 ]]; then
        # No creator attribute and only one member — they're the org creator
        ROLE="owner"
      else
        ROLE="member"
      fi

      # Fetch full user, merge org_role attribute, PUT back (KC strict profile blanks omitted fields)
      USER_JSON=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${MEMBER_ID}" \
        -H "Authorization: Bearer ${TOKEN}")
      UPDATED_JSON=$(echo "$USER_JSON" | jq --arg role "$ROLE" '.attributes.org_role = [$role]')
      curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${MEMBER_ID}" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "$UPDATED_JSON" 2>/dev/null || true
      echo "  ${MEMBER_EMAIL} (${ORG_ALIAS}): org_role=${ROLE} (backfilled)"
    done
  done
fi

echo ""
echo "=== Keycloak Bootstrap Complete ==="
echo ""
echo "  Mappers: groups, org-role (on gateway-bff)"
echo "  User:    padmin@docteams.local / password"
echo ""
echo "  Users must log out and back in to get the updated org_role claim in their JWT."
echo ""
