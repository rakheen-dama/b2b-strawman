# Phase 93 exit — the take-on, observed end to end (660A, run of record)

The client & trust take-on of a fictional firm, driven through the browser by
`frontend/e2e/tests/legal-lifecycle/take-on.spec.ts` (Playwright project `take-on`) on a freshly
built e2e stack, ending with the first post-cutover month reconciled and balanced. The fixture set
and every expected figure, worked out before the run, are in
[`qa/fixtures/take-on/README.md`](../../../qa/fixtures/take-on/README.md).

This directory holds the take-on run of record only. The other three suites (legal-lifecycle,
legal-depth-ii, exit-month), `suites/README.md` and the closure report are the orchestrator's
(task 660.4/660.6, pending).

## Run of record

| | |
|---|---|
| Result | **16 passed (2 with DEFERRED sub-checks: CashDigest, dormancy job — G93-2/G93-3), 0 failed, 0 skipped** in 2.1 min (`run-of-record/take-on-run.txt`, exit 0). After this run the review added step 17 (Mailpit total and latest message unchanged since step 0, over the whole run), so the spec now has 17 tests; step 17 is not in this run of record (the per-action Mailpit checks and `run-of-record/mailpit-final.txt` show the same total, 2, at the end). |
| Annotations | 2 × `DEFERRED` (step 12–13: CashDigest, B-3/D-261; step 14: dormancy job, B-2/D-260) and 1 × `FINDING` (step 4: F-660A-1, D-264). The list reporter does not print annotations; they are in the spec, at those steps. |
| Window | 2026-11-25T02:02:02Z → 02:04:32Z (`run-of-record/run-window-*.txt`) |
| Stack | e2e mock-auth stack, `e2e-down.sh && e2e-up.sh` from worktree-epic-660A, containers created 2026-11-25 04:01:13 SAST (02:01:13Z) (`run-of-record/stack-containers.txt`). Nothing ran on it before the spec (step 0 checks: only the seeded primary trust account, no take-on batch, no fixture client). Torn down after the evidence was taken. |
| Code | Product code at `ef5579c29` (`run-of-record/stack-head.txt`); the spec, helper and fixtures as committed in this PR. The branch was then rebased on `main` `8534e17a2`, which adds two lines of docs only (`tasks/phase93-*.md`), so the stack's product code is unchanged. |
| Tenant | `tenant_7d218705360b`, Flyway **V37** `phase93 brought forward fee notes` (`psql-output.txt`). |
| Dates | Run on 2026-11-25 (SA) → **C = 30 September 2026**, first month **October 2026**. |
| Browser | Chromium (Playwright 1.59) on nvm node v20.11.1; Bob (admin) prepares and commits, Alice (owner) signs off and reconciles; one context each. |
| Command | `cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026 TAKE_ON_EVIDENCE_DIR=<dir> pnpm test:e2e:take-on --reporter=list` |

Earlier attempts on their own fresh stacks (each failed at a spec selector, not the product, and
was fixed before the next fresh stack): run 1 step 3 (the "File room" leftover trigger's test id
carries the header index), run 2 step 4 (the wizard does not walk trust sources in upload order),
run 3 step 4 (the spec asserted "Continue to sign-off" absent on a blocked batch → F-660A-1,
D-264), run 4 step 11 (a row-wide money match ran into the due date's "…2026"). Run 5 passed 16/16;
the run of record is run 6, after the last spec edits, on its own fresh stack.

## Steps and what they observed

| # | Step | Observed (asserted in the spec) | Screenshots |
|---|---|---|---|
| 0 | Fresh stack | 1 trust account (the seeded primary), 0 batches, 0 fixture clients; Mailpit 2 | — |
| 1 | Accounts (Bob) | G "Take-on Trust Banking Account" GENERAL **not primary**, I3/I4 INVESTMENT (s86(3)/s86(4)), opened 2024-01-01 | `01-trust-accounts` |
| 2 | Clients XLSX (Bob) | Sheet step: "Old notes" hidden badge, "Clients" pre-selected, header row 1. Cards 23 · 19 · 4 · 1 · 0. Rows 20/22/23/24 errors with exact texts (in-file duplicate of row 5, `VERY_HIGH` with risk cell "—", name required, email required); row 21 "Duplicate — will be skipped" / "Matches a client already in Kazi"; filters Error = 20, 22, 23, 24 and Duplicate = 21. FICA-ATTEST-v1 "performed by E2E Test Organization", "of the 18 clients to be created"; "Attested by Bob Admin (Admin) on …". Commit "18 records will be created, and 1 duplicate skipped. This cannot be undone." → Created 18 · Duplicate — skipped 1 · Failed 0. "18 portal contacts created". Mailpit 2 → 2 (commit), 2 → 2 (portal) | `02-*` |
| 3 | Matters (Bob) | Cards 16 · 15 · 1 · 0 · 0; row 16 "No client matches C-099. Take the client on first, or correct the reference." (the only Error row); row 11 "Not a member" + exact lead text. "15 records will be created." → Created 15 · Failed 0; after the commit row 16 is still Error, row 2 Created. Matters tabs: Amahle Zulu (2), Bongani Nkosi (2), Johan Pretorius (by email), Ayesha Patel (by ID number). Mailpit 2 → 2 | `03-*` |
| 4 | Trust, debit (Bob) | Cards 9 · 8 · 1 · 0 · **2**; the TL-C1 row Error + Block with the exact `CLIENT_IN_DEBIT` text; banner "Statutory block — cannot be overridden", the exact EQ-2 text (R353 500,00 vs R403 500,00, R50 000,00) and the row-blocks line; 0 controls in the banner, no commit, `allowedActions` without SIGN_OFF/COMMIT. F-660A-1: "Continue to sign-off" offered; its step offers no sign-off and no commit | `04-trust-files`, `04-trust-debit-block`, `04-trust-debit-block-sign-off-step` |
| 5 | Trust, R10 (Bob) | Corrected client file (remove → add → map); cashbook 403 510.00 → cards 9 · 9 · 0 · 0 · 2; exact EQ-1 (U+2212) and EQ-2 (R10,00) texts; indicator EQ-1/EQ-2 `data-holds=false` "Difference R 10,00" | `05-trust-r10-block` |
| 6 | Trust, clean (Bob) | Cashbook 403 500.00 → Validated, 9 · 9 · 0 · 0 · 0, no banner, all five indicator rows "Difference R 0,00"; one flag "Unidentified deposit older than one year"; Bob's Sign-off step: "A partner or director signs off this take-on from their Home page." | `06-trust-validated` |
| 7 | Sign-off (Alice) | Home widget "Trust take-on awaiting your sign-off", item "Take-on Trust Banking Account at 30 September 2026" / "Prepared by Bob Admin"; TAKEON-SIGNOFF-v1 declaration exact; "Signed off by you (Owner) on …"; the item leaves the widget after the sheet closes | `07-*` |
| 8 | Commit (Bob) | "Signed off by Alice Owner (Owner) on …"; dialog "Commit trust opening position take-on?" / "9 records will be created. This cannot be undone." / the exact trust sentence; Failed 0; report: every position difference R 0,00, trust creditors R 403 500,00, "Signed off by Alice Owner (Owner) … Prepared by Bob Admin.", not "Prepared and signed off by the same person"; flag shown; batch COMPLETED. Mailpit 2 → 2 | `08-trust-results-report` |
| 9 | Report files | Results step PDF (`%PDF`, 9 641 B) and CSV (2 219 B, first line `# Trust take-on report — Take-on Trust Banking Account at 30 September 2026`, header `Section,Group,Client,Matter,Reference,Date,Amount`); Trust → Reports card "… — taken on at 30 September 2026 … by Bob Admin": both downloads byte-identical (`sha256-downloads.txt`), equal to `take_on_report_files.sha256` (`psql-output.txt`) | `09-trust-reports-take-on-card` |
| 10 | FICA queue (Alice) | 5 rows in order Naledi Khoza → Pieter Steyn → Refilwe Dube → Kgomotso Moloi → Highveld Logistics (Pty) Ltd, each "Review due (attested)" with its reason | `10-fica-review-queue` |
| 11 | Debtors (Bob) | Cards 11 · 6 · 5 · 0 · 0; the default due-date warning (20 October 2026) + "Default"; "Kazi's own fee-note numbers will continue after INV-0007."; "Address or email missing" (Valid); five exact errors; Error filter = 8–12. "Commit debtor opening balances take-on?" / "6 records will be created." → Created 6. Fee-note list: six rows "Sent" + "Brought forward — take-on" with their amounts; INV-0001 no badge. Mailpit 2 → 2 | `11-*` |
| 12–13 | Collections | "Enable collections reminders" off; Debtor book: each of the six clients' amount in the bucket of its due date as of 2026-11-25 (computed in the spec), other buckets R 0,00. **CashDigest DEFERRED** (no trigger) | `12-*` |
| 14 | Dormancy | "Check for Dormant Clients" → "No dormant clients detected." (backend: "Dormancy check completed: threshold=90 days, candidates=0"); all 18 imported clients ACTIVE. **Job run DEFERRED** (no trigger) | `14-dormancy-check` |
| 15 | First month (Alice) | Wizard on "Take-on Trust Banking Account (Standard Bank - 6570001)", period end 2026-10-31; `first-month-statement.csv` → "Lines imported: 6", "Period: 2026-10-01 to 2026-10-31", no import warning; "Auto-matched 2 lines", "4 lines remaining"; recorded Unidentified deposit, Fund sweep, Fund interest, Bank charge (recoverable, VAT 0). Bank = adjusted bank = cashbook = trust creditors **R 403 477,40**, both differences R 0,00; client ledgers 499 500,00, Fund 227,40, unidentified 3 750,00, placed in s86(3) −100 000,00; 0 unresolved; flags exactly `UNIDENTIFIED_OVER_ONE_YEAR` (no `OPENING_DIFFERS_FROM_TAKE_ON`); Complete → "October 2026 list locked — R 403 477,40 agrees with the cashbook and the adjusted bank balance." No manual match, exclusion or adjustment. Mailpit 2 → 2 | `15-*` |
| 16 | R&P over C | One "Balance brought forward — take-on" row R 403 500,00 and the footnote "The opening position taken on at 30 September 2026 is shown as one brought-forward line, not as receipts." | `16-receipts-payments` |

## Read-only database check (`psql-check.sh` → `psql-output.txt`)

SELECT only, inside `default_transaction_read_only=on`, run right after the spec on the same stack:
4 batches COMPLETED (clients 23/19/4/dup 1, matters 16/15/1, trust 9/9/0, debtors 11/6/5); rows
CREATED 18 + SKIPPED_DUPLICATE 1 + ERROR 4 / CREATED 15 + ERROR 1 / CREATED 9 / CREATED 6 + ERROR
5; 18 customers ACTIVE with `taken_on_at`; six SENT brought-forward fee notes (Σ 31 625,50),
`invoice_counters.next_number` 8 (past INV-0007); G: Σ cashbook_effect = Σ ledger_effect =
403 477,40 over 15 posted rows, reconciliation 2026-10-31 COMPLETED difference 0.00 notes null,
monthly list 2026-10-31 creditor total 403 477,40 difference 0.00; the six statement lines
AUTO_MATCHED ×2 / RECORDED ×4 with their classifications; G, I3, I4 `take_on_cutover_date`
2026-09-30; `collection_activities` 0, `collections_enabled` false.

## Side effects and logs

- **Mailpit:** 2 at the start (the seed's two emails to `contact@acme.example.com`) → 2 after every
  commit, the portal access and the reconciliation (8 s settle each; `run-of-record/evidence-log.txt`)
  → 2 at the end (`run-of-record/mailpit-final.txt`). The latest message never changed.
- **Backend** (`run-of-record/backend-log-run-window.txt`, 67 lines): 0 ERROR; 17 WARN
  (`backend-log-warn-error.txt`) = 16 × pdfbox "Using fallback font LiberationSans …" (one report
  render, OBS-657D-2, known) + 1 × Micrometer "Reached the maximum number of 'uri' tags for
  'http.server.requests'" (F-660A-2). "Take-on batch … committed" for clients (created=18,
  skipped=1), matters (15) and debtors (6); none for the trust commit (OBS-657C-1, known). No
  fixture name, email, ID number or fee-note number in the backend or frontend log (grep: 0).
- **Frontend container** (`frontend-log-run-window.txt`): 8 × "⨯ Error: The destination stream
  closed early." (Next.js aborted RSC stream on page reloads; the same message 657B/657C/657D
  recorded; not attributed to take-on).

## Files

- `run-of-record/` — `take-on-run.txt` (list reporter, ANSI stripped), `evidence-log.txt`
  (dates, batch/report ids, Mailpit before/after per action, downloads), `state.json`,
  `stack-containers.txt`, `stack-head.txt`, `run-window-{start,end}.txt`, backend/frontend logs,
  `mailpit-final.txt`.
- `screenshots/` — the spec's full-page captures (names = steps above).
- `csv/` — every fixture as rendered for this run (dates resolved) and `first-month-statement.csv`
  as generated.
- `downloads/` + `sha256-downloads.txt` — the report PDF/CSV from the Results step and the Reports
  card.
- `psql-check.sh`, `psql-output.txt` — the read-only check. (`psql-output.txt` is from the run of
  record; the review then added the `## cutover = C` section and its exit-4 assertion to the script.)
- `gap-report.md` — the findings (draft; the orchestrator adds the other suites' findings).
- `found.md` — review items deferred out of 660A (shared-helper refactors).
