# Slop hunt — PR #1245: fix(OBS-2105): matter detail header layout collapse

**Batch**: C — customer UX
**Reviewed**: 2026-05-01
**Verdict**: CLEAN

## PR description vs diff

Description is one line — "Per qa_cycle/fix-specs/OBS-2105.md. Frontend CSS-only. Single file." That matches the diff exactly: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`, +3/-3, three CSS-class changes:
- Outer flex row: added `flex-wrap` for responsive wrapping.
- Title block: added `basis-[280px]` to set a wrap breakpoint.
- Action cluster: replaced `shrink-0 flex-wrap` with `flex-wrap items-center justify-end` — wrap remains, the dropped `shrink-0` is what allows wrapping at narrow widths, `justify-end` fixes alignment after wrap.

## Findings

No findings.

## Test scope check

- Pure CSS class change; no behaviour, no data, no routing, no JS logic. Frontend-only, lint/build/vitest is the right gate. PR description does not cite numeric verification, but the change is so small that the merge-gate hook (per PR #1251) would either pass or refuse via the marker file — and this PR predates the lockdown anyway.
- Surface area: the matter detail page header div at line 478. No other component imports the file. No cross-package risk.
- Visual-regression coverage is light in the codebase, so no test could meaningfully assert "header doesn't collapse on narrow viewport". A Playwright viewport-size snapshot would be the right tool, but that's a project-wide gap, not a PR-level one.

## Notes

- The dropped `shrink-0` in the action cluster is a substantive change: previously the cluster could overflow the parent rather than wrap; now it can shrink, which is what `flex-wrap` on the outer row needs to actually wrap. This is correct CSS.
- No AI smells — minimal, two-line comment-free CSS changes, and the comment block at lines 600-603 (already present) wasn't touched.
- Excellent example of a properly-scoped fix: the previous noise here ("flex shrink-0 flex-wrap items-center gap-2") had clearly been authored by an agent who didn't simulate the wrap on a narrow viewport — this is the right corrective response.
- No backend touch, no schema, no routing.
