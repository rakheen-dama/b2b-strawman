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
# In addition (2026-10-19), every PR — docs-only included — must have its GitHub
# checks green: `gh pr checks` buckets must all be `pass` or `skipping`
# (path-filtered jobs). `pending`, `fail` and `cancel` deny the merge, and so does
# an empty/unreadable check list (fail-closed). The review audit trail (two
# `## Verdict: APPROVE` markers + a `## CodeRabbit:` line in the PR body) is
# required for EVERY PR — there is no documentation-only exemption — and verify
# markers are required per touched layer. Together this is what lets
# `Bash(gh pr merge:*)` be allow-listed in .claude/settings.json without a
# human review step. The command itself must be a single plain
# `gh pr merge <N> [--squash|--merge|--rebase] [--delete-branch] [--match-head-commit <sha>]`.
#
# This is enforcement, not advice. Do not bypass without raising the gate
# upstream. See CLAUDE.md §10.

set -euo pipefail

# Claude Code pipes the tool-call payload on stdin and passes NO positional args
# (.claude/settings.json). The previous "$1" guard therefore exited before reading
# stdin on every real invocation, so the gate never enforced. Only skip when stdin
# is an interactive terminal (manual invocation with nothing piped).
if [[ -t 0 ]]; then
  exit 0
fi

# Read the tool-call payload from stdin (Claude Code hook contract).
# Emit a deny decision as valid JSON (jq escapes everything; printf fallback if jq
# is missing) and exit 0. Every refusal in this file goes through here.
deny() {
  local reason="pre-pr-merge-gate: $1"
  if command -v jq >/dev/null 2>&1; then
    jq -cn --arg r "$reason" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
  else
    local esc; esc="$(printf '%s' "$reason" | tr -d '\n\t\r' | sed 's/\\/\\\\/g; s/"/\\"/g')"
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$esc"
  fi
  exit 0
}
# Fail closed: any unexpected error (set -e) becomes a deny, never a silent allow.
trap 'deny "hook error at line $LINENO (command: $BASH_COMMAND). Merge denied fail-closed; fix the hook, do not bypass."' ERR

PAYLOAD="$(cat)"
# Extract the FULL, unescaped command with jq (the old grep/sed stopped at the first
# escaped quote, so `echo "x" && gh pr merge …` was read as `echo \` and never gated).
# Fallback without jq: the raw payload — the trigger below then matches on it and the
# strict shape (which cannot match a JSON blob) denies, i.e. fail-closed.
if command -v jq >/dev/null 2>&1; then
  COMMAND="$(printf '%s' "$PAYLOAD" | jq -r '.tool_input.command // ""' 2>/dev/null || printf '%s' "$PAYLOAD")"
else
  COMMAND="$PAYLOAD"
fi


# Act on ANY command that contains `gh pr merge` — not only one that starts with it.
# Belt and braces: test the raw payload too, so no extraction quirk can hide the merge.
TOOL_INPUT_STR="$(printf '%s' "$PAYLOAD" | jq -r '.tool_input | tostring' 2>/dev/null || printf '%s' "$PAYLOAD")"
if [[ "$TOOL_INPUT_STR" =~ gh[[:space:]]+pr[[:space:]]+merge && ! "$COMMAND" =~ gh[[:space:]]+pr[[:space:]]+merge ]]; then
  deny "tool_input mentions gh pr merge but the extracted command does not — refusing (extraction mismatch)."
fi
# `Bash(gh pr merge:*)` in .claude/settings.json allow-lists the merge for agents, and
# Claude Code matches that rule per sub-command, so `cd x && gh pr merge …`,
# `true; gh pr merge …`, env-var prefixes and multi-line commands must not slip past.
if [[ ! "$COMMAND" =~ gh[[:space:]]+pr[[:space:]]+merge ]]; then
  exit 0
fi

# Exactly one plain invocation is accepted:
#   gh pr merge <N> [--squash|--merge|--rebase] [--delete-branch|-d] [--match-head-commit <sha>]
# Anything else — compound commands, `--admin` (CLAUDE.md §10), `--repo`/`-R` (would gate
# this repo's PR N but merge another repo's), `--auto` (merges later, after this check),
# `--body`/`--subject`, quoting, a second `gh pr merge` — is denied with the accepted shape.
# Real newlines/CR/tabs (jq now un-escapes them) must not be treated as plain spaces.
if [[ "$COMMAND" == *$'\n'* || "$COMMAND" == *$'\r'* || "$COMMAND" == *$'\t'* ]]; then
  deny "multi-line or tab-containing merge commands are refused; use a single line: gh pr merge <N> [--squash|--merge|--rebase] [--delete-branch]"
fi
STRICT_SHAPE='^[[:space:]]*gh[[:space:]]+pr[[:space:]]+merge[[:space:]]+([0-9]+)([[:space:]]+(--squash|--merge|--rebase|--delete-branch|-d|--match-head-commit[[:space:]]+[0-9a-fA-F]{7,40}))*[[:space:]]*$'
if [[ ! "$COMMAND" =~ $STRICT_SHAPE ]]; then
  deny "only a single plain merge is allowed: gh pr merge <N> [--squash|--merge|--rebase] [--delete-branch] [--match-head-commit <sha>]. Compound commands, --admin, --repo/-R, --auto and free-text flags are refused (CLAUDE.md §10). Got: $COMMAND"
fi
PR_NUM="${BASH_REMATCH[1]}"
WANT_HEAD=""
if [[ "$COMMAND" =~ --match-head-commit[[:space:]]+([0-9a-fA-F]{7,40}) ]]; then
  WANT_HEAD="${BASH_REMATCH[1]}"
fi

REPO_ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo .)}"
MARKERS_DIR="$REPO_ROOT/.claude/markers"
mkdir -p "$MARKERS_DIR"

# Get the PR's head branch and changed files.
HEAD_BRANCH="$(gh pr view "$PR_NUM" --json headRefName -q .headRefName 2>/dev/null || true)"
if [[ -z "$HEAD_BRANCH" ]]; then
  deny "gh pr view $PR_NUM failed (offline, unauthenticated, or no such PR). Cannot determine head branch."
fi
PR_HEAD_SHA="$(gh pr view "$PR_NUM" --json headRefOid -q .headRefOid 2>/dev/null || true)"
# Resolved here, before the docs-only early allow, so the pin applies to every PR.
# Optional pin: if the caller passed --match-head-commit, it must be the head we gated.
# (gh itself also refuses the merge if the head moved; this makes the gate's view explicit.)
if [[ -n "$WANT_HEAD" && "$PR_HEAD_SHA" != "$WANT_HEAD"* ]]; then
  deny "--match-head-commit $WANT_HEAD does not match the PR head this gate checked ($PR_HEAD_SHA)."
fi

# Phase 86 (subtraction) precondition: the horizontal platform must be archived
# before any deletion PR lands. Scoped to phase86/* branches (CLAUDE.md §10).
# Runs before the docs-only early allow below so docs-only phase86 PRs are checked too.
# Fail-closed: if `git ls-remote` fails (e.g. no network) the pipeline fails and the merge is denied.
if [[ "$HEAD_BRANCH" == phase86/* || "$HEAD_BRANCH" == epic/86-* ]]; then
  if ! git -C "$REPO_ROOT" ls-remote --tags origin refs/tags/horizontal-platform-final | grep -q horizontal-platform-final; then
    deny "tag horizontal-platform-final is not on origin. Phase 86 deletions require the archive tag (ADR-330)."
  fi
fi

# CI condition: all GitHub checks must be pass or skipping. Runs before the
# docs-only early allow so documentation PRs are gated on CI too.
# `gh pr checks` exits non-zero when anything is pending/failing but still prints
# the JSON, hence `|| true`; an empty result (CI not started / API failure) denies.
CHECKS_JSON="$(gh pr checks "$PR_NUM" --json name,bucket 2>/dev/null || true)"
if [[ -z "$CHECKS_JSON" || "$CHECKS_JSON" == "[]" ]]; then
  deny "no CI checks reported for PR $PR_NUM yet (or gh pr checks failed). Wait for CI to start and finish, then retry."
fi
ci_not_green="$(printf '%s' "$CHECKS_JSON" | jq -r '[.[] | select(.bucket != "pass" and .bucket != "skipping")] | map("\(.name)=\(.bucket)") | join(", ")' 2>/dev/null || echo "jq-error")"
if [[ -n "$ci_not_green" ]]; then
  deny "CI is not green for PR $PR_NUM — $ci_not_green. Every check must be pass or skipping before merge (CLAUDE.md §1/§10)."
fi

CHANGED_FILES="$(gh pr diff "$PR_NUM" --name-only 2>/dev/null || true)"
# Fail-closed: an empty file list (API error, rate limit, oversized diff) must not be
# mistaken for a documentation-only PR.
if [[ -z "$CHANGED_FILES" ]]; then
  deny "gh pr diff --name-only returned nothing for PR $PR_NUM; cannot classify the change. Retry, or check gh auth / rate limits."
fi

needs_backend=false
needs_frontend=false
needs_portal=false

if printf '%s\n' "$CHANGED_FILES" | grep -qE '^backend/'; then needs_backend=true; fi
if printf '%s\n' "$CHANGED_FILES" | grep -qE '^frontend/'; then needs_frontend=true; fi
if printf '%s\n' "$CHANGED_FILES" | grep -qE '^portal/'; then needs_portal=true; fi
# Shared packages (@b2mash/ui, @b2mash/shared) are consumed by both apps.
if printf '%s\n' "$CHANGED_FILES" | grep -qE '^packages/'; then needs_frontend=true; needs_portal=true; fi

# NOTE: there is deliberately NO docs-only early allow any more. Verify markers are
# layer-dependent (below), but the review audit trail (two reviewer verdicts + CodeRabbit
# line) and the CI condition apply to EVERY PR — including .claude/, .github/, infra/,
# compose/ and qa/ changes, which is exactly where a weakened gate or CI would land.

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
  if [[ -z "$marker_commit" ]]; then
    stale+=("$label (marker has no commit field — rewrite it from the verified commit)")
    return
  fi
  if [[ -n "$marker_commit" && -n "$PR_HEAD_SHA" ]]; then
    # Fetch the PR head locally if we don't have it (best-effort — don't fail on network).
    git -C "$REPO_ROOT" cat-file -e "$PR_HEAD_SHA" 2>/dev/null || \
      git -C "$REPO_ROOT" fetch origin "$HEAD_BRANCH" --quiet 2>/dev/null || true
    if ! git -C "$REPO_ROOT" merge-base --is-ancestor "$marker_commit" "$PR_HEAD_SHA" 2>/dev/null; then
      stale+=("$label (marker commit $marker_commit is not an ancestor of PR head $PR_HEAD_SHA — marker is from a different branch)")
    fi
  fi
}

# (PR_HEAD_SHA was resolved above, before the docs-only allow.)

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
  deny "${reason#pre-pr-merge-gate: }"
fi

# All gates green.
exit 0
