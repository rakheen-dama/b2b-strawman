# Phase 8 Handover — Resume from Slice 67D

## What's Done (3/17 slices)

| # | Slice | PR | Summary |
|---|-------|----|---------|
| 1 | 67A | #133 | V19 migration (org_settings, billing_rates, cost_rates, project_budgets), OrgSettings entity/repo/service/controller |
| 2 | 67B | #134 | BillingRate entity, 3-level resolution hierarchy (org→customer→project), repository, service, 19 tests |
| 3 | 67C | #135 | CostRate entity (member-level only), repository, service, 8 tests |

## Remaining Slices (14 remaining)

| # | Slice | Name | Scope | Deps |
|---|-------|------|-------|------|
| 4 | **67D** | BillingRate + CostRate controllers + integration tests | Backend | 67B, 67C |
| 5 | **68A** | Settings rates page (org-level billing/cost rates, currency) | Frontend | 67D |
| 6 | **68B** | Project + customer rate override tabs | Frontend | 67D |
| 7 | **69A** | V20 migration, TimeEntry rate snapshot columns, snapshot-on-create | Backend | 67B, 67C |
| 8 | **69B** | PATCH billable endpoint, billable filter, re-snapshot admin | Backend | 69A |
| 9 | **70A** | Billable checkbox in LogTimeDialog, rate preview, billable filter | Frontend | 69B |
| 10 | **71A** | ProjectBudget entity, service, controller, budget status | Backend | 69A |
| 11 | **73A** | Project + customer profitability endpoints, ReportService | Backend | 69A |
| 12 | **71B** | BudgetCheckService, BudgetThresholdEvent, notification integration | Backend | 71A |
| 13 | **73B** | Utilization + org profitability endpoints, access control | Backend | 73A |
| 14 | **72A** | Budget tab on project detail page | Frontend | 71B |
| 15 | **74A** | Profitability page (sidebar nav, utilization, project table) | Frontend | 73B |
| 16 | **74B** | Project financials tab + budget panel integration | Frontend | 72A, 73A |
| 17 | **74C** | Customer financials tab + customer profitability | Frontend | 73A, 68B |

## How to Resume

Run: `/phase 8 from 67D`

Or manually:
1. The `/phase` skill at `.claude/skills/phase/SKILL.md` contains the full orchestration workflow
2. Task file: `tasks/phase8-rate-cards-budgets-profitability.md`
3. Architecture doc: `architecture/phase8-rate-cards-budgets-profitability.md`
4. ADRs: `adr/ADR-039` through `ADR-043`

## Workflow Per Slice (Scout → Builder Pipeline)

1. Clean stale worktrees: `git worktree list | grep "worktree-epic-{SLICE}"`
2. Create worktree: `git worktree add ../worktree-epic-{SLICE} -b epic-{SLICE}/{branch}`
3. Dispatch scout agent (general-purpose) → writes `.epic-brief.md` to worktree
4. Dispatch builder agent (general-purpose) → reads brief, implements, creates PR
5. Download diff: `gh pr diff {N} > /tmp/pr-{N}.diff`
6. Dispatch reviewer agent (general-purpose, sonnet) → reads diff + CLAUDE.md
7. If issues: dispatch fixer agent → fix, push
8. Merge: `gh pr merge {N} --squash --delete-branch`
9. Cleanup: `git worktree remove ../worktree-epic-{SLICE} --force && git pull --rebase origin main`
10. Update status in `TASKS.md` and `tasks/phase8-rate-cards-budgets-profitability.md`

## Key Lessons from This Session

- **False positive reviews**: Reviewers sometimes flag issues in files OUTSIDE the PR diff. Always verify findings are in-scope.
- **JPQL respects @Filter**: Only `EntityManager.find()` (via `findById()`) bypasses Hibernate @Filter. JPQL queries always respect it. Don't let reviewers flag JPQL as unsafe.
- **Local main divergence**: After squash merges, local main can diverge. Use `git reset --hard origin/main` if `git pull --ff-only` fails.
- **Worktree branch deletion error**: `gh pr merge --delete-branch` fails if worktree still uses the branch. Always remove worktree FIRST, then merge, OR merge first and handle the error by cleaning up worktree afterward.
- **Build pass rate**: All 3 slices had green builds on first or second attempt. The brief quality is high.
