#!/usr/bin/env bash
# PR #1757 observation probes (sourced after lib.sh). Firm API via mock-IDP tokens (Alice owner),
# portal API via a magic-link JWT for the client contact; the DB is only read (SELECT).
#  flyway_probe <label>  — (1) global + tenant Flyway history
#  f28_probe <label>     — (2) MATTER_REQUIRED / MATTER_NOT_LINKED_TO_CLIENT refusals,
#                          (3) a matter deposit reaches the portal; portal balance == firm ledger,
#                          (4) a 150-char reference deposit lands in portal.portal_trust_transaction in full
flyway_probe(){ # label
  local L=$1 T; T=$(tenant)
  {
  echo "===== Flyway probe $L $(ts) tenant=$T"
  echo "-- global (public.flyway_schema_history), success rows:"
  psql_e2e "select installed_rank||'|'||version||'|'||description||'|'||success from public.flyway_schema_history order by installed_rank"
  echo "-- tenant ($T.flyway_schema_history), success rows:"
  psql_e2e "select installed_rank||'|'||version||'|'||description||'|'||success from $T.flyway_schema_history order by installed_rank"
  echo "-- portal.portal_trust_transaction.reference type:"
  psql_e2e "select column_name||' '||data_type||'('||character_maximum_length||')' from information_schema.columns where table_schema='portal' and table_name='portal_trust_transaction' and column_name in ('reference','description','transaction_type') order by column_name"
  } > $RAW/obs-flyway-$L.txt 2>&1
}

f28_probe(){ # label
  local L=$1 F=$RAW/obs-f28-$1.txt B=http://localhost:8081 T; T=$(tenant)
  local TODAY; TODAY=$(TZ=Africa/Johannesburg date +%F)
  mkdir -p $RAW/f28-$L
  {
  echo "===== F28/F36 probe $L $(ts) today(SA)=$TODAY tenant=$T"
  local ALICE
  ALICE=$(curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' -d '{"userId":"user_e2e_alice","orgId":"e2e-test-org","orgSlug":"e2e-test-org","orgRole":"owner"}' | jq -r .access_token)
  echo "token alice=$([ ${#ALICE} -gt 20 ] && echo ok || echo MISSING)"
  local TA CUST MATTER OTHER
  curl -s $B/api/trust-accounts -H "Authorization: Bearer $ALICE" > $RAW/f28-$L/trust-accounts.json
  TA=$(jq -r '[.[] | select(.accountName=="Mathebula & Partners Trust Account") | .id][0] // .[0].id' $RAW/f28-$L/trust-accounts.json)
  MATTER=$(psql_e2e "select id from $T.projects where name='Website Redesign' limit 1")
  CUST=$(psql_e2e "select customer_id from $T.projects where id='$MATTER'")
  OTHER=$(psql_e2e "select p.id from $T.projects p where p.customer_id is distinct from '$CUST' and not exists (select 1 from $T.customer_projects cp where cp.customer_id='$CUST' and cp.project_id=p.id) order by p.name limit 1")
  echo "trust accounts=$(jq -r '[.[] | .accountName+"="+.id] | join(", ")' $RAW/f28-$L/trust-accounts.json)"
  echo "using TA=$TA"
  echo "client=$CUST ($(psql_e2e "select name from $T.customers where id='$CUST'"))"
  echo "client matter=$MATTER (Website Redesign; projects.customer_id=$(psql_e2e "select customer_id from $T.projects where id='$MATTER'"))"
  echo "unlinked matter=$OTHER ($(psql_e2e "select name||' / client '||coalesce((select name from $T.customers c where c.id=p.customer_id),'none') from $T.projects p where id='$OTHER'")); customer_projects link to client=$(psql_e2e "select count(*) from $T.customer_projects where customer_id='$CUST' and project_id='$OTHER'")"
  post(){ curl -s -w '\n%{http_code}' -X POST "$B$1" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' -d "$3"; }
  local cnt0 cnt1 r body
  cnt0=$(psql_e2e "select count(*) from $T.trust_transactions")
  # (2a) no matter
  body=$(jq -nc --arg c "$CUST" --arg t "$TODAY" '{clientId:$c,amount:500.00,reference:"GATE/NOMATTER/1757",description:"Gate deposit without a matter",transactionDate:$t}')
  r=$(post /api/trust-accounts/$TA/transactions/deposit "$ALICE" "$body")
  echo "(2a) deposit, no matterId -> http=$(echo "$r" | tail -1)"; echo "$r" | sed '$d' | tee $RAW/f28-$L/2a-body.json | jq -c '{title,status,detail,code,reason}'
  # (2b) matter of another client
  body=$(jq -nc --arg c "$CUST" --arg m "$OTHER" --arg t "$TODAY" '{clientId:$c,matterId:$m,amount:500.00,reference:"GATE/UNLINKED/1757",description:"Gate deposit on an unlinked matter",transactionDate:$t}')
  r=$(post /api/trust-accounts/$TA/transactions/deposit "$ALICE" "$body")
  echo "(2b) deposit, unlinked matterId -> http=$(echo "$r" | tail -1)"; echo "$r" | sed '$d' | tee $RAW/f28-$L/2b-body.json | jq -c '{title,status,detail,code,matterId}'
  cnt1=$(psql_e2e "select count(*) from $T.trust_transactions")
  echo "trust_transactions count before=$cnt0 after-refusals=$cnt1; GATE/NOMATTER|GATE/UNLINKED rows=$(psql_e2e "select count(*) from $T.trust_transactions where reference like 'GATE/%MATTER/1757' or reference like 'GATE/UNLINKED/1757'")"
  # (3) happy path on the client's matter
  body=$(jq -nc --arg c "$CUST" --arg m "$MATTER" --arg t "$TODAY" '{clientId:$c,matterId:$m,amount:1757.00,reference:"GATE/DEP/1757",description:"Gate deposit on the client matter",transactionDate:$t}')
  r=$(post /api/trust-accounts/$TA/transactions/deposit "$ALICE" "$body")
  local ID3; ID3=$(echo "$r" | sed '$d' | jq -r '.id // empty')
  echo "(3) deposit, client matter -> http=$(echo "$r" | tail -1) id=$ID3"; echo "$r" | sed '$d' | tee $RAW/f28-$L/3-body.json | jq -c '{id,transactionType,status,amount,projectId,matterId,customerId,clientId,reference}'
  # (4) 150-character reference
  local REF150; REF150="GATE/F36/1757/$(printf 'R%.0s' $(seq 1 136))"
  echo "REF150 length=${#REF150}"
  body=$(jq -nc --arg c "$CUST" --arg m "$MATTER" --arg r "$REF150" --arg t "$TODAY" '{clientId:$c,matterId:$m,amount:150.00,reference:$r,description:"Gate deposit with a 150-character reference",transactionDate:$t}')
  r=$(post /api/trust-accounts/$TA/transactions/deposit "$ALICE" "$body")
  local ID4; ID4=$(echo "$r" | sed '$d' | jq -r '.id // empty')
  echo "(4) deposit, 150-char reference -> http=$(echo "$r" | tail -1) id=$ID4"; echo "$r" | sed '$d' | tee $RAW/f28-$L/4-body.json | jq -c '{id,transactionType,status,amount,refLen:(.reference|length)}'
  sleep 5
  echo "-- DB (read-only) firm trust_transactions for these ids:"
  psql_e2e "select id||'|'||transaction_type||'|'||amount||'|'||status||'|'||coalesce(project_id::text,'NULL')||'|reflen='||length(reference) from $T.trust_transactions where id in ('$ID3','$ID4')"
  echo "-- DB (read-only) portal.portal_trust_transaction for these ids:"
  psql_e2e "select id||'|'||transaction_type||'|'||amount||'|'||running_balance||'|matter='||matter_id||'|reflen='||length(reference)||'|full-match='||(reference='$REF150')::text from portal.portal_trust_transaction where id in ('$ID3','$ID4')"
  echo "-- DB (read-only) portal.portal_trust_balance for the client:"
  psql_e2e "select matter_id||'|'||current_balance||'|'||last_synced_at from portal.portal_trust_balance where customer_id='$CUST' order by matter_id"
  echo "portal balance sum=$(psql_e2e "select coalesce(sum(current_balance),0) from portal.portal_trust_balance where customer_id='$CUST'")"
  echo "-- firm client ledger (API) per trust account:"
  local FIRMSUM=0 b
  for acc in $(jq -r '.[].id' $RAW/f28-$L/trust-accounts.json); do
    r=$(curl -s -w '\n%{http_code}' $B/api/trust-accounts/$acc/client-ledgers/$CUST -H "Authorization: Bearer $ALICE")
    b=$(echo "$r" | sed '$d' | jq -r '.balance // empty' 2>/dev/null)
    echo "  account $acc -> http=$(echo "$r" | tail -1) balance=${b:-none}"
    echo "$r" | sed '$d' > $RAW/f28-$L/firm-ledger-$acc.json
    [ -n "$b" ] && FIRMSUM=$(python3 -I -c "import sys; from decimal import Decimal as D; print(D(sys.argv[1])+D(sys.argv[2]))" $FIRMSUM $b)
  done
  echo "firm client ledger sum=$FIRMSUM"
  echo "-- DB (read-only) firm unmattered rows for the client: $(psql_e2e "select count(*) from $T.trust_transactions where customer_id='$CUST' and project_id is null")"
  echo "-- portal API (magic-link JWT for the client contact):"
  local EMAIL; EMAIL=$(psql_e2e "select email from $T.portal_contacts where customer_id='$CUST' order by (email='contact@acme.example.com') desc, created_at limit 1")
  echo "contact=$EMAIL"
  local ML TOK PJ
  ML=$(curl -s -X POST $B/portal/auth/request-link -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$EMAIL" '{email:$e,orgId:"e2e-test-org"}')" | jq -r '.magicLink // empty')
  TOK=$(echo "$ML" | sed -E 's/.*token=([^&]+).*/\1/')
  PJ=$(curl -s -X POST $B/portal/auth/exchange -H 'Content-Type: application/json' -d "$(jq -nc --arg t "$TOK" '{token:$t,orgId:"e2e-test-org"}')" | jq -r '.token // empty')
  echo "portal jwt=$([ ${#PJ} -gt 20 ] && echo ok || echo MISSING)"
  curl -s $B/portal/trust/summary -H "Authorization: Bearer $PJ" > $RAW/f28-$L/portal-summary.json
  jq -c '.matters[] | {matterName,matterId,currentBalance}' $RAW/f28-$L/portal-summary.json
  echo "portal API summary sum=$(jq -r '[.matters[].currentBalance] | add' $RAW/f28-$L/portal-summary.json)"
  curl -s "$B/portal/trust/matters/$MATTER/transactions?size=50" -H "Authorization: Bearer $PJ" > $RAW/f28-$L/portal-matter-txns.json
  echo "portal API matter txns containing GATE/: $(jq -c '[.content[] | select((.reference//"")|startswith("GATE/")) | {transactionType,amount,runningBalance,refLen:(.reference|length)}]' $RAW/f28-$L/portal-matter-txns.json)"
  echo "-- backend log scan:"
  docker logs e2e-backend > $W/f28-$L-backend.log 2>&1
  echo "  'value too long'=$(grep -ci 'value too long' $W/f28-$L-backend.log) 'Portal trust sync failed'=$(grep -c 'Portal trust sync failed' $W/f28-$L-backend.log) MATTER_REQUIRED/NOT_LINKED log lines=$(grep -cE 'MATTER_REQUIRED|MATTER_NOT_LINKED|Matter required|Matter not linked' $W/f28-$L-backend.log)"
  grep -E 'Matter required|Matter not linked|MATTER_' $W/f28-$L-backend.log | cut -c1-400 | head -5
  } >> $F 2>&1
}
