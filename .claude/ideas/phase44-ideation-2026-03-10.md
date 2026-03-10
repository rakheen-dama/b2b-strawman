# Phase 44 Ideation — Navigation Zones, Command Palette & Settings Modernization
**Date**: 2026-03-10

## Lighthouse Domain
Universal across all verticals. Every SaaS product with 15+ nav items needs information architecture. This is pre-fork foundation — grouping, discoverability, and settings UX benefit every vertical equally.

## Decision Rationale
Founder identified three pain points: (1) sidebar order doesn't make sense and is too flat/overwhelming, (2) users should see less by default but have everything they need when they arrive somewhere, (3) settings layout feels dated compared to modern tools like Claude Desktop. Point (2) was split — "everything you need at the destination" (contextual completeness) deferred as likely fork-specific. This phase focuses on navigation and settings chrome.

### Key Design Choices
1. **6 collapsible zones** — Work, Delivery, Clients, Finance, Team & Resources, plus utility footer (Notifications, Settings). Matches user mental model.
2. **No persistence** — sensible defaults, no localStorage or backend prefs. Founder explicitly questioned whether persistence was needed; decided it's over-engineering.
3. **Command palette (⌘K)** — Shadcn CommandDialog (cmdk). Pages + settings + optional recent entities. Power-user nav without cluttering the sidebar.
4. **Settings sidebar + content pane** — replaces card grid. 6 groups (General, Work, Documents, Finance, Clients, Access). URL routes preserved for bookmarkability.
5. **Contextual completeness deferred** — auditing detail pages for missing actions/info is a separate phase, likely fork-specific.

## Founder Preferences (Confirmed)
- Between B and C scope (grouped sidebar + collapsible + command palette + settings modernization)
- No persistence for collapse state — sensible defaults are enough
- Settings grouping approved (General, Work, Documents, Finance, Clients, Access)
- Contextual completeness on detail pages = separate phase, fork-specific

## Phase Roadmap (Updated)
- Phase 41: Organisation Roles & Capability-Based Permissions (spec written)
- Phase 42: Word Template Pipeline (spec written)
- Phase 43: UX Quality Pass — Empty States, Contextual Help & Error Recovery (spec written)
- **Phase 44: Navigation Zones, Command Palette & Settings Modernization** (spec written)

## Estimated Scope
~4 epics, ~10-12 slices. Frontend-only, no backend changes. Key components: NavGroup config, collapsible sidebar zones, CommandDialog palette, settings layout shell, settings sidebar, mobile adaptations.
