# ADR-047: Dashboard Layout — Opinionated Fixed Layout

**Status**: Accepted

**Context**: Phase 9 introduces three dashboards: the Company Dashboard (org-level KPIs, project health, team workload), the Project Overview tab (per-project health summary), and the Personal Dashboard (individual utilization and task list enhancement). Each dashboard aggregates data from multiple widgets into a single page.

The layout question is whether users should be able to customize which widgets they see, reorder them, resize them, or whether the platform should ship with a single opinionated layout that works well for the target use case.

The platform serves professional services organizations (consulting firms, agencies, internal project teams) with team sizes of 5-50 members. The typical dashboard audience is a project manager, team lead, or org admin who needs quick situational awareness before diving into specific projects or tasks.

**Options Considered**:

1. **Configurable widget-based dashboard with persistence** -- Users can add, remove, reorder, and resize widgets on their dashboard. Layout preferences are persisted per user in a `dashboard_preferences` table. Widget library with a grid layout system (e.g., react-grid-layout).
   - Pros:
     - Maximum flexibility — each user sees exactly what they care about
     - Users who do not care about budget can hide the budget widget; users who care about activity more than health can promote it
     - Industry-standard pattern in enterprise dashboards (Salesforce, Jira, Datadog)
     - Can serve as a platform feature differentiator ("build your own dashboard")
   - Cons:
     - **Significant implementation effort**: Drag-and-drop grid layout, widget persistence, widget library UI, default templates, migration when new widgets are added in future phases, responsive behavior with user-defined grids.
     - **New persistence requirement**: `dashboard_preferences` table (tenant-scoped) with per-user JSONB column storing widget configuration. New migration, new entity, new CRUD endpoints.
     - **Testing surface area explosion**: Each widget must work in any position, any size, and any combination with other widgets. A 5-widget dashboard with 3 possible sizes each has combinatorial test permutations.
     - **Blank slate problem**: New users see an empty or default dashboard and must configure it before getting value. Most users never customize and are worse off than with a curated default.
     - **Mobile complexity**: User-configured desktop layouts must somehow translate to mobile without overlapping or hiding content. This is a hard UX problem.
     - **react-grid-layout** or similar adds ~30KB gzipped to the bundle for a feature most users will not use.
     - The Company Dashboard has 4 widgets. The configuration overhead (adding/removing/resizing) is disproportionate to the widget count. Configurability makes sense when there are 20+ widgets; with 4, it adds friction without meaningful personalization.

2. **Opinionated fixed layout** -- A single, carefully designed layout that works for all users. Widgets are fixed in position and size. The only user control is the date range selector (which adjusts the data, not the layout) and filter tabs on the project health list.
   - Pros:
     - **Ship fast**: No persistence layer, no drag-and-drop, no layout engine, no user preference management. The layout is a CSS grid in a server component.
     - **Consistent experience**: Every user sees the same dashboard, making it easy to reference in documentation, support, and team discussions ("the project health list on the left").
     - **Optimized for the common case**: The layout follows the F-reading pattern (most important info in the top-left quadrant). KPIs across the top for immediate situational awareness, project health list on the left (primary action area for project managers), team workload and activity on the right (supporting context).
     - **Responsive by design**: A fixed layout with known breakpoints (desktop: 2-column, tablet: 1-column, mobile: stacked) can be optimized and tested exhaustively.
     - **No blank slate problem**: The dashboard is useful from the first visit. Users see KPIs immediately, even if most values are zero — the structure itself communicates what data the platform can provide.
     - **Future upgrade path**: If user feedback reveals strong demand for customization, the fixed layout becomes the "default template" in a configurable system. No existing users lose their dashboard — they start with the opinionated default and can modify it.
   - Cons:
     - Users who do not care about certain widgets (e.g., team workload for a solo consultant) cannot hide them. They see widgets with "only your data" or empty states.
     - Power users in large organizations may want different widgets than the default set — e.g., a CFO might want a financial dashboard, not an operational one.
     - The layout reflects the architect's priorities, not each user's. If the KPI ordering is wrong for a specific org, they cannot reorder.
     - Adding a new widget in a future phase requires changing the layout for everyone — though this is arguably a feature (new capability is visible to all users without opt-in).

3. **Role-based preset layouts (different layout per role)** -- Ship 2-3 preset layouts optimized for different roles: "Executive" (financial KPIs, high-level health), "Manager" (full dashboard with workload and health), "Individual" (personal metrics and task list). The layout is determined by the user's org role.
   - Pros:
     - More targeted than a single layout — admins see financial metrics prominently; members see personal productivity metrics prominently
     - No user configuration needed — the role determines the layout
     - Three layouts are still testable and maintainable (vs. infinite combinations with full configurability)
   - Cons:
     - The existing role model has three roles: owner, admin, member. Mapping these to dashboard presets creates artificial constraints — what if an admin wants the "individual" view? There is no role-switching mechanism.
     - Three layouts means three times the frontend code, three times the testing, and three times the design review. The marginal value over a single layout with role-based visibility toggling (hiding financial KPIs for members) is small.
     - Role-based layouts conflate authorization (what you CAN see) with preference (what you WANT to see). Phase 9 already handles authorization by nulling financial fields for non-admins. The layout should not change — the data within it should.
     - Future role additions (e.g., "billing_admin", "project_manager") would require new preset layouts or complex role-to-layout mapping.

**Decision**: Opinionated fixed layout (Option 2).

**Rationale**:

1. **Lean and observable**: The platform is in its early growth phase with a known target persona (professional services team leads and admins). Shipping an opinionated layout and observing how users interact with it provides more useful data than guessing what users want to customize. If 80% of users never scroll past the project health list, that tells us the layout is correct. If 80% of users click "View all projects" immediately, that tells us the list is too short. Neither insight is available if every user has a different layout.

2. **F-reading pattern optimization**: The fixed layout places KPIs (aggregate numbers) in the top row — the first thing users see. Project health occupies the left column (wider) because it is the primary action trigger: a CRITICAL project badge drives the user to click into that project. Team workload and activity are supporting context on the right. This follows established information density patterns for operational dashboards and cannot be improved by user rearrangement — it can only be degraded by moving primary content to secondary positions.

3. **Role-based visibility, not role-based layout**: The permission model already handles the key personalization need: members do not see financial KPIs (Billable %, Avg. Margin). This is data-level visibility, not layout-level customization. The layout remains the same — the card either shows a value or is hidden. This approach is simpler and more maintainable than separate layouts per role.

4. **Implementation cost**: A configurable dashboard (Option 1) requires: a persistence table, a CRUD API for preferences, a drag-and-drop library, a widget registry, default templates, migration handling for new widgets, responsive grid calculations for user-defined layouts, and testing for every permutation. The total effort is estimated at 2-3 additional epics (6-9 slices) on top of the 6 slices already planned for Phase 9. The opinionated layout is a CSS grid — it is part of the page component, not a separate system.

5. **Future upgrade path is clean**: If user feedback after Phase 9 deployment reveals strong demand for customization, the opinionated layout becomes the "Default" template. A future "Dashboard Customization" phase adds a `dashboard_preferences` table and a configuration UI, with the current layout as the starting point. No existing users lose functionality — they gain the ability to modify what was previously fixed. This is a much lower-risk evolution than building configurability first and trying to find the right defaults later.

**Consequences**:
- All users see the same dashboard layout. The only controls are the date range selector and the project health filter tabs ("All" / "At Risk" / "Critical").
- Financial KPI cards (Billable %, Avg. Margin) are hidden for non-admin/non-owner users. The remaining 3 cards expand to fill the row. This is the only layout variation.
- No `dashboard_preferences` table or entity. No user preference API for dashboard layout.
- The Company Dashboard layout is a CSS grid: `grid-cols-1 lg:grid-cols-5` with the project health list spanning 3 columns and the right sidebar spanning 2 columns. Responsive breakpoints are at 768px (tablet) and 1024px (desktop).
- Adding a new widget in a future phase requires changing the dashboard page component and its CSS grid. This is a deliberate choice — new widgets should be considered carefully, not proliferated via a widget marketplace.
- If customization is needed in the future, the migration path is: (1) add `dashboard_preferences` table, (2) default new users to the current fixed layout serialized as JSON, (3) add a "Customize" button that enables drag-and-drop editing of the existing layout. The fixed layout becomes the default template.
