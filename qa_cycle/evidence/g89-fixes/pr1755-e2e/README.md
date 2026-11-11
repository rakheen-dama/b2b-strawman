# PR #1755 (G89 Batch J: raw-label fixes, W2-c2 digest + W2-c3 SoA): pre-merge e2e gate

**Verdict: PASS at PR head `1d2918ea8`. Every required suite met its baseline on its first and only scored run, with an empty per-test diff against the #1754 gate. There were no failures and no re-runs.**

- **All three changes were observed live on the PR backend:**
  - **(1) legal-za pack v15.** Every PR stack holds `pack_install legal-za = 15`. On an upgraded tenant, the v14 → v15 reconcile fired on boot: `Reconciled template pack legal-za to v15 … (0 new templates, 1 refreshed …)`, with 0 WARN and 0 ERROR.
  - **(2) SoA Type column.** It prints `Payment` and `Refund`. There were 0 raw-enum hits across 7 statements.
  - **(3) Weekly digest.** 11 digest emails were fired by hand through the internal endpoint. They show `Paid` / `Sent` / `Partially paid` and `R 5 482,63`-style money, with 0 raw status and 0 `ZAR` hits.
- **The branch moved during the gate.** `fix/g89-j-raw-labels` advanced to `34ab1c90e` (2 commits, 16:30–16:31 SAST): `fix(portal): digest keeps the ISO currency code on non-ZAR fee-note totals` and its red test.
  - The only runtime change is in `PortalDigestContentAssembler.DigestInvoiceLine`. `digestTotal()` returns exactly the old `formatAmount(...)` string when the currency is ZAR or blank, and appends `" " + currency` otherwise.
  - Every fee note in the e2e data is ZAR, so the e2e-observable behaviour of `34ab1c90e` is identical to the gated head. **This gate is for `1d2918ea8`.** The new commit needs its own `./mvnw verify`, and the orchestrator decides whether this e2e result carries over.

## Source and recipe

- **Worktree:** detached `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp8` at `1d2918ea88d1addf4c981595c947b9e15d0e9765`, created fresh.
  - `pwd` was confirmed, and the tree was clean (`dirty=0`) before every stack and after every run.
  - The merge base is origin/main `20857c03c`.
  - A second detached worktree, `…-g89-cp8-main` at `20857c03c`, was used only for the supplemental upgrade stack U.
  - Both worktrees ended with `git status --porcelain --untracked-files=all` empty and were removed with `git worktree remove`.
- **What changed vs main (runtime):** backend only.
  - `PortalDigestContentAssembler` (`DigestInvoiceLine` and the status labels);
  - `PortalEmailNotificationChannel` (labels moved out, `formatAmount` made package-visible);
  - `TrustTransactionTypeLabels` (new, shared);
  - `StatementOfAccountContextBuilder` and `TrustTxDto` (`transactionTypeLabel`);
  - legal-za `pack.json` v14 → v15 (6 SoA reconcile entries re-targeted or added);
  - `statement-of-account.json` (Type column key → `transactionTypeLabel`);
  - `portal-weekly-digest.html`.

  **No migrations, and no frontend or portal code.**
- **Recipe:** the #1754 recipe (`../pr1754-e2e/README.md` and `raw/`), with these differences:
  - `lib.sh` changed only WT, RAW and W.
  - `E2E_VISUAL` was unset (every run logs `E2E_VISUAL=unset`), and there was **no capture-then-score**. Every scored run was a single run.
  - The F71 deadline probe was dropped. The pack, SoA and digest probes (`obs.sh`) were added, plus the supplemental stack U (`run-u.sh` and `trust-probe.sh`).
- **Stacks:**
  - Every stack was built fresh with `e2e-down.sh && SHELL=/bin/bash e2e-up.sh`, nohup-detached with a marker file. **No reseed was run anywhere.**
  - The foreign-stack guard reported "no foreign e2e stack" before G1 and G2.
  - `caffeinate -dims` was held for every driver.
- **Tooling:**
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before Playwright flags.
  - Every run logs `node=v20.11.1 envnode=v20.11.1`.
  - `pnpm install --frozen-lockfile` returned rc 0.
- **Portal suites:**
  - spaced at least 330 s apart, measured from the newest magic-link token: 330 / 332 / 332 s;
  - `docker restart e2e-backend` plus a health wait before each;
  - `:3002` was free each time;
  - cumulative magic-link exchanges were 1 / 2 / 4 / 5, the same as #1753 and #1754.
- **Docker VM disk:** 22.0G used and 33.4G free (40 %) at 16:30, so **no prune was needed and none was done** (`raw/docker-disk-check.txt`). It was 23.0G used, 32.4G free after the gate.
- **KC services and load:**
  - The KC app services were stopped from the main checkout with `svc.sh stop frontend portal gateway backend` before the gate, and restarted after it. The `b2b-*` infra containers were untouched.
  - No Maven verify ran. The load average was still 15–27 during the runs, from other host activity.
- **Timing:** every run was on 2026-11-11, 16:32–17:06 SAST. The UTC and SAST dates were equal throughout.

## Verdict table

- Counts are passed / failed / skipped from Playwright JSON `.stats`. No run had any did-not-run (dnr) tests.
- The per-test diff is `diff <(cut -f1,3,4,5 prior.tsv | sort) <(cut -f1,3,4,5 run.tsv | sort)` against `../pr1754-e2e/raw/`. For lifecycle the comparison is `L-lifecycle.tsv`, #1754's passing run; for the others it is the same-named `G1-*` / `G2-*` file.

| Suite | Stack | Run (SAST) | p / f / s (dnr) | Baseline | Per-test diff vs #1754 | Status |
|---|---|---|---|---|---|---|
| portal vitest | — | 16:32:49–16:33:00 | 51 files / **284 passed** | 284 | — | **PASS** |
| legal-lifecycle | G1 | 16:34:21–16:37:30 | **105 / 0 / 0** | 105/0/0 | **empty** | **PASS** |
| legal-depth-ii | G1 | 16:37:46–16:39:46 | **40 / 0 / 4** | 40/0/4 | **empty** | **PASS** |
| default `test:e2e` | G1 | 16:41:14–16:42:28 | **165 / 0 / 14** | 165/0/14 | **empty** | **PASS** |
| portal 90-day | G2 | 16:44:42–16:45:10 | **13 / 0 / 0** | 13/0/0 | **empty** | **PASS** |
| portal responsive | G2 | 16:50:41–16:51:08 | **21 / 0 / 0** | 21/0/0 | **empty** | **PASS** |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | 16:56:39–16:56:40 | **2 / 0 / 0**, both ran | 2/0/0 | **empty** | **PASS** |
| login-bare | G2 | 17:02:27–17:02:44 | **1 / 0 / 0** | 1/0/0 | **empty** | **PASS** |

- **Snapshots, with compares off:**
  - every run had 0 "writing actual", 0 snapshot mentions and 0 baseline files written;
  - curated documentation screenshots: lifecycle 16, depth-ii 10, all others 0 (as in #1754 L / G1);
  - all were new untracked files, with `tracked-modified=0`. Each was archived to the scratchpad and restored, leaving `after-restore git-status-lines=0`.
- The known flakes **did not fire**: G89-F73 (`invoices/invoice-lifecycle.spec.ts:88`) and the `day-07-14-work-trust:78` time-summary lag.

## Stack table

- **Common to G1 and G2:** sha `1d2918ea8`, rc 0, backend `UP`, frontend / mock-idp / mailpit 200, `E2E Boot-Seed Complete!`, tenant `tenant_7d218705360b`, compose `working_dir` `…/b2b-strawman-g89-cp8/compose`, and the previous stack's volumes removed.
- **G1 backend provenance:** the image was built from this worktree (`COPY src src`, `mvnw … BUILD SUCCESS`). Its jar contains `verticals/legal/trustaccounting/transaction/TrustTransactionTypeLabels.class`, which is new in the PR. G2 and the U swap reused that same image, `35ad7b70c16c`.

| Stack | Purpose | Build (SAST) | rc | Backend image | Frontend image | Flyway tenant/global | legal-za `pack_version` |
|---|---|---|---|---|---|---|---|
| G1 | scored legal + default; SoA + digest probes | 16:33:00 → 16:34:19 | 0 | `35ad7b70c16c` (PR) | `87797a505c15` | **14 / 5** (`14\|g89 contains trust data`, `5\|portal strip matter type suffix`) | **15** |
| G2 | scored portal; digest probe | 16:43:53 → 16:44:39 | 0 | `35ad7b70c16c` (PR) | `87797a505c15` | **14 / 5** | **15** |
| U (supplemental, not scored) | main stack at v14, then the PR backend swapped in | main 17:03:04 → 17:04:20; swap 17:04:21 → 17:04:23 | 0 / 0 | `242c2bdf6a48` (main) → `35ad7b70c16c` (PR) | main | 14 / 5 | **14 → 15** |

- **Teardown:** `e2e-down.sh` at 17:05:17 returned rc 0, leaving 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.

## Observations

### (1) legal-za pack v15 and the reconcile on boot

- **Fresh PR stacks, G1 and G2** (`raw/obs-pack-G1-start.txt`, `obs-pack-G1-end.txt`, `obs-pack-G2-start.txt`):
  - read-only SELECT `pack_install` gives `legal-za|DOCUMENT_TEMPLATE|15`;
  - `org_settings.template_pack_status` gives `{"packId": "legal-za", "version": 15}`;
  - the `statement-of-account` template content binds `"key": "transactionTypeLabel"` and no longer binds `"key": "transactionType"`.
- **The boot log on a fresh tenant installs, it does not reconcile:** `Installed template pack legal-za with 18 templates`.
  - On G2 the backend was restarted 4 times, once per spacing, against the existing v15 tenant. Each restart re-ran the version gate, and the G2 log has **0 WARN and 0 ERROR** in pack, reconcile, digest, statement or legal-za.
- **A real v14 → v15 upgrade needs an existing v14 tenant**, so supplemental stack U provided one (`raw/obs-pack-U*.txt`, `raw/U-backend-pr.log`):

| | main backend (`20857c03c`) | after the PR backend swap (same postgres container and data) |
|---|---|---|
| `pack_install` legal-za | **14** | **15** |
| `template_pack_status` legal-za | `version: 14` | `version: 15`, `appliedAt 15:04:36Z` (at the PR boot) |
| SoA template | binds `transactionType`, not `transactionTypeLabel` | binds `transactionTypeLabel`, not raw `transactionType`; `updated_at 15:04:35.99Z` |
| boot log | — | `Running pack reconciliation for 1 tenants` · `Reconciled template pack legal-za to v15 for tenant tenant_7d218705360b (0 new templates, 1 refreshed, 0 re-pinned after the reconcile block)` · `Pack legal-za reconciled to v15 for tenant … (internal path)` · `Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed` |
| WARN/ERROR | 0 / 0 | **0 / 0** (no `Reconcile entry ignored`) |

### (2) Statement of Account, trust Payments Type column

- **How the text was read:** each PDF was pulled from LocalStack (`awslocal s3 cp`) and converted with `pdftotext -layout` in a throwaway `alpine:3` container. The outputs are `raw/soa-G1-*/<id>.txt` and `.pdf`.
- **Which suites produce one:** only legal-depth-ii (`day-30-45-soa-writeoff`) saves an SoA. **Legal-lifecycle generates none.** Its only "statement" is the bank-statement upload step, and `grep` finds no SoA generation in `tests/legal-lifecycle/`.

| SoA (G1, `generated_documents`) | Payments table Type column | raw-enum hits |
|---|---|---|
| Website Redesign (boot seed, 14:34:18Z) | (no trust rows) | 0 |
| Ndlovu v RAF ×4 (depth-ii 14:39:06 / :09 / :16 / :42Z) | **`Payment`** · PAY/DEEDS/mwc84584f099 · Deeds office search fee · R1 250,00 | 0 |
| Apex Holdings (depth-ii 14:39:36Z) | (no trust rows) | 0 |

- **The suites only produce PAYMENT debits.** To show more than one debit type, `trust-probe.sh` ran on stack U after the upgrade, through the firm API with no SQL writes:
  - Alice recorded a DEPOSIT (R10 000, RECORDED), a REFUND (R1 500) and a PAYMENT (R2 000);
  - Bob approved the refund and the payment (both 200, APPROVED);
  - `POST /api/matters/{Website Redesign}/statements {"periodStart":"2026-10-12","periodEnd":"2026-11-11"}` returned 201 with `containsTrustData=true`.

  The `htmlPreview` (`raw/trust-U/trust-U-soa.html`) showed:
  - `Payments` rows: `11 November 2026 | Refund | GATE/REF/1755 | … | R1 500,00` and `11 November 2026 | Payment | GATE/PAY/1755 | … | R2 000,00`;
  - `Deposits` row: `11 November 2026 | GATE/DEP/1755 | Gate deposit | R10 000,00`;
  - raw-enum hits (PAYMENT / REFUND / DEPOSIT / FEE_TRANSFER / …): **0**.
- **"Deposit" never appears in a Type column, by design.** `trust.deposits` (DEPOSIT, TRANSFER_IN, INTEREST_CREDIT) renders a Deposits table whose columns are Date / Reference / Description / Amount (`statement-of-account.json` l.275–280). Only `trust.payments` has a Type column. Deposit rows printed correctly, and with no Type column.
- **The trust-activity email moved onto the shared labels**, `TrustTransactionTypeLabels`. The three emails the U probe triggered for `contact@acme.example.com` (`raw/obs-trust-U-emails.txt`) read `Type | Deposit | R 10 000,00`, `Type | Refund | R 1 500,00` and `Type | Payment | R 2 000,00`. There were 0 raw enums.

### (3) Weekly portal digest

- **No suite sends the digest**, so it was fired once by hand per stack, after the scored runs, through `PortalDigestInternalController`. The key, `e2e-test-api-key`, comes from `compose/docker-compose.e2e.yml:126` and `application-e2e.yml`. The Mailpit UI and API are on `:8026`.

  ```
  curl -s -X POST -H 'X-API-KEY: e2e-test-api-key' 'http://localhost:8081/internal/portal/digest/run-weekly?orgId=e2e-test-org'
  ```
- **G1** (16:43:31 SAST): `{"tenantsProcessed":1,"digestsSent":10,"skipped":13,"dryRun":false,"errors":[]}`. Mailpit received 10 × "E2E Test Organization: Your weekly update" (`raw/obs-digest-G1.txt`, bodies in `raw/digest-G1/`). The 6 emails with a fee-note row:

| To | Fee-note row (HTML, tags stripped) | DB `portal_invoices` (status, total, currency) |
|---|---|---|
| contact@acme.example.com | `INV-0001 — Sent  R 8 625,00` | SENT · 8625.00 · ZAR |
| moroka.trust…@example.com | `INV-0002 — Partially paid  R 8 625,00` | PARTIALLY_PAID · 8625.00 · ZAR |
| sipho.ndlovu.mwc7zrdxc00a@… | `INV-0003 — Sent  R 5 482,63` | SENT · 5482.63 · ZAR |
| apex.holdings.mwc7zrdxc00a@… | `INV-0004 — Paid  R 40 250,00` | PAID · 40250.00 · ZAR |
| apex.holdings.mwc84584f099@… | `INV-0005 — Sent  R 4 025,00` | SENT · 4025.00 · ZAR |
| invlife-client-94pc@… | `INV-0006 — Paid  R 1 725,00` | PAID · 1725.00 · ZAR |

  The other 4 digests had no fee note in the window (0 rows). Across all 10 HTML bodies, hits for `PAID|SENT|PARTIALLY_PAID|APPROVED|DRAFT|VOID|ZAR` were **0**.
- **G2** (17:02:44 SAST): `digestsSent:1, skipped:0, errors:[]`. The email to `contact@acme.example.com` has `INV-0001 — Sent  R 8 625,00` and `INV-0002 — Partially paid  R 2 300,00`, with 0 raw hits.
- **Side note, not this PR:** in the digest's plain-text MIME part, the `<span> &mdash; </span>` separator shows up as the literal text `&mdash;`. That template line predates this PR, and the HTML part renders "—" correctly. It is not filed and not in scope; it is noted for the orchestrator.

## Failure classification

**There were no failures.** No suite failed on its scored run, so no re-run and no main reproduction were needed.

| Class | Count |
|---|---|
| (a) PR regression | **0** |
| (b) intended change needing a spec update (for example, a spec asserting raw `PAYMENT`) | **0**. The depth-ii SoA spec asserts `Payments[\s\S]*PAY/DEEDS/…`, which is agnostic to the Type text. |
| (c) pre-existing, reproduced on main | **0** |
| known flakes (F73, `day-07-14-work-trust:78`) | did not fire |

## Backend log (`raw/backend-log-scan.txt`)

Counts are JSON-level WARN/ERROR by logger:
- **G1:** **0 ERROR**. The WARN classes are as in #1754: PDFBox font fallback, draft invoice without tax number, `Forbidden … /time-summary/by-member` ×12, `No recipients found for DOCUMENT_GENERATED`, and one-off boot WARNs. There were 0 PR-area WARN/ERROR (digest, statement, reconcile, `TrustTransactionTypeLabel`, template pack, legal-za).
- **G2**, including 4 restarts and the digest fire: **0 ERROR**, 0 PR-area. The single `security.auth_failed … /api/clients` is the isolation spec's expected "Portal JWT blocked from org API".
- **U, after the swap**, including the reconcile, the trust probe and the SoA generation: **0 ERROR**, 0 PR-area.

## Raw files (`raw/`)

- **Scored runs:**
  - `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}` and `G2-{90day,responsive,isolation,login-bare}.*`;
  - a `*.dirty.txt` per run (the paths written before restore);
  - `portal-vitest.txt`, `pnpm-install.txt`.
- **Observations:**
  - `obs-pack-*.txt`;
  - `obs-soa-G1-*.txt` with `soa-G1-*/` (PDFs and pdftotext);
  - `obs-digest-G{1,2}.txt` with `digest-G{1,2}/` (HTML and text bodies);
  - `obs-trust-U.txt`, `obs-trust-U-emails.txt` and `trust-U/` (SoA `htmlPreview` and the trust emails).
- **Stacks:**
  - `stacks.txt` (checks, accounting and spacing);
  - `timeline.txt`;
  - `stack-{G1,G2,U-main,U-swap}.log`;
  - `G1-backend.log`, `G2-backend.log`, `U-backend-pr.log`;
  - `backend-log-scan.txt`;
  - `docker-disk-check.txt`;
  - `e2e-down.txt`;
  - `kc-svc-status-after.txt`.
- **Drivers:** `lib.sh`, `obs.sh`, `run-g1.sh`, `run-g2.sh`, `run-u.sh`, `trust-probe.sh`, `logscan.sh`.

## Environment left behind

- **The e2e stack is down:** 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.
- **The KC app services are restarted and all healthy** (17:06:15 SAST, `raw/kc-svc-status-after.txt`):
  - backend 8080 (PID 57642);
  - gateway 8443 (57909);
  - frontend 3000 (58044);
  - portal 3002 (58159).

  The `b2b-keycloak`, `b2b-postgres`, `b2b-localstack` and `b2b-mailpit` containers were up and healthy throughout.
  - The main checkout moved to `bd1a9518b` during the gate, through the founder's own commits "90 - redesign" and "fix conflict" (16:42–16:43). The services restarted from that checkout.
  - No file in the main checkout was modified by this gate.
- **Both gate worktrees are removed** (clean before removal). The per-run dirty archives (curated PNGs) and full test-results copies are in `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1755/`.
- **Docker VM disk:** 32.4G free. Nothing was pruned.
- Nothing was committed or merged.
