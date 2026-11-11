#!/usr/bin/env bash
# per-test diff of each scored run vs the PR #1755 gate TSVs; stats from Playwright JSON
PREV=/Users/rakheendama/Projects/2026/b2b-strawman-g89-docs/qa_cycle/evidence/g89-fixes/pr1755-e2e/raw
RAW=/Users/rakheendama/Projects/2026/b2b-strawman-g89-docs/qa_cycle/evidence/g89-fixes/exit-rescore/suites/raw
for L in "$@"; do
  [ -f $RAW/$L.json ] || { echo "$L: no json"; continue; }
  s=$(jq -r '.stats|"expected=\(.expected) unexpected=\(.unexpected) skipped=\(.skipped) flaky=\(.flaky)"' $RAW/$L.json)
  dnr=$(awk -F'\t' '$5=="notrun"' $RAW/$L.tsv | wc -l | tr -d ' ')
  d=$(diff <(cut -f1,3,4,5 $PREV/$L.tsv | sort) <(cut -f1,3,4,5 $RAW/$L.tsv | sort))
  echo "== $L  $s dnr=$dnr  rows prev=$(wc -l < $PREV/$L.tsv | tr -d ' ') now=$(wc -l < $RAW/$L.tsv | tr -d ' ')  diff-lines=$(printf '%s' "$d" | grep -c .)"
  [ -n "$d" ] && echo "$d"
done
