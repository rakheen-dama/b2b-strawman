# Phase 38 Ideation — Resource Planning & Capacity
**Date**: 2026-03-06

## Lighthouse Domain
Professional services firms at 5-50 people — the size where "who's available?" stops being answerable from memory. Accounting firms (engagement scheduling around tax deadlines), consultancies (project staffing), agencies (campaign allocation). Universal need across all verticals.

## Decision Rationale
After workflow automations (Phase 37), three candidates discussed: Resource Planning, Bulk Billing, Reporting. Resource Planning won because:
1. **Capability, not efficiency** — enables a new class of decisions ("can we take on this client?") vs. making existing things faster
2. **Forward-looking** — the platform's first predictive/planning feature (everything else is backward-looking or reactive)
3. **Data already exists** — time entries + project members + rates + budgets are all in place. Resource planning surfaces and projects this data.
4. Reporting benefits from utilization data that resource planning creates. Sequencing: resources → reporting.

### Key Design Choices
1. **Hours per week** as base unit — percentages derived, not stored. Matches how firms think ("Bob does 20h on the audit").
2. **Planned + actuals** — ResourceAllocation (forward-looking) vs TimeEntry (backward-looking). Independent but compared.
3. **Weekly granularity** — not daily. Weekly matches professional services planning rhythm. Daily is staff augmentation territory.
4. **Over-allocation = warning, not error** — firms intentionally over-commit. Blocking would make the tool hostile.
5. **Leave as simple date ranges** — no types, no accrual, no approval. Just visibility markers that reduce capacity.
6. **Auto-add project members on allocation** — reduce friction. Allocating = implicitly assigning.
7. **Projected profitability** — allocated hours × rates = forward-looking P&L. Extends Phase 8 reports.

## Founder Preferences (Confirmed)
- Hours per week (not percentages)
- Planned + actuals (not actuals-only)
- Leave management included (simple, no approval workflow)
- Full scope approved without trimming

## Phase Roadmap (Updated)
- Phase 34: Client Information Requests (in progress)
- Phase 35-36: Keycloak + Gateway BFF (complete)
- Phase 37: Workflow Automations v1 (spec written)
- **Phase 38: Resource Planning & Capacity** (spec written)
- Phase 39: Bulk Billing & Batch Operations
- Phase 40: Reporting & Data Export
- Phase N (late): Accounting Sync (Xero + Sage)

## Estimated Scope
~6 epics, ~14-16 slices. New entities: MemberCapacity, ResourceAllocation, LeaveBlock. New packages: `capacity/` (entities, service, controller), `leave/` (entity, service, controller). Signature UI: allocation grid (members × weeks) with colored project bars.
