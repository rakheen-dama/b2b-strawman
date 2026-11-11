#!/usr/bin/env bash
# PR #1755 gate observation probes (sourced after lib.sh). Read-only SQL; the digest is fired through
# the internal API (no SQL writes). Output -> $RAW/obs-<label>.txt
OBS(){ echo $RAW/obs-$1.txt; }

# (1) legal-za pack version + the SoA template row the tenant actually holds
pack_probe(){ # label
  local L=$1 T; T=$(tenant); local F; F=$(OBS pack-$L)
  {
  echo "===== pack probe $L $(ts) tenant=$T"
  echo "-- pack_install (all rows)"
  psql_e2e "select pack_id||'|'||pack_type||'|'||pack_version||'|'||installed_at from $T.pack_install order by pack_id"
  echo "-- pack_install legal-za document template pack_version: $(psql_e2e "select pack_version from $T.pack_install where pack_id='legal-za'")"
  echo "-- org_settings template pack status (legal-za):"
  psql_e2e "select template_pack_status::text from $T.org_settings" 2>&1 | grep -o '{[^{}]*legal-za[^{}]*}'
  echo "-- document_templates statement-of-account"
  psql_e2e "select pack_id||'|'||pack_template_key||'|content_hash='||coalesce(content_hash,'null')||'|updated='||updated_at||'|hasTypeLabelKey='||(content::text like '%\"transactionTypeLabel\"%')||'|hasRawTypeKey='||(content::text like '%\"key\": \"transactionType\"%') from $T.document_templates where pack_template_key='statement-of-account'"
  echo "-- shipped v15 'to' hash: sha256:2d513fa18c853ba7a7c93a9786610f79e9dbf1b999bd6c652ead8e88fc67f384 ; v14 'to' hash: sha256:b49ceb09dd24aad3137163e648fcedd4c04495674d7ca57de00088ce784a3795"
  echo "-- backend log: legal-za / reconcile / pack lines"
  docker logs e2e-backend 2>&1 | grep -iE 'legal-za|reconcil' | grep -iE 'template|reconcil' | sed -E 's/.*"message":"([^"]*)".*"level":"([A-Z]+)".*/\2 \1/; s/.*"level":"([A-Z]+)".*"message":"([^"]*)".*/\1 \2/' | cut -c1-260 | sort | uniq -c
  echo "-- backend log WARN/ERROR mentioning reconcile/pack: $(docker logs e2e-backend 2>&1 | grep -E '"level":"(WARN|ERROR)"' | grep -ciE 'reconcil|pack')"
  } >> $F 2>&1
}

# (2) every saved Statement of Account: context_snapshot trust payment labels + the PDF text
soa_probe(){ # label
  local L=$1 T; T=$(tenant); local F; F=$(OBS soa-$L)
  mkdir -p $W/soa-$L
  {
  echo "===== SoA probe $L $(ts) tenant=$T"
  psql_e2e "select gd.id||'|'||gd.file_name||'|'||gd.generated_at||'|'||gd.s3_key from $T.generated_documents gd join $T.document_templates dt on dt.id=gd.template_id where dt.pack_template_key='statement-of-account' order by gd.generated_at" > $W/soa-$L/list.txt
  echo "saved SoA rows=$(grep -c . $W/soa-$L/list.txt)"; cat $W/soa-$L/list.txt
  while IFS='|' read -r id fn ga key; do
    [ -z "$id" ] && continue
    echo "--- $id $fn ($ga)"
    echo "context_snapshot transactionType values:      $(psql_e2e "select jsonb_path_query_array(context_snapshot, 'lax \$.**.transactionType')::text from $T.generated_documents where id='$id'")"
    echo "context_snapshot transactionTypeLabel values: $(psql_e2e "select jsonb_path_query_array(context_snapshot, 'lax \$.**.transactionTypeLabel')::text from $T.generated_documents where id='$id'")"
    docker exec e2e-localstack awslocal s3 cp "s3://docteams-dev/$key" - > $W/soa-$L/$id.pdf 2>/dev/null
    echo "pdf bytes=$(wc -c < $W/soa-$L/$id.pdf | tr -d ' ')"
    docker run --rm -v $W/soa-$L:/d alpine:3 sh -c "apk add -q --no-cache poppler-utils >/dev/null 2>&1 && pdftotext -layout /d/$id.pdf /d/$id.txt"
    echo "PDF text, Trust Activity section:"
    awk '/Trust Activity/{p=1} /Summary/{if(p){print; exit}} p' $W/soa-$L/$id.txt | sed 's/^/    | /'
    echo "raw-enum hits in PDF text (whole words PAYMENT|REFUND|DEPOSIT|FEE_TRANSFER|TRANSFER_OUT|DISBURSEMENT_PAYMENT|INTEREST_LPFF|REVERSAL): $(grep -cwE 'PAYMENT|REFUND|DEPOSIT|FEE_TRANSFER|TRANSFER_OUT|DISBURSEMENT_PAYMENT|INTEREST_LPFF|REVERSAL' $W/soa-$L/$id.txt)"
    grep -nwE 'PAYMENT|REFUND|DEPOSIT|FEE_TRANSFER|TRANSFER_OUT|DISBURSEMENT_PAYMENT|INTEREST_LPFF|REVERSAL' $W/soa-$L/$id.txt | sed 's/^/    raw! /'
  done < $W/soa-$L/list.txt
  } >> $F 2>&1
  mkdir -p $RAW/soa-$L; cp $W/soa-$L/*.txt $RAW/soa-$L/ 2>/dev/null; cp $W/soa-$L/*.pdf $RAW/soa-$L/ 2>/dev/null
}

# (3) weekly digest: fire once via the internal endpoint, then read the Mailpit bodies
digest_probe(){ # label
  local L=$1 T; T=$(tenant); local F; F=$(OBS digest-$L)
  local ORG; ORG=$(psql_e2e "select external_org_id from public.org_schema_mapping where schema_name='$T'")
  {
  echo "===== digest probe $L $(ts) tenant=$T orgId=$ORG"
  echo "-- portal contacts (status|email):"; psql_e2e "select status||'|'||email from $T.portal_contacts order by created_at"
  echo "-- portal.portal_invoices issued in the 7-day lookback (number|status|total|currency|issue_date):"
  psql_e2e "select invoice_number||'|'||status||'|'||total||'|'||currency||'|'||issue_date from portal.portal_invoices where org_id='$ORG' and issue_date >= current_date - 7 order by invoice_number" 2>&1
  local before; before=$(curl -s "http://localhost:8026/api/v1/search?query=subject:%22Your%20weekly%20update%22" | jq -r '.messages_count')
  echo "-- mailpit 'Your weekly update' messages before: $before"
  local CALL="curl -s -X POST -H 'X-API-KEY: e2e-test-api-key' 'http://localhost:8081/internal/portal/digest/run-weekly?orgId=$ORG'"
  echo "-- call: $CALL"
  echo "-- response: $(eval $CALL) (at $(ts))"
  sleep 5
  local js; js=$(curl -s "http://localhost:8026/api/v1/search?query=subject:%22Your%20weekly%20update%22&limit=50")
  echo "-- mailpit 'Your weekly update' messages after: $(echo "$js" | jq -r '.messages_count')"
  for id in $(echo "$js" | jq -r '.messages[].ID'); do
    local m; m=$(curl -s "http://localhost:8026/api/v1/message/$id")
    echo "--- message $id to=$(echo "$m" | jq -r '[.To[].Address]|join(",")') subject=$(echo "$m" | jq -r .Subject) date=$(echo "$m" | jq -r .Date)"
    echo "$m" | jq -r .HTML > $W/digest-$L-$id.html
    echo "$m" | jq -r .Text > $W/digest-$L-$id.txt
    echo "  Text body, fee-note section:"
    awk '/Recent fee notes/{p=1} /Pending acceptances|Information requests|Trust|Upcoming|View/{if(p&&!/Recent/){exit}} p' $W/digest-$L-$id.txt | sed 's/^/    | /'
    echo "  HTML fee-note rows (tags stripped):"
    python3 -I -c 'import re,sys,html; s=open(sys.argv[1]).read(); m=re.search(r"Recent fee notes.*?</table>", s, re.S); t=m.group(0) if m else ""; rows=re.findall(r"<tr.*?</tr>", t, re.S); [print("    row: "+re.sub(r"\s+"," ",html.unescape(re.sub(r"<[^>]+>"," ",r))).strip()) for r in rows]; print("    rows=%d"%len(rows))' $W/digest-$L-$id.html
    echo "  raw-status/ZAR hits in HTML (PAID|SENT|PARTIALLY_PAID|APPROVED|DRAFT|VOID|ZAR): $(grep -oE '\b(PAID|SENT|PARTIALLY_PAID|APPROVED|DRAFT|VOID|ZAR)\b' $W/digest-$L-$id.html | sort | uniq -c | tr '\n' ' ')"
  done
  } >> $F 2>&1
  mkdir -p $RAW/digest-$L; cp $W/digest-$L-* $RAW/digest-$L/ 2>/dev/null
}
