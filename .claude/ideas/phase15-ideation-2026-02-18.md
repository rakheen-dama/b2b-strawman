# Phase 15 Ideation — Contextual Actions & Setup Guidance
**Date**: 2026-02-18

## Lighthouse Domain
- SA small-to-medium law firms (unchanged)
- This phase is vertical-agnostic — improves UX regardless of fork

## Decision Rationale
Founder identified "Intuitive UX" as the direction. Three pain points surfaced:
1. **Disconnected seeded fields** — custom fields exist but aren't surfaced naturally
2. **No obvious doc generation path** — templates exist but workflow to use them isn't intuitive
3. **Too many clicks, no guidance** — no indication of what to do next or what's missing

**Chosen**: Contextual Actions & Setup Guidance — a pure aggregation + UI phase.

**Key design decisions from conversation**:
- **Hardcoded checks over configurable engine** — founder questioned whether more configurability adds complexity. Agreed that 3 configurable engines (fields, checklists, templates) is enough; setup status just reads from them.
- **Entity detail pages, not dashboard** — contextual actions live where the user is already looking
- **Actions + lightweight field surfacing** — show field values on detail pages (read-only) but don't rework create/edit forms
- **No dashboard changes** — keep existing dashboards as-is

## Key Design Preferences (from founder)
1. Contextual actions > guided setup > inline fields (priority order)
2. Avoid over-configurability — the platform already has enough configurable engines
3. Entity detail pages are the right surface for this UX
4. Keep scope tight — no form rework, no dashboard changes

## Phase Roadmap (updated)
- Phase 14: Customer Compliance & Lifecycle (in progress)
- Phase 15: Contextual Actions & Setup Guidance (requirements written)
- Phase 16+: Candidates — Org Integrations (BYOAK), Customer Portal Frontend, Recurring Work/Retainers, Reporting & Export

## Architecture Notes
- **No new DB tables** — pure read/aggregation layer
- Reusable components: SetupProgressCard, ActionCard, EmptyState, FieldValueGrid
- Backend: ProjectSetupStatusService, CustomerReadinessService, UnbilledTimeSummaryService, DocumentGenerationReadiness
- All endpoints are GET-only (read-only aggregation)
