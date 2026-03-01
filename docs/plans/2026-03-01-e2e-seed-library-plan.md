# E2E Seed Library Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a modular shell script library that creates rich test data in the E2E environment via REST APIs.

**Architecture:** Per-entity shell scripts in `compose/seed/lib/` orchestrated by `compose/seed/rich-seed.sh`. Each module exports functions, uses GET-first idempotency, and authenticates via mock IDP JWTs. Runs from host (`localhost:8081`) or Docker (`backend:8080`).

**Tech Stack:** Shell (POSIX sh), curl, jq. No new dependencies.

**Design doc:** `docs/plans/2026-03-01-e2e-seed-library-design.md`

---

### Task 1: Shared Library (`lib/common.sh`)

**Files:**
- Create: `compose/seed/lib/common.sh`

**Step 1: Create the shared library**

```sh
#!/bin/sh
# compose/seed/lib/common.sh — Shared helpers for seed scripts
# Source this file; do not execute directly.

# ── Environment detection ───────────────────────────────────────────
# Inside Docker: backend:8080 / mock-idp:8090
# On host:       localhost:8081 / localhost:8090
if curl -sf http://backend:8080/actuator/health > /dev/null 2>&1; then
  BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
  MOCK_IDP_URL="${MOCK_IDP_URL:-http://mock-idp:8090}"
else
  BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
  MOCK_IDP_URL="${MOCK_IDP_URL:-http://localhost:8090}"
fi

API_KEY="${API_KEY:-e2e-test-api-key}"
ORG_ID="org_e2e_test"
ORG_SLUG="e2e-test-org"

# ── JWT cache ───────────────────────────────────────────────────────
_ALICE_JWT=""
_BOB_JWT=""
_CAROL_JWT=""

get_jwt() {
  user_id="$1"
  org_role="$2"

  # Return cached token if available
  case "$user_id" in
    user_e2e_alice) [ -n "$_ALICE_JWT" ] && echo "$_ALICE_JWT" && return ;;
    user_e2e_bob)   [ -n "$_BOB_JWT" ]   && echo "$_BOB_JWT"   && return ;;
    user_e2e_carol) [ -n "$_CAROL_JWT" ] && echo "$_CAROL_JWT" && return ;;
  esac

  token=$(curl -sf -X POST "${MOCK_IDP_URL}/token" \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": \"${user_id}\",
      \"orgId\": \"${ORG_ID}\",
      \"orgSlug\": \"${ORG_SLUG}\",
      \"orgRole\": \"${org_role}\"
    }" | jq -r '.access_token')

  if [ -z "$token" ] || [ "$token" = "null" ]; then
    echo "FATAL: Failed to get JWT for ${user_id}" >&2
    exit 1
  fi

  # Cache it
  case "$user_id" in
    user_e2e_alice) _ALICE_JWT="$token" ;;
    user_e2e_bob)   _BOB_JWT="$token" ;;
    user_e2e_carol) _CAROL_JWT="$token" ;;
  esac

  echo "$token"
}

# ── API helpers ─────────────────────────────────────────────────────

# api_post <path> <json_body> [jwt]
# Returns: JSON body on stdout. Sets $LAST_STATUS.
api_post() {
  path="$1"
  body="$2"
  jwt="${3:-}"

  auth_header=""
  if [ -n "$jwt" ]; then
    auth_header="-H \"Authorization: Bearer ${jwt}\""
  fi

  response=$(eval curl -s -w '"\\n%{http_code}"' \
    -X POST "\"${BACKEND_URL}${path}\"" \
    -H '"Content-Type: application/json"' \
    ${auth_header} \
    -d "'${body}'")

  LAST_STATUS=$(echo "$response" | tail -1)
  echo "$response" | sed '$d'
}

# api_get <path> [jwt]
# Returns: JSON body on stdout. Sets $LAST_STATUS.
api_get() {
  path="$1"
  jwt="${2:-}"

  auth_header=""
  if [ -n "$jwt" ]; then
    auth_header="-H \"Authorization: Bearer ${jwt}\""
  fi

  response=$(eval curl -s -w '"\\n%{http_code}"' \
    -X GET "\"${BACKEND_URL}${path}\"" \
    ${auth_header})

  LAST_STATUS=$(echo "$response" | tail -1)
  echo "$response" | sed '$d'
}

# api_put <path> <json_body> [jwt]
# Returns: JSON body on stdout. Sets $LAST_STATUS.
api_put() {
  path="$1"
  body="$2"
  jwt="${3:-}"

  auth_header=""
  if [ -n "$jwt" ]; then
    auth_header="-H \"Authorization: Bearer ${jwt}\""
  fi

  response=$(eval curl -s -w '"\\n%{http_code}"' \
    -X PUT "\"${BACKEND_URL}${path}\"" \
    -H '"Content-Type: application/json"' \
    ${auth_header} \
    -d "'${body}'")

  LAST_STATUS=$(echo "$response" | tail -1)
  echo "$response" | sed '$d'
}

# ── Status check ────────────────────────────────────────────────────

check_status() {
  step="$1"
  status="${2:-$LAST_STATUS}"
  case "$status" in
    2[0-9][0-9]|409) echo "    [ok] ${step} (HTTP ${status})" ;;
    *) echo "    [FAIL] ${step} (HTTP ${status})" >&2; return 1 ;;
  esac
}

# ── Idempotency helper ──────────────────────────────────────────────

# check_or_create <entity_label> <list_path> <jq_filter> <create_fn>
#   - GETs the list, applies jq_filter to find existing entity
#   - If found, prints ID and returns 0
#   - If not found, calls create_fn (which should echo the new entity JSON)
#   - create_fn receives no args — it should use variables from caller scope
#
# Example: check_or_create "Acme Corp" "/api/customers" '.content[] | select(.name == "Acme Corp")' create_acme
check_or_create() {
  label="$1"
  list_path="$2"
  jq_filter="$3"
  create_fn="$4"
  jwt="${5:-}"

  existing=$(api_get "$list_path?size=200" "$jwt" | jq -r "$jq_filter | .id" 2>/dev/null)
  if [ -n "$existing" ] && [ "$existing" != "null" ]; then
    echo "    [skip] ${label} already exists (${existing})"
    echo "$existing"
    return 0
  fi

  $create_fn
}
```

**Step 2: Verify it parses**

Run: `sh -n compose/seed/lib/common.sh`
Expected: No output (no syntax errors)

**Step 3: Commit**

```bash
git add compose/seed/lib/common.sh
git commit -m "feat(seed): add shared library for rich seed scripts"
```

---

### Task 2: Customers Module (`lib/customers.sh`)

**Files:**
- Create: `compose/seed/lib/customers.sh`

**Step 1: Create the customers module**

```sh
#!/bin/sh
# compose/seed/lib/customers.sh — Create customers across lifecycle stages
# Requires: lib/common.sh sourced first

seed_customers() {
  echo ""
  echo "==> Seeding customers"
  jwt=$(get_jwt user_e2e_alice owner)

  # ── Acme Corp (ACTIVE, COMPANY) ──────────────────────────────────
  _create_acme() {
    body=$(api_post "/api/customers" '{
      "name": "Acme Corp",
      "email": "contact@acme.example.com",
      "phone": "+1-555-0100",
      "type": "COMPANY",
      "notes": "Primary test customer"
    }' "$jwt")
    check_status "Create Acme Corp" || return 1
    id=$(echo "$body" | jq -r '.id')
    # Transition: PROSPECT -> ONBOARDING -> ACTIVE
    api_post "/api/customers/${id}/transition" '{"targetStatus":"ONBOARDING","notes":"seed"}' "$jwt" > /dev/null
    check_status "Acme -> ONBOARDING"
    api_post "/api/customers/${id}/transition" '{"targetStatus":"ACTIVE","notes":"seed"}' "$jwt" > /dev/null
    check_status "Acme -> ACTIVE"
    echo "$id"
  }
  ACME_ID=$(check_or_create "Acme Corp" "/api/customers" '.content[] | select(.name == "Acme Corp")' _create_acme "$jwt")

  # ── Bright Solutions (ACTIVE, COMPANY) ───────────────────────────
  _create_bright() {
    body=$(api_post "/api/customers" '{
      "name": "Bright Solutions",
      "email": "hello@brightsolutions.example.com",
      "phone": "+1-555-0200",
      "type": "COMPANY",
      "notes": "Secondary test customer"
    }' "$jwt")
    check_status "Create Bright Solutions" || return 1
    id=$(echo "$body" | jq -r '.id')
    api_post "/api/customers/${id}/transition" '{"targetStatus":"ONBOARDING","notes":"seed"}' "$jwt" > /dev/null
    check_status "Bright -> ONBOARDING"
    api_post "/api/customers/${id}/transition" '{"targetStatus":"ACTIVE","notes":"seed"}' "$jwt" > /dev/null
    check_status "Bright -> ACTIVE"
    echo "$id"
  }
  BRIGHT_ID=$(check_or_create "Bright Solutions" "/api/customers" '.content[] | select(.name == "Bright Solutions")' _create_bright "$jwt")

  # ── Carlos Mendez (ONBOARDING, INDIVIDUAL) ───────────────────────
  _create_carlos() {
    body=$(api_post "/api/customers" '{
      "name": "Carlos Mendez",
      "email": "carlos@mendez.example.com",
      "phone": "+1-555-0300",
      "type": "INDIVIDUAL",
      "notes": "Individual client in onboarding"
    }' "$jwt")
    check_status "Create Carlos Mendez" || return 1
    id=$(echo "$body" | jq -r '.id')
    api_post "/api/customers/${id}/transition" '{"targetStatus":"ONBOARDING","notes":"seed"}' "$jwt" > /dev/null
    check_status "Carlos -> ONBOARDING"
    echo "$id"
  }
  CARLOS_ID=$(check_or_create "Carlos Mendez" "/api/customers" '.content[] | select(.name == "Carlos Mendez")' _create_carlos "$jwt")

  # ── Dormant Industries (DORMANT, COMPANY) ────────────────────────
  _create_dormant() {
    body=$(api_post "/api/customers" '{
      "name": "Dormant Industries",
      "email": "info@dormant.example.com",
      "phone": "+1-555-0400",
      "type": "COMPANY",
      "notes": "Formerly active, now dormant"
    }' "$jwt")
    check_status "Create Dormant Industries" || return 1
    id=$(echo "$body" | jq -r '.id')
    # PROSPECT -> ONBOARDING -> ACTIVE -> DORMANT
    api_post "/api/customers/${id}/transition" '{"targetStatus":"ONBOARDING","notes":"seed"}' "$jwt" > /dev/null
    check_status "Dormant -> ONBOARDING"
    api_post "/api/customers/${id}/transition" '{"targetStatus":"ACTIVE","notes":"seed"}' "$jwt" > /dev/null
    check_status "Dormant -> ACTIVE"
    api_post "/api/customers/${id}/transition" '{"targetStatus":"DORMANT","notes":"seed"}' "$jwt" > /dev/null
    check_status "Dormant -> DORMANT"
    echo "$id"
  }
  DORMANT_ID=$(check_or_create "Dormant Industries" "/api/customers" '.content[] | select(.name == "Dormant Industries")' _create_dormant "$jwt")

  # Export IDs for downstream modules
  export ACME_ID BRIGHT_ID CARLOS_ID DORMANT_ID

  echo ""
  echo "    Customers seeded:"
  echo "      Acme Corp:          ${ACME_ID}"
  echo "      Bright Solutions:   ${BRIGHT_ID}"
  echo "      Carlos Mendez:      ${CARLOS_ID}"
  echo "      Dormant Industries: ${DORMANT_ID}"
}
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/customers.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/customers.sh
git commit -m "feat(seed): add customers module — 4 customers across lifecycle stages"
```

---

### Task 3: Projects Module (`lib/projects.sh`)

**Files:**
- Create: `compose/seed/lib/projects.sh`

**Step 1: Create the projects module**

```sh
#!/bin/sh
# compose/seed/lib/projects.sh — Create projects across customers
# Requires: lib/common.sh + lib/customers.sh (ACME_ID, BRIGHT_ID, etc.)

seed_projects() {
  echo ""
  echo "==> Seeding projects"
  jwt=$(get_jwt user_e2e_alice owner)

  _create_project() {
    name="$1"
    description="$2"
    customer_id="$3"
    body=$(api_post "/api/projects" "{
      \"name\": \"${name}\",
      \"description\": \"${description}\",
      \"customerId\": \"${customer_id}\"
    }" "$jwt")
    check_status "Create ${name}" || return 1
    echo "$body" | jq -r '.id'
  }

  # ── Acme Corp projects ──────────────────────────────────────────
  WEBSITE_REDESIGN_ID=$(check_or_create "Website Redesign" "/api/projects" \
    '.content[] | select(.name == "Website Redesign")' \
    '_create_project "Website Redesign" "Full website overhaul with new branding" "'"$ACME_ID"'"' "$jwt")

  BRAND_GUIDELINES_ID=$(check_or_create "Brand Guidelines" "/api/projects" \
    '.content[] | select(.name == "Brand Guidelines")' \
    '_create_project "Brand Guidelines" "Develop comprehensive brand guide" "'"$ACME_ID"'"' "$jwt")

  # ── Bright Solutions projects ────────────────────────────────────
  MOBILE_APP_ID=$(check_or_create "Mobile App MVP" "/api/projects" \
    '.content[] | select(.name == "Mobile App MVP")' \
    '_create_project "Mobile App MVP" "Cross-platform mobile app first release" "'"$BRIGHT_ID"'"' "$jwt")

  SEO_AUDIT_ID=$(check_or_create "SEO Audit" "/api/projects" \
    '.content[] | select(.name == "SEO Audit")' \
    '_create_project "SEO Audit" "Technical SEO audit and recommendations" "'"$BRIGHT_ID"'"' "$jwt")

  # ── Carlos Mendez project ───────────────────────────────────────
  ANNUAL_REPORT_ID=$(check_or_create "Annual Report" "/api/projects" \
    '.content[] | select(.name == "Annual Report")' \
    '_create_project "Annual Report" "2025 annual financial report" "'"$CARLOS_ID"'"' "$jwt")

  # ── Dormant Industries project ──────────────────────────────────
  LEGACY_MIGRATION_ID=$(check_or_create "Legacy Migration" "/api/projects" \
    '.content[] | select(.name == "Legacy Migration")' \
    '_create_project "Legacy Migration" "Migrate legacy systems to cloud" "'"$DORMANT_ID"'"' "$jwt")

  export WEBSITE_REDESIGN_ID BRAND_GUIDELINES_ID MOBILE_APP_ID SEO_AUDIT_ID ANNUAL_REPORT_ID LEGACY_MIGRATION_ID

  echo ""
  echo "    Projects seeded:"
  echo "      Website Redesign:   ${WEBSITE_REDESIGN_ID}"
  echo "      Brand Guidelines:   ${BRAND_GUIDELINES_ID}"
  echo "      Mobile App MVP:     ${MOBILE_APP_ID}"
  echo "      SEO Audit:          ${SEO_AUDIT_ID}"
  echo "      Annual Report:      ${ANNUAL_REPORT_ID}"
  echo "      Legacy Migration:   ${LEGACY_MIGRATION_ID}"
}
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/projects.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/projects.sh
git commit -m "feat(seed): add projects module — 6 projects across 4 customers"
```

---

### Task 4: Tasks Module (`lib/tasks.sh`)

**Files:**
- Create: `compose/seed/lib/tasks.sh`

**Step 1: Create the tasks module**

This module creates ~20 tasks across projects. Each task has a name, priority, and optional assignee. Status transitions happen after creation via PATCH (if the API supports it) or are implied by the task's initial state.

```sh
#!/bin/sh
# compose/seed/lib/tasks.sh — Create tasks across projects
# Requires: lib/common.sh, lib/projects.sh (project IDs), member IDs

seed_tasks() {
  echo ""
  echo "==> Seeding tasks"
  jwt=$(get_jwt user_e2e_alice owner)

  # Helper: get member IDs by name
  members_json=$(api_get "/api/members" "$jwt")
  ALICE_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Alice Owner") | .id')
  BOB_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Bob Admin") | .id')
  CAROL_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Carol Member") | .id')
  export ALICE_MEMBER_ID BOB_MEMBER_ID CAROL_MEMBER_ID

  echo "    Member IDs: Alice=${ALICE_MEMBER_ID}, Bob=${BOB_MEMBER_ID}, Carol=${CAROL_MEMBER_ID}"

  # Helper: create a single task
  # _seed_task <project_id> <title> <priority> <assignee_id> <due_date>
  _seed_task() {
    project_id="$1"
    title="$2"
    priority="$3"
    assignee_id="$4"
    due_date="$5"

    # Check if task already exists in this project
    existing=$(api_get "/api/projects/${project_id}/tasks?size=200" "$jwt" \
      | jq -r ".content[] | select(.title == \"${title}\") | .id" 2>/dev/null)
    if [ -n "$existing" ] && [ "$existing" != "null" ]; then
      echo "    [skip] ${title} (${existing})"
      echo "$existing"
      return 0
    fi

    assignee_field=""
    if [ -n "$assignee_id" ] && [ "$assignee_id" != "none" ]; then
      assignee_field="\"assigneeId\": \"${assignee_id}\","
    fi

    due_field=""
    if [ -n "$due_date" ] && [ "$due_date" != "none" ]; then
      due_field="\"dueDate\": \"${due_date}\","
    fi

    body=$(api_post "/api/projects/${project_id}/tasks" "{
      \"title\": \"${title}\",
      ${assignee_field}
      ${due_field}
      \"priority\": \"${priority}\"
    }" "$jwt")
    check_status "Create task: ${title}" || return 1
    echo "$body" | jq -r '.id'
  }

  # Helper: transition task status
  # _transition_task <task_id> <status>
  _transition_task() {
    task_id="$1"
    status="$2"
    api_put "/api/tasks/${task_id}" "{\"status\": \"${status}\"}" "$jwt" > /dev/null
    check_status "  -> ${status}"
  }

  today=$(date +%Y-%m-%d)
  next_week=$(date -v+7d +%Y-%m-%d 2>/dev/null || date -d "+7 days" +%Y-%m-%d)
  next_month=$(date -v+30d +%Y-%m-%d 2>/dev/null || date -d "+30 days" +%Y-%m-%d)

  echo ""
  echo "  -- Website Redesign tasks --"
  WR_T1=$(_seed_task "$WEBSITE_REDESIGN_ID" "Design homepage mockup" "HIGH" "$ALICE_MEMBER_ID" "$next_week")
  _transition_task "$WR_T1" "DONE"
  WR_T2=$(_seed_task "$WEBSITE_REDESIGN_ID" "Implement responsive navigation" "HIGH" "$BOB_MEMBER_ID" "$next_week")
  _transition_task "$WR_T2" "DONE"
  WR_T3=$(_seed_task "$WEBSITE_REDESIGN_ID" "Build contact form" "MEDIUM" "$CAROL_MEMBER_ID" "$next_month")
  _transition_task "$WR_T3" "IN_PROGRESS"
  WR_T4=$(_seed_task "$WEBSITE_REDESIGN_ID" "Set up analytics tracking" "MEDIUM" "$BOB_MEMBER_ID" "$next_month")
  _transition_task "$WR_T4" "IN_PROGRESS"
  WR_T5=$(_seed_task "$WEBSITE_REDESIGN_ID" "Write SEO meta tags" "LOW" "$ALICE_MEMBER_ID" "none")
  WR_T6=$(_seed_task "$WEBSITE_REDESIGN_ID" "Performance optimization" "URGENT" "none" "none")

  echo ""
  echo "  -- Brand Guidelines tasks --"
  BG_T1=$(_seed_task "$BRAND_GUIDELINES_ID" "Define color palette" "HIGH" "$ALICE_MEMBER_ID" "$next_week")
  _transition_task "$BG_T1" "DONE"
  BG_T2=$(_seed_task "$BRAND_GUIDELINES_ID" "Create typography guide" "MEDIUM" "$CAROL_MEMBER_ID" "$next_month")
  _transition_task "$BG_T2" "IN_PROGRESS"
  BG_T3=$(_seed_task "$BRAND_GUIDELINES_ID" "Design logo variations" "HIGH" "$ALICE_MEMBER_ID" "$next_month")

  echo ""
  echo "  -- Mobile App MVP tasks --"
  MA_T1=$(_seed_task "$MOBILE_APP_ID" "Set up React Native project" "HIGH" "$BOB_MEMBER_ID" "$next_week")
  _transition_task "$MA_T1" "DONE"
  MA_T2=$(_seed_task "$MOBILE_APP_ID" "Implement authentication flow" "URGENT" "$BOB_MEMBER_ID" "$next_week")
  _transition_task "$MA_T2" "IN_PROGRESS"
  MA_T3=$(_seed_task "$MOBILE_APP_ID" "Build dashboard screen" "HIGH" "$CAROL_MEMBER_ID" "$next_month")
  _transition_task "$MA_T3" "IN_PROGRESS"
  MA_T4=$(_seed_task "$MOBILE_APP_ID" "Push notification setup" "MEDIUM" "none" "$next_month")

  echo ""
  echo "  -- SEO Audit tasks (all done) --"
  SA_T1=$(_seed_task "$SEO_AUDIT_ID" "Crawl site structure" "HIGH" "$ALICE_MEMBER_ID" "none")
  _transition_task "$SA_T1" "DONE"
  SA_T2=$(_seed_task "$SEO_AUDIT_ID" "Analyze page speed metrics" "HIGH" "$BOB_MEMBER_ID" "none")
  _transition_task "$SA_T2" "DONE"
  SA_T3=$(_seed_task "$SEO_AUDIT_ID" "Write recommendations report" "MEDIUM" "$ALICE_MEMBER_ID" "none")
  _transition_task "$SA_T3" "DONE"

  echo ""
  echo "  -- Annual Report tasks --"
  AR_T1=$(_seed_task "$ANNUAL_REPORT_ID" "Gather financial data" "HIGH" "$ALICE_MEMBER_ID" "$next_month")
  AR_T2=$(_seed_task "$ANNUAL_REPORT_ID" "Draft executive summary" "MEDIUM" "$BOB_MEMBER_ID" "$next_month")

  echo ""
  echo "  -- Legacy Migration tasks (all done) --"
  LM_T1=$(_seed_task "$LEGACY_MIGRATION_ID" "Database schema migration" "URGENT" "$BOB_MEMBER_ID" "none")
  _transition_task "$LM_T1" "DONE"
  LM_T2=$(_seed_task "$LEGACY_MIGRATION_ID" "Data validation scripts" "HIGH" "$CAROL_MEMBER_ID" "none")
  _transition_task "$LM_T2" "DONE"

  # Export key task IDs for downstream modules (time entries, comments)
  export WR_T1 WR_T2 WR_T3 WR_T4 BG_T1 BG_T2 MA_T1 MA_T2 MA_T3 SA_T1 SA_T2 SA_T3 LM_T1 LM_T2

  echo ""
  echo "    Tasks seeded: 20 tasks across 6 projects"
}
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/tasks.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/tasks.sh
git commit -m "feat(seed): add tasks module — 20 tasks with status transitions"
```

---

### Task 5: Time Entries Module (`lib/time-entries.sh`)

**Files:**
- Create: `compose/seed/lib/time-entries.sh`

**Step 1: Create the time entries module**

```sh
#!/bin/sh
# compose/seed/lib/time-entries.sh — Log time entries on tasks
# Requires: lib/common.sh, lib/tasks.sh (task IDs, member IDs)

seed_time_entries() {
  echo ""
  echo "==> Seeding time entries"

  # Helper: log time as a specific user
  # _log_time <task_id> <user_id> <org_role> <date> <minutes> <billable> <description>
  _log_time() {
    task_id="$1"
    user_id="$2"
    org_role="$3"
    date="$4"
    minutes="$5"
    billable="$6"
    description="$7"

    jwt=$(get_jwt "$user_id" "$org_role")

    # Check if entry already exists (by description match on this task)
    existing=$(api_get "/api/tasks/${task_id}/time-entries" "$jwt" \
      | jq -r ".[] | select(.description == \"${description}\") | .id" 2>/dev/null)
    if [ -n "$existing" ] && [ "$existing" != "null" ]; then
      echo "    [skip] ${description}"
      return 0
    fi

    api_post "/api/tasks/${task_id}/time-entries" "{
      \"date\": \"${date}\",
      \"durationMinutes\": ${minutes},
      \"billable\": ${billable},
      \"description\": \"${description}\"
    }" "$jwt" > /dev/null
    check_status "Time: ${description} (${minutes}m)"
  }

  # Generate dates over the last 30 days
  d_1=$(date -v-1d +%Y-%m-%d 2>/dev/null || date -d "-1 day" +%Y-%m-%d)
  d_3=$(date -v-3d +%Y-%m-%d 2>/dev/null || date -d "-3 days" +%Y-%m-%d)
  d_5=$(date -v-5d +%Y-%m-%d 2>/dev/null || date -d "-5 days" +%Y-%m-%d)
  d_7=$(date -v-7d +%Y-%m-%d 2>/dev/null || date -d "-7 days" +%Y-%m-%d)
  d_10=$(date -v-10d +%Y-%m-%d 2>/dev/null || date -d "-10 days" +%Y-%m-%d)
  d_14=$(date -v-14d +%Y-%m-%d 2>/dev/null || date -d "-14 days" +%Y-%m-%d)
  d_21=$(date -v-21d +%Y-%m-%d 2>/dev/null || date -d "-21 days" +%Y-%m-%d)
  d_28=$(date -v-28d +%Y-%m-%d 2>/dev/null || date -d "-28 days" +%Y-%m-%d)

  echo ""
  echo "  -- Website Redesign time --"
  _log_time "$WR_T1" "user_e2e_alice" "owner" "$d_21" 240 true "Homepage mockup — initial concepts"
  _log_time "$WR_T1" "user_e2e_alice" "owner" "$d_14" 180 true "Homepage mockup — revisions"
  _log_time "$WR_T2" "user_e2e_bob" "admin" "$d_10" 120 true "Navigation component scaffold"
  _log_time "$WR_T2" "user_e2e_bob" "admin" "$d_7" 90 true "Navigation responsive breakpoints"
  _log_time "$WR_T3" "user_e2e_carol" "member" "$d_3" 60 true "Contact form wireframe"
  _log_time "$WR_T4" "user_e2e_bob" "admin" "$d_1" 45 false "Analytics research — internal"

  echo ""
  echo "  -- Brand Guidelines time --"
  _log_time "$BG_T1" "user_e2e_alice" "owner" "$d_28" 180 true "Color palette research and selection"
  _log_time "$BG_T2" "user_e2e_carol" "member" "$d_5" 120 true "Typography audit and pairing"

  echo ""
  echo "  -- Mobile App MVP time --"
  _log_time "$MA_T1" "user_e2e_bob" "admin" "$d_14" 180 true "React Native project setup"
  _log_time "$MA_T2" "user_e2e_bob" "admin" "$d_3" 240 true "Auth flow — OAuth integration"
  _log_time "$MA_T3" "user_e2e_carol" "member" "$d_1" 90 true "Dashboard screen layout"

  echo ""
  echo "  -- SEO Audit time --"
  _log_time "$SA_T1" "user_e2e_alice" "owner" "$d_28" 120 true "Crawl configuration and execution"
  _log_time "$SA_T2" "user_e2e_bob" "admin" "$d_21" 90 true "PageSpeed and Core Web Vitals analysis"
  _log_time "$SA_T3" "user_e2e_alice" "owner" "$d_14" 150 true "SEO recommendations writeup"

  echo ""
  echo "  -- Legacy Migration time --"
  _log_time "$LM_T1" "user_e2e_bob" "admin" "$d_28" 360 true "Schema migration scripts"
  _log_time "$LM_T2" "user_e2e_carol" "member" "$d_21" 120 true "Validation query development"

  echo ""
  echo "    Time entries seeded: 16 entries across 5 projects"
}
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/time-entries.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/time-entries.sh
git commit -m "feat(seed): add time entries module — 16 entries over 30 days"
```

---

### Task 6: Rates & Budgets Module (`lib/rates-budgets.sh`)

**Files:**
- Create: `compose/seed/lib/rates-budgets.sh`

**Step 1: Create the module**

```sh
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

  # Check existing rates to avoid duplicates
  existing_rates=$(api_get "/api/billing-rates" "$jwt")

  _rate_exists() {
    member_id="$1"
    echo "$existing_rates" | jq -e ".[] | select(.memberId == \"${member_id}\" and .projectId == null and .customerId == null)" > /dev/null 2>&1
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
    project_id="$1"
    label="$2"
    budget_json="$3"

    # Check if budget exists
    existing=$(api_get "/api/projects/${project_id}/budget" "$jwt")
    has_budget=$(echo "$existing" | jq -r '.budgetHours // .budgetAmount // empty' 2>/dev/null)
    if [ -n "$has_budget" ]; then
      echo "    [skip] ${label} budget exists"
      return 0
    fi

    api_put "/api/projects/${project_id}/budget" "$budget_json" "$jwt" > /dev/null
    check_status "${label} budget"
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
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/rates-budgets.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/rates-budgets.sh
git commit -m "feat(seed): add rates and budgets module"
```

---

### Task 7: Invoices Module (`lib/invoices.sh`)

**Files:**
- Create: `compose/seed/lib/invoices.sh`

**Step 1: Create the module**

```sh
#!/bin/sh
# compose/seed/lib/invoices.sh — Create invoices with lines
# Requires: lib/common.sh, customer IDs, project IDs

seed_invoices() {
  echo ""
  echo "==> Seeding invoices"
  jwt=$(get_jwt user_e2e_alice owner)

  # Check existing invoices
  existing=$(api_get "/api/invoices?size=200" "$jwt")

  # ── Invoice 1: Acme Corp (manual lines) ─────────────────────────
  acme_invoice=$(echo "$existing" | jq -r ".content[] | select(.customerName == \"Acme Corp\") | .id" 2>/dev/null | head -1)
  if [ -n "$acme_invoice" ] && [ "$acme_invoice" != "null" ]; then
    echo "    [skip] Acme Corp invoice exists (${acme_invoice})"
    ACME_INVOICE_ID="$acme_invoice"
  else
    body=$(api_post "/api/invoices" "{
      \"customerId\": \"${ACME_ID}\",
      \"currency\": \"USD\",
      \"notes\": \"Legacy migration completed work\",
      \"paymentTerms\": \"Net 30\"
    }" "$jwt")
    check_status "Create Acme Corp invoice" || return 1
    ACME_INVOICE_ID=$(echo "$body" | jq -r '.id')

    # Add manual lines
    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Database schema migration — 6 hrs",
      "quantity": 6,
      "unitPrice": 150.00,
      "sortOrder": 0
    }' "$jwt" > /dev/null
    check_status "  Line: schema migration"

    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Data validation scripts — 2 hrs",
      "quantity": 2,
      "unitPrice": 120.00,
      "sortOrder": 1
    }' "$jwt" > /dev/null
    check_status "  Line: data validation"

    api_post "/api/invoices/${ACME_INVOICE_ID}/lines" '{
      "description": "Project management and coordination",
      "quantity": 1,
      "unitPrice": 250.00,
      "sortOrder": 2
    }' "$jwt" > /dev/null
    check_status "  Line: project management"
  fi

  # ── Invoice 2: Bright Solutions (from unbilled time) ────────────
  bright_invoice=$(echo "$existing" | jq -r ".content[] | select(.customerName == \"Bright Solutions\") | .id" 2>/dev/null | head -1)
  if [ -n "$bright_invoice" ] && [ "$bright_invoice" != "null" ]; then
    echo "    [skip] Bright Solutions invoice exists (${bright_invoice})"
    BRIGHT_INVOICE_ID="$bright_invoice"
  else
    # Get unbilled time entry IDs for Bright Solutions tasks
    unbilled_ids=""
    for task_id in $MA_T1 $MA_T2 $MA_T3 $SA_T1 $SA_T2 $SA_T3; do
      ids=$(api_get "/api/tasks/${task_id}/time-entries" "$jwt" \
        | jq -r '[.[] | select(.invoiceId == null) | .id] | join(",")' 2>/dev/null)
      if [ -n "$ids" ] && [ "$ids" != "" ]; then
        unbilled_ids="${unbilled_ids:+${unbilled_ids},}${ids}"
      fi
    done

    # Convert comma list to JSON array
    time_entry_array=$(echo "$unbilled_ids" | tr ',' '\n' | jq -R . | jq -s .)

    body=$(api_post "/api/invoices" "{
      \"customerId\": \"${BRIGHT_ID}\",
      \"currency\": \"USD\",
      \"timeEntryIds\": ${time_entry_array},
      \"notes\": \"Mobile App and SEO Audit work\",
      \"paymentTerms\": \"Net 15\"
    }" "$jwt")
    check_status "Create Bright Solutions invoice (from time entries)" || return 1
    BRIGHT_INVOICE_ID=$(echo "$body" | jq -r '.id')
  fi

  export ACME_INVOICE_ID BRIGHT_INVOICE_ID

  echo ""
  echo "    Invoices seeded:"
  echo "      Acme Corp:        ${ACME_INVOICE_ID}"
  echo "      Bright Solutions: ${BRIGHT_INVOICE_ID}"
}
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/invoices.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/invoices.sh
git commit -m "feat(seed): add invoices module — manual lines and time-based"
```

---

### Task 8: Retainers Module (`lib/retainers.sh`)

**Files:**
- Create: `compose/seed/lib/retainers.sh`

**Step 1: Create the module**

```sh
#!/bin/sh
# compose/seed/lib/retainers.sh — Create retainer agreements
# Requires: lib/common.sh, ACME_ID

seed_retainers() {
  echo ""
  echo "==> Seeding retainers"
  jwt=$(get_jwt user_e2e_alice owner)

  existing=$(api_get "/api/retainers?size=200" "$jwt")
  acme_retainer=$(echo "$existing" | jq -r '.content[] | select(.name == "Monthly Support Retainer") | .id' 2>/dev/null)

  if [ -n "$acme_retainer" ] && [ "$acme_retainer" != "null" ]; then
    echo "    [skip] Monthly Support Retainer exists (${acme_retainer})"
    ACME_RETAINER_ID="$acme_retainer"
  else
    start_date=$(date -v-60d +%Y-%m-%d 2>/dev/null || date -d "-60 days" +%Y-%m-%d)

    body=$(api_post "/api/retainers" "{
      \"customerId\": \"${ACME_ID}\",
      \"name\": \"Monthly Support Retainer\",
      \"type\": \"HOURS_BASED\",
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
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/retainers.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/retainers.sh
git commit -m "feat(seed): add retainers module — monthly hours-based retainer"
```

---

### Task 9: Documents Module (`lib/documents.sh`)

**Files:**
- Create: `compose/seed/lib/documents.sh`

**Step 1: Create the module**

The document upload flow is: (1) init upload → get presigned URL, (2) PUT file to S3, (3) confirm upload. For seed purposes, we upload small text content.

```sh
#!/bin/sh
# compose/seed/lib/documents.sh — Upload documents via presigned URL flow
# Requires: lib/common.sh, project IDs, customer IDs

seed_documents() {
  echo ""
  echo "==> Seeding documents"
  jwt=$(get_jwt user_e2e_alice owner)

  # Helper: upload a document
  # _upload_doc <scope_path> <file_name> <content_type> <content> <label>
  _upload_doc() {
    scope_path="$1"
    file_name="$2"
    content_type="$3"
    content="$4"
    label="$5"

    # Check if document exists
    existing=$(api_get "${scope_path}/documents?size=200" "$jwt" \
      | jq -r ".content[] | select(.fileName == \"${file_name}\") | .id" 2>/dev/null)
    if [ -n "$existing" ] && [ "$existing" != "null" ]; then
      echo "    [skip] ${label} (${existing})"
      return 0
    fi

    size=$(echo -n "$content" | wc -c | tr -d ' ')

    # Step 1: Init upload
    init_body=$(api_post "${scope_path}/documents/upload-init" "{
      \"fileName\": \"${file_name}\",
      \"contentType\": \"${content_type}\",
      \"size\": ${size}
    }" "$jwt")
    check_status "Init ${label}" || return 1

    doc_id=$(echo "$init_body" | jq -r '.documentId')
    presigned_url=$(echo "$init_body" | jq -r '.presignedUrl')

    # Step 2: Upload content to S3
    curl -sf -X PUT "$presigned_url" \
      -H "Content-Type: ${content_type}" \
      -d "$content" > /dev/null
    echo "    [ok] Uploaded to S3"

    # Step 3: Confirm
    api_post "/api/documents/${doc_id}/confirm" '{}' "$jwt" > /dev/null
    check_status "Confirm ${label}"

    echo "$doc_id"
  }

  # ── Project-scoped: design mockup ──────────────────────────────
  _upload_doc "/api/projects/${WEBSITE_REDESIGN_ID}" \
    "design-mockup.pdf" "application/pdf" \
    "Placeholder content for design mockup document" \
    "design-mockup.pdf (Website Redesign)"

  # ── Customer-scoped: service agreement ─────────────────────────
  _upload_doc "/api/customers/${ACME_ID}" \
    "service-agreement.pdf" "application/pdf" \
    "Placeholder content for service agreement document" \
    "service-agreement.pdf (Acme Corp)"

  # ── Org-scoped: company policies ───────────────────────────────
  _upload_doc "/api" \
    "company-policies.pdf" "application/pdf" \
    "Placeholder content for company policies document" \
    "company-policies.pdf (Org)"

  echo ""
  echo "    Documents seeded: 3 documents"
}
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/documents.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/documents.sh
git commit -m "feat(seed): add documents module — presigned URL upload flow"
```

---

### Task 10: Comments Module (`lib/comments.sh`)

**Files:**
- Create: `compose/seed/lib/comments.sh`

**Step 1: Create the module**

```sh
#!/bin/sh
# compose/seed/lib/comments.sh — Add comments on tasks
# Requires: lib/common.sh, project IDs, task IDs

seed_comments() {
  echo ""
  echo "==> Seeding comments"

  # Helper: add a comment
  # _add_comment <project_id> <entity_type> <entity_id> <body> <visibility> <user_id> <org_role>
  _add_comment() {
    project_id="$1"
    entity_type="$2"
    entity_id="$3"
    body="$4"
    visibility="$5"
    user_id="$6"
    org_role="$7"

    jwt=$(get_jwt "$user_id" "$org_role")

    # Check existing comments (basic dedup by body substring)
    short_body=$(echo "$body" | head -c 40)
    existing=$(api_get "/api/projects/${project_id}/comments?entityType=${entity_type}&entityId=${entity_id}&size=200" "$jwt" \
      | jq -r ".content[] | select(.body | startswith(\"${short_body}\")) | .id" 2>/dev/null)
    if [ -n "$existing" ] && [ "$existing" != "null" ]; then
      echo "    [skip] Comment on ${entity_type} (${entity_id})"
      return 0
    fi

    api_post "/api/projects/${project_id}/comments" "{
      \"entityType\": \"${entity_type}\",
      \"entityId\": \"${entity_id}\",
      \"body\": \"${body}\",
      \"visibility\": \"${visibility}\"
    }" "$jwt" > /dev/null
    check_status "Comment on ${entity_type} by $(echo $user_id | sed 's/user_e2e_//')"
  }

  # Task comments on Website Redesign
  _add_comment "$WEBSITE_REDESIGN_ID" "TASK" "$WR_T3" \
    "Started the contact form layout. Using a two-column design for desktop, single column on mobile." \
    "INTERNAL" "user_e2e_carol" "member"

  _add_comment "$WEBSITE_REDESIGN_ID" "TASK" "$WR_T3" \
    "Looks good! Make sure to add client-side validation before the submit handler." \
    "INTERNAL" "user_e2e_bob" "admin"

  _add_comment "$WEBSITE_REDESIGN_ID" "TASK" "$WR_T4" \
    "Which analytics provider are we going with — GA4 or Plausible?" \
    "INTERNAL" "user_e2e_bob" "admin"

  # Shared comment (visible to portal)
  _add_comment "$MOBILE_APP_ID" "TASK" "$MA_T2" \
    "Authentication flow is working with OAuth. We need the client to confirm their SSO provider details." \
    "SHARED" "user_e2e_bob" "admin"

  _add_comment "$BRAND_GUIDELINES_ID" "TASK" "$BG_T2" \
    "Typography pairings are ready for review. Using Inter for headings, Source Serif for body." \
    "INTERNAL" "user_e2e_carol" "member"

  echo ""
  echo "    Comments seeded: 5 comments"
}
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/comments.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/comments.sh
git commit -m "feat(seed): add comments module — internal and shared visibility"
```

---

### Task 11: Proposals Module (`lib/proposals.sh`)

**Files:**
- Create: `compose/seed/lib/proposals.sh`

**Step 1: Create the module**

```sh
#!/bin/sh
# compose/seed/lib/proposals.sh — Create proposals with milestones
# Requires: lib/common.sh, BRIGHT_ID, member IDs

seed_proposals() {
  echo ""
  echo "==> Seeding proposals"
  jwt=$(get_jwt user_e2e_alice owner)

  existing=$(api_get "/api/proposals?size=200" "$jwt")
  bright_proposal=$(echo "$existing" | jq -r '.content[] | select(.title == "E-Commerce Platform Build") | .id' 2>/dev/null)

  if [ -n "$bright_proposal" ] && [ "$bright_proposal" != "null" ]; then
    echo "    [skip] E-Commerce Platform Build proposal exists (${bright_proposal})"
  else
    expires_at=$(date -v+30d -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -d "+30 days" -u +%Y-%m-%dT%H:%M:%SZ)

    body=$(api_post "/api/proposals" "{
      \"title\": \"E-Commerce Platform Build\",
      \"customerId\": \"${BRIGHT_ID}\",
      \"feeModel\": \"FIXED_FEE\",
      \"fixedFeeAmount\": 8500.00,
      \"fixedFeeCurrency\": \"USD\",
      \"expiresAt\": \"${expires_at}\"
    }" "$jwt")
    check_status "Create proposal" || return 1
    proposal_id=$(echo "$body" | jq -r '.id')

    # Add milestones
    api_put "/api/proposals/${proposal_id}/milestones" '[
      {"description": "Discovery and wireframes", "percentage": 30, "relativeDueDays": 14},
      {"description": "Development and launch", "percentage": 70, "relativeDueDays": 45}
    ]' "$jwt" > /dev/null
    check_status "Add milestones"

    # Add team
    api_put "/api/proposals/${proposal_id}/team" "[
      {\"memberId\": \"${ALICE_MEMBER_ID}\", \"role\": \"Lead\"},
      {\"memberId\": \"${BOB_MEMBER_ID}\", \"role\": \"Developer\"}
    ]" "$jwt" > /dev/null
    check_status "Add team members"

    echo "    Proposal ID: ${proposal_id}"
  fi

  echo ""
  echo "    Proposals seeded: 1 proposal"
}
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/proposals.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/proposals.sh
git commit -m "feat(seed): add proposals module — fixed-fee with milestones"
```

---

### Task 12: Reset Module (`lib/reset.sh`)

**Files:**
- Create: `compose/seed/lib/reset.sh`

**Step 1: Create the module**

```sh
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
```

**Step 2: Verify syntax**

Run: `sh -n compose/seed/lib/reset.sh`

**Step 3: Commit**

```bash
git add compose/seed/lib/reset.sh
git commit -m "feat(seed): add reset module — wipe and re-provision"
```

---

### Task 13: Orchestrator (`rich-seed.sh`)

**Files:**
- Create: `compose/seed/rich-seed.sh`

**Step 1: Create the orchestrator**

```sh
#!/bin/sh
# compose/seed/rich-seed.sh — Orchestrate rich E2E data seeding
#
# Usage:
#   bash compose/seed/rich-seed.sh              # Idempotent — adds missing data
#   bash compose/seed/rich-seed.sh --reset      # Wipe + recreate everything
#   bash compose/seed/rich-seed.sh --only customers,projects  # Specific modules
#
# Runs from repo root (host) or inside Docker container.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Source shared library
. "${SCRIPT_DIR}/lib/common.sh"

# ── Parse flags ──────────────────────────────────────────────────────
RESET=false
ONLY=""

while [ $# -gt 0 ]; do
  case "$1" in
    --reset) RESET=true; shift ;;
    --only)  ONLY="$2"; shift 2 ;;
    --only=*) ONLY="${1#--only=}"; shift ;;
    *) echo "Unknown flag: $1"; exit 1 ;;
  esac
done

# Helper: check if a module should run
should_run() {
  module="$1"
  if [ -z "$ONLY" ]; then
    return 0  # Run everything
  fi
  echo ",$ONLY," | grep -q ",${module},"
}

# ── Banner ───────────────────────────────────────────────────────────
echo "============================================"
echo "  E2E Rich Seed"
echo "============================================"
echo "  Backend:  ${BACKEND_URL}"
echo "  Mock IDP: ${MOCK_IDP_URL}"
echo "  Reset:    ${RESET}"
echo "  Only:     ${ONLY:-all}"
echo "============================================"

# ── Reset (if requested) ────────────────────────────────────────────
if [ "$RESET" = true ]; then
  . "${SCRIPT_DIR}/lib/reset.sh"
  do_reset
fi

# ── Verify backend is healthy ───────────────────────────────────────
if ! curl -sf "${BACKEND_URL}/actuator/health" > /dev/null 2>&1; then
  echo "ERROR: Backend not reachable at ${BACKEND_URL}"
  echo "Start the E2E stack first: bash compose/scripts/e2e-up.sh"
  exit 1
fi

# ── Run modules in dependency order ──────────────────────────────────

if should_run "customers"; then
  . "${SCRIPT_DIR}/lib/customers.sh"
  seed_customers
fi

if should_run "projects"; then
  . "${SCRIPT_DIR}/lib/projects.sh"
  seed_projects
fi

if should_run "tasks"; then
  . "${SCRIPT_DIR}/lib/tasks.sh"
  seed_tasks
fi

if should_run "time-entries"; then
  . "${SCRIPT_DIR}/lib/time-entries.sh"
  seed_time_entries
fi

if should_run "rates-budgets"; then
  . "${SCRIPT_DIR}/lib/rates-budgets.sh"
  seed_rates_budgets
fi

if should_run "invoices"; then
  . "${SCRIPT_DIR}/lib/invoices.sh"
  seed_invoices
fi

if should_run "retainers"; then
  . "${SCRIPT_DIR}/lib/retainers.sh"
  seed_retainers
fi

if should_run "documents"; then
  . "${SCRIPT_DIR}/lib/documents.sh"
  seed_documents
fi

if should_run "comments"; then
  . "${SCRIPT_DIR}/lib/comments.sh"
  seed_comments
fi

if should_run "proposals"; then
  . "${SCRIPT_DIR}/lib/proposals.sh"
  seed_proposals
fi

# ── Summary ──────────────────────────────────────────────────────────
echo ""
echo "============================================"
echo "  E2E Rich Seed Complete!"
echo "============================================"
echo ""
echo "  Customers:    4 (Acme, Bright, Carlos, Dormant)"
echo "  Projects:     6"
echo "  Tasks:        20"
echo "  Time entries: 16"
echo "  Invoices:     2"
echo "  Retainers:    1"
echo "  Documents:    3"
echo "  Comments:     5"
echo "  Proposals:    1"
echo ""
echo "  Login: http://localhost:3001/mock-login"
echo ""
```

**Step 2: Make executable and verify syntax**

Run: `chmod +x compose/seed/rich-seed.sh && sh -n compose/seed/rich-seed.sh`

**Step 3: Commit**

```bash
git add compose/seed/rich-seed.sh
git commit -m "feat(seed): add rich-seed orchestrator with --reset and --only flags"
```

---

### Task 14: Smoke Test

**Step 1: Verify E2E stack is running**

Run: `curl -sf http://localhost:8081/actuator/health | jq .status`
Expected: `"UP"`

If not running: `bash compose/scripts/e2e-up.sh`

**Step 2: Run the rich seed**

Run: `bash compose/seed/rich-seed.sh`
Expected: All steps show `[ok]` or `[skip]`, no `[FAIL]` lines.

**Step 3: Run again to verify idempotency**

Run: `bash compose/seed/rich-seed.sh`
Expected: All steps show `[skip]` (everything already exists).

**Step 4: Test --only flag**

Run: `bash compose/seed/rich-seed.sh --only customers`
Expected: Only customers module runs, others skipped.

**Step 5: Fix any issues found during smoke testing**

Debug and fix any curl/jq issues. Common problems:
- JSON body quoting (single vs double quotes in shell)
- jq filter paths not matching actual API response shape
- Pagination: some list endpoints use `.content[]`, others return bare arrays
- Status code edge cases

**Step 6: Commit any fixes**

```bash
git add compose/seed/
git commit -m "fix(seed): smoke test fixes for rich seed scripts"
```

---

### Task 15: Update Dockerfile (optional — only if seed should run in Docker)

**Files:**
- Modify: `compose/seed/Dockerfile`

**Step 1: Add lib/ directory to Docker image**

Current Dockerfile only copies `seed.sh` and `wait-for-backend.sh`. If you want `rich-seed.sh` runnable inside Docker:

```dockerfile
FROM alpine:3.19

RUN apk add --no-cache curl jq

WORKDIR /seed

COPY wait-for-backend.sh .
COPY seed.sh .
COPY rich-seed.sh .
COPY lib/ lib/

RUN chmod +x wait-for-backend.sh seed.sh rich-seed.sh

CMD ["./seed.sh"]
```

Note: CMD stays as `./seed.sh` (minimal boot seed). Rich seed is run manually.

**Step 2: Commit**

```bash
git add compose/seed/Dockerfile
git commit -m "feat(seed): include rich-seed lib in Docker image"
```

---

Plan complete and saved to `docs/plans/2026-03-01-e2e-seed-library-plan.md`. Two execution options:

**1. Subagent-Driven (this session)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** — Open new session with executing-plans, batch execution with checkpoints

Which approach?