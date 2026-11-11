# PR #1752 (G89 Batch E: layout, copy, small backend LOWs; MEDIUMs F17 and F50): pre-merge e2e gate

**Verdict: PASS on the final PR head `6fcd91abe`. No e2e regression is attributable to the PR.**

- Every required suite meets its baseline on the final head, with an empty per-test diff against the prior gate (table below).
- The gate started on head `6f9f16e02`. It found one class (b) failure: `day-75-85-closure.spec.ts:391` matched audit rows by the raw event key, which G-D10a intentionally removed. The coordinator fixed the spec in `6fcd91abe`, a spec-only commit. On that head, depth-ii is **40/0/4** in all three runs: functional, capture and scored.
- **Functional legal-lifecycle (`--ignore-snapshots`) on `6fcd91abe`: 105/0/0, twice.** The two runs used fresh stacks F and D. They exercise the tests the PR changes behaviour in: day-45 reconciliation (F17), day-60 interest create, approve and post (F41), and day-90 Section 35 (F50, F47).
- **Screenshot-scored legal-lifecycle: 72/5/28 on both PR-head scored runs.** The cause is the known frame `day-30-billing.spec.ts:90` / `day-30-fee-note-tariff-completed` (G89-F67) and its serial cascade.
  - **Main `1d2b5d0e3` reproduces it per test, identically: 72/5/28.** It is class (c) and pre-existing (C2 below).
  - The diff images show identical fee-note content in every case. The variations are only scroll offset, row order of same-date entries, and run-ID text reflow. Payment History is not in the frame: the fee note is a DRAFT, and it has no payments.
- **Default `test:e2e`:**
  - **165/0/14 in the clean window**, on the PR head `6fcd91abe` after 02:00 SAST. The per-test diff against #1751 is empty, with the same 14 skips.
  - **164/1/14 in the 00:00–02:00 window.** The one failure is `project-time.spec.ts:238`: class (c), G89-F66, a UTC-date spec bug. It is proven from the DB.
- **Portal (G89-F44 changes the Firm actions feed):**
  - vitest 278/278;
  - 90-day 13/0/0, responsive 21/0/0 and isolation 2/0/0, each with an empty per-test diff;
  - login-bare (optional) 1/0/0.
- The three audit specs the FE reviewer flagged did not fail on the raw key:
  - `audit-log/audit-log-smoke.spec.ts:19` **passed**;
  - `audit-log/matter-closure-audit-tab.spec.ts:52` **passed**;
  - `admin-audit-30day/admin-audit-lifecycle.spec.ts` was all 8 **skipped**, as in the baseline. They are among the 14 baseline skips.

## Source and recipe

- **Worktree:** detached `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp5`, with `pwd` confirmed and a clean tree.
  - Main sequence (vitest, C1, G1, C2, G2) ran at **`6f9f16e02`**.
  - Re-runs (F, C, G, D) ran at **`6fcd91abe`** = `6f9f16e02` + `test(e2e): depth-ii closure audit check no longer matches the raw event key text`. That commit changes only `frontend/e2e/tests/legal-depth-ii/day-75-85-closure.spec.ts`, +3/−1.
  - The main control ran at **`1d2b5d0e3`**, which is the merge base (#1751 merged). I checked it out in the same worktree, then returned to `6fcd91abe`.
- **What changed vs main:**
  - backend, 13 production files: trust interest, Section 35 data pack, `TrustTransactionService`, reporting CSV, portal activity allow-list, audit and activity labels, dashboard cache eviction, AI 400, and the legal-za pack v13→v14 plus trust-reports pack v2→v3 (seeders, not Flyway);
  - frontend, 22 production files.
  - **No Flyway migrations.** No `portal/` or `packages/` code, and no lockfile change.
- **Recipe:** the #1750 / #1751 recipe (`../pr1750-e2e/README.md`, `../pr1751-e2e/README.md`).
  - Every stack was fresh: `bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh`, nohup-detached. No reseed and no prune.
  - Capture-then-score: C stacks ran `--update-snapshots=all`, then G stacks were scored.
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before Playwright flags.
  - Portal suites were spaced by at least 330 s, with an `e2e-backend` restart before each.
- **Driver:** `raw/lib.sh` is the #1751 `lib-final.sh` with only `WT`, `RAW` and `W` changed. I added `wait_no_foreign_stack`, which polls every 540 s while any `e2e-*` container belongs to another worktree. It never had to wait: "no foreign e2e stack" before all 10 builds.
  - `raw/run.sh` is the main sequence.
  - `raw/probe.sh` covers F, C and G on `6fcd91abe`.
  - `raw/main-control.sh` covers the main control C and G, then D on `6fcd91abe` after 02:00.
- **Node:** v20.11.1 was first on PATH, and every run logs `node=v20.11.1 envnode=v20.11.1`. `pnpm install --frozen-lockfile` returned rc 0.
- **Timing:**
  - The brief said to start at or after 02:00. The founder overrode that: the launcher was cancelled at 00:54:47 and the run started at 00:55:10 SAST (`raw/wait.log`).
  - The main sequence and the F, C and G re-runs fell inside the 00:00–02:00 window. D ran at 02:03–02:09, when the UTC date equalled the SAST date.
  - `caffeinate -dims` was held throughout.
- **KC app services:** stopped before the run, and restarted at the end (backend, gateway, frontend and portal all ready). The KC infrastructure containers (`b2b-*`) were untouched.

## Verdict table

The counts come from Playwright JSON `.stats`: passed / failed / skipped. The per-test diff is `diff <(cut -f1,3,4,5 <prior>.tsv | sort) <(cut -f1,3,4,5 <run>.tsv | sort)`. The priors are #1751 `attempt3-pr-clean/G1-{depth-ii,default}.tsv`, #1751 `attempt2-pr-midnight/G1-lifecycle.tsv`, and #1750 `G2-*.tsv`.

| Suite | Baseline | Final-head result (`6fcd91abe`) | Per-test diff | Earlier head `6f9f16e02` | Status |
|---|---|---|---|---|---|
| legal-lifecycle, **functional** (`--ignore-snapshots`) | 105/0/0 | **105/0/0** (F, 01:36) and **105/0/0** (D, 02:03) | empty, both | — | **PASS** |
| legal-lifecycle, screenshot-scored | 105/0/0 | 72/5/28 (G) | = main 72/5/28, per test | 72/5/28 (G1) | (c) C2, pre-existing on main |
| legal-depth-ii | 40/0/4 | **40/0/4** scored (G), **40/0/4** functional (F and D), capture 40/0/4 (C) | empty, all 3 | 39/1/4 (G1), and C1 capture 39/1/4 | **PASS** (the earlier failure was (b) B1, fixed in `6fcd91abe`) |
| default `test:e2e` | 165/0/14 | **165/0/14** (D, clean window) | **empty**, same 14 skips | 164/1/14 (G1, 01:08) | **PASS** (the G1 failure is (c) C1, G89-F66) |
| portal vitest | ≥278 | — | — | **49 files / 278 passed** | **PASS** |
| portal 90-day | 13/0/0 | — | empty | **13/0/0** (G2), 0 writing-actual | **PASS** |
| portal responsive | 21/0/0 | — | empty | **21/0/0** (G2), 0 writing-actual | **PASS** |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | 2/0/0 | — | empty | **2/0/0**, both ran | **PASS** |
| login-bare (optional) | 1/0/0 | — | — | **1/0/0**, no G89-F46 | PASS |

- **Portal suites ran on `6f9f16e02` only.** The final commit is a single frontend e2e spec. It changes nothing the portal stack builds or runs: the backend image is identical (`7fc4f588f6a8` on every PR-head stack) and there is no portal code change.
- **Capture runs:**
  - C1 lifecycle 105/0/0 (26 frames); C1 depth-ii 39/1/4 (22 frames, B1);
  - C2 90-day 13/0/0 (11 frames); C2 responsive 21/0/0 (21 frames);
  - re-run C lifecycle 105/0/0 and C depth-ii 40/0/4;
  - main C lifecycle 105/0/0 (26 frames).
- **Snapshot writes on scored runs:** 0 in every scored run (`writing-actual=0` in each `stacks.txt`).

### Areas this PR touches: observations

| PR change | Observation |
|---|---|
| F17 reconciliation list loads | `day-45-recon-prescription.spec.ts:40` "Navigate to Trust Reconciliation page" and `:50` "Start a bank reconciliation … reach the statement upload step" **passed** in F, D and the clean scored runs. No backend WARN/ERROR mentions reconciliation (`raw/backend-log-scan.txt`). |
| F50 Section 35 lists every posted run, with formatted PDF money; F47 report text wraps | `day-90-review.spec.ts:118` "Generate Section 35 Data Pack report" **passed** in F and D. It asserts the Section / Item / Amount columns. The spec does not count interest rows or read the PDF, so the every-run and rand-format behaviour is covered by the PR's integration tests, not by this gate. |
| F41 interest Posted Date in SA date | day-60 create (`:97`), approve (`:173`) and post (`:194`) **passed** in F and D. The F DB has the run `POSTED`, with `posted_at = 2026-11-10T23:38:52Z`, which is 11 Nov 01:38 SAST and so a cross-midnight case (`raw/rerun-lifecycle/stacks.txt`). The spec does not assert the Posted Date cell, so the rendered SA date was **not observed in UI** by this gate. |
| F44 portal Firm actions: trust deposit and interest events | The G2 DB has a matter-scoped `trust_deposit.recorded` for Acme Corp, with `project_id` and `customer_id` in the details. The F DB has 3 matter-scoped `trust_deposit.recorded` and 2 `trust_interest_credit.recorded` rows, each carrying `project_id` and `customer_id`. Portal 90-day passed, and the `day-90-activity-trail` frame matched the capture. No portal spec asserts a trust row on the Firm tab, so the feed content is covered by `PortalActivityTrustEventsIntegrationTest`. |
| F57 SoA "Fees / Disbursements billed in period" | depth-ii `day-30-45-soa-writeoff` passed everywhere: `:273` Generate SoA (HTML preview), `:294` Save, and `:425` statement after write-off. That includes `:157`, `Total disbursements:\s*<amount>`, which is unchanged in the Disbursements section. |
| G-D10a: no raw event key in audit rows | Caused B1 (below). On G1, the error-context snapshot shows the filtered rows reading only `Matter closure closed` (the label). |
| G88-F27 "Billed", G88-F2/F65 Team list, F7 KPI grid, F8/F15/F9 wrapping and below-xl lines | No spec in these suites failed on these. Every visual baseline was re-captured on the PR head, so the scored frames reflect the new layout. |
| Backend log | WARN classes on G1, F and G2: PDFBox font fallback, `No recipients found for DOCUMENT_GENERATED` (11 on G1, the same class as #1751), draft invoice without tax number, and `Forbidden … /time-summary/by-member` (12 on G1, 9 on F). The by-member path is not in the PR diff. **0 ERROR**, and 0 WARN lines from trust, interest, reconciliation, Section 35 or statement code. |

## Stack table

Full checks are in each `stacks.txt`. Every stack:
- built with rc 0;
- had backend `UP`, frontend / mock-idp / mailpit 200, and `E2E Boot-Seed Complete!`;
- had tenant `tenant_7d218705360b`, with `Website Redesign|ACTIVE` only and `portal_deadline_view` = 0 at start;
- had compose working_dir `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp5/compose`;
- removed the previous stack's volumes;
- ran Flyway **tenant `14|g89 contains trust data` / global `5|portal strip matter type suffix`**. That is **14 / 5**, as expected with no migrations.

| Stack | Purpose | sha | Build (SAST) | Backend image | Frontend image |
|---|---|---|---|---|---|
| C1 | capture legal | `6f9f16e02` | 00:55:19 → 00:56:53 | `7fc4f588f6a8` | `95f598caf83e` |
| G1 | scored legal + default | `6f9f16e02` | 01:02:08 → 01:03:51 (**attempt 1 G89-F46**, then retry) | `7fc4f588f6a8` | `3238458127a9` |
| C2 | capture portal | `6f9f16e02` | 01:09:48 → 01:10:27 | `7fc4f588f6a8` | `3238458127a9` |
| G2 | scored portal | `6f9f16e02` | 01:16:47 → 01:17:26 | `7fc4f588f6a8` | `3238458127a9` |
| F | functional lifecycle + depth-ii, project-time proof | `6fcd91abe` | 01:35:22 → 01:36:49 | `7fc4f588f6a8` | `cd4ab962af3b` |
| C | capture lifecycle + depth-ii | `6fcd91abe` | 01:41:54 → 01:42:34 | `7fc4f588f6a8` | `cd4ab962af3b` |
| G | scored lifecycle + depth-ii, project-time | `6fcd91abe` | 01:47:38 → 01:49:05 | `7fc4f588f6a8` | `6854e76d3626` |
| main C | main control capture | `1d2b5d0e3` | 01:54:12 → 01:54:51 | `0e4a0d42ab62` (= #1751 head backend) | `08c9c3692514` |
| main G | main control scored | `1d2b5d0e3` | 01:57:54 → 01:59:19 | `0e4a0d42ab62` | `823aac5b269e` |
| D | clean window: functional lifecycle + depth-ii, then default | `6fcd91abe` | 02:01:52 → 02:03:26 | `7fc4f588f6a8` | `6c78ab2d7464` |

- **G89-F46 on the G1 build:**
  - Attempt 1 (01:02:08–01:02:29) failed in the frontend Docker `next build`. The log shows 24 × `Module not found: Can't resolve '@vercel/turbopack-next/internal/font/google/font'` (`ibm_plex_sans`); see `raw/stack-G1-attempt1-FAILED.log`.
  - The once-only retry in `stack_up` returned rc 0.
  - This is class (c), and no tests had run yet. The other 9 builds succeeded first time.
- The G frontend image differs from C because the C baselines are in the build context. This is the known 636A-c1 effect.

**Portal spacing** (`raw/stacks.txt`):

| Before | Token age when released |
|---|---|
| C2-responsive | 333 s |
| G2-responsive | 332 s |
| G2-isolation | 331 s |
| G2-login-bare | 332 s |

Each release was followed by a backend restart and a health wait, with :3002 free. The cumulative "Exchanged magic link" count was **1 / 2 / 4 / 5**, identical to #1749 and #1750. After G2 90-day, `REQ-0002|IN_PROGRESS` was created by the run.

## Classification

| ID | Failure | Where | Class | Status |
|---|---|---|---|---|
| B1 | `legal-depth-ii/day-75-85-closure.spec.ts:391` "Alice: Verify override audit event captures override_used=true + justification" | C1 capture and G1 scored, `6f9f16e02` | **(b) spec stale by an intended change (G-D10a)** | Fixed by the coordinator in `6fcd91abe`. That head is 40/0/4 in F, C, G and D. |
| C1 | `projects/project-time.spec.ts:238` "7. My Work lists today's time entries" | G1 default (01:08); also the standalone F (01:41) and G (01:53) runs, all in the window | **(c) G89-F66, UTC-date spec bug** | Passed in D after 02:00 (165/0/14). |
| C2 | `legal-lifecycle/day-30-billing.spec.ts:90` frame `day-30-fee-note-tariff-completed` (ratio 0.02), then the serial cascade (`day-45:160`, `day-60:97`, `day-90:95`, `day-90:326`, and 23 skips) | G1 (`6f9f16e02`), G (`6fcd91abe`) **and main `1d2b5d0e3`** | **(c) G89-F67, pre-existing frame nondeterminism** | Reproduces on main per test. The functional lifecycle is 105/0/0. |
| E1 | G1 stack build attempt 1, G89-F46 Google Fonts | stack build | (c) environment | Retry rc 0. |
| (a) | product regression | — | **none** | — |

### B1: (b) audit rows no longer print the raw event key

- **Assertion** (helper `openAuditLogFilteredBy`, `day-75-85-closure.spec.ts:86-90` at `6f9f16e02`):

  ```ts
  page.locator('[data-testid^="audit-row-"]:not([data-testid^="audit-row-details-"])')
      .filter({ hasText: eventType })   // eventType = "matter_closure.closed"
      .first();
  await expect(row).toBeVisible({ timeout: 15_000 });   // element(s) not found
  ```

- **Intended change.** `audit-log-client.tsx` `Row` dropped `<div className="font-mono …">{event.eventType}</div>`. The PR comment reads: "The label only: the stored key is never user-visible (ADR-357, G-D10a)".
- **Evidence.** The URL assertion `eventType=matter_closure.closed` passed, so the server filter applied. The error-context snapshot shows two filtered rows, `row "… INFO Matter closure closed Alice Owner Matter e48f3093"`, whose text has the label but no key (`raw/failure-artifacts/{C1,G1}-depth-ii/…/error-context.md`).
- **Proposed expectation**, as reported to the coordinator: stop matching on the key text. The URL-asserted server filter already pins the type. Alternatively, match the label, "Matter closure closed" or "Matter Closure Override Used".
- **Applied:** the coordinator's commit `6fcd91abe` drops the key-text match.
- **Proof the fix works:** `:391` passed in F (functional), C (capture), G (scored) and D (functional).

### C1: G89-F66 (spec computes "today" from the UTC date)

- **Spec:** `project-time.spec.ts:172` and `:211` post `date: new Date().toISOString().split("T")[0]`, which is the UTC date. My Work's "Today" is the SA date. The error context shows "Today" without the `Non-billable entry` row.
- **DB proof, F stack** (`raw/rerun-lifecycle/stacks.txt`, SAST `2026-11-11 01:41:54`, UTC `2026-11-10 23:41:54`):
  - The lifecycle's UI-logged entries are dated `2026-11-11`, the SA date.
  - The spec's `Rate snapshot test 3qo5` and `Non-billable entry 3qo5` are dated **`2026-11-10`**, the UTC date.
  - The G stack (01:53) shows the same: `j13k` entries dated `2026-11-10`.
- **Clean window, D stack** (SAST `02:09`, UTC `00:09`): the spec's entries are dated `2026-11-11`, and `:238` passed. The default suite was 165/0/14 with an empty diff.
- This is the same failure as #1751 C1, which reproduced on main `4f3323525` in the same window. The PR does not touch time entries or My Work.

### C2: G89-F67 (fee-note tariff frame), with a main control

- **Per-test identity.** The non-passing test sets are identical across all three runs:
  - `raw/G1-lifecycle.tsv` (PR `6f9f16e02`);
  - `raw/rerun-lifecycle/G-lifecycle.tsv` (PR `6fcd91abe`);
  - `raw/main-1d2b5d0e3/G-lifecycle.tsv` (main).

  They are also identical to #1751 `attempt3-pr-clean` and `main-4f3323525-clean`.
- **Diff images.** Expected, actual and diff are in each `failure-artifacts/…day-30-billing…/`. In every case the content is the same: the same lines, amounts, Subtotal R 4 767,50 and VAT R 715,13.
  - **G1 (PR):** scroll offset only. The actual landed at the page bottom (Total R 5 482,63 and "No custom fields configured"), while the expected was at the line items.
  - **G re-run (PR):** the two same-date (`2026-11-11`) Carol time-entry lines swapped order. This is an unstable sort on a date tie. The run-ID width also reflowed "Collate medical records for the letter of demand" from 3 lines to 2. There is no scroll difference.
  - **Main:** scroll offset (the diff overlays the bottom-of-page variant on the line-item view).
- **Payment History** is not in this frame. The fee note is a fresh DRAFT with no payments, and the frame is the line-item and totals area. The PR's below-`xl` Payment History change cannot affect it.
- The PR's other fee-note page change (G88-M9 remount key on `GeneratedDocumentsList` / `InvoiceAuditSection`) is below the frame. Main fails the same frame without it.
- **Functional proof:** the cascade-skipped tests pass on the PR head with snapshots ignored, 105/0/0 twice. Those are the day-30 fee notes, day-45 payment, day-60 interest and day-90 Section 35 and RBAC.

## Raw files (`raw/`)

- **Main sequence (`6f9f16e02`):**
  - scored: `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}` and `G2-{90day,responsive,isolation,login-bare}.{json,tsv,txt}`;
  - capture: `C1-*.{summary.txt,tsv,txt}` and `C2-*.summary.txt`;
  - `portal-vitest.txt`, `stacks.txt` (including the G1 interest_runs, trust audit events and generated_documents, and the G2 trust audit events), `timeline.txt`, `stack-*.log` and `stack-G1-attempt1-FAILED.log`.
- **`rerun-lifecycle/` (`6fcd91abe`):**
  - `F-{lifecycle,depth-ii}-functional.*`, `F-project-time.*`;
  - `C-{lifecycle,depth-ii}.*`, with `C-day-30-fee-note-tariff-completed.png`;
  - `G-{lifecycle,depth-ii,project-time}.*`;
  - `stacks.txt`, which holds interest_runs, trust_reconciliations, the trust audit events and the time_entries date proofs.
- **`main-1d2b5d0e3/`:** `C-lifecycle.*`, `G-lifecycle.*`, `stacks.txt` and the failure artifacts.
- **`pr-default-clean/`:** `D-{lifecycle,depth-ii}-functional.*`, `D-default.*`, and `stacks.txt` with the clean-window time_entries proof.
- **`failure-artifacts/`:** C1/G1 depth-ii (B1), G1 default (C1), G1 lifecycle (C2), and `rerun-lifecycle/failure-artifacts/G-lifecycle` (C2). Each has expected / actual / diff PNGs and `error-context.md`.
- **Other:** `backend-log-scan.txt` (G1, F and G2 WARN/ERROR by logger and message); `e2e-down.txt`.
- **Drivers:** `lib.sh`, `run.sh`, `launch.sh` (cancelled), `probe.sh`, `probe-launch.sh`, `main-control.sh` and `wait.log`.

## Environment left behind

- `bash compose/scripts/e2e-down.sh` ran at 02:09:48 SAST with rc 0. 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes remain.
- **KC app services restarted** with `svc.sh start backend gateway frontend portal`. All four are ready: 8080, 8443, 3000 and 3002.
- **Worktree:** clean at **`6fcd91abe`** (moved from `6f9f16e02` on coordinator instruction), with `git status` empty.
  - Untracked baselines and curated PNGs were archived, then removed. The archives are in `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/8adccadf-2ff1-433e-8c02-83d3600dcef2/scratchpad/pr1752/`:
    - `main-1d2b5d0e3/pr-head-baselines.tgz`: the PR-head legal and portal baselines and curated PNGs;
    - `main-1d2b5d0e3/main-baselines.tgz`;
    - `pr-default-clean/D-baselines.tgz`.
  - Full test-results copies are in the same folder.
- `node_modules` was installed with `pnpm install --frozen-lockfile`.
- Nothing was committed.
