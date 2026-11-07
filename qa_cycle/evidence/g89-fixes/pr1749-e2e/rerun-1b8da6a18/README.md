# PR #1749: legal-depth-ii re-run at `1b8da6a18` (final head)

**Verdict: PASS.**

- The scored legal-depth-ii run (stack G) is **40 passed / 0 failed / 4 skipped / 0 did not run** (2.0 m). That meets the expected 40 / 0 / 4 / 0.
- The scored legal-lifecycle run (stack G) is **105 / 0 / 0** (3.2 m). That meets the expected 105 / 0 / 0.
- Per test, both scored runs are **identical** to the `32bd5a633` re-run (`../rerun-32bd5a633/raw/G-{lifecycle,depth-ii}.tsv`), with an empty `diff`. The capture depth-ii run is identical to the prior C run too.
- 0 "writing actual" lines on either scored run. 0 `waitForLoadState` or test-timeout lines.
- No failures, so nothing needed (a), (b) or (c) classification.

## Source and recipe

- **Source:** detached worktree `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp2` at `1b8da6a185dba011bab59c97d93d6f3d43a6c777`. `pwd` was confirmed and the tree was clean at the start.
- **Change since `32bd5a633`:** product code and tests only, no e2e spec changes. `git diff --stat 32bd5a633 1b8da6a18` lists 12 files, +417 / −27:
  - backend: `StatementService`, `StatementSummary`, `TrustDataVisibility`, `StatementOfAccountContextBuilder`, `GenericMenuPolicy`, plus 4 test classes;
  - frontend: `project-statements-tab.tsx`, `lib/api/statement-of-account.ts`, plus `__tests__/legal/statements-tab.test.tsx`.
  - No migrations, so Flyway stays at 13 / 5.
- **Recipe:** identical to `../rerun-32bd5a633/README.md`.
  - Each stack was built with `bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh`, nohup-detached.
  - Each stack ran legal-lifecycle → legal-depth-ii. Stack C ran in capture mode with `--update-snapshots=all`; stack G ran scored.
  - No `e2e-reseed.sh`, no builder prune.
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before the Playwright flags.
  - The changed-spec repeats were not repeated here: no spec changed, and the scored run had no failure to probe.
- **Driver:** `raw/lib.sh` is the prior `lib.sh` with only `RAW` and `W=/tmp/pr1749s` changed. `raw/run.sh` is the prior `run.sh` without the repeat loop.
- **Node:** v20.11.1 first on PATH. Every run logs `node=v20.11.1 envnode=v20.11.1`, resolved through `pnpm exec node -v` (`raw/timeline.txt`).
- **Run window:** 2026-11-07, 16:48–17:03 SAST, on AC power, with `caffeinate -dims` held throughout.
- **Untouched:** the main checkout, the Maven verify running in the other worktree, and the stopped KC app services. The KC infrastructure containers (`b2b-*`) stayed up throughout.

## Stack table

Full checks are in `raw/stacks.txt`, with build logs in `raw/stack-{C,G}.log`.

| Stack | Purpose | sha | Build (SAST) | rc | Backend image | Frontend image | Tenant / global Flyway | Pristine at start |
|---|---|---|---|---|---|---|---|---|
| C | capture (unscored) | `1b8da6a18` | 16:48:41 → 16:50:32 | 0 | `e2d1e6868b20` (new; was `e89e160634bc`) | `d8924e45f5d8` (no baselines in context) | **13 / 5** (`13\|g89 strip matter type suffix` / `5\|portal strip matter type suffix`) | `Website Redesign\|ACTIVE` only; `portal_deadline_view` = 0 |
| G | **scored** | `1b8da6a18` | 16:55:52 → 16:57:33 | 0 | `e2d1e6868b20` | `77c4896d9f41` (C baselines in context, the known 636A-c1 effect) | **13 / 5** | same; C volumes `Removed` |

- Both stacks were healthy: backend `UP`, frontend / mock-idp / mailpit 200, and `E2E Boot-Seed Complete!` logged.
- Both used compose working_dir `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp2/compose` and tenant `tenant_7d218705360b`.
- The backend and frontend images are new, as expected for a backend + frontend product change.
- G89-F46 (Google Fonts build flake) did not occur.

## Results

| Run | Stack | Mode | Passed / failed / skipped / did not run | Notes |
|---|---|---|---|---|
| C-lifecycle | C | capture | 105 / 0 / 0 / 0 (3.2 m) | 26 frames written (= prior) |
| C-depth-ii | C | capture | 40 / 0 / 4 / 0 (2.1 m) | 23 frames written (= prior); per-test identical to prior C |
| **G-lifecycle** | G | **scored** | **105 / 0 / 0 / 0** (3.2 m) | per-test identical to `32bd5a633` G; 0 writing-actual |
| **G-depth-ii** | G | **scored** | **40 / 0 / 4 / 0** (2.0 m) | per-test identical to `32bd5a633` G; 0 writing-actual |

- The 4 skips are the same 4 as before: the declared `test.fixme` at `day-30-45-soa-writeoff.spec.ts:361` and the 3 opt-in `null-prefetch-probe.spec.ts:74` tests.
- **Per-test diff** (`diff <(cut -f1,3,4,5 ../rerun-32bd5a633/raw/G-X.tsv | sort) <(cut -f1,3,4,5 raw/G-X.tsv | sort)`): empty for both `G-lifecycle` and `G-depth-ii`.

### Statement / trust checks (the area this head changed)

- These tests passed on the scored run:
  - `day-30-45:273` Generate SoA (3.4 s);
  - `day-30-45:294` Save Statement (3.5 s), where the saved row's trust cell matched the printed trust balance;
  - `day-30-45:425` the post-write-off statement (2.2 s).
- Alice is the owner and holds VIEW_TRUST, so the saved-row trust cell still shows the figure, not "Withheld". That is unchanged behaviour, as expected.
- **DB check before teardown** (`raw/saved-statement-summaries.txt`): every Statement of Account saved on stack G persisted a numeric `trust_balance_held`, for example `3750.00` on the depth-II Ndlovu matter. There were **0** `trust_withheld` markers. That is correct, since every statement was saved by a VIEW_TRUST reader.
- The non-VIEW_TRUST "Withheld" path is not exercised by these suites. Its coverage is the PR's backend integration test (`StatementTrustVisibilityIntegrationTest`) and the vitest `statements-tab.test.tsx`, not this gate.

## Classification summary

| Item | Class | Evidence |
|---|---|---|
| Scored G-depth-ii | no failures | `raw/G-depth-ii.{json,tsv,txt}` |
| Scored G-lifecycle | no failures | `raw/G-lifecycle.{json,tsv,txt}` |
| (a) product regression | none | — |
| (b) spec stale | none | — |
| (c) environment / flake | none | — |

## Raw files (`raw/`)

- **Scored:** `G-lifecycle.{json,tsv,txt}`, `G-depth-ii.{json,tsv,txt}`. TSV columns: file, line, title, status, last-result.
- **Capture:** `C-depth-ii.{json,tsv,txt}`, `C-lifecycle.summary.txt`, `C-depth-ii.summary.txt`.
- **Stacks and timing:** `stacks.txt` (per-stack checks, image ids, Flyway, pristine state, writing-actual counts), `timeline.txt`, `stack-{C,G}.log`.
- **DB evidence:** `saved-statement-summaries.txt`.
- **Driver:** `lib.sh`, `run.sh`.

## Environment left behind

- `bash compose/scripts/e2e-down.sh` ran at 17:03:36 with rc 0. 0 `e2e-*` containers remain, and no `docteams-e2e_*` volumes remain. `caffeinate` was stopped.
- The worktree is clean at `1b8da6a18`, with `git status` empty.
  - The 75 untracked baselines and curated PNGs written by the capture were archived to `/tmp/pr1749s/rerun-baselines.tgz` and then git-cleaned.
  - Test-results copies are kept at `/tmp/pr1749s/G-{lifecycle,depth-ii}-test-results`.
- Nothing was committed.
