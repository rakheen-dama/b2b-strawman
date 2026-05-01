# Slop hunt — PR #1231: fix(OBS-704): proposals index hydration mismatch

**Batch**: B — Radix/hydration
**Reviewed**: 2026-05-01
**Verdict**: NEEDS-FOLLOW-UP

## PR description vs diff

The description claims the root cause was `now={new Date().getTime()}` flowing through SSR + hydration with divergent timestamps. The diff matches what is described — adds `useNowMs()` hook, drops the `now` prop from `ProposalTable`, returns `0` until mount. Test plan ticks lint/build/full `pnpm test` (339 files / 2120 tests). Not narrowed.

The honesty problem is downstream, not in this PR's text: PR #1234 (the v2) explicitly says this fix did **not** clear the actual hydration error and that "the real culprit is Radix `DialogTrigger`'s `aria-controls`". PR #1231's "Root cause" section is therefore wrong in retrospect. The PR was merged ~2 hours before #1234 reopened OBS-704 with a different root cause.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | MEDIUM | Misdiagnosed root cause | `frontend/components/proposals/proposal-table.tsx:37`, `frontend/hooks/use-now-ms.ts` | PR #1234 establishes that the `now` prop was not the actual hydration cause. The `useNowMs` hook + the `proposal.sentAt && now > 0` guard in the cell renderer were a workaround for a non-existent mismatch (the real one was `aria-controls` on `DialogTrigger`). The hook is now defensive code with no proven invariant — the only call site is `ProposalTable` itself. PR #1234's body even concedes "PR #1231's `useNowMs` hook for `ProposalTable` is preserved — it remains a defensive improvement". That is the textbook definition of cargo-culting a fix that did not address the reported symptom. | Either (a) revert the `useNowMs` hook + restore the `now` prop, or (b) keep the hook but explicitly document that this is preventative coverage for a future hydration class, not the OBS-704 fix. Don't carry around dead defensive code with the wrong issue ID stamped on it. |
| 2 | LOW | Dead UX state | `frontend/components/proposals/proposal-table.tsx:93–98` | The "Days Since Sent" column renders `—` for the entire SSR + first-paint window because `now > 0` is the guard. A user on a slow connection will see dashes flash to numbers post-hydration even though the server already knows `sentAt`. The server could have rendered the relative day count directly (it has the timestamp); the prop-based approach was actually fine — the divergence was tiny and could have been resolved by `suppressHydrationWarning` on that one cell, or by computing days on the server using a fixed-resolution clock. | If the hook stays, accept the flash. If the original `now` prop is reinstated, the UX is better. Document the trade-off either way. |
| 3 | LOW | Lint disable comment | `frontend/hooks/use-now-ms.ts:13` | `// eslint-disable-next-line react-hooks/set-state-in-effect -- SSR hydration: client-only timestamp...` — the inline justification is correct, but this is the exact pattern that triggered OBS-2104c2 in CherryPickStep. Two different one-liners with the same shape, neither linked to a shared note. | When the bug-class doc is written (Task H), add this hook as a documented exemption with the reasoning. |

## v1/v2 root-cause check

(Cross-reference for PR #1234 — see `slop-hunt-PR-1234.md`.) PR #1231 misdiagnosed the root cause; v2 in #1234 papered over the actual hydration mismatch with a mount-gate. **Both PRs together fail the "fix at the right layer" test**: the proper fix is to use `suppressHydrationWarning` on the `aria-controls` attribute (a known Radix pattern documented in their issue tracker) or upgrade Radix to a version that emits the controls ID via `useId()`. Instead the codebase now has both a useless hook (#1231) AND a mount-gate workaround (#1234).

## Test scope check

- Full `pnpm test` (339 files / 2120 tests). Not narrowed.
- The two updated tests in `__tests__/proposals-dashboard.test.tsx` only assert the new prop shape (drops `now`); they do **not** assert the hydration mismatch is gone. There is no test that renders the page in SSR + client and diffs HTML — which is the only test that would have caught the misdiagnosis.
- Manual browser check is on the test plan as `[ ]` (unchecked). The PR was merged anyway. PR #1234 then proves the manual check would have failed.

## Notes

This PR is the canonical example of "diagnostic-by-spec" / "fix-and-pray" forbidden by `CLAUDE.md` Quality Gate §4 ("Reproduce-before-fix"). The agent did not reproduce the hydration mismatch in the browser; if they had, the `aria-controls` warning text would have been visible in the console and the misdiagnosis would have been impossible. The 2-hour gap between #1231 merge and #1234 reopen is the cycle time of the missing reproduction step.
