# Slop hunt — PR #1234: fix(OBS-704 v2): proposals dialog hydration — mount-gate CreateProposalDialog

**Batch**: B — Radix/hydration
**Reviewed**: 2026-05-01
**Verdict**: NEEDS-FOLLOW-UP

## PR description vs diff

Description acknowledges #1231 did not fix the symptom and identifies the real cause as Radix `DialogTrigger`'s `aria-controls="radix-_R_..."` attribute, which is allocated client-only and absent from SSR HTML. The fix wraps `CreateProposalDialog` in a `useEffect`-driven mount gate: pre-mount it returns `<>{children}</>` (no `DialogTrigger`); post-mount it renders the full Radix tree.

Diff matches description: adds `useEffect` + `[mounted, setMounted] = useState(false)` + an `if (!mounted) return <>{children}</>` early-return. 20 lines added, 1 deleted, single file. Description is honest about the trade-off ("the visible button is visually identical pre- and post-mount") and explicitly preserves #1231's hook as "defensive". It also flags the manual browser check as `[ ]` unchecked — but the PR merged anyway, repeating the #1231 mistake.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | HIGH | Workaround masquerading as fix | `frontend/components/proposals/create-proposal-dialog.tsx:88–97, 206–212` | The mount-gate is the canonical hydration-mismatch antipattern. It does **not** make SSR and the first client commit identical — it makes the SSR HTML and the *eventual post-effect* render identical, by skipping the `DialogTrigger` entirely on first paint and re-rendering after `useEffect`. Result: zero SSR benefit (the dialog trigger isn't in the SSR output, so screen readers / SEO crawlers / first-paint users see only the bare `{children}` button without ARIA wiring), AND a guaranteed double-render on every page load. The proper fix is one of: (a) `suppressHydrationWarning` on the trigger element for the one attribute Radix injects, (b) upgrade `radix-ui` to a version that allocates the content ID via `React.useId()` (deterministic across SSR/client) — this is fixed upstream in radix-ui, not by the consumer, (c) pass the dialog's `id` prop explicitly so Radix doesn't auto-generate one. | Replace the mount-gate with `suppressHydrationWarning` on the cloned trigger, or pin a `radix-ui` version where this is fixed upstream. The pattern as shipped will silently neutralise SSR for every dialog that gets the same treatment, and the comment in the code (`// SSR HTML and the first client commit produce identical HTML`) is technically false — the SSR HTML omits the trigger wrapper entirely. |
| 2 | HIGH | Bug class not addressed | `frontend/components/` (every other dialog) | OBS-704 manifested on the proposals index, but the same `aria-controls` mismatch class applies to **every** Dialog/AlertDialog/Popover/DropdownMenu in the app rendered from a server component. There are 67 `*Trigger asChild` declarations in `frontend/components/`. PR #1234 fixes one (the `CreateProposalDialog`). Either the bug class is global and we need a structural fix (point 1), or it's specific to this trigger and we need an explanation of why only this dialog hit it. The PR provides neither. | Investigate why only this dialog reproduced the warning. If it's global, the mount-gate approach scales linearly and badly — find the structural fix. If it's specific, document the trigger condition (e.g., what about the proposals page renders in a way no other page does). |
| 3 | MEDIUM | Mixed Radix patterns in same file | `frontend/components/proposals/create-proposal-dialog.tsx:267, 282` | The same component has a `PopoverTrigger asChild` that **does not** get the mount-gate treatment — only the outer `DialogTrigger` does. If the diagnosis ("Radix Slot allocates aria-controls client-only") were correct, `PopoverTrigger asChild` would have the same hydration issue but is left alone. This either disproves the diagnosis or proves the fix is incomplete. | Re-verify in the browser: does the console show an `aria-controls` warning for the `PopoverTrigger` too? If yes, the fix is incomplete. If no, the diagnosis is wrong. |
| 4 | LOW | Test does not exercise behaviour | (no new test) | No new test added. The "test plan" notes that "happy-dom flushes useEffect synchronously in act-wrapped renders" — i.e., the existing tests already mount past the gate, so they don't exercise the pre-mount branch. There is no test that asserts SSR HTML and post-hydration HTML match. The fix could regress and no test would catch it. | Add an SSR snapshot test (renderToString → hydrate → diff DOM) for at least one dialog. |

## v1/v2 root-cause check (#1234)

**Verdict: COVER-UP, not genuine fix.**

- Did v2 fix the root cause? **No.** The mount-gate hides the `aria-controls` mismatch by rendering a placeholder, not by aligning SSR and client output. The cited evidence is "SSR HTML and the first client commit produce identical HTML" — but only because the first commit also returns `<>{children}</>` (the same as SSR), then a `useEffect` triggers a remount with the actual Radix tree. The mismatch isn't resolved; it's deferred until after first paint. This is precisely what the slop-hunt brief calls out: "mount-gate / `if (!mounted) return null` patterns that hide hydration mismatch instead of fixing it."
- Did v2 revert v1? **No.** It explicitly preserves #1231's `useNowMs` hook ("defensive improvement"). So the codebase now carries: (1) a hook that fixed nothing (PR #1231's misdiagnosis), (2) a mount-gate that hides rather than fixes (PR #1234), and (3) the actual `aria-controls` bug class still present in 66 other dialogs.
- Audit-03 cross-check: this fix only touched `CreateProposalDialog`. The 4 audit-03 suspects (`customer-rates-tab.tsx`, `project-rates-tab.tsx`, `expense-list.tsx`, `comment-item.tsx`) were not touched. They still have `<*Trigger asChild>` siblings; if the OBS-704 class is global, they all carry the bug.

## Test scope check

- Full `pnpm test` reportedly run (2121/2121 + 2 skipped). Not narrowed.
- **The new behaviour is not exercised** by any test. The mount-gate's pre-mount branch is the load-bearing change and there is zero coverage of it (because happy-dom sync-flushes effects, the test always hits post-mount). This is the sixth slop category from the brief: "Tests that pass without exercising the new behaviour."

## Notes

This PR's existence is the strongest evidence that v1 (#1231) was a misdiagnosis. The fact that v2's proposed mechanism (mount-gate) is itself problematic compounds it. Recommend: open a v3 cleanup PR that (a) reverts #1231's `useNowMs` hook, (b) replaces the mount-gate with `suppressHydrationWarning` or a Radix version pin, (c) audits other dialogs for the same class, (d) adds an SSR-snapshot test for `CreateProposalDialog`.
