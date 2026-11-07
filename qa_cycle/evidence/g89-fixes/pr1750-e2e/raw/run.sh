#!/usr/bin/env bash
# PR #1750 e2e gate at 53cabef57 — CP1 recipe (p1..p4 of pr1749): vitest; C1 capture legal; G1 scored legal+default; C2 capture portal; G2 scored portal
source /tmp/pr1750/lib.sh
cd $WT
echo "HEAD $(git rev-parse HEAD) $(ts)" >> $TL
caffeinate -dims & echo $! > $W/caffeinate.pid
echo "portal-vitest start $(ts) node=$(cd $WT/portal && /opt/homebrew/bin/pnpm exec node -v) envnode=$(env node -v)" >> $TL
( cd $WT/portal && NODE_OPTIONS= /opt/homebrew/bin/pnpm test ) > $RAW/portal-vitest.txt 2>&1
echo "portal-vitest rc=$? end $(ts)" >> $TL
# --- C1 capture legal
stack_up C1 || { echo C1-STACKFAIL >> $TL; exit 1; }
run_pw C1-lifecycle frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --update-snapshots=all
run_pw C1-depth-ii frontend capture env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json --update-snapshots=all
cp $W/C1-depth-ii.txt $RAW/C1-depth-ii.txt; mktsv $W/C1-depth-ii.json $RAW/C1-depth-ii.tsv; mktsv $W/C1-lifecycle.json $RAW/C1-lifecycle.tsv
echo C1-ALLDONE >> $TL
# --- G1 scored legal + default
stack_up G1 || { echo G1-STACKFAIL >> $TL; exit 1; }
run_pw G1-lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json
rm -rf $W/G1-lifecycle-test-results; cp -R $WT/frontend/test-results $W/G1-lifecycle-test-results
run_pw G1-depth-ii frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json
rm -rf $W/G1-depth-ii-test-results; cp -R $WT/frontend/test-results $W/G1-depth-ii-test-results
{ echo "===== G1 generated_documents after depth-ii $(hms)"; psql_e2e "select file_name||'|'||contains_trust_data from $(tenant).generated_documents order by generated_at"; } >> $ST
run_pw G1-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
rm -rf $W/G1-default-test-results; cp -R $WT/frontend/test-results $W/G1-default-test-results
{ echo "===== G1 projects after default"; psql_e2e "select name||'|'||status from $(tenant).projects order by created_at"; } >> $ST
echo G1-ALLDONE >> $TL
# --- C2 capture portal
stack_up C2 || { echo C2-STACKFAIL >> $TL; exit 1; }
run_pw C2-90day portal capture env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:portal-client-90day --reporter=list,json --update-snapshots=all
spacing C2-responsive
run_pw C2-responsive portal capture env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:responsive --reporter=list,json --update-snapshots=all
echo C2-ALLDONE >> $TL
# --- G2 scored portal
stack_up G2 || { echo G2-STACKFAIL >> $TL; exit 1; }
T=$(tenant)
echo "G2-90day pre: tokens|age=$(psql_e2e "select count(*)||'|'||coalesce(extract(epoch from now()-max(created_at))::int,0) from $T.magic_link_tokens")" >> $ST
run_pw G2-90day portal score env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:portal-client-90day --reporter=list,json
{ echo "== G2-90day post-checks $(hms)"; echo "portal_deadline_view=$(psql_e2e "select count(*) from portal.portal_deadline_view")"; } >> $ST
exch G2-90day
psql_e2e "select request_number||'|'||status||'|'||created_at from $T.information_requests order by created_at desc" >> $ST
spacing G2-responsive
run_pw G2-responsive portal score env "${PO_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:responsive --reporter=list,json
exch G2-responsive
spacing G2-isolation
run_pw G2-isolation frontend score env "${FE_ENV[@]}" KGOSI_EMAIL=contact@acme.example.com /opt/homebrew/bin/pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/portal/portal-data-isolation.spec.ts --reporter=list,json
exch G2-isolation
spacing G2-login-bare
run_pw G2-login-bare portal score env NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 /opt/homebrew/bin/pnpm test:e2e:login-bare --reporter=list,json
exch G2-login-bare
{ echo "===== G2 generated_documents $(hms)"; psql_e2e "select file_name||'|'||contains_trust_data from $T.generated_documents order by generated_at"; } >> $ST
echo ALLDONE >> $TL
