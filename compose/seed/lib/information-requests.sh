#!/bin/sh
# compose/seed/lib/information-requests.sh — Boot-seed information request (Phase 88 628F, U22; Day 3)
# Requires: lib/common.sh, ACME_ID, WEBSITE_REDESIGN_ID
#
# One SENT information request to Acme Corp's portal contact (contact@acme.example.com) on Website
# Redesign, with one FILE_UPLOAD item. Sending emails the contact and mints ONE portal magic-link
# token (cap 3 per contact per 5 min) — the portal 90-day globalSetup and REGRESSION each spend one.
#
# Idempotency: the request is recognised by its item name. SENT / IN_PROGRESS → skip; DRAFT → send;
# none, or only COMPLETED / CANCELLED ones (a portal Day 3 run consumed it) → create + send, so the
# portal Day 3 flow stays re-runnable on a reseeded stack.

SEED_INFO_REQUEST_ITEM="Certified ID copy"
SEED_PORTAL_CONTACT_EMAIL="contact@acme.example.com"

seed_information_request() {
  echo ""
  echo "==> Step 10: Information request (Day 3)"
  _ir_jwt=$(get_jwt user_e2e_alice owner)

  _ir_list=$(api_get "/api/information-requests?customerId=${ACME_ID}&projectId=${WEBSITE_REDESIGN_ID}" "$_ir_jwt")
  check_2xx "List Acme information requests" || return 1
  _ir_mine=$(echo "$_ir_list" | jq -c --arg n "$SEED_INFO_REQUEST_ITEM" \
    '[.[] | select(any(.items[]?; .name == $n))]')

  _ir_open=$(echo "$_ir_mine" | jq -r \
    '.[] | select(.status == "SENT" or .status == "IN_PROGRESS") | .requestNumber' | head -1)
  if [ -n "$_ir_open" ]; then
    INFO_REQUEST_NUMBER="$_ir_open"
    echo "    [skip] Information request ${INFO_REQUEST_NUMBER} already sent"
    return 0
  fi

  INFO_REQUEST_ID=$(echo "$_ir_mine" | jq -r '.[] | select(.status == "DRAFT") | .id' | head -1)
  if [ -z "$INFO_REQUEST_ID" ]; then
    _ir_contacts=$(api_get "/api/clients/${ACME_ID}/portal-contacts" "$_ir_jwt")
    check_2xx "List Acme portal contacts" || return 1
    _ir_contact=$(echo "$_ir_contacts" | jq -r --arg e "$SEED_PORTAL_CONTACT_EMAIL" \
      '.[] | select(.email == $e) | .id' | head -1)
    if [ -z "$_ir_contact" ] || [ "$_ir_contact" = "null" ]; then
      echo "    [FAIL] No portal contact ${SEED_PORTAL_CONTACT_EMAIL} on Acme Corp" >&2
      return 1
    fi

    _ir_body=$(api_post "/api/information-requests" "{
      \"clientId\": \"${ACME_ID}\",
      \"matterId\": \"${WEBSITE_REDESIGN_ID}\",
      \"portalContactId\": \"${_ir_contact}\",
      \"items\": [{
        \"name\": \"${SEED_INFO_REQUEST_ITEM}\",
        \"description\": \"Upload a certified copy of your ID document\",
        \"responseType\": \"FILE_UPLOAD\",
        \"required\": true,
        \"fileTypeHints\": \"PDF, JPG, PNG\",
        \"sortOrder\": 0
      }]
    }" "$_ir_jwt")
    check_2xx "Create information request" || return 1
    INFO_REQUEST_ID=$(echo "$_ir_body" | jq -r '.id')
  fi

  _ir_sent=$(api_post "/api/information-requests/${INFO_REQUEST_ID}/send" '' "$_ir_jwt")
  check_2xx "Send information request" || return 1
  INFO_REQUEST_NUMBER=$(echo "$_ir_sent" | jq -r '.requestNumber')
  echo "    Information request: ${INFO_REQUEST_NUMBER} (${INFO_REQUEST_ID}) SENT"
}
