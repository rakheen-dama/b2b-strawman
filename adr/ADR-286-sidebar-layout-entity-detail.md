# ADR-286: Sidebar vs Full-Width Layout for Entity Detail Pages

**Status**: Accepted

**Context**:

The matter detail page (`/org/[slug]/projects/[id]`) is Kazi's most-visited page. Over 72 phases it has grown to include a header (name, status, description, due date, customer, promoted fields, action buttons), custom fields (grouped sections with save button), tags, and 21 tabbed content panels. Everything renders in a single vertical column: header -> custom fields -> tab bar -> tab content.

This layout has three structural problems. First, custom fields consume the viewport. A matter with SA Legal field groups (Case Number, Court, Opposing Party, Opposing Attorney, Date of Instruction, Estimated Value) pushes the tab bar below the fold — the tab bar being the thing attorneys interact with most frequently. Second, the header section uses `flex-1` with `min-w-0` for the matter name, which gets squeezed by 6-8 action buttons on the right side. Long matter names like "Sipho Dlamini v Road Accident Fund --- High Court Johannesburg --- Case No 2026/12345" collapse into a single-word-width column where each word renders on its own line, consuming 2+ screens of vertical space while the right 70% of the page is empty. Third, the page has no scroll independence — scrolling to tab content scrolls the header and custom fields off screen, and scrolling back to custom fields requires scrolling past tab content.

Practice-management competitors (Clio, PracticePanther) and productivity tools (Linear, Notion, Jira) solve this with sidebar layouts: identity and metadata in a fixed-width sidebar, main content area for the primary interaction surface (tabs, editor, board). The sidebar scrolls independently, keeping metadata accessible without competing with content for vertical space. Kazi needs a layout pattern for entity detail pages that scales to the information density of professional-services verticals without degrading the page as modules and fields are added.

**Options Considered**:

1. **Sidebar + main content (CHOSEN)** — 280px fixed-width collapsible sidebar for identity, metadata, custom fields, tags, and primary lifecycle action. Fluid main area for breadcrumb, grouped tab bar, and tab content. CSS Grid layout. Sidebar collapses to 0px on desktop (toggle), renders as a Sheet on mobile.
   - Pros:
     - **Tab bar is always above the fold.** Custom fields move to the sidebar, so the tab bar renders at the top of the main area regardless of how many field groups are applied. This is the core UX win.
     - **Fixed-width sidebar eliminates the squeezing problem.** At 280px, matter names wrap predictably (3 lines max with line-clamp). No more single-word-per-line rendering caused by action buttons competing for horizontal space.
     - **Scroll independence.** Sidebar and main content scroll separately. Scrolling through tab content does not scroll metadata off screen. Scrolling through custom fields does not scroll tab content.
     - **Established pattern.** Clio Manage, Linear, Notion all use sidebar layouts for entity detail pages. Users recognize the pattern. No learning curve.
     - **Extensible.** Future entity detail pages (customer, invoice) can reuse the layout shell. The `MatterDetailLayout` component is generic — pass sidebar content and main content as slots.
     - **Works with existing Shadcn/Radix components.** `Sheet` for mobile, `Collapsible`/`Accordion` for field groups, `Tooltip` for truncated names.
   - Cons:
     - **280px width constrains custom field rendering.** Field labels and values must fit in 280px. Long dropdown values may truncate. Textarea fields render narrow. Mitigation: fields that need more space (long text, rich text) can have an "expand" affordance or link to a full-width editor.
     - **More complex component tree.** The current `page.tsx` renders everything inline. The sidebar layout requires `MatterDetailLayout`, `MatterSidebar`, and careful prop distribution. More components to maintain.
     - **Hydration layout shift.** Sidebar collapse state lives in localStorage (client-only). Server render always shows expanded sidebar. If localStorage says collapsed, the sidebar collapses on hydration — a brief layout shift. Acceptable for v1; fixable with cookie-based state in a future phase.

2. **Full-width stacked layout (current)** — Everything in a single vertical column. Header, custom fields, tab bar, tab content stacked top to bottom.
   - Pros:
     - **Simple component tree.** No layout shell, no sidebar state, no collapse logic. Everything renders inline in `page.tsx`.
     - **Full width for all content.** Custom fields, tab content, and headers all use the full viewport width. No width constraints on any element.
     - **No responsive complexity.** The page is already single-column on all viewports. No Sheet, no breakpoint logic, no collapse toggle.
   - Cons:
     - **Tab bar below the fold.** Custom fields push the tab bar down. This is the fundamental problem that triggered this ADR.
     - **Squeezing problem.** Long matter names and descriptions collapse into narrow columns when action buttons compete for horizontal space.
     - **No scroll independence.** Everything scrolls together. Scrolling to tab content scrolls metadata off screen.
     - **Does not scale.** Each new field group or module-gated tab makes the page longer. The page has only gotten worse over 72 phases, never better.

3. **Two-panel resizable layout** — Draggable divider between sidebar and content, like VS Code or Finder column view. User can resize the sidebar to any width.
   - Pros:
     - Maximum flexibility. Users with many custom fields can widen the sidebar; users with few can narrow it.
     - Power-user appeal — feels like a professional tool.
   - Cons:
     - **Over-engineering for the use case.** Attorneys are not IDE users. They need to see fields and tabs, not customize panel widths. The extra interaction surface (drag divider) adds complexity without proportional value.
     - **No Shadcn primitive for resizable panels.** Requires a third-party library (e.g., `react-resizable-panels`) or custom implementation. More dependencies, more maintenance.
     - **State management complexity.** User-configured width must be persisted per user, per entity type, per device. localStorage alone is insufficient for consistent behavior.
     - **Responsive breakpoints are harder.** A user-configured sidebar width may conflict with responsive breakpoints. If a user sets the sidebar to 500px on a 1024px screen, the main content gets 524px — too narrow for tab content.

4. **Top card + full-width tabs** — Compact card at the top of the page with matter identity and key metadata (1-2 rows). Custom fields collapse into an expandable section within the card. Tabs render full-width below the card.
   - Pros:
     - **Simpler than a sidebar.** No grid layout, no collapse state, no Sheet on mobile. The card is just a constrained `<div>` at the top.
     - **Full width for tab content.** Tabs and panels use the entire viewport width, preserving the current tab rendering behavior.
     - **Custom fields are below the fold by default.** The card shows them collapsed, so the tab bar is visible on page load.
   - Cons:
     - **Custom fields are hidden.** Collapsing them into a card means they are out of sight. Attorneys who frequently reference custom fields (case number, court name, opposing party) must click to expand every time. The sidebar keeps them persistently visible.
     - **No scroll independence.** The card scrolls with the page. Scrolling tab content scrolls the card off screen — the same problem as the current layout, just less severe because the card is shorter.
     - **Action buttons still need relocation.** The card header has the same squeezing problem if action buttons are placed alongside the matter name. Requires an overflow menu regardless.
     - **Not extensible to other entity types.** The card pattern is specific to entity headers. A sidebar layout establishes a reusable pattern for customer detail, invoice detail, etc.

**Decision**: Option 1 — Sidebar + main content with a 280px fixed-width collapsible sidebar, CSS Grid layout, Sheet on mobile.

**Rationale**:

The core problem is that custom fields and action buttons compete with the tab bar for viewport space, and the tab bar always loses. Every option except the current layout (Option 2) solves this by separating metadata from content. The question is which separation pattern best serves Kazi's use case.

The sidebar layout (Option 1) wins because it provides scroll independence — the key interaction pattern for a page that attorneys visit repeatedly throughout the day. An attorney checking a court date should not have to scroll past custom fields to reach the Schedule tab, and an attorney editing the case number should not have to scroll past tab content to reach the custom fields. The sidebar keeps both surfaces accessible simultaneously. The top-card layout (Option 4) does not provide scroll independence — it just makes the problem shorter.

The resizable layout (Option 3) is over-engineered. The sidebar width is a design decision, not a user preference. 280px is wide enough for field labels + values, narrow enough to leave ample main content space on 1024px+ screens. There is no user scenario where a draggable divider provides meaningful value over a fixed-width sidebar with a collapse toggle.

The 280px width is chosen to match Clio Manage's detail sidebar and Linear's issue sidebar. It accommodates SA Legal field labels (which are longer than typical SaaS field labels due to legal terminology) while leaving 744px+ for main content on a 1024px screen — enough for all tab panels to render without horizontal scroll.

**Consequences**:

- Positive:
  - Tab bar is always above the fold. Custom fields in the sidebar do not push tabs down.
  - Scroll independence: sidebar and main content scroll separately. No more scrolling past fields to reach tabs.
  - Fixed 280px width eliminates the squeezing problem. Matter names wrap predictably within a fixed column.
  - Establishes a reusable layout pattern for future entity detail pages (customer, invoice, proposal).
  - Mobile experience improves: Sheet overlay is a cleaner pattern than a long single-column page.

- Negative:
  - Custom fields must render within 280px. Long dropdown values and textarea fields may need expand-on-demand affordances. This is a design constraint that future custom field types must accommodate.
  - Hydration layout shift when sidebar collapse state differs between server render (expanded) and localStorage value (collapsed). Acceptable for v1; fixable with cookie-based state.
  - Component tree complexity increases. `page.tsx` no longer renders everything inline — it distributes data to `MatterDetailLayout`, `MatterSidebar`, and `OverflowActionsMenu`.

- Neutral:
  - Sidebar width is defined as a CSS custom property (`--sidebar-width: 280px`) in `globals.css`. Changing the width is a single-line edit.
  - Collapse state persisted in `localStorage` key `kazi-matter-sidebar-collapsed`. Per-device preference, not synced across devices.
  - The `MatterDetailLayout` component accepts `sidebar: ReactNode` and `children: ReactNode` slots — reusable for customer detail page in a future phase.
  - The existing `desktop-sidebar.tsx` (main navigation, 256px) and the new matter sidebar (280px) are independent components. They do not share state or CSS variables. On desktop, the page layout is: nav sidebar (256px) + matter sidebar (280px) + main content (fluid). Total fixed width consumed: 536px, leaving 488px+ on a 1024px screen — tight but workable. At 1280px+ (the common desktop resolution), main content gets 744px+.

- Related: [ADR-287](ADR-287-grouped-tabs-dense-navigation.md) (grouped tabs reduce visual noise in the main content area, complementing the sidebar's information architecture)
