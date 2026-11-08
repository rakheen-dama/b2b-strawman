#!/usr/bin/env bash
# PR #1751 e2e gate driver library (CP1 recipe, from pr1749s/lib.sh)
export WT=/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4
export RAW=/Users/rakheendama/Projects/2026/b2b-strawman-g89-docs/qa_cycle/evidence/g89-fixes/pr1751-e2e/raw
export W=/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/final
# PATH left as the user shell (nvm node v20.11.1 first); pnpm invoked by absolute path
export NODE_OPTIONS=""
export PATH=/Users/rakheendama/.nvm/versions/node/v20.11.1/bin:$PATH
TL=$RAW/timeline.txt
ST=$RAW/stacks.txt
ts(){ date +%Y-%m-%dT%H:%M:%S%z; }
hms(){ date +%H:%M:%S; }
psql_e2e(){ docker exec e2e-postgres psql -U postgres -d app -tAc "$1" 2>&1; }

stack_up(){ # label
  local L=$1
  echo "stack-$L launch $(ts)" >> $TL
  ( cd $WT && bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh ) > $W/stack-$L.log 2>&1
  local rc=$?
  echo "stack-$L rc=$rc end $(ts)" >> $TL
  if [ $rc -ne 0 ]; then # G89-F46 Google-Fonts build flake: retry once and record
    cp $W/stack-$L.log $W/stack-$L-attempt1-FAILED.log
    echo "stack-$L attempt1 FAILED rc=$rc; font-lines=$(grep -ciE 'next/font|fonts.g(oogle|static)' $W/stack-$L.log); retrying once $(ts)" >> $TL
    ( cd $WT && bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh ) > $W/stack-$L.log 2>&1
    rc=$?
    echo "stack-$L attempt2 rc=$rc end $(ts)" >> $TL
  fi
  cp $W/stack-$L.log $RAW/stack-$L.log
  stack_checks $L $rc
  return $rc
}
stack_checks(){ # label rc
  local L=$1 rc=$2
  {
  echo "===== stack $L checks $(ts) ====="
  echo "build rc=$rc"
  echo "git HEAD $(cd $WT && git rev-parse HEAD)"
  echo "backend health: $(curl -s http://localhost:8081/actuator/health)"
  echo "frontend $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/)"
  echo "mock-idp $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/.well-known/jwks.json)"
  echo "mailpit $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8026/)"
  docker logs e2e-seed 2>&1 | grep -m1 "Boot-Seed Complete" | sed 's/^/e2e-seed  | /'
  docker ps --filter name=e2e- --format '{{.Names}} {{.Image}} {{.CreatedAt}}'
  for c in e2e-backend e2e-frontend e2e-postgres; do
    docker inspect -f '{{.Name}} image={{.Image}} created={{.Created}} wd={{index .Config.Labels "com.docker.compose.project.working_dir"}}' $c 2>&1
  done
  echo "cached-lines=$(grep -c CACHED $W/stack-$L.log)"
  grep -E "Volume .* Removed" $W/stack-$L.log
  T=$(tenant)
  echo "tenant=$T"
  echo "tenant flyway: $(psql_e2e "select version||'|'||description from $T.flyway_schema_history where success order by installed_rank desc limit 1")"
  echo "global flyway: $(psql_e2e "select version||'|'||description from public.flyway_schema_history where success order by installed_rank desc limit 1")"
  echo "projects:"; psql_e2e "select name||'|'||status from $T.projects order by created_at"
  echo "portal_deadline_view=$(psql_e2e "select count(*) from portal.portal_deadline_view")"
  } >> $ST 2>&1
}
tenant(){ psql_e2e "select nspname from pg_namespace where nspname like 'tenant_%' limit 1"; }

mktsv(){ # json tsv
  jq -r '
    def walk_specs: (.specs[]? ), (.suites[]? | walk_specs);
    .suites[] | walk_specs | . as $s | .tests[] |
    [$s.file, ($s.line|tostring), $s.title, .status, ((.results|last|.status) // "notrun")] | @tsv' "$1" > "$2"
}

run_pw(){ # label subdir mode(score|capture) cmd...
  local L=$1 D=$2 M=$3; shift 3
  echo "$L start $(ts) node=$(cd $WT/$D && /opt/homebrew/bin/pnpm exec node -v) envnode=$(env node -v)" >> $TL
  local J=$W/$L.json
  ( cd $WT/$D && env NODE_OPTIONS= PLAYWRIGHT_JSON_OUTPUT_FILE=$J "$@" ) > $W/$L.txt 2>&1
  local rc=$?
  echo "$L rc=$rc end $(ts)" >> $TL
  if [ "$M" = capture ]; then
    { jq -c .stats $J; echo "writing-actual lines: $(grep -c 'writing actual' $W/$L.txt)"; grep -E '^\s+[0-9]+ (passed|failed|skipped|flaky|did not run)' $W/$L.txt; } > $RAW/$L.summary.txt
  else
    cp $J $RAW/$L.json; cp $W/$L.txt $RAW/$L.txt; mktsv $J $RAW/$L.tsv
    echo "$L writing-actual=$(grep -c 'writing actual' $W/$L.txt)" >> $ST
  fi
  return $rc
}

FE_ENV=(E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026)
PO_ENV=(NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 PORTAL_ORG_SLUG=e2e-test-org)

spacing(){ # label
  local L=$1 T; T=$(tenant)
  local q="select count(*)||'|'||coalesce(extract(epoch from now()-max(created_at))::int,0) from $T.magic_link_tokens"
  echo "spacing for before-$L: tokens|age-at-start=$(psql_e2e "$q") $(hms)" >> $ST
  while :; do
    local age; age=$(psql_e2e "select coalesce(extract(epoch from now()-max(created_at))::int,9999) from $T.magic_link_tokens")
    [ "$age" -ge 330 ] && break
    sleep 5
  done
  echo "token age now=${age}s $(hms)" >> $ST
  docker restart e2e-backend >/dev/null
  until curl -sf http://localhost:8081/actuator/health >/dev/null; do sleep 3; done
  echo "$(curl -s http://localhost:8081/actuator/health) backend restarted+healthy $(hms)" >> $ST
  if lsof -iTCP:3002 -sTCP:LISTEN -n -P >/dev/null 2>&1; then echo ":3002 BUSY" >> $ST; else echo ":3002 free" >> $ST; fi
}
exch(){ echo "$1 Exchanged magic link=$(docker logs e2e-backend 2>&1 | grep -c 'Exchanged magic link')" >> $ST; }
