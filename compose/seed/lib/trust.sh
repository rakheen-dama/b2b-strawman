#!/bin/sh
# compose/seed/lib/trust.sh — Boot-seed trust account (Phase 88 628F, U22)
# Requires: lib/common.sh (api_get, api_post, check_status, get_jwt)
#
# The values MUST equal TRUST_ACCOUNT in frontend/e2e/helpers/legal-lifecycle.ts and the depth-ii
# Day 5 create: the lifecycle Day 0 / depth-ii Day 5 specs take their "account exists" branch on this
# name, and legal-lifecycle Day 45 asserts the option text "Standard Bank - 0123456789".
# Only one primary GENERAL account may exist (unique index), so a differently named ACTIVE GENERAL
# account is a hard failure rather than a second POST.

TRUST_ACCOUNT_NAME="Mathebula & Partners Trust Account"

seed_trust_account() {
  echo ""
  echo "==> Step 8: Trust account"
  _ta_jwt=$(get_jwt user_e2e_alice owner)

  _ta_list=$(api_get "/api/trust-accounts" "$_ta_jwt")
  check_2xx "List trust accounts" || return 1

  TRUST_ACCOUNT_ID=$(echo "$_ta_list" | jq -r --arg n "$TRUST_ACCOUNT_NAME" \
    '.[] | select(.accountName == $n) | .id' | head -1)
  if [ -n "$TRUST_ACCOUNT_ID" ] && [ "$TRUST_ACCOUNT_ID" != "null" ]; then
    echo "    [skip] ${TRUST_ACCOUNT_NAME} exists (${TRUST_ACCOUNT_ID})"
    return 0
  fi

  _ta_other=$(echo "$_ta_list" | jq -r \
    '.[] | select(.accountType == "GENERAL" and .status == "ACTIVE") | .accountName' | head -1)
  if [ -n "$_ta_other" ]; then
    echo "    [FAIL] An ACTIVE GENERAL trust account already exists under another name: ${_ta_other}" >&2
    return 1
  fi

  _ta_body=$(api_post "/api/trust-accounts" '{
    "accountName": "Mathebula & Partners Trust Account",
    "bankName": "Standard Bank",
    "branchCode": "051001",
    "accountNumber": "0123456789",
    "accountType": "GENERAL",
    "isPrimary": true,
    "requireDualApproval": false
  }' "$_ta_jwt")
  check_2xx "Create trust account" || return 1
  TRUST_ACCOUNT_ID=$(echo "$_ta_body" | jq -r '.id')
  echo "    Trust account: ${TRUST_ACCOUNT_NAME} (${TRUST_ACCOUNT_ID})"
}
