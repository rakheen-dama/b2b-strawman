#!/usr/bin/env bash
# Pre-merge quality gate (CLAUDE.md §10).
#
# Triggered by PreToolUse on Bash when the command is `gh pr merge ...`.
# Blocks the merge unless one of the following marker files exists and is
# current (mtime within the last 24 hours, on a commit hash present in the
# branch's recent history):
#
#   .claude/markers/verify-backend.json   — `./mvnw verify` clean
#   .claude/markers/verify-frontend.json  — `pnpm lint && build && test` green
#   .claude/markers/verify-portal.json    — same for portal
#
# The marker is a JSON file written by the agent that ran the verify, with:
#   { "commit": "<sha>", "command": "<exact cmd>", "exit": 0, "ts": "<iso>", "summary": "..." }
#
# The gate looks at the PR's head branch, decides which markers are required
# (backend changes → backend marker, etc.), and refuses the merge if any
# required marker is missing, stale, or not on a parent of HEAD.
#
# This is enforcement, not advice. Do not bypass without raising the gate
# upstream. See CLAUDE.md §10.

set -euo pipefail

INPUT="${1:-}"
if [[ -z "$INPUT" ]]; then
  # Hook called without input — let it pass; the actual hook receives stdin.
  exit 0
fi

# Read the tool-call payload from stdin (Claude Code hook contract).
PAYLOAD="$(cat)"
COMMAND="$(printf '%s' "$PAYLOAD" | grep -oE '"command"\s*:\s*"[^"]*"' | head -1 | sed 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/' || true)"

# Only act on `gh pr merge` commands.
if [[ ! "$COMMAND" =~ ^[[:space:]]*gh[[:space:]]+pr[[:space:]]+merge ]]; then
  exit 0
fi

REPO_ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo .)}"
MARKERS_DIR="$REPO_ROOT/.claude/markers"
mkdir -p "$MARKERS_DIR"

# Extract PR number if present (`gh pr merge 1234 ...`).
PR_NUM="$(printf '%s' "$COMMAND" | grep -oE 'gh pr merge[[:space:]]+[0-9]+' | awk '{print $NF}' || true)"

if [[ -z "$PR_NUM" ]]; then
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"pre-pr-merge-gate: cannot extract PR number from command. Use `gh pr merge <PR_NUMBER> ...`."}}\n'
  exit 0
fi

# Get the PR's head branch and changed files.
HEAD_BRANCH="$(gh pr view "$PR_NUM" --json headRefName -q .headRefName 2>/dev/null || true)"
if [[ -z "$HEAD_BRANCH" ]]; then
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"pre-pr-merge-gate: gh pr view '"$PR_NUM"' failed. Cannot determine head branch."}}\n'
  exit 0
fi

CHANGED_FILES="$(gh pr diff "$PR_NUM" --name-only 2>/dev/null || true)"

needs_backend=false
needs_frontend=false
needs_portal=false

if printf '%s\n' "$CHANGED_FILES" | grep -qE '^backend/'; then needs_backend=true; fi
if printf '%s\n' "$CHANGED_FILES" | grep -qE '^frontend/'; then needs_frontend=true; fi
if printf '%s\n' "$CHANGED_FILES" | grep -qE '^portal/'; then needs_portal=true; fi

# qa_cycle/ + qa/testplan/ + .claude/ diff-only PRs are documentation; allow.
if [[ "$needs_backend" == false && "$needs_frontend" == false && "$needs_portal" == false ]]; then
  # Documentation / config-only PR — allow.
  exit 0
fi

# Verify each required marker.
missing=()
stale=()

now_epoch="$(date +%s)"
max_age_seconds=86400  # 24 hours

check_marker() {
  local label="$1"
  local marker="$MARKERS_DIR/verify-$label.json"

  if [[ ! -f "$marker" ]]; then
    missing+=("$label")
    return
  fi

  local marker_mtime
  marker_mtime="$(stat -f %m "$marker" 2>/dev/null || stat -c %Y "$marker" 2>/dev/null || echo 0)"
  local age=$((now_epoch - marker_mtime))
  if (( age > max_age_seconds )); then
    stale+=("$label (age=${age}s, max=${max_age_seconds}s)")
    return
  fi

  # Check the marker has exit:0.
  local exit_code
  exit_code="$(grep -oE '"exit"\s*:\s*[0-9]+' "$marker" | head -1 | grep -oE '[0-9]+' || echo "1")"
  if [[ "$exit_code" != "0" ]]; then
    stale+=("$label (exit=$exit_code, must be 0)")
    return
  fi

  # Verify the marker's commit is an ancestor of the PR head — prevents reusing
  # a stale marker from an unrelated branch (the docstring at the top promises this).
  local marker_commit
  marker_commit="$(grep -oE '"commit"[[:space:]]*:[[:space:]]*"[a-f0-9]+"' "$marker" | head -1 | grep -oE '[a-f0-9]+' | tail -1 || echo "")"
  if [[ -n "$marker_commit" && -n "$PR_HEAD_SHA" ]]; then
    # Fetch the PR head locally if we don't have it (best-effort — don't fail on network).
    git -C "$REPO_ROOT" cat-file -e "$PR_HEAD_SHA" 2>/dev/null || \
      git -C "$REPO_ROOT" fetch origin "$HEAD_BRANCH" --quiet 2>/dev/null || true
    if ! git -C "$REPO_ROOT" merge-base --is-ancestor "$marker_commit" "$PR_HEAD_SHA" 2>/dev/null; then
      stale+=("$label (marker commit $marker_commit is not an ancestor of PR head $PR_HEAD_SHA — marker is from a different branch)")
    fi
  fi
}

# Get the PR head SHA for the marker-ancestry check.
PR_HEAD_SHA="$(gh pr view "$PR_NUM" --json headRefOid -q .headRefOid 2>/dev/null || true)"

if $needs_backend; then check_marker "backend"; fi
if $needs_frontend; then check_marker "frontend"; fi
if $needs_portal;  then check_marker "portal"; fi

# --- Review audit-trail check (epic_v2 SKILL.md Step 4.5) ---
# The PR body MUST contain proof that the two superpowers reviews ran AND that
# CodeRabbit was either reviewed or explicitly deferred. Without this trail the
# 4-PR review-skip incident (PRs 1281, 1282, 1284, 1286) on 2026-05-03 cannot
# be detected post-hoc. Block merge if any marker is missing.
review_missing=()
PR_BODY="$(gh pr view "$PR_NUM" --json body --jq .body 2>/dev/null || echo "")"

# Two `## Verdict: APPROVE` lines required (one per superpowers reviewer).
# Alternative: `## Verdict: REQUEST_CHANGES` + a "Critical/High resolved" line is also valid.
verdict_count="$(printf '%s' "$PR_BODY" | grep -cE '^## Verdict: APPROVE$' || true)"
resolved_count="$(printf '%s' "$PR_BODY" | grep -cE 'Critical.*High.*(resolved|fixed|addressed)' || true)"
if (( verdict_count + resolved_count < 2 )); then
  review_missing+=("two superpowers '## Verdict: APPROVE' markers (or 'Critical/High resolved' equivalents) — found verdict_count=$verdict_count resolved_count=$resolved_count")
fi

# CodeRabbit marker — either REVIEWED or DEFERRED, written by Step 4.5 of epic_v2.
if ! printf '%s' "$PR_BODY" | grep -qE '^## CodeRabbit: (REVIEWED|DEFERRED)'; then
  review_missing+=("'## CodeRabbit: REVIEWED' or '## CodeRabbit: DEFERRED' line in PR body")
fi

if (( ${#missing[@]} > 0 || ${#stale[@]} > 0 || ${#review_missing[@]} > 0 )); then
  reason="pre-pr-merge-gate (CLAUDE.md §10): merge blocked. "
  if (( ${#missing[@]} > 0 )); then
    reason="${reason}Missing markers: ${missing[*]}. "
  fi
  if (( ${#stale[@]} > 0 )); then
    reason="${reason}Stale/failed markers: ${stale[*]}. "
  fi
  if (( ${#review_missing[@]} > 0 )); then
    reason="${reason}Missing review audit trail in PR body: ${review_missing[*]}. Run epic_v2 Step 4.5 (gh pr edit --body-file ...) to append the audit trail before merging. "
  fi
  reason="${reason}See CLAUDE.md §1-2 and .claude/skills/epic_v2/SKILL.md Step 4.5."
  # JSON-escape the reason
  esc_reason="$(printf '%s' "$reason" | sed 's/\\/\\\\/g; s/"/\\"/g')"
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$esc_reason"
  exit 0
fi

# All gates green.
exit 0
