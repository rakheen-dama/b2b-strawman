#!/bin/sh
# .claude/hooks/post-merge-regression.sh
# PostToolUse hook for Bash — detects PR merges to main and reminds to run regression.
#
# Triggers when a Bash command contains "gh pr merge" or "git merge" targeting main.
# Returns additionalContext to prompt Claude to run /regression.

set -eu

INPUT=$(cat)

TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty')
[ "$TOOL_NAME" = "Bash" ] || exit 0

COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
[ -n "$COMMAND" ] || exit 0

# Check if this was a merge command
IS_MERGE=false
case "$COMMAND" in
  *"gh pr merge"*) IS_MERGE=true ;;
  *"git merge"*)
    # Only trigger if merging into main
    BRANCH=$(git -C "${CLAUDE_PROJECT_DIR:-.}" branch --show-current 2>/dev/null || echo "")
    case "$BRANCH" in
      main|master) IS_MERGE=true ;;
    esac
    ;;
esac

[ "$IS_MERGE" = true ] || exit 0

# Check if the merge succeeded (tool_response should indicate success)
EXIT_CODE=$(echo "$INPUT" | jq -r '.tool_response.exitCode // .tool_response.exit_code // "0"')
[ "$EXIT_CODE" = "0" ] || exit 0

# Check if we're on main now
CURRENT_BRANCH=$(git -C "${CLAUDE_PROJECT_DIR:-.}" branch --show-current 2>/dev/null || echo "")

# Check if E2E stack is running
E2E_UP=false
if curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
  E2E_UP=true
fi

if [ "$E2E_UP" = true ]; then
  cat <<'HOOK_JSON'
{
  "hookSpecificOutput": {
    "hookEventName": "PostToolUse",
    "additionalContext": "A PR was just merged to main. The E2E stack is running. Run the regression test suite now to catch any regressions: bash scripts/run-regression-test.sh. If there are failures, use /fix-tests to diagnose and fix them."
  }
}
HOOK_JSON
else
  cat <<'HOOK_JSON'
{
  "hookSpecificOutput": {
    "hookEventName": "PostToolUse",
    "additionalContext": "A PR was just merged to main. The E2E stack is NOT running. After the E2E stack is started (bash compose/scripts/e2e-up.sh), run the regression suite: bash scripts/run-regression-test.sh. If the stack needs a rebuild for the merged changes: bash compose/scripts/e2e-rebuild.sh frontend backend."
  }
}
HOOK_JSON
fi
