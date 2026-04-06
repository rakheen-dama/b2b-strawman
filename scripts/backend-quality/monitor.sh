#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib.sh
source "$SCRIPT_DIR/lib.sh"

INTERVAL="${INTERVAL:-2}"
TAIL_LINES="${TAIL_LINES:-20}"
ONCE=false

usage() {
  cat <<USAGE
Usage:
  ./scripts/backend-quality/monitor.sh [--once] [--interval N] [--tail-lines N]

Environment:
  INTERVAL     Refresh interval in seconds (default: 2)
  TAIL_LINES   Number of log lines to show per running ticket (default: 20)
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --once)
      ONCE=true
      shift
      ;;
    --interval)
      INTERVAL="$2"
      shift 2
      ;;
    --tail-lines)
      TAIL_LINES="$2"
      shift 2
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

bq::ensure_state_file

render() {
  local state_file="$BACKEND_QUALITY_STATE_FILE"

  clear
  echo "Backend Quality Monitor"
  echo "Time: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "State: $state_file"
  echo

  local current_wave review_gate
  current_wave="$(jq -r '.program.currentWave // "-"' "$state_file")"
  review_gate="$(jq -r '.program.reviewGate.wave // "-"' "$state_file")"

  echo "Program"
  echo "  Current wave: $current_wave"
  echo "  Review gate:  $review_gate"
  echo

  echo "Tickets"
  "$SCRIPT_DIR/status.sh" || true
  echo

  local running_count
  running_count="$(jq '[.tickets | to_entries[] | select(.value.status == "running")] | length' "$state_file")"

  if [[ "$running_count" == "0" ]]; then
    echo "No running tickets."
    return 0
  fi

  while IFS=$'\t' read -r ticket log_file summary_file note; do
    [[ -z "$ticket" ]] && continue
    echo
    echo "=== $ticket ==="
    echo "Log: $log_file"
    [[ -n "$note" && "$note" != "null" ]] && echo "Note: $note"

    if [[ -f "$log_file" ]]; then
      echo "--- last $TAIL_LINES log lines ---"
      tail -n "$TAIL_LINES" "$log_file" || true
    else
      echo "Log file not created yet."
    fi

    if [[ -f "$summary_file" && -s "$summary_file" ]]; then
      echo "--- latest summary ---"
      tail -n 10 "$summary_file" || true
    fi
  done < <(
    jq -r '
      .tickets
      | to_entries[]
      | select(.value.status == "running")
      | [
          .key,
          (.value.logFile // ""),
          (.value.summaryFile // ""),
          (.value.note // "")
        ]
      | @tsv
    ' "$state_file"
  )
}

while true; do
  render
  [[ "$ONCE" == true ]] && exit 0
  sleep "$INTERVAL"
done
