#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<USAGE
Usage:
  ./scripts/backend-quality/run-ticket.sh BE-001 [codex|claude] [--dry-run]

Runs a single ticket in its dedicated worktree using Codex or Claude.
USAGE
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

ticket="$1"
agent="${2:-codex}"
dry_run=false
if [[ "${3:-}" == "--dry-run" || "${2:-}" == "--dry-run" ]]; then
  dry_run=true
  if [[ "$agent" == "--dry-run" ]]; then
    agent="codex"
  fi
fi

if ! bq::ticket_exists "$ticket"; then
  echo "Unknown ticket: $ticket" >&2
  exit 1
fi

if [[ "$agent" != "codex" && "$agent" != "claude" ]]; then
  echo "Unsupported agent: $agent" >&2
  exit 1
fi

bq::ensure_state_file
bq::parse_ticket "$ticket"

worktree="$(bq::ticket_worktree "$ticket")"
log_file="$(bq::ticket_log_file "$ticket")"
prompt_file="$(bq::ticket_prompt_file "$ticket")"
summary_file="$(bq::ticket_summary_file "$ticket")"
events_file="$(bq::ticket_events_file "$ticket")"

if [[ "$dry_run" == true ]]; then
  echo "[$ticket] dry run"
  echo "  agent: $agent"
  echo "  worktree: $worktree"
  echo "  prompt: $prompt_file"
  echo "  deps: ${BQ_TICKET_DEPS}"
  exit 0
fi

"$SCRIPT_DIR/create-worktrees.sh" "$ticket" >/dev/null
"$SCRIPT_DIR/print-prompt.sh" "$ticket" "$agent" > "$prompt_file"

if ! bq::deps_satisfied "$ticket"; then
  note="Blocked by dependencies: $(bq::missing_deps "$ticket" | tr '\n' ',' | sed 's/,$//')"
  bq::state_set_ticket "$ticket" "blocked" "$agent" "$worktree" "$log_file" "$summary_file" 0 "$note"
  echo "$note"
  exit 2
fi

: > "$log_file"
: > "$summary_file"
: > "$events_file"

bq::state_set_ticket "$ticket" "running" "$agent" "$worktree" "$log_file" "$summary_file" 0 "In progress"

set +e
if [[ "$agent" == "codex" ]]; then
  codex exec \
    --full-auto \
    --include-plan-tool \
    --cd "$worktree" \
    --output-last-message "$summary_file" \
    --json \
    - < "$prompt_file" > "$events_file" 2> "$log_file"
  exit_code=$?
  if [[ -s "$events_file" ]]; then
    cat "$events_file" >> "$log_file"
  fi
else
  claude -p "$(cat "$prompt_file")" \
    --permission-mode bypassPermissions \
    --output-format text \
    > "$summary_file" 2> "$log_file"
  exit_code=$?
  cat "$summary_file" >> "$log_file"
fi
set -e

if [[ $exit_code -eq 0 ]]; then
  bq::state_set_ticket "$ticket" "completed" "$agent" "$worktree" "$log_file" "$summary_file" "$exit_code" "Completed successfully"
  echo "[$ticket] completed"
else
  bq::state_set_ticket "$ticket" "failed" "$agent" "$worktree" "$log_file" "$summary_file" "$exit_code" "Agent exited with non-zero status"
  echo "[$ticket] failed with exit code $exit_code" >&2
  exit "$exit_code"
fi
