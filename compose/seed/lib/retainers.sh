#!/bin/sh
# compose/seed/lib/retainers.sh — Create retainer agreements
# Requires: lib/common.sh, ACME_ID
#
# Creates one "Monthly Support Retainer" for Acme Corp (20 hrs/month, $3k).

seed_retainers() {
  echo ""
  echo "==> Seeding retainers"
  jwt=$(get_jwt user_e2e_alice owner)

  existing=$(api_get "/api/retainers?size=200" "$jwt")
  acme_retainer=$(echo "$existing" | jq -r '.[] | select(.name == "Monthly Support Retainer") | .id' 2>/dev/null)

  if [ -n "$acme_retainer" ] && [ "$acme_retainer" != "null" ]; then
    echo "    [skip] Monthly Support Retainer exists (${acme_retainer})"
    ACME_RETAINER_ID="$acme_retainer"
  else
    start_date=$(date -v-60d +%Y-%m-%d 2>/dev/null || date -d "-60 days" +%Y-%m-%d)

    body=$(api_post "/api/retainers" "{
      \"customerId\": \"${ACME_ID}\",
      \"name\": \"Monthly Support Retainer\",
      \"type\": \"HOUR_BANK\",
      \"frequency\": \"MONTHLY\",
      \"startDate\": \"${start_date}\",
      \"allocatedHours\": 20,
      \"periodFee\": 3000.00,
      \"rolloverPolicy\": \"CARRY_FORWARD\",
      \"rolloverCapHours\": 10,
      \"notes\": \"20 hours per month with up to 10 hrs rollover\"
    }" "$jwt")
    check_status "Create Monthly Support Retainer" || return 1
    ACME_RETAINER_ID=$(echo "$body" | jq -r '.id')
  fi

  export ACME_RETAINER_ID

  echo ""
  echo "    Retainers seeded:"
  echo "      Monthly Support Retainer: ${ACME_RETAINER_ID}"
}
