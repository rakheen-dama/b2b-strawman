# Backend Quality Toolkit

This folder reduces the manual steps needed to execute the backend quality backlog in parallel worktrees with Codex or Claude.

## Files

- `tickets.tsv`: source data for ticket metadata and prompts
- `lib.sh`: shared parsing, state, dependency, and path helpers
- `create-worktrees.sh`: creates one worktree per ticket
- `print-prompt.sh`: prints an agent-ready prompt for a ticket
- `run-ticket.sh`: runs a single ticket in its worktree with Codex or Claude
- `run-program.sh`: orchestrates the whole backend improvement effort wave by wave
- `status.sh`: prints current orchestrator state
- `monitor.sh`: live dashboard for orchestrator progress and ticket logs
- `list-tickets.sh`: prints ticket summaries and dependencies

## One Command

Run the program manager for the full effort:

```bash
./scripts/backend-quality/run-program.sh --agent codex
```

Safer first run:

```bash
./scripts/backend-quality/run-program.sh --agent codex --from-wave 1 --to-wave 1
```

The manager will:

1. Create worktrees as needed
2. Launch one worker run per ticket
3. Track status in `.backend-quality/state.json`
4. Stop at review gates by default after waves `1`, `2`, and `4`
5. Print the exact resume command when human review is needed

## Resume After Review

```bash
./scripts/backend-quality/run-program.sh --approve-wave 1 --resume --agent codex
```

## Useful Commands

List backlog:

```bash
./scripts/backend-quality/list-tickets.sh
```

Run a single ticket:

```bash
./scripts/backend-quality/run-ticket.sh BE-001 codex
```

Show current status:

```bash
./scripts/backend-quality/status.sh
```

Live monitor:

```bash
./scripts/backend-quality/monitor.sh
```

Dry-run the full program:

```bash
./scripts/backend-quality/run-program.sh --agent codex --dry-run
```

## Notes

- One ticket per branch per worktree.
- Default review gates are intentionally conservative.
- Use `--no-review-gates` only if you truly want a fire-and-forget run.
- `codex` runs via `codex exec --full-auto`.
- `claude` runs via `claude -p --permission-mode bypassPermissions`.
- State and logs live under `.backend-quality/` in the repo root.
