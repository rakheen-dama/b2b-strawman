#!/bin/bash
# =============================================================================
# Autonomous Vertical QA Cycle
#
# Runs specialized agents in a sequential loop until the full lifecycle
# scenario passes end-to-end. All work happens on a parent bugfix branch.
#
# Usage:
#   ./scripts/run-qa-cycle.sh <scenario-file> [gap-report] [--resume]
#
# Examples:
#   ./scripts/run-qa-cycle.sh tasks/phase47-lifecycle-script.md tasks/phase47-gap-report-agent.md
#   ./scripts/run-qa-cycle.sh tasks/phase47-lifecycle-script.md --resume
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# --- Arguments ---
SCENARIO="${1:?Usage: run-qa-cycle.sh <scenario-file> [gap-report] [--resume]}"
SHIFT_ARGS=1
GAP_REPORT=""
RESUME=false

for arg in "${@:2}"; do
  case "$arg" in
    --resume) RESUME=true ;;
    *) GAP_REPORT="$arg" ;;
  esac
done

if [[ ! -f "$SCENARIO" ]]; then
  echo "ERROR: Scenario file not found: $SCENARIO"
  exit 1
fi

# --- Config ---
BRANCH="bugfix_cycle_$(date +%Y-%m-%d)"
MAX_CYCLES=20
QA_DIR="qa_cycle"
PROMPTS_DIR="scripts/qa-cycle/prompts"
STATUS_FILE="$QA_DIR/status.md"
ERROR_LOG="$QA_DIR/error-log.md"
PROGRESS_LOG="$QA_DIR/.progress.log"
SENTINEL_PID=""
E2E_COMPOSE="compose/docker-compose.e2e.yml"

# --- Helpers ---
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$PROGRESS_LOG"; }
notify() {
  if command -v osascript &>/dev/null; then
    osascript -e "display notification \"$1\" with title \"QA Cycle\" sound name \"${2:-Glass}\""
  fi
}

# --- Setup ---
if [[ "$RESUME" == "true" ]]; then
  log "Resuming QA cycle on branch $BRANCH"
  git checkout "$BRANCH" 2>/dev/null || { echo "ERROR: Branch $BRANCH not found. Cannot resume."; exit 1; }
  git pull origin "$BRANCH" 2>/dev/null || true
else
  log "Starting new QA cycle"

  # Check if branch already exists
  if git show-ref --verify --quiet "refs/heads/$BRANCH" 2>/dev/null; then
    echo "Branch $BRANCH already exists. Use --resume to continue, or delete the branch first."
    exit 1
  fi

  # Create branch (may already exist remotely)
  if git ls-remote --heads origin "$BRANCH" | grep -q "$BRANCH"; then
    git checkout "$BRANCH"
    git pull origin "$BRANCH"
  else
    git checkout -b "$BRANCH"
  fi

  # Create directory structure
  mkdir -p "$QA_DIR/fix-specs" "$QA_DIR/checkpoint-results"

  # Initialize status.md if not present
  if [[ ! -f "$STATUS_FILE" ]]; then
    log "ERROR: $STATUS_FILE not found. Create it before running."
    exit 1
  fi

  # Initialize error log if not present
  if [[ ! -f "$ERROR_LOG" ]]; then
    cat > "$ERROR_LOG" << 'ERREOF'
# E2E Error Log

| Timestamp | Service | Level | Message |
|-----------|---------|-------|---------|
ERREOF
  fi

  log "Branch: $BRANCH"
  log "Scenario: $SCENARIO"
  log "Gap report: ${GAP_REPORT:-none}"
fi

# --- Prevent machine sleep (macOS) ---
if command -v caffeinate &>/dev/null; then
  caffeinate -is -w $$ &
  log "Sleep prevention enabled (caffeinate)"
fi

# --- Phase 0: Infra seed fix (first run only) ---
if [[ "$RESUME" != "true" ]]; then
  log "=== Phase 0: Infra — Fix E2E seed ==="
  INFRA_PROMPT=$(cat "$PROMPTS_DIR/infra-seed.md")

  # Unset CLAUDE_CODE to avoid nesting issues
  unset CLAUDE_CODE 2>/dev/null || true

  log "Dispatching Infra Agent (seed fix)..."
  claude -p "$INFRA_PROMPT" 2>&1 | tee -a "$PROGRESS_LOG"

  INFRA_EXIT=${PIPESTATUS[0]}
  if [[ $INFRA_EXIT -ne 0 ]]; then
    log "ERROR: Infra agent failed (exit $INFRA_EXIT)"
    notify "Infra agent failed" "Basso"
    exit 1
  fi
  log "Infra seed fix complete"
fi

# --- Start Log Sentinel (fire-and-forget) ---
start_sentinel() {
  log "Starting log sentinel (background, fire-and-forget)..."

  # Only start if E2E stack is running
  if ! docker compose -f "$E2E_COMPOSE" ps --status running 2>/dev/null | grep -q "e2e"; then
    log "E2E stack not running — skipping sentinel"
    return
  fi

  (
    docker compose -f "$E2E_COMPOSE" logs -f --since 1m 2>&1 \
      | grep --line-buffered -iE 'ERROR|Exception|FATAL|[5][0-9]{2}\s' \
      | while IFS= read -r line; do
          SERVICE=$(echo "$line" | awk '{print $1}' | tr -d '|')
          TIMESTAMP=$(date '+%Y-%m-%dT%H:%M:%SZ')
          # Append to error log (atomic-ish)
          echo "| $TIMESTAMP | $SERVICE | ERROR | $(echo "$line" | cut -c1-200) |" >> "$ERROR_LOG"
        done
  ) &
  SENTINEL_PID=$!
  log "Sentinel started (PID: $SENTINEL_PID)"
}

stop_sentinel() {
  if [[ -n "${SENTINEL_PID:-}" ]] && kill -0 "$SENTINEL_PID" 2>/dev/null; then
    kill "$SENTINEL_PID" 2>/dev/null || true
    log "Sentinel stopped"
  fi
}

# Don't fail if sentinel dies
trap 'stop_sentinel' EXIT

start_sentinel

# --- Main Loop ---
log "=== Starting QA Cycle (max $MAX_CYCLES cycles) ==="

for cycle in $(seq 1 $MAX_CYCLES); do
  log ""
  log "========================================="
  log "  CYCLE $cycle / $MAX_CYCLES"
  log "========================================="

  # Ensure we're on the parent branch with latest
  git checkout "$BRANCH" 2>/dev/null
  git pull origin "$BRANCH" 2>/dev/null || true

  # --- QA Turn ---
  log "--- QA Agent Turn (Cycle $cycle) ---"

  QA_PROMPT=$(cat "$PROMPTS_DIR/qa-agent.md" | sed "s|SCENARIO_FILE_PLACEHOLDER|$SCENARIO|g")

  unset CLAUDE_CODE 2>/dev/null || true
  claude -p "$QA_PROMPT" 2>&1 | tee -a "$PROGRESS_LOG"
  log "QA Agent finished"

  # Pull latest status (QA may have pushed)
  git pull origin "$BRANCH" 2>/dev/null || true

  # Check: did QA complete all days?
  if grep -q "ALL_DAYS_COMPLETE" "$STATUS_FILE"; then
    log "=== ALL DAYS COMPLETE — QA cycle succeeded! ==="
    notify "QA Cycle Complete! All scenarios pass." "Glass"
    break
  fi

  # --- Product Turn ---
  log "--- Product Agent Turn (Cycle $cycle) ---"

  # Only run if there are OPEN items
  if grep -q "| OPEN |" "$STATUS_FILE" || grep -q "| REOPENED |" "$STATUS_FILE"; then
    PRODUCT_PROMPT=$(cat "$PROMPTS_DIR/product-agent.md")
    unset CLAUDE_CODE 2>/dev/null || true
    claude -p "$PRODUCT_PROMPT" 2>&1 | tee -a "$PROGRESS_LOG"
    log "Product Agent finished"
    git pull origin "$BRANCH" 2>/dev/null || true
  else
    log "No OPEN items — skipping Product Agent"
  fi

  # --- Dev Turn (one fix at a time) ---
  DEV_COUNT=0
  while grep -q "| SPEC_READY |" "$STATUS_FILE"; do
    # Extract first SPEC_READY gap ID
    GAP_ID=$(grep "| SPEC_READY |" "$STATUS_FILE" | head -1 | awk -F'|' '{print $2}' | xargs)

    if [[ -z "$GAP_ID" ]]; then
      log "Could not extract gap ID from SPEC_READY row"
      break
    fi

    FIX_SPEC="$QA_DIR/fix-specs/$GAP_ID.md"
    if [[ ! -f "$FIX_SPEC" ]]; then
      log "WARNING: Fix spec not found: $FIX_SPEC — skipping $GAP_ID"
      # Mark as stuck so we don't loop forever
      sed -i '' "s/| $GAP_ID |.*| SPEC_READY |/| $GAP_ID | (spec missing) | OPEN |/" "$STATUS_FILE" 2>/dev/null || true
      continue
    fi

    log "--- Dev Agent Turn: $GAP_ID (Cycle $cycle) ---"

    DEV_PROMPT=$(cat "$PROMPTS_DIR/dev-agent.md" | sed "s|FIX_SPEC_PLACEHOLDER|$FIX_SPEC|g")
    unset CLAUDE_CODE 2>/dev/null || true
    claude -p "$DEV_PROMPT" 2>&1 | tee -a "$PROGRESS_LOG"
    log "Dev Agent finished ($GAP_ID)"

    git checkout "$BRANCH" 2>/dev/null
    git pull origin "$BRANCH" 2>/dev/null || true

    DEV_COUNT=$((DEV_COUNT + 1))

    # Safety: max 5 fixes per cycle to avoid infinite loops
    if [[ $DEV_COUNT -ge 5 ]]; then
      log "Max 5 fixes per cycle reached — continuing to rebuild"
      break
    fi
  done

  if [[ $DEV_COUNT -gt 0 ]]; then
    log "Dev Agent completed $DEV_COUNT fixes this cycle"
  else
    log "No SPEC_READY items — skipping Dev Agent"
  fi

  # --- Infra Turn (rebuild if needed) ---
  if grep -q "NEEDS_REBUILD" "$STATUS_FILE"; then
    log "--- Infra Agent Turn: Rebuild (Cycle $cycle) ---"

    # Stop and restart sentinel after rebuild
    stop_sentinel

    REBUILD_PROMPT=$(cat "$PROMPTS_DIR/infra-rebuild.md")
    unset CLAUDE_CODE 2>/dev/null || true
    claude -p "$REBUILD_PROMPT" 2>&1 | tee -a "$PROGRESS_LOG"
    log "Infra rebuild finished"

    git pull origin "$BRANCH" 2>/dev/null || true

    # Restart sentinel
    start_sentinel
  fi

  log "Cycle $cycle complete"
  notify "QA Cycle $cycle complete" "Pop"
done

# --- Wrap Up ---
if [[ $cycle -ge $MAX_CYCLES ]] && ! grep -q "ALL_DAYS_COMPLETE" "$STATUS_FILE"; then
  log "WARNING: Max cycles ($MAX_CYCLES) reached without completing all days"
  notify "QA Cycle hit max cycles without completion" "Basso"
fi

stop_sentinel

# Generate final summary
OPEN_COUNT=$(grep -c "| OPEN |" "$STATUS_FILE" 2>/dev/null || echo "0")
FIXED_COUNT=$(grep -c "| FIXED |" "$STATUS_FILE" 2>/dev/null || echo "0")
VERIFIED_COUNT=$(grep -c "| VERIFIED |" "$STATUS_FILE" 2>/dev/null || echo "0")
WONTFIX_COUNT=$(grep -c "| WONT_FIX |" "$STATUS_FILE" 2>/dev/null || echo "0")

log ""
log "========================================="
log "  QA CYCLE SUMMARY"
log "========================================="
log "  Cycles run:  $cycle"
log "  Open:        $OPEN_COUNT"
log "  Fixed:       $FIXED_COUNT"
log "  Verified:    $VERIFIED_COUNT"
log "  Won't fix:   $WONTFIX_COUNT"
log "========================================="
log ""
log "Status file: $STATUS_FILE"
log "Progress log: $PROGRESS_LOG"
log "To resume: ./scripts/run-qa-cycle.sh $SCENARIO --resume"
