#!/bin/bash
# 642A rich dataset, step 2 of 3 (after `bash compose/seed/rich-seed.sh`, before the OBS run that
# creates "OBS 642A view" through the Save View dialog). API only (no SQL). Idempotent enough for a
# fresh stack; run it ONCE per stack. Log: setup-rich-api.txt.
#   bash setup-rich-api.sh          # apply
#   bash setup-rich-api.sh revert   # after the rich OBS capture: un-require referred_by (before specs)
set -u
B=${BACKEND_URL:-http://localhost:8081}
IDP=${MOCK_IDP_URL:-http://localhost:8090}
T=$(curl -sf -X POST "$IDP/token" -H 'Content-Type: application/json' \
  -d '{"userId":"user_e2e_alice","orgId":"e2e-test-org","orgSlug":"e2e-test-org","orgRole":"owner"}' | jq -r .access_token)
H=(-H "Authorization: Bearer $T" -H 'Content-Type: application/json')
req() { # method path [body] -> prints "<METHOD> <path> -> HTTP <code> <short body>"; body to $OUTB
  local m=$1 p=$2 b=${3:-}
  if [ -n "$b" ]; then OUTB=$(curl -s -w '\n%{http_code}' -X "$m" "$B$p" "${H[@]}" -d "$b"); else OUTB=$(curl -s -w '\n%{http_code}' -X "$m" "$B$p" "${H[@]}"); fi
  CODE=$(echo "$OUTB" | tail -1); OUTB=$(echo "$OUTB" | sed '$d')
  echo "$m $p -> HTTP $CODE $(echo "$OUTB" | head -c 240)"
}
cid() { curl -s "$B/api/clients" "${H[@]}" | jq -r --arg n "$1" '.[] | select(.name==$n) | .id'; }
fd_id() { curl -s "$B/api/field-definitions?entityType=CUSTOMER" "${H[@]}" | jq -r --arg s "$1" '.[] | select(.slug==$s) | .id'; }

if [ "${1:-}" = "revert" ]; then
  echo "== revert: referred_by requiredForContexts -> []"
  req PATCH "/api/field-definitions/$(fd_id referred_by)" '{"requiredForContexts":[]}'
  exit 0
fi

echo "== 1. tags"
for spec in "VIP:#7c3aed" "Corporate:#2563eb" "Estate:#059669" "Litigation:#dc2626"; do
  n=${spec%%:*}; c=${spec#*:}
  req POST /api/tags "{\"name\":\"$n\",\"color\":\"$c\"}"
  eval "TAG_$n=$(echo "$OUTB" | jq -r .id)"
done

echo "== 2. clients (POST /api/clients -> PROSPECT, FICA NOT_STARTED)"
req POST /api/clients '{"name":"Zulu Prospect Holdings","email":"info@zulu-prospect.example.com","clientType":"COMPANY","addressLine1":"9 Loop Street","city":"Cape Town","postalCode":"8001","country":"ZA","customFields":{"referred_by":"Website"}}'
req POST /api/clients '{"name":"Mkhize Family Trust","email":"trustees@mkhize.example.com","phone":"+27 21 555 0199","clientType":"TRUST","addressLine1":"3 Kloof Nek Road","city":"Cape Town","postalCode":"8001","country":"ZA"}'

ACME=$(cid "Acme Corp"); BRIGHT=$(cid "Bright Solutions"); DORM=$(cid "Dormant Industries"); ZULU=$(cid "Zulu Prospect Holdings")
echo "ids: Acme=$ACME Bright=$BRIGHT Dormant=$DORM Zulu=$ZULU"

echo "== 3. client tags"
req POST "/api/clients/$ACME/tags" "{\"tagIds\":[\"${TAG_VIP}\",\"${TAG_Corporate}\"]}"
req POST "/api/clients/$BRIGHT/tags" "{\"tagIds\":[\"${TAG_VIP}\"]}"
req POST "/api/clients/$DORM/tags" "{\"tagIds\":[\"${TAG_Estate}\"]}"
req POST "/api/clients/$ZULU/tags" "{\"tagIds\":[\"${TAG_VIP}\",\"${TAG_Corporate}\",\"${TAG_Estate}\",\"${TAG_Litigation}\"]}"

echo "== 4. trust deposits (primary account)"
ACC=$(curl -s "$B/api/trust-accounts" "${H[@]}" | jq -r '.[] | select(.isPrimary==true) | .id' | head -1)
M_BRIGHT=$(curl -s "$B/api/matters" "${H[@]}" | jq -r '.[] | select(.name=="SEO Audit") | .id')
M_DORM=$(curl -s "$B/api/matters" "${H[@]}" | jq -r '.[] | select(.name=="Legacy Migration") | .id')
TODAY=$(date +%Y-%m-%d)
req POST "/api/trust-accounts/$ACC/transactions/deposit" "{\"clientId\":\"$BRIGHT\",\"matterId\":\"$M_BRIGHT\",\"amount\":15000.00,\"reference\":\"OBS642A-DEP-1\",\"description\":\"OBS 642A deposit\",\"transactionDate\":\"$TODAY\"}"
echo "   status=$(echo "$OUTB" | jq -r .status)"
req POST "/api/trust-accounts/$ACC/transactions/deposit" "{\"clientId\":\"$DORM\",\"matterId\":\"$M_DORM\",\"amount\":2500.00,\"reference\":\"OBS642A-DEP-2\",\"description\":\"OBS 642A deposit\",\"transactionDate\":\"$TODAY\"}"
echo "   status=$(echo "$OUTB" | jq -r .status)"

echo "== 5. completeness: referred_by required for DOCUMENT_GENERATION (Zulu has it filled -> 100%, others 0%)"
req PATCH "/api/field-definitions/$(fd_id referred_by)" '{"requiredForContexts":["DOCUMENT_GENERATION"]}'

echo "== result: GET /api/clients"
curl -s "$B/api/clients" "${H[@]}" | jq -c '.[] | {name,clientType,lifecycleStatus,ficaStatus,openMatterCount,outstandingFeeNoteTotal,outstandingFeeNoteCount,trustBalance,completenessPercent,tags:[.tags[].name]}'
