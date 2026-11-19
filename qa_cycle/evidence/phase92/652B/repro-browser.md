# 652B — browser reproduction (task 652.4): REPRODUCED

- **Date:** 2026-11-19. **Stack:** e2e mock-auth stack on :3001 / :8081, serving `main`'s wizard (`fetchUnmatchedTransactions` asks `status: "APPROVED"` only — unchanged since before 652A). **`main`:** `6f55d5e2fbe9d538cb5dc0a791fbe74e60e09712`.
- **Script:** `repro-browser.cjs` (Playwright library, headless Chromium, run from the worktree's `frontend/`: `NODE_OPTIONS="" node ../qa_cycle/evidence/phase92/652B/repro-browser.cjs`). **Raw result:** `repro-browser-result.json`. **Statement:** `s1-IXVHO.csv`. Run id `IXVHO`.
- **User:** Alice (owner) via `/mock-login`. Every state change through the UI; psql (:5433, tenant `tenant_7d218705360b`) is read-only evidence. No SQL writes, no API calls.

## Steps

1. **Trust → Transactions → Record Transaction → Record Deposit**: client Acme Corp, matter Website Redesign, R655,48, reference `DEP/652B/IXVHO`, dated 17 Nov 2026. The row shows **RECORDED** (`repro-browser-1-deposit-recorded.png`). DB: `dbe61796-cca9-478a-9c9a-d3e6eef05b9c | RECORDED | 655.48 | 2026-11-17 | bank_statement_line_id NULL`. Deposits never wait for approval (`TrustTransactionService.java:395` creates them `RECORDED`).
2. **Reconciliation wizard**, primary account, period end **19 Nov 2026**. Upload `s1-IXVHO.csv`: one credit line, 19 Nov, R655,48, reference `BANK/652B/IXVHO`. Auto-match: "Auto-matched 0 lines / 1 lines remaining for manual review" (amount equal, date two days off, reference different → the 0.60 "close date" strategy, below threshold; the line stays `UNMATCHED`).
3. **Review & Match** (`repro-browser-2-review-pane.png`), line #1 selected.

## Observed

| | |
|---|---|
| "Unmatched Trust Transactions" pane | **two rows only**, both `APPROVED` payments: `PAY/652A/GT8HE` R1 250,00 and `PAY/652A/GUR32` R1 250,00 |
| The new recorded deposit `DEP/652B/IXVHO` | **absent** from the pane; selecting the R655,48 line offers **no Match button** (`match-btn-<depositId>` count 0) — the line can only be recorded from the statement or excluded |
| DB, the account's unlinked posted rows (read-only) | `APPROVED PAYMENT ×2`, `RECORDED DEPOSIT ×5` — all five recorded deposits are absent from the pane |
| Summary, same screen | "+ Outstanding deposits **R 20 655,48**" — the backend's calculation counts the five unlinked recorded deposits as outstanding, while the pane gives no way to match any of them |
| Console / page errors | none |

**Defect confirmed:** on `main` a recorded (posted) deposit is never offered for manual matching, although the backend accepts `RECORDED` for a manual match (`TrustReconciliationService.java:446-450`). Since every deposit lands `RECORDED`, no deposit can be matched by hand in the wizard.

## Side observation (not 652B scope)

After auto-match the line is `UNMATCHED` but its DB row carries `trust_transaction_id = dbe61796…` (the below-threshold "close date" candidate stored as a suggestion). The UI shows it as plain Unmatched. Recorded here only; not investigated.

## Residue on the shared e2e stack

One more RECORDED deposit (`DEP/652B/IXVHO`, R655,48, Acme Corp), one more statement (`s1-IXVHO.csv`, one line dated 19 Nov) and one more DRAFT reconciliation (period end 19 Nov) on the primary account.
