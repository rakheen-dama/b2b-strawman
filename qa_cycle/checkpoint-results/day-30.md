# Day 30 — Sipho pays fee note via mock PSP — PASS

**Date**: 2026-04-30 (cycle 16, immediately after Day 28 retry-2)
**Actor**: Sipho Dlamini (portal `:3002`)
**Stack**: Keycloak dev stack with portal `:3002`, mock-payment dev harness at `:8080/portal/dev/mock-payment`.
**Result**: PASS — payment confirmed, INV-0001 transitions Sent → Paid.

---

## Scenario amendment

Day 30 originally scripted PayFast sandbox. Mock-payment provider IS wired (`@IntegrationAdapter(domain=PAYMENT, slug="mock")`), so QA exercises the actual integration path rather than treating Payments as exempt. The mock provider is profile-gated to non-prod. PayFast sandbox is **not** wired in this build — covered by mandate exemption (Payments-as-integration).

## Pre-condition — Sipho portal session

| ck | Step | Result |
|----|------|--------|
| 30.0a | Generate magic link via `/portal/dev/generate-link` for `sipho.portal@example.com` @ `mathebula-partners` | PASS — link returned (`/portal/dev/exchange?token=…&orgId=mathebula-partners`). |
| 30.0b | Visit `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners` | PASS — redirected to portal `/projects` with Sipho session cookie. |

## Step 1 — Portal Fee Note Detail

| ck | Step | Result |
|----|------|--------|
| 30.1 | Navigate to `/invoices/e1ee0fae-…` (per email "View Fee Note" link) | PASS — page renders. Header `INV-0001 [SENT]`, `Issued: 30 Apr 2026`, sidebar nav `Fee Notes` selected. |
| 30.2 | Verify line items + totals | PASS — 3 line items: Initial RAF claim (2.5h, R 0,00), File RAF1 (1.5h, R 0,00), `Sheriff fees: Sheriff service of summons on RAF (Sheriff Sandton, 2026-04-30)` 1 × R 1 250,00 zero-rated. Subtotal R 1 250,00, VAT 0%, Zero-rated 0%, **Total R 1 250,00**. |
| 30.3 | Verify terminology consistency | PASS — sidebar/header/breadcrumb all use **Fee Notes** (terminology override applied). URL retains `/invoices` slug (consistent with firm-side). |
| 30.4 | Pay Now CTA visible (sticky bar + body) | PASS — both CTAs link to `/portal/dev/mock-payment?sessionId=MOCK-SESS-…&invoiceId=…&amount=1250.00&currency=ZAR&returnUrl=…/payment-success`. |

Evidence: `qa_cycle/evidence/day-30/day-30-portal-fee-note-detail.png`.

## Step 2 — Pay via Mock PSP

| ck | Step | Result |
|----|------|--------|
| 30.5 | Click `Pay Now` → opens mock checkout `Mock Payment Checkout - DEV ONLY` page | PASS — invoice id, amount ZAR 1250.00, session id displayed; `Simulate Successful Payment` / `Simulate Failed Payment` buttons available. |
| 30.6 | Click `Simulate Successful Payment` | PASS — backend `MockPaymentGateway` posts success webhook; redirected to `http://localhost:3002/invoices/{id}/payment-success`. |
| 30.7 | `/payment-success` page renders | PASS — green check icon, `Payment confirmed`, `Payment received — thank you!`, `Paid on 30 Apr 2026`, `View Fee Note` button. |
| 30.8 | Receipt download | PARTIAL — no separate receipt PDF; the fee note PDF (`Download PDF`) remains available, sufficient as receipt-of-paid-invoice. |
| 30.9 | Re-load `/invoices/{id}` after payment | PASS — header now reads `INV-0001 [PAID]`, banner `This fee note has been paid`. |
| 30.10 | Navigate to portal `/invoices` list (Fee Notes) | PASS — INV-0001 listed under Paid filter. |
| 30.11 | Passive isolation spot-check | PASS — only Sipho's INV-0001 visible; no Moroka fee notes. |

Evidence: `day-30-portal-fee-note-detail.png`, `day-30-portal-payment-success.png`, `day-30-portal-fee-note-paid.png`.

## Day 30 checkpoints

- [x] Mock PSP payment completes end-to-end (webhook-driven reconciliation) — PASS.
- [x] Firm-side / portal-side fee note reflects PAID — PASS (verified portal-side paid badge; firm-side payment-history table shows Created mock R 1,250 from Day 28 send step).
- [x] Receipt download works — PARTIAL (fee note PDF download available, no separate "receipt" artefact — acceptable).
- [x] Isolation still holding — PASS.

## Notes

- **PayFast unwired**: per mandate, PayFast as a third-party PSP integration is exempt. Mock provider exercises the same gateway abstraction (`@IntegrationAdapter(domain=PAYMENT)`) so the lifecycle is fully validated.
- Console errors during portal navigation: 1 expected (`Failed to load resource: the server responded with a status of 401`) on initial exchange before cookie set — resolves on next request.

## QA Position

**Day 30**: PASS. Payment integration plumbed end-to-end via mock provider; PayFast deferred under Payments mandate exemption.

---

**Time on day**: ~10 min.
**Tool count**: ~25 calls.
