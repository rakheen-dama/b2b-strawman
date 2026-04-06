#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TICKETS_FILE="$SCRIPT_DIR/tickets.tsv"

usage() {
  cat <<USAGE
Usage:
  ./scripts/backend-quality/print-prompt.sh BE-001 [codex|claude]
USAGE
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage
  exit 1
fi

ticket="$1"
agent="${2:-codex}"
row="$(awk -F '\t' -v t="$ticket" '$1 == t { print $0 }' "$TICKETS_FILE")"

if [[ -z "$row" ]]; then
  echo "Unknown ticket: $ticket" >&2
  exit 1
fi

IFS=$'\t' read -r id wave branch title deps scope acceptance <<< "$row"

cat <<PROMPT
Implement ticket $id in this worktree only.

Title:
$title

Context:
- Repository root: $PROJECT_ROOT
- Backend module: $PROJECT_ROOT/backend
- Ticket wave: $wave
- Dependencies: ${deps:-none}

Goal:
$title

Scope:
- $scope

Acceptance criteria:
- $acceptance

Working rules:
- Read repo instructions first, especially backend conventions.
- Inspect only files relevant to this ticket before editing.
- Keep changes focused and reviewable.
- Do not refactor unrelated areas.
- If you find adjacent issues, note them separately instead of fixing them.
- Run the narrowest relevant backend tests or checks for this ticket.
- Summarize changed files, validation performed, and any residual risks at the end.
PROMPT

if [[ "$agent" == "claude" ]]; then
  cat <<'CLAUDE'

Claude-specific note:
- Do not broaden scope beyond this ticket.
- Prefer a short plan before implementation.
CLAUDE
fi
