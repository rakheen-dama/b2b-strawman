#!/usr/bin/env bash
# Workflow Observatory — Layer 2: Session Summarizer
# Runs at SessionEnd. Parses the JSONL event log + transcript to produce stats.
set -euo pipefail

INPUT=$(cat)
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // "unknown"')
TRANSCRIPT_PATH=$(echo "$INPUT" | jq -r '.transcript_path // empty')

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-.}"
LOG_DIR="$PROJECT_DIR/.claude/logs"
LOG_FILE="$LOG_DIR/${SESSION_ID}.jsonl"
INSIGHTS_DIR="$PROJECT_DIR/tasks/insights"
mkdir -p "$INSIGHTS_DIR"

# Skip if no event log exists (trivial session)
if [[ ! -f "$LOG_FILE" ]]; then
  exit 0
fi

TOTAL_EVENTS=$(wc -l < "$LOG_FILE" | tr -d ' ')

# Skip trivial sessions (fewer than 5 tool calls)
if [[ "$TOTAL_EVENTS" -lt 5 ]]; then
  exit 0
fi

# --- Compute stats from JSONL log ---

# Tool call counts by type
TOOL_COUNTS=$(jq -r 'select(.tool_name != null) | .tool_name' "$LOG_FILE" \
  | sort | uniq -c | sort -rn \
  | awk '{printf "{\"tool\":\"%s\",\"count\":%d}\n", $2, $1}' \
  | jq -sc '.')

# Error events (use -c for compact single-line output so wc -l counts records, not JSON lines)
ERROR_COUNT=$(jq -c 'select(.event == "PostToolUseFailure")' "$LOG_FILE" | wc -l | tr -d ' ')
ERROR_DETAILS=$(jq -c 'select(.event == "PostToolUseFailure") | {timestamp, tool_name, error}' "$LOG_FILE" \
  | head -20 | jq -sc '.')

# Bash command failures (subset of errors)
BASH_FAILURES=$(jq -r 'select(.event == "PostToolUseFailure" and .tool_name == "Bash") | .error' "$LOG_FILE" \
  | head -10 | jq -Rsc '[splits("\n") | select(length > 0)]')

# Agent spawns (Task tool calls)
AGENT_SPAWNS=$(jq -r 'select(.tool_name == "Task") | .tool_input.subagent_type // .tool_input.description // "unknown"' "$LOG_FILE" \
  | sort | uniq -c | sort -rn \
  | awk '{printf "{\"type\":\"%s\",\"count\":%d}\n", $2, $1}' \
  | jq -sc '.')

# Files modified (Edit + Write)
FILES_MODIFIED=$(jq -r 'select(.tool_name == "Edit" or .tool_name == "Write") | .tool_input.file_path // empty' "$LOG_FILE" \
  | sort -u | jq -Rsc '[splits("\n") | select(length > 0)]')

# Session timing
FIRST_TS=$(jq -r '.timestamp' "$LOG_FILE" | head -1)
LAST_TS=$(jq -r '.timestamp' "$LOG_FILE" | tail -1)

# --- Parse transcript for agent token usage (if available) ---
AGENT_USAGE="[]"
if [[ -n "$TRANSCRIPT_PATH" && -f "$TRANSCRIPT_PATH" ]]; then
  # Extract token usage from Task tool results (they contain <usage> tags)
  AGENT_USAGE=$(grep -o 'total_tokens: [0-9]*' "$TRANSCRIPT_PATH" 2>/dev/null \
    | awk '{print $2}' | jq -sc '[.[] | {tokens: .}]') || AGENT_USAGE="[]"
fi

# --- Parse build logs from /tmp/ (if any recent ones exist) ---
BUILD_STATS="{}"
RECENT_MVN_LOGS=$(find /tmp -name 'mvn-epic-*.log' -newer "$LOG_FILE" -mmin -120 2>/dev/null | head -5)
if [[ -n "$RECENT_MVN_LOGS" ]]; then
  MVN_BUILD_FAIL=0
  MVN_TEST_FAIL=0
  for log in $RECENT_MVN_LOGS; do
    if grep -q "BUILD FAILURE" "$log" 2>/dev/null; then
      MVN_BUILD_FAIL=$((MVN_BUILD_FAIL + 1))
    fi
    if grep -q "Tests run:.*Failures: [1-9]" "$log" 2>/dev/null; then
      MVN_TEST_FAIL=$((MVN_TEST_FAIL + 1))
    fi
  done
  BUILD_STATS=$(jq -nc --argjson bf "$MVN_BUILD_FAIL" --argjson tf "$MVN_TEST_FAIL" \
    '{maven_build_failures: $bf, maven_test_failures: $tf}')
fi

# --- Write stats JSON ---
DATE_PREFIX=$(date -u +"%Y-%m-%d_%H%M")
STATS_FILE="$INSIGHTS_DIR/${DATE_PREFIX}-${SESSION_ID:0:8}.stats.json"

jq -nc \
  --arg sid "$SESSION_ID" \
  --arg first "$FIRST_TS" \
  --arg last "$LAST_TS" \
  --argjson total "$TOTAL_EVENTS" \
  --argjson errors "$ERROR_COUNT" \
  --argjson tools "$TOOL_COUNTS" \
  --argjson error_details "$ERROR_DETAILS" \
  --argjson bash_failures "$BASH_FAILURES" \
  --argjson agents "$AGENT_SPAWNS" \
  --argjson files "$FILES_MODIFIED" \
  --argjson agent_usage "$AGENT_USAGE" \
  --argjson build "$BUILD_STATS" \
  '{
    session_id: $sid,
    started_at: $first,
    ended_at: $last,
    total_tool_calls: $total,
    error_count: $errors,
    error_rate_pct: (if $total > 0 then (($errors / $total) * 100 | . * 100 | round / 100) else 0 end),
    tool_counts: $tools,
    error_details: $error_details,
    bash_failures: $bash_failures,
    agent_spawns: $agents,
    files_modified: $files,
    agent_token_usage: $agent_usage,
    build_stats: $build
  }' > "$STATS_FILE"

# Symlink latest for easy access
ln -sf "$STATS_FILE" "$INSIGHTS_DIR/latest.stats.json"
