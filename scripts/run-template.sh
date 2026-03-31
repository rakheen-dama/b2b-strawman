#!/usr/bin/env bash
#
# run-template.sh — Hands-off build orchestration for java-keycloak-multitenant-saas
#
# Loops through epic slices (T1A→T7B), invoking `claude -p "/template-epic {SLICE}"`
# for each one. Each slice gets a fresh context window.
#
# Usage:
#   ./scripts/run-template.sh [starting-slice] [--dry-run]
#
# Examples:
#   ./scripts/run-template.sh              # Run all remaining slices
#   ./scripts/run-template.sh T3A          # Start from slice T3A
#   ./scripts/run-template.sh --dry-run    # Preview slice order, no execution
#
set -euo pipefail

# Sleep prevention (macOS)
if [[ -z "${CAFFEINATED:-}" && "$*" != *"--no-caffeinate"* ]] && command -v caffeinate &>/dev/null; then
  export CAFFEINATED=1
  exec caffeinate -is "$0" "$@"
fi

# ─── Arguments ────────────────────────────────────────────────────────────────

START_SLICE=""
DRY_RUN=false

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --no-caffeinate) ;;
    *) START_SLICE="$arg" ;;
  esac
done

# ─── Paths ────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

TEMPLATE_ROOT="/Users/rakheendama/Projects/2026/java-keycloak-multitenant-saas"
TASK_FILE="${TEMPLATE_ROOT}/tasks/TASKS.md"
LOG_FILE="${TEMPLATE_ROOT}/tasks/.template-progress.log"

if [[ ! -f "$TASK_FILE" ]]; then
  echo "ERROR: Task file not found at $TASK_FILE"
  exit 1
fi

log() {
  local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
  echo "$msg"
  if [[ "$DRY_RUN" == false ]]; then
    echo "$msg" >> "$LOG_FILE"
  fi
}

# ─── Extract slice list ──────────────────────────────────────────────────────
#
# The TASKS.md has an Implementation Order table with rows like:
#   | 1 | T1 (...) | No | Foundation... |
# But slices are listed per-epic in Slice tables like:
#   | T2A | Gateway + Backend | ... | M |
#
# We extract from the Epic Overview: T1A,T1B,T1C,T2A,T2B,T2C,...
# by parsing the "Slices" column.

SLICES=()
while IFS= read -r line; do
  # Match: | T{N} | Name | Scope | Deps | Effort | T{N}A, T{N}B, ... | Status |
  slices_col=$(echo "$line" | awk -F'|' '{print $7}' | xargs)
  if [[ "$slices_col" =~ T[0-9]+[A-Z] ]]; then
    # Extract individual slice IDs (T1A, T1B, etc.)
    for slice in $(echo "$slices_col" | grep -oE 'T[0-9]+[A-Z]+'); do
      SLICES+=("$slice")
    done
  fi
done < <(grep -E '^\| T[0-9]+ \|' "$TASK_FILE")

if [[ ${#SLICES[@]} -eq 0 ]]; then
  echo "ERROR: No slices found in $TASK_FILE"
  exit 1
fi

log "Found ${#SLICES[@]} slices: ${SLICES[*]}"

# ─── Filter: skip Done slices and apply starting-slice ────────────────────────

is_done() {
  local slice="$1"
  # Extract epic number from slice (T2A → T2)
  local epic=$(echo "$slice" | sed 's/[A-Z]*$//')
  local epic_row
  epic_row=$(grep -E "^\| ${epic} \|" "$TASK_FILE")
  # Extract the Status column (last column before trailing |)
  local status
  status=$(echo "$epic_row" | awk -F'|' '{print $(NF-1)}' | xargs)
  # Epic fully done: **Done** in Status
  [[ "$status" == "**Done**" ]] && return 0
  # Individual slice done: slice ID appears in Status (e.g., "T1A Done, T1B Done")
  echo "$status" | grep -q "${slice}.*[Dd]one" && return 0
  return 1
}

STARTED=false
REMAINING=()

for slice in "${SLICES[@]}"; do
  # Skip if not yet at start slice
  if [[ -n "$START_SLICE" && "$STARTED" == false ]]; then
    if [[ "$slice" == "$START_SLICE" ]]; then
      STARTED=true
    else
      continue
    fi
  fi

  # Skip Done slices
  if is_done "$slice"; then
    log "SKIP $slice (already Done)"
    continue
  fi

  REMAINING+=("$slice")
done

if [[ -n "$START_SLICE" && "$STARTED" == false ]]; then
  echo "ERROR: Starting slice $START_SLICE not found in slice list"
  exit 1
fi

if [[ ${#REMAINING[@]} -eq 0 ]]; then
  log "All slices are Done!"
  exit 0
fi

log "Remaining slices: ${REMAINING[*]}"

if [[ "$DRY_RUN" == true ]]; then
  echo ""
  echo "=== DRY RUN — Would execute these slices ==="
  for i in "${!REMAINING[@]}"; do
    echo "  [$((i+1))/${#REMAINING[@]}] ${REMAINING[$i]}"
  done
  exit 0
fi

# ─── Execute slices ──────────────────────────────────────────────────────────

log "Starting template build — ${#REMAINING[@]} slices"
echo ""

for i in "${!REMAINING[@]}"; do
  slice="${REMAINING[$i]}"
  log "═══ SLICE ${slice} [$(( i + 1 ))/${#REMAINING[@]}] ═══"

  # Invoke claude with the /template-epic skill
  claude -p "/template-epic ${slice}" \
    --dangerously-skip-permissions \
    --model opus \
    2>&1 | tee -a "$LOG_FILE"

  CLAUDE_EXIT=$?

  if [[ $CLAUDE_EXIT -ne 0 ]]; then
    log "FAILED: claude exited with code $CLAUDE_EXIT for slice $slice"
    log "Fix the issue and restart: ./scripts/run-template.sh $slice"
    exit 1
  fi

  # Check if the epic is now marked Done; auto-patch if PR was merged but status missed
  if is_done "$slice"; then
    log "DONE: $slice completed successfully"
  else
    # Check if a PR for this slice was actually merged on GitHub
    local_epic=$(echo "$slice" | sed 's/[A-Z]*$//')
    merged_pr=$(cd "$TEMPLATE_ROOT" && gh pr list --state merged --search "Slice ${slice}" --json number --jq '.[0].number' 2>/dev/null)
    if [[ -n "$merged_pr" && "$merged_pr" != "null" ]]; then
      log "AUTO-FIX: $slice PR #$merged_pr was merged but TASKS.md not updated — patching"
      cd "$TEMPLATE_ROOT"
      # Read current status, append slice
      current_status=$(grep -E "^\| ${local_epic} \|" "$TASK_FILE" | awk -F'|' '{print $(NF-1)}' | xargs)
      if [[ -z "$current_status" ]]; then
        new_status="${slice} Done"
      else
        new_status="${current_status}, ${slice} Done"
      fi
      sed -i '' "s/\(| ${local_epic} |.*|.*|.*|.*|.*| \).*|/\1${new_status} |/" "$TASK_FILE"
      git add "$TASK_FILE" && git commit -m "chore: auto-mark ${slice} Done in TASKS.md (PR #${merged_pr})" && git push origin main 2>/dev/null
      cd "$PROJECT_ROOT"
      log "DONE: $slice completed successfully (auto-patched)"
    else
      log "WARNING: $slice — claude exited 0 but epic not marked Done and no merged PR found"
      log "Investigate and restart: ./scripts/run-template.sh $slice"
      exit 1
    fi
  fi

  echo ""
done

log "═══ ALL SLICES COMPLETE ═══"
