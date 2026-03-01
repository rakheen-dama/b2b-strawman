# Phase 32 Ideation — Proposal → Engagement Pipeline
**Date**: 2026-02-28

## Lighthouse Domain
Accounting/bookkeeping firms (primary). Proposal → auto-engagement is the #1 feature gap vs. Practice/Ignition. Also critical for consultancies and agencies (vs. Accelo). Legal firms (future fork) would need engagement letter variants.

## Decision Rationale
Proposals was the agreed-upon next phase from the Phase 29-31 ideation session. Founder confirmed without hesitation — this is the "aha moment" phase that turns DocTeams from toolkit into workflow product.

### Key Design Choices
1. **All three fee models** (fixed + hourly + retainer) — founder chose full flexibility over incremental rollout
2. **Full orchestration on acceptance** — auto-creates project (from template), billing (invoices/retainer), team assignment, onboarding checklist. The "wow" moment.
3. **Portal-based acceptance** — richer than email links, builds on existing portal infrastructure
4. **Tiptap proposal body** — reuses Phase 31 editor, variables for client name/fees/dates
5. **Milestone billing** for fixed-fee engagements — create scheduled invoices on acceptance
6. **Basic lifecycle tracking** — draft/sent/accepted/declined/expired. No view tracking in v1.
7. **Dedicated proposals page** with pipeline stats + customer detail tab
8. **Project ↔ Proposal linkage** — project shows originating proposal, enables future change orders

## Founder Preferences (Confirmed)
- Premium, complete experience over MVP — consistent with "first impressions last" philosophy
- Full orchestration is non-negotiable — half-automated is worse than manual
- Portal > email for client-facing experiences
- Single fee model per proposal (no mixed billing in v1)

## Phase Roadmap (Updated)
- Phase 30: Expenses, Recurring Tasks & Daily Work (in progress, ~60%)
- Phase 31: Document System Redesign (done)
- **Phase 32: Proposal → Engagement Pipeline** (spec written)
- Phase 33: Accounting Sync (Xero + Sage)
- Phase 34: Client Information Requests
- Phase 35: Resource Planning & Capacity
- Phase 36: Bulk Billing & Batch Operations
- Phase 37: Workflow Automations v1

## Estimated Scope
~6-7 epics, ~14-18 slices. Builds heavily on existing infrastructure (templates, acceptance, project templates, retainers, rate cards, portal, email).
