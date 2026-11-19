# 652C — browser reproduction (task 652.7, browser half): REPRODUCED

- **Date:** 2026-11-19. **Stack:** e2e mock-auth stack on :3001 / :8081 as already running (built from `main`; not rebuilt, restarted or reseeded). **`main`:** `30ad1643b` (backend unchanged since the stack build).
- **Script:** `repro-browser.cjs` (Playwright library, headless Chromium, `@playwright/test` resolved from the main checkout's `frontend/node_modules`; run from the worktree root: `NODE_OPTIONS="" node qa_cycle/evidence/phase92/652C/repro-browser.cjs`). **Raw result:** `repro-browser-result.json`. **Request log:** `repro-browser-requests.log`. Run id `KTQTW`.
- **User:** Alice (owner) via `/mock-login`. Every state change through the wizard UI; psql (:5433, tenant `tenant_7d218705360b`, from `public.org_schema_mapping`) is read-only SELECT evidence. No SQL writes, no direct API calls.
- **Account:** the primary trust account `5149d2be-b51f-4a7f-b375-f2a851a4a028` (the list page shows the primary account only, so a fresh non-primary account would not be countable on the list). Period ends **15 Nov 2026** (path A) and **14 Nov 2026** (path B) — no reconciliation used either before the run, and no other statement of the account has a line on or after 14 Nov before 16 Nov, so the 646C period rules pass.
- **Statements:** one-line CSVs (`STANDARD BANK` header) dated on the period end: `s1-KTQTW.csv` (R659,16), `s2-KTQTW.csv` (R660,16, the "corrected" re-upload), `s3-KTQTW.csv` (R661,16), `s4-KTQTW.csv` (R662,16).

## Before (DB)

```
-- trust_account_id, period_end, status, count(*) ... HAVING count(*)>1
5149d2be-…-f2a851a4a028|2026-11-19|DRAFT|3        (left by earlier 652A/652B runs)
-- rows for 2026-11-15: none        rows for 2026-11-14: none
```

## Path A — one wizard session, re-upload through the wizard's own Back controls (period end 15 Nov)

| Step | UI | Drafts for 15 Nov (DB) |
|---|---|---|
| A1 | select account + 15 Nov → upload `s1` → Run Auto-Match → Review Matches (`repro-browser-1-A-review-first.png`) | **1** (`6b45b608…`) |
| A2 (control) | step 4 **Back** → step 3 → Review Matches again, same statement | **1** — reused, no `createReconciliation` call |
| A3 | step 4 **Back** → step 3 **Back** (`auto-match-back-btn`) → step 2, upload corrected `s2` → auto-match → Review Matches (`repro-browser-2-A-review-after-reupload.png`) | **2** (`+19614671…`) |
| A4 | Back → Back → upload `s1` again (the same file) → auto-match → Review Matches | **3** (`+3d168e26…`) |

**List page after path A** (`repro-browser-3-list-after-path-A.png`): "9 reconciliations found"; **three rows "15 Nov 2026"**, all Unbalanced / not completed — bank balance R659,16, R660,16, R659,16 (one per upload). All three `reconciliation-row-<id>` elements matched the three DB ids.

## Path B — abandon and reopen "New reconciliation" (period end 14 Nov)

| Step | UI | Drafts for 14 Nov (DB) |
|---|---|---|
| B1 | select account + 14 Nov → upload `s3` → abandon at step 2 (navigate to `/new` again) | **0** (no draft is created before Review Matches; the statement is imported though) |
| B2 | reopened wizard, same account + 14 Nov → upload `s3` again → auto-match → Review Matches; abandon at step 4 | **1** (`f42ce541…`) |
| B3 | reopened wizard, same account + 14 Nov → upload `s4` → auto-match → Review Matches | **2** (`+93325cd7…`) |

**List page after path B** (`repro-browser-4-list-after-path-B.png`): "11 reconciliations found"; **two rows "14 Nov 2026"** (R661,16 and R662,16).

## After (DB)

```
-- trust_account_id, period_end, status, count(*) ... HAVING count(*)>1
5149d2be-…|2026-11-14|DRAFT|2
5149d2be-…|2026-11-15|DRAFT|3
5149d2be-…|2026-11-19|DRAFT|3
-- 15 Nov: 6b45b608 DRAFT s1-KTQTW.csv 13:19:09 | 19614671 DRAFT s2-KTQTW.csv 13:19:12 | 3d168e26 DRAFT s1-KTQTW.csv 13:19:14
-- 14 Nov: f42ce541 DRAFT s3-KTQTW.csv 13:19:17 | 93325cd7 DRAFT s4-KTQTW.csv 13:19:19
```

Audit `trust_reconciliation.created`: five events, one per draft above, each with a different `bank_statement_id`.
Bank statements imported by the run: **six** (`s1` ×2, `s2`, `s3` ×2, `s4`) — every upload imports a new statement, including the same file twice; nothing is reused or refused.

## Request sequence (server actions, from `page.on('request')`; names from the e2e frontend's `server-reference-manifest.json`)

- Upload: `uploadBankStatement`. Auto-match: `autoMatch` → `fetchBankStatement`.
- Review Matches with no draft in wizard state: `fetchTransactions` ×2 → **`createReconciliation`** → `calculateReconciliation`.
- Review Matches after step-4 Back only (A2): `fetchTransactions` ×2 → `calculateReconciliation` (no create — the draft in state is reused).
- A3/A4/B2/B3 each show a fresh `createReconciliation`: the upload's `handleUploadComplete` → `resetFromStep3()` clears `reconciliation`, and a remounted wizard starts with none, so `handleStartReview` creates another; the backend `createReconciliation` (`TrustReconciliationService.java:675`) always inserts and never looks up an existing DRAFT for the account + period end.
- All 51 server-action responses were 200; no page errors, no console errors.

## Verdict

**REPRODUCED.** One account + one period end ends with **3 DRAFT reconciliations** (path A: in-wizard Back → re-upload, corrected file and same file) and **2 DRAFT reconciliations** (path B: abandon at the review step, reopen New Reconciliation, upload again), each listed on the reconciliation page as a separate "Unbalanced" row. Abandoning at step 2 (before Review Matches) creates no draft but still leaves an imported statement behind.

## Residue on the shared e2e stack

Five more DRAFT reconciliations on the primary account (15 Nov ×3, 14 Nov ×2) and six more imported one-line statements (`*-KTQTW.csv`, dated 14/15 Nov). Note for later runs: an account statement now has lines on 14 and 15 Nov, so a period end on 14 Nov with a statement ending before it is refused by the 646C covering rule.
