#!/bin/sh
# compose/seed/lib/comments.sh — Add comments on tasks
# Requires: lib/common.sh, project IDs, task IDs

seed_comments() {
  echo ""
  echo "==> Seeding comments"

  # Helper: add a comment
  # _add_comment <project_id> <entity_type> <entity_id> <body> <visibility> <user_id> <org_role>
  _add_comment() {
    _ac_project_id="$1"
    _ac_entity_type="$2"
    _ac_entity_id="$3"
    _ac_body="$4"
    _ac_visibility="$5"
    _ac_user_id="$6"
    _ac_org_role="$7"

    _ac_jwt=$(get_jwt "$_ac_user_id" "$_ac_org_role")

    # Check existing comments (bare array response, dedup by body substring)
    _ac_short_body=$(echo "$_ac_body" | head -c 40)
    _ac_existing=$(api_get "/api/projects/${_ac_project_id}/comments?entityType=${_ac_entity_type}&entityId=${_ac_entity_id}&size=200" "$_ac_jwt" \
      | jq -r ".[] | select(.body | startswith(\"${_ac_short_body}\")) | .id" 2>/dev/null)
    if [ -n "$_ac_existing" ] && [ "$_ac_existing" != "null" ]; then
      echo "    [skip] Comment on ${_ac_entity_type} (${_ac_entity_id})" >&2
      return 0
    fi

    api_post "/api/projects/${_ac_project_id}/comments" "{
      \"entityType\": \"${_ac_entity_type}\",
      \"entityId\": \"${_ac_entity_id}\",
      \"body\": \"${_ac_body}\",
      \"visibility\": \"${_ac_visibility}\"
    }" "$_ac_jwt" > /dev/null
    check_status "Comment on ${_ac_entity_type} by $(echo "$_ac_user_id" | sed 's/user_e2e_//')"
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
