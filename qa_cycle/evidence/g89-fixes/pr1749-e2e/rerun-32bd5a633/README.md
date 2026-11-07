# PR #1749: legal-depth-ii re-run at `32bd5a633`

**Verdict: PASS.**

- The scored legal-depth-ii run (stack G) is **40 passed / 0 failed / 4 skipped / 0 did not run** (1.9 m). That meets the target of ≥ 40 / 0 / 4 / 0.
- Per test, it is identical to Checkpoint 1 (`../../checkpoint-1/raw/G1-depth-ii.tsv`) plus exactly one new test, `day-30-45-soa-writeoff.spec.ts:425` "Statement after the write-off — sheriff fee out of the disbursements and the WIP", which passed.
- 0 "writing actual" lines on the scored run.
- All three failures from the earlier gate (`../README.md` F1 `:209`, F2 `:354`, F3 `:391`) passed here.
- The harness hang is gone. Across 8 executions on 2 stacks, every test touched by the networkidle replacement passed, each in under 4 s (the timeout is 30 s):
  - 1 capture run;
  - 1 scored run;
  - 6 isolated repeats.
- The changed specs had no failure that needed (a), (b) or (c) classification.
- Repeats 2–3 did fail. That was an unchanged `day-05` frame that only holds on a fresh stack, with a data cascade into `day-30-45`. The cause is the reused-stack repeat design, not PR code: class (c), shown below.

## Source and recipe

- **Source:** detached worktree `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp2` at `32bd5a633e03fb8734ec75b847c08b4e55aedec8`.
- **Change since the earlier gate (`150bc743e`):** spec-only. `git diff --stat 150bc743e 32bd5a633` shows `day-14-conveyancing.spec.ts` (+7/−3) and `day-30-45-soa-writeoff.spec.ts` (+101/−17).
- **Recipe:** the same as the earlier gate and CP1.
  - Each stack was built with `bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh`, run nohup-detached.
  - Each stack ran legal-lifecycle → legal-depth-ii. Stack C ran them in capture mode with `--update-snapshots=all`; stack G ran them scored.
  - No `e2e-reseed.sh`, no builder prune.
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before the Playwright flags.
- **Driver:** `raw/lib.sh` (a copy of `/tmp/pr1749/lib.sh` with only `RAW`, `W` and the PATH pin changed), plus `raw/run.sh` and `raw/run2.sh`.
- **Node:** the driver puts `/Users/rakheendama/.nvm/versions/node/v20.11.1/bin` first on PATH, so Homebrew's node 26 cannot win (lesson E1). Every run logs `node=v20.11.1 envnode=v20.11.1`, resolved through `pnpm exec node -v` (`raw/timeline.txt`).
- **Run window:** 2026-11-07, 15:36–15:58 SAST, on AC power, with `caffeinate -dims` held throughout.

## Stack table

Full checks are in `raw/stacks.txt`, with build logs in `raw/stack-{C,G}.log`.

| Stack | Purpose | sha | Build (SAST) | rc | Backend image | Frontend image | Tenant / global Flyway | Pristine at start |
|---|---|---|---|---|---|---|---|---|
| C | capture (unscored) | `32bd5a633` | 15:36:41 → 15:38:13 | 0 | `e89e160634bc` | `f4f8ea56a097` (no baselines in context) | 13 / 5 | `Website Redesign\|ACTIVE` only; `portal_deadline_view` = 0 |
| G | **scored** + repeats | `32bd5a633` | 15:43:14 → 15:44:37 | 0 | `e89e160634bc` | `ecd407e06702` (C baselines in context, the known 636A-c1 effect) | 13 / 5 | same; C volumes `Removed` |

- Both stacks were healthy: backend `UP`, frontend / mock-idp / mailpit 200, and `E2E Boot-Seed Complete!` logged.
- Both used compose working_dir `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp2/compose` and tenant `tenant_7d218705360b`.
- The backend image is byte-identical to the earlier gate's (`e89e160634bc`), as expected for a spec-only change.
- G89-F46 (Google Fonts build flake) did not occur.

## Results

| Run | Stack | Mode | Passed / failed / skipped / did not run | Notes |
|---|---|---|---|---|
| C-lifecycle | C | capture | 105 / 0 / 0 / 0 (3.0 m) | 26 frames written (same as the earlier gate) |
| C-depth-ii | C | capture | 40 / 0 / 4 / 0 (2.0 m) | 23 frames written (= CP1; the earlier gate's C1 reached only 18) |
| G-lifecycle | G | scored | **105 / 0 / 0 / 0** (2.9 m) | per-test identical to CP1 `G1-lifecycle.tsv`; 0 writing-actual |
| **G-depth-ii** | G | **scored** | **40 / 0 / 4 / 0** (1.9 m) | CP1 + 1 new test (below); 0 writing-actual |
| G-repeat-1 | G | scored, changed specs | 29 / 0 / 1 / 0 (1.5 m) | all `day-14` and `day-30-45` tests passed |
| G-repeat-2 | G | scored, changed specs | 14 / 2 / 1 / 13 | `day-14` 9/9 passed; failures explained under "Repeats 2–3" |
| G-repeat-3 | G | scored, changed specs | 14 / 2 / 1 / 13 | identical signature to repeat-2 |
| G-repeat-4 | G | `--ignore-snapshots`, changed specs | 29 / 0 / 1 / 0 (1.4 m) | all passed |
| G-repeat-5 | G | `--ignore-snapshots`, changed specs | 29 / 0 / 1 / 0 (1.4 m) | all passed |
| G-repeat-6 | G | `--ignore-snapshots`, changed specs | 29 / 0 / 1 / 0 (1.4 m) | all passed |

- The 4 skips in depth-ii are the declared `test.fixme` at `day-30-45:361` ("Statements-tab Download fetches the PDF", formerly `:292`) and the 3 opt-in `null-prefetch-probe` tests. They are the same 4 skips as in CP1.
- **"Changed specs alone"** means `day-05-disbursements.spec.ts` + `day-14-conveyancing.spec.ts` + `day-30-45-soa-writeoff.spec.ts`, run with the depth-ii config.
  - `day-05` has to be included: it starts the depth-II run id. Without it, `currentDepthIiRun()` throws "run day-05-disbursements.spec.ts first" (`frontend/e2e/helpers/legal-depth-ii.ts:39-41`).
  - That gives 30 tests: 29 run and the 1 fixme skipped.

**Per-test diff against CP1** (`diff <(cut -f1,3,4,5 ../../checkpoint-1/raw/G1-depth-ii.tsv | sort) <(cut -f1,3,4,5 raw/G-depth-ii.tsv | sort)`):

```
> day-30-45-soa-writeoff.spec.ts	Alice: Statement after the write-off — sheriff fee out of the disbursements and the WIP	expected	passed
```

**Against the earlier gate's G1:**

- `day-14:357` (was `:354`, the hang) and `day-30-45:273` (was `:209`, the D9 expectation) now pass.
- So do their cascades: `day-14:386`, `day-30-45:294/376/398/438`.
- `day-75-85:391` (the F3 pixel frame) passes.
- The new test `:425` passes.

### What the amended SoA assertions observed (scored run, all `expected/passed`)

- **`day-30-45:273` (Generate SoA)** passed with the new assertions:
  - `Closing balance owing: R0,00`;
  - `Unbilled work in progress (not yet billed): R7 000,00` (first attempt);
  - `Total disbursements: R2 000,00`, with the sheriff fee listed.
- **`:294` (Save Statement)**: the saved row's cell 1 matched the printed closing balance and cell 2 matched the printed trust balance. They are now checked separately.
- **`:425` (new, after the Day-45 write-off)**:
  - the sheriff fee is absent;
  - `Total disbursements: R1 250,00`;
  - `Closing balance owing: R0,00`;
  - `Unbilled work in progress (not yet billed): R6 250,00`.

## Harness hang (old rate about 5 in 8): gone in 8 of 8 executions

The touched steps are:

- `day-14:314`: networkidle after Save → content wait;
- `day-14:357`: networkidle before the curated OTP screenshot → `Save to Documents` enabled. This was the earlier F2 failure point.
- `day-30-45:273`: networkidle → `statement-download-btn` enabled.

| Run | day-14:314 | day-14:357 | day-30-45:273 | day-30-45:294 | day-30-45:425 |
|---|---|---|---|---|---|
| C-depth-ii | 3.1 s ✓ | 2.3 s ✓ | 3.7 s ✓ | 4.0 s ✓ | 2.1 s ✓ |
| G-depth-ii | 3.6 s ✓ | 2.4 s ✓ | 3.0 s ✓ | 3.9 s ✓ | 2.2 s ✓ |
| G-repeat-1 | 3.5 s ✓ | 2.4 s ✓ | 2.5 s ✓ | 3.9 s ✓ | 2.1 s ✓ |
| G-repeat-2 | 3.0 s ✓ | 3.0 s ✓ | ✘ cascade (see below) | — | — |
| G-repeat-3 | 3.1 s ✓ | 2.4 s ✓ | ✘ cascade (see below) | — | — |
| G-repeat-4 | 3.0 s ✓ | 2.3 s ✓ | 2.4 s ✓ | 3.3 s ✓ | 2.2 s ✓ |
| G-repeat-5 | 2.9 s ✓ | 2.4 s ✓ | 2.4 s ✓ | 3.4 s ✓ | 2.2 s ✓ |
| G-repeat-6 | 3.0 s ✓ | 2.3 s ✓ | 2.4 s ✓ | 3.3 s ✓ | 2.2 s ✓ |

Totals:

- `day-14` (both replaced waits): **8 / 8** passed, 0 timeouts.
- `day-30-45` with valid preconditions: **6 / 6** passed (C, G, repeats 1, 4, 5, 6). That includes 3 isolated repeats in normal scored mode or better (repeat-1, plus repeats 4–6).
- No run anywhere logged a `waitForLoadState` or test timeout.

## Repeats 2–3: classified (c), reused-stack data artefact in an unchanged spec, not PR code

**Root failure:** `day-05-disbursements.spec.ts:140` "Bob: Navigate to Legal → Disbursements list page". The spec is unchanged by this PR.

```
toHaveScreenshot(day-05-disbursements-list-empty.png) — 9380 px (ratio 0.02) differ   [repeat-3: 9362 px]
```

- **Baseline:** the C-stack baseline (`raw/artefacts/day-05-list-empty-expected-C-baseline.png`) is the firm-wide list on a fresh stack, showing "0 disbursements" and "No disbursements found."
- **Actual (repeat-2):** `raw/artefacts/repeat-2-day-05-list-empty-actual.png` shows "7 disbursements", accumulated from the scored run and repeat-1 on the same stack.
- **Threshold:** the page renders at 1280×720 CSS px, so 9380 px is about 1.02 % against `maxDiffPixelRatio: 0.01`.
- **Why repeat-1 passed:** it had fewer accumulated rows and stayed under the 1 % threshold.
- **Diagnosis:** the frame is only valid on a fresh stack, and the repeat design (same stack, re-run `day-05`) breaks that precondition.

**Cascade into `day-30-45:273`:** `day-05` stopped at `:140`, so that run's new Sipho matter never received the sheriff fee, deeds fee, trust deposit or payment. The SoA then correctly shows nothing for them. Actual text (`raw/G-repeat-2.txt`, `raw/artefacts/repeat-2-day-30-45-273-error-context.md`):

```
Expected substring: "Sheriff fee — service of process"
Received: … Total fees (excl. VAT): R5 000,00 … DisbursementsDateCategoryDescriptionSupplierAmountVATTotal disbursements: R0,00
Trust ActivityOpening balance: R0,00 … Closing balance: R0,00 Summary … Closing balance owing: R0,00 Trust balance held: R0,00
Unbilled work in progress (not yet billed): R5 000,00
```

That is internally consistent product output for a matter with 2 h of fees and no disbursements or trust (WIP = R5 000 fees only). So it is not (a) and not (b).

**Proof:** repeats 4–6 ran the same three specs on the same stack with `--ignore-snapshots`. That mode skips only `toHaveScreenshot` comparisons; every functional assertion and every replaced wait still ran. `day-05` completed every time and `day-30-45` passed **3 / 3**. `day-14` passed in repeats 2 and 3 as well (9/9 each).

**Follow-up, tooling, LOW, not this PR:** `day-05-disbursements-list-empty` is a firm-wide list frame, so it holds only on a fresh stack. If isolated re-runs of depth-ii on a reused stack should become a supported workflow, the frame should be filtered to the run's matter, or masked.

## Classification summary

| Item | Class | Evidence |
|---|---|---|
| Scored G-depth-ii | — (no failures) | `raw/G-depth-ii.{json,tsv,txt}` |
| Repeats 2–3: `day-05:140` pixel diff + `day-30-45:273` cascade | (c) environment: reused-stack data accumulation in an unchanged, fresh-stack-only frame | `raw/artefacts/*`; repeats 4–6 pass 3/3 |
| (a) product regression | none | — |
| (b) spec expectation still wrong | none: the amended SoA expectations held on 6 of 6 valid executions | — |

## Raw files (`raw/`)

- **Scored:** `G-lifecycle.{json,tsv,txt}`, `G-depth-ii.{json,tsv,txt}`.
- **Capture:** `C-depth-ii.{json,tsv,txt}`, `C-*.summary.txt`.
- **Repeats:** `G-repeat-{1..6}.{json,tsv,txt}`. TSV columns: file, line, title, status, last-result.
- **Stacks and timing:** `stacks.txt` (per-stack checks, image ids, Flyway, pristine state, writing-actual counts), `timeline.txt`, `stack-{C,G}.log`.
- **Artefacts:** `artefacts/`, holding the day-05 expected / actual / diff PNGs and the `day-30-45:273` error-context for repeats 2 and 3.
- **Driver:** `lib.sh`, `run.sh`, `run2.sh`.

## Environment left behind

- `bash compose/scripts/e2e-down.sh` ran at the end with rc 0. 0 `e2e-*` containers remain, and the `docteams-e2e_*` volumes and network were removed. `caffeinate` was stopped.
- The worktree is clean at `32bd5a633`, with `git status` empty.
  - The 75 untracked baselines and curated PNGs written by the capture were archived to `/tmp/pr1749r/rerun-baselines.tgz` and then git-cleaned.
  - Test-results copies are kept at `/tmp/pr1749r/G-*-test-results`.
- Nothing was committed.
- The KC dev stack, the main checkout and the other gate worktree were not touched.
- The pre-existing `compose_e2e_*` volumes come from a different compose project and were left alone.
