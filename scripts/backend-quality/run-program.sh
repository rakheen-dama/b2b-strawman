#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "$SCRIPT_DIR/lib.sh"

usage() {
  cat <<USAGE
Usage:
  ./scripts/backend-quality/run-program.sh [options]

Options:
  --agent codex|claude       Agent to run (default: codex)
  --from-wave N              First wave to run (default: 1)
  --to-wave N                Last wave to run (default: 7)
  --tickets A,B,C            Run only specific tickets in the given order
  --dry-run                  Print the execution plan only
  --continue-on-failure      Keep going after a failed ticket
  --no-review-gates          Do not stop after gated waves
  --approve-wave N           Clear a previously required review gate for wave N
  --resume                   Resume from current state, skipping completed tickets

Examples:
  ./scripts/backend-quality/run-program.sh --agent codex --from-wave 1 --to-wave 2
  ./scripts/backend-quality/run-program.sh --agent claude --tickets BE-001,BE-004
  ./scripts/backend-quality/run-program.sh --approve-wave 1 --resume
USAGE
}

agent="codex"
from_wave=1
to_wave=7
tickets_csv=""
dry_run=false
continue_on_failure=false
review_gates=true
approve_wave=""
resume=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent)
      agent="$2"
      shift 2
      ;;
    --from-wave)
      from_wave="$2"
      shift 2
      ;;
    --to-wave)
      to_wave="$2"
      shift 2
      ;;
    --tickets)
      tickets_csv="$2"
      shift 2
      ;;
    --dry-run)
      dry_run=true
      shift
      ;;
    --continue-on-failure)
      continue_on_failure=true
      shift
      ;;
    --no-review-gates)
      review_gates=false
      shift
      ;;
    --approve-wave)
      approve_wave="$2"
      shift 2
      ;;
    --resume)
      resume=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "$agent" != "codex" && "$agent" != "claude" ]]; then
  echo "Unsupported agent: $agent" >&2
  exit 1
fi

bq::ensure_state_file

if [[ -n "$approve_wave" ]]; then
  current_gate_wave="$(jq -r '.program.reviewGate.wave // empty' "$BACKEND_QUALITY_STATE_FILE")"
  if [[ -n "$current_gate_wave" && "$current_gate_wave" == "$approve_wave" ]]; then
    bq::state_clear_review_gate
    echo "Approved review gate for wave $approve_wave"
  else
    echo "No matching review gate for wave $approve_wave"
  fi
fi

current_gate_wave="$(jq -r '.program.reviewGate.wave // empty' "$BACKEND_QUALITY_STATE_FILE")"
if [[ -n "$current_gate_wave" && "$resume" != true ]]; then
  echo "Review gate is active for wave $current_gate_wave. Re-run with --approve-wave $current_gate_wave --resume to continue."
  exit 2
fi

selected_tickets=()
if [[ -n "$tickets_csv" ]]; then
  IFS=',' read -r -a selected_tickets <<< "$tickets_csv"
else
  while IFS= read -r ticket; do
    [[ -n "$ticket" ]] && selected_tickets+=("$ticket")
  done < <(bq::print_ticket_order "$from_wave" "$to_wave")
fi

if [[ ${#selected_tickets[@]} -eq 0 ]]; then
  echo "No tickets selected"
  exit 1
fi

echo "Program plan"
echo "  agent: $agent"
echo "  tickets: ${selected_tickets[*]}"
echo "  review gates: $review_gates"

after_wave_gate_checked=""
current_wave=""

for ticket in "${selected_tickets[@]}"; do
  if ! bq::ticket_exists "$ticket"; then
    echo "Unknown ticket: $ticket" >&2
    exit 1
  fi

  bq::parse_ticket "$ticket"

  if [[ -n "$current_wave" && "$BQ_TICKET_WAVE" != "$current_wave" ]]; then
    if [[ "$review_gates" == true ]] && bq::review_gate_enabled_for_wave "$current_wave"; then
      bq::state_set_review_gate "$current_wave"
      echo
      echo "Review gate reached after wave $current_wave"
      echo "Review progress with ./scripts/backend-quality/status.sh"
      echo "Continue with: ./scripts/backend-quality/run-program.sh --approve-wave $current_wave --resume --agent $agent"
      exit 0
    fi
  fi

  current_wave="$BQ_TICKET_WAVE"
  if [[ "$dry_run" != true ]]; then
    bq::state_set_program_field "currentWave" "$current_wave"
  fi

  status="$(bq::state_get_ticket_status "$ticket")"
  if [[ "$resume" == true && "$status" == "completed" ]]; then
    echo "Skipping $ticket (already completed)"
    continue
  fi

  if [[ "$dry_run" == true ]]; then
    "$SCRIPT_DIR/run-ticket.sh" "$ticket" "$agent" --dry-run
    continue
  fi

  echo
  echo "=== Running $ticket ($BQ_TICKET_TITLE) ==="
  if ! "$SCRIPT_DIR/run-ticket.sh" "$ticket" "$agent"; then
    if [[ "$continue_on_failure" == true ]]; then
      echo "Continuing after failure in $ticket"
      continue
    fi
    echo "Stopping after failure in $ticket"
    exit 1
  fi

done

if [[ -n "$current_wave" && "$review_gates" == true ]] && bq::review_gate_enabled_for_wave "$current_wave" && [[ "$dry_run" != true ]]; then
  bq::state_set_review_gate "$current_wave"
  echo
  echo "Review gate reached after wave $current_wave"
  echo "Review progress with ./scripts/backend-quality/status.sh"
  echo "Continue with: ./scripts/backend-quality/run-program.sh --approve-wave $current_wave --resume --agent $agent"
  exit 0
fi

echo
echo "Program run complete"
"$SCRIPT_DIR/status.sh"
