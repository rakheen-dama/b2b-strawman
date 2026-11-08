#!/usr/bin/env bash
# waits until UTC date == SAST date (+60 s margin past 02:00 SAST), holding caffeinate, then runs run-final.sh
caffeinate -dims -w $$ &
echo "launch-final waiting from $(date '+%FT%T%z')" > /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/final/wait.log
until [ "$(date -u +%F)" = "$(TZ=Africa/Johannesburg date +%F)" ] && [ "$(date -u +%H%M)" -ge 0001 ]; do sleep 20; done
echo "launch-final start $(date '+%FT%T%z') utc=$(date -u +%FT%T)" >> /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/final/wait.log
bash /private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/run-final.sh
