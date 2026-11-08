# PR #1751 final head `6859a3f17`: targeted functional e2e check

**Verdict: PASS. No regression from the combobox/IME change in `mention-textarea.tsx`.**

- **legal-lifecycle: 105 passed / 0 failed / 0 skipped / 0 flaky** (`--ignore-snapshots`). Matches the expected 105.
- **`projects/project-tasks.spec.ts`: 9 passed / 0 failed / 0 skipped / 0 flaky** (`--ignore-snapshots`). Cases 1–9 all passed.
- Failures to classify: **none**. (a) regressions: 0. (c) environmental: 0.

## Scope

- Source: detached worktree `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4` at `6859a3f17032e364d477efa754d8a2e6cced4f00`. `pwd` was confirmed, and the tree was clean at the start (`dirty=0`, see `raw/timeline.txt`).
- `git diff --stat f3c191bb7 6859a3f17` shows only `frontend/components/comments/mention-textarea.tsx` (86 lines changed) and `mention-autocomplete.test.tsx` (88 lines changed); in total 148 insertions and 26 deletions. The full gate already passed on `f3c191bb7` (`../README.md`), so this is a functional check only. Screenshot baselines are ignored.

## Recipe

This reuses `../README.md` and `../raw/lib.sh`. The only changes are `RAW` and `W` (`raw/lib.sh`, driver `raw/run.sh`).

- Node v20.11.1 first on PATH. Both runs log `node=v20.11.1 envnode=v20.11.1`.
- `NODE_OPTIONS=""`; pnpm is `/opt/homebrew/bin/pnpm`; no `--` before the Playwright flags.
- ONE fresh stack: `e2e-down.sh && SHELL=/bin/bash e2e-up.sh`, nohup-detached, with `caffeinate -dims` held.
- Then, sequentially on that stack:
  1. `pnpm test:e2e:legal-lifecycle --reporter=list,json --ignore-snapshots`
  2. `pnpm test:e2e e2e/tests/projects/project-tasks.spec.ts --reporter=list,json --ignore-snapshots`
- Polling loops were each under 9 minutes.

## Timing

- Start: **02:33:26 SAST** on 2026-11-08. That is after 02:00, so the UTC and SAST dates agree and no wait was needed. This is outside the G89-F66 date-window.
- Load average at start was 4.13 / 5.47 / 6.82, so there was no competing Maven verify (0 maven/surefire processes).
- Stack build: 02:33:26 → 02:34:51, rc 0 on the first attempt. G89-F46 (fonts) did not occur.
- Lifecycle: 02:34:52 → 02:37:43, rc 0.
- project-tasks: 02:37:43 → 02:38:02, rc 0.

## Stack (`raw/stacks.txt`)

- HEAD was `6859a3f17`. Health: backend `UP`, frontend / mock-idp / mailpit all 200. `E2E Boot-Seed Complete!` was logged.
- Images:
  - Backend `0e4a0d42ab62`. This is the same as the `f3c191bb7` stacks, which is expected because the backend is unchanged.
  - Frontend `08c9c3692514`. This is a new image: `pnpm --filter frontend build` re-ran after `COPY frontend/`.
- Compose working_dir: `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/compose`.
- Flyway: tenant `14|g89 contains trust data`, global `5|portal strip matter type suffix`. Tenant `tenant_7d218705360b`.
- Pristine state: `Website Redesign|ACTIVE` only; `portal_deadline_view=0`.
- **Bundle proof that the tested build contains the new code.** In the e2e-frontend container, chunk `/app/frontend/.next/static/chunks/3-ma4nt15l458.js` (the one holding `Mention a member`) contains `role:"combobox"`, `aria-haspopup":"listbox` and `isComposing`.

## Specs that drive the textarea

| Spec | How it uses the textarea | Result |
|---|---|---|
| `legal-lifecycle/day-07-14-work-trust.spec.ts:98` "Bob: Add internal comment on the Sipho letter-of-demand task" | `sheet.getByLabel("Add a comment").fill(…)`, Visibility INTERNAL, then Post Comment. It asserts the comment is visible and the textarea is cleared. | **passed** |
| `projects/project-tasks.spec.ts:277` case 8 "Comment notification deep-links to the comment in the task sheet (G88-M6)" | It posts an `@Carol please review` task comment, then follows the notification deep link to the comment in the task sheet. | **passed** |
| `projects/project-tasks.spec.ts:322` case 9 "Matter comment notification deep-links to the highlighted comment on the Comments tab (B7)" | `panel.getByLabel("Add a comment").fill("@Carol matter thread <run>")`, then Post Comment. It asserts the comment row is present and the deep link highlights it. | **passed** |

- The `getByLabel("Add a comment")` locators still resolve now that the element is `role="combobox"`. The textarea keeps its label as its accessible name.
- Case 9 types an `@`-mention through the new combobox and posts. So the listbox and the IME guard did not block Enter or click-to-post.

### DB after both runs (`raw/stacks.txt`)

```
TASK|INTERNAL|INTERNAL|Bob Admin|babfd6ab-…|Reviewed claim documents — need additional medical records from Dr. Nk…
TASK|INTERNAL|INTERNAL|Alice Owner|58f64441-…|@Carol please review s95i
PROJECT|SHARED|INTERNAL|Alice Owner|fc85521c-…|@Carol matter thread s95i
COMMENT_ADDED|Carol Member|TASK|58f64441-…|Alice Owner commented on task "Deep Link Task s95i"
COMMENT_ADDED|Carol Member|PROJECT|fc85521c-…|Alice Owner commented on matter
```

- This has the same shape as the `f3c191bb7` gate.
  - The mention resolves to Carol only.
  - A PROJECT comment is stored as `SHARED`.
  - Bob's lifecycle task comment produces no `COMMENT_ADDED` row.
- Backend log: **0** WARN/ERROR lines mention comment or mention.

### Frontend container log (`raw/frontend-container.log`, `raw/frontend-stream-error-timestamps.txt`)

- 9 × `⨯ Error: The destination stream closed early.` (digest `1054653889`), and no other error lines.
- Mapped against each test's start and duration:
  - 3 fall in "Settings — set brand colour";
  - 2 fall in "Legal modules enabled";
  - 3 fall in "Record a R500 trust fee transfer";
  - 1 falls in project-tasks case 9.
- This is the aborted-RSC-stream-on-navigation class already observed in `../../../phase89-rewalk/634C/OBSERVATION.md:125`.
- Most of these occurrences are in flows the PR does not touch. Every test where one occurred passed.
- Not a failure. It needs no classification.

## Teardown and environment left behind (`raw/e2e-down.txt`)

- `bash compose/scripts/e2e-down.sh` ran at 02:38:46–02:38:49 SAST. It printed "Stack stopped. All containers and volumes removed." Afterwards 0 `e2e-*` containers and 0 `docteams-e2e` volumes remain.
- The lifecycle run wrote 16 untracked curated PNGs under `documentation/screenshots/legal-vertical/`. I archived them to `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751-final/lifecycle-curated-screenshots.tgz` and then removed them. No tracked file was modified.
- The worktree is at `6859a3f17` with `git status` empty. `caffeinate` was stopped. Nothing was committed.

## Raw files (`raw/`)

- `lifecycle.{json,tsv,txt}` and `project-tasks.{json,tsv,txt}`. These are the Playwright JSON and list output; the TSV columns are file, line, title, status, last-result.
- `stacks.txt`: stack checks, images, Flyway, the bundle proof, comment and notification rows, and the log scans.
- `timeline.txt`, `stack-S.log` and `driver.log`.
- `frontend-container.log` and `frontend-stream-error-timestamps.txt`.
- `lib.sh` and `run.sh`, the drivers.
- `e2e-down.txt`, the teardown record.
