#!/bin/bash
# usage: stack.sh <tag>   — e2e-down then e2e-up from the 648E worktree; log + rc marker (nohup this)
WT=/Users/rakheendama/Projects/2026/worktree-epic-648E
G=$WT/qa_cycle/evidence/phase92/648E/gates-6b4615074
cd $WT
echo "$1-down-pre start $(date -u +%FT%TZ)" >> $G/timings.txt
SHELL=/bin/bash bash compose/scripts/e2e-down.sh > $G/$1-down-pre.log 2>&1
echo "$1-down-pre end $(date -u +%FT%TZ) rc $?" >> $G/timings.txt
echo "$1-up start $(date -u +%FT%TZ)" >> $G/timings.txt
SHELL=/bin/bash bash compose/scripts/e2e-up.sh > $G/$1-up.log 2>&1
rc=$?
echo "$1-up end $(date -u +%FT%TZ) rc $rc" >> $G/timings.txt
echo $rc > $G/.$1-up.rc
