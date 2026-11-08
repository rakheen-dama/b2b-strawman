#!/usr/bin/env bash
# PR #1751 gate — attempt 2 on PR head f3c191bb7 (same recipe), midnight SAST/UTC date window
source /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/lib-a2.sh
cd $WT
echo "HEAD $(git rev-parse HEAD) $(ts)" >> $TL
caffeinate -dims & echo $! > $W/caffeinate.pid
echo "pnpm-install start $(ts) node=$(node -v)" >> $TL
( cd $WT && NODE_OPTIONS= /opt/homebrew/bin/pnpm install --frozen-lockfile ) > $W/pnpm-install.txt 2>&1
echo "pnpm-install rc=$? end $(ts)" >> $TL
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
{ echo "===== G1 comments after lifecycle $(hms)"; psql_e2e "select c.entity_type||'|'||c.visibility||'|'||c.source||'|'||coalesce(m.name,'?')||'|'||left(c.body,70) from $(tenant).comments c left join $(tenant).members m on m.id=c.author_member_id order by c.created_at"; } >> $ST
run_pw G1-depth-ii frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-depth-ii --reporter=list,json
rm -rf $W/G1-depth-ii-test-results; cp -R $WT/frontend/test-results $W/G1-depth-ii-test-results
run_pw G1-default frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e --reporter=list,json
rm -rf $W/G1-default-test-results; cp -R $WT/frontend/test-results $W/G1-default-test-results
T=$(tenant)
{ echo "===== G1 comments (all) after default $(hms)"; psql_e2e "select c.entity_type||'|'||c.visibility||'|'||c.source||'|'||coalesce(m.name,'?')||'|'||c.id||'|'||left(c.body,70) from $T.comments c left join $T.members m on m.id=c.author_member_id order by c.created_at"; } >> $ST
{ echo "===== G1 comment notifications after default $(hms)"; psql_e2e "select n.type||'|'||coalesce(m.name,'?')||'|'||coalesce(n.reference_entity_type,'')||'|'||coalesce(n.reference_comment_id::text,'')||'|'||left(n.title,80) from $T.notifications n left join $T.members m on m.id=n.recipient_member_id where n.type like '%COMMENT%' or n.type like '%MENTION%' order by n.created_at"; } >> $ST
{ echo "===== G1 projects after default"; psql_e2e "select name||'|'||status from $T.projects order by created_at"; } >> $ST
{ echo "===== G1 backend log scan (comment/notification WARN|ERROR)"; docker logs e2e-backend 2>&1 | grep -iE '(WARN|ERROR).*(comment|notification|mention)' | tail -40; echo "count=$(docker logs e2e-backend 2>&1 | grep -ciE '(WARN|ERROR).*(comment|notification|mention)')"; } >> $ST
echo ALLDONE >> $TL
kill $(cat $W/caffeinate.pid) 2>/dev/null
