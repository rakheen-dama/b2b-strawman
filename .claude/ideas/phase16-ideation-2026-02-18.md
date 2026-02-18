# Phase 16 Ideation — Project Templates & Recurring Schedules
**Date**: 2026-02-18

## Lighthouse Domain
- SA small-to-medium law firms (unchanged)
- This phase is vertical-agnostic — every professional services vertical has recurring engagements

## Decision Rationale
Founder confirmed recurring work as next feature phase. Key reasoning:
1. Every firm has recurring engagements — monthly bookkeeping, quarterly reviews, annual audits
2. Without templates/schedules, users manually recreate projects every cycle (high friction)
3. Builds on deepest parts of the stack (projects, tasks, customers, tags)
4. Fork-agnostic — accounting, legal, agencies all need this

**Scope split decision**: Founder agreed to split recurring work into 2 phases for risk reduction:
- **Phase 16**: Project Templates & Recurring Schedules (the automation engine)
- **Phase 17**: Retainer Agreements & Billing (the commercial layer)

Rationale: Phase 16 is pure project/task domain (low risk). Phase 17 introduces financial logic (hour banks, rollover, overage) which needs more careful testing. Incremental value — templates alone are useful.

## Key Design Preferences (from founder)
1. Simple, consistent, reliable — must add value above all
2. Must match what best similar products (Productive.io, Scoro, Harvest) offer
3. Risk-averse — smaller phases preferred given how much has been built
4. Role-based assignment defaults on templates (not member-specific)
5. Both manual and scheduled template instantiation (templates useful standalone)

## Phase 17 — Retainer Agreements & Billing (same session)
Designed in same session while context was fresh.

**Key decisions**:
- **Overage rate**: Standard billing rate from existing rate hierarchy (Phase 8). No separate overage rate config.
- **Rollover**: Three policies — FORFEIT, CARRY_FORWARD, CARRY_CAPPED (with configurable cap).
- **Period close**: Admin-triggered, never automated. Dashboard shows "ready to close" after period end. Admin reviews, clicks close, invoice generated as DRAFT.
- **One retainer per customer**: Simplifies consumption tracking. Multiple concurrent retainers deferred.
- **Consumption**: Query-based (SUM of billable time entries in date range), not incremental counter. Self-healing.
- **Entities**: RetainerAgreement, RetainerPeriod. InvoiceLine gets nullable retainer_period_id FK.

## Phase Roadmap (updated)
- Phase 14: Customer Compliance & Lifecycle (in progress)
- Phase 15: Contextual Actions & Setup Guidance (requirements written)
- Phase 16: Project Templates & Recurring Schedules (requirements written)
- Phase 17: Retainer Agreements & Billing (requirements written)
- Phase 18+: Candidates — Org Integrations (BYOAK), Customer Portal Frontend, Reporting & Export

## Architecture Notes (Phase 16)
- **New entities**: ProjectTemplate, TemplateTask, TemplateTag, RecurringSchedule, ScheduleExecution
- **Scheduler**: Daily `@Scheduled` cron, iterates tenant schemas, idempotent via (schedule_id, period_start) unique constraint
- **Name tokens**: Simple string substitution ({customer}, {month}, {year}, etc.)
- **Customer lifecycle awareness**: Scheduler skips OFFBOARDED/PROSPECT customers
- **Template management in Settings, Schedule management in main nav**

## Architecture Notes (Phase 17)
- **New entities**: RetainerAgreement, RetainerPeriod
- **Modified**: InvoiceLine gets `retainer_period_id` FK
- **Period lifecycle**: OPEN → CLOSED (admin-triggered)
- **Consumption**: query-based, triggered by ApplicationEvent on time entry changes
- **Notifications**: 80% capacity warning, 100% consumed alert, period ready to close
- **Retainer dashboard in main nav, retainer tab on customer detail**
