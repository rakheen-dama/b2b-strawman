# G89 exit re-score: e2e suites on origin/main `461fb99ec`

**Verdict: PASS. Every required suite met its baseline with an empty per-test diff against the #1755 gate. One suite, legal-lifecycle, got there on its one permitted re-run.**

- **Legal-lifecycle failed its first scored run, on G1:** 68 / 6 / 31 did-not-run.
  - The root failure was the known `day-07-14-work-trust:78` time-summary lag. The other 5 failures and the 31 did-not-run are a serial cascade from it.
  - The re-run on a fresh stack L passed **105 / 0 / 0**, with an empty diff.
  - The failure did not persist, so the step that reproduces on the previous gated main (`20857c03c`) was not triggered.
  - It is classed as a known flake, by history pre-existing (c), and **not a regression (0 × class a)**.
- **No other failure occurred.** G89-F73 (`invoices/invoice-lifecycle.spec.ts:88`) did not fire.
- **Read-only DB checks held on every stack:** Flyway tenant **14** / global **5**, `pack_install` legal-za **15**, and trust-reports **3**.

## Source and recipe

- **Source:**
  - origin/main `461fb99ec87eb0f5cd450d50f31e941b808a7f24`, which includes every G89 fix batch: A–H, E, F (#1753), I (#1754) and J (#1755).
  - The runtime diff vs the previous gated main `20857c03c` is the #1755 merge `9738e0b56`, backend only. It includes the post-gate `34ab1c90e` digest-currency commit.
  - Commits `90fc069de` and `bd1a9518b` (Phase 90 docs and ADRs, plus the merge) carry no runtime change.
  - PR #1756 (docs) is not included and does not affect the suites.
- **Worktree:**
  - detached `/Users/rakheendama/Projects/2026/b2b-strawman-g89-exit` at `461fb99ec`;
  - `pwd` was confirmed and `dirty=0` before every stack;
  - `after-restore git-status-lines=0` after every run;
  - `pre-remove porcelain=0`, then removed with `git worktree remove`.
- **Recipe:**
  - the #1755 gate (`../../pr1755-e2e/README.md` and `raw/`), with `lib.sh` changing only WT, RAW and W;
  - `E2E_VISUAL` unset (logged `E2E_VISUAL=unset`), and **no capture-then-score**: every scored run is a single run;
  - the #1755 PR-specific probes (SoA, digest fire, stack U) were dropped. Only the read-only `pack_probe` was kept (`obs.sh`);
  - the lifecycle re-run used `rerun-lifecycle.sh`, the #1754 pattern.
- **Stacks:**
  - every stack was built with `e2e-down.sh && SHELL=/bin/bash e2e-up.sh`, nohup-detached (`run-all.sh`, `after-g2.sh`) with done-marker files;
  - **no reseed anywhere**;
  - the foreign-stack guard reported "no foreign e2e stack" before G1, G2 and L.
- **Tooling:**
  - `caffeinate -dims` was held by each driver;
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before Playwright flags;
  - every run logs `node=v20.11.1 envnode=v20.11.1`;
  - `pnpm install --frozen-lockfile` returned rc 0.
- **Portal spacing:**
  - 333 / 330 / 332 s since the newest magic-link token;
  - `docker restart e2e-backend` plus a health wait before each;
  - `:3002` was free each time;
  - cumulative magic-link exchanges were 1 / 2 / 4 / 5, the same as #1753–#1755.
- **Docker VM disk:** 32.4G free (41 % used) at the start, so **nothing was pruned** (`raw/docker-disk-check.txt`). It was 31.9G free at the end.
- **KC app services:** stopped from the main checkout (`svc.sh stop frontend portal gateway backend`) at 17:13 and restarted at 17:44 (see the end of this README). The `b2b-*` infra containers were untouched, and no main-checkout file was modified.
- **Machine:** no Maven verify ran. Load was 29 at the start and 7–13 during G2 and L.
- **Time (G89-F66):** every run was on 2026-11-11 between 17:13 and 17:44 SAST, outside 00:00–02:00. The SA date and the UTC date were equal, so F66 was not exercised.

## Verdict table

- Counts are passed / failed / skipped (did-not-run), from the Playwright JSON `.stats` and the list reporter.
- The per-test diff is `diff <(cut -f1,3,4,5 prior.tsv | sort) <(cut -f1,3,4,5 run.tsv | sort)` against the same-named `../../pr1755-e2e/raw/` TSV. L-lifecycle is compared with `G1-lifecycle.tsv`. The script is `raw/diff.sh`.

| Suite | Stack | Run (SAST) | p / f / s (dnr) | Baseline | Per-test diff vs #1755 | Status |
|---|---|---|---|---|---|---|
| portal vitest | — | 17:13:54–17:14:04 | 51 files / **284 passed** | 284 | — | **PASS** |
| legal-lifecycle | G1 | 17:15:20–17:18:28 | 68 / **6** / 0 (**31 dnr**) | 105/0/0 | 1 root failure (`day-07-14-work-trust:78`), 5 cascade failures and 31 cascade did-not-run | **FAIL → re-run** (known flake, see below) |
| legal-lifecycle, re-run | L (fresh) | 17:40:56–17:43:50 | **105 / 0 / 0** | 105/0/0 | **empty** | **PASS** |
| legal-depth-ii | G1 | 17:18:29–17:20:19 | **40 / 0 / 4** | 40/0/4 | **empty** | **PASS** |
| default `test:e2e` | G1 | 17:20:20–17:21:19 | **165 / 0 / 14** | 165/0/14 | **empty** | **PASS** |
| portal 90-day | G2 | 17:22:16–17:22:42 | **13 / 0 / 0** | 13/0/0 | **empty** | **PASS** |
| portal responsive | G2 | 17:28:13–17:28:30 | **21 / 0 / 0** | 21/0/0 | **empty** | **PASS** |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | 17:34:04–17:34:06 | **2 / 0 / 0**, both ran | 2/0/0 | **empty** | **PASS** |
| login-bare | G2 | 17:39:52–17:40:09 | **1 / 0 / 0** | 1/0/0 | **empty** | **PASS** |

**Snapshots, with compares off:**
- every run had 0 "writing actual", 0 snapshot mentions and 0 baseline files written, and `tracked-modified=0`;
- curated documentation screenshots were G1-lifecycle 9 (cut short), L-lifecycle 16, depth-ii 10 and all others 0. They were archived to the scratchpad and restored.

## Stack table

| Stack | Purpose | Build (SAST) | rc | Backend image | Frontend image | Flyway tenant / global | legal-za `pack_version` | trust-reports |
|---|---|---|---|---|---|---|---|---|
| G1 | lifecycle, depth-ii, default | 17:14:04 → 17:15:18 | 0 | `85d0d602da75` (built here: `COPY src src`, `BUILD SUCCESS`) | `87797a505c15` | **14** (`g89 contains trust data`) / **5** (`portal strip matter type suffix`) | **15** (start and end) | **3** |
| G2 | portal suites | 17:21:20 → 17:22:13 | 0 | `85d0d602da75` | `87797a505c15` | 14 / 5 | 15 | 3 |
| L | lifecycle re-run | 17:40:14 → 17:40:54 | 0 | `85d0d602da75` | `87797a505c15` | 14 / 5 | 15 | 3 |

- **Common to all stacks:**
  - git HEAD `461fb99ec`;
  - backend `UP`, with frontend, mock-idp and mailpit all returning 200;
  - `E2E Boot-Seed Complete!`;
  - tenant `tenant_7d218705360b`;
  - compose `working_dir` `…/b2b-strawman-g89-exit/compose`;
  - G2 and L removed the previous stack's volumes;
  - org_settings `{"packId": "legal-za", "version": 15}`;
  - the SoA template has `hasTypeLabelKey=true` and `hasRawTypeKey=false` (`raw/obs-pack-*.txt`).
- **Teardown:** `e2e-down.sh` at 17:44:10 returned rc 0, leaving 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.

## Failure classification

| # | Failure | Class | Proof |
|---|---|---|---|
| F1 (root) | `legal-lifecycle/day-07-14-work-trust.spec.ts:78` "Bob: Log 120min on Sipho — letter of demand". `expect(getByRole('tabpanel')).toContainText("Bob Admin")` received a Fees → Time summary showing only Carol (`Contributors 1 · Entries 1 · 1h 30m`) | **Known flake (the `:78` time-summary lag), pre-existing by history (c). Not a regression.** | **The write landed and the read was stale:** a read-only SELECT on stack G1 shows Bob's 120-min entry committed at 15:16:20.6Z, but the panel rendered without it. **Re-run once on fresh stack L: 105/0/0, empty diff.** **History:** it failed at the #1754 gate (G1 @`80931ef69`, before any #1755 code existed) with the identical 68/6/31 cascade, and that re-run passed. That is 2 failures in about 45 lifecycle runs across `g89-fixes` evidence. Because the failure did not persist, the brief's main-reproduction step (`20857c03c`) was not triggered. `raw/f78-proof.txt` |
| F2–F6 | `day-30-billing:175`, `day-45-recon-prescription:132`, `day-60-interest:97`, `day-90-review:95`, `day-90-review:326`, plus 31 did-not-run | **Cascade of F1** | Each needs state that a test skipped after F1 would have created: the Moroka unbilled time, the Motion court date, the Moroka FEE_TRANSFER (avg-daily R 8 064,52 vs 7 790,32), the `morokaFeeNote` run-state, and Sipho's fee note in SENT ("current status: DRAFT"). All pass on L. |

| Class | Count |
|---|---|
| (a) regression | **0** |
| (b) intended change, spec update needed | **0** |
| (c) pre-existing | 1 root failure (F1, the known flake, which passed on its re-run) plus its 5 cascades |
| G89-F73 (`invoice-lifecycle:88`) | did not fire. default `test:e2e` 165/0/14 |

## Backend logs (`raw/backend-log-scan.txt`)

- **G1, G2 (including 3 restarts) and L:** **0 ERROR**, with 0 WARN/ERROR in the #1755 areas (digest, statement, reconcile, `TrustTransactionTypeLabel`, template pack, legal-za).
- **The WARN classes match #1755:**
  - PDFBox font fallback;
  - draft invoice without tax number;
  - `Forbidden … /time-summary/by-member` ×12 on G1, which is Carol's by-member breakdown permission and the same as #1755;
  - `No recipients found for DOCUMENT_GENERATED`;
  - boot WARNs.
- G2's single `security.auth_failed … /api/clients` is the isolation spec's expected "Portal JWT blocked from org API".

## Raw files (`raw/`)

- **Scored runs:** `G1-{lifecycle,depth-ii,default}`, `L-lifecycle` and `G2-{90day,responsive,isolation,login-bare}`, each as `.json`, `.tsv`, `.txt` and `.dirty.txt`. Also `portal-vitest.txt` and `pnpm-install.txt`.
- **Stacks:** `stacks.txt`, `timeline.txt`, `stack-{G1,G2,L}.log`, `{G1,G2,L}-backend.log`, `backend-log-scan.txt`, `obs-pack-{G1-start,G1-end,G2-start,L-start}.txt`, `docker-disk-check.txt`, `e2e-down.txt` and `kc-svc-status-after.txt`.
- **Failure:** `f78-proof.txt`.
- **Drivers:** `lib.sh`, `obs.sh`, `run-g1.sh`, `run-g2.sh`, `run-all.sh`, `rerun-lifecycle.sh`, `after-g2.sh`, `diff.sh` and `logscan.sh`.
- The dirty archives (curated PNGs) and full `test-results` copies, including the G1-lifecycle `error-context.md` and screenshots, are in `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/exit/`.

## Environment left behind

- **The KC app services were restarted and all are healthy** (17:44:50 SAST, `raw/kc-svc-status-after.txt`):
  - backend 8080 (PID 75318);
  - gateway 8443 (75558);
  - frontend 3000 (75768);
  - portal 3002 (75911).

  The `b2b-keycloak`, `b2b-postgres`, `b2b-localstack` and `b2b-mailpit` containers stayed healthy throughout. The main checkout is at `bd1a9518b`, still with only the founder's 2 untracked Phase 90 files, and was not modified.
- **The e2e stack is down.** The gate worktree is removed.
- Nothing was committed, merged or pruned.
