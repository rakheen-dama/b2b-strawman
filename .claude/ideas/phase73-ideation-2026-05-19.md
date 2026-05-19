# Phase 73 Ideation — Matter Detail Page Redesign
**Date**: 2026-05-19

## Catalyst
Founder flagged two screenshots showing the matter detail page's layout problems:
1. Custom fields consume the viewport before tabs are visible
2. Long matter names/descriptions collapse into single-word-per-line columns (70% of screen empty)
3. 21 flat tabs create visual clutter
4. Activity feed and overview content push actionable content below the fold

## Decision
**Sidebar + main content layout with grouped tabs.** Inspired by Clio/Linear's entity detail pattern.

## Key design choices (founder-selected)
1. **Sidebar (280px, collapsible)** over compact-header or top-card approaches — matter identity and custom fields live in sidebar, main area is 100% tabbed content
2. **Grouped tabs (5-6 dropdowns)** over pruned flat tabs or smart-per-type tabs — Work, Finance, Client, Schedule, Activity groups
3. **KPI dashboard overview** — health ring + 4-6 metric cards, no activity feed/team/task breakdown (those have their own tabs)
4. **Actions in sidebar bottom + overflow menu** — primary lifecycle button in sidebar footer, secondary actions in `...` dropdown top-right of main area

## Scope constraint
Frontend-only. No backend changes, no new APIs, no new entities. Pure layout restructure of existing components.

## What the founder explicitly rejected
- Removing Overview entirely (sidebar-as-overview approach)
- Command palette / shortcut-only actions (too hidden)
- Smart per-type tabs (too complex to build)

## Next step
`/architecture requirements/claude-code-prompt-phase73.md`
