#!/usr/bin/env bash
# PR #1751 final-head 6859a3f17 functional e2e check: ONE fresh stack; lifecycle + project-tasks, --ignore-snapshots.
source /Users/rakheendama/Projects/2026/b2b-strawman-g89-docs/qa_cycle/evidence/g89-fixes/pr1751-e2e/final-head-6859a3f17/raw/lib.sh
caffeinate -dims & CPID=$!
cd $WT
echo "HEAD $(git rev-parse HEAD) $(ts) dirty=$(git status --short --untracked-files=all | wc -l) load=$(uptime | sed 's/.*averages: //')" >> $TL
stack_up S || { echo STACKFAIL >> $TL; kill $CPID; echo ALLDONE-STACKFAIL >> $W/status; exit 1; }
run_pw lifecycle frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle --reporter=list,json --ignore-snapshots
rm -rf $W/lifecycle-test-results; cp -R $WT/frontend/test-results $W/lifecycle-test-results
echo "load after lifecycle: $(uptime | sed 's/.*averages: //')" >> $TL
run_pw project-tasks frontend score env "${FE_ENV[@]}" /opt/homebrew/bin/pnpm test:e2e e2e/tests/projects/project-tasks.spec.ts --reporter=list,json --ignore-snapshots
rm -rf $W/project-tasks-test-results; cp -R $WT/frontend/test-results $W/project-tasks-test-results
T=$(tenant)
{ echo "===== comments (all) $(hms)"; psql_e2e "select c.entity_type||'|'||c.visibility||'|'||c.source||'|'||coalesce(m.name,'?')||'|'||c.id||'|'||left(c.body,70) from $T.comments c left join $T.members m on m.id=c.author_member_id order by c.created_at"; } >> $ST
{ echo "===== comment notifications $(hms)"; psql_e2e "select n.type||'|'||coalesce(m.name,'?')||'|'||coalesce(n.reference_entity_type,'')||'|'||coalesce(n.reference_comment_id::text,'')||'|'||left(n.title,80) from $T.notifications n left join $T.members m on m.id=n.recipient_member_id where n.type like '%COMMENT%' or n.type like '%MENTION%' order by n.created_at"; } >> $ST
{ echo "===== backend log WARN/ERROR mentioning comment|mention"; docker logs e2e-backend 2>&1 | grep -E 'WARN|ERROR' | grep -iE 'comment|mention' | head -50; echo "count=$(docker logs e2e-backend 2>&1 | grep -E 'WARN|ERROR' | grep -ciE 'comment|mention')"; } >> $ST
docker logs e2e-frontend > $W/frontend-container.log 2>&1
{ echo "===== frontend container error lines"; grep -iE 'error|unhandled' $W/frontend-container.log | head -40; } >> $ST
echo "ALLDONE $(ts)" >> $TL
echo ALLDONE >> $W/status
kill $CPID
