#!/usr/bin/env bash
# PR #1753 e2e gate, part 1 at b146c09e0: install, portal vitest, G1 (fresh stack) scored legal-lifecycle,
# legal-depth-ii, default test:e2e with E2E_VISUAL unset; F46 frontend evidence; E2E_VISUAL=1 opt-in check.
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1753/lib.sh
unset E2E_VISUAL
cd $WT
echo "HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --porcelain | wc -l | tr -d ' ') E2E_VISUAL=${E2E_VISUAL:-unset}" >> $TL
caffeinate -dims & echo $! > $W/caffeinate-g1.pid
echo "pnpm-install start $(ts) node=$(node -v)" >> $TL
/opt/homebrew/bin/pnpm install --frozen-lockfile > $RAW/pnpm-install.txt 2>&1
echo "pnpm-install rc=$? end $(ts)" >> $TL
echo "portal-vitest start $(ts) node=$(cd $WT/portal && /opt/homebrew/bin/pnpm exec node -v) envnode=$(env node -v)" >> $TL
( cd $WT/portal && NODE_OPTIONS= /opt/homebrew/bin/pnpm test ) > $RAW/portal-vitest.txt 2>&1
echo "portal-vitest rc=$? end $(ts)" >> $TL
# --- G1 fresh stack
stack_up G1 || { echo G1-STACKFAIL >> $TL; kill $(cat $W/caffeinate-g1.pid); exit 1; }
{ echo "===== F46 frontend e2e image build (stack-G1.log)";
  echo "next build lines:"; grep -nE 'frontend builder .*RUN pnpm --filter frontend build|> next build|Compiled successfully|Generating static pages .*\(14/14\)|frontend .* DONE' $W/stack-G1.log | head -12;
  echo "google-font fetch lines (fonts.googleapis|fonts.gstatic|Failed to fetch|next/font/google|font/google): $(grep -ciE 'fonts.googleapis|fonts.gstatic|Failed to fetch|next/font/google|font/google' $W/stack-G1.log)";
  grep -niE 'fonts.googleapis|fonts.gstatic|Failed to fetch|next/font/google|font/google' $W/stack-G1.log | head;
  echo "built CSS in image: google refs / woff2 media files";
  docker exec e2e-frontend sh -c 'grep -rlE "fonts.g(oogleapis|static)" /app/frontend/.next/static 2>/dev/null | wc -l; ls /app/frontend/.next/static/media/ 2>/dev/null | grep -c woff2' 2>&1;
} > $RAW/f46-frontend-build.txt
fontprobe frontend-mock-login http://localhost:3001 /mock-login
run_g G1-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
run_g G1-depth-ii frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json
run_g G1-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
T=$(tenant)
{ echo "===== G1 projects after default"; psql_e2e "select name||'|'||status from $T.projects order by created_at";
  echo "===== G1 backend WARN/ERROR"; echo "all WARN/ERROR count=$(docker logs e2e-backend 2>&1 | grep -cE ' (WARN|ERROR) ')"; echo "ERROR count=$(docker logs e2e-backend 2>&1 | grep -cE ' ERROR ')";
  docker logs e2e-backend 2>&1 | grep -E ' (WARN|ERROR) ' | sed -E 's/^.* (WARN|ERROR) [0-9]+ --- \[[^]]*\] //' | cut -c1-140 | sort | uniq -c | sort -rn | head -25; } >> $ST
docker logs e2e-backend > $W/G1-backend.log 2>&1
# --- F46 (c): frontend dashboard screenshot + loaded font faces
echo "frontend-shot start $(ts)" >> $TL
node $W/shot.cjs $WT/frontend frontend http://localhost:3001 $RAW/f46-frontend-dashboard.png > $RAW/f46-frontend-dashboard.json 2>&1
echo "frontend-shot rc=$? end $(ts)" >> $TL
# --- Opt-in still live: E2E_VISUAL=1 on one regression-mode screenshot test (informational)
OPT=(e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts -g "Capture dashboard with legal navigation")
touch $W/VIS.marker; sleep 1
run_pw VIS1-optin-missing frontend score env "${FE_ENV[@]}" E2E_VISUAL=1 /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.legal-lifecycle.config.ts "${OPT[@]}" --reporter=list,json
cp $WT/frontend/e2e/screenshots/legal-lifecycle/day-00-dashboard-legal-nav-active.png $RAW/VIS1-written-actual-day-00-dashboard-legal-nav-active.png 2>/dev/null
run_pw VIS2-optin-compare frontend score env "${FE_ENV[@]}" E2E_VISUAL=1 /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.legal-lifecycle.config.ts "${OPT[@]}" --reporter=list,json
{ echo "== VIS accounting $(ts)"; echo "baseline files written since VIS start:"; find $WT/frontend/e2e/screenshots -type f -newer $W/VIS.marker | sed 's/^/    /';
  for v in VIS1-optin-missing VIS2-optin-compare; do echo "$v stats=$(jq -c .stats $RAW/$v.json) writing-actual=$(grep -c 'writing actual' $RAW/$v.txt)"; grep -nE "snapshot|writing actual|toHaveScreenshot|passed|failed" $RAW/$v.txt | head -8 | sed 's/^/    /'; done; } >> $ST
( cd $WT; git status --porcelain --untracked-files=all > $RAW/VIS.dirty.txt; awk '{print $2}' $RAW/VIS.dirty.txt > $W/VIS.dirtylist
  [ -s $W/VIS.dirtylist ] && { tar czf $W/VIS-dirty.tgz -T $W/VIS.dirtylist; git checkout -q -- .; grep '^??' $RAW/VIS.dirty.txt | awk '{print $2}' | xargs rm -f; }
  echo "VIS after-restore git-status-lines=$(git status --porcelain --untracked-files=all | wc -l | tr -d ' ')" >> $ST )
echo "G1-ALLDONE $(ts)" >> $TL
kill $(cat $W/caffeinate-g1.pid) 2>/dev/null
