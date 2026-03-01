#!/bin/sh
# compose/seed/lib/proposals.sh — Create proposals with milestones
# Requires: lib/common.sh, BRIGHT_ID, member IDs

seed_proposals() {
  echo ""
  echo "==> Seeding proposals"
  jwt=$(get_jwt user_e2e_alice owner)

  # proposals endpoint is paginated ({content:[...]}), use .content[]
  existing=$(api_get "/api/proposals?size=200" "$jwt")
  bright_proposal=$(echo "$existing" | jq -r '.content[] | select(.title == "E-Commerce Platform Build") | .id' 2>/dev/null)

  if [ -n "$bright_proposal" ] && [ "$bright_proposal" != "null" ]; then
    echo "    [skip] E-Commerce Platform Build proposal exists (${bright_proposal})"
  else
    expires_at=$(date -v+30d -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -d "+30 days" -u +%Y-%m-%dT%H:%M:%SZ)

    body=$(api_post "/api/proposals" "{
      \"title\": \"E-Commerce Platform Build\",
      \"customerId\": \"${BRIGHT_ID}\",
      \"feeModel\": \"FIXED\",
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
