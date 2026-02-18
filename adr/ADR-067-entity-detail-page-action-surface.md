# ADR-067: Entity Detail Pages as Contextual Action Surface

**Status**: Accepted

**Context**:

Phase 15 introduces contextual actions — surfacing what's missing on a project or customer and providing direct links to fix it. These actions need a home in the UI. The platform already has two dashboard surfaces (Phase 9): a company-wide dashboard with health scoring and a personal "My Work" dashboard. Entity detail pages (project detail with 11 tabs, customer detail with 6 tabs) are the existing deep-dive surfaces for individual entities.

The question is where to place setup guidance and contextual actions: on a central "action items" dashboard, on entity detail pages, or both.

**Options Considered**:

1. **Entity detail pages only (chosen)** — Setup progress cards, unbilled time prompts, and document generation readiness appear on the project and customer detail pages (Overview tab or equivalent). No dashboard modifications.
   - Pros: Zero context switching — user is already looking at the entity when they see what's missing; action links navigate within the same page (to other tabs) or open dialogs in-place; respects existing navigation structure; no dashboard redesign; progressive disclosure (cards hide when complete, so experienced users aren't burdened); each entity's guidance is self-contained.
   - Cons: User must navigate to each entity individually to see its status; no cross-entity "here are all your incomplete projects" view; doesn't help with discovery of entities that need attention.

2. **Central action-items dashboard** — New dashboard page showing all projects with incomplete setup, all customers needing attention, all unbilled time across the org. Clickable items navigate to the entity.
   - Pros: Single view of everything that needs attention; enables prioritization across entities; natural home for "what should I do next?" workflow.
   - Cons: Requires navigating away from the entity to discover issues, then navigating back to fix them — two context switches per action; duplicates some Phase 9 dashboard functionality (health scores already flag underperforming projects); requires maintaining a cross-entity aggregation query (computing setup status for all projects, not just one); adds a new top-level page to an already feature-rich navigation.

3. **Both dashboard and detail pages** — Show a summary on a dashboard with drill-through to detail pages that show the full breakdown.
   - Pros: Discovery via dashboard, resolution via detail page; covers both workflows (proactive scanning and reactive fixing).
   - Cons: Double the implementation surface; dashboard and detail page must stay in sync; risk of information overload (user sees the same warnings in two places); significantly more frontend work for marginal benefit; Phase 9's health scoring already provides the "proactive scanning" role.

**Decision**: Option 1 — entity detail pages only.

**Rationale**:

The primary use case for contextual actions is reactive: a user opens a project, sees that the rate card isn't configured, and configures it — all without leaving the page. This workflow has the shortest cognitive loop: see problem → understand problem → fix problem, with zero navigation. A dashboard-based approach would require: see summary → click through → arrive at entity → find the relevant tab → fix problem — a 3-step journey vs. a 1-step journey.

The proactive use case ("which projects need attention?") is already served by Phase 9's company dashboard, which includes project health scores, budget status indicators, and team workload metrics. Adding a parallel "setup completeness" dashboard would create redundancy and potentially conflicting signals (health score says "good" but setup says "incomplete" because one optional field isn't filled).

Progressive disclosure is key to avoiding notification fatigue. Setup cards on detail pages auto-collapse when all steps are complete — experienced users never see them. A dashboard, by contrast, would always show a list (even if empty), taking up screen real estate for a feature that's most valuable during initial setup and diminishes over time.

The "both" option (Option 3) was considered but rejected because it roughly doubles frontend implementation scope without proportional user value. If cross-entity setup visibility becomes a requirement in a future phase, it can be added as a dashboard widget that links to detail pages — building on top of the detail-page foundation established here rather than replacing it.

**Consequences**:

- Positive:
  - Zero modifications to existing dashboards (Phase 9) — no regression risk
  - Shortest possible cognitive loop for setup actions (see → fix on the same page)
  - Progressive disclosure: cards auto-hide when complete, reducing noise for power users
  - Implementation scope contained to detail page components — no new routes or navigation entries
  - Reusable components (`SetupProgressCard`, `ActionCard`) work on both project and customer detail pages
- Negative:
  - No cross-entity "all incomplete projects" view — user must visit each project individually (mitigated by Phase 9 health scores flagging at-risk projects)
  - New users don't discover which entities need setup attention until they navigate to them (mitigated by dashboard health indicators and the natural onboarding flow of creating projects one at a time)
  - If a future phase adds a "setup completeness" column to project/customer list views, it would require computing status per-row (a lightweight batch query, not a fundamental architecture change)
