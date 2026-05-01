# Slop hunt — PR #1229: fix(OBS-501): matter FICA Status Card link 404 → correct route

**Batch**: C — customer UX
**Reviewed**: 2026-05-01
**Verdict**: CLEAN

## PR description vs diff

Description honest and minimal. The diff is exactly:
- 1-line href change in `FicaStatusCard.tsx:88`: `/requests/` → `/information-requests/`.
- Existing test href assertion updated to match.
- New regression-guard test asserts the href contains `/information-requests/` and that it never reverts to `/requests/`.

PR mentions "matches the route the matter Requests-tab table already uses (`request-list.tsx:68`)" — that's a real cross-reference, not narrative.

## Findings

No findings.

## Test scope check

- PR description claims: full `pnpm test` (339 files / 2120 passed / 2 skipped), `pnpm run lint` (0 errors), `pnpm run build` clean. Full frontend gate per CLAUDE.md §1. Correct.
- Surface area: a single `<Link href=...>` URL string. The test for that component (`FicaStatusCard.test.tsx`) is the right surface; the regression-guard form (`expect(href).not.toMatch(/\/requests\//)`) is the right pattern to prevent re-introduction.
- No backend touch — no `./mvnw verify` needed.
- Cross-package risk: the URL string `/org/{slug}/requests/` was a 404, so anything that relied on that path was already broken; the change can only fix things, not break them.

## Notes

- Pure URL fix. No logic change. No new branches. No type changes.
- The "Test plan" section is a manual-test checklist (sign in as Bob, navigate to RAF-2026-001, etc.) — appropriate for a route-fix QA cycle entry.
- No AI smells — minimal comment, no over-engineering.
- This is the model of a clean small PR for the batch.
