# Phase 40 Ideation — Bulk Billing & Batch Operations
**Date**: 2026-03-07

## Lighthouse Domain
Professional services firms at 20+ monthly clients — the size where one-at-a-time invoicing becomes a full-day ordeal. Universal across all verticals (accounting, legal, consultancies, agencies). Month-end billing is the highest-friction operational task.

## Decision Rationale
Three topics explored before converging:
1. **Inter-tenant tasks/projects** — agencies on the platform could be clients of accounting firms on the platform. Interesting network-effect play but quickly identified as a different product (marketplace/procurement), not a practice management feature. Requires scale to be valuable. Shelved.
2. **Verification integrations (KYC/CIPC/ID)** — previously discussed in Phase 13 ideation, kept appearing as "Verification Port" on roadmaps but never built. Reason: vertical-specific (SA legal/accounting), integration-heavy, and the manual checklist workflow already works. Deferred to near-fork phase.
3. **Bulk Billing** — won because it's the #1 operational bottleneck, directly tied to revenue collection, universal across verticals, and builds entirely on existing invoice infrastructure.

### Key Design Choices
1. **Cherry-pick per customer, per entry** — not "generate best-guess invoices." Firms need to review and select which time entries/expenses to include for each customer before generation. Month-end billing is never "bill everything."
2. **BillingRun as a dedicated entity** — not just a batchId tag on invoices. Provides lifecycle management, preview/selection tracking, cancel with void, and historical reporting.
3. **Explicit entry selection** — BillingRunEntrySelection stores the user's explicit choices. Survives changes to unbilled data between preview and generation. Deterministic.
4. **Sync/async threshold** — sync for <= 50 customers (immediate feedback), async for larger batches. Most firms are under 50 clients.
5. **5-step wizard** — Configure -> Select Customers -> Cherry-Pick -> Review Drafts -> Send. Linear, server-side state persistence.
6. **Retainer batch close opt-in** — can include retainer period close in the same billing run.

## Founder Preferences (Confirmed)
- Cherry-pick over auto-generate — firms need per-entry control
- Full scope approved without trimming

## Phase Roadmap (Updated)
- Phase 38: Resource Planning & Capacity (in progress)
- Phase 39: Admin-Approved Org Provisioning (planned)
- **Phase 40: Bulk Billing & Batch Operations** (spec written)
- Phase 41: Reporting & Data Export (candidate)
- Phase N (late): Verification Integrations (KYC/CIPC)
- Phase N (late): Accounting Sync (Xero/Sage)

## Estimated Scope
~5 epics, ~10-12 slices. BillingRun + BillingRunItem + BillingRunEntrySelection entities. New package: `billingrun/`. Heavily reuses existing InvoiceService (generation, approve, send), email delivery, tax calculation, retainer close. The novel parts are the preview/selection system and the multi-step wizard UI.
