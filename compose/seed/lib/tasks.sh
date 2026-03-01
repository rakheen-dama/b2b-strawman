#!/bin/sh
# compose/seed/lib/tasks.sh — Create tasks across projects
# Requires: lib/common.sh, lib/projects.sh (project IDs), member IDs

seed_tasks() {
  echo ""
  echo "==> Seeding tasks"
  jwt=$(get_jwt user_e2e_alice owner)

  # Helper: get member IDs by name (bare array response)
  members_json=$(api_get "/api/members" "$jwt")
  ALICE_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Alice Owner") | .id')
  BOB_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Bob Admin") | .id')
  CAROL_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Carol Member") | .id')
  export ALICE_MEMBER_ID BOB_MEMBER_ID CAROL_MEMBER_ID

  echo "    Member IDs: Alice=${ALICE_MEMBER_ID}, Bob=${BOB_MEMBER_ID}, Carol=${CAROL_MEMBER_ID}"

  # Helper: create a single task
  # _seed_task <project_id> <title> <priority> <assignee_id> <due_date>
  # Called via $(), so [skip] must go to stderr
  _seed_task() {
    _st_project_id="$1"
    _st_title="$2"
    _st_priority="$3"
    _st_assignee_id="$4"
    _st_due_date="$5"

    # Check if task already exists in this project (bare array response)
    _st_existing=$(api_get "/api/projects/${_st_project_id}/tasks?size=200" "$jwt" \
      | jq -r ".[] | select(.title == \"${_st_title}\") | .id" 2>/dev/null)
    if [ -n "$_st_existing" ] && [ "$_st_existing" != "null" ]; then
      echo "    [skip] ${_st_title} (${_st_existing})" >&2
      echo "$_st_existing"
      return 0
    fi

    _st_assignee_field=""
    if [ -n "$_st_assignee_id" ] && [ "$_st_assignee_id" != "none" ]; then
      _st_assignee_field="\"assigneeId\": \"${_st_assignee_id}\","
    fi

    _st_due_field=""
    if [ -n "$_st_due_date" ] && [ "$_st_due_date" != "none" ]; then
      _st_due_field="\"dueDate\": \"${_st_due_date}\","
    fi

    _st_body=$(api_post "/api/projects/${_st_project_id}/tasks" "{
      \"title\": \"${_st_title}\",
      ${_st_assignee_field}
      ${_st_due_field}
      \"priority\": \"${_st_priority}\"
    }" "$jwt")
    check_status "Create task: ${_st_title}" || return 1
    echo "$_st_body" | jq -r '.id'
  }

  # Helper: transition task to DONE via PATCH /api/tasks/{id}/complete
  _complete_task() {
    _ct_task_id="$1"
    curl -s -o "$_API_TMPFILE.body" -w "%{http_code}" \
      -X PATCH "${BACKEND_URL}/api/tasks/${_ct_task_id}/complete" \
      -H "Authorization: Bearer ${jwt}" > "$_API_TMPFILE"
    check_status "  -> DONE"
  }

  # Helper: transition task to IN_PROGRESS via PUT (requires title+status+priority)
  _start_task() {
    _stt_task_id="$1"

    # Fetch current task to get title and priority
    _stt_task=$(api_get "/api/tasks/${_stt_task_id}" "$jwt")
    _stt_title=$(echo "$_stt_task" | jq -r '.title')
    _stt_priority=$(echo "$_stt_task" | jq -r '.priority')

    api_put "/api/tasks/${_stt_task_id}" "{
      \"title\": \"${_stt_title}\",
      \"status\": \"IN_PROGRESS\",
      \"priority\": \"${_stt_priority}\"
    }" "$jwt" > /dev/null
    check_status "  -> IN_PROGRESS"
  }

  today=$(date +%Y-%m-%d)
  next_week=$(date -v+7d +%Y-%m-%d 2>/dev/null || date -d "+7 days" +%Y-%m-%d)
  next_month=$(date -v+30d +%Y-%m-%d 2>/dev/null || date -d "+30 days" +%Y-%m-%d)

  echo ""
  echo "  -- Website Redesign tasks --"
  WR_T1=$(_seed_task "$WEBSITE_REDESIGN_ID" "Design homepage mockup" "HIGH" "$ALICE_MEMBER_ID" "$next_week")
  _complete_task "$WR_T1"
  WR_T2=$(_seed_task "$WEBSITE_REDESIGN_ID" "Implement responsive navigation" "HIGH" "$BOB_MEMBER_ID" "$next_week")
  _complete_task "$WR_T2"
  WR_T3=$(_seed_task "$WEBSITE_REDESIGN_ID" "Build contact form" "MEDIUM" "$CAROL_MEMBER_ID" "$next_month")
  _start_task "$WR_T3"
  WR_T4=$(_seed_task "$WEBSITE_REDESIGN_ID" "Set up analytics tracking" "MEDIUM" "$BOB_MEMBER_ID" "$next_month")
  _start_task "$WR_T4"
  WR_T5=$(_seed_task "$WEBSITE_REDESIGN_ID" "Write SEO meta tags" "LOW" "$ALICE_MEMBER_ID" "none")
  WR_T6=$(_seed_task "$WEBSITE_REDESIGN_ID" "Performance optimization" "URGENT" "none" "none")

  echo ""
  echo "  -- Brand Guidelines tasks --"
  BG_T1=$(_seed_task "$BRAND_GUIDELINES_ID" "Define color palette" "HIGH" "$ALICE_MEMBER_ID" "$next_week")
  _complete_task "$BG_T1"
  BG_T2=$(_seed_task "$BRAND_GUIDELINES_ID" "Create typography guide" "MEDIUM" "$CAROL_MEMBER_ID" "$next_month")
  _start_task "$BG_T2"
  BG_T3=$(_seed_task "$BRAND_GUIDELINES_ID" "Design logo variations" "HIGH" "$ALICE_MEMBER_ID" "$next_month")

  echo ""
  echo "  -- Mobile App MVP tasks --"
  MA_T1=$(_seed_task "$MOBILE_APP_ID" "Set up React Native project" "HIGH" "$BOB_MEMBER_ID" "$next_week")
  _complete_task "$MA_T1"
  MA_T2=$(_seed_task "$MOBILE_APP_ID" "Implement authentication flow" "URGENT" "$BOB_MEMBER_ID" "$next_week")
  _start_task "$MA_T2"
  MA_T3=$(_seed_task "$MOBILE_APP_ID" "Build dashboard screen" "HIGH" "$CAROL_MEMBER_ID" "$next_month")
  _start_task "$MA_T3"
  MA_T4=$(_seed_task "$MOBILE_APP_ID" "Push notification setup" "MEDIUM" "none" "$next_month")

  echo ""
  echo "  -- SEO Audit tasks (all done) --"
  SA_T1=$(_seed_task "$SEO_AUDIT_ID" "Crawl site structure" "HIGH" "$ALICE_MEMBER_ID" "none")
  _complete_task "$SA_T1"
  SA_T2=$(_seed_task "$SEO_AUDIT_ID" "Analyze page speed metrics" "HIGH" "$BOB_MEMBER_ID" "none")
  _complete_task "$SA_T2"
  SA_T3=$(_seed_task "$SEO_AUDIT_ID" "Write recommendations report" "MEDIUM" "$ALICE_MEMBER_ID" "none")
  _complete_task "$SA_T3"

  echo ""
  echo "  -- Annual Report tasks --"
  AR_T1=$(_seed_task "$ANNUAL_REPORT_ID" "Gather financial data" "HIGH" "$ALICE_MEMBER_ID" "$next_month")
  AR_T2=$(_seed_task "$ANNUAL_REPORT_ID" "Draft executive summary" "MEDIUM" "$BOB_MEMBER_ID" "$next_month")

  echo ""
  echo "  -- Legacy Migration tasks (all done) --"
  LM_T1=$(_seed_task "$LEGACY_MIGRATION_ID" "Database schema migration" "URGENT" "$BOB_MEMBER_ID" "none")
  _complete_task "$LM_T1"
  LM_T2=$(_seed_task "$LEGACY_MIGRATION_ID" "Data validation scripts" "HIGH" "$CAROL_MEMBER_ID" "none")
  _complete_task "$LM_T2"

  # Export key task IDs for downstream modules (time entries, comments)
  export WR_T1 WR_T2 WR_T3 WR_T4 BG_T1 BG_T2 MA_T1 MA_T2 MA_T3 SA_T1 SA_T2 SA_T3 LM_T1 LM_T2

  echo ""
  echo "    Tasks seeded: 20 tasks across 6 projects"
}
