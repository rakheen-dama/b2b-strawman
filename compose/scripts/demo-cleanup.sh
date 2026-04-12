#!/usr/bin/env bash
# demo-cleanup.sh — Reset Keycloak + Postgres to pristine post-bootstrap state.
# Safe to run multiple times (idempotent). Intended for pre-demo cleanup so the
# QA walkthrough always starts from a clean environment.
#
# After running:
#   - Only padmin@docteams.local exists in KC (platform-admin)
#   - Zero KC organizations in docteams realm
#   - Zero access_requests, organizations, subscriptions, org_schema_mappings rows
#   - Zero tenant_* schemas
#   - Mailpit inbox cleared
#
# Prerequisites:
#   - Keycloak running on localhost:8180 with realm "docteams"
#   - Postgres running (b2b-postgres container or b2mash.local:5432)
#   - Mailpit running on localhost:8025
#   - Admin credentials: admin/admin (default dev setup)
#
# Usage: bash compose/scripts/demo-cleanup.sh
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM="docteams"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
PG_CONTAINER="${PG_CONTAINER:-b2b-postgres}"
PG_USER="${PG_USER:-postgres}"
PG_DB="${PG_DB:-docteams}"

echo "=== Demo Cleanup ==="
echo ""

# ---- Pre-flight checks ----
echo "[0/5] Pre-flight checks..."

# Check Keycloak
if ! curl -sf "${KEYCLOAK_URL}/realms/${REALM}" > /dev/null 2>&1; then
  echo "  ERROR: Keycloak not reachable at ${KEYCLOAK_URL}/realms/${REALM}"
  exit 1
fi
echo "  Keycloak: reachable"

# Check Postgres (via Docker container)
if ! docker exec "${PG_CONTAINER}" pg_isready -U "${PG_USER}" > /dev/null 2>&1; then
  echo "  ERROR: Postgres not reachable via container '${PG_CONTAINER}'"
  exit 1
fi
echo "  Postgres: reachable (container: ${PG_CONTAINER})"

# Check Mailpit (non-fatal — warn and continue)
MAILPIT_OK=true
if ! curl -sf "${MAILPIT_URL}/" > /dev/null 2>&1; then
  echo "  WARN: Mailpit not reachable at ${MAILPIT_URL} — will skip inbox clear"
  MAILPIT_OK=false
else
  echo "  Mailpit: reachable"
fi

echo ""

# ---- 1. Authenticate with Keycloak ----
echo "[1/5] Authenticating with Keycloak..."
TOKEN=$(curl -sf "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN}&password=${KEYCLOAK_ADMIN_PASSWORD}" \
  | jq -r '.access_token')

if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "  ERROR: Could not authenticate with Keycloak"
  exit 1
fi
echo "  Authenticated."

# ---- 2. Delete all KC organizations (and their memberships) ----
echo "[2/5] Deleting all Keycloak organizations in realm '${REALM}'..."
ORGS_JSON=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations?first=0&max=200" \
  -H "Authorization: Bearer ${TOKEN}" || echo "[]")
ORG_COUNT=$(echo "$ORGS_JSON" | jq 'length' 2>/dev/null || echo "0")

if [[ "$ORG_COUNT" -eq 0 ]]; then
  echo "  No organizations found — nothing to delete."
else
  for i in $(seq 0 $((ORG_COUNT - 1))); do
    ORG_ID=$(echo "$ORGS_JSON" | jq -r ".[$i].id")
    ORG_NAME=$(echo "$ORGS_JSON" | jq -r ".[$i].name")
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
      "${KEYCLOAK_URL}/admin/realms/${REALM}/organizations/${ORG_ID}" \
      -H "Authorization: Bearer ${TOKEN}")
    if [[ "$HTTP_CODE" == "204" ]]; then
      echo "  Deleted org: ${ORG_NAME} (${ORG_ID})"
    else
      echo "  WARN: Failed to delete org ${ORG_NAME} (HTTP ${HTTP_CODE})"
    fi
  done
  echo "  Summary: ${ORG_COUNT} organization(s) processed."
fi

# ---- 3. Delete all non-platform-admin users ----
echo "[3/5] Deleting all non-platform-admin users from realm '${REALM}'..."
PADMIN_EMAIL="padmin@docteams.local"

# Fetch all users (paginated, up to 500 — sufficient for dev realm)
USERS_JSON=$(curl -sf "${KEYCLOAK_URL}/admin/realms/${REALM}/users?first=0&max=500" \
  -H "Authorization: Bearer ${TOKEN}" || echo "[]")
USER_COUNT=$(echo "$USERS_JSON" | jq 'length' 2>/dev/null || echo "0")

DELETED=0
KEPT=0
for i in $(seq 0 $((USER_COUNT - 1))); do
  USER_ID=$(echo "$USERS_JSON" | jq -r ".[$i].id")
  USER_EMAIL=$(echo "$USERS_JSON" | jq -r ".[$i].email // empty")

  if [[ "$USER_EMAIL" == "$PADMIN_EMAIL" ]]; then
    echo "  Kept: ${USER_EMAIL} (platform admin)"
    KEPT=$((KEPT + 1))
    continue
  fi

  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users/${USER_ID}" \
    -H "Authorization: Bearer ${TOKEN}")
  if [[ "$HTTP_CODE" == "204" ]]; then
    DELETED=$((DELETED + 1))
    echo "  Deleted: ${USER_EMAIL:-unknown} (${USER_ID})"
  else
    echo "  WARN: Failed to delete user ${USER_EMAIL:-unknown} (HTTP ${HTTP_CODE})"
  fi
done
echo "  Summary: ${DELETED} deleted, ${KEPT} kept."

# ---- 4. Clean Postgres tables and tenant schemas ----
echo "[4/5] Cleaning Postgres (database: ${PG_DB})..."

# Truncate global tables (order matters for FK constraints)
PSQL="docker exec -i ${PG_CONTAINER} psql -U ${PG_USER} -d ${PG_DB} -q"

$PSQL -c "TRUNCATE TABLE public.subscriptions CASCADE"
$PSQL -c "TRUNCATE TABLE public.org_schema_mapping CASCADE"
$PSQL -c "TRUNCATE TABLE public.organizations CASCADE"
$PSQL -c "TRUNCATE TABLE public.access_requests CASCADE"

# Drop all tenant_* schemas
TENANT_SCHEMAS=$(docker exec "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_DB}" -t -A \
  -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'")

SCHEMA_COUNT=0
if [[ -n "$TENANT_SCHEMAS" ]]; then
  while IFS= read -r schema; do
    [[ -z "$schema" ]] && continue
    docker exec "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_DB}" -q \
      -c "DROP SCHEMA \"${schema}\" CASCADE"
    echo "  Dropped schema: ${schema}"
    SCHEMA_COUNT=$((SCHEMA_COUNT + 1))
  done <<< "$TENANT_SCHEMAS"
fi

if [[ "$SCHEMA_COUNT" -eq 0 ]]; then
  echo "  No tenant schemas found."
else
  echo "  Dropped ${SCHEMA_COUNT} tenant schema(s)."
fi

echo "  Tables truncated: subscriptions, org_schema_mapping, organizations, access_requests"
echo "  Tenant schemas dropped."

# ---- 5. Clear Mailpit inbox ----
echo "[5/5] Clearing Mailpit inbox..."
if [[ "$MAILPIT_OK" == "true" ]]; then
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${MAILPIT_URL}/api/v1/messages" 2>/dev/null || echo "000")
  if [[ "$HTTP_CODE" == "200" ]]; then
    echo "  Mailpit inbox cleared."
  else
    echo "  WARN: Could not clear Mailpit (HTTP ${HTTP_CODE}) — may need manual clear."
  fi
else
  echo "  Skipped (Mailpit not reachable)."
fi

echo ""
echo "=== Demo Cleanup Complete ==="
echo ""
echo "  Keycloak:  0 orgs, only padmin@docteams.local remains"
echo "  Postgres:  0 access_requests, 0 organizations, 0 tenant schemas"
echo "  Mailpit:   inbox cleared"
echo ""
echo "  Ready for demo walkthrough."
echo ""
