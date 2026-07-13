# Day 61 — Sipho downloads Statement of Account from portal `[PORTAL]` — cycle 2026-07-12 (executed 2026-07-13)

**Actor**: Sipho Dlamini (portal :3002 — session honoured the email deep link without re-auth).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 61.1 | PASS — **improvement vs prior cycle** | This cycle a real **SoA "Document ready" email exists** (LZKC-015 fixed): Mailpit `9F7ec4bYThN66Rfpode8EH` → link `http://localhost:3002/projects/66451e87…` → landed authenticated on portal matter detail "Dlamini v Road Accident Fund · CLOSED". **LZKC-022 click-through leg PASS** (portal deep link correct + functional) |
| 61.2 | PASS | Documents section lists `statement-of-account-dlamini-v-road-accident-fund-2026-07-13.pdf · 5.5 KB · 13 Jul 2026` (plus closure letter 2.2 KB, 3 FICA PDFs, 2 medical-evidence PDFs) |
| 61.3 | PASS | Download opens presigned S3 URL (LocalStack `/docteams-dev/org/tenant_5039f2d497cf/generated/…`, X-Amz sig, 3600s) → valid `PDF document, version 1.6`, 5.6 KB on disk |
| 61.4 | PARTIAL → **LZKC-030** | Content: firm heading + **letterhead logo renders** (navy block = the actual uploaded firm logo asset), ref SOA-66451e87-20260713, period 12–13 July 2026, To Sipho Dlamini + address, Matter + File reference RAF-2026-001, fees table (2 entries 2,5h + 1,5h @ R0), disbursement SHERIFF_FEES **R1 250,00**, deposits **DEP/2026/001 R50 000,00 + DEP/2026/003 R20 000,00**, payment **PAY/2026/001 R70 000,00**, **Closing balance R0,00**, summary payments received R1 250,00 / closing owing R0,00 — **ZAR locale now uniform throughout (LZKC-017 pt1 HOLDS)**. **Defect**: "Opening balance: **R50 000,00**" while DEP/2026/001 (transaction_date = period start 12 Jul) is ALSO itemised in Deposits → printed figures don't self-reconcile (50 000 + 70 000 − 70 000 = 50 000 ≠ printed closing R0,00). DB truth: opening before 12 Jul = R0. Boundary bug: opening-balance window includes period-start-day transactions that the activity tables also itemise. Known-deferred re-observed, NOT re-filed: VAT Reg blank, "Payment Instructions" heading with empty body (017 pt2), INV-0001 not itemised by reference (017 family disposition) |
| 61.5 | PASS | 📸 `day-61-portal-soa-download.png` |
| 61.6 | PASS | Firm-side Work > Documents lists 5.5 KB; portal lists 5.5 KB; disk 5.6 KB — same single S3 `generated/` object, within rounding |
| 61.7 | PASS | Portal file name identical to firm-side entry; no "Untitled document" |
| 61.8 | PASS — **LZKC-018 fix HOLDS** | Closure letter downloads + renders; all previously-blank variables populated: **Date: 2026-07-13 · Reason for closure: Matter concluded (+ closure notes) · Total fees billed: 1250.00 · Total disbursements: 1250.00 · Duration (months): 0** (opened 12 Jul, closed 13 Jul — correct). Observation (new observable surface, LZKC-017 family): the summary amounts print plain "1250.00", not ZAR "R1 250,00" — cosmetic locale inconsistency vs the SoA |
| 61.9 | PASS — **LZKC-019 fix HOLDS** | Firm session (Thandi, standing) → matter Activity: "**Sipho Dlamini downloaded document \"matter-closure-letter-….pdf\"** — just now" + "**…\"statement-of-account-….pdf\"** — 3 minutes ago" (actor = portal contact, timestamps match Day-61 downloads). Generation events also friendly: "Thandi Mathebula generated a statement of account …", "…generated document … from template \"Matter Closure Letter\"", "…closed the matter". No raw event keys, no "performed" |

## Day-level checkpoints

- SoA downloads cleanly end-to-end: **PASS**
- Contents reconcile to firm-side Section 86 ledger + Day 60 closure state: **PARTIAL** — deposits/payment/closing figures exact (50k + 20k in, 70k out, closing R0,00 = ledger truth), but the printed **opening balance is wrong/self-inconsistent** (LZKC-030)
- Firm-side audit event for portal doc access: **PASS**

## Fix re-verifications

| Fix | Status |
|-----|--------|
| LZKC-017 pt1 (SoA letterhead logo + uniform ZAR locale) | **HOLDS** |
| LZKC-018 (closure-letter variables populated) | **HOLDS** |
| LZKC-019 (friendly activity copy for portal/document events) | **HOLDS** |
| LZKC-022 (email deep links org-scoped / portal-correct, click-through) | **HOLDS** — SoA doc-ready email portal link clicked through to authenticated matter detail |

## New gaps

- **LZKC-030 (Medium)** — Statement of Account trust-activity section double-counts period-start-day transactions: opening balance is computed **inclusive** of transactions dated on the period start date (prints "Opening balance: R50 000,00" — DEP/2026/001, 12 Jul) while the same transaction is itemised in the Deposits table, so the client-facing Section 86 reconciliation document fails self-consistency (opening + deposits − payments = R50 000 ≠ printed closing R0,00). DB-verified true opening = R0. Prior cycle didn't expose this because its first deposit fell after the period start date; any matter whose first trust transaction shares the period start date will print a non-reconciling SoA. Artefact: `statement-of-account-dlamini-v-road-accident-fund-2026-07-13.pdf` (SOA-66451e87-20260713).

## Console

0 application errors (only `localhost:8080/favicon.ico` 401, off-app origin, cosmetic).
