#!/usr/bin/env bash
set -euo pipefail

BACKEND_QUALITY_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_QUALITY_PROJECT_ROOT="$(cd "$BACKEND_QUALITY_SCRIPT_DIR/../.." && pwd)"
BACKEND_QUALITY_TICKETS_FILE="$BACKEND_QUALITY_SCRIPT_DIR/tickets.tsv"
BACKEND_QUALITY_WORKTREE_ROOT_DEFAULT="$(cd "$BACKEND_QUALITY_PROJECT_ROOT/.." && pwd)/b2b-strawman-worktrees"
BACKEND_QUALITY_WORKTREE_ROOT="${WORKTREE_ROOT:-$BACKEND_QUALITY_WORKTREE_ROOT_DEFAULT}"
BACKEND_QUALITY_STATE_DIR="${BACKEND_QUALITY_STATE_DIR:-$BACKEND_QUALITY_PROJECT_ROOT/.backend-quality}"
BACKEND_QUALITY_STATE_FILE="$BACKEND_QUALITY_STATE_DIR/state.json"
BACKEND_QUALITY_LOG_DIR="$BACKEND_QUALITY_STATE_DIR/logs"
BACKEND_QUALITY_PROMPT_DIR="$BACKEND_QUALITY_STATE_DIR/prompts"
BACKEND_QUALITY_SUMMARY_DIR="$BACKEND_QUALITY_STATE_DIR/summaries"
BACKEND_QUALITY_EVENTS_DIR="$BACKEND_QUALITY_STATE_DIR/events"
BACKEND_QUALITY_REVIEW_GATES_DEFAULT="1,2,4"

bq::ensure_dirs() {
  mkdir -p \
    "$BACKEND_QUALITY_WORKTREE_ROOT" \
    "$BACKEND_QUALITY_STATE_DIR" \
    "$BACKEND_QUALITY_LOG_DIR" \
    "$BACKEND_QUALITY_PROMPT_DIR" \
    "$BACKEND_QUALITY_SUMMARY_DIR" \
    "$BACKEND_QUALITY_EVENTS_DIR"
}

bq::ensure_state_file() {
  bq::ensure_dirs
  if [[ ! -f "$BACKEND_QUALITY_STATE_FILE" ]]; then
    cat > "$BACKEND_QUALITY_STATE_FILE" <<JSON
{
  "program": {
    "createdAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "updatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "currentWave": null,
    "reviewGate": null
  },
  "tickets": {}
}
JSON
  fi
}

bq::ticket_row() {
  local ticket="$1"
  awk -F '\t' -v t="$ticket" '$1 == t { print $0 }' "$BACKEND_QUALITY_TICKETS_FILE"
}

bq::ticket_exists() {
  [[ -n "$(bq::ticket_row "$1")" ]]
}

bq::parse_ticket() {
  local ticket="$1"
  local row
  row="$(bq::ticket_row "$ticket")"
  if [[ -z "$row" ]]; then
    echo "Unknown ticket: $ticket" >&2
    return 1
  fi
  IFS=$'\t' read -r BQ_TICKET_ID BQ_TICKET_WAVE BQ_TICKET_BRANCH BQ_TICKET_TITLE BQ_TICKET_DEPS BQ_TICKET_SCOPE BQ_TICKET_ACCEPTANCE <<< "$row"
}

bq::ticket_ids() {
  awk -F '\t' '{print $1}' "$BACKEND_QUALITY_TICKETS_FILE"
}

bq::tickets_for_wave_range() {
  local from_wave="$1"
  local to_wave="$2"
  awk -F '\t' -v from="$from_wave" -v to="$to_wave" '$2 >= from && $2 <= to {print $1}' "$BACKEND_QUALITY_TICKETS_FILE"
}

bq::ticket_worktree() {
  local ticket="$1"
  printf '%s/%s' "$BACKEND_QUALITY_WORKTREE_ROOT" "$ticket"
}

bq::ticket_log_file() {
  local ticket="$1"
  printf '%s/%s.log' "$BACKEND_QUALITY_LOG_DIR" "$ticket"
}

bq::ticket_prompt_file() {
  local ticket="$1"
  printf '%s/%s.prompt.txt' "$BACKEND_QUALITY_PROMPT_DIR" "$ticket"
}

bq::ticket_summary_file() {
  local ticket="$1"
  printf '%s/%s.summary.txt' "$BACKEND_QUALITY_SUMMARY_DIR" "$ticket"
}

bq::ticket_events_file() {
  local ticket="$1"
  printf '%s/%s.events.jsonl' "$BACKEND_QUALITY_EVENTS_DIR" "$ticket"
}

bq::state_get_ticket_status() {
  local ticket="$1"
  bq::ensure_state_file
  jq -r --arg ticket "$ticket" '.tickets[$ticket].status // "pending"' "$BACKEND_QUALITY_STATE_FILE"
}

bq::state_set_program_field() {
  local field="$1"
  local value_json="$2"
  bq::ensure_state_file
  local tmp
  tmp="$(mktemp)"
  jq --arg now "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg field "$field" --argjson value "$value_json" '
    .program[$field] = $value |
    .program.updatedAt = $now
  ' "$BACKEND_QUALITY_STATE_FILE" > "$tmp"
  mv "$tmp" "$BACKEND_QUALITY_STATE_FILE"
}

bq::state_clear_review_gate() {
  bq::state_set_program_field "reviewGate" 'null'
}

bq::state_set_review_gate() {
  local wave="$1"
  bq::state_set_program_field "reviewGate" "{\"wave\":$wave,\"setAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}"
}

bq::state_set_ticket() {
  local ticket="$1"
  local status="$2"
  local agent="$3"
  local worktree="$4"
  local log_file="$5"
  local summary_file="$6"
  local exit_code="$7"
  local note="$8"
  local tmp
  tmp="$(mktemp)"
  jq \
    --arg ticket "$ticket" \
    --arg status "$status" \
    --arg agent "$agent" \
    --arg worktree "$worktree" \
    --arg logFile "$log_file" \
    --arg summaryFile "$summary_file" \
    --arg note "$note" \
    --arg now "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --argjson exitCode "$exit_code" '
      .tickets[$ticket] = ((.tickets[$ticket] // {}) + {
        status: $status,
        agent: $agent,
        worktree: $worktree,
        logFile: $logFile,
        summaryFile: $summaryFile,
        exitCode: $exitCode,
        note: $note,
        updatedAt: $now,
        startedAt: ((.tickets[$ticket].startedAt // $now))
      }) |
      .program.updatedAt = $now
    ' "$BACKEND_QUALITY_STATE_FILE" > "$tmp"
  mv "$tmp" "$BACKEND_QUALITY_STATE_FILE"
}

bq::deps_satisfied() {
  local ticket="$1"
  bq::parse_ticket "$ticket"
  if [[ "$BQ_TICKET_DEPS" == "-" ]]; then
    return 0
  fi

  local dep
  IFS='|' read -r -a deps <<< "$BQ_TICKET_DEPS"
  for dep in "${deps[@]}"; do
    if [[ "$(bq::state_get_ticket_status "$dep")" != "completed" ]]; then
      return 1
    fi
  done
  return 0
}

bq::missing_deps() {
  local ticket="$1"
  bq::parse_ticket "$ticket"
  if [[ "$BQ_TICKET_DEPS" == "-" ]]; then
    return 0
  fi

  local dep
  local missing=()
  IFS='|' read -r -a deps <<< "$BQ_TICKET_DEPS"
  for dep in "${deps[@]}"; do
    if [[ "$(bq::state_get_ticket_status "$dep")" != "completed" ]]; then
      missing+=("$dep")
    fi
  done
  printf '%s\n' "${missing[@]}"
}

bq::review_gate_enabled_for_wave() {
  local wave="$1"
  local gates="${REVIEW_GATES:-$BACKEND_QUALITY_REVIEW_GATES_DEFAULT}"
  local item
  IFS=',' read -r -a items <<< "$gates"
  for item in "${items[@]}"; do
    if [[ "$item" == "$wave" ]]; then
      return 0
    fi
  done
  return 1
}

bq::print_ticket_order() {
  local from_wave="$1"
  local to_wave="$2"
  while IFS= read -r ticket; do
    [[ -n "$ticket" ]] && echo "$ticket"
  done < <(bq::tickets_for_wave_range "$from_wave" "$to_wave")
}
