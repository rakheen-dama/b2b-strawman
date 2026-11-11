#!/usr/bin/env bash
# PR #1753 e2e gate, part 2 at b146c09e0: standalone portal production build (F46 log), G2 (fresh stack) portal
# 90-day, responsive, portal-data-isolation, login-bare (production portal), then F46 portal probe + screenshot.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1753/lib.sh
unset E2E_VISUAL
cd $WT
echo "G2 HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain | wc -l | tr -d ' ') E2E_VISUAL=${E2E_VISUAL:-unset}" >> $TL
caffeinate -dims & echo $! > $W/caffeinate-g2.pid
# --- F46 (a): portal production build log (same command + env the login-bare webServer uses)
echo "portal-prod-build start $(ts)" >> $TL
( cd $WT/portal && env NODE_OPTIONS= NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 /opt/homebrew/bin/pnpm run build ) > $RAW/f46-portal-prod-build.txt 2>&1
echo "portal-prod-build rc=$? end $(ts) google-font-lines=$(grep -ciE 'fonts.googleapis|fonts.gstatic|Failed to fetch|next/font/google|font/google' $RAW/f46-portal-prod-build.txt)" >> $TL
# --- G2 fresh stack
stack_up G2 || { echo G2-STACKFAIL >> $TL; kill $(cat $W/caffeinate-g2.pid); exit 1; }
T=$(tenant)
echo "G2-90day pre: tokens|age=$(psql_e2e "select count(*)||'|'||coalesce(extract(epoch from now()-max(created_at))::int,0) from $T.magic_link_tokens")" >> $ST
run_g G2-90day portal score env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:portal-client-90day --reporter=list,json
exch G2-90day
spacing G2-responsive
run_g G2-responsive portal score env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:responsive --reporter=list,json
exch G2-responsive
spacing G2-isolation
run_g G2-isolation frontend score env "${FE_ENV[@]}" KGOSI_EMAIL=contact@acme.example.com /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/portal/portal-data-isolation.spec.ts --reporter=list,json
exch G2-isolation
spacing G2-login-bare
run_g G2-login-bare portal score env NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 /opt/homebrew/bin/pnpm test:e2e:login-bare --reporter=list,json
exch G2-login-bare
echo "G2-login-bare google-font-lines=$(grep -ciE 'fonts.googleapis|fonts.gstatic|Failed to fetch|next/font/google|font/google' $RAW/G2-login-bare.txt)" >> $ST
docker logs e2e-backend > $W/G2-backend.log 2>&1
{ echo "===== G2 backend WARN/ERROR"; echo "all WARN/ERROR count=$(grep -cE ' (WARN|ERROR) ' $W/G2-backend.log)"; echo "ERROR count=$(grep -cE ' ERROR ' $W/G2-backend.log)"; } >> $ST
# --- F46 (b)+(c) on the production portal build (the .next login-bare just built)
spacing F46-portal-shot
( cd $WT/portal && nohup env NODE_OPTIONS= /opt/homebrew/bin/pnpm run start > $W/portal-start.log 2>&1 < /dev/null & echo $! > $W/portal-start.pid )
until curl -sf -o /dev/null http://localhost:3002/login; do sleep 2; done
echo "portal prod server up $(ts)" >> $TL
fontprobe portal-login http://localhost:3002 /login
ML=$(curl -s -X POST http://localhost:8081/portal/auth/request-link -H 'Content-Type: application/json' -d '{"email":"contact@acme.example.com","orgId":"e2e-test-org"}' | jq -r .magicLink)
echo "portal magicLink path present: $([ -n "$ML" ] && [ "$ML" != null ] && echo yes || echo NO)" >> $ST
node $W/shot.cjs $WT/portal portal "http://localhost:3002$ML" $RAW/f46-portal-home.png > $RAW/f46-portal-home.json 2>&1
echo "portal-shot rc=$? end $(ts)" >> $TL
kill $(cat $W/portal-start.pid) 2>/dev/null; sleep 2
lsof -tiTCP:3002 -sTCP:LISTEN | xargs kill 2>/dev/null
( cd $WT; git status --porcelain --untracked-files=all > $RAW/G2-final.dirty.txt; echo "G2 final git-status-lines=$(wc -l < $RAW/G2-final.dirty.txt | tr -d ' ')" >> $ST )
echo "G2-ALLDONE $(ts)" >> $TL
kill $(cat $W/caffeinate-g2.pid) 2>/dev/null
