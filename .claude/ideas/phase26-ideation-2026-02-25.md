# Phase 26 Ideation — Invoice Tax Handling
**Date**: 2026-02-25

## Lighthouse Domain
- Pivoting from SA legal firms as first fork target
- **Accounting/bookkeeping firms** identified as closest vertical — smallest gap from current feature set
- TaxDome, Karbon, Financial Cents, Canopy as competitive reference points
- SA market underserved (Karbon/TaxDome are US/AU-focused)

## Decision Rationale
Strategic session mapped remaining gaps to reach accounting-firm-ready product. Tax handling on invoices was identified as a small but critical credibility gap — you can't sell to accounting firms with invoices that don't handle VAT properly.

Current state: `Invoice.taxAmount` is a single flat BigDecimal (manual entry). No tax rates, no per-line tax, no VAT registration number, no tax-inclusive/exclusive toggle. OrgSettings has zero tax fields.

Scope kept tight: org-level tax config + tax rates + per-line tax + template updates. No multi-jurisdiction, no withholding tax, no tax filing integration.

## Key Design Preferences
1. **Small phase, focused scope** — founder prefers smaller phases over bundling unrelated changes (UX complexity concern)
2. **Tax-exclusive as default** — SA B2B convention, most practice management tools default to exclusive
3. **VAT registration number on invoices** — legal requirement in SA, common globally
4. **Existing entity pattern** — TaxRate follows BillingRate/CostRate pattern in `rate/` package

## Locked Phase Roadmap
- Phase 24: Outbound Email Delivery (in progress)
- Phase 25: Online Payment Collection (requirements written)
- **Phase 26: Invoice Tax Handling** (requirements written)
- Phase 27: E-Signing
- Phase 28: Document Clauses
- Phase 29: Accounting Sync (Xero + Sage)
- Phase 30: Verification Port + Checklist Auto-Verify
- Phase 31: Platform Self-Billing (dogfood own invoicing for tenant subscriptions)

## Vertical Strategy Update
- After Phase 31: self-sustaining product that can bill its own tenants
- Accounting firm fork gap: ~2-3 phases after Phase 31 (deadline calendar, engagement-specific workflows, practice-specific packs)
- Legal fork deferred but `paymentDestination` seam (Phase 25) prevents rework
- Document clauses (Phase 28) serve both accounting (engagement letters) and legal (contracts)
- Verification port (Phase 30) is jurisdiction-agnostic — SA adapters are fork material
