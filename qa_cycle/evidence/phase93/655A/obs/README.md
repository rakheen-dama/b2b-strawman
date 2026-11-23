# 655A OBS (task 655.10): clients take-on — attest, commit, results, portal access, dormancy

- **Stack:** E2E mock-auth stack built fresh (`e2e-down.sh && e2e-up.sh`) from worktree-epic-655A @ `d2738e819` (PR #1850), after the E2E-LEGAL runs on a separate stack. Backend container created 2026-11-23T22:30:54Z. Tenant `tenant_7d218705360b`, Flyway V34 applied.
- **Browser:** headless Chromium from `frontend/node_modules/@playwright/test`, node v20.11.1, driven by `obs.cjs` (included as last run; `STEP=0|a|b|carol|c|d|e|f|g node obs.cjs`). Sign-in via `/mock-login`. Alice (owner) unless stated.
- **Database:** read-only `psql` `select`s only (`psql-0*.txt`). No SQL writes.
- **REST:** only the Mailpit API (`:8026/api/v1/messages`). Every product action went through the browser.
- **Window:** 2026-11-23T22:33:55Z (`obs-window-start.txt`, before the upload) → 22:38:14Z. One pass per step, no re-runs; `obs-log.txt` is the full log in step order.
- **Fixture:** `fixtures/clients-20.csv`, 20 fictional rows. Headers equal the catalogue labels, so auto-suggest mapped all 16 columns (`[suggested mapping]` in the log). The file has no CDD records location column; the batch default "Archive room B, Sandton office" was typed in the mapping step. The date format was switched to yyyy-mm-dd.
  - Duplicate 1: file row 17 `T. Mokoena` repeats row 2's email.
  - Duplicate 2: file row 18 `Acme Corporation` / `Contact@acme.example.com` (case variant), which matches seeded client Acme Corp `contact@acme.example.com` (found in the browser, step 0).
  - Bad rows: row 19 risk rating `VERY_HIGH`; row 20 missing name.
  - No email: row 21. UNRATED: row 5 `Naledi Khoza`.

## Verdict

**Not a clean PASS.** 8 checks PASS. Check (a) has one sub-check that FAILs: a row matched to an existing client cannot be told apart in the row table (OBS-655A-1, in scope, reported). Check (g) is OBSERVED-AS-NOT-CANDIDATES for the dormancy check, and the job run itself is DEFERRED.

| Check | Verdict | Figures / observation | Evidence |
|---|---|---|---|
| a. Upload → map → validate | **PASS** for totals, valid, errors and messages. **FAIL** for the duplicate count against the table (OBS-655A-1) | The cards show Total 20, Valid 16, Errors 4, Duplicates 1, Blocks 0. The table matches: 20 rows under All, 16 under Valid, 4 under Error. The status filter has no duplicate option, and row 18, the existing-client match, shows plain "Valid" with no message, so the table cannot show the 1. psql confirms row 18 `duplicate_of_id` = Acme Corp. Messages: row 17 "Duplicate of row 2 in this file"; row 19 "VERY_HIGH is not one of: Low, Medium, High, Unrated"; row 20 "Client name is required."; row 21 "An email address is required to create a client in Kazi". | `a-03`, `a-04`, `a-05-*`, `psql-01-rows-after-validate.txt` |
| b. Attestation (Alice) | **PASS** | The statement is FICA-ATTEST-v1 with "performed by **E2E Test Organization**" filled in, the org name. Batch default: CDD records location = Archive room B, Sandton office. The FICA summary covers "the 15 clients to be created" and every count matches the file: Natural person 12 · Company 1 · CC 1 · Trust 1; Low 8 · Medium 5 · High 1 · Not risk-rated 1; BO Done 3 · N/A 12; Screened clear 13 · match 1 · not screened 1; SoF Done 10 · Not required 5. Attest is disabled until the box is ticked (true → false). After attesting: "Attested by Alice Owner (Owner) on 24 Nov 2026, 00:34." with Commit and Withdraw. | `b-01..03`, log `[statement]`, `[fica summary]` |
| b′. Carol (member) cannot attest | **PASS** | The deep link `?tab=take-on&batch=…` gives "You don't have access to Take-on — showing Templates instead." No wizard, 0 Attest buttons, 0 Commit buttons. Read only. | `b-04-carol-deeplink.png` |
| c. Commit, progress, results; Mailpit | **PASS** | Mailpit was **2** before the commit and **2** after (+5 s). The dialog reads "Commit clients take-on?" / "15 records will be created, and 1 duplicate skipped. This cannot be undone.", which equals the outcome. Progress showed "Committing… 0 of 16", then Completed. The drain took ~1.7 s (commit_requested 22:35:03.69 → committed 22:35:05.35), under the 2 s poll, so no intermediate n was seen. Results: **Created 15 · Duplicate — skipped 1 · Failed 0**. | `c-01..03`, log `[progress]`, `[results]` |
| d. Clients ACTIVE | **PASS** | Client list: 16 rows (Acme + 15). Spot checks: Karoo Sunrise Family Trust and Naledi Khoza both show "Active" in the list and on detail. psql: 15 `customers` with `take_on_batch_id` = batch, all ACTIVE, all with `taken_on_at`. Rows: CREATED 15, SKIPPED_DUPLICATE 1 (→ Acme Corp id), ERROR 4. | `d-01..05`, `psql-02-customers-after-commit.txt` |
| e. Portal access; Mailpit | **PASS** | Before: **0** portal contacts for the batch's clients (1 in the tenant). After clicking: "15 portal contacts created". psql: **15** (16 in the tenant), all GENERAL / ACTIVE, each with the client's email. Mailpit **2 → 2** (+5 s). | `e-01`, `e-02`, `psql-03-portal-contacts.txt` |
| f. Result CSV | **PASS** | `result-download/take-on-result-<batch>.csv` has a UTF-8 BOM, a header and 20 rows: 15 Created (with ids, e.g. row 8 = Karoo `b7017b51…`, row 5 = Naledi `c854c32f…`, both equal to the client URLs), 1 "Duplicate — skipped" → Acme `cfed538d…`, and 4 Error with the same messages as the table. After the commit the Validation table shows the same per-row outcomes. | `result-download/`, `f-01`, `f-02` |
| g. Dormancy | **OBSERVED-AS-NOT-CANDIDATES** for the check. The job run is **DEFERRED** | Compliance page → "Check for Dormant Clients" → "No dormant clients detected." Backend log: "Dormancy check completed: threshold=90 days, candidates=0". The 15 imported clients have 0 matter links, 0 documents and 0 invoices (psql). Without `MAX(c.taken_on_at)` they would match the `IS NULL` branch of `DORMANCY_SQL`, so the take-on instant is what keeps them out. All 15 are still ACTIVE afterwards. **Job run:** no product trigger exists. `public.job_queue` has no `dormancy_check` job, the platform-admin jobs API only lists, retries and deletes existing jobs, and the platform-admin UI has no jobs page. The 02:00 job was not run. `executeDormancyTransitions()` transitions exactly `runDormancyCheck().candidates()` (`CustomerLifecycleService.java:284-287`); that is a code read, not an observation. The transition is proven by `ImportedClientDormancyIntegrationTest` (BE-VERIFY gate). | `g-01`, `g-02`, `psql-04-dormancy.txt`, `psql-05-job-queue.txt` |
| h. No PII in audit | **PASS** | 38 audit rows since the window start, all in scope: customer.created 15 (`{source: TAKE_ON, take_on_batch_id, actor_name}`), customer.fica_attested 15, and one each of takeon.batch.created / source.uploaded / source.mapped / batch.validated / batch.attested / commit_requested / batch.committed / portal_contacts.created. A grep for 104 fixture canaries (names, surnames, emails, dotted local parts, ID, tax and phone numbers) found **0 hits** in the takeon/customer rows and **0** across every audit row in the window. Positive control: "Alice Owner" was found 38×. A first pass also used the generic local part `contact` and got a false positive in the event type `portal_contacts`; that canary was dropped (recorded in the file header). | `psql-06-audit-events.txt`, `audit-canary-grep.txt`, `canaries.txt` |
| i. Backend log | **PASS** | 31 lines in the window, **0 WARN / 0 ERROR**, **0** canary hits. Log messages carry ids only, e.g. "Created portal contact <id> for customer <id>". The frontend container logged nothing in the window. | `backend-log-obs-window.txt`, `backend-log-warn-error.txt` (empty), `frontend-log-warn-error.txt` (empty) |

Browser console: 1 error in the whole run. It was the known mock-auth `MockAuthContextProvider: failed to fetch userinfo` during step d's login, which is not a 655A error.

## In-scope findings (reported to the orchestrator, not fixed)

### OBS-655A-1: a row matched to an existing client cannot be seen as a duplicate before the commit

- **Repro:** this fixture, file row 18 (`Acme Corporation`, `Contact@acme.example.com`; seeded Acme Corp is `contact@acme.example.com`).
- **What the screen shows:** the Validation step shows Duplicates **1**. Row 18 has status "Valid" and an empty Messages cell, and the status filter offers only All / Pending / Valid / Error. The attester therefore cannot see which row will be skipped, or which client it matched, before attesting and committing.
- **What the data says:** `take_on_rows.duplicate_of_id` is set on row 18 (`psql-01`). After the commit the row correctly shows "Duplicate — skipped".
- **Spec:** §17.3.2 says the cards "cannot disagree with the row table" (arch line 536).
- **Screenshot caveat:** at 1440 px the row table scrolls sideways, so the Status and Messages columns are cut off in `a-04`. The authoritative evidence is the DOM text in `obs-log.txt` (`[rows (All statuses)]`), which has row 18 as `… | Valid | City: Johannesburg … |` with an empty last cell. The batch is now COMPLETED, so the pre-commit state cannot be re-captured.
- **Where:** `frontend/components/take-on/validation-step.tsx` renders no marker for `row.duplicateOfId`. 655A is the first dataset that sets `duplicate_of_id` against existing records (`ClientsDatasetHandler`).
- **Evidence:** `a-04-validation-all.png`, `psql-01-rows-after-validate.txt`.

### OBS-655A-2 (minor): an invalid risk rating is shown as "Not risk-rated"

- **What the screen shows:** file row 19 has `VERY_HIGH`. Its Risk rating cell shows "Not risk-rated", next to the error "VERY_HIGH is not one of: Low, Medium, High, Unrated".
- **Cause:** `ClientsDatasetHandler.mapAndValidate` calls `mapped.putIfAbsent("riskRating", UNRATED)` after the enum check has dropped the bad value, so `mapped_values.riskRating = UNRATED` while `raw_values` = `VERY_HIGH` (`psql-01`).
- **Impact:** the row is ERROR, so it is never committed and is not counted in the FICA summary, which only counts VALIDATED rows. The cell still contradicts the file.
- **Evidence:** `a-04-validation-all.png`, `a-05-filter-error.png`.

## Noted, not a defect

- Imported clients show FICA "Not started" on the client list and detail. This is the designed 655A → 655B intermediate state (P-19): the projector learns `ATTESTED` in 655B.
