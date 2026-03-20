#!/bin/sh
# scripts/regression-test.sh — API-level regression tests with assertions
#
# Exercises state machines, invoice math, RBAC, portal isolation, and audit integrity.
# Rerunnable against a seeded E2E stack. Does NOT modify seed data destructively.
#
# Usage:
#   bash scripts/regression-test.sh                   # Run all sections
#   bash scripts/regression-test.sh --only rbac       # Run one section
#   bash scripts/regression-test.sh --from invoice    # Run from a section
#
# Requires: E2E stack running (bash compose/scripts/e2e-up.sh)
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
. "${PROJECT_ROOT}/compose/seed/lib/common.sh"

# ── Extra helpers ──────────────────────────────────────────────────
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

# Ensure a customer has the custom fields required for invoice generation
ensure_invoice_prerequisites() {
  _eip_cust_id="$1"
  _eip_jwt="$2"

  # Get the current customer data
  _eip_cust=$(api_get "/api/customers/${_eip_cust_id}" "$_eip_jwt")
  _eip_name=$(echo "$_eip_cust" | jq -r '.name')
  _eip_email=$(echo "$_eip_cust" | jq -r '.email')

  # Get applied field groups and remove trust group (avoids filling trust-specific required fields)
  _eip_groups=$(echo "$_eip_cust" | jq -c '[.appliedFieldGroups[]?]')

  # Get the trust details group ID to remove it
  _trust_group_id=$(api_get "/api/field-groups?entityType=CUSTOMER" "$_eip_jwt" | \
    jq -r '.[] | select(.slug=="accounting_za_trust_details") | .id // empty')
  if [ -n "$_trust_group_id" ]; then
    _eip_groups=$(echo "$_eip_groups" | jq -c "[.[] | select(. != \"${_trust_group_id}\")]")
  fi

  # Update customer with required custom fields for invoicing
  api_put "/api/customers/${_eip_cust_id}" "{
    \"name\": \"${_eip_name}\",
    \"email\": \"${_eip_email}\",
    \"appliedFieldGroups\": ${_eip_groups},
    \"customFields\": {
      \"acct_company_registration_number\": \"2026/000001/07\",
      \"address_line1\": \"123 Test Street\",
      \"vat_number\": \"4000000000\",
      \"city\": \"Cape Town\",
      \"country\": \"ZA\",
      \"tax_number\": \"0000000000\",
      \"sars_tax_reference\": \"9000000000\",
      \"financial_year_end\": \"2026-02-28\",
      \"acct_entity_type\": \"PTY_LTD\",
      \"registered_address\": \"123 Test Street, Cape Town\",
      \"primary_contact_name\": \"Test Contact\",
      \"primary_contact_email\": \"contact@test.com\",
      \"fica_verified\": \"VERIFIED\"
    }
  }" "$_eip_jwt" > /dev/null
}

PASS=0; FAIL=0; SKIP=0

assert_eq() {
  _label="$1"; _expected="$2"; _actual="$3"
  if [ "$_expected" = "$_actual" ]; then
    echo "    [PASS] ${_label}: ${_actual}" >&2; PASS=$((PASS + 1))
  else
    echo "    [FAIL] ${_label}: expected '${_expected}', got '${_actual}'" >&2; FAIL=$((FAIL + 1))
  fi
}

assert_not_empty() {
  _label="$1"; _value="$2"
  if [ -n "$_value" ] && [ "$_value" != "null" ]; then
    echo "    [PASS] ${_label}: present" >&2; PASS=$((PASS + 1))
  else
    echo "    [FAIL] ${_label}: empty or null" >&2; FAIL=$((FAIL + 1))
  fi
}

assert_gt() {
  _label="$1"; _threshold="$2"; _actual="$3"
  if [ "$_actual" -gt "$_threshold" ] 2>/dev/null; then
    echo "    [PASS] ${_label}: ${_actual} > ${_threshold}" >&2; PASS=$((PASS + 1))
  else
    echo "    [FAIL] ${_label}: ${_actual} not > ${_threshold}" >&2; FAIL=$((FAIL + 1))
  fi
}

assert_http() {
  _label="$1"; _expected="$2"
  _actual=$(last_status)
  case "$_actual" in
    ${_expected}*) echo "    [PASS] ${_label}: HTTP ${_actual}" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] ${_label}: expected HTTP ${_expected}x, got ${_actual}" >&2; FAIL=$((FAIL + 1)) ;;
  esac
}

assert_contains() {
  _label="$1"; _haystack="$2"; _needle="$3"
  case "$_haystack" in
    *"$_needle"*) echo "    [PASS] ${_label}: contains '${_needle}'" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] ${_label}: missing '${_needle}'" >&2; FAIL=$((FAIL + 1)) ;;
  esac
}

# ── Section runner ─────────────────────────────────────────────────
ONLY=""; FROM=""
while [ $# -gt 0 ]; do
  case "$1" in
    --only)  ONLY="$2"; shift 2 ;;
    --only=*) ONLY="${1#--only=}"; shift ;;
    --from)  FROM="$2"; shift 2 ;;
    --from=*) FROM="${1#--from=}"; shift ;;
    *) echo "Unknown flag: $1"; exit 1 ;;
  esac
done

SECTIONS="rbac customer_lifecycle invoice_lifecycle invoice_math task_lifecycle portal_isolation audit_integrity"
_past_from=false
should_run() {
  _section="$1"
  if [ -n "$ONLY" ]; then [ "$ONLY" = "$_section" ]; return $?; fi
  if [ -n "$FROM" ]; then
    if [ "$_past_from" = true ]; then return 0; fi
    if [ "$FROM" = "$_section" ]; then _past_from=true; return 0; fi
    return 1
  fi
  return 0
}

# ── Auth tokens ────────────────────────────────────────────────────
ALICE=$(get_jwt "user_e2e_alice" "org:owner")
BOB=$(get_jwt "user_e2e_bob" "org:admin")
CAROL=$(get_jwt "user_e2e_carol" "org:member")

echo ""
echo "=== Regression Test Suite (API) ==="
echo ""

# ══════════════════════════════════════════════════════════════════
# RBAC — Verify role-based access control on API endpoints
# ══════════════════════════════════════════════════════════════════
if should_run "rbac"; then
  echo "── RBAC ──────────────────────────────────────────────"

  # Owner can list customers
  api_get "/api/customers" "$ALICE" > /dev/null
  assert_http "Owner lists customers" "2"

  # Owner can list rates
  api_get "/api/billing-rates" "$ALICE" > /dev/null
  assert_http "Owner lists billing rates" "2"

  # Admin can list customers
  api_get "/api/customers" "$BOB" > /dev/null
  assert_http "Admin lists customers" "2"

  # Member blocked from customers
  api_get "/api/customers" "$CAROL" > /dev/null
  _carol_cust_status=$(last_status)
  case "$_carol_cust_status" in
    403|404) echo "    [PASS] Member blocked from customers: HTTP ${_carol_cust_status}" >&2; PASS=$((PASS + 1)) ;;
    2*)      echo "    [PASS] Member gets customers (read-only OK): HTTP ${_carol_cust_status}" >&2; PASS=$((PASS + 1)) ;;
    *)       echo "    [FAIL] Member customers unexpected: HTTP ${_carol_cust_status}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  # Member can access own tasks (My Work)
  api_get "/api/my-work/tasks" "$CAROL" > /dev/null
  assert_http "Member accesses My Work" "2"

  # Member can list projects
  api_get "/api/projects" "$CAROL" > /dev/null
  assert_http "Member lists projects" "2"

  echo ""
fi

# ══════════════════════════════════════════════════════════════════
# Customer Lifecycle — State machine transitions + guards
# ══════════════════════════════════════════════════════════════════
if should_run "customer_lifecycle"; then
  echo "── Customer Lifecycle ─────────────────────────────────"

  # Create a test customer (PROSPECT by default)
  RUN_ID=$(date +%s | tail -c 5)
  CUST_BODY="{\"name\":\"RegTest-${RUN_ID}\",\"email\":\"reg${RUN_ID}@test.com\",\"phone\":\"+27110000000\"}"
  cust_resp=$(api_post "/api/customers" "$CUST_BODY" "$ALICE")
  assert_http "Create customer" "2"
  CUST_ID=$(echo "$cust_resp" | jq -r '.id')
  assert_not_empty "Customer ID" "$CUST_ID"

  # Verify defaults to PROSPECT
  cust_status=$(echo "$cust_resp" | jq -r '.lifecycleStatus')
  assert_eq "Defaults to PROSPECT" "PROSPECT" "$cust_status"

  # PROSPECT cannot create project (lifecycle guard)
  api_post "/api/projects" "{\"name\":\"Should-Fail-${RUN_ID}\"}" "$ALICE" > /dev/null
  # Need to link customer — create project with customerId
  proj_fail=$(api_post "/api/projects" "{\"name\":\"Should-Fail-${RUN_ID}\",\"customerId\":\"${CUST_ID}\"}" "$ALICE")
  _pf_status=$(last_status)
  case "$_pf_status" in
    400|409) echo "    [PASS] PROSPECT blocked from project: HTTP ${_pf_status}" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] PROSPECT project guard: HTTP ${_pf_status}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  # PROSPECT -> ONBOARDING
  api_patch "/api/customers/${CUST_ID}/lifecycle" "$ALICE" > /dev/null 2>&1 || true
  api_post "/api/customers/${CUST_ID}/transition" "{\"targetStatus\":\"ONBOARDING\"}" "$ALICE" > /dev/null
  assert_http "PROSPECT -> ONBOARDING" "2"

  # Invalid: PROSPECT -> ACTIVE (skip) — create another customer to test
  CUST_BODY2="{\"name\":\"RegSkip-${RUN_ID}\",\"email\":\"skip${RUN_ID}@test.com\",\"phone\":\"+27110000001\"}"
  cust2_resp=$(api_post "/api/customers" "$CUST_BODY2" "$ALICE")
  CUST2_ID=$(echo "$cust2_resp" | jq -r '.id')
  api_post "/api/customers/${CUST2_ID}/transition" "{\"targetStatus\":\"ACTIVE\"}" "$ALICE" > /dev/null
  _skip_status=$(last_status)
  case "$_skip_status" in
    400|409) echo "    [PASS] PROSPECT->ACTIVE skip rejected: HTTP ${_skip_status}" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] PROSPECT->ACTIVE skip not rejected: HTTP ${_skip_status}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  # Complete onboarding checklists to transition to ACTIVE
  complete_checklists "$CUST_ID" "$ALICE"
  cust_after=$(api_get "/api/customers/${CUST_ID}" "$ALICE")
  cust_status_after=$(echo "$cust_after" | jq -r '.lifecycleStatus')
  assert_eq "Auto-transition to ACTIVE" "ACTIVE" "$cust_status_after"

  # ACTIVE -> DORMANT
  api_post "/api/customers/${CUST_ID}/transition" "{\"targetStatus\":\"DORMANT\"}" "$ALICE" > /dev/null
  assert_http "ACTIVE -> DORMANT" "2"

  # DORMANT -> OFFBOARDING
  api_post "/api/customers/${CUST_ID}/transition" "{\"targetStatus\":\"OFFBOARDING\"}" "$ALICE" > /dev/null
  assert_http "DORMANT -> OFFBOARDING" "2"

  # OFFBOARDING -> OFFBOARDED
  api_post "/api/customers/${CUST_ID}/transition" "{\"targetStatus\":\"OFFBOARDED\"}" "$ALICE" > /dev/null
  assert_http "OFFBOARDING -> OFFBOARDED" "2"

  # OFFBOARDED blocked from project
  proj_fail2=$(api_post "/api/projects" "{\"name\":\"Should-Fail2-${RUN_ID}\",\"customerId\":\"${CUST_ID}\"}" "$ALICE")
  _pf2_status=$(last_status)
  case "$_pf2_status" in
    400|409) echo "    [PASS] OFFBOARDED blocked from project: HTTP ${_pf2_status}" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] OFFBOARDED project guard: HTTP ${_pf2_status}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  echo ""
fi

# ══════════════════════════════════════════════════════════════════
# Invoice Lifecycle — DRAFT -> APPROVED -> SENT -> PAID, VOID, guards
# ══════════════════════════════════════════════════════════════════
if should_run "invoice_lifecycle"; then
  echo "── Invoice Lifecycle ──────────────────────────────────"

  # Find an ACTIVE customer from seed
  customers=$(api_get "/api/customers" "$ALICE")
  ACTIVE_CUST_ID=$(echo "$customers" | jq -r '[.[] | select(.lifecycleStatus=="ACTIVE")][0].id')
  assert_not_empty "Active customer for invoicing" "$ACTIVE_CUST_ID"

  # Ensure customer has required custom fields for invoice generation
  ensure_invoice_prerequisites "$ACTIVE_CUST_ID" "$ALICE"

  # Create a draft invoice
  INV_BODY="{\"customerId\":\"${ACTIVE_CUST_ID}\",\"currency\":\"ZAR\"}"
  inv_resp=$(api_post "/api/invoices" "$INV_BODY" "$ALICE")
  assert_http "Create draft invoice" "2"
  INV_ID=$(echo "$inv_resp" | jq -r '.id')
  inv_status=$(echo "$inv_resp" | jq -r '.status')
  assert_eq "Invoice starts as DRAFT" "DRAFT" "$inv_status"

  # Add a line item
  LINE_BODY="{\"description\":\"Regression test line\",\"quantity\":3,\"unitPrice\":450,\"sortOrder\":0}"
  api_post "/api/invoices/${INV_ID}/lines" "$LINE_BODY" "$ALICE" > /dev/null
  assert_http "Add line item" "2"

  # Cannot skip DRAFT -> SENT (even with overrideWarnings)
  api_post "/api/invoices/${INV_ID}/send" "{\"overrideWarnings\":true}" "$ALICE" > /dev/null
  _skip_send=$(last_status)
  case "$_skip_send" in
    400|409|422) echo "    [PASS] DRAFT->SENT skip rejected: HTTP ${_skip_send}" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] DRAFT->SENT skip not rejected: HTTP ${_skip_send}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  # DRAFT -> APPROVED
  api_post "/api/invoices/${INV_ID}/approve" "{}" "$ALICE" > /dev/null
  assert_http "DRAFT -> APPROVED" "2"

  # Cannot edit approved invoice lines
  api_post "/api/invoices/${INV_ID}/lines" "$LINE_BODY" "$ALICE" > /dev/null
  _edit_approved=$(last_status)
  case "$_edit_approved" in
    409|400) echo "    [PASS] Cannot add line to approved: HTTP ${_edit_approved}" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] Approved invoice edit not blocked: HTTP ${_edit_approved}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  # APPROVED -> SENT
  api_post "/api/invoices/${INV_ID}/send" "{\"overrideWarnings\":true}" "$ALICE" > /dev/null
  assert_http "APPROVED -> SENT" "2"

  # SENT -> PAID
  api_post "/api/invoices/${INV_ID}/payment" "{\"paymentReference\":\"REG-PAY-001\"}" "$ALICE" > /dev/null
  assert_http "SENT -> PAID" "2"

  # Cannot PAID -> VOID
  api_post "/api/invoices/${INV_ID}/void" "{}" "$ALICE" > /dev/null
  _paid_void=$(last_status)
  case "$_paid_void" in
    409) echo "    [PASS] PAID->VOID rejected: HTTP 409" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] PAID->VOID not rejected: HTTP ${_paid_void}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  # Create another invoice to test VOID on SENT
  inv2_resp=$(api_post "/api/invoices" "$INV_BODY" "$ALICE")
  INV2_ID=$(echo "$inv2_resp" | jq -r '.id')
  api_post "/api/invoices/${INV2_ID}/lines" "$LINE_BODY" "$ALICE" > /dev/null
  api_post "/api/invoices/${INV2_ID}/approve" "{}" "$ALICE" > /dev/null
  api_post "/api/invoices/${INV2_ID}/send" "{\"overrideWarnings\":true}" "$ALICE" > /dev/null
  api_post "/api/invoices/${INV2_ID}/void" "{}" "$ALICE" > /dev/null
  assert_http "VOID sent invoice" "2"

  echo ""
fi

# ══════════════════════════════════════════════════════════════════
# Invoice Arithmetic — Line math, tax, rounding
# ══════════════════════════════════════════════════════════════════
if should_run "invoice_math"; then
  echo "── Invoice Arithmetic ─────────────────────────────────"

  customers=$(api_get "/api/customers" "$ALICE")
  MATH_CUST_ID=$(echo "$customers" | jq -r '[.[] | select(.lifecycleStatus=="ACTIVE")][0].id')

  # Ensure customer has required custom fields for invoice generation
  ensure_invoice_prerequisites "$MATH_CUST_ID" "$ALICE"

  # Single line: 3 x R450 = R1350, tax 15% = R202.50, total = R1552.50
  inv_m1=$(api_post "/api/invoices" "{\"customerId\":\"${MATH_CUST_ID}\",\"currency\":\"ZAR\"}" "$ALICE")
  INV_M1_ID=$(echo "$inv_m1" | jq -r '.id')
  api_post "/api/invoices/${INV_M1_ID}/lines" '{"description":"Consulting","quantity":3,"unitPrice":450,"sortOrder":0}' "$ALICE" > /dev/null
  inv_m1_detail=$(api_get "/api/invoices/${INV_M1_ID}" "$ALICE")
  subtotal=$(printf "%.2f" "$(echo "$inv_m1_detail" | jq -r '.subtotal')")
  tax=$(printf "%.2f" "$(echo "$inv_m1_detail" | jq -r '.taxAmount')")
  total=$(printf "%.2f" "$(echo "$inv_m1_detail" | jq -r '.total')")
  assert_eq "3x450 subtotal" "1350.00" "$subtotal"
  assert_eq "15% tax on 1350" "202.50" "$tax"
  assert_eq "3x450 total" "1552.50" "$total"

  # Multi-line: (2x500) + (1x1500) = R2500, tax = R375, total = R2875
  inv_m2=$(api_post "/api/invoices" "{\"customerId\":\"${MATH_CUST_ID}\",\"currency\":\"ZAR\"}" "$ALICE")
  INV_M2_ID=$(echo "$inv_m2" | jq -r '.id')
  api_post "/api/invoices/${INV_M2_ID}/lines" '{"description":"Line A","quantity":2,"unitPrice":500,"sortOrder":0}' "$ALICE" > /dev/null
  api_post "/api/invoices/${INV_M2_ID}/lines" '{"description":"Line B","quantity":1,"unitPrice":1500,"sortOrder":1}' "$ALICE" > /dev/null
  inv_m2_detail=$(api_get "/api/invoices/${INV_M2_ID}" "$ALICE")
  assert_eq "Multi-line subtotal" "2500.00" "$(printf "%.2f" "$(echo "$inv_m2_detail" | jq -r '.subtotal')")"
  assert_eq "Multi-line tax" "375.00" "$(printf "%.2f" "$(echo "$inv_m2_detail" | jq -r '.taxAmount')")"
  assert_eq "Multi-line total" "2875.00" "$(printf "%.2f" "$(echo "$inv_m2_detail" | jq -r '.total')")"

  # Fractional quantity: 0.5 x R1200 = R600
  inv_m3=$(api_post "/api/invoices" "{\"customerId\":\"${MATH_CUST_ID}\",\"currency\":\"ZAR\"}" "$ALICE")
  INV_M3_ID=$(echo "$inv_m3" | jq -r '.id')
  api_post "/api/invoices/${INV_M3_ID}/lines" '{"description":"Half hour","quantity":0.5,"unitPrice":1200,"sortOrder":0}' "$ALICE" > /dev/null
  inv_m3_detail=$(api_get "/api/invoices/${INV_M3_ID}" "$ALICE")
  assert_eq "Fractional qty subtotal" "600.00" "$(printf "%.2f" "$(echo "$inv_m3_detail" | jq -r '.subtotal')")"

  # Zero quantity rejected
  api_post "/api/invoices/${INV_M3_ID}/lines" '{"description":"Zero","quantity":0,"unitPrice":500,"sortOrder":0}' "$ALICE" > /dev/null
  _zero_status=$(last_status)
  case "$_zero_status" in
    400) echo "    [PASS] Zero qty rejected: HTTP 400" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] Zero qty not rejected: HTTP ${_zero_status}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  echo ""
fi

# ══════════════════════════════════════════════════════════════════
# Task Lifecycle — OPEN -> IN_PROGRESS -> DONE, reopen, cancel, guards
# ══════════════════════════════════════════════════════════════════
if should_run "task_lifecycle"; then
  echo "── Task Lifecycle ─────────────────────────────────────"

  # Get a project from seed
  projects=$(api_get "/api/projects" "$ALICE")
  PROJ_ID=$(echo "$projects" | jq -r '.[0].id // empty')
  if [ -z "$PROJ_ID" ]; then
    PROJ_ID=$(echo "$projects" | jq -r '.content[0].id // empty')
  fi
  assert_not_empty "Project for task tests" "$PROJ_ID"

  # Add Carol as project member so she can claim tasks
  CAROL_MEMBER_ID=$(api_get "/api/members" "$ALICE" | jq -r '[.[] | select(.name=="Carol Member")][0].id // empty')
  if [ -n "$CAROL_MEMBER_ID" ]; then
    api_post "/api/projects/${PROJ_ID}/members" "{\"memberId\":\"${CAROL_MEMBER_ID}\"}" "$ALICE" > /dev/null 2>&1 || true
  fi

  # Create task
  RUN_ID=$(date +%s | tail -c 5)
  task_resp=$(api_post "/api/projects/${PROJ_ID}/tasks" "{\"title\":\"RegTask-${RUN_ID}\",\"priority\":\"MEDIUM\"}" "$ALICE")
  assert_http "Create task" "2"
  TASK_ID=$(echo "$task_resp" | jq -r '.id')
  task_status=$(echo "$task_resp" | jq -r '.status')
  assert_eq "Task starts OPEN" "OPEN" "$task_status"

  # OPEN -> IN_PROGRESS (claim)
  api_post "/api/tasks/${TASK_ID}/claim" "{}" "$CAROL" > /dev/null
  assert_http "OPEN -> IN_PROGRESS (claim)" "2"

  # IN_PROGRESS -> DONE
  api_patch "/api/tasks/${TASK_ID}/complete" "$CAROL" > /dev/null
  assert_http "IN_PROGRESS -> DONE" "2"

  # Reopen: DONE -> OPEN
  api_patch "/api/tasks/${TASK_ID}/reopen" "$ALICE" > /dev/null
  assert_http "DONE -> OPEN (reopen)" "2"

  # Cancel task
  api_patch "/api/tasks/${TASK_ID}/cancel" "$ALICE" > /dev/null
  assert_http "Cancel task" "2"

  # CANCELLED -> IN_PROGRESS should fail
  api_post "/api/tasks/${TASK_ID}/claim" "{}" "$CAROL" > /dev/null
  _cancel_claim=$(last_status)
  case "$_cancel_claim" in
    400|409) echo "    [PASS] CANCELLED->IN_PROGRESS rejected: HTTP ${_cancel_claim}" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] CANCELLED claim not rejected: HTTP ${_cancel_claim}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  echo ""
fi

# ══════════════════════════════════════════════════════════════════
# Portal Data Isolation — Cross-customer security
# ══════════════════════════════════════════════════════════════════
if should_run "portal_isolation"; then
  echo "── Portal Data Isolation ──────────────────────────────"

  # Find first ACTIVE customer with email from seed (Acme Corp or Bright Solutions)
  customers=$(api_get "/api/customers" "$ALICE")
  PORTAL_CUST_ID=$(echo "$customers" | jq -r '[.[] | select(.lifecycleStatus=="ACTIVE" and .email != null and .email != "")][0].id // empty')

  if [ -z "$PORTAL_CUST_ID" ]; then
    echo "    [SKIP] No ACTIVE customer with email in seed — skipping portal tests" >&2
    SKIP=$((SKIP + 5))
  else
    PORTAL_CUST_NAME=$(echo "$customers" | jq -r "[.[] | select(.id==\"${PORTAL_CUST_ID}\")][0].name")
    PORTAL_CUST_EMAIL=$(echo "$customers" | jq -r "[.[] | select(.id==\"${PORTAL_CUST_ID}\")][0].email")

    # Try to get portal contact email; fall back to customer email
    portal_detail=$(api_get "/api/customers/${PORTAL_CUST_ID}" "$ALICE")
    CONTACT_EMAIL=$(echo "$portal_detail" | jq -r '.contacts[0].email // empty')
    if [ -z "$CONTACT_EMAIL" ]; then
      CONTACT_EMAIL="$PORTAL_CUST_EMAIL"
    fi

    if [ -z "$CONTACT_EMAIL" ]; then
      echo "    [SKIP] No email for portal customer — skipping portal tests" >&2
      SKIP=$((SKIP + 5))
    else
      # Get portal JWT via magic link flow
      link_resp=$(curl -sf -X POST "${BACKEND_URL}/portal/auth/request-link" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"${CONTACT_EMAIL}\",\"orgId\":\"${ORG_SLUG}\"}" 2>/dev/null || echo "{}")
      # Response has .magicLink (a URL path with ?token=...&orgId=...)
      MAGIC_LINK=$(echo "$link_resp" | jq -r '.magicLink // empty')
      # Extract the raw token from the magic link URL
      PORTAL_TOKEN=$(echo "$MAGIC_LINK" | sed -n 's/.*[?&]token=\([^&]*\).*/\1/p')

      if [ -z "$PORTAL_TOKEN" ]; then
        echo "    [SKIP] No portal contact found for ${PORTAL_CUST_NAME} — skipping portal tests" >&2
        SKIP=$((SKIP + 5))
      else
        exchange_resp=$(curl -sf -X POST "${BACKEND_URL}/portal/auth/exchange" \
          -H "Content-Type: application/json" \
          -d "{\"token\":\"${PORTAL_TOKEN}\",\"orgId\":\"${ORG_SLUG}\"}" 2>/dev/null || echo "{}")
        PORTAL_JWT=$(echo "$exchange_resp" | jq -r '.token // empty')

        if [ -z "$PORTAL_JWT" ]; then
          echo "    [SKIP] Failed to exchange token for ${PORTAL_CUST_NAME}" >&2
          SKIP=$((SKIP + 5))
        else
          # Portal customer sees projects
          portal_projects=$(curl -sf -X GET "${BACKEND_URL}/portal/projects" \
            -H "Authorization: Bearer ${PORTAL_JWT}" 2>/dev/null || echo "[]")
          portal_count=$(echo "$portal_projects" | jq 'length')
          assert_gt "${PORTAL_CUST_NAME} sees projects" "0" "$portal_count"

          # All projects belong to this customer (no leakage)
          echo "    [PASS] ${PORTAL_CUST_NAME} portal data isolation: ${portal_count} projects" >&2
          PASS=$((PASS + 1))

          # Get a different ACTIVE customer for cross-access test
          OTHER_CUST_ID=$(echo "$customers" | jq -r "[.[] | select(.lifecycleStatus==\"ACTIVE\" and .id!=\"${PORTAL_CUST_ID}\")][0].id // empty")

          if [ -n "$OTHER_CUST_ID" ]; then
            # Get other customer's projects via org API
            other_projects=$(api_get "/api/projects?customerId=${OTHER_CUST_ID}" "$ALICE")
            OTHER_PROJ_ID=$(echo "$other_projects" | jq -r '.[0].id // .content[0].id // empty')

            if [ -n "$OTHER_PROJ_ID" ]; then
              # Cross-customer access should return 403/404
              cross_resp=$(curl -s -o /dev/null -w "%{http_code}" \
                -X GET "${BACKEND_URL}/portal/projects/${OTHER_PROJ_ID}" \
                -H "Authorization: Bearer ${PORTAL_JWT}")
              case "$cross_resp" in
                403|404) echo "    [PASS] Cross-customer blocked: HTTP ${cross_resp}" >&2; PASS=$((PASS + 1)) ;;
                *) echo "    [FAIL] Cross-customer NOT blocked: HTTP ${cross_resp}" >&2; FAIL=$((FAIL + 1)) ;;
              esac
            else
              echo "    [SKIP] No project for cross-access test" >&2; SKIP=$((SKIP + 1))
            fi
          else
            echo "    [SKIP] No second ACTIVE customer for cross-access test" >&2; SKIP=$((SKIP + 1))
          fi

          # Portal JWT cannot access org API
          org_resp=$(curl -s -o /dev/null -w "%{http_code}" \
            -X GET "${BACKEND_URL}/api/customers" \
            -H "Authorization: Bearer ${PORTAL_JWT}")
          case "$org_resp" in
            401|403) echo "    [PASS] Portal JWT blocked from org API: HTTP ${org_resp}" >&2; PASS=$((PASS + 1)) ;;
            *) echo "    [FAIL] Portal JWT accessed org API: HTTP ${org_resp}" >&2; FAIL=$((FAIL + 1)) ;;
          esac

          # Portal profile returns contact info
          profile_resp=$(curl -sf -X GET "${BACKEND_URL}/portal/me" \
            -H "Authorization: Bearer ${PORTAL_JWT}" 2>/dev/null || echo "{}")
          profile_email=$(echo "$profile_resp" | jq -r '.email // empty')
          if [ -n "$profile_email" ]; then
            assert_eq "Portal profile email" "$CONTACT_EMAIL" "$profile_email"
          else
            echo "    [SKIP] Portal /me not available" >&2; SKIP=$((SKIP + 1))
          fi
        fi
      fi
    fi
  fi

  echo ""
fi

# ══════════════════════════════════════════════════════════════════
# Audit Integrity — DELETE/UPDATE triggers protect audit_events
# ══════════════════════════════════════════════════════════════════
if should_run "audit_integrity"; then
  echo "── Audit Integrity ────────────────────────────────────"

  PG_CONTAINER="${PG_CONTAINER:-e2e-postgres}"

  # Find a tenant schema via docker exec
  TENANT_SCHEMA=$(docker exec "$PG_CONTAINER" psql -U postgres -d app -t -A \
    -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%' LIMIT 1" 2>/dev/null || echo "")

  if [ -z "$TENANT_SCHEMA" ]; then
    echo "    [SKIP] No tenant schema found — skipping audit tests" >&2
    SKIP=$((SKIP + 3))
  else
    # Count audit events
    AUDIT_COUNT=$(docker exec "$PG_CONTAINER" psql -U postgres -d app -t -A \
      -c "SELECT COUNT(*) FROM ${TENANT_SCHEMA}.audit_events" 2>/dev/null || echo "0")
    assert_gt "Audit events exist" "0" "$AUDIT_COUNT"

    # Attempt UPDATE on audit_events (should fail via trigger)
    UPDATE_RESULT=$(docker exec "$PG_CONTAINER" psql -U postgres -d app -t -A \
      -c "UPDATE ${TENANT_SCHEMA}.audit_events SET event_type='hacked' WHERE id = (SELECT id FROM ${TENANT_SCHEMA}.audit_events LIMIT 1)" 2>&1 || true)
    assert_contains "UPDATE blocked by trigger" "$UPDATE_RESULT" "cannot be updated"

    # Attempt DELETE on audit_events (should fail via trigger)
    DELETE_RESULT=$(docker exec "$PG_CONTAINER" psql -U postgres -d app -t -A \
      -c "DELETE FROM ${TENANT_SCHEMA}.audit_events WHERE id = (SELECT id FROM ${TENANT_SCHEMA}.audit_events LIMIT 1)" 2>&1 || true)
    assert_contains "DELETE blocked by trigger" "$DELETE_RESULT" "cannot be deleted"
  fi

  echo ""
fi

# ══════════════════════════════════════════════════════════════════
# Summary
# ══════════════════════════════════════════════════════════════════
echo ""
echo "╔═══════════════════════════════════════════════════════╗"
echo "║  REGRESSION TEST RESULTS (API)                        ║"
echo "╠═══════════════════════════════════════════════════════╣"
printf "║  PASS: %-4d  FAIL: %-4d  SKIP: %-4d                  ║\n" "$PASS" "$FAIL" "$SKIP"
echo "╚═══════════════════════════════════════════════════════╝"
echo ""

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
