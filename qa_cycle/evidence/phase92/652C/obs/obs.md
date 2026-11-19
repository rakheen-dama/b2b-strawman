# 652C — OBS gate (PR #1817): one reconciliation per trust account and period end

- **Head:** `93c8af2d5a3b542b4fb4610576f899aa1a5e325a` (worktree `worktree-epic-652C`, tree clean apart from this `obs/` dir).
- **Date:** 2026-11-19 (SAST). **Stack:** e2e mock-auth (:3001 / :8081), rebuilt from the head with `e2e-rebuild.sh backend frontend` (`rebuild.log`, exit 0). Not wiped or reseeded, so V29 ran on the duplicate rows the repro left.
- **User:** Alice (owner) via `/mock-login`. Headless Chromium through the Playwright library (`obs.cjs`, `obs-refusal.cjs`). The Playwright MCP was not used. Every state change went through the wizard UI. psql was used for read-only SELECTs only. No product code was changed.
- **Tenant:** `tenant_7d218705360b`. **Account:** primary `5149d2be-b51f-4a7f-b375-f2a851a4a028` (the only trust account).

## Overall: PASS for a, b, c and d. Two narrow sub-points are not observable (see d and the notes).

## 0. The stack serves the head

| Proof | Evidence |
|---|---|
| Flyway applied V29 on the tenant schema | `backend-flyway-v29.log`: `Migrating schema "tenant_7d218705360b" to version "29 - phase92 trust recon unique"` then `Successfully applied 1 migration … now at version v29 (execution time 00:00.014s)` at 13:51:10Z. `flyway_schema_history` rank 29, `V29__phase92_trust_recon_unique.sql`, success t (`psql-after.txt`) |
| Backend jar is the head build | `chunk-grep.txt`: `app.jar` copied out of `e2e-backend` holds `db/migration/tenant/V29__phase92_trust_recon_unique.sql`, **byte-identical** to the worktree source. `TrustReconciliationService.class` contains the strings `This period is already reconciled`, `trust_reconciliation.statement_changed` and `uq_trust_recon_account_period` |
| Frontend is the head build | `chunk-grep.txt`: the server chunk's `createReconciliation` now returns `{success:!1,error:b,...a&&a!==b?{errorTitle:a}:{}}` (the 652C `actions.ts` change; on main this action had no `errorTitle`). The client chunk `30d89qof0d-jy.js` carries `errorTitle` and `reconciliation-error-title` |

The 409 title text comes from the backend, not the frontend bundle. The frontend proof is the new `errorTitle` plumbing.

## 1. V29 on the live duplicates

**BEFORE** (`psql-before.txt`): 11 reconciliations, all DRAFT, none COMPLETED, no monthly lists, max tenant Flyway version 28. Only `idx_trust_recon_account_period` and the pkey existed. Duplicate groups:

| period_end | rows | ids (updated_at) |
|---|---|---|
| 2026-11-14 | 2 | f42ce541 (13:19:17.51), **93325cd7 (13:19:19.33)** |
| 2026-11-15 | 3 | 6b45b608 (13:19:09.50), 19614671 (13:19:12.37), **3d168e26 (13:19:14.20)** |
| 2026-11-19 | 3 | 74203dcb (11:26:55.28), 22476a5c (12:26:25.14), **258446a3 (12:56:46.42)** |

**AFTER** the rebuild (`psql-after.txt`):
- 6 rows remain. **No (account, period_end) group has count > 1.**
- `uq_trust_recon_account_period` is present: `CREATE UNIQUE INDEX … (trust_account_id, period_end)`. `idx_trust_recon_account_period` is kept.
- The surviving row for each key is the bold one above: 93325cd7 (14 Nov), 3d168e26 (15 Nov) and 258446a3 (19 Nov).
- This matches the rule. No row is referenced by a monthly list and none is COMPLETED, so ranks 1 and 2 are ties. Rank 3 is `completed_at` (all null), then `updated_at DESC`, so the most recently updated DRAFT is kept. The 5 non-duplicated rows are untouched.
- The audit history of the 5 deleted rows is kept (13 `audit_events` rows still reference them).
- The list page then shows "7 reconciliations found", with one row each for 19, 15 and 14 Nov (`obs-a1-list.png` … `obs-a4-list.png`). The repro showed 11.

## a. Re-upload within one wizard session (period end 16 Nov 2026): PASS

`obs.cjs` run `M362I` (`obs-result-run1.json`, `obs-requests-run1.log`). Each step checks the DB, takes a wizard screenshot, and checks the list in a second tab so the wizard session survives.

| Step | Server actions (all 200) | Drafts for 16 Nov (DB) | Draft `bank_statement_id` | List rows for 16 Nov |
|---|---|---|---|---|
| A1: upload `a1` (R101,11), auto-match, Review Matches | … `createReconciliation`, `calculateReconciliation` | **1**, `cd7b2e57` | `42af8abd` (a1, 1st import) | **1** ("16 Nov 2026 R 101,11 … Unbalanced") |
| A2 (control): Back, then Review Matches again | `fetchTransactions` ×2, `calculateReconciliation` (no create) | **1**, `cd7b2e57` | `42af8abd` (unchanged) | **1** |
| A3: Back, Back, upload corrected `a2` (R102,22), auto-match, Review | … `createReconciliation`, `calculateReconciliation` | **1**, `cd7b2e57` (same id) | `81891f24` (a2) | **1** ("R 102,22") |
| A4: Back, Back, upload `a1` again (same file), auto-match, Review | … `createReconciliation`, `calculateReconciliation` | **1**, `cd7b2e57` (same id) | `e15c7d2e` (a1, 2nd import) | **1** ("R 101,11") |

- Screenshots: `obs-a{1..4}-wizard-review.png` and `obs-a{1..4}-list.png`.
- On main the repro got 3 drafts and 3 list rows from the same sequence.
- The list row's bank balance follows the re-pointed statement.

## b. Abandon and reopen New Reconciliation (period end 17 Nov 2026): PASS

| Step | Drafts for 17 Nov | `bank_statement_id` |
|---|---|---|
| B1: upload `b1`, auto-match, Review Matches, then abandon by navigating to `/new` | **1**, `292c85cd` | `cd2f3b05` (b1) |
| B2: reopened wizard, same account and 17 Nov, upload `b2`, auto-match, Review | **1**, `292c85cd` (same id; `createReconciliation` 200) | `547bc54e` (b2) |

- List: **1** row "17 Nov 2026 R 202,22 … Unbalanced" (`obs-b-list.png`). Screenshots: `obs-b1-wizard-review.png`, `obs-b2-wizard-review.png`.
- On main the repro got 2 drafts.

## c. Refusal for a completed period (period end 18 Nov 2026): PASS

**Completing a reconciliation through the UI.** Run 1 (`M362I`) created the 18 Nov draft `4a34b829` from `c1`. Its figures were cashbook 12 161,34, outstanding deposits 15 961,34 and outstanding payments 1 250,00. The script then re-uploaded `c2-try1` with closing balance −2 550,00 (= cashbook − OD + OP). The same draft was re-pointed and showed difference 0, balanced. Run 1 stopped there: the line's Exclude button only appears once the line is selected, and the script did not select it. That was a script bug, not a product one (`obs-run1-c1-complete-disabled-line-unexcluded.png`; Complete correctly disabled because of `UNRESOLVED_STATEMENT_LINES`).

Run 2 (`M4OG6`, `PHASES=cd`, `obs-result-run2.json`):
- Reopened the wizard for 18 Nov and uploaded `c1`. `createReconciliation` returned the **same** draft `4a34b829`, re-pointed to `3bfca330` (this repeats check b on a third period end).
- Re-uploaded `c2-try1` (−2 550,00). Same draft again, re-pointed to `f09ea647`: bank −2 550,00, adjusted 12 161,34 = cashbook 12 161,34 = creditors 12 161,34, difference 0.
- Selected the line and clicked Exclude (`excludeLine` 200, `calculateReconciliation` 200). Complete became enabled (`obs-run2-c1-ready-to-complete.png`).
- Clicked **Complete Reconciliation** (`completeReconciliation` 200). Step 5 shows "Reconciliation Complete … Bank Balance -R 2 550,00 · Cashbook Balance R 12 161,34 · Trust creditors R 12 161,34 · No monthly list was locked: 18 Nov 2026 is not the last day of a month." (`obs-run2-c2-step5-completed.png`). DB: `4a34b829` COMPLETED, completed_at 13:55:44.23Z.

**Refusal.** Reopened New Reconciliation with the same account and 18 Nov, uploaded `c3`, auto-matched, clicked Review Matches:
- The banner heading (`reconciliation-error-title`) reads **"This period is already reconciled"**. The body reads "This trust account was reconciled to 18 November 2026. A completed reconciliation cannot be changed; choose a later period end." (`obs-run2-c3-refused-banner.png`).
- The wizard stays on step 3 and the review pane is not shown.
- DB count for 18 Nov is **1 before and 1 after** (`4a34b829` still COMPLETED, `bank_statement_id` still `f09ea647`).
- No audit row was written. The list shows one 18 Nov row, "Balanced", completed 19 Nov 2026 (`obs-run2-c-list.png`).

**The 409 on the wire.** The backend logs nothing for a 4xx at its e2e log level: there are 9 log lines during the whole OBS, all INFO scheduler lines (`backend-during-obs.log`). So the backend log cannot show the 409. Instead the refusal was observed a third time (`obs-refusal.cjs`, run `M6GXJ`, `obs-refusal-result.json`, `obs-refusal-banner.png`), with a tcpdump sidecar in the `e2e-backend` network namespace (`obs-refusal-tcpdump.txt`, Authorization redacted):

```
POST /api/trust-accounts/5149d2be-…/reconciliations  {"periodEnd":"2026-11-18","bankStatementId":"0f687ed5-…"}
HTTP/1.1 409   Content-Type: application/problem+json
{"detail":"This trust account was reconciled to 18 November 2026. A completed reconciliation cannot be changed; choose a later period end.", …,"status":409,"title":"This period is already reconciled"}
```

The banner showed the same title and body. DB count for 18 Nov stayed 1 and no audit rows were written after the click.

## d. Audit: PASS for re-points; one sub-point not observable through the UI

`audit_events` for 16, 17 and 18 Nov (`psql-end.txt`; `obs-result-run{1,2}.json` → `d`):

| Draft | `created` | `statement_changed` (previous → new) |
|---|---|---|
| 16 Nov `cd7b2e57` | 1 (→ 42af8abd) | 2: 42af8abd→81891f24 (A3), 81891f24→e15c7d2e (A4) |
| 17 Nov `292c85cd` | 1 (→ cd2f3b05) | 1: cd2f3b05→547bc54e (B2) |
| 18 Nov `4a34b829` | 1 (→ 62af0c0a) | 3: 62af0c0a→574d41db, 574d41db→3bfca330, 3bfca330→f09ea647; then 1 `completed` |

- There is one `statement_changed` per re-point, carrying `previous_bank_statement_id`, and one `created` per draft. The repro got one `created` per upload.
- The A2 control (Review Matches again with the same statement) wrote no `statement_changed` row. Only `trust_reconciliation.calculated` appears between 13:54:30 and 13:54:35.
- The refusals wrote no audit rows.
- **Not observable through the UI:** "no `statement_changed` when create is called again with the *same* `bank_statement_id`". The wizard never sends that. Every upload imports a new statement, even the same file (A4 got a new id, e15c7d2e ≠ 42af8abd, and is correctly audited as a change). The A2 path reuses the draft in wizard state without calling create. That backend branch is covered only by `TrustReconciliationDraftIntegrationTest`, not by this OBS.

## Console, page and backend errors

- Page errors: 0. Console errors: 0 (run 1, run 2 and the refusal run).
- Server-action responses: all 200. The one entry with no recorded status is an early `fetchOnboardingProgress` request on the dashboard redirect; the refusal run recorded it as 200.
- Backend ERROR lines during the OBS: 0. The whole e2e-backend container log since the rebuild also has 0 ERROR lines.

## Notes (not 652C defects)

1. **The 409 is not logged by the backend** at the e2e log level, so the "backend log shows the 409" evidence comes from the wire capture above.
2. **Orphan statements remain after a refusal or an abandoned upload.** The refused attempts' statements `c3-M4OG6` and `c4-M6GXJ`, and every superseded statement, stay imported and unlinked. This is pre-existing behaviour also seen in the repro, outside 652C's scope.
3. The `obs-refusal.cjs` attempt to capture the server-action response body came back empty (Playwright `response.text()` on the RSC stream). The banner and the wire capture cover the same content.

## Residue on the shared e2e stack

- New DRAFTs: 16 Nov `cd7b2e57` and 17 Nov `292c85cd`.
- New **COMPLETED** reconciliation: 18 Nov `4a34b829`. A later period end must be after 18 Nov, or the covering rule applies.
- 11 new one-line statements: `*-M362I.csv`, `*-M4OG6.csv` and `c4-M6GXJ.csv`, dated 16–18 Nov.

## Files

`obs.cjs`, `obs-refusal.cjs`, `obs-result-run1.json`, `obs-result-run2.json`, `obs-refusal-result.json`, `obs-requests-run1.log`, `obs-requests-run2.log`, `obs-refusal-tcpdump.txt`, `psql-before.txt`, `psql-after.txt`, `psql-end.txt`, `backend-flyway-v29.log`, `backend-during-obs.log`, `frontend-during-obs.log` (empty), `chunk-grep.txt`, `rebuild.log`, the screenshots `obs-*.png` and the CSVs `*-M362I.csv`, `*-M4OG6.csv` and `c4-M6GXJ.csv`.
