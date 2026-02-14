#!/usr/bin/env bash
# Workflow Observatory — Layer 1: Event Logger
# Async hook that captures tool calls, agent lifecycle, and errors to JSONL.
# Receives JSON on stdin from Claude Code hooks system.
set -euo pipefail

INPUT=$(cat)
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")

SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // "unknown"')
HOOK_EVENT=$(echo "$INPUT" | jq -r '.hook_event_name // "unknown"')
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty')

# Log directory — per-session file
LOG_DIR="${CLAUDE_PROJECT_DIR:-.}/.claude/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/${SESSION_ID}.jsonl"

# Build the event record
EVENT=$(jq -nc \
  --arg ts "$TIMESTAMP" \
  --arg event "$HOOK_EVENT" \
  --argjson input "$INPUT" \
  '{
    timestamp: $ts,
    event: $event,
    tool_name: ($input.tool_name // null),
    tool_input: ($input.tool_input // null),
    tool_response: ($input.tool_response // null),
    error: ($input.error // null),
    session_id: ($input.session_id // null),
    transcript_path: ($input.transcript_path // null)
  }')

# Append atomically (>> is atomic for lines < PIPE_BUF on macOS/Linux)
echo "$EVENT" >> "$LOG_FILE"
