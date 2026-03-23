#!/usr/bin/env bash
# dev-seed-tenant.sh — Seed Keycloak + provision acme-corp tenant for migrated E2E tests.
# Decouples test execution from the onboarding UI flow.
#
# Prerequisites: Full dev stack running (dev-e2e-up.sh or dev-up.sh --all)
#
# Usage: bash compose/scripts/dev-seed-tenant.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"

echo "=== Seed Tenant (acme-corp) ==="
echo ""

# Step 1: Seed Keycloak
echo "[1/3] Running Keycloak seed..."
bash "$SCRIPT_DIR/keycloak-seed.sh"

# Step 2: Wait for backend
echo "[2/3] Waiting for backend..."
MAX_WAIT=60
ELAPSED=0
while [[ $ELAPSED -lt $MAX_WAIT ]]; do
  if curl -sf "${BACKEND_URL}/actuator/health" > /dev/null 2>&1; then
    echo "  Backend is ready."
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done
if [[ $ELAPSED -ge $MAX_WAIT ]]; then
  echo "  ERROR: Backend not ready after ${MAX_WAIT}s"
  exit 1
fi

# Step 3: Provision tenant schema
echo "[3/3] Provisioning acme-corp tenant..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "${BACKEND_URL}/internal/orgs/provision" \
  -H "Content-Type: application/json" \
  -d '{"clerkOrgId":"acme-corp","orgName":"Acme Corp","verticalProfile":"accounting-za"}')

if [[ "$HTTP_STATUS" == "201" ]]; then
  echo "  Tenant provisioned (201)."
elif [[ "$HTTP_STATUS" == "409" ]]; then
  echo "  Tenant already provisioned (409) — skipping."
else
  echo "  ERROR: Unexpected HTTP status ${HTTP_STATUS}"
  exit 1
fi

echo ""
echo "=== Seed Complete ==="
echo ""
echo "  Org slug:  acme-corp"
echo "  Users:"
echo "    alice@example.com / password  (owner — first member, auto-promoted)"
echo "    bob@example.com   / password  (member — lazy-created on first login)"
echo "    carol@example.com / password  (member — lazy-created on first login)"
echo ""
echo "  Run migrated tests:  cd frontend && npx playwright test e2e/tests/keycloak/existing-migration.spec.ts"
echo ""
