#!/bin/sh
# compose/seed/lib/statements.sh — Boot-seed Statement of Account (Phase 88 628F, U22; Day 60)
# Requires: lib/common.sh, WEBSITE_REDESIGN_ID
#
# One Statement of Account on Website Redesign, shared with the client portal (shareWithClient:true →
# the paired Document is PORTAL-visible). notifyClient:false: no email, no extra magic-link token.
# Run after seed_boot_fee_notes so the statement lists the seeded fee notes.
# Idempotent: skipped when the matter already has a statement.

seed_statement() {
  echo ""
  echo "==> Step 11: Statement of Account (Day 60)"
  _st_jwt=$(get_jwt user_e2e_alice owner)

  _st_list=$(api_get "/api/matters/${WEBSITE_REDESIGN_ID}/statements?size=1" "$_st_jwt")
  check_2xx "List Website Redesign statements" || return 1
  _st_count=$(echo "$_st_list" | jq -r '.page.totalElements // (.content | length) // 0')
  if [ "$_st_count" -gt 0 ]; then
    echo "    [skip] Website Redesign already has ${_st_count} statement(s)"
    return 0
  fi

  _st_start="$(date +%Y)-01-01"
  _st_end="$(date +%Y-%m-%d)"
  _st_body=$(api_post "/api/matters/${WEBSITE_REDESIGN_ID}/statements" "{
    \"periodStart\": \"${_st_start}\",
    \"periodEnd\": \"${_st_end}\",
    \"shareWithClient\": true,
    \"notifyClient\": false
  }" "$_st_jwt")
  check_2xx "Generate Statement of Account" || return 1
  STATEMENT_ID=$(echo "$_st_body" | jq -r '.id')
  echo "    Statement of Account: ${STATEMENT_ID} (${_st_start} → ${_st_end}, shared with client)"
}
