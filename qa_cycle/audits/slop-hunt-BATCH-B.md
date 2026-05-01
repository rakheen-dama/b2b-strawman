# Slop hunt — Batch B summary: React 19 / Radix asChild / hydration

**Reviewed**: 2026-05-01
**PRs**: #1231, #1234, #1239, #1242, #1243

## Per-PR verdicts

| PR | Title | Verdict | HIGH count |
|----|-------|---------|---|
| #1231 | OBS-704 hydration (v1) — useNowMs hook | NEEDS-FOLLOW-UP | 0 |
| #1234 | OBS-704 hydration (v2) — mount-gate CreateProposalDialog | NEEDS-FOLLOW-UP | 2 |
| #1239 | OBS-2103 customer Edit/Archive collision (v1) — cloneElement | NEEDS-FOLLOW-UP | 1 |
| #1242 | OBS-2103b refactor (v2) — dialog owns button | CLEAN (with NIT) | 0 |
| #1243 | OBS-2104c2 setState-in-render warning | CLEAN | 0 |

## Findings tally

| Severity | Count | Notes |
|----|----|----|
| HIGH | 3 | All in the OBS-704 v1+v2 pair (#1231 misdiagnosis carried forward; #1234 mount-gate workaround; #1239 lazy-children fragility — superseded by #1242 but the lesson stays). |
| MEDIUM | 4 | Mostly test-doesn't-exercise-behaviour, mixed Radix patterns, manual QA gates skipped. |
| LOW | 9 | Comments going stale, pattern not propagated, targeted-not-full test scope, ergonomic API trade-offs, dead UX state. |

## Top-3 patterns observed

1. **Mount-gate / useEffect-defer as a hydration "fix".** PR #1234 wraps `CreateProposalDialog` in `if (!mounted) return <>{children}</>`. This is the canonical hydration-mismatch antipattern — it doesn't make SSR and the first commit identical, it skips the problematic subtree on first paint and re-renders after `useEffect`. The brief explicitly flagged this pattern as a workaround signal. **Confirmed cover-up.**
2. **Misdiagnosis-then-fix without reproduction.** PR #1231 stamped the OBS-704 issue ID on a fix that addressed a different symptom (timestamp prop divergence). The actual hydration mismatch was an `aria-controls` injection, only identified two hours later in PR #1234. The reproduction-before-fix gate (`CLAUDE.md` §4) would have caught this — the agent did not run the page in a browser before writing the fix. PR #1234 then preserves #1231's hook as "defensive", so the codebase carries dead defensive code with the wrong issue ID stamped on it.
3. **Tests that pass without exercising the bug surface.** PR #1239's regression test rendered both dialogs adjacent with concrete (non-RSC) children — and passed. The actual bug shape was lazy/RSC children where `children.props` is undefined, which jsdom can't reproduce. PR #1234 has zero new tests. PR #1243 ran targeted vitest only. None of the v1 fixes in this batch had a test that would have caught their failure mode; only #1242 (v2) added a test approaching the bug surface (remount cycle).

## v1 / v2 root-cause verdicts

### Pair 1: #1231 → #1234 (OBS-704 hydration)
**Verdict: COVER-UP, both PRs.**
- #1231 misdiagnosed the root cause (timestamp prop, not Radix `aria-controls`). The `useNowMs` hook fixed nothing.
- #1234 correctly identified the real cause (Radix Slot's client-only `aria-controls`) but chose a workaround (mount-gate) instead of a structural fix. The mount-gate doesn't fix the mismatch — it hides it by skipping the problematic Radix tree on first paint and re-rendering after `useEffect`. The result: SSR for this dialog is neutered (no trigger wrapper rendered), and the underlying bug class lives on in 66 other `*Trigger asChild` declarations across the codebase.
- Recommended: open a v3 cleanup PR that (a) reverts #1231's `useNowMs` hook, (b) replaces the mount-gate with a deterministic-ID fix — pin `radix-ui` to a version where `aria-controls` is allocated via `useId()`, or pass an explicit `id` to the trigger so SSR and client agree. Do NOT use `suppressHydrationWarning`: that is itself a cover-up (silences the warning, leaves the mismatch). (c) audits other dialogs for the same hydration mismatch, (d) adds an SSR-snapshot test.

### Pair 2: #1239 → #1242 (OBS-2103 / OBS-2103b customer Edit/Archive)
**Verdict: GENUINE fix in #1242.**
- #1239 correctly identified the Radix Slot adjacency collision (right diagnosis) but chose `cloneElement(children, { onClick })` as the fix. This works in vitest+jsdom but fails in the RSC pipeline because `children.props` is not always readable from a lazy/RSC element. So #1239 fixed one symptom (Slot adjacency) and exposed another (lazy-children onClick-strip).
- #1242 chose the structurally correct fix: dialog owns the button. No Slot, no cloneElement, no consumer-supplied children. The dialog component renders its own `<Button>` and the consumer passes `triggerLabel` / `triggerVariant` / `triggerIcon` props. This eliminates both bug classes by elimination, not by detection.
- The v2 is a real refactor, not a workaround layered on v1. The cloneElement code is gone, not augmented. The diff shows real intent.

## Audit-03 status (the 4 suspect files)

The "dialog owns button" pattern was applied **only** to the customer Edit + Archive dialogs. The 4 audit-03 suspects still carry `<*Trigger asChild>` siblings at HEAD:

| File | Line(s) | Pattern |
|---|---|---|
| `frontend/components/rates/customer-rates-tab.tsx` | 276, 437, 558 | DialogTrigger asChild × 2 + AlertDialogTrigger asChild × 1 |
| `frontend/components/rates/project-rates-tab.tsx` | 256, 377 | DialogTrigger asChild + AlertDialogTrigger asChild |
| `frontend/components/expenses/expense-list.tsx` | 329, 341, 369, 397, 414 | TooltipTrigger asChild × 4 + AlertDialogTrigger asChild × 1 |
| `frontend/components/comments/comment-item.tsx` | 108 | AlertDialogTrigger asChild × 1 |

Total: 11 `*Trigger asChild` declarations across the four files. The OBS-2103 / OBS-2103b fix pattern was NOT propagated. **Bug class is dormant, not eliminated.**

- TooltipTriggers in `expense-list.tsx` are hover-only (no onClick), so the OBS-2103 click-loss class doesn't apply to those four hits.
- The two AlertDialogTriggers (`expense-list.tsx:369`, `comment-item.tsx:108`) and all five Dialog/AlertDialog triggers in the rates tabs are live regression risks if rendered as adjacent siblings.

Per audit-03's own action items, each is a 5-min eyeball check. None has been done. Recommended: open a scoped sweep PR that audits all four files for adjacency, then either (a) migrates each to dialog-owns-button, or (b) documents why the existing structure can't collide.

## NEEDS-FOLLOW-UP PRs

- **#1231** — revert the `useNowMs` hook OR re-document what bug class it actually guards (it's not OBS-704). LOW-effort.
- **#1234** — replace the mount-gate with a deterministic-ID fix (Radix version pin to a release that uses `useId()` for `aria-controls`, or explicit `id` prop on the trigger). Do NOT use `suppressHydrationWarning` — that just hides the warning. MEDIUM-effort. This is the highest-value follow-up in the batch — the mount-gate as shipped is a documented antipattern and will scale badly if copied.
- **#1239** — already superseded by #1242 in the codebase, but the failure mode (cloneElement on RSC children) needs a `frontend/CLAUDE.md` anti-pattern entry. LOW-effort.

## Recommendations (ordered by impact)

1. **Open the OBS-704 v3 PR.** The mount-gate workaround is the largest live slop in the batch. Concrete acceptance: SSR HTML and post-hydration HTML diff to zero on the proposals page (snapshot test).
2. **Sweep the 4 audit-03 files** for asChild adjacency. Either migrate to dialog-owns-button or document why each is safe. Concrete acceptance: a 1-line ESLint rule or a comment-per-trigger explaining adjacency safety.
3. **Codify "dialog owns button" in `frontend/CLAUDE.md`** as the canonical pattern for triggers that may render adjacent to other triggers. Reference PR #1242 and audit-03.
4. **Add an SSR-snapshot test harness** for the dialog component family. The fact that the entire OBS-704 v1/v2 cycle happened with zero SSR test coverage is the structural gap.

## Honesty notes

- Three of five PRs (#1231, #1239, #1243) shipped with manual QA browser checkboxes unchecked but were merged anyway. Per CLAUDE.md Gate §3 ("PASS means observed"), the correct status was MERGED-AWAITING-VERIFY, not VERIFIED. The lockdown PR #1251 came later and now blocks this exact pattern.
- #1242 also has unchecked QA boxes — the merge happened to be correct in retrospect (no OBS-2103c was filed), but the discipline gap was the same.
- #1243 used targeted vitest scope (single file), forbidden by §5. The change was small enough that risk was low; the principle still applies.

The single highest-leverage takeaway from this batch: **reproduce-before-fix would have prevented the #1231 misdiagnosis entirely**, and **a 5-line SSR snapshot test would have prevented #1234 from shipping a workaround**. The infrastructure to enforce both now exists (the merge gate + the verify markers); pre-merge agents must use it.
