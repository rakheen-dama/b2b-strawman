---
name: phase_v2
description: Run an entire development phase hands-off. Uses a bash wrapper script that invokes /epic_v2 per slice with fresh context. Usage - /phase_v2 <phase-number> [starting-slice]
---

# Phase Orchestration v2

Run all remaining slices in a development phase **completely hands-off**. Each slice gets its own fresh `claude` invocation via an external bash loop — keeping each context well under the 1M ceiling, even for L-effort slices with large test suites and CodeRabbit iteration.

## How It Works

The orchestration is done by `scripts/run-phase.sh`, a bash script that:

1. Reads the phase task file (`tasks/phase{N}-*.md`)
2. Extracts the ordered slice list from the Implementation Order / Slices tables (rows starting with `| **NNNX** |`)
3. Skips slices already marked `**Done**`
4. For each remaining slice, invokes:
   ```
   claude -p "/epic_v2 {SLICE} auto-merge" --dangerously-skip-permissions --model opus
   ```
   The `/epic_v2` skill natively supports slice IDs (digits+letter → single-slice mode).
5. After each invocation, checks if that slice was marked Done in the task file
6. If Done → next slice. If not Done → stops (you investigate and restart)

Each `claude` invocation gets a **fresh context window**. No cross-slice context accumulation; no epic-level prompt-overflow failures.

## Usage

```bash
# Run all remaining slices in phase 10
./scripts/run-phase.sh 10

# Start from a specific slice (skips earlier ones even if not Done)
./scripts/run-phase.sh 10 84A

# Start from the first slice of an epic (e.g., 84 → starts at 84A)
./scripts/run-phase.sh 10 84

# Preview which slices will run (no execution)
./scripts/run-phase.sh 10 --dry-run
```

## Monitoring

```bash
# Watch progress in real-time
tail -f tasks/.phase-10-progress.log

# Check which slices are done
grep 'Done' tasks/phase10-invoicing-billing.md | head -20
```

## Resuming After Failure

If a slice fails (review found unfixable issues, build errors, etc.):

1. Check the log: `tail -50 tasks/.phase-10-progress.log`
2. Investigate and fix manually, or run `/epic_v2 {SLICE}` interactively
3. Once fixed and merged, restart: `./scripts/run-phase.sh 10`
   (it will skip all Done slices automatically)


## Differences from /phase (v1)

| Aspect | /phase (v1) | /phase_v2 |
|--------|-------------|-----------|
| Orchestrator | Claude agent (single session) | Bash script (external loop) |
| Unit of work per claude invocation | Whole phase | **Single slice** |
| Context limits | Hits ~75%, needs manual restart | Fresh 1M context per slice |
| Merge approval | Manual (asks user) | Auto-merge after review passes |
| Failure handling | Agent tries to recover in-session | Script stops, user restarts |
| Monitoring | Watch Claude output | `tail -f` on log file |
| Parallel slices | No | No (sequential is safer) |

## Why slice-level (not epic-level)

Earlier versions of this script grouped slices by epic and passed `/epic_v2 {EPIC}`, letting one invocation handle all slices in the epic as a single PR. That broke down for large epics (L-effort slices + 4674-test backend suite + CodeRabbit iteration) — a single `/epic_v2` invocation could exhaust its 1M context window before finishing review cycles. Slice-level invocation keeps each context comfortably bounded.

Trade-off: more PRs (one per slice instead of one per epic). If multi-slice bundling is desirable for a specific phase (e.g., tightly coupled DDL + code), invoke `/epic_v2 {EPIC}` manually outside the script.
