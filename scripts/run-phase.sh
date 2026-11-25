#!/usr/bin/env bash
#
# run-phase.sh — Hands-off phase orchestration for DocTeams
#
# Iterates the phase's slice list in build order and invokes
# `claude -p "/epic_v2 {SLICE} auto-merge"` for each pending slice. Build order comes from the
# task file's `<!-- run-order: 645A 649A ... -->` line when present, else from slice-row order
# (see "Execution order source" below).
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
# By default, keeps macOS awake for the whole run with a background loop of OVERLAPPING
# `caffeinate -dimsu` assertions (see "Sleep prevention" below), and warns when the machine
# is on battery (a closed lid on battery sleeps regardless). Use --no-caffeinate to disable.
#
set -euo pipefail

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
    # A conditional slice whose hypothesis did not reproduce is marked STRUCK (hypothesis protocol) —
    # it is complete, not failed, so it counts as Done (Phase 92 stage 9).
    if echo "$line" | grep -qE '\*\*Done\*\*|STRUCK'; then
      echo "${slice}:DONE"
    else
      echo "${slice}:PENDING"
    fi
  done
}

# ─── Build execution list ─────────────────────────────────────────────────────

mapfile -t ROW_SLICES < <(extract_slices "$TASK_FILE")

if [[ ${#ROW_SLICES[@]} -eq 0 ]]; then
  echo "ERROR: No slices found in $TASK_FILE"
  echo "Expected Implementation Order tables with rows like: | **81A** | ..."
  exit 1
fi

# ─── Execution order source ───────────────────────────────────────────────────
#
# A task file may carry exactly ONE machine-readable line listing every slice id in build order:
#   <!-- run-order: 645A 649A 646A 649B ... -->
# When present it IS the execution order. Phase 92 lesson (2026-11): slice rows live in per-epic
# tables, so row order ran 646A before 649A and halted the phase — the prose Implementation Order
# table is for humans and is never parsed. Without the line, slices run in slice-row order.
# Done status always comes from the slice rows.

mapfile -t RUN_ORDER_LINES < <(grep -nE '^[[:space:]]*<!--[[:space:]]*run-order:' "$TASK_FILE" || true)
MISSING_FROM_ORDER=()

if [[ ${#RUN_ORDER_LINES[@]} -gt 1 ]]; then
  echo "ERROR: $TASK_FILE has ${#RUN_ORDER_LINES[@]} run-order lines (expected exactly one):"
  printf "  line %s\n" "${RUN_ORDER_LINES[@]%%:*}"
  exit 1
elif [[ ${#RUN_ORDER_LINES[@]} -eq 1 ]]; then
  RUN_ORDER_LINE_NO="${RUN_ORDER_LINES[0]%%:*}"
  RUN_ORDER_TEXT="${RUN_ORDER_LINES[0]#*:}"
  if [[ "$RUN_ORDER_TEXT" != *"-->"* ]]; then
    echo "ERROR: run-order line ${TASK_FILE}:${RUN_ORDER_LINE_NO} is not closed with '-->'"
    exit 1
  fi
  RUN_ORDER_IDS_TEXT=$(sed -E 's/^[[:space:]]*<!--[[:space:]]*run-order:(.*)-->.*$/\1/' <<<"$RUN_ORDER_TEXT")
  read -r -a RUN_ORDER_IDS <<<"$RUN_ORDER_IDS_TEXT"
  if [[ ${#RUN_ORDER_IDS[@]} -eq 0 ]]; then
    echo "ERROR: run-order line ${TASK_FILE}:${RUN_ORDER_LINE_NO} lists no slice ids"
    exit 1
  fi

  # Slice id → DONE|PENDING from the rows (DONE wins if an id has more than one row,
  # matching the post-slice Done check, which greps any row of that id).
  declare -A ROW_STATUS=()
  for entry in "${ROW_SLICES[@]}"; do
    slice="${entry%%:*}"
    status="${entry#*:}"
    if [[ "${ROW_STATUS[$slice]:-}" != "DONE" ]]; then
      ROW_STATUS[$slice]="$status"
    fi
  done

  declare -A LISTED=()
  ALL_SLICES=()
  ORDER_ERRORS=0
  for slice in "${RUN_ORDER_IDS[@]}"; do
    if [[ ! "$slice" =~ ^[0-9]+[A-Z]+$ ]]; then
      echo "ERROR: run-order entry '$slice' is not a slice id (e.g. 450A)"
      ORDER_ERRORS=$((ORDER_ERRORS + 1))
      continue
    fi
    if [[ -n "${LISTED[$slice]:-}" ]]; then
      echo "ERROR: run-order lists slice $slice more than once"
      ORDER_ERRORS=$((ORDER_ERRORS + 1))
      continue
    fi
    LISTED[$slice]=1
    if [[ -z "${ROW_STATUS[$slice]:-}" ]]; then
      echo "ERROR: run-order lists slice $slice but $TASK_FILE has no slice row | **${slice}** | ..."
      ORDER_ERRORS=$((ORDER_ERRORS + 1))
      continue
    fi
    ALL_SLICES+=("${slice}:${ROW_STATUS[$slice]}")
  done
  if [[ "$ORDER_ERRORS" -gt 0 ]]; then
    echo "ERROR: ${ORDER_ERRORS} problem(s) in run-order line ${TASK_FILE}:${RUN_ORDER_LINE_NO} — fix it before running."
    exit 1
  fi

  # Slice rows the run-order line leaves out never run — warn (listed once each, in row order).
  declare -A WARNED=()
  for entry in "${ROW_SLICES[@]}"; do
    slice="${entry%%:*}"
    if [[ -z "${LISTED[$slice]:-}" && -z "${WARNED[$slice]:-}" ]]; then
      WARNED[$slice]=1
      MISSING_FROM_ORDER+=("${slice} (${ROW_STATUS[$slice]})")
    fi
  done

  ORDER_SOURCE="run-order line (${TASK_FILE}:${RUN_ORDER_LINE_NO}, ${#ALL_SLICES[@]} slices)"
else
  ALL_SLICES=("${ROW_SLICES[@]}")
  ORDER_SOURCE="slice-row order (no <!-- run-order: ... --> line in ${TASK_FILE})"
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
log "Order source: ${ORDER_SOURCE}"
if [[ ${#MISSING_FROM_ORDER[@]} -gt 0 ]]; then
  log "WARNING: ${#MISSING_FROM_ORDER[@]} slice row(s) missing from the run-order line will NOT run: ${MISSING_FROM_ORDER[*]}"
fi
log "Total slices: ${TOTAL_SLICES}, Done: ${DONE_SLICE_COUNT}, Remaining: ${#SLICES_TO_RUN[@]}"

if [[ ${#SLICES_TO_RUN[@]} -eq 0 ]]; then
  log "All slices are Done! Nothing to run."
  exit 0
fi

log "Execution order: ${SLICES_TO_RUN[*]}"

if [[ "$DRY_RUN" == true ]]; then
  echo ""
  echo "=== DRY RUN ==="
  echo "Order source: ${ORDER_SOURCE}"
  echo "Would execute ${#SLICES_TO_RUN[@]} slices:"
  for i in "${!SLICES_TO_RUN[@]}"; do
    echo "  $((i + 1)). Slice ${SLICES_TO_RUN[$i]}"
  done
  echo ""
  echo "Command per slice:"
  echo "  CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS=0 claude -p \"/epic_v2 {SLICE} auto-merge\" --dangerously-skip-permissions --model opus --session-id <uuid>"
  echo "  Watchdog: stopped only when HUNG (no transcript write for ${SLICE_IDLE_MIN:-45}m); backstop ${SLICE_MAX_MIN:-720}m"
  exit 0
fi

# ─── Sleep prevention (macOS) ────────────────────────────────────────────────
#
# Phase 92 lesson (2026-11-17/18): a single `exec caffeinate -is "$0"` wrapper was killed
# mid-run (cause not found), orphaning this script with no assertion; the Mac then idle-slept
# ~30 min mid-slice, and a non-overlapping caffeinate loop still slept in the gap between
# cycles. So: a background loop starts a fresh 180s `caffeinate -dimsu` every 60s — always
# 2–3 live assertions, no gap, a killed one replaced within a minute — and stops when this
# script exits. -s only holds on AC power, so warn when on battery.

if [[ "$*" != *"--no-caffeinate"* ]] && command -v caffeinate &>/dev/null; then
  RUNNER_PID=$$
  ( while kill -0 "$RUNNER_PID" 2>/dev/null; do
      caffeinate -dimsu -t 180 &
      sleep 60
    done ) >/dev/null 2>&1 &
  KEEPAWAKE_PID=$!
  trap 'kill "$KEEPAWAKE_PID" 2>/dev/null || true' EXIT
  log "Sleep prevention: overlapping caffeinate loop (pid ${KEEPAWAKE_PID})"
  if command -v pmset &>/dev/null && pmset -g batt 2>/dev/null | grep -q "Battery Power"; then
    log "WARNING: on BATTERY power — a closed lid will sleep the Mac and stop this run. Plug in."
  fi
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

  # ── Hang watchdog, not a work budget ──
  # A slice that is still making progress is never stopped, however long it runs. The runner
  # stops a slice only when its session is HUNG: no write to the slice's session transcript or
  # any of its subagent transcripts for SLICE_IDLE_MIN minutes. (653A, 2026-11-23: the longest
  # silent stretch across parent + 20 subagents was 9.5 min; the parent alone went 32 min quiet
  # while subagents worked, so subagent transcripts must count.) SLICE_MAX_MIN is only a
  # backstop for a session that loops while writing. Agents: do not plan your work around
  # either limit; finish the slice.
  # History: a fixed 200 → 260 min cap ended 648A one commit from merge (Phase 92) and made
  # 653A stand down 9 min before it with only an OBS re-run left (Phase 93).
  SLICE_START=$(date +%s)
  SLICE_IDLE_MIN="${SLICE_IDLE_MIN:-45}"
  SLICE_MAX_MIN="${SLICE_MAX_MIN:-720}"
  SLICE_TIMEOUT=$(( SLICE_MAX_MIN * 60 ))
  SESSION_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
  TRANSCRIPT_DIR="${HOME}/.claude/projects/$(pwd -P | sed 's#[/.]#-#g')"
  TRANSCRIPT="${TRANSCRIPT_DIR}/${SESSION_ID}.jsonl"
  log "Session ${SESSION_ID} — watchdog: hung after ${SLICE_IDLE_MIN}m without a transcript write; backstop ${SLICE_MAX_MIN}m"

  # Unset CLAUDECODE to avoid nested session detection.
  # CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS=0: without it, -p stops background subagents 10 min
  # after the orchestrator's last turn and exits 0 mid-slice (614B).
  # -k 120: a claude -p that ignores SIGTERM is SIGKILLed 2 min later (634A lived 29 min past
  # its cap); timeout then exits 137 instead of 124. timeout forwards a SIGTERM it receives.
  timeout -k 120 "${SLICE_TIMEOUT}" env CLAUDECODE="" CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS=0 \
    claude -p "/epic_v2 ${slice} auto-merge" \
    --session-id "${SESSION_ID}" \
    --dangerously-skip-permissions \
    --model opus \
    >> "$LOG_FILE" 2>&1 &
  CLAUDE_PID=$!

  SLICE_HUNG=0
  WARNED_NO_TRANSCRIPT=0
  while kill -0 "$CLAUDE_PID" 2>/dev/null; do
    sleep 60
    kill -0 "$CLAUDE_PID" 2>/dev/null || break
    NOW=$(date +%s)
    if [[ -f "$TRANSCRIPT" ]]; then
      # `|| true`: set -euo pipefail would end the runner when the subagent dir does not exist yet.
      LAST_WRITE=$( { find "$TRANSCRIPT" "${TRANSCRIPT_DIR}/${SESSION_ID}" -type f -exec stat -f %m {} + 2>/dev/null || true; } | sort -n | tail -1)
    else
      LAST_WRITE=""
      # No transcript after 10 min means the path scheme changed: fall back to the backstop
      # only (never kill on a signal we cannot see).
      if (( NOW - SLICE_START > 600 && WARNED_NO_TRANSCRIPT == 0 )); then
        log "WARNING: no transcript at ${TRANSCRIPT}; hang watchdog off for ${slice}, backstop ${SLICE_MAX_MIN}m only"
        WARNED_NO_TRANSCRIPT=1
      fi
    fi
    if [[ -n "$LAST_WRITE" ]] && (( NOW - LAST_WRITE >= SLICE_IDLE_MIN * 60 )); then
      log "ERROR: Slice ${slice} HUNG — no transcript write for $(( (NOW - LAST_WRITE) / 60 ))m; stopping it"
      SLICE_HUNG=1
      kill -TERM "$CLAUDE_PID" 2>/dev/null || true
      break
    fi
  done
  if wait "$CLAUDE_PID"; then
    CLAUDE_EXIT=0
  else
    CLAUDE_EXIT=$?
  fi

  SLICE_END=$(date +%s)
  SLICE_DURATION=$(( SLICE_END - SLICE_START ))
  SLICE_MINUTES=$(( SLICE_DURATION / 60 ))

  # Check if the slice was marked Done. Re-read the task file since
  # /epic_v2 may have updated it.
  # Match the slice's own table row (parser-aligned, see line 104), not any
  # line that mentions the slice id — dependency/unblock columns on a sibling
  # Done row caused false positives (e.g. 511B's Done row lists 512A as a
  # downstream-unblock, falsely marking 512A done before it ever ran).
  # Slices push their bookkeeping from a worktree, so the Done row can be on origin/main while
  # this checkout is stale (658A, 2026-11-24: merged and marked Done, yet the runner stopped).
  # Check origin/main too, then fast-forward this checkout so the next slice starts from it.
  git fetch -q origin main >> "$LOG_FILE" 2>&1 || true
  if grep -E "^\| \*\*${slice}\*\*.*(\*\*Done\*\*|STRUCK)" "$TASK_FILE" >/dev/null \
    || git show "origin/main:${TASK_FILE}" 2>/dev/null \
      | grep -E "^\| \*\*${slice}\*\*.*(\*\*Done\*\*|STRUCK)" >/dev/null; then
    git pull --ff-only >> "$LOG_FILE" 2>&1 || log "WARNING: could not fast-forward main after ${slice}"
    log "Slice ${slice} completed successfully (${SLICE_MINUTES}m)"
  else
    # 137 means a timeout only when the cap was actually reached (timeout -k escalated to SIGKILL);
    # an earlier SIGKILL (e.g. OOM) is reported as the failure it is.
    if [[ "$SLICE_HUNG" -eq 1 ]]; then
      log "ERROR: Slice ${slice} stopped as HUNG after ${SLICE_MINUTES}m (exit ${CLAUDE_EXIT}). Transcript: ${TRANSCRIPT}"
    elif [[ "$CLAUDE_EXIT" -eq 124 ]] || [[ "$CLAUDE_EXIT" -eq 137 && "$SLICE_DURATION" -ge "$SLICE_TIMEOUT" ]]; then
      log "ERROR: Slice ${slice} hit the ${SLICE_MAX_MIN}m BACKSTOP after ${SLICE_MINUTES}m"
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
