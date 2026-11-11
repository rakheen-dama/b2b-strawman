#!/usr/bin/env bash
# waits until UTC date == SAST date and UTC >= 00:01 (i.e. >= 02:01 SAST; G89-F66 window), holding caffeinate, then runs run.sh
S=/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1752
caffeinate -dims -w $$ &
echo "launch waiting from $(date '+%FT%T%z')" > $S/wait.log
until [ "$(date -u +%F)" = "$(TZ=Africa/Johannesburg date +%F)" ] && [ "$(date -u +%H%M)" -ge 0001 ]; do sleep 20; done
echo "launch start $(date '+%FT%T%z') utc=$(date -u +%FT%T)" >> $S/wait.log
bash $S/run.sh
echo "run.sh exit=$? $(date '+%FT%T%z')" >> $S/wait.log
