# Phase 47 Ideation — Vertical QA: Small SA Accounting Firm
**Date**: 2026-03-15

## Lighthouse Domain
SA small accounting firm (3-person practice). Chosen because it's the smallest fork gap and exercises nearly every platform feature. The QA phase doubles as vertical profile creation.

## Decision Rationale
After 46 phases of feature building, founder identified the core risk: no end-to-end validation against real workflows. Features tested in isolation but never as a system. Picking a vertical gives the QA phase teeth — testing workflows, not features. Two-pass approach: agent finds functional gaps, founder judges UX.

### Key Insight: Skins Before Forks
Discussion revealed the platform already has ~80% of skinning infrastructure (i18n catalog, field packs, template packs, compliance packs, feature flags, org branding). A thin vertical profile layer could support multiple verticals from one codebase. Fork only needed for truly different domain logic (trust accounting, court calendars). The QA phase tests this hypothesis — if the accounting profile works well with configuration alone, the fork strategy shifts.

### Why Accounting First
- Smallest fork gap (~2-3 phases for vertical-specific features)
- Core loop (time → invoice → payment) is already built
- FICA packs partially exist
- No trust accounting needed (unlike law)
- Construction/tech/beauty SME clients exercise different entity types and billing models

## Founder Preferences (Confirmed)
- Start small: 3-person firm, 4 clients, standard services
- Two passes: agent first (functional), founder second (UX judgement)
- Gap report is the deliverable, not fixes
- SA-specific: real field names, real compliance requirements, real terminology
- Accelerated clock: simulated state, not time manipulation

## Phase Roadmap (Updated)
- Phase 45: In-App AI Assistant — BYOAK (specced, not started)
- Phase 46: RBAC Decoupling (nearly complete, 353A/353B remaining)
- **Phase 47: Vertical QA — Small SA Accounting Firm** (spec written)
- Phase 48 (candidate): Gap fix phase based on Phase 47 findings
- Phase 49 (candidate): Vertical profile infrastructure (if gaps confirm skin layer needed)

## Estimated Scope
5 epics: vertical profile creation, 90-day script writing, agent execution pass, founder walkthrough guide, gap consolidation. Code changes limited to pack data + scripts + reports. No production code fixes.
