# Slop hunt — PR #1232: fix(OBS-702): proposal expiry timezone drift

**Batch**: A — notifications
**Reviewed**: 2026-05-01
**Verdict**: NIT

## PR description vs diff

Description is honest. It explains the bug (UTC end-of-day crossed the date line east of UTC), the fix (encode local end-of-day → ISO), and the new helper `formatProposalExpiresAt`. Diff matches: 4 files (`create-proposal-dialog.tsx`, `lib/format.ts`, `proposals/[id]/page.tsx`, test). The `formatProposalExpiresAt` helper is acknowledged as "functionally identical to formatDate today" — the PR author is upfront that this is signalling intent rather than fixing behaviour.

Out-of-scope items (other date submission sites) are explicitly deferred — good discipline.

This PR has nothing to do with the listener pipeline. Included in batch A by ticket-id locality, not surface area.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | MEDIUM | Workaround / partial fix | frontend/components/proposals/create-proposal-dialog.tsx:173–178 | The local-end-of-day fix is applied at the **dialog** site, not at a shared helper. The PR description acknowledges "Audit of every other Input type=date submission site is a follow-up" — meaning the same `${date}T23:59:59Z` bug-pattern almost certainly lives at other call-sites (other proposal-edit flows, retainer-end, deadline forms, etc.). The fix-spec admits this. The inline IIFE is a code smell; the right shape is a `lib/format.ts#localEndOfDayIso(yyyyMmDd)` helper. | Open a follow-up to grep `T23:59:59Z` and `T00:00:00Z` across `frontend/` + `portal/` and migrate every site to a shared helper. Track as a real bug, not "follow-up". |
| 2 | LOW | AI smell — redundant abstraction | frontend/lib/format.ts:110–122 | New `formatProposalExpiresAt` is defined as a wrapper that is "functionally identical to formatDate today" (PR description). It exists only to "give a single insertion point if the formatter ever needs to diverge". This is YAGNI. The `proposals/[id]/page.tsx:183` change just routes through it. | Either delete it and call `formatDate(proposal.expiresAt)`, or actually diverge it (e.g. force the rendering zone to be the org's primary zone, not the viewer's, since this is an authoritative date picked by the firm). Don't leave a synonym in the codebase. |
| 3 | LOW | Test scope | frontend/components/proposals/__tests__/create-proposal-dialog.test.tsx:96–168 | The test asserts the captured payload decodes to local May-12 — but the IIFE constructs the date using the **test runner's local zone**, so this only verifies the bug in whichever zone CI runs in. If CI runs in UTC, the assertion still passes for the buggy `T23:59:59Z` form (in UTC, `23:59:59Z` decodes to date=12). The "belt-and-braces" `expect(payload.expiresAt).not.toBe("2026-05-12T23:59:59Z")` is the only assertion that is zone-independent — and that's a string-form check, not a behavioural check. | Add an explicit timezone via `vi.stubGlobal` / `process.env.TZ = 'Africa/Johannesburg'` in `beforeEach` so the test is deterministic across CI environments. Otherwise the test could go green in a UTC runner while the bug re-regresses for SAST users. |

## Test scope check

- Description claims `pnpm test → 339 files / 2121 tests pass` and `pnpm run lint`/`pnpm run build` green. Plausible for a frontend-only fix.
- A new vitest was added (good). However the test is timezone-fragile (Finding 3).
- Manual QA Day 7 re-verification is checked off as `[ ]` (unchecked) in the PR body — consistent with "MERGED-AWAITING-VERIFY" semantics. This is acceptable per the rules but worth flagging that the PR was merged without the manual leg.

## Notes

The bug-class here ("convert local-picked YYYY-MM-DD to ISO") is a known ergonomic foot-gun. The test guards against the literal previous string form but not against future variants (`new Date('YYYY-MM-DDTHH:mm:ss').toISOString()` does the same thing in some browsers). A `localEndOfDayIso` helper with a unit test that pins TZ would be the durable fix.
