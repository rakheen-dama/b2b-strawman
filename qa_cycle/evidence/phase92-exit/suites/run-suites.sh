#!/bin/bash
# 652F exit suites — each suite on its own fresh e2e stack, built from the worktree (= main b08a8916a).
#
# EVIDENCE OF WHAT RAN, NOT A REUSABLE GATE. The logic below is left exactly as it ran.
# Known gaps (CodeRabbit round 1, PR #1822):
#   - a suite is not gated on setup failure: fresh_stack returns e2e-up's rc but no caller checks it, and the
#     health wait gives up silently after 60 tries, so the suite runs anyway;
#   - suite failures do not reach the script's exit code or ALL.done: each rc is only logged to runner.txt,
#     and ALL.done is touched unconditionally.
# For this run the orchestrator checked each stage by hand from runs/runner.txt: every "e2e-up rc=0" and
# every suite "rc=0", with each suite's count read from its own log (runs/A-exit-month.txt ... E-portal.txt).
# Any reuse must first add those gates (stop on a failed e2e-up or health wait; fail the script and skip
# ALL.done when any suite rc != 0).
set -u
export PATH="/Users/rakheendama/.nvm/versions/node/v20.11.1/bin:/Applications/Docker.app/Contents/Resources/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin"
export NODE_OPTIONS="" SHELL=/bin/bash
W=/Users/rakheendama/Projects/2026/worktree-epic-652F
O=/tmp/652F/suites
P=/opt/homebrew/bin/pnpm
mkdir -p "$O"
caffeinate -dimsu -w $$ &

log() { echo "[$(date -u +%H:%M:%SZ)] $*" | tee -a "$O/runner.txt"; }

fresh_stack() {
  local name=$1
  log "$name: e2e-down"
  (cd "$W" && bash compose/scripts/e2e-down.sh) > "$O/$name-down.txt" 2>&1
  log "$name: e2e-up"
  (cd "$W" && bash compose/scripts/e2e-up.sh) > "$O/$name-up.txt" 2>&1
  local rc=$?
  log "$name: e2e-up rc=$rc"
  for i in $(seq 1 60); do curl -sf http://localhost:8081/actuator/health >/dev/null && curl -sf -o /dev/null http://localhost:3001/ && break; sleep 5; done
  return $rc
}

restore_tree() {
  (cd "$W" && git checkout -- frontend/test-results documentation/screenshots 2>/dev/null; git clean -fdq -- frontend/test-results frontend/e2e/screenshots documentation/screenshots portal/test-results portal/e2e/screenshots 2>/dev/null)
}

kill_3002() { lsof -ti tcp:3002 | xargs kill 2>/dev/null; sleep 2; }

# ---- A: exit month (portal dev up) ----
kill_3002
fresh_stack A-exit-month
(cd "$W/portal" && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 nohup $P dev > "$O/A-portal-dev.txt" 2>&1 &)
for i in $(seq 1 60); do curl -sf -o /dev/null http://localhost:3002/ && break; sleep 3; done
log "A: portal :3002 up"
rm -rf /tmp/652F/exit-month-evidence /tmp/652F/exit-month-report
(cd "$W/frontend" && EXIT_MONTH_EVIDENCE_DIR=/tmp/652F/exit-month-evidence E2E_AUTH_MODE=mock \
  PLAYWRIGHT_BASE_URL=http://localhost:3001 PORTAL_BASE_URL=http://localhost:3002 \
  PLAYWRIGHT_HTML_OUTPUT_DIR=/tmp/652F/exit-month-report PLAYWRIGHT_HTML_OPEN=never \
  $P test:e2e:exit-month --reporter=line,html) > "$O/A-exit-month.txt" 2>&1
log "A: exit-month rc=$?"
kill_3002
restore_tree

# ---- B: legal lifecycle ----
fresh_stack B-lifecycle
(cd "$W/frontend" && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 $P test:e2e:legal-lifecycle --reporter=line) > "$O/B-lifecycle.txt" 2>&1
log "B: lifecycle rc=$?"
restore_tree

# ---- C: legal depth-ii ----
fresh_stack C-depth-ii
(cd "$W/frontend" && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 $P test:e2e:legal-depth-ii --reporter=line) > "$O/C-depth-ii.txt" 2>&1
log "C: depth-ii rc=$?"
restore_tree

# ---- D: default ----
fresh_stack D-default
(cd "$W/frontend" && KGOSI_EMAIL=contact@acme.example.com E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 $P test:e2e --reporter=line) > "$O/D-default.txt" 2>&1
log "D: default rc=$?"
restore_tree

# ---- E: portal ----
kill_3002
fresh_stack E-portal
(cd "$W/portal" && $P test:e2e:portal --reporter=line) > "$O/E-portal.txt" 2>&1
log "E: portal rc=$?"
kill_3002
restore_tree

log "ALL DONE"
touch "$O/ALL.done"
