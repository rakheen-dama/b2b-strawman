# Phase 49 Ideation — Vertical Architecture: Module Guard & Profile System
**Date**: 2026-03-18

## Strategic Decision
**Independent forks vs. parallel microservices vs. modular monolith** — resolved in favor of modular monolith with tenant-gated modules.

### Options Considered
1. **Independent forks** (clone repo per vertical) — rejected. At 5+ aspirational verticals, maintenance overhead (N cherry-picks per core fix) is unsustainable for a solo founder.
2. **Domain-specific microservices** (trust-accounting-service, court-calendar-service) — rejected. Operational overhead (separate deploys, monitoring, API versioning, distributed tracing) kills velocity for a solo team. The 5+ vertical aspiration doesn't justify the infrastructure complexity.
3. **Modular monolith with tenant-gated modules** — chosen. All vertical modules always deployed, access gated per tenant via OrgSettings `enabled_modules`. Same pattern as existing CustomerLifecycleGuard and plan enforcement.

### Key Architectural Insight
`@ConditionalOnProperty` (application-level toggle) doesn't work for multi-tenant apps — can't toggle per-request based on which tenant is interacting. The correct pattern is runtime tenant metadata checks, identical to existing lifecycle guards and RBAC. Deploy everything, gate per tenant.

## Concrete Verticals Defined

| Vertical | Type | Unique Needs |
|----------|------|-------------|
| IT Consulting / MSP | Pure config (profile only) | None — core product IS their workflow |
| SA Accounting Firm | Config + possibly thin deadline module | FICA packs, SARS/CIPC deadline tracking, accounting templates |
| SA Law Firm | Config + 3-5 domain modules | Trust accounting, court calendar, conflict check, LSSA tariff, Section 35 reporting |

### Design Litmus Test for "Config vs. Module"
If the feature requires **automation** (recurring generation, cross-entity business rules, regulatory reporting), it's a module. If it's just a different way of looking at existing data, it's configuration.

## Founder Context
- Solo owner with full-time day job
- AI agents do most implementation
- 3 known verticals (accounting, legal, agency), aspirational 5+
- Originally thought forks could grow independently — now understands maintenance cost
- Phase 47 "skins before forks" hypothesis partially validated — accounting works with config alone

## Phase Roadmap (Updated)
- Phase 47: Vertical QA — Accounting (gap report done/in progress)
- Phase 48: QA Gap Closure (if needed)
- **Phase 49: Vertical Architecture — Module Guard & Profile System**
- Phase 50 (candidate): Legal Modules — Trust Accounting (first real domain module)

## Estimated Scope
~5 epics, ~12-15 slices. Backend: VerticalProfile entity, ModuleGuard, module registry, Flyway conditional migrations. Frontend: profile-aware UI (conditional nav, conditional components), profile switcher for admin provisioning. First profile: accounting (from Phase 47 data).
