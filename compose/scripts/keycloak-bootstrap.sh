#!/usr/bin/env bash
# keycloak-bootstrap.sh — Bootstrap Keycloak after realm import.
# Adds protocol mappers to the gateway-bff client, creates the platform admin user,
# backfills org_role attributes on existing organization members, and configures the MCP OAuth
# dynamic-client-registration (DCR) policies so the firm's Claude can connect to the MCP server.
# The platform-admins group is already created by the realm-export.json import.
#
# REQUIRED after every fresh Keycloak import (local dev AND the VPS deploy) — the MCP DCR config
# below cannot live in realm-export.json (Keycloak re-adds the default service_account scope and
# default registration policies on import), so it must be applied here post-import.
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

# Use kcadm.sh from the Keycloak Docker container (override KC_CONTAINER on the VPS if the
# container is named differently, e.g. a compose-generated name).
KC_CONTAINER="${KC_CONTAINER:-b2b-keycloak}"
KCADM="docker exec ${KC_CONTAINER} /opt/keycloak/bin/kcadm.sh"

# kcadm runs INSIDE the Keycloak container, so it talks to Keycloak over the
# container's own loopback — never the public URL. This decouples it from
# KEYCLOAK_URL (which the host-side curl calls use). On the VPS, Keycloak has no
# published host port, so KEYCLOAK_URL is the public Caddy host while KCADM_SERVER
# stays in-container; on local dev both default to localhost:8180.
KCADM_SERVER="${KCADM_SERVER:-http://localhost:8180}"

# Dev-credential seeding (step [5/9] create padmin/password + step [7/9] reset
# every org member's password to "password") is LOCAL-DEV convenience only. It is
# DANGEROUS on a public deploy: a weak backdoor admin and a mass password reset of
# real users. Set SEED_DEV_CREDENTIALS=false on the VPS. Default true = dev unchanged.
SEED_DEV_CREDENTIALS="${SEED_DEV_CREDENTIALS:-true}"

echo "=== Keycloak Bootstrap ==="
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
  --server "${KCADM_SERVER}" \
  --realm master \
  --user "${KEYCLOAK_ADMIN}" \
  --password "${KEYCLOAK_ADMIN_PASSWORD}"

# ---- Register org_role in User Profile ----
echo "[3/9] Registering org_role in user profile..."

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
echo "[4/9] Configuring protocol mappers on gateway-bff client..."

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
echo "[5/9] Creating platform admin user..."

if [[ "${SEED_DEV_CREDENTIALS}" != "true" ]]; then
  echo "  [skipped — SEED_DEV_CREDENTIALS=false; create the platform admin via the Keycloak admin console]"
else
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
fi

# ---- Backfill org_role attribute on existing org members (safe for prod) ----
echo "[6/9] Backfilling org_role for existing organization members..."

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

# ---- Backfill passwords for existing org members (LOCAL DEV ONLY) ----
echo "[7/9] Backfilling passwords for existing organization members..."

if [[ "${SEED_DEV_CREDENTIALS}" != "true" ]]; then
  echo "  [skipped — SEED_DEV_CREDENTIALS=false; will NOT reset real users' passwords]"
else

# Re-fetch token (may have expired during org_role backfill)
TOKEN=$(curl -sf "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN}&password=${KEYCLOAK_ADMIN_PASSWORD}" \
  | jq -r '.access_token')

DEFAULT_PASSWORD="password"

if [[ "$ORG_COUNT" -eq 0 ]]; then
  echo "  No organizations found — nothing to backfill."
else
  for i in $(seq 0 $((ORG_COUNT - 1))); do
    ORG_ID=$(echo "$ORGS_JSON" | jq -r ".[$i].id")
    ORG_ALIAS=$(echo "$ORGS_JSON" | jq -r ".[$i].alias")

    # List org members
    MEMBERS_JSON=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations/${ORG_ID}/members" \
      -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || echo "[]")
    MEMBER_COUNT=$(echo "$MEMBERS_JSON" | jq 'length' 2>/dev/null || echo "0")

    for j in $(seq 0 $((MEMBER_COUNT - 1))); do
      MEMBER_ID=$(echo "$MEMBERS_JSON" | jq -r ".[$j].id")
      MEMBER_EMAIL=$(echo "$MEMBERS_JSON" | jq -r ".[$j].email // .[$j].username")

      # Set password via REST API (idempotent — overwrites any existing password)
      HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
        "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${MEMBER_ID}/reset-password" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "{\"type\":\"password\",\"value\":\"${DEFAULT_PASSWORD}\",\"temporary\":false}")
      if [[ "$HTTP_CODE" == "204" ]]; then
        echo "  ${MEMBER_EMAIL} (${ORG_ALIAS}): password set to '${DEFAULT_PASSWORD}'"
      else
        echo "  ${MEMBER_EMAIL} (${ORG_ALIAS}): WARN failed to set password (HTTP ${HTTP_CODE})"
      fi
    done
  done
fi

fi

# ---- Configure MCP OAuth dynamic client registration (DCR) ----
echo "[8/9] Configuring MCP OAuth dynamic-client-registration policies..."

# Claude (and other MCP clients) self-register via OAuth Dynamic Client Registration. Keycloak's
# default anonymous client-registration policies block this:
#   - Trusted Hosts: empty allowlist + source-host match  -> "Host not trusted"
#   - Allowed Client Scopes: only default scopes permitted -> optional scopes rejected
#   - the built-in `service_account` scope appears in scopes_supported but is invalid at the
#     authorization endpoint -> "invalid_scope: ... service_account ..."
# Enable DCR but keep it CONSTRAINED: trust only localhost + claude.ai redirect hosts, allow the
# OIDC optional scopes Claude needs (incl. offline_access), and drop the service_account scope.
# Idempotent — safe to re-run. Uses the REST API (kcadm array config is unreliable).
TOKEN=$(curl -sf "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN}&password=${KEYCLOAK_ADMIN_PASSWORD}" \
  | jq -r '.access_token')

CRP_TYPE="org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy"
POLICIES=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/components?type=${CRP_TYPE}" \
  -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || echo "[]")

# Trusted Hosts: allow localhost + claude.ai callback hosts; disable the source-host check (the
# registration request arrives via the reverse proxy / Docker gateway, not the client's own IP).
TH_ID=$(echo "$POLICIES" | jq -r '.[] | select(.providerId=="trusted-hosts") | .id' | head -1)
if [[ -n "$TH_ID" ]]; then
  TH=$(echo "$POLICIES" | jq --arg id "$TH_ID" '.[] | select(.id==$id)
    | .config["trusted-hosts"]=["localhost","127.0.0.1","claude.ai"]
    | .config["host-sending-registration-request-must-match"]=["false"]
    | .config["client-uris-must-match"]=["true"]')
  curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/components/${TH_ID}" \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$TH" \
    && echo "  Trusted Hosts policy -> localhost,127.0.0.1,claude.ai (source-host check off)" \
    || echo "  WARN: failed to update Trusted Hosts policy"
else
  echo "  WARN: Trusted Hosts policy not found"
fi

# Allowed Client Scopes (anonymous + authenticated): permit the valid interactive OIDC optional
# scopes Claude requests. service_account is deliberately excluded.
for ACS_ID in $(echo "$POLICIES" | jq -r '.[] | select(.providerId=="allowed-client-templates") | .id'); do
  ACS=$(echo "$POLICIES" | jq --arg id "$ACS_ID" '.[] | select(.id==$id)
    | .config["allowed-client-scopes"]=["offline_access","organization","address","phone","microprofile-jwt"]
    | .config["allow-default-scopes"]=["true"]')
  curl -sf -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/components/${ACS_ID}" \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -d "$ACS" \
    && echo "  Allowed Client Scopes policy configured (${ACS_ID})" \
    || echo "  WARN: failed to update Allowed Client Scopes policy ${ACS_ID}"
done

# Remove the built-in service_account scope so it never appears in scopes_supported (it is invalid
# at the authorization endpoint and breaks Claude's PKCE flow). No client uses it for app function.
SA_ID=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/client-scopes" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.[] | select(.name=="service_account") | .id' | head -1)
if [[ -n "$SA_ID" ]]; then
  curl -sf -X DELETE "${KEYCLOAK_URL}/admin/realms/${REALM}/client-scopes/${SA_ID}" \
    -H "Authorization: Bearer ${TOKEN}" \
    && echo "  service_account scope removed" \
    || echo "  WARN: failed to remove service_account scope"
else
  echo "  service_account scope already absent"
fi

# ---- Apply realm session/token lifetimes (env-overridable; defaults match realm-export.json) ----
# Prod hardening knob: a firm may shorten idle/max without editing committed defaults. Uses the REST
# API (curl + bearer token) — kcadm array config is unreliable, and a realm PUT is the safe idiom.
echo "[9/9] Applying realm session/token lifetimes..."

KC_ACCESS_TOKEN_LIFESPAN="${KC_ACCESS_TOKEN_LIFESPAN:-300}"
KC_SSO_IDLE_TIMEOUT="${KC_SSO_IDLE_TIMEOUT:-1800}"
KC_SSO_MAX_LIFESPAN="${KC_SSO_MAX_LIFESPAN:-36000}"

# Re-fetch token (may have expired during earlier steps)
TOKEN=$(curl -sf "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN}&password=${KEYCLOAK_ADMIN_PASSWORD}" \
  | jq -r '.access_token')

LIFETIMES_JSON=$(jq -n \
  --argjson at "$KC_ACCESS_TOKEN_LIFESPAN" \
  --argjson idle "$KC_SSO_IDLE_TIMEOUT" \
  --argjson max "$KC_SSO_MAX_LIFESPAN" \
  '{accessTokenLifespan:$at, ssoSessionIdleTimeout:$idle, ssoSessionMaxLifespan:$max}')

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
  "${KEYCLOAK_URL}/admin/realms/${REALM}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$LIFETIMES_JSON")
if [[ "$HTTP_CODE" == "204" || "$HTTP_CODE" == "200" ]]; then
  echo "  lifetimes set: access=${KC_ACCESS_TOKEN_LIFESPAN}s idle=${KC_SSO_IDLE_TIMEOUT}s max=${KC_SSO_MAX_LIFESPAN}s"
else
  echo "  ERROR: failed to set realm lifetimes (HTTP ${HTTP_CODE})"
fi

echo ""
echo "=== Keycloak Bootstrap Complete ==="
echo ""
echo "  Mappers: groups, org-role (on gateway-bff)"
if [[ "${SEED_DEV_CREDENTIALS}" == "true" ]]; then
  echo "  User:    padmin@docteams.local / password"
  echo "  Org members: password backfilled to 'password' for local dev login"
else
  echo "  Dev credentials: SKIPPED (SEED_DEV_CREDENTIALS=false) — no padmin seeded, no passwords reset"
fi
echo "  MCP DCR:  Trusted Hosts (localhost,claude.ai) + allowed scopes set; service_account removed"
echo "  Lifetimes: access=${KC_ACCESS_TOKEN_LIFESPAN}s idle=${KC_SSO_IDLE_TIMEOUT}s max=${KC_SSO_MAX_LIFESPAN}s"
echo ""
echo "  Users must log out and back in to get the updated org_role claim in their JWT."
echo ""
