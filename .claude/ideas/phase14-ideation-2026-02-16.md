# Phase 14 Ideation — Recurring Work & Retainers
**Date**: 2026-02-16

## Lighthouse Domain
- SA small-to-medium law firms (2-20 fee earners), with accounting firms as closest fork target
- Recurring revenue is table stakes for going to market in professional services

## Decision Rationale
Three candidates discussed after Phase 13 (in progress):
1. **Org Integrations / BYOAK** — deferred, founder has no test API keys for external services
2. **Customer Portal Frontend** — strong candidate (Phase 7 backend exists), but less critical for go-to-market
3. **Recurring Work & Retainers** — **chosen**, directly enables recurring revenue billing which is essential for market entry

**Key insight from founder**: "Recurring payments seems like a must to go into market" — without retainer/recurring billing, the platform can only model one-off engagements, which doesn't reflect how most professional services firms actually operate.

## Scope Split Decision
Recurring Work was split into two logical phases:
- **Phase 14**: Retainer agreements + hour banks + recurring invoice generation + scheduling backbone (the money side)
- **Phase 15**: Project templates + recurrence rules + auto-spawning (the delivery side)

Split rationale: clean technical separation. Retainers extend the invoice domain (Phase 10). Project templates extend the project domain (Phases 1, 4). Only coupling is a FK (retainer ↔ project template), added in Phase 15.

## Key Design Preferences
1. **Background scheduler, not manual trigger** — founder wants scheduling infra because self-managed subscriptions (Phase 2) will also need it
2. **Both billing models**: fixed-fee AND hour bank retainers in v1
3. **Rollover kept simple**: NONE or UNLIMITED. No capped rollover in v1.
4. **Full scope in one phase** — founder not in prod yet, wants comprehensive feature

## Phase Roadmap (updated)
- Phase 13: Customer Compliance & Lifecycle (in progress, 2/7 epics done)
- Phase 14: Recurring Work & Retainers (requirements written)
- Phase 15: Project Templates & Recurrence (scoped but not spec'd)
- Phase 16+: Customer Portal Frontend, Org Integrations/BYOAK, Reporting & Export

## Scheduling Infrastructure Note
Phase 14 introduces the first background scheduling infrastructure (`TenantAwareJob` abstraction). This is foundational — future consumers include:
- Phase 13: dormancy detection, retention policy enforcement
- Phase 2 (retrofit): subscription renewal checks
- Phase 15: recurring project auto-spawning
Design must be generic and reusable, not retainer-specific.
