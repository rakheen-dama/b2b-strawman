#!/bin/bash
# usage: run-suite.sh <script-name> <tag>   e.g. test:e2e:legal-lifecycle lc1
WT=/Users/rakheendama/Projects/2026/worktree-epic-648E
G=$WT/qa_cycle/evidence/phase92/648E/gates-6b4615074
export PATH="$HOME/.nvm/versions/node/v20.11.1/bin:$PATH"
export NODE_OPTIONS="" PLAYWRIGHT_BASE_URL=http://localhost:3001
echo ".$2.start $(date -u +%FT%TZ)" >> $G/timings.txt
git -C $WT status --short > $G/$2-tree-pre.txt
/opt/homebrew/bin/pnpm --dir $WT/frontend run $1 --reporter=list > $G/$2.log 2>&1
rc=$?
echo ".$2.end $(date -u +%FT%TZ) rc $rc" >> $G/timings.txt
git -C $WT status --short > $G/$2-tree-post.txt
echo $rc > $G/.$2.rc
