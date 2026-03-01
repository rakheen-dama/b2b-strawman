#!/bin/sh
# compose/seed/lib/rates-budgets.sh — Billing rates and project budgets
# Requires: lib/common.sh, lib/projects.sh, lib/tasks.sh (member IDs)

seed_rates_budgets() {
  echo ""
  echo "==> Seeding billing rates and budgets"
  jwt=$(get_jwt user_e2e_alice owner)

  effective_from=$(date -v-60d +%Y-%m-%d 2>/dev/null || date -d "-60 days" +%Y-%m-%d)

  # ── Billing rates ──────────────────────────────────────────────
  echo ""
  echo "  -- Billing rates --"

  # billing-rates is paginated ({content:[...]}), use .content[]
  existing_rates=$(api_get "/api/billing-rates" "$jwt")

  _rate_exists() {
    _re_member_id="$1"
    echo "$existing_rates" | jq -e ".content[] | select(.memberId == \"${_re_member_id}\" and .projectId == null and .customerId == null)" > /dev/null 2>&1
  }

  if ! _rate_exists "$ALICE_MEMBER_ID"; then
    api_post "/api/billing-rates" "{
      \"memberId\": \"${ALICE_MEMBER_ID}\",
      \"currency\": \"USD\",
      \"hourlyRate\": 150.00,
      \"effectiveFrom\": \"${effective_from}\"
    }" "$jwt" > /dev/null
    check_status "Alice org rate: \$150/hr"
  else
    echo "    [skip] Alice org rate exists"
  fi

  if ! _rate_exists "$BOB_MEMBER_ID"; then
    api_post "/api/billing-rates" "{
      \"memberId\": \"${BOB_MEMBER_ID}\",
      \"currency\": \"USD\",
      \"hourlyRate\": 120.00,
      \"effectiveFrom\": \"${effective_from}\"
    }" "$jwt" > /dev/null
    check_status "Bob org rate: \$120/hr"
  else
    echo "    [skip] Bob org rate exists"
  fi

  # ── Project budgets ────────────────────────────────────────────
  echo ""
  echo "  -- Project budgets --"

  _set_budget() {
    _sb_project_id="$1"
    _sb_label="$2"
    _sb_budget_json="$3"

    # Check if budget exists
    _sb_existing=$(api_get "/api/projects/${_sb_project_id}/budget" "$jwt")
    _sb_has_budget=$(echo "$_sb_existing" | jq -r '.budgetHours // .budgetAmount // empty' 2>/dev/null)
    if [ -n "$_sb_has_budget" ]; then
      echo "    [skip] ${_sb_label} budget exists"
      return 0
    fi

    api_put "/api/projects/${_sb_project_id}/budget" "$_sb_budget_json" "$jwt" > /dev/null
    check_status "${_sb_label} budget"
  }

  _set_budget "$BRAND_GUIDELINES_ID" "Brand Guidelines" '{
    "budgetHours": 40,
    "budgetAmount": 5000.00,
    "budgetCurrency": "USD",
    "alertThresholdPct": 80,
    "notes": "Fixed scope engagement"
  }'

  _set_budget "$MOBILE_APP_ID" "Mobile App MVP" '{
    "budgetHours": 120,
    "budgetAmount": 15000.00,
    "budgetCurrency": "USD",
    "alertThresholdPct": 75,
    "notes": "MVP phase budget"
  }'

  echo ""
  echo "    Rates and budgets seeded"
}
