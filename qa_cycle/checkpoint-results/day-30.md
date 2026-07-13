# Day 30 — Sipho pays fee note via mock payment gateway `[PORTAL]` — cycle 2026-07-12 (executed 2026-07-13)

**Actor**: Sipho Dlamini (portal :3002). Prior portal JWT expired → fresh magic link (`/login` → Send Magic Link → Mailpit `myRFUFbkXWhha9VJjzv2oC` → `/auth/exchange`) which honoured `redirectTo` straight onto the fee-note detail.

**Gateway note**: mock gateway (`MockPaymentGateway`, dev checkout at `:8080/portal/dev/mock-payment`), not PayFast — prior-cycle precedent / Day-61 amendment. Unchanged.

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 30.1 | PASS | Fee-note email **View Fee Note** → `:3002/invoices/f44ef40d…` = exact URL landed on post-login. Detail renders INV-0001 · SENT |
| 30.2 | PASS (product-shape) | 3 line items (2 TIME @ R 0,00 per OBS-2101 + sheriff R 1 250,00), Subtotal R 1 250,00, **VAT — Standard (15%) R 0,00 / Zero-rated (0%) R 0,00 summary rows render with full labels on portal** (contrast LZKC-028 firm-side), Total R 1 250,00, Pay Now, Download PDF. "Due:" blank (no due date set — Day-28 note, observation) |
| 30.3 | PASS | Portal copy "Fee Notes" / "Back to fee notes" / "This fee note has been paid"; URL `/invoices` consistent with firm scheme. No new discrepancies |
| 30.4 | PASS | 📸 `day-30-portal-fee-note-detail.png` |
| 30.5 | PASS | Pay Now → "Mock Payment Checkout — DEV ONLY" (session `MOCK-SESS-6e2a423c…`) 📸 `day-30-mock-payment-checkout.png` |
| 30.6 | PASS | Checkout auto-populated: Invoice `f44ef40d…`, Amount ZAR 1250.00 → **Simulate Successful Payment** |
| 30.7 | PASS | Redirect `/invoices/f44ef40d…/payment-success`: "Payment confirmed — Payment received — thank you! Paid on 13 Jul 2026". Detail reloads **PAID** + "This fee note has been paid", Pay Now gone |
| 30.8 | KNOWN-DEFERRED (not re-filed) | No receipt/payment-confirmation artefact (LZKC-012 pt 2 DEFERRED epic). Material improvement vs prior cycle: **Download PDF is now the real line-item fee note** — S3 `generated/fee-note-inv-0001-2026-07-12.pdf`, **MD5 `a4a39557…` byte-identical to email attachment INV-0001.pdf** → **LZKC-012 pt 1 HOLDS (portal leg)** |
| 30.9 | PASS | 📸 `day-30-portal-payment-success.png` |
| 30.10 | PASS (product-shape) | `/invoices`: single row INV-0001 · **PAID** · 12 Jul 2026 · R 1 250,00 (no Due/Paid filter tabs in product — status-badge table, same as prior cycle) |
| 30.11 | PASS | Isolation: list shows only Sipho's INV-0001; DB has exactly 1 invoice tenant-wide (read-only corroboration); no Moroka artefacts |

## Day-level checkpoints

- Mock-gateway payment end-to-end (webhook-driven reconciliation): **PASS** — backend 00:37:39Z: `webhook processed … status=COMPLETED reference=MOCK-PAY-6e88d10b…` → `Recorded webhook payment` → `Reconciled completed payment … via mock` → `status=PAID`; DB `INV-0001 | PAID | 1250.00`
- Firm-side reflects PAID within 60s: **PASS** — firm detail (Bob, :3000): badge **Paid**, "Payment Received — Paid on: 13 Jul 2026, Reference: MOCK-PAY-6e88d10b…", Payment History Completed + Created rows (webhook <1s after simulate click)
- Receipt download: **KNOWN-DEFERRED** (LZKC-012 pt 2)
- Isolation holds: **PASS**

## Fix re-verifications

- **LZKC-012 pt 1 HOLDS (portal leg)** — portal Download PDF = real fee note, byte-identical to email attachment (completes the Day-28 attachment-leg verification).
- **LZKC-022** — still N/A: no firm-side notification email fired on payment (prior cycle same — no paid-notification email exists; receipt email = deferred pt 2). Re-check on a later day that generates firm-side notification email.

## New gaps

None.

## Observations

- Console clean on all portal navigations (0 errors; 1 benign dev warning per load) and firm detail reload.
- Payment reference `MOCK-PAY-6e88d10b-5008-470c-ac64-202500bb7e73`, provider `mock` — Day 61 expectation "PAID Day 30 via mock payment gateway" satisfied.
- Sipho portal session fresh (magic-link token 00:35:50Z, 2026-07-13).
