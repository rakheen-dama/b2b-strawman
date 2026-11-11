#!/usr/bin/env bash
# PR #1754 e2e gate driver library (PR #1753 lib.sh with WT/RAW/W changed; fontprobe dropped — no font change in this PR)
export WT=/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp7
export RAW=/Users/rakheendama/Projects/2026/b2b-strawman-g89-docs/qa_cycle/evidence/g89-fixes/pr1754-e2e/raw
export W=/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1754
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

# PR #1752 addition: never tear down another session's e2e stack. Poll every 9 min while a
# foreign e2e-* container (compose working_dir != this worktree) exists.
wait_no_foreign_stack(){ # label
  while :; do
    local foreign=""
    for c in $(docker ps -a --filter name=e2e- --format '{{.Names}}'); do
      local wd; wd=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' $c 2>/dev/null)
      [ "$wd" != "$WT/compose" ] && foreign="$foreign $c($wd)"
    done
    [ -z "$foreign" ] && break
    echo "before-$1 foreign e2e stack present:$foreign — waiting 540s $(ts)" >> $TL
    sleep 540
  done
  echo "before-$1 no foreign e2e stack $(ts)" >> $TL
}
_stack_up_orig="$(declare -f stack_up)"
eval "${_stack_up_orig/stack_up ()/stack_up_inner ()}"
stack_up(){ wait_no_foreign_stack $1; stack_up_inner "$@"; }

# ---- PR #1753 additions -------------------------------------------------------------------
# run_g: run_pw + per-run accounting of curated documentation screenshots, baseline writes and
# snapshot-compare output, then archive and restore every dirty path so each run starts clean.
run_g(){ # label subdir mode cmd...
  local L=$1 D=$2
  touch $W/$L.marker; sleep 1
  run_pw "$@"; local rc=$?
  rm -rf $W/$L-test-results; cp -R $WT/$D/test-results $W/$L-test-results 2>/dev/null
  ( cd $WT
    local docs base
    docs=$(find documentation/screenshots -type f -newer $W/$L.marker 2>/dev/null | wc -l | tr -d ' ')
    base=$(find frontend/e2e/screenshots portal/e2e/screenshots -type f -newer $W/$L.marker 2>/dev/null | wc -l | tr -d ' ')
    git status --porcelain --untracked-files=all > $RAW/$L.dirty.txt
    {
      echo "== $L accounting $(ts)"
      echo "$L curated-doc-screenshots-written=$docs"
      find documentation/screenshots -type f -newer $W/$L.marker 2>/dev/null | sort | sed 's/^/    /'
      echo "$L baseline-files-written(frontend+portal e2e/screenshots)=$base"
      echo "$L writing-actual=$(grep -c 'writing actual' $W/$L.txt) snapshot-mentions=$(grep -ciE 'toHaveScreenshot|snapshot doesn|screenshot comparison|pixels .* are different' $W/$L.txt)"
      echo "$L git-dirty-paths=$(wc -l < $RAW/$L.dirty.txt | tr -d ' ') (tracked-modified=$(grep -vc '^??' $RAW/$L.dirty.txt))"
    } >> $ST
    awk '{print $2}' $RAW/$L.dirty.txt > $W/$L.dirtylist
    if [ -s $W/$L.dirtylist ]; then
      tar czf $W/$L-dirty.tgz -T $W/$L.dirtylist 2>/dev/null
      git checkout -q -- . 
      grep '^??' $RAW/$L.dirty.txt | awk '{print $2}' | xargs rm -f
    fi
    echo "$L after-restore git-status-lines=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ')" >> $ST
  )
  return $rc
}

