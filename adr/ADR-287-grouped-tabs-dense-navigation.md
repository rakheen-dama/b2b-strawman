# ADR-287: Grouped Tabs Pattern for Dense Navigation

**Status**: Accepted

**Context**:

The matter detail page currently renders 21 tabs in a flat horizontal row using Radix `TabsPrimitive.List`: Overview, Documents, Members, Customers, Tasks, Time, Expenses, Budget, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Disbursements, Statements, Activity, Audit. Module-gating hides some tabs (Court Dates, Adverse Parties, Trust, Disbursements, Statements, Audit), but a legal-za tenant with all modules enabled still sees 15-18 tabs simultaneously.

At 15+ tabs, the horizontal tab bar either wraps onto multiple rows (breaking the visual line) or scrolls horizontally (hiding tabs behind an overflow edge). Neither behavior is acceptable for a page that attorneys visit dozens of times per day. The tab bar should provide orientation ("where am I?") and navigation ("how do I get to Time?") without requiring the user to scan 15+ labels or scroll an overflow container.

The problem will only get worse. Each new phase adds features that tend to surface as tabs on the matter detail page. Phase 55 added Court Dates and Adverse Parties. Phase 57 added Trust. Phase 63 added Disbursements and Statements. Phase 67 added Audit. The current flat-tab pattern has no grouping mechanism — every feature gets a peer-level tab regardless of its conceptual relationship to other features. Time, Expenses, Budget, Rates, Financials, Statements, and Trust are all financial in nature, but they render as 7 undifferentiated tabs alongside Documents and Tasks.

The solution must preserve the existing `?tab=<id>` URL parameter for deep linking and bookmarks, support keyboard navigation (WCAG requirement), work on touch devices, and integrate with the existing module-gating system that conditionally hides tabs based on `OrgProfile` feature flags.

**Options Considered**:

1. **Dropdown tab groups (CHOSEN)** — 5-6 top-level group items in the tab bar. Each group either navigates directly (if it has one visible sub-tab) or opens a dropdown menu listing its sub-tabs. Groups: Overview (standalone), Work (Tasks, Documents, Generated Docs, Staffing), Finance (Time, Expenses, Budget, Rates, Financials, Statements, Trust), Client (Customers, Requests, Client Comments, Adverse Parties), Schedule (Court Dates), Activity (Activity Feed, Audit).
   - Pros:
     - **Reduces visible items from 15-21 to 5-6.** The tab bar fits on a single line at any viewport width. No wrapping, no horizontal scroll.
     - **Semantic grouping aids navigation.** "Finance" is a single scan target. The user clicks Finance, then selects Time — two focused actions instead of scanning 15+ labels for "Time".
     - **Scales to future tabs.** A new financial feature (e.g., "Retainer" tab in a future phase) adds a sub-tab to the Finance group. The top-level tab bar does not change. This decouples feature growth from navigation complexity.
     - **Group-level gating.** If all sub-tabs in a group are module-gated off, the group itself hides. Example: if the `court_calendar` module is off, the Schedule group (which only contains Court Dates) disappears. No empty groups.
     - **Active sub-tab indication.** The group label shows which sub-tab is active: "Finance . Time". This preserves orientation without requiring the dropdown to be open.
     - **Uses standard Shadcn/Radix primitives.** `DropdownMenu` for the dropdown, `Tabs` for the content panels. No custom UI library needed.
     - **URL backward compatibility.** The `?tab=time` URL resolves to the Finance group with Time selected. All 21 existing tab IDs continue to work.
   - Cons:
     - **Two clicks to navigate.** Reaching Time requires clicking "Finance" then clicking "Time". The current flat layout requires one click. Mitigated: the group label click can auto-navigate to the first sub-tab (or the last-active sub-tab in the group), so a single click on "Finance" gets you to Time if Time was the last-active finance sub-tab.
     - **Dropdown menus on touch devices are less discoverable.** Users must know to tap a group to see its sub-tabs. Mitigated: the dropdown arrow icon signals interactivity. Groups with one sub-tab navigate directly (no dropdown).
     - **Group assignment is opinionated.** "Should Expenses be in Finance or Work?" is a product decision that some users may disagree with. Mitigated: group definitions are in code, easily adjusted based on user feedback. The architecture does not hard-code group assignment — it is a configuration array.
     - **Keyboard navigation is more complex.** Arrow keys must navigate between groups, Enter opens the dropdown, then arrow keys navigate sub-tabs. More key presses to reach a target than flat tabs. Mitigated: standard WAI-ARIA Tabs + Menu pattern — screen readers and keyboard users are familiar with this interaction.

2. **Flat tabs with overflow menu** — Show the 6-8 most-used tabs flat in the tab bar. Remaining tabs go behind a "..." (MoreHorizontal) overflow menu at the right edge.
   - Pros:
     - **Most-used tabs are one click.** Overview, Documents, Tasks, Time, Activity — the 5-6 tabs attorneys use most — render directly in the tab bar without a dropdown intermediate.
     - **Simple implementation.** The current flat-tab pattern is preserved for visible tabs. Only the overflow logic is new.
     - **Familiar pattern.** Chrome browser tabs, GitHub repository tabs, and Slack channels all use overflow menus for excess items.
   - Cons:
     - **Which tabs are "most used"?** There is no usage data to inform the cutoff. Hardcoding the visible set means some tenants (trust-accounting-heavy firms) always need the overflow menu for their most-used tab. Per-user customization would solve this but adds significant complexity (backend persistence, drag-to-reorder UI).
     - **Overflow tabs are second-class.** Items behind "..." are harder to find and slower to access. If a legal-za firm uses Trust and Court Dates frequently, those tabs are always behind the overflow — worse than the current flat layout where they are at least visible (even if the bar scrolls).
     - **Does not scale.** The overflow menu grows linearly with features. At 25+ tabs, the overflow menu itself becomes long. There is no semantic grouping to help the user find "Statements" among 15 overflow items.
     - **Active tab may be hidden.** If the user navigates to "Trust" (an overflow tab), the active state is invisible in the main tab bar — only the "..." button shows an active indicator. This hurts orientation.

3. **Vertical sidebar tabs** — Tabs render as a vertical list in the matter sidebar itself. The sidebar becomes a combined metadata + navigation surface. The main content area shows the selected tab's content without a horizontal tab bar.
   - Pros:
     - Vertical space is abundant — all 21 tabs fit without wrapping or overflow.
     - Metadata and navigation are co-located in the sidebar. One surface for everything except content.
     - Vertical tab labels can be longer (full "Client Comments" instead of truncated labels).
   - Cons:
     - **Sidebar becomes overloaded.** Identity, metadata, custom fields, tags, lifecycle action, AND 21 navigation items in a single 280px column. The sidebar would need to scroll significantly, defeating the purpose of having persistent metadata.
     - **Breaks the mental model.** Horizontal tabs for content switching is a universal web pattern. Vertical tabs are uncommon outside of settings pages (which use them differently — as page navigation, not content tabs). Attorneys are not settings-page power users.
     - **Conflicts with sidebar collapse.** If the sidebar is collapsed, the tab navigation disappears. The user cannot switch tabs without expanding the sidebar first. This is a fundamental UX regression — tab switching is the primary interaction on this page.
     - **No grouping inherent in vertical lists.** A vertical list of 21 items is no better than a horizontal list of 21 items. Grouping would need to be added as collapsible sections within the sidebar, creating a nested navigation structure that is harder to scan than a horizontal grouped tab bar.

4. **Segmented navigation (nested tab bar)** — Top-level horizontal segments (Overview, Work, Finance, Client, Schedule, Activity) as a first row. A second row of sub-tabs for the active segment. Two rows of horizontal tabs.
   - Pros:
     - **Both levels are always visible.** The user sees the segment (Finance) and the sub-tab (Time) simultaneously. No dropdown required.
     - **One click to switch sub-tabs within a segment.** Moving from Time to Budget is a single click on the second row — same as the current flat layout for adjacent tabs.
     - **Familiar from multi-level navigation.** GitHub repository settings, AWS console, and many admin panels use this pattern.
   - Cons:
     - **Two rows of tabs consume vertical space.** The tab bar goes from ~44px (one row) to ~88px (two rows). On a 768px viewport, 88px of tab chrome is significant. Combined with the breadcrumb row, 120px+ of navigation chrome sits between the sidebar and the content.
     - **The second row changes.** When the user clicks from "Finance" to "Work", the second row completely replaces its content (Time/Expenses/Budget/Rates/Financials/Statements/Trust -> Tasks/Documents/Generated Docs/Staffing). This animated content swap in the navigation area is disorienting — it feels like the page is reloading.
     - **Segments with one sub-tab waste a row.** Overview (standalone) and Schedule (one module-gated sub-tab) would show a second row with a single item. This looks broken — a tab bar with one tab is not useful.
     - **More complex responsive behaviour.** Two rows of tabs need to adapt to narrow viewports. The first row might overflow, the second row might overflow independently. Handling two overflow contexts is more complex than handling one dropdown-based tab bar.

**Decision**: Option 1 — Dropdown tab groups with 5-6 top-level items and dropdown sub-navigation for groups with multiple visible sub-tabs.

**Rationale**:

The fundamental requirement is to reduce 15-21 visible tab items to a scannable set of 5-6 without hiding tabs behind an unprioritized overflow. Dropdown tab groups achieve this by adding semantic structure — Finance, Work, Client — that flat tabs and overflow menus lack.

The overflow menu (Option 2) was the closest runner-up. It has a simpler implementation and preserves one-click access to the most-used tabs. However, it fails the scalability test: as Kazi adds features, the overflow menu grows unbounded, and there is no principled way to decide which tabs are "most used" without per-user analytics or customization infrastructure. The grouped approach is deterministic — every tab has a group, every group has a semantic label, and the structure does not change as tabs are added (new tabs are assigned to existing groups or a new group is created).

The vertical sidebar tabs (Option 3) was rejected because it conflicts with the sidebar's primary role as a metadata surface and breaks when the sidebar is collapsed. Tab switching is the page's primary interaction — it must not depend on sidebar state.

The segmented navigation (Option 4) was rejected because two rows of horizontal tabs consume too much vertical space and the row-swapping interaction is disorienting. The dropdown approach uses one row with on-demand expansion, which is more space-efficient and less disorienting.

The threshold for grouping is **>8 visible tabs**. Below 8, flat tabs fit on a single line at 1024px+ and grouping adds unnecessary interaction complexity. Above 8, grouping becomes necessary. The matter detail page's 15-21 tabs far exceed this threshold. If the customer detail page (currently ~8 tabs) grows past this threshold in a future phase, the same grouped pattern should be applied.

**Consequences**:

- Positive:
  - Visible tab items reduced from 15-21 to 5-6. Tab bar fits on a single line at all viewport widths.
  - Semantic grouping aids navigation. Users scan 5-6 group labels instead of 15-21 individual labels.
  - Future tabs are added to existing groups, not to the top-level bar. Navigation complexity does not grow linearly with features.
  - Group-level gating: if all sub-tabs in a group are module-gated off, the group hides. No empty groups clutter the tab bar.
  - All 21 existing `?tab=<id>` URLs continue to resolve correctly. No broken deep links or bookmarks.

- Negative:
  - Two clicks to reach a sub-tab (click group, click sub-tab) instead of one click on a flat tab. Mitigated by auto-navigating to the first sub-tab on group click and showing the active sub-tab label in the group trigger.
  - Group assignment is a product decision that may need iteration. Initial grouping is based on conceptual similarity (all financial tabs in Finance), but user feedback may suggest different groupings.
  - Keyboard navigation requires more key presses: ArrowRight to reach group, Enter to open, ArrowDown to reach sub-tab, Enter to select. Standard WAI-ARIA pattern but more complex than flat tabs.

- Neutral:
  - Tab group definitions are a TypeScript constant array in `grouped-tab-bar.tsx`. Changing group assignment (e.g., moving "Staffing" from Work to a new "Team" group) is a one-line edit.
  - The `GroupedTabBar` component uses Radix `DropdownMenu` for dropdowns. The underlying `TabsPrimitive.Root` / `TabsPrimitive.Content` pattern is preserved — only the trigger row changes.
  - The `members` tab ID is mapped to `staffing` for backward compatibility. The Members panel content is accessible via Work > Staffing.
  - Groups with exactly one visible sub-tab render as standalone tabs (no dropdown arrow, no dropdown menu). This avoids the "dropdown with one item" anti-pattern for Schedule (Court Dates only) and Activity (Activity Feed only, when Audit is gated off).
  - The active sub-tab is indicated in the group trigger label: "Finance . Time". This uses a `text-muted-foreground` dot separator to distinguish the group name from the active sub-tab name. When no sub-tab in the group is active, only the group name renders.

- Related: [ADR-286](ADR-286-sidebar-layout-entity-detail.md) (sidebar layout — grouped tabs complement the sidebar by keeping the main content area focused on content, not navigation chrome)
