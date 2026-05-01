# Slop hunt — PR #1230: fix(OBS-502): portal envelope counter shows accepted/total when COMPLETED

**Batch**: A — notifications
**Reviewed**: 2026-05-01
**Verdict**: CLEAN

## PR description vs diff

Description claims a "two-file portal change" with conditional rendering on `status === "COMPLETED"`. Diff matches exactly: 7 added / 3 removed lines across the two named portal files. No backend, no DTO, no migration. Verification claim ("pnpm lint / build / test green") is plausible for a 2-file portal-only change; targeting was appropriate (no need for a backend `mvnw verify` here).

This PR has nothing to do with the listener pipeline — it's a portal-side render fix only. Included in batch A because of bug-id naming, not by surface area. No notification work to audit.

## Findings

| # | Severity | Category | File:line | Finding | Suggested action |
|---|----|---|---|---|---|
| 1 | LOW | AI smell | portal/app/(authenticated)/requests/[id]/page.tsx:101–104 | The ternary inlined into the `<p>` produces awkward JSX whitespace ("{...}{" "}• status {detail.status}") with the `{" "}` between the ternary result and the bullet. It works, but the original single-line phrasing was clearer. | Optional refactor — extract to a helper `formatRequestProgress(detail)` next time this file is touched. Not worth a follow-up PR. |

## Test scope check

- Description says `pnpm test` green (42 files / 187 passed). The diff did not add a test for the new conditional. There is no portal regression test for "COMPLETED status renders accepted/total" — the change is small enough that QA browser-driven verification arguably substitutes, but the assertion is asymmetric: every other portal status path is untested for this counter as well, so this is not unique to PR #1230.
- For a 7-line render change, mandating a vitest is borderline. Calling this LOW.

## Notes

The bug class (firm/portal counter divergence on terminal state) is a generic display-only concern and does not interact with the listener-registration pattern that the rest of batch A touches. No cross-PR drift to flag from #1230.
