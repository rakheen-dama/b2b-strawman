# PR #1751 (G89 Batch G: comments): pre-merge e2e gate

**Verdict: PASS. No e2e regression attributable to the PR.** No failure reproduces on the PR head without also reproducing on main `4f3323525`, or without passing on a PR-head re-run.

- **legal-depth-ii: 40 / 0 / 4 / 0.** Met on the PR head in attempt 3, run in the clean date window. The per-test diff against the #1750 gate is empty.
- **default `test:e2e`: 165 / 0 / 14.** Met on the PR head in attempt 3, clean window. The per-test diff against #1750 is empty, with the same 14 skips.
- **legal-lifecycle: 105 / 0 / 0.** Met on the PR head in attempt 2. The per-test diff against #1750 is empty, with 0 writing-actual.
  - The lifecycle was not date-window sensitive in any run.
  - In attempts 1, 3 and 4 the lifecycle failed on a fee-note screenshot frame, and serial mode skipped the tests after it.
  - Main `4f3323525` reproduces the attempt-1/3 failure set **per test, identically** (`main-4f3323525-clean`).
  - The attempt-4 frame (`day-30-fee-note-list-mixed`) passed on the PR head in attempt 2. The PR changes no fee-note code. Detail is under C3 and C4.
- **Comment specs: passed in every PR-head run.**
  - `day-07-14-work-trust.spec.ts:98` (Bob posts an internal comment on the Sipho task) passed in all 4 PR-head lifecycle runs, on both main runs, and with the posted row in the DB.
  - `projects/project-tasks.spec.ts` cases 8 and 9 passed in all 3 PR-head default runs.
- **Not a clean single-run gate.** I needed 4 PR-head cycles and 2 main control cycles. Two things polluted the first runs:
  - The gate started at 00:03 SAST. Until 02:00 SAST the UTC date (Nov 7) lags the SAST date (Nov 8).
  - Load average was about 66 while the other worktree's Maven verify was running.
  - The lifecycle frame flake (C3/C4) is **pre-existing on main**. It should be filed as a test-infra finding. It is not caused by this PR.

## Source and recipe

- **Source:** detached worktree `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4` at `f3c191bb7707a287bc5c2448f5a47d0458edd52e` (PR head). `pwd` was confirmed and the tree was clean at the start.
  - The merge base is main `4f3323525` (#1750 merged).
  - For the main control runs I checked out `4f3323525` **in the same worktree**, then returned it to `f3c191bb7`.
- **What changed vs main:**
  - backend `CommentService` and `NotificationService`, plus 2 integration test classes;
  - frontend `components/comments/*` (new `mention-textarea.tsx`; `comment-section.tsx` removed), `lib/comments.ts`, `lib/actions/comments.ts`, and `lib/api/matters.ts` (`page?` param), plus vitest files.
  - No migrations. `git diff --stat 4f3323525 f3c191bb7 -- portal packages pnpm-lock.yaml package.json pnpm-workspace.yaml compose` is **empty**, so portal suites were not run (per brief).
- **Recipe:** the #1750 recipe (`../pr1750-e2e/README.md`), legal + default only.
  - Each stack was built with `bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh`, nohup-detached.
  - C stack: capture legal-lifecycle and legal-depth-ii with `--update-snapshots=all`. G stack: scored lifecycle, depth-ii, then default.
  - No `e2e-reseed.sh` and no builder prune.
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before the Playwright flags. `pnpm install --frozen-lockfile` returned rc 0.
- **Driver:** `raw/lib.sh` is `../pr1750-e2e/raw/lib.sh` with only `WT`, `RAW` and `W` changed. `raw/run.sh` is the #1750 `run.sh` without the portal phases, plus comment and notification DB queries and a backend log scan.
  - Per-attempt copies: `raw/attempt2-pr-midnight/run-a2.sh` and `raw/attempt3-pr-clean/run-final.sh`. The attempt-3 launcher (`launch-final.sh`) waited until the UTC date equalled the SAST date, then started at 02:01:14 SAST.
  - `raw/main-4f3323525-midnight/run-main.sh` is the first main control run.
  - `raw/probe.sh` ran the lifecycle-only C+G cycles: main-clean, then attempt 4.
- **Node:** v20.11.1 first on PATH. Every run logs `node=v20.11.1 envnode=v20.11.1` (each `timeline.txt`).
- **Run window:** 2026-11-08, 00:03–02:30 SAST, with `caffeinate -dims` held throughout.
- **G89-F46 (Google Fonts):** did not occur. All 12 stack builds returned rc 0 on the first attempt.
- **Untouched:** the main checkout, the other worktree's Maven verify (it finished `EXIT=0` at about 00:46), and the stopped KC app services.

## Run matrix

The counts come from Playwright JSON `.stats`: passed / failed / skipped. The skipped figure includes "did not run" serial cascades.

The per-test diff is `diff <(cut -f1,3,4,5 ../pr1750-e2e/raw/G1-<suite>.tsv | sort) <(cut -f1,3,4,5 <run>.tsv | sort)`.

| Run (`raw/<dir>`) | sha | Window (SAST) | C lifecycle / depth-ii (frames) | G lifecycle | G depth-ii | G default |
|---|---|---|---|---|---|---|
| `attempt1-pr-midnight` | PR | 00:03–00:19, load avg about 66 | 91/2/12 (21) · 40/0/4 (23) | 72/5/28 (C3 cascade) | 36/1/7 (C2) | 164/1/14 (C1) |
| `main-4f3323525-midnight` | main | 00:21–00:35 | 91/2/12 (21) · 40/0/4 (23) | 100/5/0 (5 missing-baseline writes, C5) | 36/1/7 (C2) | 164/1/14 (C1) |
| `attempt2-pr-midnight` | PR | 00:36–00:49 | **105/0/0 (26)** · 40/0/4 (23) | **105/0/0, empty diff** | **40/0/4, empty diff** | 164/1/14 (C1 only) |
| `attempt3-pr-clean` | PR | 02:01–02:13 | 105/0/0 (26) · 40/0/4 (23) | 72/5/28 (C3 cascade) | **40/0/4, empty diff** | **165/0/14, empty diff** |
| `main-4f3323525-clean` | main | 02:14–02:21 | 105/0/0 (26) · — | 72/5/28, **per-test identical to attempt 3 and attempt 1** | — | — |
| `attempt4-pr-clean` | PR | 02:22–02:29 | 105/0/0 (26) · — | 103/1/1 (C4) | — | — |

- Writing-actual on scored runs was 0 in every PR-head G run. The main-midnight G lifecycle had 5, explained in C5.
- Frame counts on clean captures equal the #1750 gate: 26 lifecycle and 23 depth-ii.

### Specs the brief asked about

| Spec | Runs | Result |
|---|---|---|
| `legal-lifecycle/day-07-14-work-trust.spec.ts:98` "Bob: Add internal comment on the Sipho letter-of-demand task" (comment post) | PR attempts 1–4, main ×2 | **passed in all 6** |
| `projects/project-tasks.spec.ts:277` case 8 "Comment notification deep-links to the comment in the task sheet (G88-M6)": the `@Carol` mention and the notification deep link | PR attempts 1–3, main-midnight | **passed in all 4** |
| `projects/project-tasks.spec.ts:322` case 9 "Matter comment notification deep-links to the highlighted comment on the Comments tab (B7)": the matter comment deep link | PR attempts 1–3, main-midnight | **passed in all 4** |

- Case 9 fills `@Carol matter thread <run>` into the panel composer, which is now the PR's `MentionTextarea`, and clicks **Post Comment**. So the autocomplete did not block posting.
- Case 9 also asserts that the posted PROJECT comment is `visibility=SHARED`, and it passed.

### Areas this PR touches: DB and log observations (G stacks, after default)

| PR change | Observation |
|---|---|
| Comment recipients filtered to existing members with matter access (G89-F31) | PR-head `stacks.txt` (attempts 1–3): `COMMENT_ADDED` rows go **only to Carol Member**. One is TASK, `reference_comment_id` = the `@Carol please review` comment. The other is PROJECT, = the `@Carol matter thread` comment. Bob's lifecycle task comment produced no `COMMENT_ADDED` row. Main midnight is **identical** in shape. These suites do not exercise portal-authored comments or a mentioned member without matter access. That path is covered by the PR's `CommentNotificationRecipientsIntegrationTest`, not by this gate. |
| PROJECT ⇒ SHARED on edit | Matter-level comments persisted as `PROJECT\|SHARED`, and task comments as `TASK\|INTERNAL`. No suite edits a comment's visibility, so this is covered by `CommentServiceIntegrationTest`. |
| Comment lists load all pages | E2E threads are 1–2 comments. No spec crosses the 50-comment page boundary, so this is covered by `comment-pagination.test.tsx`. |
| Portal-authored comment author name | Not exercised: portal suites were out of scope, and no portal comment exists in these runs. |
| Backend log | 0 WARN/ERROR lines mention comment or mention. The only notification WARNs are `No recipients found for DOCUMENT_GENERATED` (10 on attempt 1, 12 on attempt 2, 11 on attempt 3), and main midnight has 11. That is the same class, and not comment-related. |

## Stack table

Full checks are in each `raw/<run>/stacks.txt`.

| Run | Stack | sha | rc | Backend image | Frontend image | Tenant / global Flyway | Pristine |
|---|---|---|---|---|---|---|---|
| attempt1 | C1 / G1 | `f3c191bb7` | 0 / 0 | `0e4a0d42ab62` | `987d275ab7dc` / `563376f2987b` | **14 / 5** | `Website Redesign\|ACTIVE` only; `portal_deadline_view` = 0 |
| main-midnight | C1 / G1 | `4f3323525` | 0 / 0 | `e32daa17385c` | `728cc5a6b52e` (= #1750 C1 image) / `8cd93fa709fa` | **14 / 5** | same; volumes `Removed` |
| attempt2 | C1 / G1 | `f3c191bb7` | 0 / 0 | `0e4a0d42ab62` | `987d275ab7dc` / `538c1ce35bd5` | **14 / 5** | same; volumes `Removed` |
| attempt3 | C1 / G1 | `f3c191bb7` | 0 / 0 | `0e4a0d42ab62` | `987d275ab7dc` / `6de919b3a265` | **14 / 5** | same; volumes `Removed` |
| main-clean | C / G | `4f3323525` | 0 / 0 | `e32daa17385c` | `728cc5a6b52e` / `92b45c867e5b` | **14 / 5** | same; volumes `Removed` |
| attempt4 | C / G | `f3c191bb7` | 0 / 0 | `0e4a0d42ab62` | `987d275ab7dc` / `c83e88c05473` | **14 / 5** | same; volumes `Removed` |

- Flyway on every stack is tenant `14|g89 contains trust data` and global `5|portal strip matter type suffix`.
- Every stack was healthy: backend `UP`, frontend / mock-idp / mailpit 200, and `E2E Boot-Seed Complete!` logged. The tenant was `tenant_7d218705360b`.
- Compose working_dir was `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp4/compose` for every stack.
- The G frontend image differs from C on every run because the C baselines are in the build context. This is the known 636A-c1 effect.

## Classification

| ID | Failure | Where | Class | Evidence |
|---|---|---|---|---|
| C1 | `projects/project-time.spec.ts:238` "7. My Work lists today's time entries" | default: PR attempts 1–2 and main midnight; **passed in PR attempt 3** (clean window) | **(c) environment: SAST/UTC date window** | See C1 below. |
| C2 | `legal-depth-ii/day-14-conveyancing.spec.ts:282` frame `day-14-conveyancing-fields-populated` (0.03 ratio) | depth-ii: PR attempt 1 and main midnight, identically; **passed in PR attempts 2 and 3** | **(c) capture artefact under load** | See C2 below. |
| C3 | `legal-lifecycle/day-30-billing.spec.ts:90` frame `day-30-fee-note-tariff-completed` (0.02 ratio), then the serial cascade | lifecycle: PR attempts 1 and 3, **and main clean, per-test identical**; **passed in PR attempts 2 and 4** | **(c) pre-existing frame flake, reproduces on main** | See C3 below. |
| C4 | `legal-lifecycle/day-30-billing.spec.ts:282` frame `day-30-fee-note-list-mixed` (0.02 ratio) | lifecycle: PR attempt 4 only; **passed on PR attempt 2 and main midnight** | **(c) run-ID-dependent table reflow** | See C4 below. |
| C5 | C-stack capture failures `day-75-complex:224` "Log 360min on Moroka" and `day-90-review:83` "six matters", plus 5 G "snapshot doesn't exist, writing actual" failures on main | C1 capture on PR attempt 1 **and** main midnight, identically; none in later captures | **(c) environment: load and the midnight window, reproduces on main** | See C5 below. |
| (a) | product regression | — | **none** | — |
| (b) | spec stale by an intended change | — | **none** | — |

### C1: SAST/UTC date window

- The spec posts `date: new Date().toISOString().split("T")[0]`, which is the **UTC** date. My Work's "Today" is the SAST date.
- The attempt-2 DB shows `2026-11-07|30|false|Non-billable entry w4u2`, while local time was `2026-11-08 00:49 SAST` and UTC was `2026-11-07 22:49` (`raw/attempt2-pr-midnight/project-time-entry-dates.txt`).
- The error context shows "Today" listing only SAST-dated entries (`failure-artifacts/G1-default/…`).
- It fails identically on main in the same window.
- This is a spec date-helper defect, outside this PR: the spec should use `todayInSA()`, the way the legal helpers already do.

### C2: conveyancing capture artefact

- The expected image (C1 capture, taken at load average about 66) is scrolled to the field grid. The G actual is unscrolled, which **matches the #1750 baseline** (`/tmp/pr1750/PR1750-baselines.tgz`).
- The badge count differs too, 15 vs 8, because C1 lifecycle reached day 90 while G1 lifecycle stopped at day 30.
- The `failure-artifacts/G1-depth-ii/` folder has the expected, actual and diff images.

### C3: tariff frame flake

- Content is identical between the images. Only the viewport scroll offset differs.
  - In attempt 1 the actual was 2 px higher.
  - In attempt 3 and on main clean, the capture landed scrolled to the bottom of the page (Total row and the "No custom fields configured" panel), while G landed at the #1750 position.
- The fee-note page imports nothing the PR changed. `git grep` on the PR head finds comment components only on the matter page, `documents-panel` and `task-detail-sheet`.
- The cascade explains the rest:
  - `day-45:160` and `day-90:95`: `Run-state value "apexFeeNote" was not recorded`.
  - `day-60:97`: Moroka interest is computed on R 250 000 instead of R 241 500, because the day-30 fee transfer was skipped. 8 064,52 / 7 790,32 = 1.0352 = 250 000 / 241 500.
  - `day-90:326`: "Fee note must be in APPROVED, SENT or PARTIALLY_PAID status, current status: DRAFT".

### C4: fee-note list reflow

- Expected and actual have the same 6 fee notes, statuses, amounts, totals cards and row order.
- Only the auto-width table columns differ. The C stack's client names (run ID `mw738mwn1602`) wrapped onto 2 lines, while the G stack's (`mw73e6pff426`) did not.
- The fee-notes list is not touched by the PR.

### C5: midnight captures under load

- The capture failures are identical on PR and main, and absent from every later capture (attempts 2–4 and main clean: 105/0/0, 26 frames).
- Because C1 never reached day 90, main's G run had to write 5 missing day-90 baselines. Those are the "snapshot doesn't exist, writing actual" failures.

**Follow-up candidates for the orchestrator.** These are not part of this PR, and I did not fix them:

1. `project-time.spec.ts:207/238` uses the UTC date. It fails every night between 00:00 and 02:00 SAST.
2. `day-30-billing.spec.ts` frames `day-30-fee-note-tariff-completed` and `day-30-fee-note-list-mixed` are nondeterministic on main. The first depends on scroll position; the second reflows with the run-ID width. Since 02:00 SAST on 2026-11-08 the tariff frame failed on main 1/1 and on the PR head in 1 of 2 clean runs. Likely remedies are masking or element-scoped screenshots, or a scroll reset before capture. That is a spec decision.

## Raw files (`raw/`)

- Every run folder holds:
  - `G*-{lifecycle,depth-ii,default}.{json,tsv,txt}`, with TSV columns file, line, title, status, last-result;
  - `C*-*.summary.txt` and `.tsv`, plus `C*-lifecycle.txt`;
  - `stacks.txt` (stack checks, image ids, Flyway, pristine state, writing-actual counts, and after default the comment rows, `COMMENT_ADDED` notifications, projects and backend log scan);
  - `timeline.txt` and `stack-*.log`;
  - `failure-artifacts/` (expected / actual / diff PNGs and `error-context.md` per failed test).
- `main-4f3323525-clean/C-day-30-fee-note-tariff-completed.png` and `attempt4-pr-clean/C-day-30-fee-note-tariff-completed.png` are the captured tariff baselines that show the scrolled-to-bottom variant.
- `attempt2-pr-midnight/project-time-entry-dates.txt` is the C1 DB proof.
- **Drivers:**
  - `lib.sh` and `run.sh` (attempt 1);
  - `attempt2-pr-midnight/{lib-a2,run-a2}.sh`;
  - `main-4f3323525-midnight/{lib-main,run-main}.sh`;
  - `attempt3-pr-clean/{lib-final,run-final,launch-final}.sh` and `wait.log`;
  - `probe.sh` and `probe.status` (main-clean and attempt 4).
- `e2e-down.txt` is the teardown record.

## Environment left behind

- `bash compose/scripts/e2e-down.sh` ran at 02:30:29 SAST with rc 0. 0 `e2e-*` containers remain, and 0 `docteams-e2e_*` volumes remain. `caffeinate` was stopped.
- The worktree is back at `f3c191bb7`, with `git status` empty.
  - Untracked baselines and curated PNGs from every capture were archived, then removed. The archives are in `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1751/`: `attempt1-PR-baselines.tgz`, `main-baselines.tgz`, `a2-baselines.tgz`, `a3-baselines.tgz`, `a4-baselines.tgz` and `attempt4-pr-clean/dirty-before.tgz` (the main-clean baselines, removed before attempt 4).
  - Full test-results copies are in the same folder.
- Nothing was committed.
