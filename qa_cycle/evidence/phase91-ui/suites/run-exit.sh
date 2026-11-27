#!/bin/bash
# 670A exit runner (Phase 91, tasks 670.1/670.2 gates), committed verbatim as executed.
# Order (orchestrator ruling): stack B fresh -> exit-month alone (+portal dev :3002, stopped after)
#   -> stack A fresh -> legal-lifecycle -> legal-depth-ii -> default (stack A LEFT RUNNING for Stage M)
#   -> FE-GATE -> PORTAL-GATE. One job at a time; every step's rc captured immediately.
# Writes $O/DONE only when every rc is 0 and every gate held; otherwise $O/FAILED.
set -u
source ~/.nvm/nvm.sh && nvm use 20 >/dev/null
export SHELL=/bin/bash NODE_OPTIONS="" E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026
W=/Users/rakheendama/Projects/2026/worktree-epic-670A
O=/tmp/670A/run1
P=/opt/homebrew/bin/pnpm
C=$W/compose/docker-compose.e2e.yml
mkdir -p "$O"; S="$O/summary.txt"
log(){ echo "[$(date -u +%FT%TZ)] $*" >> "$S"; }
log "start head $(git -C $W rev-parse HEAD) node $(node -v) pnpm $($P -v)"
health(){ local n=$1 u ok=0
  for u in http://localhost:8081/actuator/health http://localhost:3001/ http://localhost:8090/.well-known/jwks.json http://localhost:8026/; do
    if curl -sf -o /dev/null "$u"; then log "$n health $u ok"; else log "$n health $u FAIL"; ok=1; fi
  done; return $ok; }
fresh(){ local n=$1 rc seed
  if pgrep -f "surefire|maven|mvnw" >/dev/null; then log "$n Maven JVM present -> abort"; return 1; fi
  (cd $W && bash compose/scripts/e2e-down.sh) > $O/$n-down.log 2>&1; rc=$?; log "$n e2e-down rc=$rc"; [ $rc -eq 0 ] || return 1
  (cd $W && docker builder prune -f) > $O/$n-prune.log 2>&1; rc=$?; log "$n prune rc=$rc :: $(tail -1 $O/$n-prune.log)"
  local t=$(date +%s)
  (cd $W && bash compose/scripts/e2e-up.sh) > $O/$n-up.log 2>&1; rc=$?; log "$n e2e-up rc=$rc wall=$(( $(date +%s)-t ))s build-head=$(git -C $W rev-parse HEAD)"; [ $rc -eq 0 ] || return 1
  seed=$(docker inspect e2e-seed --format '{{.State.ExitCode}}' 2>/dev/null || echo gone); log "$n seed exit=$seed"
  if [ "$seed" != 0 ]; then
    if docker compose -f $C logs seed 2>/dev/null | grep -q "Boot-Seed Complete"; then log "$n seed log shows Boot-Seed Complete"; else log "$n seed NOT proven"; return 1; fi
  fi
  health $n || return 1
  { docker ps -a --format '{{.Names}} {{.Image}} {{.CreatedAt}} {{.Status}}' | grep e2e-
    for c in e2e-frontend e2e-backend; do docker inspect $c --format "$c {{.Image}} {{.Created}} {{index .Config.Labels \"com.docker.compose.project.working_dir\"}}"; done
  } > $O/$n-containers.txt 2>&1
  return 0; }
suite(){ local n=$1 rc t=$(date +%s); shift
  (cd $W/frontend && "$@") > $O/$n.log 2>&1; rc=$?
  log "$n rc=$rc wall=$(( $(date +%s)-t ))s :: $(grep -E '^\s+[0-9]+ (passed|failed|skipped|flaky|did not run|interrupted)' $O/$n.log | tr -s ' ' | paste -sd';' -) :: flaky/retry lines=$(grep -ciE 'flaky|retry #' $O/$n.log)"
  return $rc; }
restore(){ local n=$1
  mkdir -p $O/artefacts-$n
  (cd $W && for d in frontend/test-results frontend/e2e/screenshots documentation/screenshots/legal-vertical; do [ -d "$d" ] && cp -R "$d" "$O/artefacts-$n/" 2>/dev/null; done; true)
  (cd $W && git checkout -- frontend/test-results documentation/screenshots 2>/dev/null
   git clean -n -- frontend/test-results frontend/e2e/screenshots documentation/screenshots portal/test-results > $O/clean-$n.txt 2>&1
   git clean -fdq -- frontend/test-results frontend/e2e/screenshots documentation/screenshots portal/test-results 2>/dev/null
   git status --short > $O/git-status-$n.txt)
  log "$n tree restored; clean removed $(wc -l < $O/clean-$n.txt | tr -d ' ') paths; status lines $(wc -l < $O/git-status-$n.txt | tr -d ' ')"; }
BAD=0
# ---- stack B: exit-month alone, portal dev on :3002 ----
if fresh B; then
  cd $W/portal && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 nohup $P dev > $O/B-portal.log 2>&1 &
  PORTAL_PID=$!; cd $W
  PR=1; for i in $(seq 1 60); do curl -sf -o /dev/null http://localhost:3002/ && { PR=0; break; }; sleep 5; done
  log "B portal :3002 pid=$PORTAL_PID ready=$([ $PR -eq 0 ] && echo yes || echo NO) after ${i}x5s"
  if [ $PR -eq 0 ]; then
    suite B-exit-month env PORTAL_BASE_URL=http://localhost:3002 EXIT_MONTH_EVIDENCE_DIR=$O/exit-month-evidence $P test:e2e:exit-month || BAD=1
  else BAD=1; fi
  lsof -tiTCP:3002 -sTCP:LISTEN | xargs kill 2>/dev/null; pkill -P $PORTAL_PID 2>/dev/null; kill $PORTAL_PID 2>/dev/null; sleep 3
  log "B portal stopped; :3002 listeners now: $(lsof -tiTCP:3002 -sTCP:LISTEN | wc -l | tr -d ' ')"
  restore B
else log "B setup FAILED"; BAD=1; fi
# ---- stack A: lifecycle -> depth-ii -> default (baseline order); left running ----
if fresh A; then
  suite A-legal-lifecycle $P test:e2e:legal-lifecycle || BAD=1
  suite A-legal-depth-ii  $P test:e2e:legal-depth-ii  || BAD=1
  suite A-default         $P test:e2e                 || BAD=1
  restore A
else log "A setup FAILED"; BAD=1; fi
# ---- FE-GATE then PORTAL-GATE (every step runs; each rc logged) ----
if pgrep -f "surefire|maven|mvnw" >/dev/null; then log "gates: Maven JVM present -> abort"; BAD=1; else
for a in frontend portal; do
  [ $a = frontend ] && rm -rf $W/frontend/.next
  log "$a node $(cd $W/$a && node -v)"
  for s in lint typecheck format:check test build; do
    (cd $W/$a && $P $s) > $O/$a-${s/:/-}.log 2>&1; rc=$?; log "$a $s rc=$rc"; [ $rc -eq 0 ] || BAD=1
  done
  log "$a vitest :: $(grep -E 'Test Files|Tests  ' $O/$a-test.log | tr -s ' ' | paste -sd';' -)"
  log "$a lint :: $(grep -E '[0-9]+ problems?' $O/$a-lint.log | tail -1)"
done; fi
git -C $W status --short > $O/git-status-final.txt; log "end BAD=$BAD"
[ $BAD -eq 0 ] && touch $O/DONE || touch $O/FAILED
