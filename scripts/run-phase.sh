#!/usr/bin/env bash
#
# run-phase.sh — Hands-off phase orchestration for DocTeams
#
# Iterates the phase's slice list in task-file order and invokes
# `claude -p "/epic_v2 {SLICE} auto-merge"` for each pending slice.
# Each slice gets a fresh context window and produces its own PR.
# Progress is tracked by the **Done** markers that /epic_v2 writes to the task file.
#
# Slice-level granularity (vs epic-level) keeps each claude invocation's context
# within the 1M ceiling, even for L-effort slices with large test suites and
# CodeRabbit iteration. The /epic_v2 skill natively supports slice IDs
# (digits+letter → single-slice mode) — see .claude/skills/epic_v2/SKILL.md.
#
# Usage:
#   ./scripts/run-phase.sh <phase-number> [starting-slice] [--dry-run] [--no-caffeinate]
#
# Examples:
#   ./scripts/run-phase.sh 10              # Run all remaining slices
#   ./scripts/run-phase.sh 10 84A          # Start from slice 84A
#   ./scripts/run-phase.sh 10 84           # Start from first pending slice of epic 84
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

# ─── Parse slice list ─────────────────────────────────────────────────────────
#
# Slices appear as rows like:  | **84A** | 84.1-84.10 | description... | status |
# Output format per line: "<slice-id>:<DONE|PENDING>"

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

# ─── Build execution list ─────────────────────────────────────────────────────

mapfile -t ALL_SLICES < <(extract_slices "$TASK_FILE")

if [[ ${#ALL_SLICES[@]} -eq 0 ]]; then
  echo "ERROR: No slices found in $TASK_FILE"
  echo "Expected Implementation Order tables with rows like: | **81A** | ..."
  exit 1
fi

# Resolve START_FROM:
#   - Exact slice ID (e.g., 450A) → start at that slice
#   - Epic number (e.g., 450) → start at first slice of that epic (450A if present)
# Anything else → error.
START_SLICE=""
if [[ -n "$START_FROM" ]]; then
  if [[ "$START_FROM" =~ ^[0-9]+[A-Z]+$ ]]; then
    START_SLICE="$START_FROM"
  elif [[ "$START_FROM" =~ ^[0-9]+$ ]]; then
    # Find the first slice whose numeric prefix matches this epic number.
    for entry in "${ALL_SLICES[@]}"; do
      slice="${entry%%:*}"
      epic_prefix=$(echo "$slice" | sed -E 's/[A-Z]+$//')
      if [[ "$epic_prefix" == "$START_FROM" ]]; then
        START_SLICE="$slice"
        break
      fi
    done
    if [[ -z "$START_SLICE" ]]; then
      echo "ERROR: No slice found for epic $START_FROM in $TASK_FILE"
      exit 1
    fi
  else
    echo "ERROR: Starting position '$START_FROM' is neither a slice ID (e.g. 450A) nor an epic number (e.g. 450)"
    exit 1
  fi
fi

# Filter to pending slices, respecting start_slice.
SLICES_TO_RUN=()
PAST_START=true
if [[ -n "$START_SLICE" ]]; then
  PAST_START=false
fi

for entry in "${ALL_SLICES[@]}"; do
  slice="${entry%%:*}"
  status="${entry#*:}"

  if [[ "$PAST_START" == false ]]; then
    if [[ "$slice" == "$START_SLICE" ]]; then
      PAST_START=true
    else
      continue
    fi
  fi

  # Skip Done slices.
  if [[ "$status" == "DONE" ]]; then
    continue
  fi

  SLICES_TO_RUN+=("$slice")
done

if [[ "$PAST_START" == false ]]; then
  echo "ERROR: Starting slice '$START_FROM' not found in $TASK_FILE"
  exit 1
fi

# ─── Report plan ──────────────────────────────────────────────────────────────

TOTAL_SLICES=${#ALL_SLICES[@]}
DONE_SLICE_COUNT=0
for entry in "${ALL_SLICES[@]}"; do
  status="${entry#*:}"
  [[ "$status" == "DONE" ]] && ((DONE_SLICE_COUNT++)) || true
done

log "Phase ${PHASE} — ${TASK_FILE}"
log "Total slices: ${TOTAL_SLICES}, Done: ${DONE_SLICE_COUNT}, Remaining: ${#SLICES_TO_RUN[@]}"

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
    echo "  $((i + 1)). Slice ${SLICES_TO_RUN[$i]}"
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
  log "Starting Slice ${slice} (${step})"
  log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Sync main before each slice so the worktree branches from latest main.
  log "Syncing main before starting slice..."
  git fetch origin main >> "$LOG_FILE" 2>&1
  git checkout main >> "$LOG_FILE" 2>&1
  git pull --ff-only >> "$LOG_FILE" 2>&1

  # Run claude with fresh context (timeout: 200 min = 12000 sec)
  SLICE_START=$(date +%s)
  SLICE_TIMEOUT=12000

  # Unset CLAUDECODE to avoid nested session detection
  if timeout "${SLICE_TIMEOUT}" env CLAUDECODE="" claude -p "/epic_v2 ${slice} auto-merge" \
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

  # Check if the slice was marked Done. Re-read the task file since
  # /epic_v2 may have updated it.
  if grep "$slice" "$TASK_FILE" | grep -q '\*\*Done\*\*'; then
    log "Slice ${slice} completed successfully (${SLICE_MINUTES}m)"
  else
    if [[ "$CLAUDE_EXIT" -eq 124 ]]; then
      log "ERROR: Slice ${slice} TIMED OUT after ${SLICE_MINUTES}m (limit: $((SLICE_TIMEOUT / 60))m)"
    else
      log "ERROR: Slice ${slice} did NOT complete (exit code: ${CLAUDE_EXIT}, duration: ${SLICE_MINUTES}m)"
    fi
    log "Slice was not marked Done in ${TASK_FILE}."
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
