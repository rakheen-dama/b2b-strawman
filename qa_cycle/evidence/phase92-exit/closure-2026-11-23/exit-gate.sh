#!/bin/bash
# Phase 92 exit gate re-run on main (FU closure). One suite per fresh stack. Every stage's rc is checked and recorded.
WT=/Users/rakheendama/Projects/2026/wt-exit
OUT=/tmp/exit92; mkdir -p $OUT/runs
log () { echo "[$(date -u +%FT%TZ)] $*" >> $OUT/runs/runner.txt; }
caffeinate -dimsu -w $$ &
export PATH=/Users/rakheendama/.nvm/versions/node/v20.11.1/bin:$PATH NODE_OPTIONS="" SHELL=/bin/bash
cd $WT; log "head $(git rev-parse HEAD)"
# 1. BE-VERIFY + harness
cd $WT/backend && ./mvnw -q spotless:check > $OUT/runs/spotless.txt 2>&1; log "spotless rc=$?"
bash scripts/verify.sh -q > $OUT/runs/be-verify.txt 2>&1; rc=$?; log "BE-VERIFY rc=$rc"; echo $rc > $OUT/be.rc
cd $WT && bash qa_cycle/evidence/phase92-exit/harness-check.sh backend/target/surefire-reports > $OUT/runs/harness-check.txt 2>&1; log "harness rc=$? $(tail -1 $OUT/runs/harness-check.txt)"
# 2. FE + portal gates (idle machine)
cd $WT && /opt/homebrew/bin/pnpm install --frozen-lockfile > $OUT/runs/install.txt 2>&1; log "install rc=$?"
cd $WT/frontend && ( rm -rf .next && /opt/homebrew/bin/pnpm lint && /opt/homebrew/bin/pnpm typecheck && /opt/homebrew/bin/pnpm format:check && /opt/homebrew/bin/pnpm test && /opt/homebrew/bin/pnpm build ) > $OUT/runs/fe-gate.txt 2>&1; log "FE-GATE rc=$?"
cd $WT/portal && ( rm -rf .next && /opt/homebrew/bin/pnpm lint && /opt/homebrew/bin/pnpm typecheck && /opt/homebrew/bin/pnpm format:check && /opt/homebrew/bin/pnpm test && /opt/homebrew/bin/pnpm build ) > $OUT/runs/portal-gate.txt 2>&1; log "PORTAL-GATE rc=$?"
fresh () { # $1 = stack letter; returns non-zero if the stack is not healthy
  lsof -ti :3002 | xargs kill 2>/dev/null
  cd $WT && bash compose/scripts/e2e-down.sh > $OUT/runs/$1-down.txt 2>&1
  bash compose/scripts/e2e-up.sh > $OUT/runs/$1-up.txt 2>&1; local up=$?; log "stack $1 e2e-up rc=$up"
  [ $up -eq 0 ] || return 1
  for i in $(seq 1 60); do curl -sf http://localhost:8081/actuator/health >/dev/null && curl -s -o /dev/null http://localhost:3001/ && return 0; sleep 5; done
  log "stack $1 health wait FAILED"; return 1
}
restore () { cd $WT && git checkout -- frontend/test-results documentation/screenshots 2>/dev/null; git clean -fdq -- frontend/test-results frontend/e2e/screenshots documentation/screenshots portal/test-results 2>/dev/null; }
# A. exit month (portal on :3002)
if fresh A; then
  cd $WT/portal && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 /opt/homebrew/bin/pnpm dev > $OUT/runs/A-portal-dev.txt 2>&1 & PP=$!
  for i in $(seq 1 60); do curl -s -o /dev/null http://localhost:3002/ && break; sleep 5; done
  cd $WT/frontend && EXIT_MONTH_EVIDENCE_DIR=$OUT/exit-month-evidence E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 PORTAL_BASE_URL=http://localhost:3002 PLAYWRIGHT_HTML_OUTPUT_DIR=$OUT/exit-month-report PLAYWRIGHT_HTML_OPEN=never /opt/homebrew/bin/pnpm test:e2e:exit-month --reporter=line > $OUT/runs/A-exit-month.txt 2>&1; log "A exit-month rc=$?"
  kill $PP 2>/dev/null; lsof -ti :3002 | xargs kill 2>/dev/null
else log "A SKIPPED: stack not healthy"; fi; restore
# B. lifecycle
if fresh B; then cd $WT/frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=line > $OUT/runs/B-lifecycle.txt 2>&1; log "B lifecycle rc=$?"; else log "B SKIPPED"; fi; restore
# C. depth-ii
if fresh C; then cd $WT/frontend && E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=line > $OUT/runs/C-depth-ii.txt 2>&1; log "C depth-ii rc=$?"; else log "C SKIPPED"; fi; restore
# D. default
if fresh D; then cd $WT/frontend && KGOSI_EMAIL=contact@acme.example.com E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 /opt/homebrew/bin/pnpm test:e2e --reporter=line > $OUT/runs/D-default.txt 2>&1; log "D default rc=$?"; else log "D SKIPPED"; fi; restore
# E. portal
if fresh E; then cd $WT/portal && /opt/homebrew/bin/pnpm test:e2e:portal --reporter=line > $OUT/runs/E-portal.txt 2>&1; log "E portal rc=$?"; lsof -ti :3002 | xargs kill 2>/dev/null; else log "E SKIPPED"; fi; restore
cd $WT && bash compose/scripts/e2e-down.sh > $OUT/runs/final-down.txt 2>&1
log "ALL DONE"; echo done > $OUT/ALL.done
