#!/bin/sh
# compose/seed/lib/time-entries.sh — Log time entries on tasks
# Requires: lib/common.sh, lib/tasks.sh (task IDs, member IDs)

seed_time_entries() {
  echo ""
  echo "==> Seeding time entries"

  # Helper: log time as a specific user
  # _log_time <task_id> <user_id> <org_role> <date> <minutes> <billable> <description>
  _log_time() {
    _lt_task_id="$1"
    _lt_user_id="$2"
    _lt_org_role="$3"
    _lt_date="$4"
    _lt_minutes="$5"
    _lt_billable="$6"
    _lt_description="$7"

    _lt_jwt=$(get_jwt "$_lt_user_id" "$_lt_org_role")

    # Check if entry already exists (bare array response)
    _lt_existing=$(api_get "/api/tasks/${_lt_task_id}/time-entries" "$_lt_jwt" \
      | jq -r ".[] | select(.description == \"${_lt_description}\") | .id" 2>/dev/null)
    if [ -n "$_lt_existing" ] && [ "$_lt_existing" != "null" ]; then
      echo "    [skip] ${_lt_description}"
      return 0
    fi

    api_post "/api/tasks/${_lt_task_id}/time-entries" "{
      \"date\": \"${_lt_date}\",
      \"durationMinutes\": ${_lt_minutes},
      \"billable\": ${_lt_billable},
      \"description\": \"${_lt_description}\"
    }" "$_lt_jwt" > /dev/null
    check_status "Time: ${_lt_description} (${_lt_minutes}m)"
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
