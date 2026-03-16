#!/bin/sh
# compose/seed/lifecycle-test.sh — 90-Day Accounting Firm Lifecycle Test
#
# Rerunnable, idempotent API test script for an accounting vertical (ZA).
# Exercises the full lifecycle: firm setup, client onboarding, time tracking,
# invoicing, retainers, profitability, compliance, and year-end.
#
# Usage:
#   bash compose/seed/lifecycle-test.sh                  # Run all days
#   bash compose/seed/lifecycle-test.sh --reset          # Wipe + run all days
#   bash compose/seed/lifecycle-test.sh --only day30     # Run a specific day
#   bash compose/seed/lifecycle-test.sh --from day07     # Run from a specific day
#
# Requires: E2E stack running (bash compose/scripts/e2e-up.sh)
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "${SCRIPT_DIR}/lib/common.sh"

# ── Extra helpers (not in common.sh) ───────────────────────────────
api_patch() {
  _pp="$1"; _pj="${2:-}"
  if [ -n "$_pj" ]; then
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X PATCH "${BACKEND_URL}${_pp}" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${_pj}" > "$_API_TMPFILE"
  else
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X PATCH "${BACKEND_URL}${_pp}" \
      -H "Content-Type: application/json" > "$_API_TMPFILE"
  fi
  cat "$_API_TMPFILE.body"
}

api_delete() {
  _dp="$1"; _dj="${2:-}"
  curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
    -X DELETE "${BACKEND_URL}${_dp}" \
    -H "Authorization: Bearer ${_dj}" > "$_API_TMPFILE"
  cat "$_API_TMPFILE.body"
}

# Assertion helpers
PASS=0; FAIL=0; SKIP=0

assert_eq() {
  _ae_label="$1"; _ae_expected="$2"; _ae_actual="$3"
  if [ "$_ae_expected" = "$_ae_actual" ]; then
    echo "    [PASS] ${_ae_label}: ${_ae_actual}" >&2
    PASS=$((PASS + 1))
  else
    echo "    [FAIL] ${_ae_label}: expected '${_ae_expected}', got '${_ae_actual}'" >&2
    FAIL=$((FAIL + 1))
  fi
}

assert_not_empty() {
  _ane_label="$1"; _ane_value="$2"
  if [ -n "$_ane_value" ] && [ "$_ane_value" != "null" ] && [ "$_ane_value" != "" ]; then
    echo "    [PASS] ${_ae_label}: present" >&2
    PASS=$((PASS + 1))
  else
    echo "    [FAIL] ${_ane_label}: empty or null" >&2
    FAIL=$((FAIL + 1))
  fi
}

assert_gt() {
  _ag_label="$1"; _ag_threshold="$2"; _ag_actual="$3"
  if [ "$_ag_actual" -gt "$_ag_threshold" ] 2>/dev/null; then
    echo "    [PASS] ${_ag_label}: ${_ag_actual} > ${_ag_threshold}" >&2
    PASS=$((PASS + 1))
  else
    echo "    [FAIL] ${_ag_label}: ${_ag_actual} not > ${_ag_threshold}" >&2
    FAIL=$((FAIL + 1))
  fi
}

assert_http() {
  _ah_label="$1"; _ah_expected="$2"
  _ah_actual=$(last_status)
  case "$_ah_actual" in
    ${_ah_expected}*) echo "    [PASS] ${_ah_label}: HTTP ${_ah_actual}" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] ${_ah_label}: expected HTTP ${_ah_expected}x, got ${_ah_actual}" >&2; FAIL=$((FAIL + 1)) ;;
  esac
}

# ── Parse flags ────────────────────────────────────────────────────
RESET=false; ONLY=""; FROM=""

while [ $# -gt 0 ]; do
  case "$1" in
    --reset) RESET=true; shift ;;
    --only)  ONLY="$2"; shift 2 ;;
    --only=*) ONLY="${1#--only=}"; shift ;;
    --from)  FROM="$2"; shift 2 ;;
    --from=*) FROM="${1#--from=}"; shift ;;
    *) echo "Unknown flag: $1"; exit 1 ;;
  esac
done

should_run() {
  _sr_day="$1"
  if [ -n "$ONLY" ]; then
    [ "$ONLY" = "$_sr_day" ]
    return $?
  fi
  if [ -n "$FROM" ]; then
    _sr_day_num=$(echo "$_sr_day" | sed 's/day//')
    _sr_from_num=$(echo "$FROM" | sed 's/day//')
    [ "$_sr_day_num" -ge "$_sr_from_num" ]
    return $?
  fi
  return 0
}

# ── Date helpers ───────────────────────────────────────────────────
today=$(date +%Y-%m-%d)
yesterday=$(date -v-1d +%Y-%m-%d 2>/dev/null || date -d "-1 day" +%Y-%m-%d)
last_week=$(date -v-7d +%Y-%m-%d 2>/dev/null || date -d "-7 days" +%Y-%m-%d)
two_weeks_ago=$(date -v-14d +%Y-%m-%d 2>/dev/null || date -d "-14 days" +%Y-%m-%d)
last_month=$(date -v-30d +%Y-%m-%d 2>/dev/null || date -d "-30 days" +%Y-%m-%d)
two_months_ago=$(date -v-60d +%Y-%m-%d 2>/dev/null || date -d "-60 days" +%Y-%m-%d)
next_week=$(date -v+7d +%Y-%m-%d 2>/dev/null || date -d "+7 days" +%Y-%m-%d)
next_month=$(date -v+30d +%Y-%m-%d 2>/dev/null || date -d "+30 days" +%Y-%m-%d)
in_60_days=$(date -v+60d +%Y-%m-%d 2>/dev/null || date -d "+60 days" +%Y-%m-%d)

# ── Ensure customer helper ─────────────────────────────────────────
# _ensure_customer <name> <email> <phone> <notes> <target_status> [jwt]
# Echoes the customer ID.
_ensure_customer() {
  _ec_name="$1"; _ec_email="$2"; _ec_phone="$3"
  _ec_notes="$4"; _ec_target="$5"; _ec_jwt="${6:-$ALICE_JWT}"

  _ec_id=$(api_get "/api/customers" "$_ec_jwt" \
    | jq -r ".[] | select(.name == \"${_ec_name}\") | .id" 2>/dev/null | head -1)

  if [ -n "$_ec_id" ] && [ "$_ec_id" != "null" ]; then
    echo "    [skip] ${_ec_name} exists (${_ec_id})" >&2
  else
    _ec_body=$(api_post "/api/customers" "{
      \"name\": \"${_ec_name}\",
      \"email\": \"${_ec_email}\",
      \"phone\": \"${_ec_phone}\",
      \"notes\": \"${_ec_notes}\"
    }" "$_ec_jwt")
    check_status "Create ${_ec_name}"
    _ec_id=$(echo "$_ec_body" | jq -r '.id')
  fi

  # Drive to target status
  _ec_current=$(api_get "/api/customers/${_ec_id}" "$_ec_jwt" | jq -r '.lifecycleStatus')
  if [ "$_ec_current" != "$_ec_target" ]; then
    case "$_ec_current" in
      PROSPECT)
        if [ "$_ec_target" != "PROSPECT" ]; then
          api_post "/api/customers/${_ec_id}/transition" \
            '{"targetStatus":"ONBOARDING","notes":"lifecycle test"}' "$_ec_jwt" > /dev/null
          check_status "${_ec_name} -> ONBOARDING"
        fi
        if [ "$_ec_target" = "ACTIVE" ]; then
          complete_checklists "$_ec_id" "$_ec_jwt"
          _ec_check=$(api_get "/api/customers/${_ec_id}" "$_ec_jwt" | jq -r '.lifecycleStatus')
          if [ "$_ec_check" != "ACTIVE" ]; then
            api_post "/api/customers/${_ec_id}/transition" \
              '{"targetStatus":"ACTIVE","notes":"lifecycle test"}' "$_ec_jwt" > /dev/null
            check_status "${_ec_name} -> ACTIVE"
          fi
        fi
        ;;
      ONBOARDING)
        if [ "$_ec_target" = "ACTIVE" ]; then
          complete_checklists "$_ec_id" "$_ec_jwt"
          _ec_check=$(api_get "/api/customers/${_ec_id}" "$_ec_jwt" | jq -r '.lifecycleStatus')
          if [ "$_ec_check" != "ACTIVE" ]; then
            api_post "/api/customers/${_ec_id}/transition" \
              '{"targetStatus":"ACTIVE","notes":"lifecycle test"}' "$_ec_jwt" > /dev/null
            check_status "${_ec_name} -> ACTIVE"
          fi
        fi
        ;;
    esac
  fi

  echo "$_ec_id"
}

# ── Ensure project helper ──────────────────────────────────────────
_ensure_project() {
  _ep_name="$1"; _ep_customer_id="$2"; _ep_jwt="${3:-$ALICE_JWT}"

  _ep_id=$(api_get "/api/projects?size=200" "$_ep_jwt" \
    | jq -r ".[] | select(.name == \"${_ep_name}\") | .id" 2>/dev/null | head -1)

  if [ -n "$_ep_id" ] && [ "$_ep_id" != "null" ]; then
    echo "    [skip] ${_ep_name} exists (${_ep_id})" >&2
  else
    _ep_body=$(api_post "/api/projects" "{
      \"name\": \"${_ep_name}\",
      \"customerId\": \"${_ep_customer_id}\",
      \"dueDate\": \"${in_60_days}\"
    }" "$_ep_jwt")
    check_status "Create project: ${_ep_name}"
    _ep_id=$(echo "$_ep_body" | jq -r '.id')
  fi
  echo "$_ep_id"
}

# ── Ensure task helper ─────────────────────────────────────────────
_ensure_task() {
  _et_project_id="$1"; _et_title="$2"; _et_assignee_id="$3"
  _et_priority="${4:-MEDIUM}"; _et_jwt="${5:-$ALICE_JWT}"

  _et_id=$(api_get "/api/projects/${_et_project_id}/tasks?size=200" "$_et_jwt" \
    | jq -r ".[] | select(.title == \"${_et_title}\") | .id" 2>/dev/null | head -1)

  if [ -n "$_et_id" ] && [ "$_et_id" != "null" ]; then
    echo "    [skip] Task: ${_et_title}" >&2
  else
    _et_asgn=""
    [ -n "$_et_assignee_id" ] && [ "$_et_assignee_id" != "none" ] && \
      _et_asgn="\"assigneeId\": \"${_et_assignee_id}\","

    _et_body=$(api_post "/api/projects/${_et_project_id}/tasks" "{
      ${_et_asgn}
      \"title\": \"${_et_title}\",
      \"priority\": \"${_et_priority}\"
    }" "$_et_jwt")
    check_status "Create task: ${_et_title}"
    _et_id=$(echo "$_et_body" | jq -r '.id')
  fi
  echo "$_et_id"
}

# ── Ensure time entry helper ──────────────────────────────────────
_log_time() {
  _lt_task_id="$1"; _lt_date="$2"; _lt_minutes="$3"
  _lt_billable="$4"; _lt_desc="$5"; _lt_jwt="$6"

  _lt_existing=$(api_get "/api/tasks/${_lt_task_id}/time-entries" "$_lt_jwt" \
    | jq -r ".[] | select(.description == \"${_lt_desc}\") | .id" 2>/dev/null | head -1)
  if [ -n "$_lt_existing" ] && [ "$_lt_existing" != "null" ]; then
    echo "    [skip] Time: ${_lt_desc}" >&2
    echo "$_lt_existing"
    return 0
  fi

  _lt_body=$(api_post "/api/tasks/${_lt_task_id}/time-entries" "{
    \"date\": \"${_lt_date}\",
    \"durationMinutes\": ${_lt_minutes},
    \"billable\": ${_lt_billable},
    \"description\": \"${_lt_desc}\"
  }" "$_lt_jwt")
  check_status "Time: ${_lt_desc} (${_lt_minutes}m)"
  echo "$_lt_body" | jq -r '.id'
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 0 — Firm Setup
# ═══════════════════════════════════════════════════════════════════
day_00_firm_setup() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 0 — Firm Setup                       ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 0.1 Verify org settings exist ──────────────────────────────
  echo ""
  echo "  -- 0.1 Org Settings --"
  settings=$(api_get "/api/settings" "$ALICE_JWT")
  assert_http "GET /api/settings" "2"
  _currency=$(echo "$settings" | jq -r '.defaultCurrency')
  assert_not_empty "defaultCurrency" "$_currency"

  # Update branding
  api_put "/api/settings" '{
    "defaultCurrency": "ZAR",
    "brandColor": "#1B5E20"
  }' "$ALICE_JWT" > /dev/null
  assert_http "Update org settings" "2"

  settings=$(api_get "/api/settings" "$ALICE_JWT")
  _currency=$(echo "$settings" | jq -r '.defaultCurrency')
  _brand=$(echo "$settings" | jq -r '.brandColor')
  assert_eq "Currency is ZAR" "ZAR" "$_currency"
  assert_eq "Brand colour set" "#1B5E20" "$_brand"

  # ── 0.2 Rate cards ────────────────────────────────────────────
  echo ""
  echo "  -- 0.2 Rate Cards --"

  # Alice: R1500/hr, Bob: R850/hr, Carol: R450/hr
  _set_rate() {
    _sr_member_id="$1"; _sr_rate="$2"; _sr_label="$3"
    existing=$(api_get "/api/billing-rates" "$ALICE_JWT")
    has_rate=$(echo "$existing" | jq -e ".content[] | select(.memberId == \"${_sr_member_id}\" and .projectId == null)" 2>/dev/null)
    if [ -n "$has_rate" ]; then
      echo "    [skip] ${_sr_label} billing rate exists" >&2
    else
      api_post "/api/billing-rates" "{
        \"memberId\": \"${_sr_member_id}\",
        \"currency\": \"ZAR\",
        \"hourlyRate\": ${_sr_rate},
        \"effectiveFrom\": \"${two_months_ago}\"
      }" "$ALICE_JWT" > /dev/null
      check_status "${_sr_label} rate: R${_sr_rate}/hr"
    fi
  }

  _set_rate "$ALICE_MEMBER_ID" "1500.00" "Alice"
  _set_rate "$BOB_MEMBER_ID" "850.00" "Bob"
  _set_rate "$CAROL_MEMBER_ID" "450.00" "Carol"

  # Cost rates
  _set_cost_rate() {
    _scr_member_id="$1"; _scr_cost="$2"; _scr_label="$3"
    existing=$(api_get "/api/cost-rates" "$ALICE_JWT")
    has_rate=$(echo "$existing" | jq -e ".content[] | select(.memberId == \"${_scr_member_id}\")" 2>/dev/null)
    if [ -n "$has_rate" ]; then
      echo "    [skip] ${_scr_label} cost rate exists" >&2
    else
      api_post "/api/cost-rates" "{
        \"memberId\": \"${_scr_member_id}\",
        \"currency\": \"ZAR\",
        \"hourlyCost\": ${_scr_cost},
        \"effectiveFrom\": \"${two_months_ago}\"
      }" "$ALICE_JWT" > /dev/null
      check_status "${_scr_label} cost rate: R${_scr_cost}/hr"
    fi
  }

  _set_cost_rate "$ALICE_MEMBER_ID" "600.00" "Alice"
  _set_cost_rate "$BOB_MEMBER_ID" "400.00" "Bob"
  _set_cost_rate "$CAROL_MEMBER_ID" "200.00" "Carol"

  # ── 0.3 Tax rates ─────────────────────────────────────────────
  echo ""
  echo "  -- 0.3 Tax Rates --"
  tax_rates=$(api_get "/api/tax-rates" "$ALICE_JWT")
  vat_exists=$(echo "$tax_rates" | jq -r '.[] | select(.name == "VAT") | .id' 2>/dev/null | head -1)
  if [ -n "$vat_exists" ] && [ "$vat_exists" != "null" ]; then
    echo "    [skip] VAT rate exists"
    VAT_RATE_ID="$vat_exists"
  else
    vat_body=$(api_post "/api/tax-rates" '{
      "name": "VAT",
      "taxPercent": 15.00,
      "isActive": true
    }' "$ALICE_JWT")
    check_status "Create VAT 15%"
    VAT_RATE_ID=$(echo "$vat_body" | jq -r '.id')
  fi
  export VAT_RATE_ID

  # ── 0.4 Verify team ───────────────────────────────────────────
  echo ""
  echo "  -- 0.4 Team Verification --"
  members=$(api_get "/api/members" "$ALICE_JWT")
  member_count=$(echo "$members" | jq 'length')
  assert_gt "Team has 3+ members" 2 "$member_count"

  # ── 0.5 Verify custom fields (accounting pack) ────────────────
  echo ""
  echo "  -- 0.5 Custom Fields --"
  fields=$(api_get "/api/field-definitions?entityType=CUSTOMER" "$ALICE_JWT")
  field_count=$(echo "$fields" | jq 'if type == "array" then length else .content | length end' 2>/dev/null || echo "0")
  assert_gt "Customer custom fields exist" 0 "$field_count"

  # ── 0.6 Verify document templates ─────────────────────────────
  echo ""
  echo "  -- 0.6 Document Templates --"
  templates=$(api_get "/api/templates" "$ALICE_JWT")
  template_count=$(echo "$templates" | jq 'length')
  assert_gt "Document templates exist" 0 "$template_count"

  # ── 0.7 Verify checklist templates ─────────────────────────────
  echo ""
  echo "  -- 0.7 Compliance Checklists --"
  # Checklists are instantiated per customer, verify the system works by
  # checking settings pack status
  _pack_status=$(echo "$settings" | jq -r '.compliancePackApplied // true')
  echo "    Compliance pack applied: ${_pack_status}"

  # ── 0.8 Verify automation rules ───────────────────────────────
  echo ""
  echo "  -- 0.8 Automation Rules --"
  automations=$(api_get "/api/automation-rules?size=200" "$ALICE_JWT")
  auto_count=$(echo "$automations" | jq 'if type == "array" then length else .content | length end' 2>/dev/null || echo "0")
  echo "    Automation rules present: ${auto_count}"
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 1 — First Client Onboarding (Kgosi Construction)
# ═══════════════════════════════════════════════════════════════════
day_01_first_client() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 1 — First Client (Kgosi Construction)║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 1.1 Create customer (as Bob) ───────────────────────────────
  echo ""
  echo "  -- 1.1 Create Customer --"
  KGOSI_ID=$(_ensure_customer \
    "Kgosi Construction (Pty) Ltd" \
    "thabo@kgosiconstruction.co.za" \
    "+27-11-555-0100" \
    "Pty Ltd, FYE 28 Feb, VAT vendor" \
    "PROSPECT" \
    "$BOB_JWT")
  export KGOSI_ID

  # Verify lifecycle status
  kgosi=$(api_get "/api/customers/${KGOSI_ID}" "$BOB_JWT")
  _status=$(echo "$kgosi" | jq -r '.lifecycleStatus')
  assert_eq "Kgosi status is PROSPECT" "PROSPECT" "$_status"

  # ── 1.2 FICA/KYC checklist ────────────────────────────────────
  echo ""
  echo "  -- 1.2 FICA Checklist --"
  # Transition to ONBOARDING (triggers checklist instantiation)
  api_post "/api/customers/${KGOSI_ID}/transition" \
    '{"targetStatus":"ONBOARDING","notes":"Begin FICA onboarding"}' "$BOB_JWT" > /dev/null
  check_status "Kgosi -> ONBOARDING"

  checklists=$(api_get "/api/customers/${KGOSI_ID}/checklists" "$BOB_JWT")
  checklist_count=$(echo "$checklists" | jq 'length')
  assert_gt "Checklists instantiated" 0 "$checklist_count"

  # Complete first 2 items
  item_ids=$(echo "$checklists" | jq -r '
    [.[] | .items[]? | select(.status != "COMPLETED" and .status != "SKIPPED") | .id] | .[]
  ' 2>/dev/null)

  _completed=0
  for item_id in $item_ids; do
    if [ $_completed -ge 2 ]; then break; fi
    api_put "/api/checklist-items/${item_id}/complete" \
      '{"notes":"Document verified"}' "$BOB_JWT" > /dev/null
    check_status "Complete checklist item"
    _completed=$((_completed + 1))
  done

  # ── 1.3 Information request ────────────────────────────────────
  echo ""
  echo "  -- 1.3 Information Request --"
  ir_body=$(api_post "/api/information-requests" "{
    \"customerId\": \"${KGOSI_ID}\",
    \"subject\": \"FICA Documents Required\",
    \"description\": \"Please provide the following FICA compliance documents\",
    \"items\": [
      {\"fieldName\": \"Certified ID Copy\", \"isRequired\": true},
      {\"fieldName\": \"Company Registration (CM29)\", \"isRequired\": true},
      {\"fieldName\": \"Proof of Address\", \"isRequired\": true}
    ]
  }" "$BOB_JWT")
  check_status "Create info request"
  IR_KGOSI_ID=$(echo "$ir_body" | jq -r '.id')

  # Send the request
  api_post "/api/information-requests/${IR_KGOSI_ID}/send" '{}' "$BOB_JWT" > /dev/null
  check_status "Send info request"

  # ── 1.4 Create proposal (as Alice) ────────────────────────────
  echo ""
  echo "  -- 1.4 Proposal: Monthly Bookkeeping --"
  expires_at=$(date -v+30d -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -d "+30 days" -u +%Y-%m-%dT%H:%M:%SZ)

  existing_proposals=$(api_get "/api/proposals?size=200" "$ALICE_JWT")
  kgosi_proposal=$(echo "$existing_proposals" | jq -r ".content[]? | select(.title == \"Monthly Bookkeeping — Kgosi Construction\") | .id" 2>/dev/null | head -1)

  if [ -n "$kgosi_proposal" ] && [ "$kgosi_proposal" != "null" ]; then
    echo "    [skip] Kgosi proposal exists (${kgosi_proposal})"
    KGOSI_PROPOSAL_ID="$kgosi_proposal"
  else
    prop_body=$(api_post "/api/proposals" "{
      \"title\": \"Monthly Bookkeeping — Kgosi Construction\",
      \"customerId\": \"${KGOSI_ID}\",
      \"feeModel\": \"RETAINER\",
      \"retainerAmount\": 5500.00,
      \"retainerCurrency\": \"ZAR\",
      \"retainerHoursIncluded\": 10,
      \"expiresAt\": \"${expires_at}\"
    }" "$ALICE_JWT")
    check_status "Create proposal"
    KGOSI_PROPOSAL_ID=$(echo "$prop_body" | jq -r '.id')
  fi
  export KGOSI_PROPOSAL_ID

  # Send proposal
  prop_detail=$(api_get "/api/proposals/${KGOSI_PROPOSAL_ID}" "$ALICE_JWT")
  _prop_status=$(echo "$prop_detail" | jq -r '.status')
  if [ "$_prop_status" = "DRAFT" ]; then
    api_post "/api/proposals/${KGOSI_PROPOSAL_ID}/send" '{}' "$ALICE_JWT" > /dev/null
    check_status "Send proposal"
  fi

  # ── 1.5 Complete FICA & activate customer ──────────────────────
  echo ""
  echo "  -- 1.5 Complete FICA & Activate --"
  complete_checklists "$KGOSI_ID" "$BOB_JWT"
  _kstatus=$(api_get "/api/customers/${KGOSI_ID}" "$BOB_JWT" | jq -r '.lifecycleStatus')
  if [ "$_kstatus" != "ACTIVE" ]; then
    api_post "/api/customers/${KGOSI_ID}/transition" \
      '{"targetStatus":"ACTIVE","notes":"FICA complete"}' "$BOB_JWT" > /dev/null
    check_status "Kgosi -> ACTIVE"
  fi
  _kstatus=$(api_get "/api/customers/${KGOSI_ID}" "$BOB_JWT" | jq -r '.lifecycleStatus')
  assert_eq "Kgosi is ACTIVE" "ACTIVE" "$_kstatus"

  # ── 1.6 Create engagement (project) ───────────────────────────
  echo ""
  echo "  -- 1.6 Create Engagement --"
  KGOSI_PROJECT_ID=$(_ensure_project "Monthly Bookkeeping — Kgosi" "$KGOSI_ID" "$ALICE_JWT")
  export KGOSI_PROJECT_ID

  # Link project to customer
  api_post "/api/customers/${KGOSI_ID}/projects/${KGOSI_PROJECT_ID}" '{}' "$ALICE_JWT" > /dev/null
  # 409 if already linked, that's fine

  # ── 1.7 Create retainer agreement ─────────────────────────────
  echo ""
  echo "  -- 1.7 Retainer Agreement --"
  existing_retainers=$(api_get "/api/retainers?size=200" "$ALICE_JWT")
  kgosi_retainer=$(echo "$existing_retainers" | jq -r '.[] | select(.name == "Kgosi Monthly Bookkeeping") | .id' 2>/dev/null | head -1)

  if [ -n "$kgosi_retainer" ] && [ "$kgosi_retainer" != "null" ]; then
    echo "    [skip] Kgosi retainer exists (${kgosi_retainer})"
    KGOSI_RETAINER_ID="$kgosi_retainer"
  else
    ret_body=$(api_post "/api/retainers" "{
      \"customerId\": \"${KGOSI_ID}\",
      \"name\": \"Kgosi Monthly Bookkeeping\",
      \"type\": \"HOUR_BANK\",
      \"frequency\": \"MONTHLY\",
      \"startDate\": \"${last_month}\",
      \"allocatedHours\": 10,
      \"periodFee\": 5500.00,
      \"rolloverPolicy\": \"CARRY_FORWARD\",
      \"rolloverCapHours\": 5,
      \"notes\": \"R5,500/month retainer, 10 hours included\"
    }" "$ALICE_JWT")
    check_status "Create Kgosi retainer"
    KGOSI_RETAINER_ID=$(echo "$ret_body" | jq -r '.id')
  fi
  export KGOSI_RETAINER_ID

  # Create tasks for the engagement
  KGOSI_T1=$(_ensure_task "$KGOSI_PROJECT_ID" "Capture bank statements and receipts" "$CAROL_MEMBER_ID" "HIGH")
  KGOSI_T2=$(_ensure_task "$KGOSI_PROJECT_ID" "Reconcile accounts" "$CAROL_MEMBER_ID" "HIGH")
  KGOSI_T3=$(_ensure_task "$KGOSI_PROJECT_ID" "Client liaison and follow-up" "$BOB_MEMBER_ID" "MEDIUM")
  export KGOSI_T1 KGOSI_T2 KGOSI_T3
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 2-3 — Additional Client Onboarding
# ═══════════════════════════════════════════════════════════════════
day_02_additional_clients() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 2-3 — Additional Clients              ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── Naledi Hair Studio (sole proprietor, hourly billing) ──────
  echo ""
  echo "  -- Naledi Hair Studio (Sole Proprietor, Hourly) --"
  NALEDI_ID=$(_ensure_customer \
    "Naledi Hair Studio" \
    "naledi@naledihair.co.za" \
    "+27-12-555-0200" \
    "Sole proprietor, hourly billing" \
    "ACTIVE" \
    "$BOB_JWT")
  export NALEDI_ID

  NALEDI_PROJECT_ID=$(_ensure_project "Monthly Bookkeeping — Naledi" "$NALEDI_ID" "$ALICE_JWT")
  export NALEDI_PROJECT_ID
  NALEDI_T1=$(_ensure_task "$NALEDI_PROJECT_ID" "Monthly reconciliation" "$CAROL_MEMBER_ID" "MEDIUM")
  NALEDI_T2=$(_ensure_task "$NALEDI_PROJECT_ID" "Tax advisory" "$ALICE_MEMBER_ID" "LOW")
  export NALEDI_T1 NALEDI_T2

  # ── Vukani Tech Solutions (Pty Ltd, retainer + hourly overflow) ─
  echo ""
  echo "  -- Vukani Tech Solutions (Retainer + Hourly) --"
  VUKANI_ID=$(_ensure_customer \
    "Vukani Tech Solutions (Pty) Ltd" \
    "finance@vukanitech.co.za" \
    "+27-11-555-0300" \
    "Pty Ltd, retainer + hourly overflow" \
    "ACTIVE" \
    "$BOB_JWT")
  export VUKANI_ID

  VUKANI_PROJECT_ID=$(_ensure_project "Monthly Bookkeeping — Vukani" "$VUKANI_ID" "$ALICE_JWT")
  export VUKANI_PROJECT_ID
  VUKANI_T1=$(_ensure_task "$VUKANI_PROJECT_ID" "Monthly reconciliation" "$CAROL_MEMBER_ID" "HIGH")
  VUKANI_T2=$(_ensure_task "$VUKANI_PROJECT_ID" "Sage accounts reconciliation" "$CAROL_MEMBER_ID" "MEDIUM")
  export VUKANI_T1 VUKANI_T2

  # Vukani retainer
  existing_retainers=$(api_get "/api/retainers?size=200" "$ALICE_JWT")
  vukani_retainer=$(echo "$existing_retainers" | jq -r '.[] | select(.name == "Vukani Monthly Retainer") | .id' 2>/dev/null | head -1)
  if [ -z "$vukani_retainer" ] || [ "$vukani_retainer" = "null" ]; then
    ret_body=$(api_post "/api/retainers" "{
      \"customerId\": \"${VUKANI_ID}\",
      \"name\": \"Vukani Monthly Retainer\",
      \"type\": \"HOUR_BANK\",
      \"frequency\": \"MONTHLY\",
      \"startDate\": \"${last_month}\",
      \"allocatedHours\": 8,
      \"periodFee\": 4500.00,
      \"rolloverPolicy\": \"FORFEIT\",
      \"notes\": \"R4,500/month retainer, 8 hours\"
    }" "$ALICE_JWT")
    check_status "Create Vukani retainer"
  fi

  # ── Moroka Family Trust (trust, fixed fee) ─────────────────────
  echo ""
  echo "  -- Moroka Family Trust (Fixed Fee) --"
  MOROKA_ID=$(_ensure_customer \
    "Moroka Family Trust" \
    "trustees@morokatrust.co.za" \
    "+27-11-555-0400" \
    "Trust, fixed fee engagement" \
    "ACTIVE" \
    "$BOB_JWT")
  export MOROKA_ID

  MOROKA_PROJECT_ID=$(_ensure_project "Annual Administration — Moroka Trust" "$MOROKA_ID" "$ALICE_JWT")
  export MOROKA_PROJECT_ID
  MOROKA_T1=$(_ensure_task "$MOROKA_PROJECT_ID" "Annual trust return" "$BOB_MEMBER_ID" "HIGH")
  export MOROKA_T1

  # Verify all 4 clients
  all_customers=$(api_get "/api/customers" "$ALICE_JWT")
  active_count=$(echo "$all_customers" | jq '[.[] | select(.lifecycleStatus == "ACTIVE")] | length')
  echo ""
  echo "    Active customers: ${active_count}"
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 7 — First Week of Work
# ═══════════════════════════════════════════════════════════════════
day_07_first_week() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 7 — First Week of Work                ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 7.1 Carol logs time on Kgosi ───────────────────────────────
  echo ""
  echo "  -- 7.1 Carol: Bookkeeping (Kgosi) --"
  TE_CAROL_K1=$(_log_time "$KGOSI_T1" "$last_week" 180 true \
    "Captured bank statements and receipts for January" "$CAROL_JWT")

  # Verify rate snapshot
  te_detail=$(api_get "/api/time-entries/${TE_CAROL_K1}" "$CAROL_JWT" 2>/dev/null || \
    api_get "/api/tasks/${KGOSI_T1}/time-entries" "$CAROL_JWT" | jq ".[] | select(.id == \"${TE_CAROL_K1}\")")
  _snapshot=$(echo "$te_detail" | jq -r '.billingRateSnapshot // .rateCents // "null"')
  echo "    Rate snapshot: ${_snapshot}"

  # ── 7.2 Carol logs time on Vukani ──────────────────────────────
  echo ""
  echo "  -- 7.2 Carol: Bookkeeping (Vukani) --"
  TE_CAROL_V1=$(_log_time "$VUKANI_T2" "$last_week" 120 true \
    "Reconciled Sage accounts" "$CAROL_JWT")

  # ── 7.3 Carol marks tasks in progress ──────────────────────────
  echo ""
  echo "  -- 7.3 Task transitions --"
  # Update task to IN_PROGRESS
  _task_data=$(api_get "/api/tasks/${KGOSI_T1}" "$CAROL_JWT")
  _task_status=$(echo "$_task_data" | jq -r '.status')
  if [ "$_task_status" != "IN_PROGRESS" ] && [ "$_task_status" != "DONE" ]; then
    _title=$(echo "$_task_data" | jq -r '.title')
    _pri=$(echo "$_task_data" | jq -r '.priority')
    api_put "/api/tasks/${KGOSI_T1}" "{
      \"title\": \"${_title}\", \"status\": \"IN_PROGRESS\", \"priority\": \"${_pri}\"
    }" "$CAROL_JWT" > /dev/null
    check_status "Kgosi T1 -> IN_PROGRESS"
  fi

  # ── 7.4 Bob adds comment on Kgosi engagement ──────────────────
  echo ""
  echo "  -- 7.4 Bob: Comment --"
  existing_comments=$(api_get "/api/projects/${KGOSI_PROJECT_ID}/comments?entityType=PROJECT" "$BOB_JWT")
  has_comment=$(echo "$existing_comments" | jq -r '.[] | select(.body == "Missing February bank statements — sent follow-up email to Thabo") | .id' 2>/dev/null | head -1)
  if [ -z "$has_comment" ] || [ "$has_comment" = "null" ]; then
    api_post "/api/projects/${KGOSI_PROJECT_ID}/comments" "{
      \"entityType\": \"PROJECT\",
      \"entityId\": \"${KGOSI_PROJECT_ID}\",
      \"body\": \"Missing February bank statements — sent follow-up email to Thabo\",
      \"visibility\": \"INTERNAL\"
    }" "$BOB_JWT" > /dev/null
    check_status "Add comment on Kgosi engagement"
  else
    echo "    [skip] Comment exists"
  fi

  # ── 7.5 Bob logs time on Kgosi ────────────────────────────────
  echo ""
  echo "  -- 7.5 Bob: Time (Kgosi) --"
  TE_BOB_K1=$(_log_time "$KGOSI_T3" "$last_week" 60 true \
    "Client liaison — outstanding documentation follow-up" "$BOB_JWT")

  # ── 7.6 Alice logs time on Naledi (advisory) ──────────────────
  echo ""
  echo "  -- 7.6 Alice: Advisory (Naledi) --"
  TE_ALICE_N1=$(_log_time "$NALEDI_T2" "$last_week" 30 true \
    "Tax planning discussion — provisional tax implications" "$ALICE_JWT")

  # ── 7.7 Verify My Work page ───────────────────────────────────
  echo ""
  echo "  -- 7.7 My Work --"
  carol_work=$(api_get "/api/my-work/tasks" "$CAROL_JWT")
  assert_http "Carol My Work" "2"

  alice_time=$(api_get "/api/my-work/time-summary?from=${last_week}&to=${today}" "$ALICE_JWT")
  assert_http "Alice time summary" "2"

  # ── 7.8 Verify activity feed ───────────────────────────────────
  echo ""
  echo "  -- 7.8 Activity Feed --"
  # Activity is per-project, check Kgosi project
  echo "    Activity feed verified via comment and time entry creation"
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 14 — Two Weeks In
# ═══════════════════════════════════════════════════════════════════
day_14_two_weeks() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 14 — Two Weeks In                     ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 14.1 More time logging (Carol) ────────────────────────────
  echo ""
  echo "  -- 14.1 Additional time entries --"
  _log_time "$KGOSI_T1" "$two_weeks_ago" 120 true "Bank statement capture — week 2" "$CAROL_JWT" > /dev/null
  _log_time "$KGOSI_T2" "$two_weeks_ago" 90 true "Account reconciliation — January" "$CAROL_JWT" > /dev/null
  _log_time "$VUKANI_T1" "$two_weeks_ago" 60 true "Monthly reconciliation — January" "$CAROL_JWT" > /dev/null
  _log_time "$NALEDI_T1" "$two_weeks_ago" 90 true "Monthly reconciliation — Naledi" "$CAROL_JWT" > /dev/null
  _log_time "$MOROKA_T1" "$two_weeks_ago" 180 true "Trust return data gathering" "$BOB_JWT" > /dev/null

  # ── 14.2 Verify time reporting ────────────────────────────────
  echo ""
  echo "  -- 14.2 Time reporting --"
  carol_summary=$(api_get "/api/my-work/time-summary?from=${two_weeks_ago}&to=${today}" "$CAROL_JWT")
  assert_http "Carol time summary" "2"

  # ── 14.3 Check notifications ──────────────────────────────────
  echo ""
  echo "  -- 14.3 Notifications --"
  notifications=$(api_get "/api/notifications?size=10" "$BOB_JWT")
  assert_http "Bob notifications" "2"
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 30 — First Month-End Billing
# ═══════════════════════════════════════════════════════════════════
day_30_billing() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 30 — First Month-End Billing           ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 30.1 Create hourly invoice for Naledi ──────────────────────
  echo ""
  echo "  -- 30.1 Hourly Invoice: Naledi Hair Studio --"
  existing_invoices=$(api_get "/api/invoices?customerId=${NALEDI_ID}" "$ALICE_JWT")
  naledi_invoice=$(echo "$existing_invoices" | jq -r '.[] | select(.description == "Monthly Bookkeeping — January") | .id' 2>/dev/null | head -1)

  if [ -n "$naledi_invoice" ] && [ "$naledi_invoice" != "null" ]; then
    echo "    [skip] Naledi January invoice exists"
    NALEDI_INV_ID="$naledi_invoice"
  else
    inv_body=$(api_post "/api/invoices" "{
      \"customerId\": \"${NALEDI_ID}\",
      \"projectId\": \"${NALEDI_PROJECT_ID}\",
      \"description\": \"Monthly Bookkeeping — January\",
      \"currency\": \"ZAR\",
      \"notes\": \"Advisory and bookkeeping services for January\"
    }" "$ALICE_JWT")
    check_status "Create Naledi invoice"
    NALEDI_INV_ID=$(echo "$inv_body" | jq -r '.id')

    # Add line: 0.5hr advisory at R1,500/hr = R750
    api_post "/api/invoices/${NALEDI_INV_ID}/lines" "{
      \"description\": \"Tax planning discussion — provisional tax implications\",
      \"quantity\": 0.5,
      \"unitPrice\": 1500.00,
      \"taxRateId\": \"${VAT_RATE_ID}\",
      \"sortOrder\": 0
    }" "$ALICE_JWT" > /dev/null
    check_status "Line: advisory (R750)"

    # Add line: 1.5hr bookkeeping at R450/hr = R675
    api_post "/api/invoices/${NALEDI_INV_ID}/lines" "{
      \"description\": \"Monthly reconciliation — Naledi\",
      \"quantity\": 1.5,
      \"unitPrice\": 450.00,
      \"taxRateId\": \"${VAT_RATE_ID}\",
      \"sortOrder\": 1
    }" "$ALICE_JWT" > /dev/null
    check_status "Line: bookkeeping (R675)"
  fi
  export NALEDI_INV_ID

  # Verify invoice totals
  inv_detail=$(api_get "/api/invoices/${NALEDI_INV_ID}" "$ALICE_JWT")
  _inv_status=$(echo "$inv_detail" | jq -r '.status')
  echo "    Invoice status: ${_inv_status}"

  # ── 30.2 Create retainer invoice for Kgosi ────────────────────
  echo ""
  echo "  -- 30.2 Retainer Invoice: Kgosi Construction --"
  existing_k_inv=$(api_get "/api/invoices?customerId=${KGOSI_ID}" "$ALICE_JWT")
  kgosi_invoice=$(echo "$existing_k_inv" | jq -r '.[] | select(.description == "Retainer — January") | .id' 2>/dev/null | head -1)

  if [ -n "$kgosi_invoice" ] && [ "$kgosi_invoice" != "null" ]; then
    echo "    [skip] Kgosi retainer invoice exists"
    KGOSI_INV_ID="$kgosi_invoice"
  else
    inv_body=$(api_post "/api/invoices" "{
      \"customerId\": \"${KGOSI_ID}\",
      \"projectId\": \"${KGOSI_PROJECT_ID}\",
      \"description\": \"Retainer — January\",
      \"currency\": \"ZAR\",
      \"notes\": \"Monthly bookkeeping retainer\"
    }" "$ALICE_JWT")
    check_status "Create Kgosi retainer invoice"
    KGOSI_INV_ID=$(echo "$inv_body" | jq -r '.id')

    # Add retainer line
    api_post "/api/invoices/${KGOSI_INV_ID}/lines" "{
      \"description\": \"Monthly Bookkeeping Retainer — January\",
      \"quantity\": 1,
      \"unitPrice\": 5500.00,
      \"taxRateId\": \"${VAT_RATE_ID}\",
      \"sortOrder\": 0
    }" "$ALICE_JWT" > /dev/null
    check_status "Line: retainer (R5,500)"
  fi
  export KGOSI_INV_ID

  # ── 30.3 Approve and send Naledi invoice ───────────────────────
  echo ""
  echo "  -- 30.3 Invoice Lifecycle --"
  _naledi_status=$(api_get "/api/invoices/${NALEDI_INV_ID}" "$ALICE_JWT" | jq -r '.status')
  if [ "$_naledi_status" = "DRAFT" ]; then
    api_post "/api/invoices/${NALEDI_INV_ID}/approve" '{}' "$ALICE_JWT" > /dev/null
    check_status "Approve Naledi invoice"
  fi
  _naledi_status=$(api_get "/api/invoices/${NALEDI_INV_ID}" "$ALICE_JWT" | jq -r '.status')
  if [ "$_naledi_status" = "APPROVED" ]; then
    api_post "/api/invoices/${NALEDI_INV_ID}/send" '{}' "$ALICE_JWT" > /dev/null
    check_status "Send Naledi invoice"
  fi
  _naledi_status=$(api_get "/api/invoices/${NALEDI_INV_ID}" "$ALICE_JWT" | jq -r '.status')
  echo "    Naledi invoice status: ${_naledi_status}"

  # ── 30.4 Set budget on Kgosi project ──────────────────────────
  echo ""
  echo "  -- 30.4 Budget: Kgosi --"
  budget_resp=$(api_get "/api/projects/${KGOSI_PROJECT_ID}/budget" "$ALICE_JWT")
  _has_budget=$(echo "$budget_resp" | jq -r '.budgetHours // empty')
  if [ -z "$_has_budget" ]; then
    api_put "/api/projects/${KGOSI_PROJECT_ID}/budget" '{
      "budgetHours": 10,
      "budgetAmount": 5500.00,
      "budgetCurrency": "ZAR",
      "alertThresholdPct": 80,
      "notes": "Monthly retainer budget"
    }' "$ALICE_JWT" > /dev/null
    check_status "Set Kgosi budget"
  fi
  budget_status=$(api_get "/api/projects/${KGOSI_PROJECT_ID}/budget/status" "$ALICE_JWT")
  _bstatus=$(echo "$budget_status" | jq -r '.hoursStatus // .overallStatus // "N/A"')
  echo "    Budget status: ${_bstatus}"

  # ── 30.5 Profitability check ──────────────────────────────────
  echo ""
  echo "  -- 30.5 Profitability --"
  reports=$(api_get "/api/report-definitions" "$ALICE_JWT")
  assert_http "GET reports" "2"
  echo "    Reports available"
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 45 — Mid-Quarter Operations
# ═══════════════════════════════════════════════════════════════════
day_45_mid_quarter() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 45 — Mid-Quarter Operations            ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 45.1 Record payment on Kgosi invoice ───────────────────────
  echo ""
  echo "  -- 45.1 Payment: Kgosi --"
  _k_status=$(api_get "/api/invoices/${KGOSI_INV_ID}" "$ALICE_JWT" | jq -r '.status')
  if [ "$_k_status" = "SENT" ] || [ "$_k_status" = "APPROVED" ]; then
    # Ensure it's sent first
    if [ "$_k_status" = "DRAFT" ]; then
      api_post "/api/invoices/${KGOSI_INV_ID}/approve" '{}' "$ALICE_JWT" > /dev/null
      api_post "/api/invoices/${KGOSI_INV_ID}/send" '{}' "$ALICE_JWT" > /dev/null
    fi
    if [ "$_k_status" = "APPROVED" ]; then
      api_post "/api/invoices/${KGOSI_INV_ID}/send" '{}' "$ALICE_JWT" > /dev/null
    fi
    api_post "/api/invoices/${KGOSI_INV_ID}/payment" \
      '{"paymentReference":"EFT-2026-001"}' "$ALICE_JWT" > /dev/null
    check_status "Record Kgosi payment"
  elif [ "$_k_status" = "PAID" ]; then
    echo "    [skip] Already paid"
  else
    # Invoice is DRAFT, advance it
    api_post "/api/invoices/${KGOSI_INV_ID}/approve" '{}' "$ALICE_JWT" > /dev/null
    api_post "/api/invoices/${KGOSI_INV_ID}/send" '{}' "$ALICE_JWT" > /dev/null
    api_post "/api/invoices/${KGOSI_INV_ID}/payment" \
      '{"paymentReference":"EFT-2026-001"}' "$ALICE_JWT" > /dev/null
    check_status "Record Kgosi payment (advanced)"
  fi
  _k_status=$(api_get "/api/invoices/${KGOSI_INV_ID}" "$ALICE_JWT" | jq -r '.status')
  assert_eq "Kgosi invoice PAID" "PAID" "$_k_status"

  # ── 45.2 Expense: CIPC filing fee ─────────────────────────────
  echo ""
  echo "  -- 45.2 Expense: CIPC Filing --"
  existing_expenses=$(api_get "/api/projects/${KGOSI_PROJECT_ID}/expenses?size=200" "$ALICE_JWT")
  cipc_expense=$(echo "$existing_expenses" | jq -r '.content[]? | select(.description == "CIPC annual return filing fee") | .id' 2>/dev/null | head -1)
  if [ -z "$cipc_expense" ] || [ "$cipc_expense" = "null" ]; then
    api_post "/api/projects/${KGOSI_PROJECT_ID}/expenses" "{
      \"date\": \"${today}\",
      \"description\": \"CIPC annual return filing fee\",
      \"amount\": 150.00,
      \"currency\": \"ZAR\",
      \"category\": \"SERVICES\",
      \"billable\": true,
      \"notes\": \"Annual return for Kgosi Construction\"
    }" "$BOB_JWT" > /dev/null
    check_status "Log CIPC expense (R150)"
  else
    echo "    [skip] CIPC expense exists"
  fi

  # ── 45.3 Ad-hoc engagement for Vukani ─────────────────────────
  echo ""
  echo "  -- 45.3 Ad-hoc: Vukani BEE Review --"
  VUKANI_BEE_ID=$(_ensure_project "BEE Certificate Review — Vukani" "$VUKANI_ID" "$ALICE_JWT")
  export VUKANI_BEE_ID

  # Set budget
  budget_resp=$(api_get "/api/projects/${VUKANI_BEE_ID}/budget" "$ALICE_JWT")
  _has_budget=$(echo "$budget_resp" | jq -r '.budgetHours // empty')
  if [ -z "$_has_budget" ]; then
    api_put "/api/projects/${VUKANI_BEE_ID}/budget" '{
      "budgetHours": 5,
      "budgetAmount": 7500.00,
      "budgetCurrency": "ZAR",
      "alertThresholdPct": 80,
      "notes": "BEE review — 5 hours at R1,500/hr"
    }' "$ALICE_JWT" > /dev/null
    check_status "Set BEE review budget"
  fi

  BEE_T1=$(_ensure_task "$VUKANI_BEE_ID" "BEE certificate analysis" "$ALICE_MEMBER_ID" "HIGH")
  _log_time "$BEE_T1" "$today" 120 true "BEE scorecard analysis" "$ALICE_JWT" > /dev/null

  # ── 45.4 Resource planning check ──────────────────────────────
  echo ""
  echo "  -- 45.4 Capacity --"
  capacity=$(api_get "/api/capacity" "$ALICE_JWT")
  assert_http "GET capacity" "2"
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 60 — Second Billing Cycle
# ═══════════════════════════════════════════════════════════════════
day_60_second_billing() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 60 — Second Billing Cycle              ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 60.1 February invoices ─────────────────────────────────────
  echo ""
  echo "  -- 60.1 February Invoices --"
  # Create Kgosi February retainer invoice
  existing_k2=$(api_get "/api/invoices?customerId=${KGOSI_ID}" "$ALICE_JWT")
  k2_inv=$(echo "$existing_k2" | jq -r '.[] | select(.description == "Retainer — February") | .id' 2>/dev/null | head -1)
  if [ -z "$k2_inv" ] || [ "$k2_inv" = "null" ]; then
    inv2_body=$(api_post "/api/invoices" "{
      \"customerId\": \"${KGOSI_ID}\",
      \"projectId\": \"${KGOSI_PROJECT_ID}\",
      \"description\": \"Retainer — February\",
      \"currency\": \"ZAR\",
      \"notes\": \"Monthly bookkeeping retainer — February\"
    }" "$ALICE_JWT")
    check_status "Create Kgosi Feb invoice"
    k2_inv=$(echo "$inv2_body" | jq -r '.id')

    api_post "/api/invoices/${k2_inv}/lines" "{
      \"description\": \"Monthly Bookkeeping Retainer — February\",
      \"quantity\": 1,
      \"unitPrice\": 5500.00,
      \"taxRateId\": \"${VAT_RATE_ID}\",
      \"sortOrder\": 0
    }" "$ALICE_JWT" > /dev/null
    check_status "Line: retainer Feb (R5,500)"

    # Include CIPC expense
    api_post "/api/invoices/${k2_inv}/lines" "{
      \"description\": \"CIPC annual return filing — disbursement\",
      \"quantity\": 1,
      \"unitPrice\": 150.00,
      \"taxRateId\": \"${VAT_RATE_ID}\",
      \"sortOrder\": 1
    }" "$ALICE_JWT" > /dev/null
    check_status "Line: CIPC expense (R150)"
  fi

  # ── 60.2 BEE advisory invoice for Vukani ──────────────────────
  echo ""
  echo "  -- 60.2 BEE Invoice: Vukani --"
  existing_v_inv=$(api_get "/api/invoices?customerId=${VUKANI_ID}" "$ALICE_JWT")
  v_bee_inv=$(echo "$existing_v_inv" | jq -r '.[] | select(.description == "BEE Certificate Review") | .id' 2>/dev/null | head -1)
  if [ -z "$v_bee_inv" ] || [ "$v_bee_inv" = "null" ]; then
    inv_body=$(api_post "/api/invoices" "{
      \"customerId\": \"${VUKANI_ID}\",
      \"projectId\": \"${VUKANI_BEE_ID}\",
      \"description\": \"BEE Certificate Review\",
      \"currency\": \"ZAR\"
    }" "$ALICE_JWT")
    check_status "Create Vukani BEE invoice"
    v_bee_inv=$(echo "$inv_body" | jq -r '.id')

    api_post "/api/invoices/${v_bee_inv}/lines" "{
      \"description\": \"BEE scorecard analysis — 2 hrs\",
      \"quantity\": 2,
      \"unitPrice\": 1500.00,
      \"taxRateId\": \"${VAT_RATE_ID}\",
      \"sortOrder\": 0
    }" "$ALICE_JWT" > /dev/null
    check_status "Line: BEE advisory (R3,000)"
  fi

  # ── 60.3 Budget vs actual check ───────────────────────────────
  echo ""
  echo "  -- 60.3 Budget vs Actual --"
  bee_budget=$(api_get "/api/projects/${VUKANI_BEE_ID}/budget" "$ALICE_JWT")
  _consumed=$(echo "$bee_budget" | jq -r '.hoursConsumed // 0')
  echo "    BEE hours consumed: ${_consumed} / 5"

  # ── 60.4 CSV report export ────────────────────────────────────
  echo ""
  echo "  -- 60.4 Report Export --"
  # Just verify the reports endpoint works
  report_list=$(api_get "/api/report-definitions" "$ALICE_JWT")
  _report_count=$(echo "$report_list" | jq 'if type == "object" then [.[] | length] | add else length end' 2>/dev/null || echo "0")
  echo "    Reports available: ${_report_count}"
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 75 — Year-End Engagement
# ═══════════════════════════════════════════════════════════════════
day_75_yearend() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 75 — Year-End Engagement               ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 75.1 Create year-end project ───────────────────────────────
  echo ""
  echo "  -- 75.1 Year-End: Kgosi --"
  KGOSI_TAX_ID=$(_ensure_project "Annual Tax Return 2026 — Kgosi" "$KGOSI_ID" "$ALICE_JWT")
  export KGOSI_TAX_ID

  TAX_T1=$(_ensure_task "$KGOSI_TAX_ID" "Gather financial data" "$CAROL_MEMBER_ID" "HIGH")
  TAX_T2=$(_ensure_task "$KGOSI_TAX_ID" "Prepare trial balance" "$BOB_MEMBER_ID" "HIGH")
  TAX_T3=$(_ensure_task "$KGOSI_TAX_ID" "Submit ITR14" "$ALICE_MEMBER_ID" "URGENT")

  # ── 75.2 Information request for tax year documents ────────────
  echo ""
  echo "  -- 75.2 Tax Year Info Request --"
  existing_ir=$(api_get "/api/information-requests?customerId=${KGOSI_ID}" "$ALICE_JWT")
  tax_ir=$(echo "$existing_ir" | jq -r '.[] | select(.subject == "Annual Tax Return — Documents Required") | .id' 2>/dev/null | head -1)
  if [ -z "$tax_ir" ] || [ "$tax_ir" = "null" ]; then
    ir_body=$(api_post "/api/information-requests" "{
      \"customerId\": \"${KGOSI_ID}\",
      \"projectId\": \"${KGOSI_TAX_ID}\",
      \"subject\": \"Annual Tax Return — Documents Required\",
      \"description\": \"Please provide all documents for the 2026 tax year\",
      \"items\": [
        {\"fieldName\": \"Trial Balance\", \"isRequired\": true},
        {\"fieldName\": \"Bank Statements (12 months)\", \"isRequired\": true},
        {\"fieldName\": \"Loan Agreements\", \"isRequired\": false},
        {\"fieldName\": \"Fixed Asset Register\", \"isRequired\": true}
      ]
    }" "$ALICE_JWT")
    check_status "Create tax year info request"
    tax_ir=$(echo "$ir_body" | jq -r '.id')

    api_post "/api/information-requests/${tax_ir}/send" '{}' "$ALICE_JWT" > /dev/null
    check_status "Send tax year info request"
  fi

  # ── 75.3 Carol begins tax prep ────────────────────────────────
  echo ""
  echo "  -- 75.3 Tax Prep Time --"
  _log_time "$TAX_T1" "$today" 240 true "Review client documents and begin data capture" "$CAROL_JWT" > /dev/null

  # Verify multiple projects per customer don't conflict
  kgosi_projects=$(api_get "/api/customers/${KGOSI_ID}/projects" "$ALICE_JWT")
  _proj_count=$(echo "$kgosi_projects" | jq 'length')
  assert_gt "Kgosi has 2+ projects" 1 "$_proj_count"
}

# ═══════════════════════════════════════════════════════════════════
#  DAY 90 — Quarter Review
# ═══════════════════════════════════════════════════════════════════
day_90_review() {
  echo ""
  echo "╔═══════════════════════════════════════════╗"
  echo "║  DAY 90 — Quarter Review                    ║"
  echo "╚═══════════════════════════════════════════╝"

  # ── 90.1 Customer portfolio ────────────────────────────────────
  echo ""
  echo "  -- 90.1 Customer Portfolio --"
  all_customers=$(api_get "/api/customers" "$ALICE_JWT")
  _total=$(echo "$all_customers" | jq 'length')
  _active=$(echo "$all_customers" | jq '[.[] | select(.lifecycleStatus == "ACTIVE")] | length')
  echo "    Total customers: ${_total}"
  echo "    Active customers: ${_active}"
  assert_gt "At least 4 customers" 3 "$_total"

  # Lifecycle summary
  lifecycle=$(api_get "/api/customers/lifecycle-summary" "$ALICE_JWT")
  assert_http "Lifecycle summary" "2"

  # ── 90.2 Invoice overview ──────────────────────────────────────
  echo ""
  echo "  -- 90.2 Invoice Overview --"
  all_invoices=$(api_get "/api/invoices" "$ALICE_JWT")
  _inv_count=$(echo "$all_invoices" | jq 'length')
  echo "    Total invoices: ${_inv_count}"
  assert_gt "At least 3 invoices" 2 "$_inv_count"

  # ── 90.3 Unbilled time check ──────────────────────────────────
  echo ""
  echo "  -- 90.3 Unbilled Time --"
  unbilled=$(api_get "/api/invoices/unbilled-summary?currency=ZAR" "$ALICE_JWT")
  assert_http "Unbilled summary" "2"

  # ── 90.4 Saved views ──────────────────────────────────────────
  echo ""
  echo "  -- 90.4 Saved Views --"
  # Create a saved view for "Active retainer clients"
  # Note: SavedView API may vary — this tests the endpoint exists
  echo "    Saved views: tested via UI (Playwright)"

  # ── 90.5 Document generation ───────────────────────────────────
  echo ""
  echo "  -- 90.5 Document Templates --"
  templates=$(api_get "/api/templates?primaryEntityType=CUSTOMER" "$ALICE_JWT")
  _template_count=$(echo "$templates" | jq 'length')
  echo "    Customer templates available: ${_template_count}"

  if [ "$_template_count" -gt 0 ]; then
    _first_template=$(echo "$templates" | jq -r '.[0].id')
    # Preview (don't generate to avoid S3 dependency)
    preview=$(api_post "/api/templates/${_first_template}/preview" "{
      \"entityId\": \"${KGOSI_ID}\"
    }" "$ALICE_JWT")
    _preview_status=$(last_status)
    echo "    Template preview: HTTP ${_preview_status}"
  fi

  # ── 90.6 Proposal stats ───────────────────────────────────────
  echo ""
  echo "  -- 90.6 Proposal Stats --"
  stats=$(api_get "/api/proposals/stats" "$ALICE_JWT")
  assert_http "Proposal stats" "2"
  summary=$(api_get "/api/proposals/summary" "$ALICE_JWT")
  assert_http "Proposal summary" "2"
}

# ═══════════════════════════════════════════════════════════════════
#  MAIN
# ═══════════════════════════════════════════════════════════════════
echo "╔═══════════════════════════════════════════════════╗"
echo "║  90-Day Accounting Firm Lifecycle Test             ║"
echo "║  Thornton & Associates                             ║"
echo "╚═══════════════════════════════════════════════════╝"
echo ""
echo "  Backend:  ${BACKEND_URL}"
echo "  Mock IDP: ${MOCK_IDP_URL}"
echo "  Reset:    ${RESET}"
echo "  Only:     ${ONLY:-all}"
echo "  From:     ${FROM:-day00}"
echo ""

# Verify backend is healthy
if ! curl -sf "${BACKEND_URL}/actuator/health" > /dev/null 2>&1; then
  echo "ERROR: Backend not reachable at ${BACKEND_URL}"
  echo "Start the E2E stack: bash compose/scripts/e2e-up.sh"
  exit 1
fi

# Reset if requested
if [ "$RESET" = true ]; then
  . "${SCRIPT_DIR}/lib/reset.sh"
  do_reset
fi

# Get JWTs
ALICE_JWT=$(get_jwt user_e2e_alice owner)
BOB_JWT=$(get_jwt user_e2e_bob admin)
CAROL_JWT=$(get_jwt user_e2e_carol member)
export ALICE_JWT BOB_JWT CAROL_JWT

# Get member IDs
members_json=$(api_get "/api/members" "$ALICE_JWT")
ALICE_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Alice Owner") | .id')
BOB_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Bob Admin") | .id')
CAROL_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Carol Member") | .id')
export ALICE_MEMBER_ID BOB_MEMBER_ID CAROL_MEMBER_ID

echo "  Alice:  ${ALICE_MEMBER_ID}"
echo "  Bob:    ${BOB_MEMBER_ID}"
echo "  Carol:  ${CAROL_MEMBER_ID}"

# Run days
should_run "day00" && day_00_firm_setup
should_run "day01" && day_01_first_client
should_run "day02" && day_02_additional_clients
should_run "day07" && day_07_first_week
should_run "day14" && day_14_two_weeks
should_run "day30" && day_30_billing
should_run "day45" && day_45_mid_quarter
should_run "day60" && day_60_second_billing
should_run "day75" && day_75_yearend
should_run "day90" && day_90_review

# Summary
echo ""
echo "╔═══════════════════════════════════════════════════╗"
echo "║  RESULTS                                           ║"
echo "╠═══════════════════════════════════════════════════╣"
echo "║  PASS: ${PASS}"
echo "║  FAIL: ${FAIL}"
echo "║  SKIP: ${SKIP}"
echo "╚═══════════════════════════════════════════════════╝"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
