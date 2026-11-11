# PR #1757 (G89 Batch L: F28 matter-required client trust postings, F36 portal trust reference 200): pre-merge e2e gate

**Verdict: PASS at PR head `b63284ef5`. Every required suite met its baseline on its first and only scored run, with an empty per-test diff against the exit-rescore TSVs. There were no failures and no re-runs.**

- **All four observations held on the PR backend (G1 stack):**
  - **(1) Flyway.** Global is **6** (`V6__portal_trust_reference_200`) and tenant is **14**, on G1 and G2. `portal.portal_trust_transaction.reference` is `character varying(200)`.
  - **(2) F28 refusals.** A deposit with no matter returned **400 `MATTER_REQUIRED`**, detail "A matter is required for client trust transactions". A deposit on another client's matter returned **400 `MATTER_NOT_LINKED_TO_CLIENT`**. Neither wrote a row.
  - **(3) F28 happy path.** A deposit on the client's matter returned **201** and reached the portal. The portal balance (read model, and `/portal/trust/summary` with a magic-link JWT) is **R 1 907,00**, equal to the firm client ledger (R 1 907,00).
  - **(4) F36.** A deposit with a **150-character reference** returned **201**. The `portal.portal_trust_transaction` row holds the full 150-character reference, an exact match. The backend log has **0** "value too long" and **0** "Portal trust sync failed".
- **The branch moved during the gate.** `fix/g89-l-trust-portal-rows` advanced from `b63284ef5` to **`9a5168491`**: 10 commits, 19:37–19:54 SAST, from the PR review.
  - **Runtime changes:**
    - `TrustTransactionService`: a transfer posts each leg on its own client's matter (D39), and a fee transfer's matter must still be linked to the client;
    - `TrustInvestmentService` and `TrustInvestmentRepository`: withdraw only once the placement is paid, refuse reversing a withdrawn investment's placement, and serialise withdraw against reverse on the same locks;
    - the frontend `RecordTransferDialog`, `PlaceInvestmentDialog` (scrolls at short viewports), `lib/schemas/trust.ts` and transactions `actions.ts`.
  - These touch flows that the legal suites drive: the Day 60 investment placement, and the transfer and fee-transfer paths. **This gate is for `b63284ef5` only.** The new head needs its own `./mvnw verify` and its own e2e gate. Whether this result carries over is the orchestrator's call; I do not recommend carrying it over without a re-run.

## Source and recipe

- **Worktree:**
  - created as a detached worktree, `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp9`, at `b63284ef562551fb02a8a540478aecf7c659be7a`;
  - `pwd` was confirmed, and the tree was clean (`dirty=0`) before every stack and after every run;
  - removed with `git worktree remove` after a final `status-lines=0`.

  The merge base is origin/main `6a0488185`.
- **What changed vs main (runtime):**
  - **Backend:**
    - `TrustTransactionService`: `requireClientMatter` on deposit, payment, refund, transfer and investment placement; a fee transfer needs a single fee-note matter; `recordInvestmentWithdrawal`;
    - `TrustInvestmentService` (`PlaceInvestmentRequest.matterId`);
    - `LegalDemoDataSeeder` (demo trust rows on a matter);
    - **global Flyway `V6__portal_trust_reference_200.sql`**.
  - **Frontend:**
    - the trust dialogs and `lib/schemas/trust.ts` make the matter required;
    - `PlaceInvestmentDialog`;
    - the investments `actions.ts`.
  - **e2e:** `helpers/legal-lifecycle.ts` (`recordTrustDeposit` requires a matter) and `day-60-interest.spec.ts` (the placement fills Matter ID).
  - **Scenario:** line 12.2 of `qa/testplan/demos/legal-za-90day-keycloak.md`.
- **Recipe:** the #1755 recipe (`../pr1755-e2e/README.md` and `raw/`), as reused by `../exit-rescore/suites/README.md`, with these differences:
  - `lib.sh` changed only WT, RAW and W;
  - `E2E_VISUAL` was unset (every run logs `E2E_VISUAL=unset`), and there was **no capture-then-score**. Every scored run was a single run;
  - the pack, SoA and digest probes and stack U were dropped. `obs.sh` now holds `flyway_probe` and `f28_probe`;
  - `diff.sh` diffs against `../exit-rescore/suites/raw/`. Lifecycle is compared with `L-lifecycle.tsv`, the exit-rescore passing run.
- **Stacks:**
  - Every stack was built fresh with `e2e-down.sh && SHELL=/bin/bash e2e-up.sh`, nohup-detached with a marker file (`G1.done`, `G2.done`). **No reseed was run anywhere.**
  - The foreign-stack guard reported "no foreign e2e stack" before G1 and G2.
  - `caffeinate -dims` was held by each driver for its whole run.
- **Tooling:**
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before Playwright flags;
  - every run logs `node=v20.11.1 envnode=v20.11.1`;
  - `pnpm install --frozen-lockfile` returned rc 0.
- **Portal suites:**
  - spaced at least 330 s apart, measured from the newest magic-link token: 330 / 330 / 330 s;
  - `docker restart e2e-backend` plus a health wait before each;
  - `:3002` was free each time;
  - cumulative magic-link exchanges were 1 / 2 / 4 / 5, the same as #1755 and the exit-rescore.
- **Docker VM disk:** 23.5G used and 31.9G free (42 %) at 19:33, so **no prune was needed and none was done**. It was 24.5G used and 30.9G free after the gate (`raw/docker-disk-check.txt`).
- **Concurrent verify and load:**
  - The full Maven verify in `.claude/worktrees/g89-l` ran throughout. Its JVMs were not touched.
  - The load average was 8–27 during the runs.
- **Timing:** every run was on 2026-11-11, 19:33–20:04 SAST.

## Verdict table

- Counts are passed / failed / skipped from Playwright JSON `.stats`. No run had any did-not-run (dnr) tests, and none had a flaky test.
- The per-test diff is `diff <(cut -f1,3,4,5 prior.tsv | sort) <(cut -f1,3,4,5 run.tsv | sort)` against `../exit-rescore/suites/raw/` (`raw/diff.sh`, output in `raw/per-test-diff.txt`).

| Suite | Stack | Run (SAST) | p / f / s (dnr) | Baseline | Per-test diff vs exit-rescore | Status |
|---|---|---|---|---|---|---|
| portal vitest | — | 19:35:41–19:35:57 | 51 files / **284 passed** | 284 | — | **PASS** |
| legal-lifecycle | G1 | 19:37:28–19:40:27 | **105 / 0 / 0** | 105/0/0 | **empty** (vs `L-lifecycle`) | **PASS** |
| legal-depth-ii | G1 | 19:40:29–19:42:22 | **40 / 0 / 4** | 40/0/4 | **empty** | **PASS** |
| default `test:e2e` | G1 | 19:42:24–19:43:33 | **165 / 0 / 14** | 165/0/14 | **empty** | **PASS** |
| portal 90-day | G2 | 19:44:56–19:45:19 | **13 / 0 / 0** | 13/0/0 | **empty** | **PASS** |
| portal responsive | G2 | 19:50:52–19:51:12 | **21 / 0 / 0** | 21/0/0 | **empty** | **PASS** |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | 19:56:45–19:56:47 | **2 / 0 / 0**, both ran | 2/0/0 | **empty** | **PASS** |
| login-bare | G2 | 20:02:32–20:02:49 | **1 / 0 / 0** | 1/0/0 | **empty** | **PASS** |

- **Snapshots, with compares off:**
  - every run had 0 "writing actual", 0 snapshot mentions and 0 baseline files written;
  - curated documentation screenshots: lifecycle 16, depth-ii 10, all others 0, the same as #1755;
  - all were new untracked files, with `tracked-modified=0`. Each was archived to the scratchpad and restored, leaving `after-restore git-status-lines=0`.
- **The known flakes did not fire:** G89-F85 (`day-07-14-work-trust:78`, time-summary lag) and G89-F73 (`invoices/invoice-lifecycle.spec.ts:88`).
- **The PR's e2e changes ran and passed:**
  - the lifecycle's `recordTrustDeposit` now requires a matter;
  - the Day 60 investment placement fills Matter ID.

  Under the PR backend, a client posting without a matter gets a 400, so these steps passing means they posted with a matter.

## Stack table

- **Common to G1 and G2:**
  - sha `b63284ef5`, rc 0, backend `UP`, frontend / mock-idp / mailpit 200, `E2E Boot-Seed Complete!`;
  - tenant `tenant_7d218705360b`;
  - compose `working_dir` `…/b2b-strawman-g89-cp9/compose`.
- **G1 build provenance** (`raw/stack-G1.log`, `raw/image-provenance.txt`):
  - **Backend:** the image was built from this worktree (`COPY src src`, `BUILD SUCCESS`). Its jar holds `db/migration/global/V6__portal_trust_reference_200.sql` and a `TrustTransactionService.class` stamped 11-11-2026 17:36 UTC.
  - **Frontend:** `next build` compiled from this worktree. 16 `.next` files contain the PR's new string "A matter is required for client trust transactions".
  - **G2:** rebuilt fresh. The build was cache-identical, giving the same image IDs. G1's postgres and localstack volumes were removed before it.

| Stack | Purpose | Build (SAST) | rc | Backend image | Frontend image | Flyway tenant / global |
|---|---|---|---|---|---|---|
| G1 | scored legal + default; Flyway + F28/F36 probes | 19:35:57 → 19:37:26 | 0 | `e4cc6d32c764` (PR) | `43b673255189` (PR) | **14 / 6** (`14\|g89 contains trust data`, `6\|portal trust reference 200`) |
| G2 | scored portal; Flyway probe | 19:44:01 → 19:44:53 | 0 | `e4cc6d32c764` (PR) | `43b673255189` (PR) | **14 / 6** |

- **Teardown:** `e2e-down.sh` returned rc 0 at about 20:03, leaving 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes (`raw/e2e-down.txt`).

## Observations

These used the firm API with mock-IDP bearer tokens (Alice, owner), the same pattern as #1755's `trust-probe.sh`. The portal API used a magic-link JWT, from `request-link` then `exchange` (the e2e profile returns the link). The DB was only read, with SELECTs. They were run on G1 after the default suite, 19:43:34–19:43:41 SAST (`raw/obs-f28-G1.txt`, bodies in `raw/f28-G1/`).

- Client: **Acme Corp** (`9160f1f0…`). Its matter is **Website Redesign** (`1b99d52a…`, `projects.customer_id` = Acme).
- The unlinked matter is **Apex Annual Returns mwcej8xh0819** (`cb647375…`). It belongs to client Apex Holdings and has 0 `customer_projects` links to Acme.
- The trust account is `Mathebula & Partners Trust Account` (`a08c57b6…`), the only one.

### (1) Flyway (`raw/obs-flyway-G1-start.txt`, `-G1-end.txt`, `-G2-start.txt`)

- **Global** `public.flyway_schema_history`:
  - V1 baseline;
  - V2 portal contact directory;
  - V3 portal backfill markers;
  - V4 portal trust transaction date;
  - V5 portal strip matter type suffix;
  - **V6 portal trust reference 200**.

  All six succeeded, so **global = 6**.
- **Tenant:** V1 … **V14 `g89 contains trust data`**, all succeeded, so **tenant = 14**.
- `information_schema` gives `portal_trust_transaction.reference character varying(200)`. Main has `varchar(100)` (V1 baseline l.245).

### (2) F28 refusals

```
(2a) POST /api/trust-accounts/{TA}/transactions/deposit  {clientId: Acme, no matterId, amount 500, ref GATE/NOMATTER/1757}
     -> 400 {"title":"Matter required","status":400,"detail":"A matter is required for client trust transactions","code":"MATTER_REQUIRED"}
(2b) same, matterId = Apex Annual Returns (client Apex Holdings)
     -> 400 {"title":"Matter not linked to client","status":400,"detail":"Matter Apex Annual Returns mwcej8xh0819 is not linked to client Acme Corp","code":"MATTER_NOT_LINKED_TO_CLIENT","matterId":"cb647375-…"}
```

The tenant had `trust_transactions` count 11 before both refusals and 11 after. Rows with `GATE/NOMATTER` or `GATE/UNLINKED`: 0.

### (3) F28 happy path: the firm ledger matches the portal

- **The request:** a deposit with `matterId` = Website Redesign, R 1 757,00, `GATE/DEP/1757`.
- **The response:** **201**, `DEPOSIT · RECORDED`, `matterId 1b99d52a…`.
- **Firm row (SELECT):** `DEPOSIT|1757.00|RECORDED|project_id=1b99d52a…`.
- **Portal row (SELECT `portal.portal_trust_transaction`):** `DEPOSIT|1757.00|running 1757.00|matter=1b99d52a…`.

Firm and portal agree, with probe (4)'s R 150 deposit included:

| Source | Acme trust balance |
|---|---|
| firm `GET /api/trust-accounts/{TA}/client-ledgers/{Acme}` (`balance`) | **1907.00** |
| `portal.portal_trust_balance` (sum over Acme's matters; one row, Website Redesign) | **1907.00** |
| portal API `GET /portal/trust/summary` as `contact@acme.example.com` | **1907.00** (`Website Redesign`) |
| firm rows for Acme with `project_id IS NULL` | 0 |

The portal API `GET /portal/trust/matters/{Website Redesign}/transactions` lists both gate deposits, with running balances 1757.00 → 1907.00.

### (4) F36: a 150-character reference

- **The request:** a deposit on Website Redesign, R 150,00. The reference is `GATE/F36/1757/RRRR…`, 150 characters.
- **The response:** **201**, `DEPOSIT · RECORDED`, `refLen 150`.
- **Firm row:** `reflen=150`.
- **Portal row:** `portal.portal_trust_transaction` id `bdd81eb1…` has `reflen=150` and `full-match=true` (`reference = '<the 150-char string>'`), running balance 1907.00.
- The portal API row also has `refLen 150`.
- **Backend log (G1, whole stack life):** `value too long` 0, `Portal trust sync failed` 0. G2 also has 0 and 0.

On main, the varchar(100) column would make this sync abort, per the V6 comment. Only the PR side was run here; the red test `PortalTrustLongReferenceIntegrationTest` covers the main side.

## Failure classification

**There were no failures.** No suite failed on its scored run, so no re-run and no main reproduction were needed.

| Class | Count |
|---|---|
| (a) PR regression | **0** |
| (b) intended change needing a spec update (a spec that records a trust row without a matter) | **0**. The PR's own spec changes (lifecycle helper, Day 60 Matter ID) ran green. |
| (c) pre-existing, reproduced on main | **0** |
| known flakes (F85 `day-07-14-work-trust:78`, F73) | did not fire |

## Backend log (`raw/backend-log-scan.txt`)

Counts are JSON-level WARN/ERROR by logger:
- **G1** (`raw/G1-backend.log`), including the probes: **0 ERROR**.
  - The WARN classes are as in #1755: PDFBox font fallback, draft invoice without tax number, `Forbidden … /time-summary/by-member` ×12, `No recipients found for DOCUMENT_GENERATED`, and one-off boot WARNs.
  - PR-area WARN/ERROR (`MATTER_*`, `value too long`, `Portal trust sync failed`, `TrustLedgerPortalSync`, `portal_trust`): **0**.
  - The two 400 refusals are not logged at WARN.
- **G2** (`raw/G2-backend.log`), including 3 restarts: **0 ERROR**, 0 PR-area. The single `security.auth_failed … /api/clients` is the isolation spec's expected "Portal JWT blocked from org API".

## Raw files (`raw/`)

- **Scored runs:**
  - `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}` and `G2-{90day,responsive,isolation,login-bare}.*`;
  - a `*.dirty.txt` per run, plus `G2-final.dirty.txt`;
  - `portal-vitest.txt`, `pnpm-install.txt`, `per-test-diff.txt`.
- **Observations:** `obs-flyway-{G1-start,G1-end,G2-start}.txt`, `obs-f28-G1.txt` and `f28-G1/` (response bodies, firm ledger JSON, portal summary and transactions JSON).
- **Stacks:**
  - `stacks.txt` (checks, accounting and spacing);
  - `timeline.txt`;
  - `stack-G1.log`, `stack-G2.log`;
  - `image-provenance.txt`;
  - `G1-backend.log`, `G2-backend.log`, `backend-log-scan.txt`;
  - `docker-disk-check.txt`, `e2e-down.txt`, `kc-svc-status-after.txt`.
- **Drivers:** `lib.sh`, `obs.sh`, `run-g1.sh`, `run-g2.sh`, `logscan.sh`, `diff.sh`.

## Environment left behind

- **The e2e stack is down:** 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.
- **The KC app services:**
  - **Before the gate:** stopped at 19:34 SAST from the main checkout with `svc.sh stop frontend portal gateway backend`.
  - **After the gate:** restarted with `svc.sh start all`. All four were healthy at 20:03:54 SAST (`raw/kc-svc-status-after.txt`):
    - backend 8080 (PID 47148);
    - gateway 8443 (47535);
    - frontend 3000 (47752);
    - portal 3002 (47857).
  - **Infra containers:** `b2b-keycloak`, `b2b-postgres`, `b2b-localstack` and `b2b-mailpit` were untouched and healthy throughout.
  - **Main checkout:** it stayed at `bd1a9518b`. Its only status lines are the founder's two untracked Phase 90 files, which existed before the gate. No file in it was modified.
- **The gate worktree is removed** (clean before removal). The per-run dirty archives (curated PNGs) and the full `test-results` copies are in `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1757/`.
- **Docker VM disk:** 30.9G free. Nothing was pruned.
- **The concurrent Maven verify** in `.claude/worktrees/g89-l` was not touched.
- Nothing was committed or merged.
