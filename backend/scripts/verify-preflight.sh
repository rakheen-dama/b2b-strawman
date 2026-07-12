#!/usr/bin/env bash
# verify-preflight.sh — clear zombie Maven/Surefire JVMs and refuse to start a
# second concurrent verify. Root-cause fix for lessons.md failure modes:
#   - zombie launcher JVMs loading the machine (GreenMail 2s startup timeout, 2026-06-10)
#   - orphaned surefire forks holding SMTP port 13025 (2026-05-29, 2026-06-11/12)
# Usage: bash backend/scripts/verify-preflight.sh   (or via scripts/verify.sh)
set -euo pipefail

# Anything older than this is a zombie. Healthy full verify ~23 min; worst
# observed legitimate wall time well under 60. Override: PREFLIGHT_MAX_AGE_MIN=n
MAX_AGE_MIN="${PREFLIGHT_MAX_AGE_MIN:-90}"
SMTP_PORT=13025

# Parse ps etime ([[dd-]hh:]mm:ss) into whole minutes.
etime_to_minutes() {
  local e="$1" days=0 hours=0 mins=0
  if [[ "$e" == *-* ]]; then days="${e%%-*}"; e="${e#*-}"; fi
  local IFS=':'; read -r -a p <<<"$e"
  case "${#p[@]}" in
    3) hours="${p[0]}"; mins="${p[1]}" ;;
    2) mins="${p[0]}" ;;
  esac
  echo $(( 10#$days * 1440 + 10#$hours * 60 + 10#$mins ))
}

live=0
# Match only Maven launcher + surefire fork JVMs: surefirebooter jar, ForkedBooter,
# classworlds Launcher, -Dmaven.home. Excludes IDEs, gradle daemons, app servers.
while read -r pid etime cmd; do
  [[ -z "${pid:-}" ]] && continue
  age="$(etime_to_minutes "$etime")"
  if (( age >= MAX_AGE_MIN )); then
    echo "preflight: KILLING zombie JVM (age ${age}m >= ${MAX_AGE_MIN}m): pid=$pid ${cmd:0:140}" >&2
    kill -9 "$pid" 2>/dev/null || true
  else
    live=$(( live + 1 ))
    echo "preflight: LIVE Maven/Surefire JVM (age ${age}m): pid=$pid ${cmd:0:140}" >&2
  fi
done < <(ps -axo pid=,etime=,command= \
          | grep -E 'java' \
          | grep -E 'surefirebooter|ForkedBooter|classworlds\.launcher|maven\.home' \
          | grep -v grep || true)

if (( live > 0 )); then
  echo "preflight: another verify appears to be RUNNING (${live} live JVM(s))." >&2
  echo "preflight: REFUSING to start a second concurrent verify (two runs contend on CPU and the embedded Postgres; email cascades have followed)." >&2
  exit 1
fi

# GreenMail now binds a DYNAMIC port (scan starting at 13025 — see
# GreenMailTestSupport), so a held port no longer blocks a new run: the next
# JVM simply picks the next free port. A holder after the zombie sweep is
# still worth flagging (it is usually an untracked leftover process), but it
# is a WARNING, not a refusal.
if lsof -nP -iTCP:"$SMTP_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "preflight: WARNING — port ${SMTP_PORT} (GreenMail scan start / yml fallback) is held after the zombie sweep:" >&2
  lsof -nP -iTCP:"$SMTP_PORT" -sTCP:LISTEN >&2 || true
  echo "preflight: continuing — GreenMail's dynamic port scan will skip past it. Investigate the holder if it is not expected." >&2
else
  echo "preflight: :${SMTP_PORT} free." >&2
fi

echo "preflight: clean — no stale Maven/Surefire JVMs." >&2
