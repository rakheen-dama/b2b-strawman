# Phase 91 — decisions log

Defaults and orchestrator calls made during the build. Founder rulings live in `tasks/phase91-ui-modernization-ii.md` (Founder decisions table). Newest last.

| ID | Date | Slice | Decision | Why | Status |
|---|---|---|---|---|---|
| D-1 | 2026-11-25 | — | The parallel backend-test PR (Surefire forks) is skipped and Phase 91 starts. | Founder answer at session start. Phase 91 has no backend verifies. | **FOUNDER** |
| D-2 | 2026-11-25 | 661A | One review-fix round on PR #1862 (reviewer 2, two MEDIUMs). Added F-661A-8: under A the rail loses 16 px of height, cutting the active item at 1024×768 collapsed. Wrote the dark-inset question as plain-English options (c)/(a)/(b), proposing (c) as the default (≈ 2 h in 661B). Two LOW wording fixes were folded into the same commit. | MEDIUMs are fixed in-PR. The LOWs cost no extra round. | DONE @2892c209a |
| D-3 | 2026-11-25 | 661A | CodeRabbit's 5 Minor comments and the reviewers' remaining LOWs were answered in their threads, not fixed. The scripts stay as executed. The carry-overs are listed in the PR #1862 comment "661B carry-overs" (tag the prototype SHA before deleting the branch; README reproduction addendum; F-661A-8 mechanism is the container `p-2`, not the card `m-2`; brand wording in README §8; repo-relative paths; the `accentButtonPaint` probe; unrounded ratios in `token-contrast.test.ts`). | Only HIGH/MEDIUM are fixed in-PR (founder 2026-11-22). | CARRIED → 661B |
| D-4 | 2026-11-25 | 661A | A founder-facing comparison page (claude.ai artifact) was built from 37 of the 661A PNGs. FD-1 is asked there and in chat. If only a scheme is named and it is A, dark inset defaults to (c), and K-21 stays in the backlog. | A visual pick is easier from one page than from 138 PR files. | CLOSED — FD-1 ruled "A1, (c)" 2026-11-25 |
| D-5 | 2026-11-25 | 661B | The prototype SHA `5caf1b74a` is tagged `archive/phase91-p1-scheme-prototype` before 661B deletes the branch (PR #1862 carry-over 1). | Keeps the 661A README's reproduction steps resolvable. | DONE |
