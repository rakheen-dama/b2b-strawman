#!/usr/bin/env bash
#
# check-verify-markers.sh — prove a merged PR's verify markers were committed (G88-F35)
#
# The merge-gate hook reads .claude/markers/verify-<area>.json from the working tree at
# merge time; it cannot tell whether the marker was ever committed. This script can: for
# every area the PR touched it requires a verify-<area>.json whose "commit" field is a
# commit FROM THE PR BRANCH — the merged PR head or an ancestor of it that is not already
# an ancestor of main at the branch point (<mergeCommit>^) — and that marker must be
#   (a) committed: in a commit of <mergeCommit>^..HEAD touching the marker file
#       (inclusive of the squash-merge commit itself), or
#   (b) staged: in the current index, when the index differs from HEAD for that path.
# Unstaged working-tree edits are never read.
#
# Areas (same mapping as .claude/hooks/pre-pr-merge-gate.sh):
#   backend/  → backend     frontend/ → frontend     portal/ → portal
#   packages/ → frontend AND portal (shared @b2mash/* packages are consumed by both apps)
#
# It operates on the checkout you run it FROM (`git rev-parse --show-toplevel` of the
# current directory and its index), not on the script's own location: /epic_v2 Step 5
# stages markers in whatever checkout does the bookkeeping commit.
#
# Usage:
#   scripts/check-verify-markers.sh <pr-number>
#
# Examples:
#   bash scripts/check-verify-markers.sh 1737     # after `git add .claude/markers/...`
#
# Exit codes:
#   0  every touched area has a qualifying marker (or the PR is docs/tooling-only)
#   1  at least one area has no qualifying marker (all missing areas are named)
#   2  usage / environment / PR-state error (bad arg, unknown or unmerged PR, gh/jq/git
#      missing, not in a git checkout, merge commit not in HEAD, PR head not fetchable)
#
# Requires: git, gh (authenticated), jq. Portable to macOS /bin/bash 3.2.
#
set -euo pipefail

PROG=check-verify-markers

usage() {
  echo "Usage: scripts/check-verify-markers.sh <pr-number>" >&2
  echo "  exit 0 = every touched area (backend/frontend/portal) has a committed or staged" >&2
  echo "           verify marker from the PR branch, or the PR is docs/tooling-only" >&2
  echo "  exit 1 = at least one area has no qualifying marker" >&2
  echo "  exit 2 = usage / environment / PR-state error" >&2
}

die() {
  echo "$PROG: $*" >&2
  exit 2
}

if [ $# -ne 1 ]; then
  usage
  exit 2
fi
case "$1" in
  -h|--help) usage; exit 0 ;;
esac
pr=$1
if ! [[ "$pr" =~ ^[0-9]+$ ]]; then
  echo "$PROG: '$pr' is not a PR number" >&2
  usage
  exit 2
fi

for tool in git gh jq; do
  command -v "$tool" >/dev/null 2>&1 || die "'$tool' is required but not on PATH"
done
root=$(git rev-parse --show-toplevel 2>/dev/null) || die "not inside a git checkout"
cd "$root"

# 1. PR metadata. '|' delimiter: an empty TSV field would collapse under `read`.
meta=$(gh pr view "$pr" --json state,mergeCommit,headRefOid \
  --jq '"\(.state)|\(.mergeCommit.oid // "-")|\(.headRefOid)"' 2>/dev/null) \
  || die "gh pr view $pr failed (no such PR, offline, or gh not authenticated)"
IFS='|' read -r state merge_oid head_oid <<<"$meta"
if [ "$state" != MERGED ] || [ "$merge_oid" = "-" ]; then
  die "PR #$pr is $state, not merged — nothing to check"
fi

# 2. Areas. `gh pr view --json files` truncates at 100 files; the REST API paginates.
files=$(gh api "repos/{owner}/{repo}/pulls/$pr/files" --paginate --jq '.[].filename' 2>/dev/null) \
  || die "could not list the files of PR #$pr"
areas=""
if printf '%s\n' "$files" | grep -qE '^backend/'; then areas="$areas backend"; fi
if printf '%s\n' "$files" | grep -qE '^(frontend|packages)/'; then areas="$areas frontend"; fi
if printf '%s\n' "$files" | grep -qE '^(portal|packages)/'; then areas="$areas portal"; fi
if [ -z "$areas" ]; then
  echo "$PROG: PR #$pr touches no backend/, frontend/, packages/ or portal/ path — docs/tooling-only, no verify marker required"
  exit 0
fi

# 3. The merge commit must be in HEAD; the PR head must be a local object.
if ! git cat-file -e "${merge_oid}^{commit}" 2>/dev/null \
  || ! git merge-base --is-ancestor "$merge_oid" HEAD; then
  die "HEAD does not contain PR #$pr's merge commit ${merge_oid:0:9} — run 'git pull origin main' first"
fi
if ! git cat-file -e "${head_oid}^{commit}" 2>/dev/null; then
  git fetch --quiet origin "pull/$pr/head" 2>/dev/null \
    || die "cannot fetch PR #$pr head ${head_oid:0:9} (git fetch origin pull/$pr/head)"
  git cat-file -e "${head_oid}^{commit}" 2>/dev/null \
    || die "PR #$pr head ${head_oid:0:9} is still unknown after fetching pull/$pr/head"
fi
branch_point="${merge_oid}^"

echo "$PROG: PR #$pr merge ${merge_oid:0:9} head ${head_oid:0:9} — areas:$areas"

# qualifies <marker-json> <label>: 0 when the marker's commit is the PR head or a PR-branch
# ancestor of it. Sets $mc (the marker's commit field) for the caller's report line.
mc=""
qualifies() {
  mc=$(printf '%s' "$1" | jq -r '.commit // empty' 2>/dev/null) || mc=""
  if ! [[ "$mc" =~ ^[0-9a-f]{7,40}$ ]]; then
    echo "  note: $2 has no usable commit field ('$mc') — skipped" >&2
    return 1
  fi
  local full
  if ! full=$(git rev-parse --verify --quiet "${mc}^{commit}" 2>/dev/null); then
    echo "  note: $2 marker commit $mc is not a known commit (unknown or ambiguous) — skipped" >&2
    return 1
  fi
  if ! git merge-base --is-ancestor "$full" "$head_oid"; then
    return 1
  fi
  if git merge-base --is-ancestor "$full" "$branch_point"; then
    echo "  note: $2 marker commit $mc is already on main before the PR (ancestor of ${merge_oid:0:9}^) — not a PR-branch verify, skipped" >&2
    return 1
  fi
  return 0
}

missing=""
for area in $areas; do
  path=".claude/markers/verify-$area.json"
  found=""
  # (a) committed since the merge commit (inclusive), newest first
  while read -r c; do
    [ -n "$c" ] || continue
    content=$(git show "$c:$path" 2>/dev/null) || continue   # file deleted in that commit
    if qualifies "$content" "$area committed ${c:0:9}"; then
      found="committed ${c:0:9} (marker commit $mc)"
      break
    fi
  done < <(git log --format=%H "${merge_oid}^..HEAD" -- "$path")
  # (b) staged in the index (only when the index differs from HEAD for this path)
  if [ -z "$found" ] && ! git diff --cached --quiet -- "$path"; then
    content=$(git show ":$path" 2>/dev/null) || content=""
    if qualifies "$content" "$area staged (index)"; then
      found="staged in index (marker commit $mc)"
    fi
  fi
  if [ -n "$found" ]; then
    echo "  $area: OK — $found"
  else
    echo "  $area: MISSING — no $path committed since ${merge_oid:0:9} or staged whose commit is ${head_oid:0:9} or a PR-branch ancestor of it"
    missing="$missing $area"
  fi
done

if [ -n "$missing" ]; then
  echo "$PROG: FAIL — PR #$pr has no verify marker for:$missing" >&2
  exit 1
fi
echo "$PROG: PASS — every touched area of PR #$pr has a verify marker"
exit 0
