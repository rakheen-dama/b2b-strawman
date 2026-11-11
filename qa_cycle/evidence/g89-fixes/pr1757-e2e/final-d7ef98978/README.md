# PR #1757 (G89 Batch L) FINAL pre-merge e2e gate at `d7ef98978`

**Verdict: PASS at PR head `d7ef98978b15fc4a25261aeccdfbd77c712a2500`. One rule-1 re-run was needed, and every observation held.**

- **Suites.** Every required suite met its baseline with an **empty per-test diff** against the early-read gate (`../raw/*.tsv`, at `b63284ef5`).
  - **Depth-ii** and all four **portal suites** passed on their first scored run.
  - **Legal-lifecycle** and **default** each failed 2 tests on their first scored run (G1).
    - The machine was at load average 60–68, from the concurrent full Maven verify.
    - Each failing test's server-side write **was persisted** (SQL evidence below). The UI did not reflect it in time, or another test overwrote it.
    - The rule-1 re-run on a **fresh stack** (R1) passed both suites at baseline, **105/0/0** and **165/0/14**, with empty diffs, on **identical image IDs**.
    - Rule 2 (reproduce on main) was not triggered, because nothing persisted.
  - None of these failures is in code this PR touches. Each is classed as a load-induced flake, not a regression. See the classification section.
- **Observations (1)–(6), all on the PR backend (G1):**
  - **(1)** Flyway is **tenant 15** (`V15__g89_trust_investment_cancelled`) and **global 6**. `chk_trust_invest_status` includes `CANCELLED`.
  - **(2)** `MATTER_REQUIRED` and `MATTER_NOT_LINKED_TO_CLIENT` returned 400s and wrote no rows. On the happy path, firm ledger = portal read model = `/portal/trust/summary` = **R 1 907,00**.
  - **(3)** The 150-character reference arrived in the portal in full.
  - **(4)** D39: TRANSFER_OUT posted on A's matter and TRANSFER_IN on B's matter. B's portal went 0 → 250. An unlinked target matter returned 400.
  - **(5)** Investments:
    - withdrawing while the placement is AWAITING_APPROVAL returned 400;
    - after rejecting the placement, the investment is CANCELLED, is absent from the register, and the trust card is unchanged;
    - reversing an approved placement returned 400 ("Withdraw the investment instead…");
    - the withdrawal returned 200 and credited the matter once.
  - **(6)** Backend log: **0 ERROR** and **0 deadlock or lock-timeout lines** on G1, R1 and G2.
- **The branch moved during the gate.** `fix/g89-l-trust-portal-rows` is now at **`864af7343`**, after `34178496c` (review round 4) and `864af7343`.
  - `git diff --stat d7ef98978..864af7343` touches 4 backend **test** classes and `qa/testplan/demos/legal-za-90day-keycloak.md` (1 line, "G89 D38 + D42").
  - It has **0 lines** under `backend/src/main`, `frontend/{app,components,lib}`, `portal/`, `packages/` or `compose/`, and changes no e2e spec.
  - So this gate's runtime result applies to `864af7343` unchanged.
  - The scenario-doc line is a scenario amendment. Per CLAUDE.md §6 it needs explicit authorisation, which is the orchestrator's call and was not reviewed here.

## Source and recipe

- **Worktree:**
  - created as a detached worktree, `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp10`, at `d7ef98978`;
  - `pwd` was confirmed, and `dirty=0` before every stack and after every run;
  - removed with `git worktree remove` after `status-lines=0`.

  The merge base is origin/main `6a0488185`.
- **Recipe:** the early-read gate (`../README.md`, `../raw/`), with these differences:
  - `lib.sh` changed only `WT`, `RAW` and `W`;
  - `diff.sh` compares against the **early-read TSVs**, label for label;
  - `obs.sh` is the early-read file, plus a `chk_trust_invest_status` line in `flyway_probe`, plus two new probes, `d39_probe` and `inv_probe`;
  - `run-g1.sh` calls the two new probes after `f28_probe`;
  - `run-r1.sh` is the rule-1 re-run driver.
- **Run conditions:**
  - `E2E_VISUAL` was unset, and every scored run was a single run;
  - every run logs `node=v20.11.1 envnode=v20.11.1`;
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--`;
  - `pnpm install --frozen-lockfile` returned rc 0;
  - `caffeinate -dims` was held by each driver.
- **Stacks:**
  - Every stack was built fresh with `e2e-down.sh && SHELL=/bin/bash e2e-up.sh`, nohup-detached with marker files (`G1.done`, `R1.done`, `G2.done`). **No reseed was run anywhere.**
  - The foreign-stack guard reported "no foreign e2e stack" before each.
- **Portal spacing:**
  - **332 / 334 / 331 s** from the newest magic-link token;
  - `docker restart e2e-backend` plus a health wait before each;
  - `:3002` was free each time;
  - magic-link exchanges were 1 / 2 / 4 / 5, the same as the early-read gate.
- **Docker VM disk:** 30.9G free at 20:47, so **no prune was needed and none was done**. It was 30.0G free after the gate.
- **Concurrent verify:**
  - The full Maven verify in `.claude/worktrees/g89-l` ran throughout. Its JVMs were not touched.
  - The load average was **40–68 during G1**, 13–52 during R1 and 19–36 during G2. The early-read gate saw 8–27.
  - That verify's own result was written by another session to `.claude/markers/verify-backend.json` in the main checkout at 21:24:54 SAST: 6823 testcases, 0 failures, BUILD SUCCESS. This gate did not write that file.
- **Timing:** every run was on 2026-11-11, 20:47–21:26 SAST.

## Verdict table

- Counts are passed / failed / skipped from Playwright JSON `.stats`. No run had any did-not-run (dnr) tests, and none had a flaky test.
- The per-test diff is `diff <(cut -f1,3,4,5 early.tsv | sort) <(cut -f1,3,4,5 run.tsv | sort)` (`raw/diff.sh`, output in `raw/per-test-diff.txt` and `raw/per-test-diff-R1.txt`).

| Suite | Stack | Run (SAST) | p / f / s | Baseline | Per-test diff vs early-read | Status |
|---|---|---|---|---|---|---|
| portal vitest | — | 20:48:28–20:48:41 | 51 files / **284 passed** | 284 | — | **PASS** |
| legal-lifecycle (scored) | G1 | 20:50:52–20:54:27 | 91 / **2** / 12 | 105/0/0 | 40 lines: `day-75-complex:224` failed, so 2 serial skips; `day-90-review:83` failed (cascade), so 10 serial skips | failed, re-run |
| legal-lifecycle (**rule-1 re-run**) | **R1** (fresh) | 21:00:22–21:03:38 | **105 / 0 / 0** | 105/0/0 | **empty** | **PASS** |
| legal-depth-ii | G1 | 20:54:29–20:56:31 | **40 / 0 / 4** | 40/0/4 | **empty** | **PASS** |
| default `test:e2e` (scored) | G1 | 20:56:33–20:57:47 | 157 / **2** / 20 | 165/0/14 | 22 lines: `invoice-lifecycle:112` failed, so 6 serial skips; `general-settings:65` failed | failed, re-run |
| default `test:e2e` (**rule-1 re-run**) | **R1** (fresh) | 21:03:40–21:04:56 | **165 / 0 / 14** | 165/0/14 | **empty** | **PASS** |
| portal 90-day | G2 | 21:06:00–21:06:29 | **13 / 0 / 0** | 13/0/0 | **empty** | **PASS** |
| portal responsive | G2 | 21:11:59–21:12:15 | **21 / 0 / 0** | 21/0/0 | **empty** | **PASS** |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | 21:17:51–21:17:53 | **2 / 0 / 0**, both ran | 2/0/0 | **empty** | **PASS** |
| login-bare | G2 | 21:23:38–21:23:57 | **1 / 0 / 0** | 1/0/0 | **empty** | **PASS** |

- **Snapshots, with compares off:**
  - every run had 0 "writing actual", 0 snapshot mentions and 0 baseline files written;
  - curated documentation screenshots: G1 lifecycle 15 (the failed run skipped one step), R1 lifecycle 16, depth-ii 10, all others 0;
  - all were untracked, with `tracked-modified=0`. Each was archived to the scratchpad and restored, leaving `after-restore git-status-lines=0`.
- **The PR's own e2e changes passed in R1:** `recordTrustDeposit` requires a matter, and the Day 60 placement fills Matter ID.

## Stack table

| Stack | Purpose | Build (SAST) | rc | Backend image | Frontend image | Flyway tenant / global |
|---|---|---|---|---|---|---|
| G1 | scored lifecycle, depth-ii and default; probes (1)–(5) | 20:48:41 → 20:50:49 | 0 | `2525abe3c745` | `212efbe57e13` | **15 / 6** (`15\|g89 trust investment cancelled`, `6\|portal trust reference 200`) |
| R1 | rule-1 re-run of lifecycle and default | 20:59:37 → 21:00:20 | 0 | `2525abe3c745` (same) | `212efbe57e13` (same) | **15 / 6** |
| G2 | scored portal suites | 21:05:16 → 21:05:58 | 0 | `2525abe3c745` (same) | `212efbe57e13` (same) | **15 / 6** |

- **Common to all three stacks:**
  - git HEAD `d7ef98978`, backend `UP`, `E2E Boot-Seed Complete!`;
  - tenant `tenant_7d218705360b`;
  - compose `working_dir` `…/b2b-strawman-g89-cp10/compose`.
- **Provenance** (`raw/image-provenance.txt`):
  - **Backend:** built from this worktree (`COPY src src`, `BUILD SUCCESS`). Its jar holds `db/migration/tenant/V15__g89_trust_investment_cancelled.sql` and a `TrustInvestmentService.class` stamped 18:49 UTC.
  - **Frontend:** `.next` holds the D39 string "Source client's matter UUID", which is new since `b63284ef5`, and `targetMatterId`, in 2 files each.
- **Teardown:** `e2e-down.sh` returned rc 0, leaving 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes (`raw/e2e-down.txt`).

## Observations

These used the firm API with mock-IDP bearer tokens: Alice (owner), and Bob (admin) for the reject and approve steps. The portal API used magic-link JWTs. The DB was only read, with SELECTs. They ran on G1 after the default suite, 20:57:47–20:58:05 SAST.

- **Common IDs:**
  - trust account `Mathebula & Partners Trust Account` (`656633a6…`), with `require_dual_approval=false` and no threshold;
  - client **A**: Acme Corp (`d0d7649a…`), matter **Website Redesign** (`1537ad8a…`);
  - client **B**: Apex Holdings mwch5m1q0874 (`fb4da198…`), matter **Apex Shareholder Agreement** (`c582c32a…`, `projects.customer_id` = B), portal contact `apex.holdings.mwch5m1q0874@example.com`.

### (1) Flyway (`raw/obs-flyway-G1-start.txt`, `-G1-end.txt`, `-R1-start.txt`, `-G2-start.txt`)

- **Tenant:** V1 … V14 `g89 contains trust data`, then **V15 `g89 trust investment cancelled`**, all succeeded, so **tenant = 15**.
- **Global:** V1 … **V6 `portal trust reference 200`**, so **global = 6**.
- **Constraint:** `chk_trust_invest_status CHECK (status = ANY (ARRAY['ACTIVE','MATURED','WITHDRAWN','CANCELLED']))`.
- `portal_trust_transaction.reference` is `character varying(200)`.
- These are identical on G1, R1 and G2.

### (2) F28 refusals and happy path (`raw/obs-f28-G1.txt`, `raw/f28-G1/`)

```
(2a) deposit, no matterId            -> 400 {"title":"Matter required","detail":"A matter is required for client trust transactions","code":"MATTER_REQUIRED"}
(2b) deposit, matter of Apex Holdings -> 400 {"title":"Matter not linked to client","code":"MATTER_NOT_LINKED_TO_CLIENT","matterId":"1872e736-…"}
trust_transactions count 11 -> 11; GATE/NOMATTER|GATE/UNLINKED rows = 0
(3)  deposit R1 757,00 on Website Redesign -> 201 DEPOSIT RECORDED; firm row project_id=1537ad8a…; portal row running 1757.00
```

| Source | Acme trust balance (after (3) and (4)) |
|---|---|
| firm `GET …/client-ledgers/{Acme}` | **1907.00** |
| `portal.portal_trust_balance` (sum) | **1907.00** |
| portal API `GET /portal/trust/summary` (`contact@acme.example.com`) | **1907.00** (`Website Redesign`) |
| firm rows for Acme with `project_id IS NULL` | 0 |

### (3) F36: a 150-character reference

- **The request:** a deposit of R 150,00 with the reference `GATE/F36/1757/RRRR…`, 150 characters.
- **The response:** **201**, `refLen 150`.
- **Firm row:** `reflen=150`.
- **Portal row:** `portal.portal_trust_transaction` has `reflen=150` and **`full-match=true`**, running balance 1907.00. The portal API row has `refLen 150`.
- **Backend log:** `value too long` 0, `Portal trust sync failed` 0.

### (4) D39: each transfer leg posts on its own client's matter (`raw/obs-d39-G1.txt`, `raw/d39-G1/`)

```
BEFORE: firm ledger A=1907.00 B=0 | portal read model B=0 | portal API B=0
(4a) transfer A/Website Redesign -> B, targetMatterId = Website Redesign (not linked to B)
     -> 400 {"title":"Matter not linked to client","detail":"Matter Website Redesign is not linked to client Apex Holdings mwch5m1q0874","code":"MATTER_NOT_LINKED_TO_CLIENT"}
     trust_transactions 13 -> 13; GATE/D39/UNLINKED rows = 0
(4b) transfer R250 A/Website Redesign -> B/Apex Shareholder Agreement -> 201 [TRANSFER_OUT matter 1537ad8a…, TRANSFER_IN matter c582c32a…]
SELECT (trust_transactions join customers/projects):
  TRANSFER_OUT|250.00|RECORDED|customer_id=d0d7649a… (Acme Corp)|project_id=1537ad8a… (Website Redesign)|project.customer_id=d0d7649a…
  TRANSFER_IN |250.00|RECORDED|customer_id=fb4da198… (Apex Holdings)|project_id=c582c32a… (Apex Shareholder Agreement)|project.customer_id=fb4da198…
  legs on their own client's matter: 2/2
portal.portal_trust_transaction: TRANSFER_IN 250.00 customer=B matter=c582c32a… running 250.00; TRANSFER_OUT 250.00 customer=A matter=1537ad8a… running 1657.00
AFTER: firm ledger A=1657.00 B=250.00 | portal read model B=250.00 | portal API /portal/trust/summary B=250.00 (Apex Shareholder Agreement)
deltas: A=-250.00  B-firm=+250.00  B-portal-API=+250.00
```

### (5) Investments: F86, F87 (V15) and withdraw-after-approval (`raw/obs-inv-G1.txt`, `raw/inv-G1/`)

**Before:** trust card `{cashbook 292156.98, clientLedgerTotal 292182.50, difference -25.52, pendingApprovalCount 3}`; firm ledger A **1657.00**; matter balance **1657.00**.

- **(5a) Place INV1** (Gate Bank `GATE-F87-REJ`, R 100, on Website Redesign).
  - **201**; the investment is ACTIVE.
  - SELECT: placement `0af6c530…` is **PAYMENT AWAITING_APPROVAL**, project = Website Redesign. The pending count rose to 4.
- **(5b) Withdraw INV1:** **400** "Cannot withdraw investment … The investment's placement payment must be approved before the investment can be withdrawn (placement payment status: AWAITING_APPROVAL)". The DB is unchanged.
- **(5c) Reject the placement** (Bob): **200**, PAYMENT **REJECTED**.
  - SELECT `trust_investments`: **CANCELLED**. `GET /api/trust-accounts/{TA}/investments` also shows INV1 as CANCELLED.
  - **Register report API** (`POST /api/report-definitions/investment-register/execute`, `trust_account_id` only): 2 rows, both the lifecycle's ACTIVE ones. **0 rows for `GATE-F87-REJ`**. With `status=CANCELLED`, the one row for `GATE-F87-REJ` is returned.
  - **Trust card after the reject:** identical to before (`{292156.98, 292182.50, -25.52, 3}`), so **unchanged: YES**. The ledger is unchanged (1657.00) and the matter balance is unchanged (1657.00).
- **(5d) Place INV2** (`GATE-F87-APR`, R 120): **201**.
  - Bob approved it: **200**, APPROVED, with no second approver. Ledger A went 1657.00 → **1537.00**.
  - **Reversing the approved placement** gave **400** "Cannot reverse transaction … This payment placed a trust investment (ACTIVE). **Withdraw the investment instead of reversing its placement payment.**"
  - Reversal rows for the placement: 0. Its status stayed APPROVED.
- **(5e) Withdraw INV2:** **200**, **WITHDRAWN**, `withdrawalAmount 120.00`, `withdrawalTransactionId 9fa297e7…`.
  - SELECT: **exactly 1** `INV-WD-Gate Bank-GATE-F87-APR` row, `DEPOSIT 120.00 RECORDED`, project = Website Redesign.
  - Ledger A went 1537.00 → **1657.00**, back to the starting value, so the matter was **credited once**. The trust card is again `{…, 3}`, the same as before.

**Context only.** The trust card's -25.52 difference was there before any probe and the probes left it unchanged. It equals exactly the lifecycle's Day 60 `INTEREST_LPFF` row (R 25,52, no client), which is a cashbook-only posting from the interest run. It is not in this PR's change set (`raw/obs-trustcard-diff-G1.txt`).

### (6) Backend log (`raw/backend-log-scan-{G1,R1,G2}.txt`)

| Log | ERROR | deadlock / lock-timeout / `PessimisticLock` / `CannotAcquireLock` | PR-area WARN/ERROR |
|---|---|---|---|
| G1, including probes (`raw/G1-backend.log`) | **0** | **0** | 0. Also 0 for `TrustInvestment`, `TRANSFER_`, `CANCELLED` and placement. |
| R1 (`raw/R1-backend.log`) | **0** | **0** | 0 |
| G2, including 3 restarts (`raw/G2-backend.log`) | **0** | **0** | 0 |

The WARN classes are the same as in the early-read gate: PDFBox font fallback, draft invoice without tax number, `Forbidden … /time-summary/by-member`, `No recipients found for DOCUMENT_GENERATED`, and one-off boot WARNs.

## Failure classification

All four first-run failures were on G1, under load average 60–68. Evidence is in `raw/G1-failure-evidence.txt`, SQL run on the live G1 stack at 20:58–20:59 SAST.

| # | Test (G1 fail time, SAST) | What the UI asserted | DB proof the write landed | R1 fresh-stack re-run | Class |
|---|---|---|---|---|---|
| 1 | `legal-lifecycle/day-75-complex.spec.ts:224` "Log 360min on Moroka estate matter" (≈20:53:45–20:54) | Fees → Time panel to contain "Prepare Liquidation & Distribution account 6h" within 15 s. It showed only the earlier 1h entry. | `time_entries`: **360 min**, task "Prepare Liquidation & Distribution account", Estate Late Peter Moroka, created **20:53:45.330** | **passed** | **Flake, F85 mechanism** (time summary lags under load). **Not regression (a).** It is a **new site** for F85: the documented F85 site is `day-07-14-work-trust:78`. Both F85 proof conditions hold (data in the DB, fresh-stack re-run passes). The orchestrator may want F85's scope widened. |
| 2 | `legal-lifecycle/day-90-review.spec.ts:83` "Matters list shows the lifecycle's six matters" | link "Apex Annual Returns …" | n/a | **passed** | **Cascade of #1.** The serial `day-75` describe skipped `:270` "Create 2nd Commercial matter for Apex", so that matter never existed. |
| 3 | `default/invoices/invoice-lifecycle.spec.ts:112` "APPROVED -> SENT" (≈20:57:09–:24) | "Record Payment" button within 15 s after "Send fee note" | `audit_events`: `invoice.approved` 20:57:06.641, **`invoice.sent` 20:57:08.999**; `invoices`: **INV-0006 SENT** | **passed** | **Flake**: the server action committed but the page did not re-render. This is the Next 16.2.6 lost-React-ping class on a loaded server, the same spec family as the known flake F73 (`:88`). **Not (a).** It cascaded to 6 serial skips. |
| 4 | `default/settings/general-settings.spec.ts:65` "Update brand colour" (≈20:57:45) | after reload, Brand Color = `#1a8f6e`; received `#1B3A4B` | `audit_events`: **20:57:34 `org_settings.updated brand_color #1a8f6e`** (this test's save landed), then 20:57:45, :46 and :47 `org_settings.updated brand_color #1B3A4B` | **passed** | **Flake: test-isolation race on shared org settings.** Other settings tests in parallel workers saved the whole form with the old colour after this test's save, and load widened the window. **Not (a).** |

- **Why none is a regression (a):**
  - the PR's runtime diff (`6a0488185..d7ef98978`) touches only trust accounting, plus the e2e helper and Day 60 spec that already passed. It does not touch time entries, the time summary, invoices or org settings;
  - every failing write was persisted;
  - every failure passed on R1, built from the same image IDs.
- **No (b)**, intended change needing a spec update: 0.
- **No (c)**, pre-existing reproduced on main: rule 2 runs only if a failure persists after the re-run, and none did, so main `6a0488185` was not run. These are classed as flakes on evidence, not "pre-existing on main".

## Raw files (`raw/`)

- **Scored runs:**
  - `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}`, `R1-{lifecycle,default}.*` and `G2-{90day,responsive,isolation,login-bare}.*`;
  - a `*.dirty.txt` per run, plus `G2-final.dirty.txt`;
  - `portal-vitest.txt`, `pnpm-install.txt`, `per-test-diff.txt`, `per-test-diff-R1.txt`.
- **Observations:**
  - `obs-flyway-{G1-start,G1-end,R1-start,G2-start}.txt`;
  - `obs-f28-G1.txt` with `f28-G1/`, `obs-d39-G1.txt` with `d39-G1/`, and `obs-inv-G1.txt` with `inv-G1/` (positions, register JSON, response bodies);
  - `obs-trustcard-diff-G1.txt`.
- **Failures:** `G1-failure-evidence.txt`.
- **Stacks and environment:**
  - `stacks.txt`, `timeline.txt`, `image-provenance.txt`;
  - `stack-{G1,R1,G2}.log`, `{G1,R1,G2}-backend.log`, `backend-log-scan-{G1,R1,G2}.txt`;
  - `docker-disk-check.txt`, `e2e-down.txt`, `kc-svc-before.txt`, `kc-svc-status-after.txt`.
- **Drivers:** `lib.sh`, `obs.sh`, `run-g1.sh`, `run-r1.sh`, `run-g2.sh`, `diff.sh`, `logscan.sh`.
- The per-run test-results and dirty archives are in `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1757-final/`. They include the failure screenshots and error contexts in `G1-lifecycle-test-results/` and `G1-default-test-results/`.

## Environment left behind

- **The e2e stack is down:** 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.
- **The KC app services:**
  - **Before the gate:** stopped at 20:45:37 SAST from the main checkout with `svc.sh stop frontend portal gateway backend`.
  - **After the gate:** restarted with `svc.sh start all` (rc 0). All four were healthy at 21:25:00 SAST (`raw/kc-svc-status-after.txt`):
    - backend 8080 (PID 83576, `/actuator/health` UP);
    - gateway 8443 (83921, UP);
    - frontend 3000 (84099, 200);
    - portal 3002 (84175, 307 redirect to login).
  - **Infra containers:** `b2b-keycloak`, `b2b-postgres`, `b2b-localstack` and `b2b-mailpit` were untouched and healthy.
- **Main checkout:** this gate modified no file in it. It is still at `bd1a9518b`.
  - The founder's two untracked Phase 90 files are unchanged.
  - `.claude/markers/verify-{backend,frontend}.json` were rewritten at **21:24:54 SAST by another session**, recording PR #1757's verify. This gate did not write them.
- **The gate worktree is removed.** It was clean before removal.
- **Docker VM disk:** 30.0G free. Nothing was pruned.
- **The concurrent Maven verify** was not touched.
- Nothing was committed or merged.
