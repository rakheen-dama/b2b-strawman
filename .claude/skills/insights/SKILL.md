---
name: insights
description: Analyze a workflow session and produce an insight report. Run after /phase, /epic, or any significant workflow. Usage - /insights [session-id|latest]
user_invocable: true
---

# Workflow Insights Generator

You are a workflow analyst. Your job is to read raw session stats and event logs, then produce a clear, actionable insight document for the developer.

## Step 1 — Locate the Stats File

If the user provided a session ID, look for:
```
tasks/insights/*-{session-id-prefix}.stats.json
```

Otherwise, read the latest:
```
tasks/insights/latest.stats.json
```

Use the Read tool to load the stats JSON.

## Step 2 — Load Raw Event Log (if deeper analysis needed)

The stats JSON contains `session_id`. Use it to find the raw log:
```
.claude/logs/{session_id}.jsonl
```

Read this file to understand the sequence of events — what tools were called, in what order, where errors occurred.

## Step 3 — Analyze and Produce Report

Write a markdown report to `tasks/insights/{date}-report.md` with this structure:

```markdown
# Workflow Insight Report — {date}

**Session**: `{session_id}`
**Duration**: {start} → {end} ({elapsed})
**Total Tool Calls**: {N} | **Errors**: {N} ({rate}%)

## Summary
<!-- 2-3 sentence overview of what this session accomplished -->

## What Went Well
<!-- Bullet points with evidence from the data -->
- Example: "Zero build failures across N Maven builds"
- Example: "Agent token efficiency: average {N}k tokens per subagent"

## What Could Be Better
<!-- Bullet points with specific, actionable suggestions -->
- Example: "3 Bash failures were retry-related — consider adding retry logic to hook X"
- Example: "Agent Y consumed 85k tokens — brief was likely too large"

## What Didn't Work
<!-- Only include if there were actual failures -->
- Example: "Build failed 2x due to missing dependency — required manual intervention"

## Stats

### Tool Usage
| Tool | Calls | Errors |
|------|-------|--------|
<!-- From tool_counts and error_details -->

### Subagent Performance
| Agent Type | Spawns | Avg Tokens |
|------------|--------|------------|
<!-- From agent_spawns and agent_token_usage -->

### Build Results
<!-- From build_stats — Maven/pnpm pass/fail -->

### Files Modified
<!-- Count + list from files_modified -->

## Raw Data
- Stats: `tasks/insights/{file}.stats.json`
- Event log: `.claude/logs/{session_id}.jsonl`
```

## Guidelines

- **Be specific** — cite actual numbers, tool names, error messages
- **Be actionable** — every "could be better" item should suggest a fix
- **Be honest** — if the session was clean, say so briefly; don't manufacture problems
- **Compare to baselines** — if previous reports exist in `tasks/insights/`, note trends
- **Keep it concise** — aim for 40-80 lines, not a novel
