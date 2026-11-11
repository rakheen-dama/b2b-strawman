#!/usr/bin/env bash
# PR #1755 supplemental (sourced; lib.sh + obs.sh loaded): a DEPOSIT, a REFUND and a PAYMENT on Acme / Website Redesign
# through the firm API (Alice records, Bob approves — no SQL writes), then a Statement of Account via
# POST /api/matters/{id}/statements, so the Payments Type column shows more than one debit type; plus the
# trust-activity emails (which now read TrustTransactionTypeLabels). The DB is only read.
trust_probe(){ # label
  local L=$1 F=$RAW/obs-trust-$1.txt B=http://localhost:8081 T; T=$(tenant)
  local TODAY; TODAY=$(TZ=Africa/Johannesburg date +%F)
  local FROM; FROM=$(date -j -v-30d -f %F "$TODAY" +%F)
  {
  echo "===== trust/SoA probe $L $(ts) today(SA)=$TODAY tenant=$T"
  local ALICE BOB
  ALICE=$(curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' -d '{"userId":"user_e2e_alice","orgId":"e2e-test-org","orgSlug":"e2e-test-org","orgRole":"owner"}' | jq -r .access_token)
  BOB=$(curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' -d '{"userId":"user_e2e_bob","orgId":"e2e-test-org","orgSlug":"e2e-test-org","orgRole":"admin"}' | jq -r .access_token)
  echo "tokens: alice=$([ ${#ALICE} -gt 20 ] && echo ok || echo MISSING) bob=$([ ${#BOB} -gt 20 ] && echo ok || echo MISSING)"
  local TA CUST MATTER
  TA=$(curl -s $B/api/trust-accounts -H "Authorization: Bearer $ALICE" | jq -r '.[] | select(.accountName=="Mathebula & Partners Trust Account") | .id')
  MATTER=$(psql_e2e "select id from $T.projects where name='Website Redesign' limit 1")
  CUST=$(psql_e2e "select customer_id from $T.projects where id='$MATTER'")
  echo "trust account=$TA matter=$MATTER customer=$CUST ($(psql_e2e "select name from $T.customers where id='$CUST'"))"
  post(){ curl -s -w '\n%{http_code}' -X POST "$B$1" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' -d "$3"; }
  local r id
  for spec in "deposit|10000.00|GATE/DEP/1755|Gate deposit" "refund|1500.00|GATE/REF/1755|Gate refund to client" "payment|2000.00|GATE/PAY/1755|Gate payment to sheriff"; do
    IFS='|' read -r kind amt ref desc <<< "$spec"
    body=$(jq -nc --arg c "$CUST" --arg m "$MATTER" --arg a "$amt" --arg r "$ref" --arg d "$desc" --arg t "$TODAY" '{clientId:$c,matterId:$m,amount:($a|tonumber),reference:$r,description:$d,transactionDate:$t}')
    r=$(post /api/trust-accounts/$TA/transactions/$kind "$ALICE" "$body")
    id=$(echo "$r" | sed '$d' | jq -r '.id // empty' 2>/dev/null)
    echo "$kind $amt -> http=$(echo "$r" | tail -1) id=${id:-none} type=$(echo "$r" | sed '$d' | jq -r '.transactionType // empty' 2>/dev/null) status=$(echo "$r" | sed '$d' | jq -r '.status // empty' 2>/dev/null) $( [ -z "$id" ] && echo "$r" | sed '$d' | cut -c1-300)"
    if [ -n "$id" ] && [ "$(echo "$r" | sed '$d' | jq -r .status)" = "AWAITING_APPROVAL" ]; then
      r=$(post /api/trust-transactions/$id/approve "$BOB" '{}')
      echo "  approve (Bob) -> http=$(echo "$r" | tail -1) status=$(echo "$r" | sed '$d' | jq -r '.status // empty' 2>/dev/null) $( [ "$(echo "$r" | tail -1)" != 200 ] && echo "$r" | sed '$d' | cut -c1-300)"
    fi
  done
  echo "-- DB (read-only) trust_transactions on this matter:"
  psql_e2e "select transaction_type||'|'||amount||'|'||status||'|'||reference from $T.trust_transactions where project_id='$MATTER' order by created_at"
  sleep 3
  local SB; SB=$(jq -nc --arg f "$FROM" --arg t "$TODAY" '{periodStart:$f,periodEnd:$t}')
  r=$(post /api/matters/$MATTER/statements "$ALICE" "$SB")
  echo "POST /api/matters/$MATTER/statements $SB -> http=$(echo "$r" | tail -1) id=$(echo "$r" | sed '$d' | jq -r '.id // empty' 2>/dev/null) containsTrustData=$(echo "$r" | sed '$d' | jq -r '.containsTrustData' 2>/dev/null)"
  echo "$r" | sed '$d' | jq -r '.htmlPreview // empty' > $W/trust-$L-soa.html
  echo "-- htmlPreview Payments table rows (tags stripped):"
  python3 -I -c 'import re,sys,html; s=open(sys.argv[1]).read(); i=s.find("Payments"); t=s[i:]; e=t.find("</table>"); t=t[:e]; rows=re.findall(r"<tr.*?</tr>", t, re.S); [print("    row: "+re.sub(r"\s+"," ",html.unescape(re.sub(r"<[^>]+>"," ",r))).strip()) for r in rows]' $W/trust-$L-soa.html
  echo "-- htmlPreview Deposits table rows:"
  python3 -I -c 'import re,sys,html; s=open(sys.argv[1]).read(); i=s.find("Deposits"); t=s[i:]; e=t.find("</table>"); t=t[:e]; rows=re.findall(r"<tr.*?</tr>", t, re.S); [print("    row: "+re.sub(r"\s+"," ",html.unescape(re.sub(r"<[^>]+>"," ",r))).strip()) for r in rows]' $W/trust-$L-soa.html
  echo "-- raw-enum hits in htmlPreview text: $(python3 -I -c 'import re,sys,html; s=open(sys.argv[1]).read(); print(re.sub(r"<[^>]+>"," ",s))' $W/trust-$L-soa.html | grep -owE 'PAYMENT|REFUND|DEPOSIT|FEE_TRANSFER|TRANSFER_OUT|DISBURSEMENT_PAYMENT|INTEREST_LPFF|REVERSAL' | sort | uniq -c | tr '\n' ' ')"
  echo "-- trust-activity emails (Mailpit subject 'Trust account activity'):"
  local js; js=$(curl -s "http://localhost:8026/api/v1/search?query=subject:%22Trust%20account%20activity%22&limit=20")
  echo "count=$(echo "$js" | jq -r .messages_count)"
  for mid in $(echo "$js" | jq -r '.messages[].ID'); do
    local m; m=$(curl -s "http://localhost:8026/api/v1/message/$mid")
    echo "$m" | jq -r .HTML > $W/trust-$L-mail-$mid.html
    echo "  $mid to=$(echo "$m" | jq -r '[.To[].Address]|join(",")') type-cell=$(python3 -I -c 'import re,sys,html; s=open(sys.argv[1]).read(); m=re.search(r"font-weight: 500;\">([^<]*)</span>", s); print(html.unescape(m.group(1)) if m else "?")' $W/trust-$L-mail-$mid.html) amount-line=$(python3 -I -c 'import re,sys,html; s=html.unescape(re.sub(r"<[^>]+>"," ",open(sys.argv[1]).read())); m=re.search(r"R\s?[0-9][0-9  ]*,[0-9]{2}", s); print(m.group(0) if m else "?")' $W/trust-$L-mail-$mid.html)"
  done
  } >> $F 2>&1
  mkdir -p $RAW/trust-$L; cp $W/trust-$L-* $RAW/trust-$L/ 2>/dev/null
}
