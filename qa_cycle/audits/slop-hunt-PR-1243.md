# Slop hunt — PR #1243: fix(OBS-2104c2): CherryPickStep — eliminate setState-in-render warning

**Batch**: B — Radix/hydration
**Reviewed**: 2026-05-01
**Verdict**: CLEAN

## PR description vs diff

Description: `toggleSection()` called `loadCustomerData()` inside the `setExpandedIds` updater. Under React 19 strict mode the updater can run twice during render, and `loadCustomerData` triggers `setCustomerData` synchronously + dispatches server actions that touch the Router cache, producing the "Cannot update a component (Router) while rendering a different component (CherryPickStep)" warning. Fix: resolve `isCurrentlyExpanded` synchronously **outside** the updater, then issue `loadCustomerData()` after the toggle commits.

Diff matches: 18 additions / 4 deletions, single file (`cherry-pick-step.tsx`), single function (`toggleSection`). The change is a clean separation of pure state-update from side-effect dispatch — the updater stays pure, the effect happens after `setExpandedIds` returns. No behaviour change, only side-effect placement. The diagnosis is plausibly correct (calling setState/server-actions from inside a state-updater function is a documented React 19 antipattern).

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | Verbose comment | `cherry-pick-step.tsx:157–165` | 9-line OBS-2104c2 comment explaining the React 19 strict-mode dual-invocation risk. Useful while the bug is fresh; will go stale. | When the bug-class doc is written (Task H from HANDOFF), move the React 19 strict-mode notes there and shorten the inline comment. |
| 2 | LOW | Targeted test only | PR description test plan | `pnpm test __tests__/billing-runs/cherry-pick-step.test.tsx — 6/6 pass`. Targeted, not full suite. Per CLAUDE.md Quality Gate §1 + §5, the merge bar is full `pnpm test`, not a narrowed run. The QA browser checkbox is unchecked. | This PR predates the lockdown gate (PR #1251), so the gap was technically allowed. Note for retrospect: targeted test scoping is exactly what §5 forbids; the agent should have run full vitest. |
| 3 | LOW | Test does not exercise the warning | (existing tests, not modified) | The 6 existing tests in `cherry-pick-step.test.tsx` were not updated to assert the absence of the strict-mode warning. The test plan explicitly defers verification to "QA: re-open Bulk Billing wizard step 3" (unchecked). So the change is uncovered: a future revert would not fail any test. | Add a test that wraps `<CherryPickStep>` in `<React.StrictMode>` and asserts the toggle works without console warnings (vitest can spy on `console.error`). |

## Test scope check

- **Targeted**, not full. `pnpm test __tests__/billing-runs/cherry-pick-step.test.tsx` only. Forbidden by Quality Gate §1/§5 once locked in (PR #1251), allowed under the historical regime when this PR landed (#1243 was merged before #1251).
- The new behaviour (the side-effect-after-commit ordering) is not exercised by any test. The regression risk is low because the function is small and the change is mechanical, but coverage is genuinely zero on this path.

## Notes

This PR is the smallest in the batch (18+/4-, 1 file, 1 function). It was not a drive-by — it stayed in scope. No "while I was here…" creep. The core change is correct and minimal.

The two real gaps are:
1. Targeted-not-full test scope (forbidden by the new gate; allowed at merge time).
2. No test asserts the absence of the warning — the change could regress silently.

Neither is HIGH-severity. The PR did one thing, did it cleanly, and didn't pretend to do more.
