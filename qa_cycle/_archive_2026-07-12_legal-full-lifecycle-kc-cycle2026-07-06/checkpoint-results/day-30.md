# Day 30 — Sipho pays fee note via mock payment gateway `[PORTAL]` — 2026-07-06

**Actor**: Sipho Dlamini (portal :3002). Prior portal session expired → fresh magic-link login (`sipho.portal@example.com` → Send Magic Link → dev-mode exchange link) which honoured `redirectTo` straight onto the fee-note detail.

**Gateway note (0.G)**: The configured payment path is the **mock gateway** (`MockPaymentGateway`, `@IntegrationAdapter(domain=PAYMENT, slug="mock")`, dev-only checkout at `:8080/portal/dev/mock-payment`), not PayFast sandbox — consistent with the Day-61 amendment ("PAID Day 30 via mock payment gateway"). No PayFast credentials in `OrgIntegration`.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 30.1 | PASS | Fee-note email (Mailpit `FbM5JuniKthFkSG9NokDgo`) contains **View Fee Note** → `http://localhost:3002/invoices/dcc26611…` — the exact URL landed on after login redirect. Portal detail renders INV-0001 · SENT |
| 30.2 | PASS (product-shape notes) | Detail: 3 line items (2 TIME + sheriff disbursement), Subtotal R 1 250,00, VAT — Standard (15%) R 0,00 + Zero-rated (0%) R 0,00 rows, Total R 1 250,00, **Pay Now** button, Download PDF. "Due:" is blank — no due date was set on the fee note (wizard/billing run never asked; email showed "Due Date N/A"). Lines are TIME not tariff per OBS-2101 |
| 30.3 | PASS | Portal copy uses "Fee Notes" / "Back to fee notes" / "This fee note has been paid" via terminology override; URL segment `/invoices` matches firm-side URL scheme — consistent. No discrepancy beyond the already-logged firm-side leaks (LZKC-009) |
| 30.4 | PASS | 📸 `day-30-portal-fee-note-detail.png` |
| 30.5 | PASS | Pay Now → new tab **"Mock Payment Checkout — DEV ONLY"** (`:8080/portal/dev/mock-payment?sessionId=MOCK-SESS-d999f5cb…&amount=1250.00&currency=ZAR&returnUrl=…payment-success`) |
| 30.6 | PASS | Checkout shows Invoice dcc26611…, Amount ZAR 1250.00 (auto-populated), Session MOCK-SESS-d999f5cb… → **Simulate Successful Payment** |
| 30.7 | PASS | Redirect to `/invoices/dcc26611…/payment-success`: "Payment confirmed — Payment received — thank you! Paid on 6 Jul 2026". Fee-note detail reloads as **PAID** with "This fee note has been paid" banner (no Pay button) |
| 30.8 | FAIL → **LZKC-012** | No receipt/payment-confirmation artefact exists. The only download is "Download PDF", which serves S3 `generated/invoice-cover-letter-inv-0001-2026-07-06.pdf` — the **Invoice Cover Letter** (PDF text: "Please find attached the invoice for services rendered. Invoice Number: [blank] Total Amount: [blank]"), no line items, no amounts, no receipt. Same 1183-byte file is the email attachment `INV-0001.pdf` (identical size + generation timestamp 13:22:32Z = send time) — the client-facing "fee note PDF" contains no fee note |
| 30.9 | PASS | 📸 `day-30-portal-payment-success.png` |
| 30.10 | PASS (product-shape note) | `/invoices` list: single row INV-0001 · **PAID** · 6 Jul 2026 · R 1 250,00. Product has no "Due"/"Paid" filter tabs — one table with status badges; PAID state correct |
| 30.11 | PASS | Isolation: `/invoices` shows only Sipho's INV-0001; no Moroka fee notes (DB has exactly 1 invoice, belonging to Sipho — read-only corroboration) |

## Day 30 day-level checkpoints

- Mock-gateway payment completes end-to-end (webhook-driven reconciliation): **PASS** — backend 13:28:33Z: `MockPayment: webhook processed … status=COMPLETED reference=MOCK-PAY-b34b7841…` → `Recorded webhook payment for invoice dcc26611…` → `Reconciled completed payment … via mock`; DB `INV-0001 | PAID`
- Firm-side fee note reflects PAID within 60s: **PASS** — firm detail (Thandi session, tab 0) shows **Paid**, "Payment Received — Paid on: 6 Jul 2026, Reference: MOCK-PAY-b34b7841…", Payment History rows Completed + Created (webhook processed 13:28:33Z, i.e. <1s after simulate click)
- Receipt download works: **FAIL** (LZKC-012 — no receipt artefact; PDF download is the empty cover letter)
- Isolation holds — no Moroka fee notes: **PASS**

## New gaps

- **LZKC-012 (High, OPEN)** — Client-facing fee-note PDF is wrong/empty: the portal "Download PDF" and the email attachment `INV-0001.pdf` are both the generated **Invoice Cover Letter** (1183 bytes) whose Invoice Number and Total Amount template fields render blank and which contains no line items — the client never receives a real fee-note document, and there is no receipt/payment-confirmation download after paying (scenario 30.8). Related-but-distinct: LZKC-007 (no letterhead/banking on preview), LZKC-010 (blank field in cover letter).

## Notes for later days

- Payment reference `MOCK-PAY-b34b7841-cad8-4cd3-9bc7-7329e20d3464`; reconciliation provider `mock`.
- Day 61 expects "Fee note: INV-0001 R 1,250 (Day 28, PAID Day 30 via mock payment gateway)" — satisfied.
- Sipho portal session is fresh (new magic-link token issued today 13:27Z).
