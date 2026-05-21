# Day 46 Checkpoint Results — Portal: Sipho responds to second info request + trust re-check + isolation spot-check

**Date**: 2026-05-21
**Agent**: QA
**Stack**: Keycloak dev stack (portal :3002, backend :8080, gateway :8443)
**Actor**: Sipho Dlamini (portal contact, sipho.portal@example.com)
**POV**: [PORTAL]

## Authentication

- Magic-link extracted from Mailpit email ID `BNuz3FpeYP8zSNyTWwhLRn` (Subject: "Information request REQ-0003 from Mathebula & Partners")
- Token exchange at `/auth/exchange?token=...&orgId=mathebula-partners` succeeded
- Redirected to `/projects` — Sipho Dlamini identity confirmed in top-right header

## Checkpoint Results

### 46.1 — Login via magic-link for second info request
**PASS** — Magic-link from REQ-0003 email exchanged successfully. Sipho authenticated on portal :3002. No Keycloak form appeared.

### 46.2 — `/home` shows "Supporting medical evidence" as pending
**PASS** — `/home` displayed "Pending info requests: 1". Clicked into Requests page: REQ-0003 ("Dlamini v Road Accident Fund", SENT, 0/2 submitted) visible alongside completed REQ-0001 (COMPLETED, 3/3 accepted). Clicked into REQ-0003 detail: 2 items displayed — "Hospital discharge summary" (required) and "Orthopaedic report" (required), each with file input + "Upload and submit" button. Status header: "0/2 submitted - status SENT".

### 46.3 — Upload 2 test PDFs and submit
**PASS** — Both documents uploaded and submitted via per-item "Upload and submit" buttons:
1. Hospital discharge summary: file set via JS DataTransfer API -> "Upload and submit" clicked -> status transitioned to "Submitted - status: SUBMITTED" (green). Header updated to "1/2 submitted - status IN_PROGRESS".
2. Orthopaedic report: same process -> "Submitted - status: SUBMITTED" (green). Header updated to "2/2 submitted - status IN_PROGRESS".

Envelope state machine: SENT -> IN_PROGRESS (2/2 submitted, awaiting firm-side accept). Consistent with Day 4/5 FICA pattern.

### 46.4 — Trust balance shows R 70,000
**PASS** — `/trust` displays trust balance card: **R 70,000.00** as of 21 May 2026, Matter 85b09bb3 (RAF-2026-001).

Note: Scenario text referenced R 71,000 (including a R 1,000 carry-over from a prior cycle's OBS-1101). In this run, only 2 deposits exist (R 50,000 Day 10 + R 20,000 Day 45 = R 70,000). Day 45 checkpoint already reconciled to R 70,000. No discrepancy — the R 1,000 carry-over deposit does not exist in this cycle's data.

### 46.5 — Transaction list shows deposits with correct ordering
**PASS** — Transactions table displays 2 deposits, ordered descending by running balance (newest first):

| Date | Type | Description | Amount | Running balance |
|------|------|-------------|--------|-----------------|
| 21 May 2026 | DEPOSIT | Top-up per engagement letter | R 20,000.00 | R 70,000.00 |
| 21 May 2026 | DEPOSIT | Initial trust deposit — RAF-2026-001 | R 50,000.00 | R 50,000.00 |

Dates correct (single-day E2E run, all 21 May 2026). Amounts correct. Running balances correct. ZAR currency (R) throughout.

### 46.6 — Passive isolation spot-check
**PASS** — Isolation verified across multiple portal pages:
- `/trust`: Balance R 70,000.00 (Sipho only, NOT R 95,000 aggregate). Only Sipho's matter (85b09bb3) displayed. No Moroka deposit (R 25,000) visible.
- `/projects`: Only Sipho's matters shown ("Engagement Letter — Litigation (Dlamini v RAF)" + "Dlamini v Road Accident Fund"). No Moroka Family Trust / Estate Late Peter Moroka.
- `/invoices` (Fee Notes): Only INV-0001 (PAID, R 1,250.00). No Moroka fee notes.
- `/home`: Only Sipho's data in all cards (pending requests, deadlines, fee notes, trust movement).

### 46.7 — `/home` pending info requests cleared
**PASS** — After both items submitted, `/home` shows "Pending info requests: 0". The medical evidence request no longer appears as pending from the portal contact's perspective.

### 46.8 — Optional screenshot
Not captured (no new visual regression concern).

## Console Errors
**Zero** — No JavaScript errors detected across `/home`, `/requests/{id}`, `/trust`, `/projects`, `/invoices` pages.

## Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 46.1 Magic-link login | PASS | Token exchange, no KC form |
| 46.2 Pending request visible | PASS | REQ-0003, 2 items, SENT |
| 46.3 Upload + submit 2 PDFs | PASS | 2/2 submitted, IN_PROGRESS |
| 46.4 Trust balance R 70,000 | PASS | Matches Day 45 reconciliation |
| 46.5 Transaction list correct | PASS | 2 deposits, correct order/amounts |
| 46.6 Isolation spot-check | PASS | No Moroka data on any page |
| 46.7 Pending cleared on /home | PASS | 0 pending after submission |
| Console errors | PASS | Zero errors |

**Overall: PASS — 7/7 checkpoints pass, 0 new gaps, 0 console errors.**
