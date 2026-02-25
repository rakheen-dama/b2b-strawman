# Phase 25 Ideation — Online Payment Collection
**Date**: 2026-02-25

## Lighthouse Domain
- SA small-to-medium law firms (unchanged)
- Payment collection is vertical-agnostic — every fork needs "send invoice, get paid"
- Legal fork adds trust accounting ON TOP of the same PSP adapters (not a replacement)

## Decision Rationale
Founder asked about "payments". Researched competitor landscape — Harvest, Scoro, Accelo, Productive all offer Stripe-based "Pay Now" on invoices as table stakes. Teamwork notably lacks this and it's a known weakness. Clio (legal) adds trust accounting as a separate layer on top of standard payment collection.

Key design decisions:
1. **Tier 1 only** — "Pay Now" button on invoices, webhook reconciliation, done. No partial payments, no auto-charge, no payment plans. Minimum to be taken seriously.
2. **Stripe + PayFast** — Stripe for global, PayFast for SA market. Founder confirmed both are needed.
3. **Stripe Checkout (hosted)** — not embedded Elements. Zero PCI burden, Stripe handles Apple Pay/Google Pay/bank transfers automatically. Matches Harvest/Scoro approach.
4. **PayFast over Peach Payments** — simpler, more recognized by SA SMEs. Peach can be a future adapter.
5. **Full payment only** — no partial payments in v1. SENT → PAID, clean and simple.
6. **`paymentDestination` seam** — one VARCHAR field defaulting to "OPERATING". Legal fork adds "TRUST" + ledger logic. Zero restructuring on fork day.
7. **BYOAK only** — no platform-managed PSP account (unlike email which has platform SMTP default). Tenants must bring their own Stripe/PayFast keys.

## Competitive Analysis Summary
- **Harvest**: Stripe + PayPal, inline payment (no redirect), card-on-file, recurring auto-charge
- **Scoro**: Stripe only, payment link on invoice, late payment reminders
- **Accelo**: Stripe + PayPal + Authorize.net, auto-charge on due date, payment reminders
- **Productive.io**: Stripe only, payment link, reminder sequences
- **Ignition**: Stripe + GoCardless, proposal-to-payment flow, scheduled billing
- **Clio**: Full trust accounting, IOLTA compliance, payment plans, Affirm BNPL (2025), QR/tap-to-pay
- **Teamwork**: NO online payment (PDF invoice only) — notable gap

## Key Design Preferences
1. Trust accounting is fork material, not foundation — but the `paymentDestination` seam prevents rework
2. About 80% of payment infrastructure carries over to legal: PSP adapters, webhooks, portal payment UI, status tracking
3. Legal fork adds: TrustAccount entity, per-client trust ledger, trust-to-operating transfers, LPFF interest rules, trust audit report

## Phase Roadmap (updated)
- Phase 24: Outbound Email Delivery (in progress)
- Phase 25: Online Payment Collection (requirements written)
- Phase 26+: Candidates — Accounting sync (Xero/Sage), recurring work/retainer billing improvements, trust accounting (legal fork), AI features
