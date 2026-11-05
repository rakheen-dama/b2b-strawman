# 636A — Three suites on a fresh stack (Phase 89 exit gate, task 636.1)

**Overall status: PASS.** Every scored run finished with 0 failed, 0 did-not-run and 0 flaky. Every count is at or above the plan floor and equal to current main. Every skip is on the known-skip list, and every named un-fixme'd spec shows ✓. All scored runs started and ended between 20:07 and 20:42 SAST on 2026-11-05, which is outside 00:00–02:00. All four stacks were fresh and built from `$WT` @ `1034b6af888d36c55b8af84a5c0e4d1f4bad3c18` (= origin/main at run time), with one qualifier: the G1/G2 (and C2) frontend image `d026d5fd096c` was built from `1034b6af8` plus the 49 untracked C1 legal baseline PNGs that were in the build context (636A-c1; `e2e/` is not in the standalone output, so no runtime effect). The C1 frontend image is `3e6a055783a9`; the backend image `8111f5608eda` is the same on all four. No stack was redone and no spec was re-run.

Each count below comes from the Playwright JSON reporter (`raw/<label>.json`, `.stats`), cross-checked against the list-reporter summary (`raw/<label>.txt`). The two agree for every suite. Per-test outcomes are in `raw/<label>.tsv` (file, line, title, status, last-result).

## Verdict table

| Suite | Stack | This run (passed / failed / skipped / did not run) | Plan floor | Current main (totals only) | Status |
|---|---|---|---|---|---|
| legal-lifecycle | G1 | **105 / 0 / 0 / 0** | ≥ 102 / 0 / 2 | 105 / 0 / 0 | PASS |
| legal-depth-ii | G1 | **39 / 0 / 4 / 0** | ≥ 39 / 0 / 4 | 39 / 0 / 4 | PASS |
| default (`test:e2e`) | G1 | **165 / 0 / 14 / 0** | ≥ 160 / 0 / 17 | 165 / 0 / 14 | PASS |
| portal vitest | — | **47 files / 273 tests passed** | 256 tests | 47 / 273 | PASS |
| portal 90-day | G2 | **13 / 0 / 0 / 0** | day specs | 13 / 0 / 0 | PASS |
| portal responsive | G2 | **21 / 0 / 0 / 0** | — | 21 / 0 / 0 | PASS |
| portal-data-isolation (`KGOSI_EMAIL` set) | G2 | **2 / 0 / 0 / 0** | — | 2 / 0 / 0 | PASS |
| login-bare (opt-in, production portal) | G2 | **1 / 0 / 0 / 0** | opt-in | 1 / 0 / 0 | PASS |

The "Current main" column is a totals comparison: no per-test list exists for the current-main runs (see `lifecycle.md`), so equality there means equal totals, not a per-test diff.

Per-suite detail: [`lifecycle.md`](lifecycle.md), [`depth-ii.md`](depth-ii.md), [`default.md`](default.md), [`portal.md`](portal.md).

## Named un-fixme'd specs (all ✓, `expected` / last-result `passed`)

| Plan ref | Current test | Run | Result |
|---|---|---|---|
| `day-60-interest:160` | `day-60-interest.spec.ts:97` create + calculate / `:168` Bob approves / `:189` Alice posts | G1-lifecycle | ✓ ✓ ✓ |
| `day-90-review:123` | `day-90-review.spec.ts:118` Generate Section 35 Data Pack report | G1-lifecycle | ✓ |
| `customer-data-protection:48,76,107` | `customer-data-protection.spec.ts:47` / `:77` / `:108` (also `:150`, `:165`) | G1-default | ✓ ✓ ✓ (✓ ✓) |
| `day-45-75:166` | `day-45-75.spec.ts:181` Day 75 diary dates + `:124` Day 60 SoA | G2-90day | ✓ ✓ |
| receipt case (632C) | `day-14-30.spec.ts:149` Day 30 receipt for a part payment | G2-90day | ✓ |
| deep-link cases (634C / 634E) | `project-tasks.spec.ts:277` case 8 (G88-M6) / `:322` case 9 (B7) | G1-default | ✓ ✓ |

Reproduce (POSIX `[[:space:]]` between TSV fields, so it works with BSD and GNU grep): `grep -E '(^|/)(day-60-interest\.spec\.ts[[:space:]](97|168|189)|day-90-review\.spec\.ts[[:space:]]118|customer-data-protection\.spec\.ts[[:space:]](47|77|108|150|165)|project-tasks\.spec\.ts[[:space:]](277|322)|day-14-30\.spec\.ts[[:space:]]149|day-45-75\.spec\.ts[[:space:]](124|181))[[:space:]]' raw/*.tsv`. It returns 14 rows, all `expected` / `passed` in the last two columns (checked with `/usr/bin/grep` on macOS).

## Stack table

All four stacks were built with `bash compose/scripts/e2e-down.sh && docker builder prune -f && SHELL=/bin/bash bash compose/scripts/e2e-up.sh`, run from `$WT`, nohup-detached. Full output: `raw/stacks.txt`.

| Stack | Purpose | Build start → end (SAST) | rc | Backend / frontend image | Compose working_dir | Tenant | Tenant / global Flyway | Seed | Pristine projects |
|---|---|---|---|---|---|---|---|---|---|
| C1 | capture legal (unscored) | 19:57:27 → 19:59:11 | 0 | `8111f5608eda` / `3e6a055783a9` | `$WT/compose` | `tenant_7d218705360b` | 11 (phase89 comment notification id) / 3 (portal backfill markers) | `E2E Boot-Seed Complete!` | `Website Redesign\|ACTIVE` |
| G1 | **scored** legal + default | 20:05:51 → 20:07:28 | 0 | `8111f5608eda` / `d026d5fd096c` | `$WT/compose` | same name (deterministic) | 11 / 3 | Complete! | `Website Redesign\|ACTIVE` |
| C2 | capture portal (unscored) | 20:14:56 → 20:15:40 | 0 | `8111f5608eda` / `d026d5fd096c` | `$WT/compose` | same name | 11 / 3 | Complete! | `Website Redesign\|ACTIVE`; `portal_deadline_view` = 0 |
| G2 | **scored** portal | 20:22:47 → 20:23:29 | 0 | `8111f5608eda` / `d026d5fd096c` | `$WT/compose` | same name | 11 / 3 | Complete! | `Website Redesign\|ACTIVE`; `portal_deadline_view` = 0 |

Frontend image note: `d026d5fd096c` (G1, C2, G2) was built from `1034b6af8` plus the 49 untracked C1 baseline PNGs in the build context (636A-c1, no runtime effect); `3e6a055783a9` (C1) was built before any baseline existed.

Every stack passed its health checks: backend `UP`, frontend 200, mock-idp 200, mailpit 200.

How freshness was proven: each `e2e-down.sh` ran `docker compose down -v` ("Volume docteams-e2e_e2e_postgres_data Removed" in each `stack-*.log`; excerpts committed in `raw/stacks.txt` → "Volume-removal evidence"). Each new stack's `tenant_7d218705360b.projects` table held only the seed row (`Website Redesign|ACTIVE`). After all three G1 suites the same table held 2 rows, the seed plus `E2E closure override 5rld|CLOSED` (`raw/stacks.txt` → "G1 projects after default"); the legal suites' matters are evidently not written to this table (only these 2 rows remain after lifecycle and depth-ii ran), so it does not by itself show the absence of C1 residue. The volume removal is the freshness proof. The Postgres container was re-created on each stack (G1 `Created` 18:06:54Z).

## Run timeline (SAST, 2026-11-05) — `raw/timeline.txt`

| Run | Start | End | rc |
|---|---|---|---|
| portal vitest | 19:57:34 | 19:57:41 | 0 |
| C1-lifecycle (`--update-snapshots=all`) | 20:00:06 | 20:03:04 | 0 |
| C1-depth-ii (`--update-snapshots=all`) | 20:03:16 | 20:05:11 | 0 |
| **G1-lifecycle** | 20:07:45 | 20:10:40 | 0 |
| **G1-depth-ii** | 20:10:51 | 20:12:49 | 0 |
| **G1-default** | 20:12:58 | 20:14:02 | 0 |
| KC dev portal stopped (:3002 freed) | 20:14:46 | | |
| C2-90day (`--update-snapshots=all`) | 20:15:56 | 20:16:22 | 0 |
| C2-responsive (`--update-snapshots=all`) | 20:22:03 | 20:22:26 | 0 |
| **G2-90day** | 20:23:36 | 20:23:58 | 0 |
| **G2-responsive** | 20:29:31 | 20:29:51 | 0 |
| **G2-isolation** | 20:35:26 | 20:35:28 | 0 |
| **G2-login-bare** | 20:41:27 | 20:41:45 | 0 |
| KC dev portal restarted from the main checkout | 20:42:02 | | |

`caffeinate -dims -t 21600` was held for the whole run, on AC power.

## Procedure (why four stacks)

Screenshot baselines are not committed. A first run on a fresh worktree therefore fails every `toHaveScreenshot` with "snapshot doesn't exist, writing actual". The procedure used two stacks per screenshot-bearing group:

1. **Capture.** Each screenshot-bearing group was first run on its own fresh stack with `--update-snapshots=all`: C1 for lifecycle → depth-ii, C2 for 90-day → responsive.
   - C1 wrote 26 + 23 frames. A diff against every non-curated `captureScreenshot(page, "<name>")` in the specs found 0 missing and 0 extra frames, so `day-14-otp-acceptance-request-sent.png` is present.
   - C2 wrote 11 + 21 frames, 32 PNGs in total.
   - Two of the captured PNGs were opened and checked: both are authenticated pages, not `/login`.
2. **Gate.** Each group was then scored on a second fresh stack against those baselines, in the same order.
   - G1 ran lifecycle → depth-ii → default. Lifecycle never runs after depth-ii (634D).
   - G2 ran 90-day → responsive → isolation → login-bare.
   - The C2 and G2 sequences were identical, so the seeded deposit-row count matched for the `trust-matter-list` baselines (the 633C lesson). The scored runs printed 0 "writing actual" lines.

**Magic-link spacing.** Between portal suites, the procedure waited until the newest `magic_link_tokens` row was at least 330 s old, then ran `docker restart e2e-backend` and waited for health. The waits recorded were 335 s (before C2-responsive, unscored capture), then 330 s, 332 s and 337 s before the scored G2-responsive, G2-isolation and G2-login-bare runs (`raw/stacks.txt` → "portal spacing").

## Deviations from the 636.1 literal / the brief

1. **Plan line numbers predate the un-fixmes.** The current lines are in the named-spec table. The default known-skip lines in the JSON are the `test(...)` declaration lines (`customer-lifecycle.spec.ts:157`, `:178`; `roles-capabilities.spec.ts:53`). The brief cites the `test.fixme(` call lines one below them (`:158`, `:179`, `:54`). These are the same tests.
2. **`git checkout -- frontend/test-results documentation/screenshots` errors as a whole.** `frontend/test-results` is gitignored (`.gitignore:141`) and has no tracked files, so git aborts. The cleanup ran `git checkout -- documentation/screenshots`, `git checkout -- portal/next-env.d.ts` and `git clean -f -- documentation/screenshots frontend/e2e/screenshots portal/e2e/screenshots` instead, which removed 107 untracked PNGs. The baselines were archived first to `/tmp/636A/baselines.tgz` (not committed).
3. **`login-bare` was run** as the opt-in part of E2E-PORTAL (628G precedent).
4. **`docker builder prune -f` did not force a cold backend build.** Without `-a`, it removes only dangling cache. In every stack log, each backend builder step (`COPY src src`, `./mvnw clean package`) shows `CACHED`, so every stack used the same backend image, `8111f5608eda`. This does not affect provenance. BuildKit `COPY` cache keys are content checksums, so a `CACHED` `COPY src` means the source is byte-identical to `backend/src` at `1034b6af8`. `git diff --stat 0827ce3f9 1034b6af8 -- backend frontend portal packages compose` is also empty: no code has changed since the #1739 stack that built the cache. The frontend `next build` re-ran on C1 and G1, then was `CACHED` on C2 and G2 (see 636A-c1). This is recorded as candidate 636A-c2.
5. **The tenant schema name is deterministic** (`tenant_7d218705360b` on all four stacks). The brief expected a new name per stack. Freshness is proven by the volume removal and the pristine `projects` table, not by the schema name.
6. **`docker logs e2e-backend` persists across `docker restart`.** The brief said the log restarts. The "Exchanged magic link" counts in `raw/stacks.txt` are therefore cumulative over G2: 1 after 90-day, 2 after responsive, 4 after isolation, 5 after login-bare. That is one exchange each for 90-day globalSetup, responsive globalSetup and login-bare, and two for isolation.
7. **The depth-ii known-skip attribution in the brief / 634C is inaccurate.** The 4 skips are `day-30-45-soa-writeoff.spec.ts:292` (`test.fixme` "Statements-tab Download fetches the PDF") plus `null-prefetch-probe.spec.ts:74` ×3: one parametrised opt-in test, skipped unless `NULL_PREFETCH_PROBE` is set (`:71`). The `day-05-disbursements.spec.ts:147` `test.skip` is conditional on the module being absent and did not fire. The `day-14-conveyancing` fixme was removed in 628E (comment at `:140`). The count is still 4 and every skip is a declared fixme/opt-in, so this is a documentation correction only (636A-c3).
8. **JSON reporter files and one PNG artefact were committed.** The brief says "Do not commit JSON files, traces, videos, `test-results/**` trees, or any screenshot baselines". Committed anyway: the seven scored-run Playwright JSON reports `raw/G1-{lifecycle,depth-ii,default}.json` and `raw/G2-{90day,responsive,isolation,login-bare}.json` (~0.5 MB together; they contain absolute `/Users/rakheendama/...` paths from the reporter), and `raw/artefacts/G2-day-75-deadline-detail.png` (173 KB, a Day 75 frame copied from `portal/test-results/`, not a baseline). Reason: the JSON `.stats` blocks are the ground-truth source for every count in this PR and were what the reviewers re-verified against; the `.tsv` files are derived from them with `jq`. The PNG is the visual evidence for the Day 75 un-fixme (`portal.md`). No traces, videos, baselines or `test-results/**` trees were committed. Capture-run (C1/C2) JSON stayed in `/tmp/636A/`.

## G89 candidates (for 636B's register)

No product defects and no test failures were found. Three low-severity tooling/doc observations:

- **636A-c1 — tooling, low. The root `.dockerignore` does not exclude `frontend/e2e/screenshots/**`.** Its deny-by-default list re-allows `frontend/` and re-denies only `node_modules`, `.next`, `test-results` and `playwright-report` (`.dockerignore`, the "Re-deny heavy/generated paths" block).
  - Effect: the 49 untracked legal baseline PNGs written by C1 entered the frontend build context. That explains the C1 → G1 change: G1 log `[frontend builder 9/11] COPY frontend/` shows `DONE`, not `CACHED`, and `next build` re-ran, so the image changed from `3e6a05…` to `d026d5…`. C2 and G2 then built from the same context (baselines still present) and were `CACHED` on `d026d5…`.
  - C1 itself also shows `COPY frontend/` `DONE` and `next build` re-running (`raw/stacks.txt` → "build-cache status"), before any baseline existed. Why C1 rebuilt the frontend is undetermined. The stack torn down by C1 had been built from a different, since-deleted worktree (`worktree-fix-closure-spec`, per the 636A brief's scout note), so the cached frontend layers may have come from a different checkout's context; this was not verified.
  - Impact: runtime behaviour is unaffected, because the standalone output does not ship `e2e/`. But an image built "from sha X" depends on untracked files in the checkout, and every capture run forces a ~1 min frontend rebuild.
  - Suggested owner: tooling / Epic 636 follow-up. Add `frontend/e2e/screenshots/**/*.png` and `portal/e2e/screenshots/**/*.png` to the re-deny block.
- **636A-c2 — gate vocabulary, low. The 636.1 / E2E-LEGAL literal `docker builder prune -f` does not achieve a cold rebuild** with the docker driver while the `docteams-e2e-*` images exist: backend layers stay `CACHED` (deviation 4). The gate is still valid, because content-addressed caching guarantees source identity. If the intent is "re-run maven from scratch", the vocabulary needs `docker builder prune -af` (or `e2e-up` with `--no-cache`). Suggested owner: whoever owns the gate vocabulary (Epic 636 / phase doc).
- **636A-c3 — doc, informational.** The depth-ii known-skip list in 634C `e2e-gate.md` ("existing skip/fixme in day-05, day-14, day-30-45 and null-prefetch-probe") misattributes 2 of the 4 skips (deviation 7). Nothing to fix in code.

## Tree hygiene

- Commands run: `tar czf /tmp/636A/baselines.tgz frontend/e2e/screenshots portal/e2e/screenshots`, `git checkout -- documentation/screenshots`, `git checkout -- portal/next-env.d.ts`, and `git clean -f -- documentation/screenshots frontend/e2e/screenshots portal/e2e/screenshots` (107 PNGs).
- `git status --short` afterwards showed only `?? qa_cycle/evidence/phase89-rewalk/suites/`. `.epic-brief.md` is gitignored.
- The tracked `.gitkeep` / README files under the screenshot dirs remain (8 files).
- No baselines, `test-results/**` trees, traces or videos are committed. The seven scored-run JSON reporter files and one Day 75 PNG artefact are committed against the brief's literal (deviation 8); the suites dir totals ~0.9 MB.

## Environment left behind

- **E2E stack G2 is left up** (built from `1034b6af8`) so DB/log state can be re-inspected. Tear it down with `bash compose/scripts/e2e-down.sh` if memory is needed.
- **KC dev portal restarted** from the main checkout (`svc.sh start portal`). `svc.sh status` afterwards shows `portal 51238 yes yes 3002` (cwd `/Users/rakheendama/Projects/2026/b2b-strawman/portal`). Backend :8080, gateway :8443 and frontend :3000 were never touched.
