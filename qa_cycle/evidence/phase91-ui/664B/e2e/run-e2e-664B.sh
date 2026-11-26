#!/bin/bash
# 664B E2E gates on head 1e5a0b548 (product = 9ef3ae9c9). Stack 1: legal-lifecycle, depth-ii, projects, customers. Stack 2: exit-month (+portal :3002).
source ~/.nvm/nvm.sh && nvm use 20 >/dev/null
export SHELL=/bin/bash NODE_OPTIONS="" E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026
WT=/Users/rakheendama/Projects/2026/worktree-epic-664B
S=/tmp/e2e-664B.status; : > $S
log(){ echo "[$(date '+%H:%M:%S')] $*" >> $S; }
fresh(){ cd $WT && bash compose/scripts/e2e-down.sh > /tmp/e2e-664B-down-$1.log 2>&1; docker builder prune -f > /dev/null 2>&1; bash compose/scripts/e2e-up.sh > /tmp/e2e-664B-up-$1.log 2>&1; log "stack$1 up rc=$?"; }
run(){ cd $WT/frontend; T=$(date +%s); /opt/homebrew/bin/pnpm $1 > /tmp/e2e-664B-${1//:/-}.log 2>&1; log "$1 rc=$? wall=$(( $(date +%s)-T ))s"; }
fresh 1
run test:e2e:legal-lifecycle
run test:e2e:legal-depth-ii
run test:e2e:projects
run test:e2e:customers
fresh 2
cd $WT/portal && (NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 nohup /opt/homebrew/bin/pnpm dev > /tmp/e2e-664B-portal.log 2>&1 &) 
for i in $(seq 1 60); do curl -sf -o /dev/null http://localhost:3002/ && break; sleep 5; done; log "portal :3002 ready after ${i}x5s"
PORTAL_BASE_URL=http://localhost:3002 run test:e2e:exit-month
pkill -f "next dev.*3002" ; lsof -tiTCP:3002 -sTCP:LISTEN | xargs kill 2>/dev/null
cd $WT && git checkout -- frontend/test-results documentation/screenshots 2>/dev/null
log done; echo done > /tmp/e2e-664B.done
