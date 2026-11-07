# PR #1750 (G89 Batch H: document access control): pre-merge e2e gate

**Verdict: PASS. No e2e regression.**

- All 7 required suites meet their baseline exactly, and every scored run has an **empty per-test diff** against the prior gate's TSVs.
- 0 failures in any scored run, so nothing is classified (a) or (b).
- The only event was the optional login-bare suite. It hit the **G89-F46** Google-Fonts build flake on its first 3 attempts, in the portal production `next build` before any test ran. That is class (c). It passed 1 / 0 / 0 on attempt 4, after a standalone build showed the font fetch had recovered. The portal code is byte-identical to main (see E1).
- The portal client still downloads a shared Statement of Account. The shared statement is trust-flagged (`contains_trust_data = t`), and the client downloaded it (`portal.document.downloaded` audit row).

## Source and recipe

- **Source:** detached worktree `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp3` at `53cabef577950a0edf726deb7ff41ae40aa0d09a` (PR head). The merge base is main `c38616dd5` (#1749 merged). `pwd` was confirmed and the tree was clean at the start.
- **What changed vs main:** backend, frontend, and tenant V14 `contains_trust_data`. `git diff --stat c38616dd5 HEAD -- portal packages pnpm-lock.yaml package.json pnpm-workspace.yaml` is **empty**: no portal, shared-package or lockfile change.
- **Recipe:** the Checkpoint 1 / PR #1749 recipe, unchanged (`../pr1749-e2e/README.md`, `../pr1749-e2e/rerun-1b8da6a18/README.md`).
  - Stacks ran in order: C1 capture legal → G1 scored legal + default → C2 capture portal → G2 scored portal.
  - Every stack was built with `bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh`, nohup-detached. `e2e-reseed.sh` was never used, and neither was a builder prune.
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before the Playwright flags.
- **Driver:** `raw/lib.sh` is `/tmp/pr1749s/lib.sh` with only `WT`, `RAW` and `W` changed, plus a once-only G89-F46 retry in `stack_up`. That retry was never triggered: every stack build returned rc 0 on its first attempt.
  - `raw/run.sh` is `p1.sh` … `p4.sh` of the #1749 gate concatenated, plus a test-results copy after each scored legal run (avoiding the #1749 E2 overwrite) and DB evidence queries.
  - `raw/retry*-login-bare.sh` are the F46 retries (E1).
- **Node:** v20.11.1 was first on PATH. Every run, portal vitest included, logs `node=v20.11.1 envnode=v20.11.1`, resolved through `pnpm exec node -v` (`raw/timeline.txt`).
- **Dependencies:** `pnpm install --frozen-lockfile` was run under node v20.11.1 (rc 0).
- **Run window:** 2026-11-07, 21:31–22:15 SAST, on AC power, with `caffeinate -dims` held throughout.
- **Untouched:** the main checkout, the Maven verify in the other worktree, and the stopped KC app services. The KC infrastructure containers (`b2b-*`) stayed up throughout.

## Verdict table

The counts come from the Playwright JSON `.stats` (`raw/<label>.json`), cross-checked against the list reporter (`raw/<label>.txt`). The per-test diff is `diff <(cut -f1,3,4,5 <baseline>.tsv | sort) <(cut -f1,3,4,5 raw/<label>.tsv | sort)`.

| Suite | Stack | Passed / failed / skipped / did not run | Baseline | Per-test diff (baseline TSV) | Status |
|---|---|---|---|---|---|
| legal-lifecycle | G1 | **105 / 0 / 0 / 0** (3.0 m) | 105 / 0 / 0 | empty (`../pr1749-e2e/rerun-1b8da6a18/raw/G-lifecycle.tsv`) | PASS |
| legal-depth-ii | G1 | **40 / 0 / 4 / 0** (1.9 m) | 40 / 0 / 4 / 0 | empty (`../pr1749-e2e/rerun-1b8da6a18/raw/G-depth-ii.tsv`) | PASS |
| default (`test:e2e`) | G1 | **165 / 0 / 14 / 0** (1.3 m) | 165 / 0 / 14 | empty (`../pr1749-e2e/raw/G1-default.tsv`): same 14 skips | PASS |
| portal vitest | — | **49 files / 278 tests passed** (node v20.11.1) | 278 | — | PASS |
| portal 90-day | G2 | **13 / 0 / 0 / 0** (23.9 s) | 13 / 0 / 0 | empty (`../pr1749-e2e/raw/G2-90day.tsv`) | PASS |
| portal responsive | G2 | **21 / 0 / 0 / 0** (21.3 s) | 21 / 0 / 0 | empty (`../pr1749-e2e/raw/G2-responsive.tsv`) | PASS |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | **2 / 0 / 0 / 0**: both passed, not skipped | 2 / 0 / 0 | empty (`../pr1749-e2e/raw/G2-isolation.tsv`) | PASS |
| login-bare (optional, production portal) | G2 | **1 / 0 / 0 / 0** (12.0 s) on attempt 4. Attempts 1–3 hit the F46 build error with 0 tests run | 1 / 0 / 0 | empty (`../pr1749-e2e/raw/G2-login-bare.tsv`) | PASS after F46 retry (E1) |

- **Snapshot writes on scored runs:** 0 "writing actual" lines in every scored run (`raw/stacks.txt`).
- **Capture runs** (frame counts equal the prior gate):
  - C1-lifecycle: 105 / 0 / 0, 26 frames.
  - C1-depth-ii: 40 / 0 / 4, 23 frames.
  - C2-90day: 13 / 0 / 0, 11 frames.
  - C2-responsive: 21 / 0 / 0, 21 frames.

### Areas this PR touches: observed on the scored runs

| PR change | Observation |
|---|---|
| Template generate / preview / generate-docx need matter view access | `document-generation.spec.ts` (G1-default): all 5 passed. These are `:78` preview, `:87` generate from the matter, `:103` generate from the client page, `:120` Download PDF and `:134` generated-documents list. The depth-ii generate flows also passed (Offer to Purchase, engagement letter, SoA, closure letter). |
| Statements endpoints need matter access | depth-ii `day-30-45-soa-writeoff` Generate SoA, Save Statement and the post-write-off statement all passed. Lifecycle SoA passed. |
| V14 `contains_trust_data` flag | G1 DB after depth-ii (`raw/stacks.txt`): every `statement-of-account-*` row is `true`. Fee notes, payment receipts, the engagement letter, the OTP and closure letters are `false`. |
| Trust-locked download button (no VIEW_TRUST) | The scored suites drive Alice (owner, VIEW_TRUST), so downloads stayed enabled and every download test passed. The suites do not exercise the no-VIEW_TRUST path. That path is covered by the PR's `trust-locked-download.test.tsx` and `GeneratedDocumentTrustAccessIntegrationTest`, not by this gate. |
| **Portal client download of a shared statement** | The G2 DB shows the shared `statement-of-account-website-redesign-2026-11-07.pdf` is `scope=PROJECT`, `visibility=PORTAL`, `documents.contains_trust_data = t` and `generated_documents.contains_trust_data = t` (`raw/G2-shared-statement-flag.txt`). `day-45-75.spec.ts:124` "Client downloads the Statement of Account from the matter page" **passed**, with its `presign-download` response OK. The audit row `portal.document.downloaded \| statement-of-account-website-redesign-2026-11-07.pdf \| 19:55:02Z` is present, and `day-85-90:110` "Activity trail shows this run's … download" passed. The portal reads through `PortalQueryService.getPresignedDownloadUrl`, which the PR does not change, and is not gated by `TrustDataVisibility`. |
| Refusals on legitimate actors | The G2 backend log has 0 "Trust details are not available" lines and 0 WARN/ERROR lines about generated documents, statements or presign. |

## Stack table

Full checks are in `raw/stacks.txt`, with build logs in `raw/stack-{C1,G1,C2,G2}.log`.

| Stack | Purpose | sha | Build (SAST) | rc | Backend image | Frontend image | Tenant | Tenant / global Flyway | Pristine at start |
|---|---|---|---|---|---|---|---|---|---|
| C1 | capture legal | `53cabef57` | 21:32:06 → 21:33:54 | 0 | `1d26f2002ca0` | `728cc5a6b52e` (no baselines in context) | `tenant_7d218705360b` | **14 / 5** | `Website Redesign\|ACTIVE` only; `portal_deadline_view` = 0 |
| G1 | **scored** legal + default | `53cabef57` | 21:39:00 → 21:40:26 | 0 | `1d26f2002ca0` | `9e307608ad1d` (C1 baselines in context, the known 636A-c1 effect) | same | **14 / 5** | same; C1 volumes `Removed` |
| C2 | capture portal | `53cabef57` | 21:46:43 → 21:47:25 | 0 | `1d26f2002ca0` | `9e307608ad1d` | same | **14 / 5** | same; volumes `Removed` |
| G2 | **scored** portal | `53cabef57` | 21:53:57 → 21:54:39 | 0 | `1d26f2002ca0` | `9e307608ad1d` | same | **14 / 5** | same; volumes `Removed` |

- Flyway on every stack is tenant `14|g89 contains trust data` and global `5|portal strip matter type suffix`.
- Every stack was healthy: backend `UP`, frontend / mock-idp / mailpit 200, and `E2E Boot-Seed Complete!` logged.
- Compose working_dir was `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp3/compose` for all four stacks.
- **G89-F46 on stack builds:** it did not occur. All 4 builds returned rc 0 on the first attempt.

**Portal magic-link spacing** (`raw/stacks.txt`): before each portal suite after the first, the driver waited until the newest token was at least 330 s old, ran `docker restart e2e-backend`, waited for health, and confirmed :3002 was free.

| Before | Token age when released |
|---|---|
| C2-responsive | 330 s |
| G2-responsive | 332 s |
| G2-isolation | 333 s |
| G2-login-bare attempt 1 | 332 s |
| attempt 2 / 3 / 4 | 368 s / 410 s / 464 s (each still had a backend restart) |

- The cumulative "Exchanged magic link" counts were **1 / 2 / 4 / 5** after 90-day / responsive / isolation / login-bare, identical to CP1 and #1749. The failed login-bare attempts spent no exchange: the count stayed at 4 until attempt 4.
- G2-90day post-checks: `portal_deadline_view` = 0, and `REQ-0002 | IN_PROGRESS` was created by the run.

## Classification

| Item | Class | Evidence |
|---|---|---|
| Any scored failure in the 7 required suites | **none** | per-test diffs above are all empty |
| (a) product regression | none | No 403 or 404 against a legitimate actor. The owner's generate, preview, statement and download flows passed, the portal client's shared-SoA download passed, and the backend log has 0 trust-refusal lines. |
| (b) spec stale by an intended change | none | — |
| (c) E1: login-bare attempts 1–3, G89-F46 | (c) environment | See E1 |

### E1: login-bare G89-F46 (Google-Fonts build flake), class (c)

- **Attempt 1 (22:12:22).** The Playwright `webServer` (`pnpm run build && pnpm run start`) failed in `next build` with 24 × `Module not found: Can't resolve '@vercel/turbopack-next/internal/font/google/font'` (`ibm_plex_sans` in `portal/app/layout.tsx`). 0 tests ran (`raw/G2-login-bare-attempt1-F46.txt`). This is the same signature as CP1's `stack-g1-attempt1-font-fetch.txt`.
- **Attempt 2 (22:12:57, the "retry once" from the brief).** It failed in 1.7 s with the same 24 errors (`raw/G2-login-bare-attempt2-F46-cached.txt`). The speed is consistent with Turbopack's persistent `portal/.next/cache` replaying the failed font result, so this was not a fresh fetch.
- **Attempt 3 (22:13:39).** I removed `portal/.next`, which is gitignored build output, and ran again. It failed with the same 24 errors (`raw/G2-login-bare-attempt3-F46-cleancache.txt`), so the fetch itself was still failing at that moment.
- **Standalone build (22:14).** `cd portal && pnpm run build`, under the same env and node v20.11.1, returned **rc 0 with 0 font errors** (`raw/portal-build-standalone-1.txt`). The font fetch had recovered: `curl https://fonts.googleapis.com/css2?family=IBM+Plex+Sans` returned 200 at that time.
- **Attempt 4 (22:14:34).** 1 / 0 / 0 (`raw/G2-login-bare.*`), with an empty per-test diff vs #1749.
- **Why this is not the PR:** the PR changes no file under `portal/`, `packages/` or the lockfile, so the portal build is byte-identical to main `c38616dd5`. The error is a font fetch at build time, before any request reaches the backend.
- **Note for the orchestrator:** this exceeds the brief's "retry once". Attempts 2 and 3 are recorded as failed. Attempt 2 was not an independent retry, because of the cached failure. login-bare is optional and not in the baseline table, so the verdict on the 7 required suites does not depend on it.

## Raw files (`raw/`)

- **Scored:**
  - `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}`;
  - `G2-{90day,responsive,isolation,login-bare}.{json,tsv,txt}`;
  - `G2-login-bare-attempt{1-F46,2-F46-cached,3-F46-cleancache}.{json,tsv,txt}`.
  - TSV columns: file, line, title, status, last-result.
- **Capture:** `C1-{lifecycle,depth-ii}.summary.txt` and `.tsv`, `C1-depth-ii.txt`, `C2-{90day,responsive}.summary.txt`.
- `portal-vitest.txt`
- **Stacks and timing:**
  - `stacks.txt`: per-stack checks, image ids, Flyway, pristine state, spacing, exchange counts, `generated_documents` trust flags (G1 after depth-ii and G2), the G1 projects list after default, and the G2 log scan and portal download audit row;
  - `timeline.txt`;
  - `stack-{C1,G1,C2,G2}.log`.
- **DB evidence:** `G2-shared-statement-flag.txt`.
- **E1:** `portal-build-standalone-1.txt`.
- **Driver:** `lib.sh`, `run.sh`, `retry-login-bare.sh`, `retry2-login-bare.sh`, `retry3-login-bare.sh`.

## Environment left behind

- `bash compose/scripts/e2e-down.sh` ran at 22:15:13 with rc 0. 0 `e2e-*` containers remain, and 0 `docteams-e2e_*` volumes remain. `caffeinate` was stopped.
- The worktree is clean at `53cabef57`, with `git status` empty.
  - The 107 untracked baselines and curated PNGs from the captures were archived to `/tmp/pr1750/PR1750-baselines.tgz` and then git-cleaned.
  - Test-results copies are kept at `/tmp/pr1750/G1-{lifecycle,depth-ii,default}-test-results`.
  - `node_modules` was installed with `pnpm install --frozen-lockfile`.
- Nothing was committed.
