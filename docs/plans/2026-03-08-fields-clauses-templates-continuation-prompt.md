# Continuation Prompt — Fields-Clauses-Templates Integration

Copy everything below the line as the prompt for a fresh Claude Code session.

---

Continue implementing the fields-clauses-templates integration plan. Previous sessions completed Tasks 1-8. You need to:

1. **First**, read the handover doc at `docs/plans/2026-03-08-fields-clauses-templates-handover.md` for full context on what's done, branch state, and architecture
2. **Then**, read the full plan at `docs/plans/2026-03-08-fields-clauses-templates-integration.md` for remaining task details

## What's Done
- Tasks 1-5, 7-8 (GAP-1 through GAP-5, GAP-9, GAP-7) — all P0, P1, and most P2 gaps resolved
- Branch: `feat/fields-clauses-templates-integration` in worktree `.worktrees/fields-clauses-templates-integration`
- 11 commits, all tests passing
- Task 8 review was dispatched at end of last session — check results first

## What's Left

**Check Task 8 reviews first** — if issues were found, fix them before proceeding.

Then implement remaining tasks in order:
- **Task 10**: Inline Missing-Data Indicators (UX-1) — React context + amber variable pill styling
- **Task 11**: Template Editor Live Preview (UX-5) — entity picker + client-side render preview

Tasks 12-14 (P3) can be deferred.

## Workflow

Use `/superpowers:subagent-driven-development`. Per task:
1. Dispatch implementer subagent (general-purpose) with full task text from plan
2. Dispatch spec reviewer subagent
3. Dispatch code quality reviewer subagent (superpowers:code-reviewer)
4. Fix issues, re-review
5. After all tasks: use `/superpowers:finishing-a-development-branch` to create PR

Work from worktree: `.worktrees/fields-clauses-templates-integration`

All changes go on branch `feat/fields-clauses-templates-integration`. PRs must be reviewed with valid comments addressed.
