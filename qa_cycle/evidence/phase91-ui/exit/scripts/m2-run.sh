#!/bin/bash
# 670A M2: run capture groups one after another (each its own node process and log), detached-safe.
# Usage (from anywhere): nohup caffeinate -dimsu bash m2-run.sh <group>... > /tmp/670A-exit/m2-run.nohup 2>&1 &
# Per group: /tmp/670A-exit/<group>.log and "<group> rc=<n>" in /tmp/670A-exit/m2-rc.txt; DONE marker
# /tmp/670A-exit/m2-DONE-<first group> written at the end with the count of non-zero exits.
cd "$(dirname "$0")" || exit 2
source ~/.nvm/nvm.sh >/dev/null 2>&1 && nvm use 20 >/dev/null 2>&1
mkdir -p /tmp/670A-exit
BAD=0
for g in "$@"; do
  echo "$(date -u +%FT%TZ) start $g node $(node -v)" >> /tmp/670A-exit/m2-rc.txt
  node m2-capture.cjs "$g" > "/tmp/670A-exit/$g.log" 2>&1
  rc=$?
  [ $rc -ne 0 ] && BAD=$((BAD + 1))
  echo "$(date -u +%FT%TZ) $g rc=$rc" >> /tmp/670A-exit/m2-rc.txt
done
echo "BAD=$BAD" > "/tmp/670A-exit/m2-DONE-$1"
