# 652C — OBS run 2 (PR #1817): a stale wizard tab cannot calculate or complete

- **Head:** `bc4684518cd5cffae4d5eb19b5538d556ae8c6d5` (branch `epic-652C/recon-draft-get-or-create`, worktree `worktree-epic-652C`). The tree is clean apart from the untracked `obs/` directory.
- **Date:** 2026-11-19, 14:19–14:24 UTC.
- **Stack:** e2e mock-auth (:3001 / :8081), rebuilt from the head (`rebuild.log`, exit 0, 14:19:00–14:20:29Z). Maven `BUILD SUCCESS` and `next build` "Compiled successfully" ran inside the image build. The stack was not wiped. Run 1's state carried over.
- **How it was driven:**
  - User: Alice (owner), signed in through `/mock-login`.
  - Browser: one headless Chromium context with two pages, tab A and tab B, driven by the Playwright library (`obs-run2.cjs`, run id `N423A`). The Playwright MCP was not used, and no Chrome process was touched.
  - Every state change went through the wizard UI. psql was used for read-only SELECTs only. No product code was changed.
- **Tenant / account:** `tenant_7d218705360b`, primary account `5149d2be-…a028`.
- **Period end:** **19 Nov 2026** (today). This continues the V29-surviving DRAFT **D = `258446a3`**. It was the only period end left: it is after the COMPLETED 18 Nov reconciliation and not after today.
- **646C period fit:** every run-2 statement has one line dated 19 Nov, so its last line equals the period end. The covering-statement rule searches lines strictly after the last line and up to the period end, which is an empty range here. That is why each upload was accepted.

## Overall: e PASS, f PASS, g PASS, console/page/backend errors none

## 0. The stack serves the head (`head-proof.txt`)

| Proof | Observed |
|---|---|
| Frontend | Three SSR chunks in `e2e-frontend:/app/frontend/.next/server/chunks/ssr/` contain `expectedBankStatementId`. Examples: ``api.post(`/api/trust-reconciliations/${a}/calculate`,{expectedBankStatementId:b})`` and the same call for `/complete`. |
| Backend | `app.jar` copied out of `e2e-backend` (sha256 `9d26379d…`). `TrustReconciliationService.class` contains `This reconciliation now uses a different statement`, `ExpectedStatementRequest` and `expectedBankStatementId`. `TrustReconciliationRepository.class` contains `findByIdForUpdate`, and `findStatusById` is gone. The controller signature takes `ExpectedStatementRequest`. |

## DB before (`psql-before.txt`, 14:21Z)

- **D `258446a3`:** 19 Nov, DRAFT, on statement `020df165` (`obs-K0NIA.csv`, IMPORTED).
- **Duplicate (account, period end) groups:** 0.
- **`trust_monthly_lists`:** 0 rows.
- **Statements:** 1 RECONCILED (run 1's 18 Nov), 25 IMPORTED.
- **D's audit rows:** 3.

## g. Regression of run 1 (a): re-upload in one session keeps one draft — PASS

- Tab A uploaded `sa0` (R1,11), auto-matched and clicked Review Matches. `createReconciliation` and `calculateReconciliation` both returned 200.
  - DB: **1** row for 19 Nov, the same id `258446a3`, now on `15acfe1b` (sa0).
  - Figures: bank 1,11, OD 20 655,48, OP 2 500,00, cashbook 15 911,34, difference 2 245,25.
- Tab A then went Back, Back, uploaded `sa-try1` (−2 244,14 = cashbook − OD + OP), auto-matched and clicked Review.
  - DB: **1** row, the same id, now on **SA = `b41e11a7`**.
  - Figures: adjusted 15 911,34 = cashbook = creditors, difference **0,00**, balanced.
- Audit: one `statement_changed` per re-point: 020df165→15acfe1b, then 15acfe1b→b41e11a7.
- Screenshots: `g1-tabA-review-sa0.png`, `g2-tabA-review-SA-same-draft.png`.
- A fresh, unused period end was not available: 19 Nov already had D. So D was reused before it was completed, as the brief allowed.

## e. Stale tab refused — PASS

**Set-up.**

1. **Tab A:** selected SA's line and clicked Exclude. `excludeLine` and `calculateReconciliation` both returned 200, and **Complete was enabled** (`e1-tabA-ready-to-complete-on-SA.png`).
2. **Tab B** (same signed-in context): New Reconciliation, same account, 19 Nov. It uploaded `sb` (−2 244,14), auto-matched and clicked Review Matches. `createReconciliation` and `calculateReconciliation` both returned 200.
   - DB: **D is still the only 19 Nov row, and `D.bank_statement_id` is SB = `5303d065`**. updated_at 14:23:18.311858.
   - Audit: `statement_changed` b41e11a7→5303d065 (`e2-tabB-review-SB.png`).
3. The DB state before the stale actions is in `obs-run2-result.json` → `e.before`:
   - D is DRAFT on SB, updated_at 14:23:18.311858.
   - SA and SB are both IMPORTED.
   - `trust_monthly_lists` has 0 rows, and 1 statement is RECONCILED.

**Tab A, Complete Reconciliation** (step 4, button enabled).

- Banner title: **"This reconciliation now uses a different statement"**.
- Banner body: "Since you opened it, this reconciliation was continued with another bank statement. Reload the reconciliation and review the statement it now uses before you continue."
- Step 5 is **not** shown, and tab A stays on step 4 (Review & Match), with `step5Complete=false` and `step4SplitPane=true`. See `e3-tabA-complete-refused-banner.png`.
- Server actions, all returning 200: `completeReconciliation`, then the refusal's follow-up `calculateReconciliation`, `fetchBankStatement` and `fetchTransactions`.

**Tab A, Back → Review Matches** (the calculate the step runs).

- The same banner title and body appear. Tab A **stays on step 3 (Auto-Match)** and the review pane is not shown (`e4-tabA-review-calculate-refused-banner.png`).
- Server actions: `fetchTransactions` twice and `calculateReconciliation`. No create was sent.

**DB after each refusal** (`e.completeRefusal.db` and `e.calculateRefusal.db`).

| | D status | D statement | D updated_at | SA | SB | monthly lists | RECONCILED stmts |
|---|---|---|---|---|---|---|---|
| before | DRAFT | SB 5303d065 | 14:23:18.311858 | IMPORTED | IMPORTED | 0 | 1 |
| after Complete click | DRAFT | SB 5303d065 | 14:23:18.311858 | IMPORTED | IMPORTED | 0 | 1 |
| after Review click | DRAFT | SB 5303d065 | 14:23:18.311858 | IMPORTED | IMPORTED | 0 | 1 |

- D's `updated_at` did not change and its figures are identical.
- **0 audit rows** were written for D from the first stale click onwards (`e.auditDuringStale = []`).

**On the wire** (`e-wire-tcpdump-excerpt.txt`).

- Method: an `alpine:3` + tcpdump sidecar in the `e2e-backend` network namespace, `tcp port 8080`, the same method as run 1.
- The sidecar was started just before the stale clicks and removed afterwards. `docker ps -a` shows no `obs652c*` container.
- `Authorization` and `Cookie` headers are redacted. The full redacted capture is `e-wire-tcpdump-full.txt` (26 KB) and contains 0 bearer tokens.

```
14:23:21.504 POST /api/trust-reconciliations/258446a3-…/complete   {"expectedBankStatementId":"b41e11a7-…"}   (SA)
14:23:21.576 HTTP/1.1 409  application/problem+json
  {"detail":"Since you opened it, this reconciliation was continued with another bank statement. Reload …","instance":"/api/trust-reconciliations/258446a3-…/complete","status":409,"title":"This reconciliation now uses a different statement"}
14:23:21.589 POST /api/trust-reconciliations/258446a3-…/calculate  {"expectedBankStatementId":"b41e11a7-…"}   (complete's follow-up recalculation)
14:23:21.612 HTTP/1.1 409  … same title, instance …/calculate
14:23:23.844 POST /api/trust-reconciliations/258446a3-…/calculate  {"expectedBankStatementId":"b41e11a7-…"}   (Back → Review Matches)
14:23:23.857 HTTP/1.1 409  … same title, instance …/calculate
```

The other backend responses in that window were 200s, from the refusal's re-reads of the statement and the transactions.

## f. Happy path in the current tab — PASS

1. Tab B selected SB's line and clicked Exclude. `excludeLine` and `calculateReconciliation` both returned 200, the figures had difference 0,00, and Complete was enabled (`f1-tabB-ready-to-complete-on-SB.png`).
2. Tab B clicked **Complete Reconciliation**. `completeReconciliation` returned 200.
   - Step 5 reads "Reconciliation Complete … Bank Balance -R 2 244,14 · Cashbook Balance R 15 911,34 · Trust creditors R 15 911,34 · No monthly list was locked: 19 Nov 2026 is not the last day of a month." (`f2-tabB-step5-completed.png`).
3. DB after (`psql-after.txt`):
   - **D `258446a3` is COMPLETED** (completed_at 14:23:29.089Z) on SB `5303d065`, difference 0,00, is_balanced t.
   - **SB is RECONCILED. SA stays IMPORTED**, with its line EXCLUDED.
   - `trust_monthly_lists` has 0 rows, which is correct because 19 Nov is not a month end.
   - There are 0 duplicate groups.
   - Audit: `calculated`, then `completed`.
4. The list page is in `f3-list.png`.

## Console, page and backend errors

- **Browser:** page errors 0 and console errors 0 across both tabs.
- **Server actions:** every response was 200. The backend's 409s are returned to the client as values, by design.
- **Backend:** `backend-since-rebuild.log` (JSON, 97 lines since 14:20Z) has **0 `"level":"ERROR"`**. There are 8 WARN lines, all startup-time Spring `BeanPostProcessorChecker` and `CglibAopProxy` warnings at 14:20:16–21. During the OBS there were only 2 INFO `DefaultJobEnqueuer` lines. As in run 1, the backend logs nothing for a 4xx.
- **Frontend:** `frontend-during-obs.log` is empty.

## Observations (not failures against the stated expectations)

1. **Complete stays enabled after a stale refusal.** After the stale Complete refusal, tab A still shows its SA figures with "Reconciliation is balanced", and the button is still enabled.
   - The cause: the follow-up recalculation is itself refused, so the old SA figures stay on screen.
   - Clicking again would be refused again (409), and nothing can change. The banner tells the user to reload.
   - This is consistent with the stated design (stay on the step, banner). It is flagged only in case a disabled or stale-marked Complete is wanted after this refusal.
2. **Screenshot artefact.** `e3-…png` is a full-page capture of a long page, so the sticky header and sidebar are drawn mid-page. This is a capture artefact, not a layout defect. The banner sits at the top of the page.
3. **Excluding SA's line was a real write.** Tab A's set-up excluded SA's only line before tab B re-pointed D. That line stays EXCLUDED on an orphaned IMPORTED statement. This is the same orphan-statement residue as run 1's note 2, and is pre-existing.

## Residue on the shared e2e stack

- **19 Nov `258446a3` is now COMPLETED.** No reconciliation period end is left for this account on or before today (2026-11-19). The next usable period end is 20 Nov, from tomorrow.
- **3 new one-line statements** (`*-N423A.csv`): `sa0` and `sa-try1` are IMPORTED, and `sb` is RECONCILED.

## Files (this directory)

- **Script and result:** `obs-run2.cjs`, `obs-run2-result.json`, `obs-run2-requests.log`.
- **Database:** `psql-before.txt`, `psql-after.txt`.
- **Wire capture:** `e-wire-tcpdump-excerpt.txt`, `e-wire-tcpdump-full.txt`.
- **Build and logs:** `head-proof.txt`, `rebuild.log`, `backend-since-rebuild.log`, `frontend-during-obs.log` (empty).
- **Screenshots:** `g1…`, `g2…`, `e1…` to `e4…`, `f1…` to `f3…` (`.png`).
- **Uploaded CSVs:** `sa0-N423A.csv`, `sa-try1-N423A.csv`, `sb-N423A.csv`.
