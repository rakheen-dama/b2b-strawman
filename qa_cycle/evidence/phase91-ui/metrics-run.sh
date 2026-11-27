#!/bin/bash
# 670A Stage D metrics runner: each command verbatim from baseline/README.md §1 (+ extras), prints command then output.
cd /Users/rakheendama/Projects/2026/worktree-epic-670A || exit 1
B='frontend/app/(app)/org/[slug]'
run() { echo "### $1"; echo "\$ $2"; bash -c "B='$B'; $2" 2>&1; echo "(exit ${PIPESTATUS[0]})"; echo; }
echo "# 670A metrics raw output — HEAD $(git rev-parse HEAD) ($(git log -1 --format=%s | cut -c1-60)); product code = main c4595f924: git log --oneline c4595f924..HEAD -- backend frontend portal packages gateway compose prints $(git log --oneline c4595f924..HEAD -- backend frontend portal packages gateway compose | wc -l | tr -d ' ') lines"
echo "# date $(date -u +%Y-%m-%dT%H:%M:%SZ); git $(git --version | awk '{print $3}'); shell bash -c; B='$B'"
echo
run "1 Pages under org/[slug]" 'find "$B" -name page.tsx | wc -l'
run "2 PageHeader adoption" 'find "$B" -name page.tsx -exec grep -l "PageHeader" {} + | wc -l'
run "2a PageHeader adoption (listing)" 'find "$B" -name page.tsx -exec grep -l "PageHeader" {} + | sort'
run "2b page.tsx WITHOUT PageHeader (listing)" 'find "$B" -name page.tsx -exec grep -L "PageHeader" {} + | sort'
run "2c page.tsx with a <PageHeader tag (2 minus comment-only hits)" 'find "$B" -name page.tsx -exec grep -l "<PageHeader" {} + | wc -l'
run "2d page.tsx matching PageHeader but with no <PageHeader tag" 'find "$B" -name page.tsx -exec grep -l "PageHeader" {} + | while read f; do grep -q "<PageHeader" "$f" || echo "$f"; done'
run "3 Files with <h1" "grep -rl '<h1' \"\$B\" | wc -l"
run "3a Files with <h1 (listing)" "grep -rl '<h1' \"\$B\" | sort"
run "4 Display h1" 'git grep -c "font-display text-3xl" -- frontend/app frontend/components | awk -F: '"'"'{s+=$NF} END {print s}'"'"''
run "4a Display h1 (listing)" 'git grep -n "font-display text-3xl" -- frontend/app frontend/components'
run "5 Direct tables (listing)" 'grep -rlE "<Table\b|<table\b" "$B" | sort'
run "5a Direct tables by first path segment" 'grep -rlE "<Table\b|<table\b" "$B" | awk -v b="$B/" '"'"'{print substr($0, length(b)+1)}'"'"' | awk -F/ '"'"'{print $1}'"'"' | sort | uniq -c'
run "5b Direct tables (line hits)" 'grep -rnE "<Table\b|<table\b" "$B"'
run "6 EmptyState without variant (interim perl scan)" 'perl qa_cycle/evidence/phase91-ui/baseline/scripts/emptystate-scan.pl'
run "6a EmptyState scan listing" 'LIST=1 perl qa_cycle/evidence/phase91-ui/baseline/scripts/emptystate-scan.pl'
run "6b Guard allowlist" 'grep -n "const ALLOWLIST" frontend/__tests__/emptystate-variant-guard.test.ts'
run "6c variant required in the type" 'grep -n "variant" frontend/components/empty-state.tsx'
run "7 Raw hex (PCRE, *.tsx)" 'git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" | wc -l'
run "7a Raw hex (PCRE) listing" 'git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx"'
run "7b Raw hex ERE companion" 'git grep -nE "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" | wc -l'
run "8 dark: lines, whole frontend" 'git grep -c "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" | awk -F: '"'"'{s+=$NF} END {print s}'"'"''
run "8a dark: occurrences, whole frontend" 'git grep -o "dark:" -- "frontend/**/*.ts" "frontend/**/*.tsx" | wc -l'
run "8b dark: lines at baseline 551778100 (git grep on the commit)" 'git grep -c "dark:" 551778100 -- "frontend/**/*.ts" "frontend/**/*.tsx" | awk -F: '"'"'{s+=$NF} END {print s}'"'"''
run "8c dark: lines at 663C 5daacb930" 'git grep -c "dark:" 5daacb930 -- "frontend/**/*.ts" "frontend/**/*.tsx" | awk -F: '"'"'{s+=$NF} END {print s}'"'"''
run "9 page.tsx containing <h1" "find \"\$B\" -name page.tsx -exec grep -l '<h1' {} + | wc -l"
run "10 frontend/components non-test files with <h1" "git grep -l '<h1' -- frontend/components | grep -vE '__tests__|\\.test\\.' | wc -l"
run "10a listing" "git grep -l '<h1' -- frontend/components | grep -vE '__tests__|\\.test\\.'"
run "11 backend/gateway diff over the phase" 'git diff --name-only 551778100..c4595f924 -- backend gateway | wc -l'
run "12 dependency files diff over the phase" 'git diff --stat 551778100..c4595f924 -- frontend/package.json portal/package.json packages/*/package.json pnpm-lock.yaml'
run "13 migrations over the phase" 'git diff --name-only 551778100..c4595f924 -- "backend/src/main/resources/db" | wc -l'

echo "# ---- like-with-like: the same metrics on the baseline commit 551778100 via git grep on the tree (no checkout) ----"; echo
run "B3 Files with <h1 @551778100" "git grep -l '<h1' 551778100 -- 'frontend/app/(app)/org/*' | wc -l"
run "B5 Direct tables @551778100 by first path segment (git grep -P: macOS git ERE has no \\\\b)" "git grep -lP '<Table\\b|<table\\b' 551778100 -- 'frontend/app/(app)/org/*' | sed 's|^551778100:||' | awk -v b=\"\$B/\" '{print substr(\$0, length(b)+1)}' | awk -F/ '{print \$1}' | sort | uniq -c"
run "B7 Raw hex PCRE @551778100" 'git grep -nP "#[0-9a-fA-F]{3,6}\b" 551778100 -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" | wc -l'
run "B7a Raw hex lines removed / added (file:text, line numbers stripped, LC_ALL=C sort; < baseline only, > exit only)" 'diff <(git grep -nP "#[0-9a-fA-F]{3,6}\b" 551778100 -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" | sed -E "s/^551778100://; s/^([^:]+):[0-9]+:[[:space:]]*/\1: /" | LC_ALL=C sort) <(git grep -nP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" | sed -E "s/^([^:]+):[0-9]+:[[:space:]]*/\1: /" | LC_ALL=C sort)'
run "B7b Raw hex files @551778100 vs exit (count per file)" 'diff <(git grep -cP "#[0-9a-fA-F]{3,6}\b" 551778100 -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx" | sed "s/^551778100://") <(git grep -cP "#[0-9a-fA-F]{3,6}\b" -- "frontend/components/**/*.tsx" "frontend/app/**/*.tsx")'
run "B8a dark: occurrences @551778100" 'git grep -o "dark:" 551778100 -- "frontend/**/*.ts" "frontend/**/*.tsx" | wc -l'
run "B9 page.tsx containing <h1 @551778100" "git grep -l '<h1' 551778100 -- 'frontend/app/(app)/org/*page.tsx' | wc -l"
run "B10 frontend/components non-test files with <h1 @551778100" "git grep -l '<h1' 551778100 -- frontend/components | grep -vE '__tests__|\\.test\\.' | wc -l"
run "D1 dark: lines whole frontend, per Phase 91 squash (parent -> commit)" 'c(){ git grep -c "dark:" $1 -- "frontend/**/*.ts" "frontend/**/*.tsx" | awk -F: '"'"'{s+=$NF} END {print s}'"'"'; }; for x in 1862:0474dcf4e 1863:023ddb315 1864:cb8c26839 1865:d1fdeb475 1866:c66dd0575 1867:5daacb930 1868:6d0f5377e 1869:d90376640 1870:dac162907 1871:cccc20cde 1872:582ad0511 1873:4ccb88b07 1874:f8f51f37e 1875:05ea3eb5d 1876:55de88b29 1877:f8417b892 1878:795571eea 1879:8e6af4c6b 1880:22eb92ec6 1881:99f18aea3 1882:23c441961; do s=${x##*:}; a=$(c $s^); b=$(c $s); echo "#${x%%:*} $s $a -> $b ($((b-a)))"; done'
run "D2 non-PR commits in the range touching frontend (should be none)" 'git log --oneline 551778100..c4595f924 -- frontend | grep -vE "\(#18(6[2-9]|7[0-9]|8[0-2])\)$"'
run "D3 new files with dark: in the slices whose DARK-AUDIT differs from git" 'for s in 6d0f5377e 55de88b29 795571eea 8e6af4c6b 4ccb88b07; do git diff --name-only $s^ $s -- "frontend/**/*.ts" "frontend/**/*.tsx" | while read f; do a=$(git show $s^:"$f" 2>/dev/null | grep -c "dark:"); b=$(git show $s:"$f" 2>/dev/null | grep -c "dark:"); [ "$b" -gt "$a" ] && echo "$s $f $a -> $b"; done; done; true'
run "D4 665C (582ad0511) per-file dark: before -> after" 'git diff --name-only 582ad0511^ 582ad0511 -- "frontend/**/*.ts" "frontend/**/*.tsx" | while read f; do a=$(git show 582ad0511^:"$f" 2>/dev/null | grep -c "dark:"); b=$(git show 582ad0511:"$f" 2>/dev/null | grep -c "dark:"); [ "$a" != "$b" ] && echo "$f $a -> $b"; done; true'
