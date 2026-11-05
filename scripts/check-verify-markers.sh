#!/usr/bin/env bash
#
# check-verify-markers.sh — prove a merged PR's verify markers were committed (G88-F35)
#
# The merge-gate hook reads .claude/markers/verify-<area>.json from the working tree at
# merge time; it cannot tell whether the marker was ever committed. This script can: for
# every area the PR touched it requires a verify-<area>.json whose "exit" field is the JSON
# number 0 (as the hook requires) and whose "commit" field is a commit FROM THE PR BRANCH — the merged PR head or an ancestor of it that is not already
# an ancestor of main at the branch point (<mergeCommit>^) — and that marker must be
#   (a) committed: in a commit of <mergeCommit>^..HEAD touching the marker file
#       (inclusive of the squash-merge commit itself), or
#   (b) staged: in the current index, when the index differs from HEAD for that path.
#       Skipped entirely under --committed-only.
# Unstaged working-tree edits are never read.
#
# Modes:
#   default           committed OR staged — /epic_v2 Step 5 runs this after `git add` and
#                     before the bookkeeping commit.
#   --committed-only  committed only; the index is ignored. Use for post-hoc audits (e.g.
#                     Phase 89 exit gate 636.5) so nothing in the auditor's index can pass.
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
#   scripts/check-verify-markers.sh [--committed-only] <pr-number>
#   (--committed-only may also follow the PR number)
#
# Examples:
#   bash scripts/check-verify-markers.sh 1737                    # after `git add .claude/markers/...`
#   bash scripts/check-verify-markers.sh --committed-only 1737   # post-hoc audit
#
# Exit codes:
#   0  every touched area has a qualifying marker (or the PR is docs/tooling-only)
#   1  at least one area has no qualifying marker (all missing areas are named)
#   2  usage / environment / PR-state error (bad arg or option, unknown or unmerged PR, gh/jq/git
#      missing, not in a git checkout, merge commit not in HEAD, PR head not fetchable)
#
# Requires: git, gh (authenticated), jq. Portable to macOS /bin/bash 3.2.
#
set -euo pipefail

PROG=check-verify-markers

# usage: print the CLI synopsis and exit-code contract to stderr.
usage() {
  echo "Usage: scripts/check-verify-markers.sh [--committed-only] <pr-number>" >&2
  echo "  --committed-only  ignore the index; only committed markers count (post-hoc audits)" >&2
  echo "  exit 0 = every touched area (backend/frontend/portal) has a committed (or, without" >&2
  echo "           --committed-only, staged) verify marker with exit 0 from the PR branch," >&2
  echo "           or the PR is docs/tooling-only" >&2
  echo "  exit 1 = at least one area has no qualifying marker" >&2
  echo "  exit 2 = usage / environment / PR-state error" >&2
}

# die <msg>: report an environment / PR-state error and exit 2.
die() {
  echo "$PROG: $*" >&2
  exit 2
}

committed_only=0
pr=""
pr_seen=0
for arg in "$@"; do
  case "$arg" in
    -h|--help) usage; exit 0 ;;
    --committed-only) committed_only=1 ;;
    -*)
      echo "$PROG: unknown option '$arg'" >&2
      usage
      exit 2
      ;;
    *)
      if [ "$pr_seen" -eq 1 ]; then
        echo "$PROG: more than one PR number ('$pr', '$arg')" >&2
        usage
        exit 2
      fi
      pr=$arg
      pr_seen=1
      ;;
  esac
done
if [ "$pr_seen" -ne 1 ]; then
  usage
  exit 2
fi
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
# Here-strings, not `printf | grep -q`: under pipefail grep -q's early exit SIGPIPEs printf
# on a long list (status 141), which `if` would read as "no match" and drop the area.
areas=""
if grep -qE '^backend/' <<<"$files"; then areas="$areas backend"; fi
if grep -qE '^(frontend|packages)/' <<<"$files"; then areas="$areas frontend"; fi
if grep -qE '^(portal|packages)/' <<<"$files"; then areas="$areas portal"; fi
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
  git fetch --quiet origin "pull/$pr/head" \
    || die "cannot fetch PR #$pr head ${head_oid:0:9} (git fetch origin pull/$pr/head)"
  git cat-file -e "${head_oid}^{commit}" 2>/dev/null \
    || die "PR #$pr head ${head_oid:0:9} is still unknown after fetching pull/$pr/head"
fi
branch_point="${merge_oid}^"

if [ "$committed_only" -eq 1 ]; then
  mode="committed-only"
else
  mode="committed or staged"
fi
echo "$PROG: PR #$pr merge ${merge_oid:0:9} head ${head_oid:0:9} — areas:$areas — mode: $mode"

# qualifies <marker-json> <label>: 0 when the marker's "exit" is the JSON number 0 and its
# commit is the PR head or a PR-branch ancestor of it. Sets $mc (the marker's commit field)
# for the caller's report line.
mc=""
qualifies() {
  # "exit" must be the JSON integer 0 — never weaker than the hook (pre-pr-merge-gate.sh,
  # regex "exit"\s*:\s*[0-9]+), which only accepts a bare unsigned number: the string "0",
  # -0 and non-zero values are all rejected (tojson == "0" pins the plain integer form).
  local ex
  ex=$(printf '%s' "$1" | jq -r '.exit
      | if . == null then "missing"
        elif type != "number" then "non-numeric \(tojson)"
        elif . == 0 and tojson == "0" then "0"
        else tojson end' 2>/dev/null) || ex=""
  if [ "$ex" != "0" ]; then
    echo "  note: $2 marker exit is '${ex:-unreadable}', must be the number 0 — skipped" >&2
    return 1
  fi
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
  # --is-ancestor: 0 = ancestor, 1 = not, anything else (128) = git error — never qualifies.
  local rc=0
  git merge-base --is-ancestor "$full" "$head_oid" || rc=$?
  if [ "$rc" -eq 1 ]; then
    return 1
  elif [ "$rc" -ne 0 ]; then
    echo "  note: $2 git merge-base --is-ancestor $mc ${head_oid:0:9} failed (exit $rc) — skipped" >&2
    return 1
  fi
  rc=0
  git merge-base --is-ancestor "$full" "$branch_point" || rc=$?
  if [ "$rc" -eq 0 ]; then
    echo "  note: $2 marker commit $mc is already on main before the PR (ancestor of ${merge_oid:0:9}^) — not a PR-branch verify, skipped" >&2
    return 1
  elif [ "$rc" -ne 1 ]; then
    echo "  note: $2 git merge-base --is-ancestor $mc ${merge_oid:0:9}^ failed (exit $rc) — skipped" >&2
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
  # (b) staged in the index (only when the index differs from HEAD for this path;
  #     never under --committed-only)
  if [ -z "$found" ] && [ "$committed_only" -eq 0 ] && ! git diff --cached --quiet -- "$path"; then
    content=$(git show ":$path" 2>/dev/null) || content=""
    if qualifies "$content" "$area staged (index)"; then
      found="staged in index (marker commit $mc)"
    fi
  fi
  if [ -n "$found" ]; then
    echo "  $area: OK — $found"
  elif [ "$committed_only" -eq 1 ]; then
    echo "  $area: MISSING — no $path committed since ${merge_oid:0:9} with exit 0 and commit ${head_oid:0:9} or a PR-branch ancestor of it (index ignored: --committed-only)"
    missing="$missing $area"
  else
    echo "  $area: MISSING — no $path committed since ${merge_oid:0:9} or staged with exit 0 and commit ${head_oid:0:9} or a PR-branch ancestor of it"
    missing="$missing $area"
  fi
done

if [ -n "$missing" ]; then
  echo "$PROG: FAIL — PR #$pr has no verify marker for:$missing" >&2
  exit 1
fi
echo "$PROG: PASS — every touched area of PR #$pr has a verify marker"
exit 0
