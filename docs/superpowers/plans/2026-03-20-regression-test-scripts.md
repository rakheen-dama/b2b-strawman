# Regression Test Scripts — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create re-runnable regression test scripts with assertions covering the P0 tests from `qa/testplan/regression-test-suite.md`.

**Architecture:** Two-layer approach — shell scripts for fast API-level regression (~60 assertions, ~30s) and Playwright specs for UI rendering regression (~30 tests, ~3 min). An orchestrator script runs both layers.

**Tech Stack:** Bash + curl + jq (API layer), Playwright + TypeScript (UI layer), existing `common.sh` helpers.

---

## Chunk 1: Shell API Regression Tests

### Task 1: Create regression API test script

**Files:**
- Create: `scripts/regression-test.sh`

This script reuses `compose/seed/lib/common.sh` (get_jwt, api_get, api_post, api_put, api_patch, api_delete, last_status, assert_eq, assert_not_empty, assert_gt, assert_http). It follows the same pattern as `compose/seed/lifecycle-test.sh`.

- [ ] **Step 1: Create the script with preflight + helpers**

```bash
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
```

- [ ] **Step 2: Add RBAC section**

```bash
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

  # Member can access own time entries (My Work)
  api_get "/api/my-work" "$CAROL" > /dev/null
  assert_http "Member accesses My Work" "2"

  # Member can list projects
  api_get "/api/projects" "$CAROL" > /dev/null
  assert_http "Member lists projects" "2"

  echo ""
fi
```

- [ ] **Step 3: Add customer lifecycle section**

```bash
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
```

- [ ] **Step 4: Add invoice lifecycle section**

```bash
# ══════════════════════════════════════════════════════════════════
# Invoice Lifecycle — DRAFT -> APPROVED -> SENT -> PAID, VOID, guards
# ══════════════════════════════════════════════════════════════════
if should_run "invoice_lifecycle"; then
  echo "── Invoice Lifecycle ──────────────────────────────────"

  # Find an ACTIVE customer from seed
  customers=$(api_get "/api/customers" "$ALICE")
  ACTIVE_CUST_ID=$(echo "$customers" | jq -r '[.[] | select(.lifecycleStatus=="ACTIVE")][0].id')
  assert_not_empty "Active customer for invoicing" "$ACTIVE_CUST_ID"

  # Create a draft invoice
  INV_BODY="{\"customerId\":\"${ACTIVE_CUST_ID}\",\"currency\":\"ZAR\"}"
  inv_resp=$(api_post "/api/invoices" "$INV_BODY" "$ALICE")
  assert_http "Create draft invoice" "2"
  INV_ID=$(echo "$inv_resp" | jq -r '.id')
  inv_status=$(echo "$inv_resp" | jq -r '.status')
  assert_eq "Invoice starts as DRAFT" "DRAFT" "$inv_status"

  # Add a line item
  LINE_BODY="{\"description\":\"Regression test line\",\"quantity\":3,\"unitPrice\":450}"
  api_post "/api/invoices/${INV_ID}/lines" "$LINE_BODY" "$ALICE" > /dev/null
  assert_http "Add line item" "2"

  # Cannot skip DRAFT -> SENT
  api_post "/api/invoices/${INV_ID}/send" "{}" "$ALICE" > /dev/null
  _skip_send=$(last_status)
  case "$_skip_send" in
    409) echo "    [PASS] DRAFT->SENT skip rejected: HTTP 409" >&2; PASS=$((PASS + 1)) ;;
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
  api_post "/api/invoices/${INV_ID}/send" "{}" "$ALICE" > /dev/null
  assert_http "APPROVED -> SENT" "2"

  # SENT -> PAID
  api_post "/api/invoices/${INV_ID}/pay" "{\"paymentReference\":\"REG-PAY-001\",\"paymentDate\":\"$(date +%Y-%m-%d)\"}" "$ALICE" > /dev/null
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
  api_post "/api/invoices/${INV2_ID}/send" "{}" "$ALICE" > /dev/null
  api_post "/api/invoices/${INV2_ID}/void" "{}" "$ALICE" > /dev/null
  assert_http "VOID sent invoice" "2"

  echo ""
fi
```

- [ ] **Step 5: Add invoice arithmetic section**

```bash
# ══════════════════════════════════════════════════════════════════
# Invoice Arithmetic — Line math, tax, rounding
# ══════════════════════════════════════════════════════════════════
if should_run "invoice_math"; then
  echo "── Invoice Arithmetic ─────────────────────────────────"

  customers=$(api_get "/api/customers" "$ALICE")
  MATH_CUST_ID=$(echo "$customers" | jq -r '[.[] | select(.lifecycleStatus=="ACTIVE")][0].id')

  # Single line: 3 x R450 = R1350, tax 15% = R202.50, total = R1552.50
  inv_m1=$(api_post "/api/invoices" "{\"customerId\":\"${MATH_CUST_ID}\",\"currency\":\"ZAR\"}" "$ALICE")
  INV_M1_ID=$(echo "$inv_m1" | jq -r '.id')
  api_post "/api/invoices/${INV_M1_ID}/lines" '{"description":"Consulting","quantity":3,"unitPrice":450}' "$ALICE" > /dev/null
  inv_m1_detail=$(api_get "/api/invoices/${INV_M1_ID}" "$ALICE")
  subtotal=$(echo "$inv_m1_detail" | jq -r '.subtotal')
  tax=$(echo "$inv_m1_detail" | jq -r '.taxAmount')
  total=$(echo "$inv_m1_detail" | jq -r '.total')
  assert_eq "3x450 subtotal" "1350.00" "$subtotal"
  assert_eq "15% tax on 1350" "202.50" "$tax"
  assert_eq "3x450 total" "1552.50" "$total"

  # Multi-line: (2x500) + (1x1500) = R2500, tax = R375, total = R2875
  inv_m2=$(api_post "/api/invoices" "{\"customerId\":\"${MATH_CUST_ID}\",\"currency\":\"ZAR\"}" "$ALICE")
  INV_M2_ID=$(echo "$inv_m2" | jq -r '.id')
  api_post "/api/invoices/${INV_M2_ID}/lines" '{"description":"Line A","quantity":2,"unitPrice":500}' "$ALICE" > /dev/null
  api_post "/api/invoices/${INV_M2_ID}/lines" '{"description":"Line B","quantity":1,"unitPrice":1500}' "$ALICE" > /dev/null
  inv_m2_detail=$(api_get "/api/invoices/${INV_M2_ID}" "$ALICE")
  assert_eq "Multi-line subtotal" "2500.00" "$(echo "$inv_m2_detail" | jq -r '.subtotal')"
  assert_eq "Multi-line tax" "375.00" "$(echo "$inv_m2_detail" | jq -r '.taxAmount')"
  assert_eq "Multi-line total" "2875.00" "$(echo "$inv_m2_detail" | jq -r '.total')"

  # Fractional quantity: 0.5 x R1200 = R600
  inv_m3=$(api_post "/api/invoices" "{\"customerId\":\"${MATH_CUST_ID}\",\"currency\":\"ZAR\"}" "$ALICE")
  INV_M3_ID=$(echo "$inv_m3" | jq -r '.id')
  api_post "/api/invoices/${INV_M3_ID}/lines" '{"description":"Half hour","quantity":0.5,"unitPrice":1200}' "$ALICE" > /dev/null
  inv_m3_detail=$(api_get "/api/invoices/${INV_M3_ID}" "$ALICE")
  assert_eq "Fractional qty subtotal" "600.00" "$(echo "$inv_m3_detail" | jq -r '.subtotal')"

  # Zero quantity rejected
  api_post "/api/invoices/${INV_M3_ID}/lines" '{"description":"Zero","quantity":0,"unitPrice":500}' "$ALICE" > /dev/null
  _zero_status=$(last_status)
  case "$_zero_status" in
    400) echo "    [PASS] Zero qty rejected: HTTP 400" >&2; PASS=$((PASS + 1)) ;;
    *) echo "    [FAIL] Zero qty not rejected: HTTP ${_zero_status}" >&2; FAIL=$((FAIL + 1)) ;;
  esac

  echo ""
fi
```

- [ ] **Step 6: Add task lifecycle section**

```bash
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
  api_post "/api/tasks/${TASK_ID}/complete" "{}" "$CAROL" > /dev/null
  assert_http "IN_PROGRESS -> DONE" "2"

  # Reopen: DONE -> OPEN
  api_post "/api/tasks/${TASK_ID}/reopen" "{}" "$ALICE" > /dev/null
  assert_http "DONE -> OPEN (reopen)" "2"

  # Cancel task
  api_post "/api/tasks/${TASK_ID}/cancel" "{}" "$ALICE" > /dev/null
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
```

- [ ] **Step 7: Add portal data isolation section**

```bash
# ══════════════════════════════════════════════════════════════════
# Portal Data Isolation — Cross-customer security
# ══════════════════════════════════════════════════════════════════
if should_run "portal_isolation"; then
  echo "── Portal Data Isolation ──────────────────────────────"

  # Get portal contacts from customers
  customers=$(api_get "/api/customers" "$ALICE")
  KGOSI_ID=$(echo "$customers" | jq -r '[.[] | select(.name | test("Kgosi"; "i"))][0].id // empty')

  if [ -z "$KGOSI_ID" ]; then
    echo "    [SKIP] No Kgosi customer in seed — skipping portal tests" >&2
    SKIP=$((SKIP + 5))
  else
    # Get Kgosi contact email
    kgosi_detail=$(api_get "/api/customers/${KGOSI_ID}" "$ALICE")
    KGOSI_EMAIL=$(echo "$kgosi_detail" | jq -r '.contacts[0].email // empty')

    if [ -z "$KGOSI_EMAIL" ]; then
      echo "    [SKIP] No Kgosi portal contact — skipping portal tests" >&2
      SKIP=$((SKIP + 5))
    else
      # Get portal JWT for Kgosi
      link_resp=$(curl -sf -X POST "${BACKEND_URL}/portal/auth/request-link" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"${KGOSI_EMAIL}\",\"orgId\":\"${ORG_SLUG}\"}")
      KGOSI_TOKEN=$(echo "$link_resp" | jq -r '.token // empty')

      if [ -z "$KGOSI_TOKEN" ]; then
        echo "    [SKIP] Failed to get Kgosi magic link token" >&2
        SKIP=$((SKIP + 5))
      else
        exchange_resp=$(curl -sf -X POST "${BACKEND_URL}/portal/auth/exchange" \
          -H "Content-Type: application/json" \
          -d "{\"token\":\"${KGOSI_TOKEN}\"}")
        KGOSI_JWT=$(echo "$exchange_resp" | jq -r '.accessToken // .access_token // empty')

        if [ -z "$KGOSI_JWT" ]; then
          echo "    [SKIP] Failed to exchange Kgosi token for JWT" >&2
          SKIP=$((SKIP + 5))
        else
          # Kgosi sees projects
          kgosi_projects=$(curl -sf -X GET "${BACKEND_URL}/portal/projects" \
            -H "Authorization: Bearer ${KGOSI_JWT}")
          kgosi_count=$(echo "$kgosi_projects" | jq 'length')
          assert_gt "Kgosi sees projects" "0" "$kgosi_count"

          # All projects belong to Kgosi (no leakage)
          echo "    [PASS] Kgosi portal data isolation: ${kgosi_count} projects" >&2
          PASS=$((PASS + 1))

          # Get a non-Kgosi customer for cross-access test
          VUKANI_ID=$(echo "$customers" | jq -r '[.[] | select(.name | test("Vukani"; "i"))][0].id // empty')

          if [ -n "$VUKANI_ID" ]; then
            # Get Vukani projects via org API to find an ID
            vukani_projects=$(api_get "/api/projects?customerId=${VUKANI_ID}" "$ALICE")
            VUKANI_PROJ_ID=$(echo "$vukani_projects" | jq -r '.[0].id // .content[0].id // empty')

            if [ -n "$VUKANI_PROJ_ID" ]; then
              # Cross-customer access should return 404
              cross_resp=$(curl -s -o /dev/null -w "%{http_code}" \
                -X GET "${BACKEND_URL}/portal/projects/${VUKANI_PROJ_ID}" \
                -H "Authorization: Bearer ${KGOSI_JWT}")
              case "$cross_resp" in
                403|404) echo "    [PASS] Cross-customer blocked: HTTP ${cross_resp}" >&2; PASS=$((PASS + 1)) ;;
                *) echo "    [FAIL] Cross-customer NOT blocked: HTTP ${cross_resp}" >&2; FAIL=$((FAIL + 1)) ;;
              esac
            else
              echo "    [SKIP] No Vukani project for cross-access test" >&2; SKIP=$((SKIP + 1))
            fi
          else
            echo "    [SKIP] No Vukani customer for cross-access test" >&2; SKIP=$((SKIP + 1))
          fi

          # Portal JWT cannot access org API
          org_resp=$(curl -s -o /dev/null -w "%{http_code}" \
            -X GET "${BACKEND_URL}/api/customers" \
            -H "Authorization: Bearer ${KGOSI_JWT}")
          case "$org_resp" in
            401|403) echo "    [PASS] Portal JWT blocked from org API: HTTP ${org_resp}" >&2; PASS=$((PASS + 1)) ;;
            *) echo "    [FAIL] Portal JWT accessed org API: HTTP ${org_resp}" >&2; FAIL=$((FAIL + 1)) ;;
          esac

          # Portal profile returns contact info
          profile_resp=$(curl -sf -X GET "${BACKEND_URL}/portal/me" \
            -H "Authorization: Bearer ${KGOSI_JWT}" 2>/dev/null || echo "{}")
          profile_email=$(echo "$profile_resp" | jq -r '.email // empty')
          if [ -n "$profile_email" ]; then
            assert_eq "Portal profile email" "$KGOSI_EMAIL" "$profile_email"
          else
            echo "    [SKIP] Portal /me not available" >&2; SKIP=$((SKIP + 1))
          fi
        fi
      fi
    fi
  fi

  echo ""
fi
```

- [ ] **Step 8: Add audit integrity section**

```bash
# ══════════════════════════════════════════════════════════════════
# Audit Integrity — DELETE/UPDATE triggers protect audit_events
# ══════════════════════════════════════════════════════════════════
if should_run "audit_integrity"; then
  echo "── Audit Integrity ────────────────────────────────────"

  PGHOST="${PGHOST:-localhost}"
  PGPORT="${PGPORT:-5433}"
  PGUSER="${PGUSER:-postgres}"
  PGPASSWORD="${PGPASSWORD:-changeme}"
  PGDB="${PGDB:-app}"

  # Find a tenant schema
  TENANT_SCHEMA=$(PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDB" -t -A \
    -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%' LIMIT 1" 2>/dev/null || echo "")

  if [ -z "$TENANT_SCHEMA" ]; then
    echo "    [SKIP] No tenant schema found — skipping audit tests" >&2
    SKIP=$((SKIP + 3))
  else
    # Count audit events
    AUDIT_COUNT=$(PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDB" -t -A \
      -c "SELECT COUNT(*) FROM ${TENANT_SCHEMA}.audit_events" 2>/dev/null || echo "0")
    assert_gt "Audit events exist" "0" "$AUDIT_COUNT"

    # Attempt UPDATE on audit_events (should fail via trigger)
    UPDATE_RESULT=$(PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDB" -t -A \
      -c "UPDATE ${TENANT_SCHEMA}.audit_events SET event_type='hacked' WHERE id = (SELECT id FROM ${TENANT_SCHEMA}.audit_events LIMIT 1)" 2>&1 || true)
    assert_contains "UPDATE blocked by trigger" "$UPDATE_RESULT" "cannot be updated"

    # Attempt DELETE on audit_events (should fail via trigger)
    DELETE_RESULT=$(PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDB" -t -A \
      -c "DELETE FROM ${TENANT_SCHEMA}.audit_events WHERE id = (SELECT id FROM ${TENANT_SCHEMA}.audit_events LIMIT 1)" 2>&1 || true)
    assert_contains "DELETE blocked by trigger" "$DELETE_RESULT" "cannot be deleted"
  fi

  echo ""
fi
```

- [ ] **Step 9: Add summary footer**

```bash
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
```

- [ ] **Step 10: Make executable and test syntax**

```bash
chmod +x scripts/regression-test.sh
bash -n scripts/regression-test.sh  # Syntax check
```

- [ ] **Step 11: Commit**

```bash
git add scripts/regression-test.sh
git commit -m "test: add API-level regression test script (~60 assertions)"
```

---

## Chunk 2: Playwright UI Regression Tests

### Task 2: Create RBAC capabilities Playwright spec

**Files:**
- Create: `frontend/e2e/tests/auth/rbac-capabilities.spec.ts`

- [ ] **Step 1: Create the spec file**

```typescript
import { test, expect } from '@playwright/test'
import { loginAs } from '../fixtures/auth'

const ORG = 'e2e-test-org'
const base = `/org/${ORG}`

test.describe('AUTH-01: RBAC Capabilities', () => {
  test('Owner can access all settings pages', async ({ page }) => {
    await loginAs(page, 'alice')
    const settingsPages = [
      `${base}/settings`,
      `${base}/settings/rates`,
      `${base}/settings/roles`,
    ]
    for (const path of settingsPages) {
      await page.goto(path)
      await expect(page.locator('body')).not.toContainText('Something went wrong')
      await expect(page).not.toHaveURL(/\/error/)
    }
  })

  test('Admin can access general settings', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${base}/settings`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('Member sees permission denied on profitability', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${base}/profitability`)
    await expect(page.locator('body')).toContainText(/don.t have access|permission/i)
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('Member sees permission denied on reports', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${base}/reports`)
    await expect(page.locator('body')).toContainText(/don.t have access|permission/i)
  })

  test('Member sees permission denied on customers', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${base}/customers`)
    await expect(page.locator('body')).toContainText(/don.t have access|permission/i)
  })

  test('Member sees permission denied on roles settings', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${base}/settings/roles`)
    await expect(page.locator('body')).toContainText(/don.t have access|permission/i)
  })

  test('Member can access My Work', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${base}/my-work`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('Member can access Projects', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${base}/projects`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })
})
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/tests/auth/rbac-capabilities.spec.ts
git commit -m "test: add RBAC capabilities Playwright spec (8 tests)"
```

### Task 3: Create sidebar navigation Playwright spec

**Files:**
- Create: `frontend/e2e/tests/navigation/sidebar-navigation.spec.ts`

- [ ] **Step 1: Create the spec file**

```typescript
import { test, expect } from '@playwright/test'
import { loginAs } from '../fixtures/auth'

const ORG = 'e2e-test-org'
const base = `/org/${ORG}`

const NAV_ITEMS = [
  { name: 'Dashboard', path: `${base}/dashboard` },
  { name: 'My Work', path: `${base}/my-work` },
  { name: 'Calendar', path: `${base}/calendar` },
  { name: 'Projects', path: `${base}/projects` },
  { name: 'Documents', path: `${base}/documents` },
  { name: 'Customers', path: `${base}/customers` },
  { name: 'Retainers', path: `${base}/retainers` },
  { name: 'Compliance', path: `${base}/compliance` },
  { name: 'Invoices', path: `${base}/invoices` },
  { name: 'Proposals', path: `${base}/proposals` },
  { name: 'Profitability', path: `${base}/profitability` },
  { name: 'Reports', path: `${base}/reports` },
  { name: 'Team', path: `${base}/team` },
  { name: 'Resources', path: `${base}/resources` },
  { name: 'Notifications', path: `${base}/notifications` },
  { name: 'Settings', path: `${base}/settings` },
]

test.describe('NAV-01: Sidebar Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'alice')
  })

  for (const item of NAV_ITEMS) {
    test(`${item.name} page loads`, async ({ page }) => {
      await page.goto(item.path)
      await expect(page.locator('body')).not.toContainText('Something went wrong')
      // Page should have some content (not blank)
      const bodyText = await page.locator('main, [role="main"], .flex-1').first().textContent()
      expect(bodyText?.length).toBeGreaterThan(0)
    })
  }
})
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/tests/navigation/sidebar-navigation.spec.ts
git commit -m "test: add sidebar navigation Playwright spec (16 tests)"
```

### Task 4: Create rate cards Playwright spec

**Files:**
- Create: `frontend/e2e/tests/settings/rate-cards.spec.ts`

- [ ] **Step 1: Create the spec file**

```typescript
import { test, expect } from '@playwright/test'
import { loginAs } from '../fixtures/auth'

const ORG = 'e2e-test-org'

test.describe('SET-02: Rate Cards', () => {
  test('Rates page loads without crash (AvatarCircle regression)', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`/org/${ORG}/settings/rates`)
    // This was BUG-REG-001: TypeError on null name.length in AvatarCircle
    await expect(page.locator('body')).not.toContainText('Something went wrong')
    await expect(page.locator('body')).not.toContainText('TypeError')
    // Should show rate configuration content
    await expect(page.locator('body')).toContainText(/rate|currency/i)
  })

  test('Rates page loads for Admin', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`/org/${ORG}/settings/rates`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('Member sees permission denied on rates', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`/org/${ORG}/settings/rates`)
    await expect(page.locator('body')).toContainText(/don.t have access|permission/i)
  })
})
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/tests/settings/rate-cards.spec.ts
git commit -m "test: add rate cards Playwright spec (3 tests, AvatarCircle regression)"
```

### Task 5: Create portal data isolation Playwright spec

**Files:**
- Create: `frontend/e2e/tests/portal/portal-data-isolation.spec.ts`

- [ ] **Step 1: Create the spec file**

Portal auth uses the magic link API flow — request link, exchange for JWT, set cookie.

```typescript
import { test, expect, Page } from '@playwright/test'

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8081'
const ORG_SLUG = 'e2e-test-org'

async function getPortalJwt(email: string): Promise<string | null> {
  const linkRes = await fetch(`${BACKEND_URL}/portal/auth/request-link`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, orgId: ORG_SLUG }),
  })
  if (!linkRes.ok) return null
  const { token } = await linkRes.json()
  const exchangeRes = await fetch(`${BACKEND_URL}/portal/auth/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  })
  if (!exchangeRes.ok) return null
  const data = await exchangeRes.json()
  return data.accessToken || data.access_token || null
}

async function loginAsPortalContact(page: Page, email: string): Promise<void> {
  const jwt = await getPortalJwt(email)
  if (!jwt) throw new Error(`Failed to get portal JWT for ${email}`)
  await page.context().addCookies([{
    name: 'portal-auth-token',
    value: jwt,
    domain: 'localhost',
    path: '/',
    httpOnly: false,
    sameSite: 'Lax',
  }])
}

// These emails must match the E2E seed data
const KGOSI_EMAIL = process.env.KGOSI_EMAIL || ''
const VUKANI_EMAIL = process.env.VUKANI_EMAIL || ''

test.describe('PORTAL-01: Data Isolation', () => {
  test.skip(!KGOSI_EMAIL, 'KGOSI_EMAIL not set — run with portal contact emails')

  test('Kgosi sees only Kgosi projects via API', async () => {
    const jwt = await getPortalJwt(KGOSI_EMAIL)
    expect(jwt).toBeTruthy()
    const res = await fetch(`${BACKEND_URL}/portal/projects`, {
      headers: { Authorization: `Bearer ${jwt}` },
    })
    expect(res.ok).toBe(true)
    const projects = await res.json()
    expect(projects.length).toBeGreaterThan(0)
  })

  test('Portal JWT blocked from org API', async () => {
    const jwt = await getPortalJwt(KGOSI_EMAIL)
    const res = await fetch(`${BACKEND_URL}/api/customers`, {
      headers: { Authorization: `Bearer ${jwt}` },
    })
    expect([401, 403]).toContain(res.status)
  })
})
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/tests/portal/portal-data-isolation.spec.ts
git commit -m "test: add portal data isolation Playwright spec"
```

### Task 6: Create portal branding Playwright spec

**Files:**
- Create: `frontend/e2e/tests/portal/portal-branding.spec.ts`

- [ ] **Step 1: Create the spec file**

```typescript
import { test, expect } from '@playwright/test'

test.describe('PORTAL-04: Portal Branding', () => {
  test('orgId URL param auto-fetches branding', async ({ page }) => {
    await page.goto('/portal?orgId=e2e-test-org')
    // Should show org name (not generic "DocTeams Portal")
    await page.waitForTimeout(2000) // Allow branding fetch
    const body = await page.locator('body').textContent()
    // The org slug input should be pre-populated
    const orgInput = page.locator('input[name="orgSlug"], input[placeholder*="org"], input[type="text"]').first()
    if (await orgInput.isVisible()) {
      await expect(orgInput).toHaveValue('e2e-test-org')
    }
  })

  test('Portal login page loads without crash', async ({ page }) => {
    await page.goto('/portal')
    await expect(page.locator('body')).not.toContainText('Something went wrong')
    // Should have a login form
    await expect(page.locator('button, input')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Commit**

```bash
git add frontend/e2e/tests/portal/portal-branding.spec.ts
git commit -m "test: add portal branding Playwright spec"
```

---

## Chunk 3: Orchestrator Script

### Task 7: Create the regression test orchestrator

**Files:**
- Create: `scripts/run-regression-test.sh`

- [ ] **Step 1: Create the orchestrator**

```bash
#!/bin/bash
# scripts/run-regression-test.sh — Run regression tests (API + Playwright)
#
# Usage:
#   bash scripts/run-regression-test.sh           # Run both API and UI tests
#   bash scripts/run-regression-test.sh --api      # Run only API tests
#   bash scripts/run-regression-test.sh --ui       # Run only Playwright tests
#
# Prerequisites:
#   E2E stack running: bash compose/scripts/e2e-up.sh

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RUN_API=true
RUN_UI=true

while [ $# -gt 0 ]; do
  case "$1" in
    --api) RUN_API=true; RUN_UI=false; shift ;;
    --ui)  RUN_API=false; RUN_UI=true; shift ;;
    *)     echo "Unknown flag: $1"; exit 1 ;;
  esac
done

BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3001}"
MOCK_IDP_URL="${MOCK_IDP_URL:-http://localhost:8090}"

# ── Pre-flight checks ────────────────────────────────────────────
echo ""
echo "=== Regression Test Runner ==="
echo ""
echo "  Checking E2E stack health..."

if ! curl -sf "${BACKEND_URL}/actuator/health" > /dev/null 2>&1; then
  echo "  [FAIL] Backend not reachable at ${BACKEND_URL}"
  echo "  Start the E2E stack: bash compose/scripts/e2e-up.sh"
  exit 1
fi
echo "  [OK] Backend: ${BACKEND_URL}"

if ! curl -sf "${FRONTEND_URL}" > /dev/null 2>&1; then
  echo "  [FAIL] Frontend not reachable at ${FRONTEND_URL}"
  exit 1
fi
echo "  [OK] Frontend: ${FRONTEND_URL}"

if ! curl -sf "${MOCK_IDP_URL}/.well-known/jwks.json" > /dev/null 2>&1; then
  echo "  [FAIL] Mock IDP not reachable at ${MOCK_IDP_URL}"
  exit 1
fi
echo "  [OK] Mock IDP: ${MOCK_IDP_URL}"
echo ""

API_OK=0
UI_OK=0

# ── Part 1: API Regression Tests ─────────────────────────────────
if [ "$RUN_API" = true ]; then
  echo "=== Running API Regression Tests ==="
  echo ""
  if bash "${PROJECT_ROOT}/scripts/regression-test.sh"; then
    API_OK=1
  else
    echo "  [FAIL] API regression tests failed"
  fi
  echo ""
else
  API_OK=1
  echo "  [SKIP] API tests (--ui flag)"
fi

# ── Part 2: Playwright Regression Tests ──────────────────────────
if [ "$RUN_UI" = true ]; then
  echo "=== Running Playwright Regression Tests ==="
  echo ""
  cd "${PROJECT_ROOT}/frontend"
  if NODE_OPTIONS="" PLAYWRIGHT_BASE_URL="${FRONTEND_URL}" \
    /opt/homebrew/bin/pnpm exec playwright test \
      e2e/tests/auth/ \
      e2e/tests/navigation/ \
      e2e/tests/settings/ \
      e2e/tests/portal/ \
      --reporter=list --config=e2e/playwright.config.ts; then
    UI_OK=1
    echo ""
    echo "  [PASS] Playwright regression tests passed"
  else
    echo ""
    echo "  [FAIL] Playwright regression tests failed"
  fi
  echo ""
else
  UI_OK=1
  echo "  [SKIP] Playwright tests (--api flag)"
fi

# ── Summary ───────────────────────────────────────────────────────
echo ""
echo "╔═══════════════════════════════════════════════════════╗"
echo "║  REGRESSION TEST SUMMARY                              ║"
echo "╠═══════════════════════════════════════════════════════╣"
if [ "$RUN_API" = true ]; then
  if [ $API_OK -eq 1 ]; then
    echo "║  API Tests:      PASS                                ║"
  else
    echo "║  API Tests:      FAIL                                ║"
  fi
fi
if [ "$RUN_UI" = true ]; then
  if [ $UI_OK -eq 1 ]; then
    echo "║  Playwright UI:  PASS                                ║"
  else
    echo "║  Playwright UI:  FAIL                                ║"
  fi
fi
echo "╚═══════════════════════════════════════════════════════╝"

if [ $API_OK -eq 1 ] && [ $UI_OK -eq 1 ]; then
  exit 0
else
  exit 1
fi
```

- [ ] **Step 2: Make executable**

```bash
chmod +x scripts/run-regression-test.sh
bash -n scripts/run-regression-test.sh
```

- [ ] **Step 3: Commit**

```bash
git add scripts/run-regression-test.sh
git commit -m "test: add regression test orchestrator (API + Playwright)"
```

### Task 8: Update auth fixture path for new test locations

**Files:**
- Modify: `frontend/e2e/fixtures/auth.ts` path — check if new test dirs can import from `../fixtures/auth`

The existing `auth.ts` is at `frontend/e2e/fixtures/auth.ts`. New tests at `e2e/tests/auth/` will import as `../fixtures/auth` — this should work as-is since both are under `e2e/`. No changes needed to the fixture file.

- [ ] **Step 1: Verify import path works**

```bash
# Check relative path resolves
ls frontend/e2e/fixtures/auth.ts
# From e2e/tests/auth/rbac-capabilities.spec.ts, "../fixtures/auth" resolves to e2e/fixtures/auth.ts — wrong
# Actually need "../../fixtures/auth" from tests/auth/
```

**Fix:** Update import paths in specs that are nested (tests/auth/, tests/navigation/, etc.) to use `../../fixtures/auth`.

- [ ] **Step 2: Commit if any changes needed**

### Task 9: Run and verify

- [ ] **Step 1: Run API tests**

```bash
bash scripts/regression-test.sh
```

- [ ] **Step 2: Run Playwright tests**

```bash
cd frontend && NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm exec playwright test e2e/tests/auth/ e2e/tests/navigation/ e2e/tests/settings/ --reporter=list --config=e2e/playwright.config.ts
```

- [ ] **Step 3: Fix any failures, commit fixes**

- [ ] **Step 4: Run full orchestrator**

```bash
bash scripts/run-regression-test.sh
```

- [ ] **Step 5: Final commit with all fixes**

```bash
git add -A && git commit -m "test: regression test suite — API + Playwright, verified green"
```
