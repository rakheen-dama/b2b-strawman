# Phase 60 Ideation — Trust Accounting (Legal Phase 2)
**Date**: 2026-04-03

## Decision
Full trust accounting — double-entry ledger, client ledger cards, bank reconciliation, interest/LPFF allocation, investment register, Section 35 compliance reports. No lean/light version — founder explicitly chose full model.

## Rationale
Phase 55 (Legal Foundations) is built (PRs #841–#853) — court calendar, conflict check, LSSA tariff all operational. Trust accounting is the single feature that makes or breaks law firm adoption. Every matter involving client money requires it. Legal Practice Act Section 86 is non-negotiable.

### Key Decisions
1. **Full model, not lean** — "Splitting light then full is just deferring complexity, and sometimes ends up being more complex. We need to model this properly." Direct quote.
2. **TrustAccount as proper entity** (not OrgSettings config) — supports multiple accounts per firm (general + investment).
3. **Invoice FK for fee transfers** — direct FK to Invoice entity. Works alongside portal payments (PaymentEvent) and direct EFT. Invoice doesn't care about payment path.
4. **Configurable dual authorization** — not just single capability. Org-level setting with optional threshold. Self-approval prevention.
5. **Daily balance interest calculation** — correct method, not monthly average. Pro-rata splitting for mid-period rate changes.
6. **Split-pane bank reconciliation UX** — bank lines left, unmatched transactions right. This is where bookkeepers live.
7. **LPFF rate history table** — not a single config value. Supports rate changes over time for correct historical calculations.

## Integration Analysis
Trust accounting requires **zero external integrations**. Bank reconciliation is via CSV upload, not API. LPFF reporting is generated PDF, not electronic submission. This makes it a self-contained module — complexity is in the accounting logic, not the integration layer.

## Phase 55 Note
Phase 55 is complete but has no test coverage. Trust accounting builds on the same vertical module patterns, so this technical debt should be addressed before or alongside this phase.

## Phase Roadmap (Updated)
- Phase 58: Demo Readiness (complete)
- Phase 59: Help Documentation (complete)
- **Phase 60: Trust Accounting** (spec written)
