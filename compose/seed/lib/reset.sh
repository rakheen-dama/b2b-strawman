#!/bin/sh
# compose/seed/lib/reset.sh — Wipe E2E data and re-provision
# Requires: lib/common.sh
# This calls e2e-reseed.sh from the host, or drops/recreates the tenant schema from Docker.

do_reset() {
  echo ""
  echo "==> Resetting E2E data"

  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  RESEED_SCRIPT="${SCRIPT_DIR}/../../scripts/e2e-reseed.sh"

  if [ -f "$RESEED_SCRIPT" ]; then
    echo "    Running e2e-reseed.sh..."
    bash "$RESEED_SCRIPT"
    echo "    [ok] Reseed complete — base data restored"
  else
    # Fallback: re-provision via internal API
    echo "    e2e-reseed.sh not found, re-provisioning via API..."

    # The provision endpoint is idempotent, so this is safe
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "${BACKEND_URL}/internal/orgs/provision" \
      -H "Content-Type: application/json" \
      -H "X-API-KEY: ${API_KEY}" \
      -d "{\"clerkOrgId\": \"${ORG_ID}\", \"orgName\": \"E2E Test Organization\"}")
    check_status "Re-provision org" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "${BACKEND_URL}/internal/orgs/plan-sync" \
      -H "Content-Type: application/json" \
      -H "X-API-KEY: ${API_KEY}" \
      -d "{\"clerkOrgId\": \"${ORG_ID}\", \"planSlug\": \"pro\"}")
    check_status "Plan sync" "$STATUS"

    # Re-sync members
    for user in "user_e2e_alice alice@e2e-test.local Alice_Owner owner" \
                "user_e2e_bob bob@e2e-test.local Bob_Admin admin" \
                "user_e2e_carol carol@e2e-test.local Carol_Member member"; do
      set -- $user
      STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "${BACKEND_URL}/internal/members/sync" \
        -H "Content-Type: application/json" \
        -H "X-API-KEY: ${API_KEY}" \
        -d "{
          \"clerkOrgId\": \"${ORG_ID}\",
          \"clerkUserId\": \"$1\",
          \"email\": \"$2\",
          \"name\": \"$(echo $3 | tr '_' ' ')\",
          \"avatarUrl\": \"https://api.dicebear.com/7.x/initials/svg?seed=$1\",
          \"orgRole\": \"$4\"
        }")
      check_status "Sync $3" "$STATUS"
    done
  fi
}
