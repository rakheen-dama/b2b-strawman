# Phase 43 Ideation — UX Quality Pass: Empty States, Contextual Help & Error Recovery
**Date**: 2026-03-09

## Lighthouse Domain
Universal across all verticals. Every SaaS product needs onboarding polish — this is the "first 60 seconds" problem. Particularly impactful for demo-readiness and early-user retention. Not a domain feature but a quality layer that compounds across all existing features.

## Decision Rationale
Founder triggered this after experiencing the cold-start problem firsthand — new org provision leads to bland, empty dashboards with no guidance. After 42 phases of feature building, the gap between "works" and "feels good to use" became apparent. Three pillars identified: empty states (guide action), contextual help (explain complexity), error recovery (actionable feedback).

### Key Design Choices
1. **i18n message catalog first** — all new strings centralised in `frontend/src/messages/en/*.json` with a `useMessage` hook. Not a full i18n framework — just the catalog structure that makes locale switching trivial later.
2. **Option A for dashboard onboarding** — Getting Started checklist card on dashboard, not a full dashboard transformation. Faster to build, still effective.
3. **Computed-on-read for checklist progress** — no event-driven tracking, just count entities on each API call. Simpler, always accurate.
4. **~15 pages get empty states**, ~22 help points, error classification layer on frontend.
5. **No backend error format changes** — frontend interprets existing HTTP status codes + error codes.

## Founder Preferences (Confirmed)
- Medium-depth pass (not light, not deep onboarding platform)
- i18n-ready catalog with codes, not scattered strings — future translation-friendly
- Getting Started checklist (Option A) over dashboard transformation (Option B)
- Static help content (no CMS, no admin-editable)

## Phase Roadmap (Updated)
- Phase 40: Bulk Billing & Batch Operations
- Phase 41: Organisation Roles & Capability-Based Permissions (spec written)
- Phase 42: Word Template Pipeline (spec written)
- **Phase 43: UX Quality Pass — Empty States, Contextual Help & Error Recovery** (spec written)

## Estimated Scope
5 epics, ~12-15 slices. Frontend-heavy with one backend touchpoint (onboarding progress endpoint + OrgSettings column). Reusable components: EmptyState, HelpTip, ErrorBoundary, GettingStartedCard. Message catalog foundation enables incremental string migration in future phases.
