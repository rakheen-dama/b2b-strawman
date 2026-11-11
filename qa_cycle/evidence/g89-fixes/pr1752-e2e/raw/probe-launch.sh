#!/usr/bin/env bash
S=/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1752
TL=/Users/rakheendama/Projects/2026/b2b-strawman-g89-docs/qa_cycle/evidence/g89-fixes/pr1752-e2e/raw/timeline.txt
until ! pgrep -f "$S/run.sh" >/dev/null; do sleep 15; done
echo "probe-launch: run.sh gone $(date '+%FT%T%z'); last=$(tail -1 $TL)" >> $S/wait.log
bash $S/probe.sh
echo "probe.sh exit=$? $(date '+%FT%T%z')" >> $S/wait.log
