#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<USAGE
Usage:
  ./scripts/backend-quality/create-worktrees.sh BE-001 [BE-004 ...]
  WORKTREE_ROOT=/custom/path ./scripts/backend-quality/create-worktrees.sh BE-001

Creates one git worktree per ticket using the branch slug defined in tickets.tsv.
USAGE
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

bq::ensure_dirs

for ticket in "$@"; do
  if ! bq::ticket_exists "$ticket"; then
    echo "Unknown ticket: $ticket" >&2
    exit 1
  fi

  bq::parse_ticket "$ticket"
  worktree_path="$(bq::ticket_worktree "$ticket")"

  if [[ -e "$worktree_path" ]]; then
    echo "Skipping $ticket: worktree path already exists at $worktree_path"
    continue
  fi

  if git -C "$BACKEND_QUALITY_PROJECT_ROOT" show-ref --verify --quiet "refs/heads/$BQ_TICKET_BRANCH"; then
    echo "Creating worktree for $ticket -> existing branch $BQ_TICKET_BRANCH"
    git -C "$BACKEND_QUALITY_PROJECT_ROOT" worktree add "$worktree_path" "$BQ_TICKET_BRANCH"
  else
    echo "Creating worktree for $ticket -> new branch $BQ_TICKET_BRANCH"
    git -C "$BACKEND_QUALITY_PROJECT_ROOT" worktree add "$worktree_path" -b "$BQ_TICKET_BRANCH"
  fi
done
