# Phase 33 Ideation — Data Completeness & Prerequisite Enforcement
**Date**: 2026-03-01

## Lighthouse Domain
Accounting/bookkeeping firms (primary). FICA compliance is non-negotiable — incomplete customer records can't produce valid tax documents or invoices. Also critical for legal (FICA + trust accounting prerequisites) and consultancies (billing address/VAT for invoicing).

## Decision Rationale
Founder observed that the customer creation dialog hasn't been updated since Phase 4 — field packs and custom fields exist but aren't surfaced at intake or enforced at action points. The system captures metadata but never enforces data quality.

Chose this over accounting sync (previously queued as Phase 33) because: syncing incomplete data to Xero/Sage is worse than no sync at all. Fix data quality first, then sync clean data.

### Key Design Choices
1. **Two-layer model**: Customer lifecycle gates (base completeness) + engagement prerequisites (service-specific fields). Customer stays ACTIVE; new project types surface their own requirements.
2. **Tiered approach**: Smart intake + lifecycle gating + action-point checks + visibility — belt and suspenders. Founder chose full approach over incremental.
3. **Soft-blocking**: Prerequisite modal with inline field editors — never a dead end. Fill gaps and continue without navigating away.
4. **Configurable contexts**: `requiredForContexts` on FieldDefinition — admins control which fields gate which actions. Field packs ship with defaults.
5. **Project templates declare customer field requirements**: "Tax Return" template requires SARS reference. The engagement surfaces the gap, not the customer lifecycle.

## Founder Preferences (Confirmed)
- Data quality before external integrations (this before accounting sync)
- Lifecycle status must mean something — ACTIVE = "we have everything we need"
- Customers don't regress through lifecycle for new service requirements
- Full tiered approach over incremental — consistent with "premium, complete" philosophy

## Phase Roadmap (Updated)
- **Phase 33: Data Completeness & Prerequisite Enforcement** (spec written)
- **Phase 34: Client Information Requests** — #1 daily friction for firms, portal adoption driver
- Phase 35: Resource Planning & Capacity
- Phase 36: Bulk Billing & Batch Operations
- Phase 37: Workflow Automations v1
- Phase N (late): Accounting Sync (Xero + Sage) — pushed to near-market, integration not capability

## Estimated Scope
~6 epics, ~14-16 slices. Primarily wiring existing infrastructure (field packs, readiness service, lifecycle guards, project templates) into enforcement points. One new shared component (PrerequisiteModal). No new domain entities for core system.
