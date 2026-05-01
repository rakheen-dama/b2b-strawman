# Slop hunt — PR #1242: fix(OBS-2103b): Edit/Archive trigger refactor — dialog owns button

**Batch**: B — Radix/hydration
**Reviewed**: 2026-05-01
**Verdict**: CLEAN (with NIT)

## PR description vs diff

Description: refactor `EditCustomerDialog` + `ArchiveCustomerDialog` to render the trigger button **directly inside the dialog**. Adds `triggerLabel` / `triggerVariant` / `triggerSize` / `triggerClassName` / `triggerIcon` props. Removes both the OBS-2103 Slot collision risk AND the OBS-2103b lazy/RSC `cloneElement` onClick-strip. Updates call site at `customer/[id]/page.tsx`.

Diff matches description and is **proportionate**: 192 additions / 113 deletions across 6 files (2 dialogs + 1 call site + 3 test files). The refactor is a clear improvement: no Slot wrapper, no cloneElement, no consumer-supplied children. The dialog owns the button. The rationale is preserved as a multi-paragraph comment in `edit-customer-dialog.tsx` explaining the OBS-2103 → OBS-2103b → fix history.

Test file changes are real, not cosmetic: tests that previously passed `<button>` children now pass `triggerLabel="..."` props; the regression test gains a "remount cycle" case that's the closest thing to exercising the RSC boundary in jsdom. Lint / build / full test all reportedly green.

QA browser checkboxes unchecked. Merged anyway — same gate-skip pattern as #1239. The fact that no OBS-2103c re-opening happened is the only retrospective validation.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | Pattern not propagated | `frontend/components/customers/` (other dialogs) | The "dialog owns button" pattern is structurally correct and audit-03 explicitly flags it as the canonical replacement for `cloneElement(children, { onClick })`. But the pattern was applied only to Edit + Archive customer dialogs — the same file family has `link-project-dialog`, `anonymize-customer-dialog`, `data-export-dialog`, `create-customer-dialog` that still take `children` and clone (or `asChild`). If two of those ever render adjacent in a future page, OBS-2103-class regression. Per scope discipline (one fix per PR) this is correct for the PR; the follow-up is a separate scoped sweep. | Open a follow-up PR (or epic) to migrate the rest of the customer dialogs to the dialog-owns-button pattern, then propagate to rates/expenses (audit-03 files). The pattern is well-documented in the comment block here; codify it in `frontend/CLAUDE.md`. |
| 2 | LOW | API ergonomics regression | `frontend/components/customers/edit-customer-dialog.tsx:42–58`, `archive-customer-dialog.tsx:23–34` | The `triggerLabel` / `triggerVariant` / `triggerSize` / `triggerClassName` / `triggerIcon` quintet replicates the `<Button>` API surface as 5 props. If Button gains a new prop (e.g., `loading`), every dialog component using this pattern needs updating. Compare to `<DialogTriggerButton>{...}</DialogTriggerButton>` (named slot) which would forward the full Button API. Acceptable for two dialogs, but won't scale to 20. | If the pattern propagates, consider `triggerProps?: ButtonProps` instead of 5 separate props, OR a render-prop API. Defer until 3+ dialogs use it. |
| 3 | LOW | Comment stale risk | `edit-customer-dialog.tsx:148–158` | 11-line comment narrating the OBS-2103 → OBS-2103b → fix history is helpful right now but will go stale. The single-line `frontend/CLAUDE.md` entry recommended in `qa_cycle/audits/03-radix-aschild.md` (Action item 3) is the right home for this content. | Move the historical narrative to `frontend/CLAUDE.md`'s anti-patterns section; keep a 1-line comment in the code with the link/issue ID. |
| 4 | LOW | Manual QA gate skipped | PR description test plan | Two QA browser checkboxes (Sipho, Moroka) unchecked. Same pattern as #1239. The merge gate was not yet active (PR #1251 came later). | Note for retrospect: this PR happened to be correct, but the discipline gap was the same as #1239. |

## v1/v2 root-cause check (#1239 → #1242)

**Verdict: GENUINE fix, addresses the root cause.**

- Did v2 fix the root cause? **Yes.** "Dialog owns button" eliminates both:
  - OBS-2103: no Radix Slot wrapper around consumer-supplied children → no adjacency collision possible.
  - OBS-2103b: no `cloneElement(children, ...)` → no dependence on `children.props` being readable from the RSC payload.
- Did v2 revert v1? **Yes, structurally.** The cloneElement code from #1239 is gone. The new component shape is fundamentally different (props-driven button, not children-driven). This is a real refactor, not a workaround layered on top of #1239. The diff shows the lazy/RSC concern is resolved by elimination — the dialog component never touches consumer children, so it can't be confused by RSC-payload shape.
- Audit-03 cross-check: the fix pattern was NOT applied to the 4 audit-03 suspects. They still have `<*Trigger asChild>{children}</*Trigger>`:
  - `customer-rates-tab.tsx:276, 437, 558` (still asChild × 3)
  - `project-rates-tab.tsx:256, 377` (still asChild × 2)
  - `expense-list.tsx:329, 341, 369, 397, 414` (still asChild × 5; mix of Tooltip + AlertDialog)
  - `comment-item.tsx:108` (still asChild × 1)

The bug class is **dormant**, not eliminated. If any of these tabs render two `*Trigger asChild` siblings in the same flex/grid block, OBS-2103-class regression. Per audit-03 they need a manual eyeball pass, which this PR explicitly did not undertake (correctly — scope discipline).

## Test scope check

- Full `pnpm test` (340 / 2129). Not narrowed.
- The "remount cycle" test (`both dialogs operate independently after a remount cycle`) is the closest the test suite gets to exercising the bug surface. It still doesn't go through the RSC boundary. The remaining test gap (real RSC payload) needs a Playwright test if you want true coverage.
- Existing per-dialog tests (edit / archive) were updated to the new prop API — not deleted. Coverage preserved.

## Notes

This is the v2 we wanted. The fix is structural, the call site is clean, the tests evolved with the API. Two real outstanding gaps:
1. The bug class is not eliminated app-wide (audit-03 files still vulnerable).
2. The "dialog owns button" pattern needs to land in `frontend/CLAUDE.md` so future dialogs default to it.

Both are follow-ups, not flaws in this PR.
