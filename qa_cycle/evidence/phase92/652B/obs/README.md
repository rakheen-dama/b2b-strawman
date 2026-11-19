# 652B OBS gate — PR #1816, head `82f5490d6` — ALL FOUR CHECKS PASS (observed)

- **Date:** 2026-11-19 ~15:00 SAST. **Stack:** e2e mock-auth (:3001 / :8081), `e2e-frontend` container created 2026-11-19T12:52:59Z (image `sha256:4ebb281c…`, BUILD_ID `UdMqABpR5Hk9onZ_IhE1g`), built from worktree `worktree-epic-652B` at `82f5490d6`.
- **Script:** `obs.cjs` (adapted from `../repro-browser.cjs`; Playwright library, headless Chromium, Alice/owner via `/mock-login`). Run from the worktree's `frontend/`: `NODE_OPTIONS="" node ../qa_cycle/evidence/phase92/652B/obs/obs.cjs`. Run id **`K0NIA`**. Raw output incl. every psql query and its output: `obs-result.json`. Statement: `obs-K0NIA.csv`.
- Every state change through the UI (deposit dialog, New Client dialog + Start Onboarding, New Matter dialog, Record Transfer dialog, wizard upload / auto-match / Match). psql against `e2e-postgres`, tenant `tenant_7d218705360b`, read-only. No page errors, no console errors (`pageErrors: []`, `consoleErrors: []`).

## Step 0 — stack serves the head

`main`'s page has none of these strings (`git show main:'frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx' | grep -c -E 'UNIDENT_ALLOC|TRANSFER_OUT|RECORDED'` → `0`). In the container:

```
$ docker exec e2e-frontend sh -c 'grep -rl UNIDENT_ALLOC_OUT /app/frontend/.next | xargs grep -l TRANSFER_OUT'
/app/frontend/.next/server/chunks/ssr/1oju_app_(app)_org_[slug]_trust-accounting_reconciliation_new_page_tsx_0gjhtfk._.js
/app/frontend/.next/static/chunks/0uf349ancctem.js
```

Client chunk `0uf349ancctem.js` (the new `fetchUnmatchedTransactions`, minified):

```
let ec=["RECORDED","APPROVED"],ed=["TRANSFER_OUT","TRANSFER_IN","UNIDENT_ALLOC_OUT","UNIDENT_ALLOC_IN"];async function eo(e){return(await Promise.all(ec.map(t=>es(e,{status:t,page:0,size:1e3})))).flatMap(e=>e.content).filter(e=>!e.bankStatementLineId&&!ed.includes(e.transactionType
```

The SSR chunk carries the same code (`ai=["RECORDED","APPROVED"],aj=["TRANSFER_OUT",…]`). **Head served.**

## Setup (UI)

| What | UI | DB (read-only) |
|---|---|---|
| Deposit Acme Corp / Website Redesign, R305,86, `DEP/652B-OBS/K0NIA`, 17 Nov | row "17 Nov 2026 DEP/652B-OBS/K0NIA Deposit R 305,86 **RECORDED**" (`obs-1-deposit-recorded.png`) | `1e030559-6d17-49ad-a994-44bf1c42dd59 \| DEPOSIT \| RECORDED \| 305.86 \| 2026-11-17 \| bank_statement_line_id NULL` |
| Client `OBS652B Client K0NIA` (Individual) → Start Onboarding → matter `OBS652B Matter K0NIA` | — | `f2352f59-… \| ONBOARDING`; matter `4036236e-… customer_id f2352f59-…` |
| Record Transfer Acme → OBS652B client, R407,48, `TRF/652B-OBS/K0NIA`, 18 Nov | dialog `obs-2a-transfer-dialog.png`; rows "Transfer out R 407,48 RECORDED" + "Transfer in R 407,48 RECORDED" (`obs-2b-transfer-recorded.png`) | `e9022b9d-… \| TRANSFER_OUT \| RECORDED \| 407.48 \| NULL`; `0333393d-… \| TRANSFER_IN \| RECORDED \| 407.48 \| NULL` |

Statement `obs-K0NIA.csv` (primary account, period end 19 Nov): `-1250.00` 17 Nov (/P), `+305.86` 19 Nov (/D), `+407.48` 19 Nov (/TI), `-407.48` 19 Nov (/TO). Auto-match: **"Auto-matched 0 lines / 4 lines remaining for manual review"** — all four lines `UNMATCHED` in DB (the /D and /P lines carry a 0.60 close-date suggestion; /TI and /TO none — the backend's sign check rejects transfer legs).

Pane "Unmatched Trust Transactions" listed 8 rows (DB-resolved): DEPOSIT/RECORDED ×6 (incl. `DEP/652B-OBS/K0NIA`, `DEP/652B/IXVHO`), PAYMENT/APPROVED ×2 (`PAY/652A/GT8HE`, `PAY/652A/GUR32`). DB's unlinked posted rows on the account: `DEPOSIT|RECORDED|6`, `PAYMENT|APPROVED|2`, `TRANSFER_IN|RECORDED|1`, `TRANSFER_OUT|RECORDED|1` — i.e. the pane = every unlinked posted row except the two transfer legs. Newest-first order observed.

## Checks

| | Result | Observed | Screenshot |
|---|---|---|---|
| **(a)** recorded deposit offered | **PASS** | Line #2 (R305,86 CR, Unmatched) selected → pane row "DEP/652B-OBS/K0NIA 17 Nov 2026 · Deposit … R 305,86 **Match**" (`match-btn-1e030559…` count 1). Deposit is RECORDED in DB. | `obs-3-recorded-deposit-offered.png` |
| **(b)** manual match succeeds | **PASS** | Click Match → line #2 shows **Matched** + Unmatch; deposit gone from the pane; "1 / 4 lines resolved"; no error banner, no stale-figures banner; outstanding deposits R20 961,34 → R20 655,48 (−305,86). | `obs-4-deposit-matched.png` |
| **(c)** transfer legs not offered | **PASS** | Neither `TRANSFER_OUT e9022b9d…` nor `TRANSFER_IN 0333393d…` listed; selecting line #3 (+407,48) and line #4 (−407,48): zero Match buttons at all (no other R407,48 candidate exists). | `obs-5-transfer-line-selected.png` (line #3 selected) |
| **(d)** approved payment still offered | **PASS** | Both APPROVED unlinked payments listed; selecting line #1 (R1 250,00 DR) offers Match on `PAY/652A/GT8HE` and `PAY/652A/GUR32` (not clicked). | `obs-6-approved-payment-offered.png` |

### psql (read-only), after the run

```
select id, transaction_type, status, amount, reference, transaction_date, bank_statement_line_id
  from tenant_7d218705360b.trust_transactions
 where reference in ('DEP/652B-OBS/K0NIA','TRF/652B-OBS/K0NIA')
    or id in ('cef85e96-df3f-48af-b1b6-f0aa3a76fe22','82ae7ae5-0dc7-4e81-b0ba-85cfc250c926')
 order by reference, transaction_type;

 1e030559-6d17-49ad-a994-44bf1c42dd59 | DEPOSIT      | RECORDED | 305.86  | DEP/652B-OBS/K0NIA | 2026-11-17 | 71d1e6d1-ddaf-450b-ba40-b535cb88fd43
 cef85e96-df3f-48af-b1b6-f0aa3a76fe22 | PAYMENT      | APPROVED | 1250.00 | PAY/652A/GT8HE     | 2026-11-19 |
 82ae7ae5-0dc7-4e81-b0ba-85cfc250c926 | PAYMENT      | APPROVED | 1250.00 | PAY/652A/GUR32     | 2026-11-13 |
 0333393d-a55e-4ef3-ab00-76f0684ecb29 | TRANSFER_IN  | RECORDED | 407.48  | TRF/652B-OBS/K0NIA | 2026-11-18 |
 e9022b9d-818f-4613-a6a9-991fd9e02fe9 | TRANSFER_OUT | RECORDED | 407.48  | TRF/652B-OBS/K0NIA | 2026-11-18 |

select id, amount, reference, match_status, trust_transaction_id, match_confidence
  from tenant_7d218705360b.bank_statement_lines
 where bank_statement_id='020df165-0614-40bc-a46f-de758baf28c6' order by reference;

 71d1e6d1-ddaf-450b-ba40-b535cb88fd43 |   305.86 | BANK/652B-OBS/K0NIA/D  | MANUALLY_MATCHED | 1e030559-6d17-49ad-a994-44bf1c42dd59 | 1.00
 72a1ca01-3d63-49aa-8d19-0e9a54060bc9 | -1250.00 | BANK/652B-OBS/K0NIA/P  | UNMATCHED        | cef85e96-df3f-48af-b1b6-f0aa3a76fe22 | 0.60
 0a863ed0-95eb-4734-a262-73b831876a5b |   407.48 | BANK/652B-OBS/K0NIA/TI | UNMATCHED        |                                      |
 7207e3ca-52be-4d52-8a62-bd4bd99c79ec |  -407.48 | BANK/652B-OBS/K0NIA/TO | UNMATCHED        |                                      |

select id, status, period_end, bank_statement_id from tenant_7d218705360b.trust_reconciliations
 where bank_statement_id='020df165-0614-40bc-a46f-de758baf28c6';
 258446a3-3f94-4d43-b4a4-dfef85d45295 | DRAFT | 2026-11-19 | 020df165-0614-40bc-a46f-de758baf28c6
```

(b) in DB: the deposit's `bank_statement_line_id` = line `71d1e6d1…`, the line is `MANUALLY_MATCHED` to the deposit. The two payments and both transfer legs stay unlinked.

## Defects / side observations (not fixed)

- No 652B defect seen.
- (Pre-existing, already noted by the scout, not 652B scope) a below-threshold auto-match leaves `UNMATCHED` lines carrying a 0.60 suggestion in `trust_transaction_id` (line /P → `PAY/652A/GT8HE`); the pane shows no "suggested" marker — both R1 250 payments are offered equally.
- The Record Transfer dialog asks for raw client / matter **UUIDs** (free-text inputs "Source Client ID", "Target Matter", …) rather than pickers; the UUIDs typed were read from the DB. Usable but not user-grade — outside 652B.

## Residue on the shared e2e stack

Client `OBS652B Client K0NIA` (ONBOARDING) + matter `OBS652B Matter K0NIA`; deposit `DEP/652B-OBS/K0NIA` (now matched); transfer pair `TRF/652B-OBS/K0NIA` (R407,48 Acme → OBS652B client); statement `obs-K0NIA.csv` (4 lines, 1 matched) and DRAFT reconciliation `258446a3…` (period end 19 Nov) on the primary account.
