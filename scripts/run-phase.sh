#!/usr/bin/env bash
#
# run-phase.sh — Hands-off phase orchestration for DocTeams
#
# Groups slices by epic and invokes `claude -p "/epic_v2 {EPIC} auto-merge"` for each
# epic. Each epic gets a fresh context window and produces a single PR for all its slices.
# Progress is tracked by the **Done** markers that /epic_v2 writes to the task file.
#
# Usage:
#   ./scripts/run-phase.sh <phase-number> [starting-epic] [--dry-run] [--no-caffeinate]
#
# Examples:
#   ./scripts/run-phase.sh 10              # Run all remaining epics
#   ./scripts/run-phase.sh 10 84           # Start from epic 84
#   ./scripts/run-phase.sh 10 84A          # Start from epic containing slice 84A
#   ./scripts/run-phase.sh 10 --dry-run    # Preview epic order, no execution
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

PHASE="${1:?Usage: ./scripts/run-phase.sh <phase-number> [starting-epic] [--dry-run]}"
START_FROM=""
DRY_RUN=false

for arg in "${@:2}"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --no-caffeinate) ;; # handled above before arg parsing
    *) START_FROM="$arg" ;;
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

# ─── Parse slice list and group by epic ───────────────────────────────────────
#
# Slices appear as rows like:  | **84A** | 84.1-84.10 | description... | status |
# We extract the slice ID, group by epic number, and determine epic-level status.
# An epic is DONE only if ALL its slices are Done.

extract_slices() {
  local task_file="$1"
  grep -E '^\| \*\*[0-9]+[A-Z]+\*\*' "$task_file" | while IFS= read -r line; do
    slice=$(echo "$line" | sed -E 's/^\| \*\*([0-9]+[A-Z]+)\*\*.*/\1/')
    if echo "$line" | grep -q '\*\*Done\*\*'; then
      echo "${slice}:DONE"
    else
      echo "${slice}:PENDING"
    fi
  done
}

# Group slices into epics. Output: "epic_number:DONE|PENDING:slice1,slice2,..."
group_by_epic() {
  declare -A epic_slices  # epic_number -> "slice1,slice2"
  declare -A epic_status  # epic_number -> DONE|PENDING
  declare -a epic_order   # preserve insertion order

  while IFS=: read -r slice status; do
    # Strip letter suffix to get epic number: 450A -> 450
    epic=$(echo "$slice" | sed -E 's/[A-Z]+$//')

    if [[ -z "${epic_slices[$epic]+x}" ]]; then
      epic_order+=("$epic")
      epic_slices[$epic]="$slice"
      epic_status[$epic]="$status"
    else
      epic_slices[$epic]="${epic_slices[$epic]},$slice"
      # Epic is PENDING if ANY slice is PENDING
      if [[ "$status" == "PENDING" ]]; then
        epic_status[$epic]="PENDING"
      fi
    fi
  done

  for epic in "${epic_order[@]}"; do
    echo "${epic}:${epic_status[$epic]}:${epic_slices[$epic]}"
  done
}

# ─── Build execution list ─────────────────────────────────────────────────────

mapfile -t ALL_SLICES < <(extract_slices "$TASK_FILE")

if [[ ${#ALL_SLICES[@]} -eq 0 ]]; then
  echo "ERROR: No slices found in $TASK_FILE"
  echo "Expected Implementation Order tables with rows like: | **81A** | ..."
  exit 1
fi

mapfile -t ALL_EPICS < <(printf '%s\n' "${ALL_SLICES[@]}" | group_by_epic)

# Resolve START_FROM: accept epic number (450) or slice ID (450A) → epic number
START_EPIC=""
if [[ -n "$START_FROM" ]]; then
  # Strip letter suffix if present: 450A -> 450
  START_EPIC=$(echo "$START_FROM" | sed -E 's/[A-Z]+$//')
fi

# Filter to pending epics, respecting start_epic
EPICS_TO_RUN=()
PAST_START=true
if [[ -n "$START_EPIC" ]]; then
  PAST_START=false
fi

for entry in "${ALL_EPICS[@]}"; do
  epic="${entry%%:*}"
  rest="${entry#*:}"
  status="${rest%%:*}"
  slices="${rest#*:}"

  if [[ "$PAST_START" == false ]]; then
    if [[ "$epic" == "$START_EPIC" ]]; then
      PAST_START=true
    else
      continue
    fi
  fi

  # Skip fully Done epics
  if [[ "$status" == "DONE" ]]; then
    continue
  fi

  EPICS_TO_RUN+=("$entry")
done

if [[ "$PAST_START" == false ]]; then
  echo "ERROR: Starting epic '$START_FROM' (resolved to epic $START_EPIC) not found in $TASK_FILE"
  exit 1
fi

# ─── Report plan ──────────────────────────────────────────────────────────────

TOTAL_EPICS=${#ALL_EPICS[@]}
DONE_EPIC_COUNT=0
for entry in "${ALL_EPICS[@]}"; do
  rest="${entry#*:}"
  status="${rest%%:*}"
  [[ "$status" == "DONE" ]] && ((DONE_EPIC_COUNT++)) || true
done

log "Phase ${PHASE} — ${TASK_FILE}"
log "Total epics: ${TOTAL_EPICS}, Done: ${DONE_EPIC_COUNT}, Remaining: ${#EPICS_TO_RUN[@]}"

if [[ ${#EPICS_TO_RUN[@]} -eq 0 ]]; then
  log "All epics are Done! Nothing to run."
  exit 0
fi

# Build display list
EPIC_NAMES=()
for entry in "${EPICS_TO_RUN[@]}"; do
  epic="${entry%%:*}"
  rest="${entry#*:}"
  slices="${rest#*:}"
  EPIC_NAMES+=("${epic} (${slices})")
done
log "Execution order: ${EPIC_NAMES[*]}"

if [[ "$DRY_RUN" == true ]]; then
  echo ""
  echo "=== DRY RUN ==="
  echo "Would execute ${#EPICS_TO_RUN[@]} epics:"
  for i in "${!EPICS_TO_RUN[@]}"; do
    entry="${EPICS_TO_RUN[$i]}"
    epic="${entry%%:*}"
    rest="${entry#*:}"
    slices="${rest#*:}"
    echo "  $((i + 1)). Epic ${epic} — slices: ${slices}"
  done
  echo ""
  echo "Command per epic:"
  echo "  claude -p \"/epic_v2 {EPIC} auto-merge\" --dangerously-skip-permissions --model opus"
  exit 0
fi

# ─── Execute epics ────────────────────────────────────────────────────────────

log "Starting phase ${PHASE} execution..."
echo ""

for i in "${!EPICS_TO_RUN[@]}"; do
  entry="${EPICS_TO_RUN[$i]}"
  epic="${entry%%:*}"
  rest="${entry#*:}"
  slices="${rest#*:}"
  step="$((i + 1))/${#EPICS_TO_RUN[@]}"

  log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  log "Starting Epic ${epic} — slices: ${slices} (${step})"
  log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Sync main before each epic so the worktree branches from latest main.
  log "Syncing main before starting epic..."
  git fetch origin main >> "$LOG_FILE" 2>&1
  git checkout main >> "$LOG_FILE" 2>&1
  git pull --ff-only >> "$LOG_FILE" 2>&1

  # Run claude with fresh context (timeout: 200 min = 12000 sec)
  EPIC_START=$(date +%s)
  EPIC_TIMEOUT=12000

  # Unset CLAUDECODE to avoid nested session detection
  if timeout "${EPIC_TIMEOUT}" env CLAUDECODE="" claude -p "/epic_v2 ${epic} auto-merge" \
    --dangerously-skip-permissions \
    --model opus \
    >> "$LOG_FILE" 2>&1; then
    CLAUDE_EXIT=0
  else
    CLAUDE_EXIT=$?
  fi

  EPIC_END=$(date +%s)
  EPIC_DURATION=$(( EPIC_END - EPIC_START ))
  EPIC_MINUTES=$(( EPIC_DURATION / 60 ))

  # Check if ALL slices in this epic were marked Done
  # Re-read the task file since epic_v2 may have updated it
  EPIC_DONE=true
  IFS=',' read -ra SLICE_LIST <<< "$slices"
  for s in "${SLICE_LIST[@]}"; do
    if ! grep "$s" "$TASK_FILE" | grep -q '\*\*Done\*\*'; then
      EPIC_DONE=false
      break
    fi
  done

  if [[ "$EPIC_DONE" == true ]]; then
    log "Epic ${epic} completed successfully (${EPIC_MINUTES}m)"
  else
    if [[ "$CLAUDE_EXIT" -eq 124 ]]; then
      log "ERROR: Epic ${epic} TIMED OUT after ${EPIC_MINUTES}m (limit: $((EPIC_TIMEOUT / 60))m)"
    else
      log "ERROR: Epic ${epic} did NOT complete (exit code: ${CLAUDE_EXIT}, duration: ${EPIC_MINUTES}m)"
    fi
    log "Not all slices were marked Done in ${TASK_FILE}."
    log ""
    log "To investigate:"
    log "  tail -100 ${LOG_FILE}"
    log ""
    log "To resume after fixing:"
    log "  ./scripts/run-phase.sh ${PHASE} ${epic}"
    if command -v osascript &>/dev/null; then
      osascript -e 'display notification "Epic '"$epic"' failed after '"$EPIC_MINUTES"'m" with title "Phase '"$PHASE"' FAILED" sound name "Basso"'
    fi
    exit 1
  fi

  echo ""
done

# ─── Summary ──────────────────────────────────────────────────────────────────

log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "Phase ${PHASE} COMPLETE — all ${#EPICS_TO_RUN[@]} epics done!"
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if command -v osascript &>/dev/null; then
  osascript -e 'display notification "All '"${#EPICS_TO_RUN[@]}"' epics done" with title "Phase '"$PHASE"' Complete" sound name "Glass"'
fi
