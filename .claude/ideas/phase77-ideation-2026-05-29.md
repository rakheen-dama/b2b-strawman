# Phase 77 Ideation — Customer Detail Page Redesign
**Date**: 2026-05-29

## Catalyst
Founder flagged screenshots showing the customer detail page has the exact same layout problems the matter detail page had before Phase 73: 7 action buttons sprawled horizontally, vertical metadata wall (Address → Contact → Business Details → Fields → Tags → Setup Cards) before tabs are visible, 11 flat tabs with no grouping.

## Decision
**Header card + grouped tabs pattern.** Adopt the post-Phase-73 final evolution (not the intermediate sidebar version). Compact `ClientHeaderCard` at top, 5 grouped tab groups via shared `GroupedTabBar`, overflow menu for actions.

## Key design choices (founder-selected)
1. **Header card + tabs** over sidebar — the sidebar was an intermediate state in Phase 73 that was later replaced. Go straight to the final pattern.
2. **5 tab groups** — Details (address/contact/biz/fields/tags), Overview (readiness/unbilled/retainer/AI), Work (projects/docs/generated), Finance (invoices/rates/retainer/financials/trust), Compliance (onboarding/requests), Activity (audit).
3. **Overview tab with setup guidance** — setup cards, unbilled time, retainer status, lifecycle prompts, and AI panels all move into an Overview tab (default landing tab).
4. **Overflow menu + 1 smart primary action** — lifecycle-aware primary button (Prospect→"Start Onboarding", Onboarding→"Activate", Active→"Edit"), everything else in `...` overflow dropdown.

## What was explicitly rejected
- Sidebar layout (Phase 73 intermediate state)
- Hybrid header card + sidebar (over-engineered)
- 4 groups (merging Compliance into Details — too large)
- 6 groups with separate Trust (unnecessary elevation)
- Keeping setup cards inline above tabs (defeats the purpose)
- 2 visible action buttons (1 primary is cleaner)

## Scope constraint
Frontend-only. No backend changes, no new APIs. Pure layout restructure of existing components. Shared `GroupedTabBar` extraction from projects domain.

## Next step
`/architecture requirements/claude-code-prompt-phase77.md`
