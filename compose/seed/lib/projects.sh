#!/bin/sh
# compose/seed/lib/projects.sh — Create projects across customers
# Requires: lib/common.sh + lib/customers.sh (ACME_ID, BRIGHT_ID, etc.)

seed_projects() {
  echo ""
  echo "==> Seeding projects"
  jwt=$(get_jwt user_e2e_alice owner)

  # Get member IDs for project member assignment
  members_json=$(api_get "/api/members" "$jwt")
  BOB_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Bob Admin") | .id')
  CAROL_MEMBER_ID=$(echo "$members_json" | jq -r '.[] | select(.name == "Carol Member") | .id')
  export BOB_MEMBER_ID CAROL_MEMBER_ID

  _create_project() {
    _cp_name="$1"
    _cp_description="$2"
    _cp_customer_id="$3"

    # Check if project already exists (bare array response)
    _cp_id=$(find_existing "/api/projects" ".[] | select(.name == \"${_cp_name}\")" "$jwt")

    if [ -n "$_cp_id" ] && [ "$_cp_id" != "null" ]; then
      echo "    [skip] ${_cp_name} already exists (${_cp_id})" >&2
      echo "$_cp_id"
      return 0
    fi

    _cp_body=$(api_post "/api/projects" "{
      \"name\": \"${_cp_name}\",
      \"description\": \"${_cp_description}\",
      \"customerId\": \"${_cp_customer_id}\"
    }" "$jwt")
    check_status "Create ${_cp_name}" || return 1
    _cp_id=$(echo "$_cp_body" | jq -r '.id')

    # Add Bob and Carol as project members
    api_post "/api/projects/${_cp_id}/members" "{\"memberId\":\"${BOB_MEMBER_ID}\",\"projectRole\":\"contributor\"}" "$jwt" > /dev/null
    check_status "  Add Bob to ${_cp_name}"
    api_post "/api/projects/${_cp_id}/members" "{\"memberId\":\"${CAROL_MEMBER_ID}\",\"projectRole\":\"contributor\"}" "$jwt" > /dev/null
    check_status "  Add Carol to ${_cp_name}"

    echo "$_cp_id"
  }

  # ── Acme Corp projects ──────────────────────────────────────────
  WEBSITE_REDESIGN_ID=$(_create_project "Website Redesign" "Full website overhaul with new branding" "$ACME_ID")
  BRAND_GUIDELINES_ID=$(_create_project "Brand Guidelines" "Develop comprehensive brand guide" "$ACME_ID")

  # ── Bright Solutions projects ────────────────────────────────────
  MOBILE_APP_ID=$(_create_project "Mobile App MVP" "Cross-platform mobile app first release" "$BRIGHT_ID")
  SEO_AUDIT_ID=$(_create_project "SEO Audit" "Technical SEO audit and recommendations" "$BRIGHT_ID")

  # ── Carlos Mendez project ───────────────────────────────────────
  ANNUAL_REPORT_ID=$(_create_project "Annual Report" "2025 annual financial report" "$CARLOS_ID")

  # ── Dormant Industries project ──────────────────────────────────
  LEGACY_MIGRATION_ID=$(_create_project "Legacy Migration" "Migrate legacy systems to cloud" "$DORMANT_ID")

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
