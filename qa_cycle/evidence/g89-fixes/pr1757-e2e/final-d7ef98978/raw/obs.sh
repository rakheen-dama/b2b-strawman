#!/usr/bin/env bash
# PR #1757 FINAL-gate observation probes (sourced after lib.sh) — early-read obs.sh + chk_trust_invest_status in flyway_probe, d39_probe, inv_probe. Firm API via mock-IDP tokens (Alice owner),
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
  echo "-- tenant chk_trust_invest_status:"
  psql_e2e "select c.conname||' '||pg_get_constraintdef(c.oid) from pg_constraint c join pg_namespace n on n.oid=c.connamespace where c.conname='chk_trust_invest_status' and n.nspname='$T'"
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

# ---- final-gate additions (d7ef98978) --------------------------------------------------------
mocktok(){ curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' -d "{\"userId\":\"$1\",\"orgId\":\"e2e-test-org\",\"orgSlug\":\"e2e-test-org\",\"orgRole\":\"$2\"}" | jq -r .access_token; }
hpost(){ curl -s -w '\n%{http_code}' -X POST "http://localhost:8081$1" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' -d "$3"; }
hget(){ curl -s -w '\n%{http_code}' "http://localhost:8081$1" -H "Authorization: Bearer $2"; }
portaljwt(){ # email
  local ML TOK
  ML=$(curl -s -X POST http://localhost:8081/portal/auth/request-link -H 'Content-Type: application/json' -d "$(jq -nc --arg e "$1" '{email:$e,orgId:"e2e-test-org"}')" | jq -r '.magicLink // empty')
  TOK=$(echo "$ML" | sed -E 's/.*token=([^&]+).*/\1/')
  curl -s -X POST http://localhost:8081/portal/auth/exchange -H 'Content-Type: application/json' -d "$(jq -nc --arg t "$TOK" '{token:$t,orgId:"e2e-test-org"}')" | jq -r '.token // empty'
}
probe_ctx(){ # sets T TA MATTER CUST TODAY ALICE BOB
  T=$(tenant); TODAY=$(TZ=Africa/Johannesburg date +%F)
  ALICE=$(mocktok user_e2e_alice owner); BOB=$(mocktok user_e2e_bob admin)
  TA=$(curl -s http://localhost:8081/api/trust-accounts -H "Authorization: Bearer $ALICE" | jq -r '[.[] | select(.accountName=="Mathebula & Partners Trust Account") | .id][0] // .[0].id')
  MATTER=$(psql_e2e "select id from $T.projects where name='Website Redesign' limit 1")
  CUST=$(psql_e2e "select customer_id from $T.projects where id='$MATTER'")
}
ledger_bal(){ hget /api/trust-accounts/$TA/client-ledgers/$1 "$ALICE" | sed '$d' | jq -r '.balance'; }

# (4) D39 — a transfer posts each leg on its own client's matter; B's portal reflects it; an unlinked target matter is refused
d39_probe(){ # label
  local L=$1 F=$RAW/obs-d39-$1.txt; local D=$RAW/d39-$1; mkdir -p $D
  probe_ctx
  {
  echo "===== D39 transfer probe $L $(ts) today(SA)=$TODAY tenant=$T TA=$TA"
  echo "tokens alice=$([ ${#ALICE} -gt 20 ] && echo ok || echo MISSING) bob=$([ ${#BOB} -gt 20 ] && echo ok || echo MISSING)"
  local BC BM BEMAIL BAD
  BC=$(psql_e2e "select c.id from $T.customers c where c.id<>'$CUST' and exists(select 1 from $T.portal_contacts pc where pc.customer_id=c.id) and exists(select 1 from $T.projects p where p.customer_id=c.id) order by c.name limit 1")
  BM=$(psql_e2e "select id from $T.projects where customer_id='$BC' order by created_at limit 1")
  BEMAIL=$(psql_e2e "select email from $T.portal_contacts where customer_id='$BC' order by created_at limit 1")
  BAD=$(psql_e2e "select p.id from $T.projects p where p.customer_id is distinct from '$BC' and not exists (select 1 from $T.customer_projects cp where cp.customer_id='$BC' and cp.project_id=p.id) order by (p.id='$MATTER') desc, p.name limit 1")
  echo "client A=$CUST ($(psql_e2e "select name from $T.customers where id='$CUST'")) matter A=$MATTER (Website Redesign)"
  echo "client B=$BC ($(psql_e2e "select name from $T.customers where id='$BC'")) matter B=$BM ($(psql_e2e "select name||' / projects.customer_id='||customer_id from $T.projects where id='$BM'")) portal contact=$BEMAIL"
  echo "unlinked target matter for B=$BAD ($(psql_e2e "select name||' / client '||coalesce((select name from $T.customers c where c.id=p.customer_id),'none') from $T.projects p where id='$BAD'")); customer_projects links to B=$(psql_e2e "select count(*) from $T.customer_projects where customer_id='$BC' and project_id='$BAD'")"
  local AB0 BB0 PJ BRM0 BAPI0
  AB0=$(ledger_bal $CUST); BB0=$(ledger_bal $BC)
  PJ=$(portaljwt "$BEMAIL"); echo "portal jwt B=$([ ${#PJ} -gt 20 ] && echo ok || echo MISSING)"
  BRM0=$(psql_e2e "select coalesce(sum(current_balance),0) from portal.portal_trust_balance where customer_id='$BC'")
  curl -s http://localhost:8081/portal/trust/summary -H "Authorization: Bearer $PJ" > $D/portal-summary-B-before.json
  BAPI0=$(jq -r '[.matters[]?.currentBalance] | add // 0' $D/portal-summary-B-before.json)
  echo "BEFORE: firm ledger A=$AB0 B=$BB0 | portal read model B=$BRM0 | portal API /portal/trust/summary B=$BAPI0"
  local c0 c1 r body
  c0=$(psql_e2e "select count(*) from $T.trust_transactions")
  # (4a) target matter not linked to B
  body=$(jq -nc --arg a "$CUST" --arg am "$MATTER" --arg b "$BC" --arg bm "$BAD" --arg t "$TODAY" '{sourceClientId:$a,sourceMatterId:$am,targetClientId:$b,targetMatterId:$bm,amount:250.00,reference:"GATE/D39/UNLINKED",description:"Gate transfer to an unlinked target matter",transactionDate:$t}')
  r=$(hpost /api/trust-accounts/$TA/transactions/transfer "$ALICE" "$body")
  echo "(4a) transfer A->B, target matter not linked to B -> http=$(echo "$r" | tail -1)"; echo "$r" | sed '$d' | tee $D/4a-body.json | jq -c '{title,status,detail,code,matterId}'
  c1=$(psql_e2e "select count(*) from $T.trust_transactions")
  echo "trust_transactions count before=$c0 after-refusal=$c1; GATE/D39/UNLINKED rows=$(psql_e2e "select count(*) from $T.trust_transactions where reference='GATE/D39/UNLINKED'")"
  # (4b) happy path
  body=$(jq -nc --arg a "$CUST" --arg am "$MATTER" --arg b "$BC" --arg bm "$BM" --arg t "$TODAY" '{sourceClientId:$a,sourceMatterId:$am,targetClientId:$b,targetMatterId:$bm,amount:250.00,reference:"GATE/D39/1757",description:"Gate transfer A matter to B matter",transactionDate:$t}')
  r=$(hpost /api/trust-accounts/$TA/transactions/transfer "$ALICE" "$body")
  echo "(4b) transfer A/matterA -> B/matterB R250 -> http=$(echo "$r" | tail -1)"; echo "$r" | sed '$d' | tee $D/4b-body.json | jq -c '.[]? | {id,transactionType,status,amount,customerId,matterId,projectId}'
  sleep 5
  echo "-- DB (read-only) firm rows for the transfer (type|amount|status|customer|project):"
  psql_e2e "select t.transaction_type||'|'||t.amount||'|'||t.status||'|customer_id='||t.customer_id||' ('||c.name||')|project_id='||coalesce(t.project_id::text,'NULL')||' ('||coalesce(p.name,'none')||')|project.customer_id='||coalesce(p.customer_id::text,'NULL') from $T.trust_transactions t join $T.customers c on c.id=t.customer_id left join $T.projects p on p.id=t.project_id where t.reference='GATE/D39/1757' order by t.transaction_type desc"
  echo "-- legs on their own client's matter: $(psql_e2e "select count(*) from $T.trust_transactions t join $T.projects p on p.id=t.project_id where t.reference='GATE/D39/1757' and (p.customer_id=t.customer_id or exists(select 1 from $T.customer_projects cp where cp.customer_id=t.customer_id and cp.project_id=t.project_id))")/2"
  echo "-- DB (read-only) portal.portal_trust_transaction rows for the transfer:"
  psql_e2e "select transaction_type||'|'||amount||'|customer='||customer_id||'|matter='||matter_id||'|running='||running_balance from portal.portal_trust_transaction where reference='GATE/D39/1757' order by transaction_type"
  local AB1 BB1 BRM1 BAPI1
  AB1=$(ledger_bal $CUST); BB1=$(ledger_bal $BC)
  BRM1=$(psql_e2e "select coalesce(sum(current_balance),0) from portal.portal_trust_balance where customer_id='$BC'")
  curl -s http://localhost:8081/portal/trust/summary -H "Authorization: Bearer $PJ" > $D/portal-summary-B-after.json
  BAPI1=$(jq -r '[.matters[]?.currentBalance] | add // 0' $D/portal-summary-B-after.json)
  jq -c '.matters[]? | {matterName,matterId,currentBalance}' $D/portal-summary-B-after.json
  echo "AFTER: firm ledger A=$AB1 B=$BB1 | portal read model B=$BRM1 | portal API B=$BAPI1"
  echo "deltas: A=$(python3 -I -c "import sys; from decimal import Decimal as D; print(D(sys.argv[2])-D(sys.argv[1]))" $AB0 $AB1) B-firm=$(python3 -I -c "import sys; from decimal import Decimal as D; print(D(sys.argv[2])-D(sys.argv[1]))" $BB0 $BB1) B-portal-API=$(python3 -I -c "import sys; from decimal import Decimal as D; print(D(sys.argv[2])-D(sys.argv[1]))" $BAPI0 $BAPI1)"
  curl -s "http://localhost:8081/portal/trust/matters/$BM/transactions?size=50" -H "Authorization: Bearer $PJ" > $D/portal-B-matter-txns.json
  echo "portal API B matter txns GATE/D39: $(jq -c '[.content[]? | select((.reference//"")|startswith("GATE/D39")) | {transactionType,amount,runningBalance}]' $D/portal-B-matter-txns.json)"
  } >> $F 2>&1
}

# (5) investments — AWAITING placement refuses withdraw; rejecting it CANCELS the investment (V15) and leaves the
# trust card unchanged; an APPROVED placement refuses reversal (F86); withdraw credits the matter once
inv_probe(){ # label
  local L=$1 F=$RAW/obs-inv-$1.txt; local D=$RAW/inv-$1; mkdir -p $D
  probe_ctx
  local MAT90; MAT90=$(TZ=Africa/Johannesburg date -v+90d +%F)
  pos(){ hget "/api/dashboard/trust-position?accountId=$TA" "$ALICE" | sed '$d' | tee $D/position-$1.json | jq -c '{cashbookBalance,clientLedgerTotal,difference,pendingApprovalCount}'; }
  mbal(){ hget /api/matters/$MATTER/trust-balance "$ALICE" | sed '$d' | tee $D/matter-balance-$1.json | jq -c '.'; }
  reg(){ # tag acct [status]
    local p; if [ -n "$3" ]; then p=$(jq -nc --arg ta "$TA" --arg s "$3" '{parameters:{trust_account_id:$ta,status:$s},page:0,size:500}'); else p=$(jq -nc --arg ta "$TA" '{parameters:{trust_account_id:$ta},page:0,size:500}'); fi
    local r; r=$(hpost /api/report-definitions/investment-register/execute "$ALICE" "$p")
    echo "$r" | sed '$d' > $D/register-$1.json
    echo "http=$(echo "$r" | tail -1) rows=$(jq '.rows|length' $D/register-$1.json) rows-with-accountNumber=$2: $(jq --arg a "$2" '[.rows[]|select(.accountNumber==$a)]|length' $D/register-$1.json) statuses=$(jq -c '[.rows[].status]|group_by(.)|map({(.[0]):length})|add' $D/register-$1.json) summary=$(jq -c '.summary' $D/register-$1.json)"
  }
  place(){ # acct principal
    local body; body=$(jq -nc --arg c "$CUST" --arg m "$MATTER" --arg a "$1" --argjson p "$2" --arg t "$TODAY" --arg mt "$MAT90" '{clientId:$c,matterId:$m,institution:"Gate Bank",accountNumber:$a,principal:$p,interestRate:0.0500,depositDate:$t,maturityDate:$mt,notes:"PR 1757 final gate",investmentBasis:"FIRM_DISCRETION"}')
    hpost /api/trust-accounts/$TA/investments "$ALICE" "$body"
  }
  inv_row(){ psql_e2e "select i.status||'|principal='||i.principal||'|placement='||i.deposit_transaction_id||' '||t.transaction_type||' '||t.status||' project='||coalesce(t.project_id::text,'NULL')||'|withdrawal_tx='||coalesce(i.withdrawal_transaction_id::text,'NULL') from $T.trust_investments i join $T.trust_transactions t on t.id=i.deposit_transaction_id where i.id='$1'"; }
  {
  echo "===== investment probe $L $(ts) today(SA)=$TODAY tenant=$T TA=$TA client=$CUST matter=$MATTER"
  echo "tokens alice=$([ ${#ALICE} -gt 20 ] && echo ok || echo MISSING) bob=$([ ${#BOB} -gt 20 ] && echo ok || echo MISSING)"
  echo "approval mode: $(psql_e2e "select 'require_dual_approval='||coalesce(require_dual_approval::text,'?')||' threshold='||coalesce(payment_approval_threshold::text,'NULL') from $T.trust_accounts where id='$TA'" 2>&1 | head -1)"
  local P0 L0 M0
  P0=$(pos before); L0=$(ledger_bal $CUST); M0=$(mbal before)
  echo "BEFORE: trust card=$P0 | firm ledger A=$L0 | matter trust-balance=$M0"
  # (5a) place INV1 -> placement AWAITING_APPROVAL
  local r I1 PID1
  r=$(place GATE-F87-REJ 100.00)
  I1=$(echo "$r" | sed '$d' | jq -r '.id // empty'); PID1=$(echo "$r" | sed '$d' | jq -r '.depositTransactionId // empty')
  echo "(5a) place INV1 (GATE-F87-REJ, R100) -> http=$(echo "$r" | tail -1) id=$I1 placement=$PID1"; echo "$r" | sed '$d' | tee $D/5a-place1.json | jq -c '{id,status,principal,depositTransactionId}'
  echo "   DB: $(inv_row $I1)"
  echo "   trust card after place: $(pos after-place1)"
  # (5b) withdraw while AWAITING -> 400
  r=$(hpost /api/trust-investments/$I1/withdraw "$ALICE" '{}')
  echo "(5b) withdraw INV1 while placement AWAITING_APPROVAL -> http=$(echo "$r" | tail -1)"; echo "$r" | sed '$d' | tee $D/5b-withdraw1.json | jq -c '{title,status,detail}'
  echo "   DB: $(inv_row $I1)"
  # (5c) reject the placement -> investment CANCELLED
  r=$(hpost /api/trust-transactions/$PID1/reject "$BOB" '{"reason":"PR 1757 final gate: reject the placement"}')
  echo "(5c) reject placement $PID1 (Bob) -> http=$(echo "$r" | tail -1)"; echo "$r" | sed '$d' | tee $D/5c-reject1.json | jq -c '{id,transactionType,status,rejectionReason}'
  echo "   DB: $(inv_row $I1)"
  echo "   register (default, no status filter): $(reg default GATE-F87-REJ)"
  echo "   register (status=CANCELLED):        $(reg cancelled GATE-F87-REJ CANCELLED)"
  local P1 L1 M1
  P1=$(pos after-reject); L1=$(ledger_bal $CUST); M1=$(mbal after-reject)
  echo "   AFTER REJECT: trust card=$P1 | firm ledger A=$L1 | matter trust-balance=$M1"
  echo "   trust card unchanged vs BEFORE: $([ "$P0" = "$P1" ] && echo YES || echo NO) | ledger unchanged: $([ "$L0" = "$L1" ] && echo YES || echo NO) | matter balance unchanged: $([ "$M0" = "$M1" ] && echo YES || echo NO)"
  r=$(hget /api/trust-accounts/$TA/investments "$ALICE"); echo "$r" | sed '$d' > $D/investments-list-after-reject.json
  echo "   GET investments list: http=$(echo "$r" | tail -1) INV1 status=$(jq -r --arg i "$I1" '(.content // .)[]? | select(.id==$i) | .status' $D/investments-list-after-reject.json)"
  # (5d) place INV2, approve, try to reverse the placement -> 400
  local I2 PID2
  r=$(place GATE-F87-APR 120.00)
  I2=$(echo "$r" | sed '$d' | jq -r '.id // empty'); PID2=$(echo "$r" | sed '$d' | jq -r '.depositTransactionId // empty')
  echo "(5d) place INV2 (GATE-F87-APR, R120) -> http=$(echo "$r" | tail -1) id=$I2 placement=$PID2"; echo "$r" | sed '$d' > $D/5d-place2.json
  r=$(hpost /api/trust-transactions/$PID2/approve "$BOB" '{}')
  echo "   approve placement (Bob) -> http=$(echo "$r" | tail -1) $(echo "$r" | sed '$d' | tee $D/5d-approve2.json | jq -c '{status,approvedBy,secondApprovedBy}')"
  echo "   DB: $(inv_row $I2)"
  local L2; L2=$(ledger_bal $CUST); echo "   firm ledger A after approval=$L2 (before=$L0)"
  r=$(hpost /api/trust-transactions/$PID2/reverse "$ALICE" '{"reason":"PR 1757 final gate: try to reverse the placement"}')
  echo "   reverse approved placement $PID2 -> http=$(echo "$r" | tail -1)"; echo "$r" | sed '$d' | tee $D/5d-reverse2.json | jq -c '{title,status,detail}'
  echo "   reversal rows for the placement=$(psql_e2e "select count(*) from $T.trust_transactions where reversal_of='$PID2'") placement status=$(psql_e2e "select status from $T.trust_transactions where id='$PID2'")"
  # (5e) withdraw INV2 -> 200, matter credited once
  r=$(hpost /api/trust-investments/$I2/withdraw "$ALICE" '{}')
  echo "(5e) withdraw INV2 -> http=$(echo "$r" | tail -1)"; echo "$r" | sed '$d' | tee $D/5e-withdraw2.json | jq -c '{id,status,withdrawalAmount,withdrawalDate,withdrawalTransactionId}'
  echo "   DB: $(inv_row $I2)"
  echo "   withdrawal DEPOSIT rows (reference INV-WD-Gate Bank-GATE-F87-APR): $(psql_e2e "select count(*)||' rows; '||string_agg(transaction_type||' '||amount||' '||status||' project='||coalesce(project_id::text,'NULL'),', ') from $T.trust_transactions where reference='INV-WD-Gate Bank-GATE-F87-APR'")"
  local P2 L3 M2
  P2=$(pos final); L3=$(ledger_bal $CUST); M2=$(mbal final)
  echo "   FINAL: trust card=$P2 | firm ledger A=$L3 (before=$L0, after approval=$L2) | matter trust-balance=$M2"
  echo "   matter credited once: ledger back to BEFORE=$([ "$L0" = "$L3" ] && echo YES || echo NO)"
  echo "   register (default) INV2: $(reg final GATE-F87-APR)"
  } >> $F 2>&1
}
