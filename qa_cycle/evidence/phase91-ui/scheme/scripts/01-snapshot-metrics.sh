#!/usr/bin/env bash
# 661.1 provisional snapshot (P-2): architecture §18.1 metric commands, verbatim, on main's HEAD.
# Not the phase baseline — 661B records that on its own then-HEAD.
# Run from the repo root of a checkout of main. Output: snapshot-metrics.txt (next to this script).
set -u
B='frontend/app/(app)/org/[slug]'
echo "HEAD: $(git rev-parse HEAD)"
echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo
echo "## Pages under org/[slug]"
find "$B" -name page.tsx | wc -l
echo "## PageHeader adoption"
find "$B" -name page.tsx -exec grep -l "PageHeader" {} + | wc -l
echo "## Files with <h1"
grep -rl '<h1' "$B" | wc -l
echo "## Display h1"
git grep -c "font-display text-3xl" -- frontend/app frontend/components | awk -F: '{s+=$NF} END {print s}'
echo "## Direct tables by area (grouped by first path segment under org/[slug])"
grep -rlE "<Table\b|<table\b" "$B" | awk -v b="$B/" '{ p = substr($0, length(b) + 1); split(p, a, "/"); print a[1] }' | sort | uniq -c
echo "## Raw hex (PCRE, *.tsx)"
git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" | wc -l
echo "## dark: lines, whole frontend"
git grep -c "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" | awk -F: '{s+=$NF} END {print s}'
echo "## Phase 93 presence: take-on tab in settings-nav + its loader"
git grep -n '"take-on"' -- frontend/lib/settings-nav.ts
git grep -ln "take-on\|TakeOn" -- "frontend/app/(app)/org/[slug]/settings" | head -5
