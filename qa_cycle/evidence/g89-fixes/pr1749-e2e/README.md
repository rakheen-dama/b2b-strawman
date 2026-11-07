# PR #1749 (G89 Batch C2: template packs and document generation): pre-merge e2e gate

**Verdict: NOT CLEAN, but no regression caused by PR #1749 was found.**

- Six of the seven scored suites match Checkpoint 1 exactly: an empty per-test diff against `../checkpoint-1/raw/*.tsv`. The six are legal-lifecycle, default, portal 90-day, portal responsive, portal-data-isolation and login-bare. Portal vitest also matches.
- legal-depth-ii does not match. It scored **31 / 3 / 4 / 5** against a baseline of 39 / 0 / 4. Classification of the three failures:
  - **1 × (b), already on main.** `day-30-45-soa-writeoff.spec.ts:209` fails the same way on main `08c64dd88`, which has no #1749 code: 3 of 3 main runs failed (MC, MG, MP). Main has had this failure since #1748 (C1, G89-F16 / D9: the SoA closing balance is now on a fee-note basis), which merged after Checkpoint 1. It needs a spec amendment, and merging #1749 neither causes nor fixes it.
  - **2 × (c), non-deterministic.** `day-14-conveyancing.spec.ts:354` and `day-75-85-closure.spec.ts:391` failed on the first scored stack and on its fresh re-run. Both stacks used the same frontend image build. Both tests passed on a second independent capture → score round at the same PR sha. Both also pass on main. The `:354` hang reproduces on main code with a standalone probe.
- No failure is classified (a).

Every expected difference listed in the brief was observed. The changed `document-generation.spec.ts` passed all 5 tests.

## Source and recipe

- **Source:** detached worktree `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp2` at `150bc743ec1921f3ee76b5d2f0993bd2f1483458` (PR head). Merge base is `08c64dd88` (main after #1748). `origin/main` is `6528cd38c`, which is docs-only on top of `08c64dd88`: `git diff --stat 08c64dd88 origin/main -- backend frontend portal compose packages` is empty.
- **Recipe:** Checkpoint 1 / 636A.
  - Stacks: C1 capture legal → G1 scored legal + default → C2 capture portal → G2 scored portal.
  - Every stack was built with `bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh`, run nohup-detached from the worktree. `e2e-reseed.sh` was never used, and neither was `docker builder prune`.
  - Commands and environment are byte-identical to CP1, with pnpm at `/opt/homebrew/bin/pnpm`. Driver: `/tmp/pr1749/lib.sh`, `p1.sh` … `p6.sh` (not committed).
- **Extra classification runs** (beyond the recipe):
  - G1b: a fresh re-run of G1 lifecycle → depth-ii.
  - MC/MG: main `08c64dd88` capture → score of lifecycle → depth-ii.
  - C1b/G1c: a second independent PR capture → score of lifecycle → depth-ii.
  - MP: a main stack for the networkidle A/B probe.
  - For the main stacks, the worktree was temporarily checked out at `08c64dd88` and then restored to `150bc743e`; see the timeline.
- **Run window:** 2026-11-07, 13:48–15:26 SAST. `caffeinate -dims` was held throughout. The machine was **on battery** (91 % at start), not on AC as in CP1. A full Maven verify was running concurrently in another worktree.
- **Frontend Google-Fonts flake (G89-F46):** it did not occur. All 10 stack builds returned rc 0 on the first attempt.

## Verdict table

The counts come from the Playwright JSON `.stats` in `raw/<label>.json`, cross-checked against the list-reporter summary in `raw/<label>.txt`. "Did not run" is the list reporter's count of serial-group cascades. The JSON reporter folds those tests into `skipped`, and the TSV records them as `skipped/skipped`.

| Suite | Stack | Passed / failed / skipped / did not run | Checkpoint 1 baseline | Per-test diff vs CP1 | Status |
|---|---|---|---|---|---|
| legal-lifecycle | G1 | **105 / 0 / 0 / 0** (3.1 m) | 105 / 0 / 0 | identical | PASS |
| legal-depth-ii | G1 | **31 / 3 / 4 / 5** (2.4 m) | 39 / 0 / 4 | 3 failed + 5 cascaded (below) | **FAIL**: 1 × (b) already on main, 2 × (c) |
| default (`test:e2e`) | G1 | **165 / 0 / 14 / 0** (1.1 m) | 165 / 0 / 14 | identical (same 14 skips) | PASS |
| portal vitest | — | **49 files / 278 tests passed** (node v20.11.1) | 49 / 278 | identical | PASS (see E1) |
| portal 90-day | G2 | **13 / 0 / 0 / 0** (18.5 s) | 13 / 0 / 0 | identical | PASS |
| portal responsive | G2 | **21 / 0 / 0 / 0** (17.4 s) | 21 / 0 / 0 | identical | PASS |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | **2 / 0 / 0 / 0** | 2 / 0 / 0 | identical (both passed, not skipped) | PASS |
| login-bare (optional, production portal) | G2 | **1 / 0 / 0 / 0** (16.0 s) | 1 / 0 / 0 | identical | PASS |

- **Snapshot writes on scored runs:** 0 "writing actual" lines in every scored run.
- **Capture runs (C1 / C2):**
  - C1-lifecycle: 105 / 0 / 0, 26 frames.
  - C1-depth-ii: 34 / 1 / 4 + 4 did not run, 18 frames. CP1 had 23 frames; the 5 frames after the SoA failure were never reached.
  - C2-90day: 13 / 0 / 0, 11 frames.
  - C2-responsive: 21 / 0 / 0, 21 frames.

### Changed spec: `documents/document-generation.spec.ts` (G1-default), all `expected` / `passed`

| Line | Test |
|---|---|
| 78 | Preview shows resolved variables ✓ |
| 87 | Generate document from template on the matter page ✓ |
| 103 | Generate a document from the client page ✓ |
| 120 | Download PDF ✓ |
| 134 | Generated documents list ✓ |

### Expected differences from the PR (observed)

| Expected difference | Evidence |
|---|---|
| The Generate menu no longer offers Matter Summary Report, Standard Engagement Letter, SoA, closure letter or the payment / trust receipts | On the G1b stack, the Khumalo matter's Generate Document menu (Playwright MCP) listed only the 10 legal-za matter templates: Engagement Letter — Litigation / Conveyancing / General, Notice of Motion, Founding Affidavit, Letter of Demand, Offer to Purchase, Deed of Transfer, Power of Attorney to Pass Transfer, Bond Cancellation Instruction. `document-generation.spec.ts` uses "Engagement Letter — General" and passed |
| No "Matter type: X" suffix | G2 DB: `projects` 0 rows with the suffix, `project_templates` 0, `portal.portal_projects` 0 of 1 |
| SoA has an "Unbilled work in progress" row | The SoA preview text on the PR includes `Unbilled work in progress (not yet billed): R7 000,00`. On main (MC) the summary ends at `Trust balance held` (`raw/artefacts/*-soa-209-failure.txt`) |
| Receipt file names include `-r1-` | G2 DB `generated_documents.file_name`: `payment-receipt-inv-0002-r1-2026-11-07.pdf` |
| Tenant V13 / global V5 | Every PR stack: tenant Flyway `13 \| g89 strip matter type suffix`, global `5 \| portal strip matter type suffix` |

## Stack table

Full checks are in `raw/stacks.txt` and the timeline in `raw/timeline.txt`. Every stack was healthy: backend `UP`, frontend 200, mock-idp 200, mailpit 200. Every stack logged `E2E Boot-Seed Complete!`. Every stack after the first logged `Volume docteams-e2e_e2e_postgres_data Removed`, and each held only `Website Redesign|ACTIVE` in `projects` and 0 rows in `portal_deadline_view` when it came up. All were built from compose working_dir `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp2/compose`.

| Stack | Purpose | sha | Build (SAST) | rc | Backend / frontend image | Tenant | Tenant / global Flyway |
|---|---|---|---|---|---|---|---|
| C1 | capture legal | `150bc743e` | 13:51:47 → 13:52:28 | 0 | `e89e160634bc` / `35268e87b63a` | `tenant_7d218705360b` | **13 / 5** |
| G1 | **scored** legal + default | `150bc743e` | 13:58:21 → 13:59:59 | 0 | `e89e160634bc` / `082e362e3ac6` | same | **13 / 5** |
| G1b | re-run of G1 lifecycle → depth-ii (classification) | `150bc743e` | 14:07:36 → 14:08:26 | 0 | `e89e160634bc` / `082e362e3ac6` | same | 13 / 5 |
| C2 | capture portal | `150bc743e` | 14:26:03 → 14:26:42 | 0 | `e89e160634bc` / `082e362e3ac6` | same | 13 / 5 |
| G2 | **scored** portal | `150bc743e` | 14:33:06 → 14:33:45 | 0 | `e89e160634bc` / `082e362e3ac6` | same | **13 / 5** |
| MC | main capture (classification) | `08c64dd88` | 14:51:51 → 14:53:22 | 0 | `75ce5c6562ed` / `62c242cf8e83` | same | 12 / 4 |
| MG | main scored (classification) | `08c64dd88` | 14:58:03 → 14:59:23 | 0 | `75ce5c6562ed` / `ebec3b79a772` | same | 12 / 4 |
| C1b | second PR capture (classification) | `150bc743e` | 15:05:26 → 15:06:05 | 0 | `e89e160634bc` / `35268e87b63a` | same | 13 / 5 |
| G1c | second PR scored (classification) | `150bc743e` | 15:10:51 → 15:12:13 | 0 | `e89e160634bc` / `72115ee5eed6` | same | 13 / 5 |
| MP | main, networkidle A/B probe | `08c64dd88` | 15:19:13 → 15:19:52 | 0 | `75ce5c6562ed` / `62c242cf8e83` | same | 12 / 4 |

The frontend image changes whenever the untracked baseline PNGs in the build context change. This is the known 636A-c1 effect: `.dockerignore` does not exclude `frontend/e2e/screenshots/**`. Images built with no baselines share an id: `35268e87b63a` for C1 and C1b. G1 and G1b share `082e362e3ac6`, built with the C1 baselines. G1c is `72115ee5eed6`, built with the C1b baselines.

**Portal magic-link spacing** (`raw/stacks.txt` → "spacing"): each portal suite waited until the newest token was at least 330 s old, then ran `docker restart e2e-backend` and confirmed :3002 was free.

| Before | Token age when released |
|---|---|
| C2-responsive | 333 s |
| G2-responsive | 331 s |
| G2-isolation | 331 s |
| G2-login-bare | 333 s |

The cumulative "Exchanged magic link" counts were 1 / 2 / 4 / 5 after 90-day / responsive / isolation / login-bare, identical to CP1. G2-90day post-checks: `portal_deadline_view` = 0, and `REQ-0002 | IN_PROGRESS` was created by the run.

## Failure classification (G1-depth-ii)

### F1: `legal-depth-ii/day-30-45-soa-writeoff.spec.ts:209` "Alice: Generate Statement of Account with HTML preview": **(b) spec expectation broken by an intended change, already on main from #1748, not from #1749**

**Assertion** (`:107`, inside `verifyStatementPreview`):

```ts
await expect(preview).toContainText(/Closing balance owing:\s*R\s?[1-9]/);
```

**Received on PR head:**

```
Total fees: R0,00 · Total disbursements: R0,00 · Previous balance owing: R0,00 · Payments received: R0,00 · Closing balance owing: R0,00 · Trust balance held: R3 750,00 · Unbilled work in progress (not yet billed): R7 000,00
```

**Received on main `08c64dd88`:** identical, but without the WIP row.

**Evidence that this comes from main and is intended:**

- It fails deterministically on all 5 PR depth-ii runs (C1, G1, G1b, C1b, G1c) and on all 3 main runs (MC, MG, MP) (`raw/MC-depth-ii.tsv`, `raw/MG-depth-ii.tsv`, `raw/MP-depth-ii.tsv`, `raw/artefacts/*-soa-209-failure.txt`).
- It passed at CP1 (`d6742789f`), before #1748.
- #1748 (`08c64dd88`, G89-F16, founder decision D9) moved the SoA summary to a fee-note basis. Its commit message says "unbilled WIP is counted as owing" was a red test it fixed, and "summary.unbilled_wip … shown, never owing. The template row ships with C2's legal-za pack bump".
- On Day 30 the Sipho matter has no issued fee note, so closing owing is now correctly R0,00. The R7 000 (5 000 fees excl. VAT + 2 000 disbursements) is now WIP.
- #1749's `StatementOfAccountContextBuilder` diff only swaps the trust gate (`VerticalModuleGuard` → `TrustDataVisibility`) and adds `trust.status`. It does not touch the summary.

**Cascade:** the serial group did not run `:227` Save Statement, `:307` write-off, `:329` write-off audit and `:356` write-off excluded. `:292` is the declared `test.fixme`.

**Proposed new expectation** (`day-30-45-soa-writeoff.spec.ts:105–107`; needs authorization):

```ts
await expect(preview).toContainText("Summary");
await expect(preview).toContainText(/Closing balance owing:\s*R\s?0,00/);              // D9: no fee note issued yet
await expect(preview).toContainText(/Unbilled work in progress \(not yet billed\):\s*R\s?7\s?000,00/); // D9 + #1749 WIP row
```

The WIP line is valid only once #1749 lands. On main alone, only the closing-balance change applies.

The helper's returned pattern for `:227` (`R\s?0,00`) should still work, but it was not observed, because `:227` never ran.

**This spec was not edited.** Spec changes are out of scope for this gate.

### F2: `legal-depth-ii/day-14-conveyancing.spec.ts:354` "Alice: Generate Offer to Purchase document with conveyancing clauses": **(c) non-deterministic harness race, which reproduces on main code**

**Failure:**

```
Test timeout of 30000ms exceeded. page.waitForLoadState("networkidle") at :375
```

All content assertions before `:375` passed: Conditions of Sale, voetstoots, the G88-F12 party wording, suspensive, transfer duty.

**Evidence:**

- **Outcome across runs.**

  | Code | Runs | Result |
  |---|---|---|
  | PR | C1 (capture), C1b (capture), G1c (scored) | passed |
  | PR | G1 (scored), G1b (fresh re-run), plus 2 in-place re-runs on the G1b stack (one with `--trace=retain-on-failure`, one in `--update-snapshots=all` mode) | failed |
  | main | MC, MG, MP | passed |

  Every failure was on frontend image `082e362e3ac6`. So the failure is not tied to scored vs capture mode, and it does not happen on every PR stack.

- **The hang is not a pending product request.**
  - The trace (`/tmp/pr1749/probe/dii-trace/…/trace.zip`) shows every request completed.
  - A standalone probe (`raw/probe/probe2.cjs`) repeats the flow: save an OTP, open a new page, then Generate → Offer to Purchase → Next → `waitForLoadState('networkidle')`. When it times out, it reports **0 in-flight requests**.
  - The only frame activity is the sandboxed `about:srcdoc` preview iframe attaching. The iframe has `sandbox=""`, no scripts and no external URLs. The console shows "Blocked script execution in 'about:srcdoc'".
  - This is consistent with Playwright's subtree networkidle never firing for the sandboxed srcdoc child frame.

- **The same probe times out on main code.**

  | Code | Stack | Probe timeouts | Source |
  |---|---|---|---|
  | main | MG | 2 / 8 | `raw/probe/main-probe-rate.txt` |
  | main | MP (prepared like G1c) | 3 / 8 | `raw/probe/main-probe-rate-MP.txt` |
  | PR | G1c | 5 / 8 | `raw/probe/pr-probe-rate.txt` |

  The PR / main difference (5/8 vs 5/16) is not significant at this sample size. No PR file is in the preview / iframe path except a one-line label change in the required-field warning list (`GenerateDocumentDialog.tsx:309`).

- **Honest caveat:** the higher PR probe rate and the 4-of-7 PR suite failures (against 0 of 3 on main) keep a timing shift possible. There is no evidence of a product defect.

**Recommended follow-up (tooling, LOW, not part of this PR):** replace `await page.waitForLoadState("networkidle")` before the curated screenshot at `day-14-conveyancing.spec.ts:375` with a deterministic wait. The preview-content assertions above it already act as that wait. The same pattern exists at `:339`, which passed every time.

### F3: `legal-depth-ii/day-75-85-closure.spec.ts:391` "Alice: Verify override audit event …": **(c) baseline/capture artefact**

**Failure:** `toHaveScreenshot(day-85-audit-log-override-event.png)` — 9 502 px (0.02) differ.

**Evidence:**

- The functional assertions passed: `override_used: true`, the "Matter Closure Override Used" event, and the justification text.
- The diff (`raw/artefacts/day-85-audit-log-override-event-{expected,actual,diff}.png`) is a vertical scroll / expansion-state difference on the Settings → Audit trail page:
  - The C1 baseline froze the page with the filtered row not yet expanded and the Filters card higher up.
  - The scored frame shows the same row expanded (Object(4): reason / actor / project / justification), scrolled about 120 px further.
- The audit trail page is not touched by the PR.
- Outcome across runs:

  | Runs | Result |
  |---|---|
  | PR G1, PR G1b | failed against the C1 baseline |
  | PR C1b → G1c (second capture → score round) | passed |
  | main MC → MG | passed |

### Re-run-only observations (not part of the scored gate, recorded for completeness)

- **G1c-lifecycle (2nd PR round): 93 / 2 / 0 + 10 did not run.** The failures were pixel diffs on `day-30-fee-note-list-mixed.png` (0.02) and `day-90-portfolio-review-loaded.png` (0.02).
  - These are 636A's known-candidate lifecycle frames.
  - The same PR sha scored 105 / 0 / 0 on G1 and G1b.
  - **Class (c):** capture/score frame instability.
- **MG-lifecycle (main): 72 / 5 / 0 + 28 did not run.** This was caused by the same instability, on `day-30-fee-note-tariff-completed.png`, followed by run-state cascades.
- **Takeaway:** screenshot baselines captured on one stack and scored on another are not fully deterministic on either main or the PR. Neither of these lifecycle runs is a scored gate run.

## Infrastructure / environment events

- **E1: portal vitest attempt 1 ran under node v26 (driver error), class (c).**
  - Cause: my driver put `/opt/homebrew/bin` first on `PATH`, so `pnpm`'s `#!/usr/bin/env node` resolved to Homebrew node v26.0.0. CP1 ran on the shell's nvm v20.11.1.
  - Effect: 12 failed / 266 passed in `lib/__tests__/auth.test.ts` and `app/(authenticated)/__tests__/layout.test.tsx`, all with `TypeError: Cannot read properties of undefined (reading 'clear')` on `localStorage.clear()`. Node 26's own `localStorage` global shadows happy-dom's (`raw/portal-vitest-node26-attempt1.txt`).
  - Fix and re-run: the PATH prepend was removed. Under node v20.11.1 the result was 49 / 278 passed, matching CP1.
  - The C1-lifecycle capture that had started under node 26 was killed after about 1 minute. Its partial PNGs were removed with git clean, and C1 was rebuilt fresh. Every recorded run used node v20.11.1.
  - Observation: portal vitest is not runnable on node ≥ 25 without a happy-dom / `--no-experimental-webstorage` accommodation.
- **E2: the frontend `test-results/` of G1-depth-ii was overwritten by G1-default**, which uses the same output dir. G1b reproduced the same failures with artefacts kept (`/tmp/pr1749/G1b-depth-ii-test-results`).
- **G89-F46** (Google Fonts build flake): did not occur.

## Raw files (`raw/`)

- **Scored gate runs:** `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}` and `G2-{90day,responsive,isolation,login-bare}.{json,tsv,txt}`.
  - TSV columns: file, line, title, status, last-result.
- **Classification runs:**
  - `G1b-*` and `G1c-*`: `{json,tsv,txt}`.
  - `MG-*`: `{json,tsv,txt}`.
  - `MC-depth-ii.{tsv,txt}`, `MP-depth-ii.tsv`, `C1b-depth-ii.tsv`, `C1-depth-ii.txt`.
  - Capture summaries: `C1-*.summary.txt`, `C2-*.summary.txt`, `C1b-*.summary.txt`, `MC-*.summary.txt`, `MP-*.summary.txt`.
- `portal-vitest.txt` (node v20, scored) and `portal-vitest-node26-attempt1.txt` (E1).
- `stacks.txt`: per-stack checks, image ids, Flyway, pristine state, spacing, magic-link exchange counts, and the G1 projects list after default.
- `timeline.txt`
- `artefacts/`: the F3 expected / actual / diff PNGs, the F2 error-context from G1b, and the F1 failure excerpts for each run.
- `probe/`: `probe2.cjs` and the networkidle probe tallies.

**Reproduce the per-test diff** from this directory:

```
diff <(cut -f1,3,4,5 ../checkpoint-1/raw/<L>.tsv | sort) <(cut -f1,3,4,5 raw/<L>.tsv | sort)
```

It is empty for G1-lifecycle, G1-default and all four G2 labels. For G1-depth-ii it shows exactly F1, F2, F3 and the 5 cascades.

## Environment left behind

- `bash compose/scripts/e2e-down.sh` was run at 15:26 SAST. 0 `e2e-*` containers remain and the volumes were removed. `caffeinate` was stopped.
- The worktree is back at `150bc743e`, with a clean `git status`.
  - The screenshot baselines were archived, not committed: `/tmp/pr1749/C1-baselines.tgz`, `PR-baselines-all.tgz`, `PR2-baselines.tgz`, `MAIN-baselines.tgz`.
  - `node_modules` was installed with `pnpm install --frozen-lockfile`.
- Nothing was committed.
- The KC dev stack, the main checkout and the Maven-verify worktree were not touched. :3002 was free before every portal suite.
