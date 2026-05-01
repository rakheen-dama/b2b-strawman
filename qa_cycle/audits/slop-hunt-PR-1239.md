# Slop hunt — PR #1239: fix(OBS-2103): customer Edit/Archive button collision under React 19/Radix

**Batch**: B — Radix/hydration
**Reviewed**: 2026-05-01
**Verdict**: NEEDS-FOLLOW-UP

## PR description vs diff

Description names the bug accurately (adjacent `<DialogTrigger asChild>` + `<AlertDialogTrigger asChild>` colliding under React 19 + Radix Slot, dropping one button). Says the fix replaces both with `React.cloneElement(children, { onClick })` and adds a regression test. Diff matches: 167 additions / 6 deletions across 3 files (the two dialogs + a new test).

Test plan claims `pnpm run lint`, `pnpm run build`, full `pnpm test` (340 files / 2127 passed). Not narrowed. New test file `__tests__/customer-detail-action-row.test.tsx` is genuinely a regression guard — renders both dialogs adjacent (the bug-triggering shape) and asserts each opens the correct dialog.

QA browser checkboxes are unchecked. PR was merged anyway. The bug then re-opened as OBS-2103b within hours, fixed by PR #1242.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | HIGH | Fix at the wrong layer | `frontend/components/customers/edit-customer-dialog.tsx:128–144`, `frontend/components/customers/archive-customer-dialog.tsx:56–72` | The fix swaps Radix Slot for `React.cloneElement(children, { onClick })`. Per PR #1242's body, this still failed in production: "EditCustomerDialog children prop arrives as a lazy/RSC element where `children.props` is undefined, so `cloneElement` returns an element with default props only and the injected onClick disappears." So the fix relied on `children.props` being readable, which is not invariant for RSC payloads. The regression test passed because it constructed children with concrete props at test time. The bug surface (server-component-supplied children) was never exercised. | This PR was reverted-by-replacement in PR #1242, but the lesson sticks: `cloneElement` on consumer-supplied children that may originate from the RSC boundary is unsafe. The codebase's `frontend/CLAUDE.md` should explicitly call this out as an anti-pattern. |
| 2 | MEDIUM | Test that doesn't exercise the bug surface | `frontend/__tests__/customer-detail-action-row.test.tsx` (new) | The test renders `<EditCustomerDialog>` and `<ArchiveCustomerDialog>` adjacent with concrete `<Button>` children at test-time. The actual bug shape was that the consumer was a server component whose children prop arrived as a lazy/RSC element. Vitest + happy-dom doesn't go through the RSC boundary. The test is a genuine regression guard for the *Slot adjacency* bug class but **does not** guard the *cloneElement on lazy children* bug class — the latter is OBS-2103b, which only surfaced in the actual browser/RSC pipeline. This is the brief's category 5: "tests that pass without exercising the new behaviour." | Either (a) add a Playwright/E2E test that drives the real customer detail page in-browser (the only place where the RSC payload shape matters), or (b) document explicitly that vitest+jsdom cannot exercise this bug class. |
| 3 | MEDIUM | Manual QA gates skipped | PR description test plan | Three QA browser checkboxes (Sipho ACTIVE/ACTIVE, Moroka ACTIVE/PROSPECT, click-each-opens-correct-dialog) are unchecked. Per CLAUDE.md Quality Gate §3 ("PASS means observed"), this PR should have been marked MERGED-AWAITING-VERIFY. It was merged anyway. The reopen as OBS-2103b within hours is exactly what the gate exists to prevent. | The merge was technically permitted (the lockdown PR #1251 came later), but the gap is real: the agent inferred PASS from "code looks right + unit test green" — explicitly forbidden by Gate §3. Note for the bug-class doc. |
| 4 | LOW | Verbose comment block | `edit-customer-dialog.tsx:128–134` (and identical at archive) | 7-line OBS-2103 comment explaining the Slot adjacency collision. This level of inline commentary is fine for a tricky fix BUT the comment was authoritative ("Inject the open handler via React.cloneElement instead so the child renders as a plain `<Button>` with no Slot wrapper") — and was wrong about the fix being sufficient. Comment-as-documentation pattern goes stale fast. | When PR #1242 supersedes this code, ensure the OBS-2103 comment isn't carried forward verbatim. (PR #1242's diff confirms it was rewritten.) |

## v1/v2 root-cause check (#1239 → #1242)

**Verdict: PARTIAL fix, replaced by genuine fix in #1242.**

- #1239 correctly identified that adjacent `<*Trigger asChild>` siblings collide under React 19 + Radix Slot (this part of the diagnosis is right).
- #1239 chose `React.cloneElement(children, { onClick })` as the fix. This works in vitest but fails in the RSC pipeline because `children.props` is not always readable. So #1239 fixes one symptom (adjacent Slot collision) and introduces another (lazy children onClick-strip).
- #1242 chose the structurally correct fix: dialog owns the button. No Slot, no cloneElement, no consumer-supplied children. This is a genuine fix that addresses the root cause of *both* OBS-2103 and OBS-2103b. See `slop-hunt-PR-1242.md` for the v2 review.

## Audit-03 cross-check

The OBS-2103 fix pattern (cloneElement) was applied **only** to `edit-customer-dialog.tsx` + `archive-customer-dialog.tsx`. The 4 audit-03 suspects still carry `<*Trigger asChild>{children}</*Trigger>`:
- `frontend/components/rates/customer-rates-tab.tsx:276, 437, 558` — DialogTrigger asChild × 2 + AlertDialogTrigger asChild × 1
- `frontend/components/rates/project-rates-tab.tsx:256, 377` — DialogTrigger asChild + AlertDialogTrigger asChild
- `frontend/components/expenses/expense-list.tsx:329, 341, 369, 397, 414` — TooltipTrigger asChild × 4 + AlertDialogTrigger asChild × 1 (Tooltips are hover-only, lower risk; the AlertDialog one is risky)
- `frontend/components/comments/comment-item.tsx:108` — AlertDialogTrigger asChild (single, lower risk unless adjacent to another trigger)

If the rates tabs render multiple of these as adjacent siblings in the same JSX block, OBS-2103 / OBS-2103b risk is live. The pattern was not propagated. Per scope discipline (CLAUDE.md §7) this was correct for the PR's scope, but the bug class survives.

## Test scope check

- Full `pnpm test` (340 / 2127). Not narrowed.
- New test file mirrors the bug-triggering shape but uses concrete (non-RSC) children. Insufficient to catch OBS-2103b.

## Notes

The fast reopen (#1239 → #1242, same day) is the system working: cycle 17's day-45 retest caught the regression and forced the v2. But the cost of two PRs back-to-back is real: the regression test added in #1239 had to be amended in #1242. A reproduce-before-fix approach (loading the actual customer detail page in browser) would have caught the lazy-children path before #1239 merged.
