#!/usr/bin/env bash
# PR #1754 G89-F71 observation on the G2 stack, after the scored portal runs (sourced by run-g2.sh; lib.sh loaded).
# Firm-side rows are created through the firm API as Alice (no SQL writes); the DB is only read.
# For every portal_deadline_view row: stored status (DB) vs API status (/portal/deadlines as the Acme contact)
# vs the PortalDeadlineStatus rule recomputed here from due_date and the SA date. Then the rows are removed
# again through the API (cancel / interrupt → portal row deleted), leaving portal_deadline_view empty.
O=$RAW/deadline-probe.txt
B=http://localhost:8081
TODAY=$(TZ=Africa/Johannesburg date +%F)
d(){ date -j -v"$1"d -f %F "$TODAY" +%F; }
T=$(tenant)
{
echo "===== deadline probe $(ts)  today(SA)=$TODAY  utc-date=$(date -u +%F)  tenant=$T"
ALICE=$(curl -s -X POST http://localhost:8090/token -H 'Content-Type: application/json' -d '{"userId":"user_e2e_alice","orgSlug":"e2e-test-org"}' | jq -r .access_token)
echo "alice token: $([ -n "$ALICE" ] && [ "$ALICE" != null ] && echo ok || echo MISSING)"
MATTER=$(psql_e2e "select id from $T.projects where name='Website Redesign' limit 1")
echo "matter Website Redesign=$MATTER"
echo "portal_deadline_view rows before probe=$(psql_e2e 'select count(*) from portal.portal_deadline_view')"
: > $W/probe-courtdates.txt; : > $W/probe-trackers.txt
for off in -2 0 3 7 8 30; do
  due=$(d $( [ $off -ge 0 ] && echo "+$off" || echo "$off" ))
  body=$(jq -nc --arg m "$MATTER" --arg dt "$due" --arg o "$off" '{matterId:$m,dateType:"HEARING",scheduledDate:$dt,scheduledTime:"10:00:00",courtName:("F71 probe court d"+$o),courtReference:("F71-"+$o),reminderDays:7,judgeMagistrate:"probe judge",description:"probe notes"}')
  r=$(curl -s -w '\n%{http_code}' -X POST $B/api/court-dates -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' -d "$body")
  code=$(echo "$r" | tail -1); id=$(echo "$r" | sed '$d' | jq -r '.id // empty' 2>/dev/null)
  echo "court date offset=$off due=$due -> http=$code id=${id:-none} $( [ -z "$id" ] && echo "$r" | sed '$d' | cut -c1-200)"
  [ -n "$id" ] && echo "$id" >> $W/probe-courtdates.txt
done
for off in -3 5; do
  due=$(d $( [ $off -ge 0 ] && echo "+$off" || echo "$off" )); coa=$(date -j -v-3y -f %F "$due" +%F)
  body=$(jq -nc --arg m "$MATTER" --arg c "$coa" '{matterId:$m,causeOfActionDate:$c,prescriptionType:"GENERAL_3Y",notes:"F71 probe notes"}')
  r=$(curl -s -w '\n%{http_code}' -X POST $B/api/prescription-trackers -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' -d "$body")
  code=$(echo "$r" | tail -1); id=$(echo "$r" | sed '$d' | jq -r '.id // empty' 2>/dev/null)
  echo "prescription offset=$off due=$due (cause=$coa) -> http=$code id=${id:-none} status=$(echo "$r" | sed '$d' | jq -r '.status // empty' 2>/dev/null) $( [ -z "$id" ] && echo "$r" | sed '$d' | cut -c1-200)"
  [ -n "$id" ] && echo "$id" >> $W/probe-trackers.txt
done
sleep 5
echo "--- DB (read-only): select source_entity, id, due_date, status from portal.portal_deadline_view"
psql_e2e "select source_entity||'|'||id||'|'||due_date||'|'||status from portal.portal_deadline_view order by due_date" | tee $W/probe-db.txt
ML=$(curl -s -X POST $B/portal/auth/request-link -H 'Content-Type: application/json' -d '{"email":"contact@acme.example.com","orgId":"e2e-test-org"}' | jq -r .magicLink)
TOK=${ML##*token=}; TOK=${TOK%%&*}
PJ=$(curl -s -X POST $B/portal/auth/exchange -H 'Content-Type: application/json' -d "{\"token\":\"$TOK\",\"orgId\":\"e2e-test-org\"}" | jq -r .token)
echo "portal jwt: $([ -n "$PJ" ] && [ "$PJ" != null ] && echo ok || echo MISSING)"
FROM=$(d -30); TO=$(d +60)
api(){ curl -s -w '\n%{http_code}' "$B/portal/deadlines$1" -H "Authorization: Bearer $PJ"; }
for q in "" "?from=$FROM&to=$TO" "?from=$FROM&to=$TO&status=OVERDUE" "?from=$FROM&to=$TO&status=DUE_SOON" "?from=$FROM&to=$TO&status=UPCOMING" "?from=$FROM&to=$TO&status=COMPLETED"; do
  r=$(api "$q"); code=$(echo "$r" | tail -1)
  echo "--- GET /portal/deadlines${q:- (default window)} http=$code"
  echo "$r" | sed '$d' | jq -r '.[] | "\(.sourceEntity)|\(.id)|\(.dueDate)|\(.status)"' 2>/dev/null | sort -t'|' -k3
done
api "?from=$FROM&to=$TO" | sed '$d' > $W/probe-api.json
echo "--- per-row: stored(DB) vs API vs rule(due,today=$TODAY)"
printf '%-22s %-10s %-9s %-9s %-9s %s\n' source due stored api rule verdict
while IFS='|' read -r src id due st; do
  [ -z "$id" ] && continue
  apist=$(jq -r --arg id "$id" --arg s "$src" '.[] | select(.id==$id and .sourceEntity==$s) | .status' $W/probe-api.json)
  if [ "$st" = COMPLETED ] || [ "$st" = CANCELLED ]; then rule=$st
  elif [[ "$due" < "$TODAY" ]]; then rule=OVERDUE
  elif [[ ! "$due" > "$(d +7)" ]]; then rule=DUE_SOON
  else rule=UPCOMING; fi
  v=$( [ "$apist" = "$rule" ] && echo "api==rule" || echo "API!=RULE" ); [ "$apist" = "$st" ] && v="$v, api==stored" || v="$v, api!=stored"
  printf '%-22s %-10s %-9s %-9s %-9s %s\n' "$src" "$due" "$st" "${apist:-absent}" "$rule" "$v"
done < $W/probe-db.txt
echo "--- cleanup through the firm API"
for id in $(cat $W/probe-courtdates.txt); do echo "cancel court date $id -> $(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/court-dates/$id/cancel -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' -d '{"reason":"F71 probe cleanup"}')"; done
for id in $(cat $W/probe-trackers.txt); do echo "interrupt tracker $id -> $(curl -s -o /dev/null -w '%{http_code}' -X POST $B/api/prescription-trackers/$id/interrupt -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' -d "{\"interruptionDate\":\"$TODAY\",\"interruptionReason\":\"F71 probe cleanup\"}")"; done
sleep 3
echo "portal_deadline_view rows after cleanup=$(psql_e2e 'select count(*) from portal.portal_deadline_view')"
psql_e2e "select source_entity||'|'||due_date||'|'||status from portal.portal_deadline_view order by due_date"
echo "API after cleanup (wide window): $(api "?from=$FROM&to=$TO" | sed '$d' | jq -c '[.[] | {sourceEntity,dueDate,status}]')"
echo "===== deadline probe end $(ts)"
} >> $O 2>&1
