# Fix Spec: GAP-D0-09 — Pre-demo cleanup script (stale KC/DB state)

## Problem
When the Day 0 approval flow reuses a stale Keycloak organization (via the GAP-D0-01 fix's 409-retry path), the newly created tenant schema does not get seeded with `org_settings` (vertical profile, enabled modules, terminology, pack statuses). Result: generic terminology ("Projects" not "Matters"), no legal modules, 500 errors on the dashboard. This blocks the entire demo walkthrough.

The root cause is that the stale-org reuse path is incomplete — but fixing production idempotency is out of scope for this demo QA cycle. Instead, we ensure the demo always starts from a pristine environment by providing a cleanup script that purges all prior-cycle state.

## Strategy
**Approach B (Demo pragmatic)**: Add `compose/scripts/demo-cleanup.sh` that resets both Keycloak and Postgres to the post-bootstrap baseline state. With no stale orgs/users/schemas, the approval flow always takes the clean-create path (which seeds `org_settings` correctly, as verified in QA Turn 1 CP 0.17).

This also subsumes GAP-D0-08 (stale-org `inviteUser` 409 and `redirectUrl` not updated) — with no stale orgs, those code paths never fire.

## Scope
One new file: `compose/scripts/demo-cleanup.sh`

## Fix

### File: `compose/scripts/demo-cleanup.sh` (new)

```bash
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
#   - Postgres running on localhost:5432 (or b2mash.local:5432)
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
PG_HOST="${PG_HOST:-b2mash.local}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-postgres}"
PG_PASSWORD="${PG_PASSWORD:-changeme}"
PG_DB="${PG_DB:-app}"

echo "=== Demo Cleanup ==="
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
echo "[4/5] Cleaning Postgres..."
export PGPASSWORD="${PG_PASSWORD}"

# Truncate global tables (order matters for FK constraints)
psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d "${PG_DB}" -q <<'SQL'
-- Truncate in dependency order (subscriptions → organizations → access_requests)
TRUNCATE TABLE public.subscriptions CASCADE;
TRUNCATE TABLE public.org_schema_mapping CASCADE;
TRUNCATE TABLE public.organizations CASCADE;
TRUNCATE TABLE public.access_requests CASCADE;

-- Drop all tenant_* schemas
DO $$
DECLARE
  s TEXT;
BEGIN
  FOR s IN
    SELECT schema_name FROM information_schema.schemata
    WHERE schema_name LIKE 'tenant_%'
  LOOP
    EXECUTE format('DROP SCHEMA %I CASCADE', s);
    RAISE NOTICE 'Dropped schema: %', s;
  END LOOP;
END
$$;
SQL

echo "  Tables truncated: subscriptions, org_schema_mapping, organizations, access_requests"
echo "  Tenant schemas dropped."

# ---- 5. Clear Mailpit inbox ----
echo "[5/5] Clearing Mailpit inbox..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "${MAILPIT_URL}/api/v1/messages" 2>/dev/null || echo "000")
if [[ "$HTTP_CODE" == "200" ]]; then
  echo "  Mailpit inbox cleared."
else
  echo "  WARN: Could not clear Mailpit (HTTP ${HTTP_CODE}) — may need manual clear."
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
```

## Implementation Notes

### Keycloak API endpoints used
All are standard Admin REST API endpoints already used in `KeycloakProvisioningClient.java` and `KeycloakAdminClient.java`:
- `GET /admin/realms/{realm}/organizations?first=0&max=200` — list all orgs (same pattern as `findOrganizationByAlias`)
- `DELETE /admin/realms/{realm}/organizations/{id}` — delete org (same as `KeycloakAdminClient.deleteOrganization`)
- `GET /admin/realms/{realm}/users?first=0&max=500` — list all users (same pattern as `findUserIdByEmail`)
- `DELETE /admin/realms/{realm}/users/{id}` — delete user (same as `KeycloakAdminClient.deleteUser`)
- `POST /realms/master/protocol/openid-connect/token` — admin auth (same as `getAdminToken`)

### Postgres schema naming
Tenant schemas use `tenant_<12-hex-chars>` naming (confirmed in `SchemaNameGenerator` and QA evidence: `tenant_5039f2d497cf`). The `LIKE 'tenant_%'` filter is safe and specific.

### Postgres host
Uses `b2mash.local` as default (per MEMORY.md: "Postgres host: `b2mash.local:5432`"). Overridable via `PG_HOST` env var.

### Idempotency
- Deleting a non-existent org/user returns 404, which the script logs as WARN and continues.
- `TRUNCATE ... CASCADE` is safe on empty tables.
- `DROP SCHEMA IF EXISTS` equivalent via the PL/pgSQL loop.
- Mailpit `DELETE /api/v1/messages` on empty inbox returns 200.

### Integration with QA workflow
QA Turn 3 should run `bash compose/scripts/demo-cleanup.sh` as the pre-run step (replacing the ad-hoc manual cleanup done in QA Turn 1 "Session 0.A/0.B"). This gives a documented, repeatable, one-command reset.

## Testing
1. Run `bash compose/scripts/demo-cleanup.sh` — should complete with all steps OK.
2. Verify: `curl -sf http://localhost:8180/admin/realms/docteams/organizations -H "Authorization: Bearer $TOKEN"` returns `[]`.
3. Verify: `psql -h b2mash.local -U postgres -d app -c "SELECT count(*) FROM public.organizations"` returns 0.
4. Verify: `psql -h b2mash.local -U postgres -d app -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'"` returns 0 rows.
5. Run the cleanup script a second time — should complete cleanly (idempotent).
6. Run Day 0 walkthrough — approval should seed `org_settings` with `vertical_profile=legal-za`.

## Out of Scope
- Production idempotency for stale-org reuse path (GAP-D0-08 + the seeding gap). Tracked for a future production-hardening epic.
- Deleting the platform-admin user — `padmin@docteams.local` is always preserved.
