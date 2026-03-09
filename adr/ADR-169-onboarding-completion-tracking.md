# ADR-169: Onboarding Completion Tracking

**Status**: Accepted
**Date**: 2026-03-09
**Phase**: Phase 43 — UX Quality Pass

## Context

Phase 43 introduces a "Getting Started" checklist on the dashboard for newly provisioned organisations. The checklist has 6 steps (create project, add customer, invite member, log time, set up rates, generate invoice) and needs to track which steps are complete.

The question is how to determine completion: should the system compute step status on each request by counting entities, or should it record completion events as they happen and serve the stored state?

The checklist also needs a "dismiss" action — once an org admin dismisses the checklist, it never reappears, regardless of completion state. This dismissal state must persist across sessions and members.

## Options Considered

1. **Computed-on-read (chosen)** — Each `GET /api/onboarding/progress` request runs simple count queries against entity tables (`projectRepository.count() > 0`, `customerRepository.count() > 0`, etc.) and returns the computed completion state. No completion records are stored. Only `onboarding_dismissed_at` is persisted on `OrgSettings`.
   - Pros: Always accurate (no stale state possible), no event wiring or listeners needed, trivially simple implementation (6 count queries), no new tables or entities, self-healing (if data is deleted and re-created, status updates automatically), easy to test (just seed data and query)
   - Cons: 6 SQL count queries per dashboard load (minor performance cost), cannot track *when* a step was completed (no history), cannot track *who* completed a step, cannot distinguish "never done" from "done then undone"

2. **Event-driven tracking** — Listen for domain events (`ProjectCreatedEvent`, `CustomerCreatedEvent`, etc.) and write completion records to a `onboarding_steps` table. Serve stored state on read.
   - Pros: O(1) read performance (just read stored records), captures completion timestamps and actors, decouples read from entity existence (step stays "complete" even if entity is later deleted)
   - Cons: Requires event listeners for 6 entity types, new table + entity + migration, stale state risk (what if the event is missed, or data is restored from backup?), more code to maintain, completion records persist even after onboarding is dismissed (wasted storage), over-engineered for a checklist that most orgs interact with for 1-2 sessions

3. **Client-side tracking (localStorage)** — Track completion in the browser. No backend involvement except for the dismiss flag.
   - Pros: Zero backend cost, instant reads, no API calls
   - Cons: Not shared across team members, lost on browser clear, different state per device, cannot show org-wide progress, fundamentally broken for a multi-user product where any member's action should update the shared checklist

## Decision

Use **computed-on-read** (Option 1). The `OnboardingService` runs 6 count queries per request. The `onboarding_dismissed_at` timestamp on `OrgSettings` controls visibility.

## Rationale

The getting started checklist is a transient onboarding aid, not a permanent feature. Most organisations will interact with it during their first 1-2 sessions, then dismiss it. Investing in event-driven tracking infrastructure for something this short-lived is disproportionate.

The 6 count queries are cheap: each is a `SELECT COUNT(*) FROM {table}` on the tenant schema, hitting indexed primary keys. For a newly provisioned org, these tables have 0-10 rows. Even for a mature org (where the checklist would already be dismissed), the queries would return in <1ms each. The total overhead is negligible compared to the dashboard's existing 8-12 API calls.

Computed-on-read is also more accurate: if an admin deletes all projects and starts over, the checklist correctly reflects "no projects" without needing reconciliation logic. Event-driven tracking (Option 2) would show "complete" for a step whose entity no longer exists — confusing for a new user.

Client-side tracking (Option 3) is fundamentally wrong for a multi-user product. If Alice creates a project, Bob should see that step as complete when he loads the dashboard. localStorage cannot provide this.

## Consequences

- **Positive**: Zero new tables or entities (only one column added to OrgSettings)
- **Positive**: Always accurate — no stale state, no event sync issues
- **Positive**: Simple to test — seed entities, call endpoint, assert response
- **Positive**: Self-healing — data changes (deletes, restores) automatically reflected
- **Negative**: Cannot report on onboarding completion rates across tenants (no stored history) — acceptable for v1, could add analytics tracking later if needed
- **Negative**: 6 extra queries per dashboard load — negligible cost, but could be cached (30s TTL) if ever measured as a bottleneck
- **Neutral**: The `onboarding_dismissed_at` field on OrgSettings means dismissal is org-wide — all members see the same state. This is intentional (onboarding is an org concern, not a personal one)
