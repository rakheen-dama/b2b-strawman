#!/usr/bin/env bash
#
# run-phase.sh — Hands-off phase orchestration for DocTeams
#
# Loops through epic slices in a phase, invoking `claude -p "/epic_v2 {SLICE} auto-merge"`
# for each one. Each slice gets a fresh context window. Progress is tracked by the
# **Done** markers that /epic_v2 writes to the task file after merging.
#
# Usage:
#   ./scripts/run-phase.sh <phase-number> [starting-slice] [--dry-run] [--no-caffeinate]
#
# Examples:
#   ./scripts/run-phase.sh 10              # Run all remaining slices
#   ./scripts/run-phase.sh 10 84A          # Start from slice 84A
#   ./scripts/run-phase.sh 10 --dry-run    # Preview slice order, no execution
#   ./scripts/run-phase.sh 10 --no-caffeinate  # Skip sleep prevention
#
# By default, uses `caffeinate -i` on macOS to prevent idle sleep during execution.
# This ensures long-running phases aren't suspended when the laptop lid closes or
# the machine goes idle. Use --no-caffeinate to disable this behavior.
#
set -euo pipefail

# ─── Sleep prevention (macOS) ────────────────────────────────────────────────
#
# Re-exec under caffeinate if available and not already caffeinated.
# caffeinate -i prevents idle sleep; -s prevents sleep on AC power (lid close).
# The process tree inherits the assertion, so all child claude processes are covered.

if [[ -z "${CAFFEINATED:-}" && "$*" != *"--no-caffeinate"* ]] && command -v caffeinate &>/dev/null; then
  export CAFFEINATED=1
  exec caffeinate -is "$0" "$@"
fi

# ─── Arguments ────────────────────────────────────────────────────────────────

PHASE="${1:?Usage: ./scripts/run-phase.sh <phase-number> [starting-slice] d]}"
START_SLICE=""
DRY_RUN=false

for arg in "${@:2}"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --no-caffeinate) ;; # handled above before arg parsing
    *) START_SLICE="$arg" ;;
  esac
done

# ─── Locate project root and task file ────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Find the task file — if multiple match, pick the one with Implementation Order tables
TASK_FILES=($(find tasks -maxdepth 1 -name "phase${PHASE}-*.md" -type f 2>/dev/null))

if [[ ${#TASK_FILES[@]} -eq 0 ]]; then
  echo "ERROR: No task file found for phase ${PHASE} in tasks/"
  echo "Expected: tasks/phase${PHASE}-*.md"
  exit 1
elif [[ ${#TASK_FILES[@]} -eq 1 ]]; then
  TASK_FILE="${TASK_FILES[0]}"
else
  # Multiple files — pick the one with slice rows (Implementation Order tables)
  TASK_FILE=""
  for f in "${TASK_FILES[@]}"; do
    if grep -qE '^\| \*\*[0-9]+[A-Z]+\*\*' "$f" 2>/dev/null; then
      TASK_FILE="$f"
      break
    fi
  done
  if [[ -z "$TASK_FILE" ]]; then
    echo "ERROR: Multiple task files found but none contain Implementation Order tables:"
    printf "  %s\n" "${TASK_FILES[@]}"
    exit 1
  fi
fi

LOG_FILE="tasks/.phase-${PHASE}-progress.log"

log() {
  local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
  echo "$msg"
  if [[ "$DRY_RUN" == false ]]; then
    echo "$msg" >> "$LOG_FILE"
  fi
}

# ─── Parse slice list from Implementation Order tables ────────────────────────
#
# Slices appear as rows like:  | **84A** | 84.1-84.10 | description... | status |
# We extract the slice ID and check the status column for **Done**.

extract_slices() {
  local task_file="$1"
  # Match rows starting with | **{digits}{letter(s)}** |
  grep -E '^\| \*\*[0-9]+[A-Z]+\*\*' "$task_file" | while IFS= read -r line; do
    # Extract slice ID: everything between first ** and next **
    slice=$(echo "$line" | sed -E 's/^\| \*\*([0-9]+[A-Z]+)\*\*.*/\1/')
    # Check if this slice's row contains **Done**
    if echo "$line" | grep -q '\*\*Done\*\*'; then
      echo "${slice}:DONE"
    else
      echo "${slice}:PENDING"
    fi
  done
}

# ─── Build execution list ─────────────────────────────────────────────────────

mapfile -t ALL_SLICES < <(extract_slices "$TASK_FILE")

if [[ ${#ALL_SLICES[@]} -eq 0 ]]; then
  echo "ERROR: No slices found in $TASK_FILE"
  echo "Expected Implementation Order tables with rows like: | **81A** | ..."
  exit 1
fi

# Filter to pending slices, respecting start_slice
SLICES_TO_RUN=()
PAST_START=true
if [[ -n "$START_SLICE" ]]; then
  PAST_START=false
fi

for entry in "${ALL_SLICES[@]}"; do
  slice="${entry%%:*}"
  status="${entry##*:}"

  # If starting from a specific slice, skip everything before it
  if [[ "$PAST_START" == false ]]; then
    if [[ "$slice" == "$START_SLICE" ]]; then
      PAST_START=true
    else
      continue
    fi
  fi

  # Skip Done slices
  if [[ "$status" == "DONE" ]]; then
    continue
  fi

  SLICES_TO_RUN+=("$slice")
done

if [[ "$PAST_START" == false ]]; then
  echo "ERROR: Starting slice '$START_SLICE' not found in $TASK_FILE"
  exit 1
fi

# ─── Report plan ──────────────────────────────────────────────────────────────

TOTAL=${#ALL_SLICES[@]}
DONE_COUNT=0
for entry in "${ALL_SLICES[@]}"; do
  [[ "${entry##*:}" == "DONE" ]] && ((DONE_COUNT++)) || true
done

log "Phase ${PHASE} — ${TASK_FILE}"
log "Total slices: ${TOTAL}, Done: ${DONE_COUNT}, Remaining: ${#SLICES_TO_RUN[@]}"

if [[ ${#SLICES_TO_RUN[@]} -eq 0 ]]; then
  log "All slices are Done! Nothing to run."
  exit 0
fi

log "Execution order: ${SLICES_TO_RUN[*]}"

if [[ "$DRY_RUN" == true ]]; then
  echo ""
  echo "=== DRY RUN ==="
  echo "Would execute ${#SLICES_TO_RUN[@]} slices:"
  for i in "${!SLICES_TO_RUN[@]}"; do
    echo "  $((i + 1)). ${SLICES_TO_RUN[$i]}"
  done
  echo ""
  echo "Command per slice:"
  echo "  claude -p \"/epic_v2 {SLICE} auto-merge\" --dangerously-skip-permissions --model opus"
  exit 0
fi

# ─── Execute slices ───────────────────────────────────────────────────────────

log "Starting phase ${PHASE} execution..."
echo ""

for i in "${!SLICES_TO_RUN[@]}"; do
  slice="${SLICES_TO_RUN[$i]}"
  step="$((i + 1))/${#SLICES_TO_RUN[@]}"

  log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  log "Starting slice ${slice} (${step})"
  log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Run claude with fresh context
  SLICE_START=$(date +%s)

  # Unset CLAUDECODE to avoid nested session detection
  if CLAUDECODE="" claude -p "/epic_v2 ${slice} auto-merge" \
    --dangerously-skip-permissions \
    --model opus \
    >> "$LOG_FILE" 2>&1; then
    CLAUDE_EXIT=0
  else
    CLAUDE_EXIT=$?
  fi

  SLICE_END=$(date +%s)
  SLICE_DURATION=$(( SLICE_END - SLICE_START ))
  SLICE_MINUTES=$(( SLICE_DURATION / 60 ))

  # Check if slice was actually marked Done in the task file
  SLICE_DONE=false
  if grep -E "^\| \*\*${slice}\*\*" "$TASK_FILE" | grep -q '\*\*Done\*\*'; then
    SLICE_DONE=true
  fi

  if [[ "$SLICE_DONE" == true ]]; then
    log "Slice ${slice} completed successfully (${SLICE_MINUTES}m)"
  else
    log "ERROR: Slice ${slice} did NOT complete (exit code: ${CLAUDE_EXIT}, duration: ${SLICE_MINUTES}m)"
    log "The slice was not marked Done in ${TASK_FILE}."
    log ""
    log "To investigate:"
    log "  tail -100 ${LOG_FILE}"
    log ""
    log "To resume after fixing:"
    log "  ./scripts/run-phase.sh ${PHASE} ${slice}"
    if command -v osascript &>/dev/null; then
      osascript -e 'display notification "Slice '"$slice"' failed after '"$SLICE_MINUTES"'m" with title "Phase '"$PHASE"' FAILED" sound name "Basso"'
    fi
    exit 1
  fi

  echo ""
done

# ─── Summary ──────────────────────────────────────────────────────────────────

log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "Phase ${PHASE} COMPLETE — all ${#SLICES_TO_RUN[@]} slices done!"
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if command -v osascript &>/dev/null; then
  osascript -e 'display notification "All '"${#SLICES_TO_RUN[@]}"' slices done" with title "Phase '"$PHASE"' Complete" sound name "Glass"'
fi
