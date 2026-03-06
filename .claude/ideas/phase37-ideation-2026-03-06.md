# Phase 37 Ideation — Workflow Automations v1
**Date**: 2026-03-06

## Lighthouse Domain
Professional services firms broadly (accounting, legal, consultancies). Every firm has repeatable operational patterns that are currently manual. Automations are the feature that separates "project management tool" from "practice management platform" — the differentiator vs. Harvest/basic tools and the table stakes vs. Accelo/Productive.io.

## Decision Rationale
Three candidates discussed: Workflow Automations, Resource Planning, Bulk Billing. Automations won because:
1. **Highest leverage** — multiplies the value of everything already built (events, notifications, templates, lifecycle machines)
2. **Connective tissue** — wires discrete features into an integrated system
3. **Foundation for future phases** — automations become the engine for resource alerts, billing triggers, compliance reminders
4. Resource Planning is visibility (important but passive); Bulk Billing is efficiency (important but incremental). Automations are *active* — they reduce manual work.

### Key Design Choices
1. **Rules engine, not visual workflow builder** — trigger + conditions + actions configured via forms. Covers 80% of use cases. Visual builder is v2.
2. **Immediate + scheduled actions** — most fire instantly, but delayed actions (wait N days) supported via polling scheduler (reuses TimeReminderScheduler pattern).
3. **6 pre-built templates** — seeded per tenant, firms activate and customize. Reduces onboarding friction.
4. **Log + notify on failure** — transparent error handling. Rules stay active, admins see failures in execution log.
5. **Cycle detection via event metadata** — domain events carry `automationExecutionId` flag, listener skips re-trigger. Simple and reliable.
6. **JSONB for configs** — same pattern as custom fields. Validated by Java sealed classes + TypeScript discriminated unions.

## Founder Preferences (Confirmed)
- Rules engine over presets or visual builder — right balance of power vs. buildability
- Delayed actions required — reminders and escalations are core use cases
- Templates included — consistent with "premium, complete" philosophy
- Log + notify over auto-disable — transparency over safety switches

## Phase Roadmap (Updated)
- Phase 34: Client Information Requests (in progress)
- Phase 35-36: Keycloak + Gateway BFF (complete)
- **Phase 37: Workflow Automations v1** (spec written)
- Phase 38: Resource Planning & Capacity
- Phase 39: Bulk Billing & Batch Operations
- Phase N (late): Accounting Sync (Xero + Sage)

## Estimated Scope
~6 epics, ~14-16 slices. Heavily leverages existing infrastructure (domain events, notification handlers, scheduled jobs, project templates, email delivery). New entities: AutomationRule, AutomationAction, AutomationExecution, ActionExecution. New packages: `automation/` (engine, rules, actions, conditions, scheduler, templates, controller).
