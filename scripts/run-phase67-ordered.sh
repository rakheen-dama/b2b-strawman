#!/usr/bin/env bash
#
# run-phase67-ordered.sh — Phase 67 orchestration with explicit dep-aware order.
#
# The generic run-phase.sh parses slice rows in file order, which for phase 67
# puts 487 (Disbursement Invoicing Integration) before 489 (Matter Closure).
# Epic 487 depends on 489A (needs V97 invoice_lines.disbursement_id column),
# so we must run 489A before 487A/487B.
#
# This wrapper drives slices in the order from `## Implementation Order`
# (Stages 0–5) flattened to serial, with 489A/489B moved before 487A/487B.
#
# Usage:
#   ./scripts/run-phase67-ordered.sh             # Run all remaining slices
#   ./scripts/run-phase67-ordered.sh 489A        # Start from slice 489A
#   ./scripts/run-phase67-ordered.sh --dry-run   # Preview order

set -euo pipefail

if [[ -z "${CAFFEINATED:-}" && "$*" != *"--no-caffeinate"* ]] && command -v caffeinate &>/dev/null; then
  export CAFFEINATED=1
  exec caffeinate -is "$0" "$@"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

PHASE=67
TASK_FILE="tasks/phase67-legal-depth-ii.md"
LOG_FILE="tasks/.phase-67-progress.log"

# Dep-aware order (489 before 487 per user; 486B before 491A; 489A before 492A/492B):
SLICES=(
  486A  # Stage 0
  486B  # Stage 1 (serialized)
  489A  # Stage 1 — pulled ahead of 487 to satisfy 487's dep on 489A
  489B  # Stage 2 — matter closure service
  487A  # Stage 1 — safe now that 489A is done
  487B  # Stage 2 — needs 487A + 489A (V97)
  488A  # Stage 1 frontend — safe after 486B+487
  488B  # Stage 3 frontend — needs 487
  490A  # Stage 3 frontend — needs 489
  490B  # Stage 4 — reopen action
  491A  # Stage 2 — needs 486B
  491B  # Stage 3 — needs 491A
  492A  # Stage 1 pack — needs 489A for acceptance_eligible
  492B  # Stage 2 pack — needs 489A
  493A  # Stage 5 — capstone
)

START_FROM=""
DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --no-caffeinate) ;;
    *) START_FROM="$arg" ;;
  esac
done

log() {
  local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
  echo "$msg"
  if [[ "$DRY_RUN" == false ]]; then
    echo "$msg" >> "$LOG_FILE"
  fi
}

# Resolve start point
TO_RUN=()
PAST_START=true
if [[ -n "$START_FROM" ]]; then
  PAST_START=false
fi

for slice in "${SLICES[@]}"; do
  if [[ "$PAST_START" == false ]]; then
    if [[ "$slice" == "$START_FROM" ]]; then
      PAST_START=true
    else
      continue
    fi
  fi
  # Skip if already Done
  if grep "| \*\*${slice}\*\*" "$TASK_FILE" | grep -q '\*\*Done\*\*'; then
    log "Skipping ${slice} (already Done)"
    continue
  fi
  TO_RUN+=("$slice")
done

if [[ "$PAST_START" == false ]]; then
  echo "ERROR: starting slice '$START_FROM' not in SLICES list"
  exit 1
fi

log "Phase 67 — ${TASK_FILE}"
log "Total slices: ${#SLICES[@]}, Remaining: ${#TO_RUN[@]}"
log "Execution order: ${TO_RUN[*]}"

if [[ "$DRY_RUN" == true ]]; then
  echo ""
  echo "=== DRY RUN ==="
  for i in "${!TO_RUN[@]}"; do
    echo "  $((i + 1)). ${TO_RUN[$i]}"
  done
  exit 0
fi

if [[ ${#TO_RUN[@]} -eq 0 ]]; then
  log "All slices Done. Nothing to run."
  exit 0
fi

log "Starting phase 67 execution..."

for i in "${!TO_RUN[@]}"; do
  slice="${TO_RUN[$i]}"
  step="$((i + 1))/${#TO_RUN[@]}"

  log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  log "Starting Slice ${slice} (${step})"
  log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  log "Syncing main before starting slice..."
  git fetch origin main >> "$LOG_FILE" 2>&1
  git checkout main >> "$LOG_FILE" 2>&1
  git pull --ff-only >> "$LOG_FILE" 2>&1

  SLICE_START=$(date +%s)
  SLICE_TIMEOUT=12000

  if timeout "${SLICE_TIMEOUT}" env CLAUDECODE="" claude -p "/epic_v2 ${slice} auto-merge" \
    --dangerously-skip-permissions \
    --model opus \
    >> "$LOG_FILE" 2>&1; then
    CLAUDE_EXIT=0
  else
    CLAUDE_EXIT=$?
  fi

  SLICE_END=$(date +%s)
  SLICE_MINUTES=$(( (SLICE_END - SLICE_START) / 60 ))

  if grep "| \*\*${slice}\*\*" "$TASK_FILE" | grep -q '\*\*Done\*\*'; then
    log "Slice ${slice} completed successfully (${SLICE_MINUTES}m)"
  else
    if [[ "$CLAUDE_EXIT" -eq 124 ]]; then
      log "ERROR: Slice ${slice} TIMED OUT after ${SLICE_MINUTES}m"
    else
      log "ERROR: Slice ${slice} did NOT complete (exit ${CLAUDE_EXIT}, ${SLICE_MINUTES}m)"
    fi
    log "To resume: ./scripts/run-phase67-ordered.sh ${slice}"
    if command -v osascript &>/dev/null; then
      osascript -e 'display notification "Slice '"$slice"' failed after '"$SLICE_MINUTES"'m" with title "Phase 67 FAILED" sound name "Basso"'
    fi
    exit 1
  fi

  echo ""
done

log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "Phase 67 COMPLETE — all ${#TO_RUN[@]} slices done!"
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if command -v osascript &>/dev/null; then
  osascript -e 'display notification "All '"${#TO_RUN[@]}"' slices done" with title "Phase 67 Complete" sound name "Glass"'
fi
