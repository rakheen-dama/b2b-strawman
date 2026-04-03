# Visual Polish Audit — Findings Report

Analyzed 22 routes across 24 screenshots at 1440x900 (light mode, org app only).

---

## Cross-Page Issues (Shared Components)

### 1. Page Title Spacing Inconsistency
- **Dimension:** spacing
- **Severity:** medium
- **Affected Pages:** Dashboard, My Work, Invoices, Proposals, Retainers, Calendar, Compliance, Resources, Schedules, Team
- **Root Cause:** page-specific — no shared page header component; each page implements its own title/subtitle + action button layout
- **Observation:** The gap between page title and the first content element varies significantly:
  - Dashboard: title has subtitle + period tabs inline → ~24px to KPI cards
  - Invoices: title with tabs below → tight spacing to KPI summary cards
  - Proposals: title + count badge + "New Proposal" button → then KPI cards → then info bar → then filter tabs → then table. Multiple spacing zones, feels busy.
  - Retainers: title + count + button → KPI cards → double filter row (pill filter + tab row). Two separate filter mechanisms stacked.
  - Team: title + count badge → invite card → tab row → table. Good flow.
  - Calendar: title + subtitle → filter row → month/list tabs → calendar grid. Clean.
  - Resources: title + subtitle + link → filter row → grid. Clean.
- **Proposed Fix:** Standardize the title-to-first-content gap across all pages. Consider a shared `<PageHeader>` component or at least consistent Tailwind spacing classes.

### 2. KPI Card Height/Padding Inconsistency
- **Dimension:** rhythm, spacing
- **Severity:** medium
- **Affected Pages:** Dashboard (6 KPI cards), Proposals (4 cards), Retainers (3 cards), Compliance (5 cards), Invoices (3 summary cards)
- **Root Cause:** shared component (likely different card layouts per page)
- **Observation:**
  - Dashboard KPI cards: compact, `text-xs` label + large number, consistent height
  - Proposals KPI cards: similar but with icon in top-right, slightly different padding
  - Retainers KPI cards: taller, more padding, icon in top-right
  - Compliance lifecycle cards: horizontal icon + number + label layout (different from others)
  - Invoice summary cards: colored top border variant, different height
- **Proposed Fix:** Audit all KPI/stat card implementations. They should share consistent height, padding, and typography scale even if content varies.

### 3. Filter Tab Styling Inconsistency
- **Dimension:** alignment, rhythm
- **Severity:** medium
- **Affected Pages:** Customers, Invoices, My Work, Proposals, Retainers, Schedules, Dashboard (Project Health)
- **Root Cause:** multiple filter patterns used interchangeably
- **Observation:** At least 3 different filter tab styles:
  - **Pill filters** (rounded-full, filled active state): Customers ("All", "Prospect", etc.), Invoices ("All", "Draft", etc.), Calendar ("All", "Tasks", "Projects")
  - **Underline tabs**: Proposals ("All", "Draft", "Sent"), Team ("Members", "Pending Invitations"), Invoices page-level ("Invoices", "Billing Runs")
  - **Plain text tabs**: Dashboard Project Health ("All", "At Risk", "Over Budget")
  - **Double filter rows**: Retainers has BOTH a pill filter row AND a tab row underneath — visually heavy
- **Proposed Fix:** Choose one filter tab pattern and use it consistently. Pill filters for data filtering, underline tabs for sub-navigation. Remove the double filter pattern on Retainers.

### 4. Table Header Typography Inconsistency
- **Dimension:** typography
- **Severity:** low-medium
- **Affected Pages:** All pages with tables (Customers, Invoices, Proposals, Team, Resources, Settings/Rates, My Work)
- **Root Cause:** shared Table component, but inconsistent usage
- **Observation:**
  - Most tables: `text-xs font-medium uppercase tracking-wider` headers — correct per design system
  - Some tables appear to have slightly different header weights or sizes
  - Proposals table: headers feel more compact than Customers table
  - Resources table: "Member" column header is left-aligned while date columns are center-aligned — inconsistent
- **Proposed Fix:** Verify all table instances use the shared Table component. Normalize header alignment rules.

### 5. Empty State Vertical Positioning
- **Dimension:** rhythm, spacing
- **Severity:** low
- **Affected Pages:** Notifications, Profitability, Schedules, Billing Runs, Dashboard (Team Time)
- **Root Cause:** shared empty-state component
- **Observation:** Empty states are vertically centered in the available space, which is correct. However, the icon size, text hierarchy, and CTA button styles vary:
  - Notifications: bell icon + "You're all caught up" + subtitle
  - Profitability: chart icon + "No profitability data yet" + subtitle + teal link
  - Schedules: gear icon + "No active schedules found." + subtitle (no CTA)
  - Billing Runs: stack icon + "No billing runs" + subtitle + outline button
  - Dashboard Team Time: bar chart icon + "No time logged" + subtitle
- **Proposed Fix:** Standardize empty state icon size, text sizes, and CTA style. All should use the same pattern: icon (consistent size) → heading (same weight/size) → subtitle → optional CTA button (consistent style).

### 6. Action Button Bar Alignment
- **Dimension:** alignment
- **Severity:** medium
- **Affected Pages:** Project detail, Customer detail, Invoice detail
- **Root Cause:** page-specific — detail page headers each implement their own action bar
- **Observation:**
  - Project detail: "Complete Project" + "..." + "Generate Document" + "Save as Template" + "Edit" + "Delete" — 6 actions, horizontally laid out, right of title. Action icons are mixed (some have icons, some don't).
  - Customer detail: "Change Status" + "Generate Document" + "Export Data" + "Anonymize" + "Edit" + "Archive" — 6 actions, same pattern but with colored destructive actions (Anonymize in red, Archive in red)
  - Invoice detail: "Preview" + "Send Invoice" + "Void" — 3 actions, right-aligned, different button variants
- **Proposed Fix:** Standardize the detail page action bar pattern. Consider grouping secondary actions into a "..." dropdown to reduce visual clutter. Use consistent button variants for destructive vs. primary actions.

### 7. Settings Sidebar vs Main Sidebar Competing Navigation
- **Dimension:** alignment, rhythm
- **Severity:** low
- **Affected Pages:** All /settings/* pages
- **Root Cause:** layout — settings pages have a secondary sidebar within the content area
- **Observation:** Settings pages show two sidebars: the main dark sidebar (w-60) AND a lighter secondary settings nav (categories: General, Work, Documents, Finance, Clients). The secondary sidebar text sizes and category labels are visually similar to the main sidebar's section headers, creating visual competition. The settings sidebar categories use `text-xs uppercase tracking-wider` gray labels, while main sidebar section headers use a similar treatment.
- **Proposed Fix:** Increase the visual differentiation — perhaps use lighter weight for settings category headers, or add more spacing between the settings sidebar and main content.

---

## Page-Specific Issues

### 8. Dashboard — Project Health Table Truncation
- **Dimension:** rhythm
- **Severity:** low
- **Affected Pages:** Dashboard only
- **Observation:** The Project Health table shows 10+ projects in a scrollable list. The "View all engagements" link at the bottom is easily missed. The table's progress bars (teal, 0%) are all identical since no work has been logged — in production with real data this would look better.
- **Proposed Fix:** Minor — consider limiting to 5-7 rows with a more prominent "View all" link.

### 9. Dashboard — Unequal Card Grid Heights
- **Dimension:** rhythm
- **Severity:** medium
- **Affected Pages:** Dashboard
- **Observation:** The dashboard has a 2-column layout below KPI cards:
  - Left column: "Project Health" (tall, many rows) + "Recent Activity" (also tall)
  - Right column: "Team Time" (shorter, shows empty state) + "Admin" (short, 3 items)
  - This creates significant height imbalance — the right column has lots of empty space below "Admin"
- **Proposed Fix:** Consider making Team Time / Admin cards grow to fill available height, or restructuring the dashboard grid to balance visual weight.

### 10. Customers Table — Dense Long Table
- **Dimension:** spacing, rhythm
- **Severity:** medium
- **Affected Pages:** Customers
- **Observation:** 48 customers in a single scrolling table with no pagination. The table shows NAME, EMAIL, PHONE, LIFECYCLE, STATUS, COMPLETENESS, CREATED columns. Some cells wrap (phone numbers), creating inconsistent row heights. The "Offboarded" lifecycle badge uses red which draws excessive attention.
- **Proposed Fix:** Add pagination or virtual scroll. Consider truncating phone numbers to prevent wrapping.

### 11. Projects — Card Grid Has No Visual Grouping
- **Dimension:** rhythm
- **Severity:** low
- **Affected Pages:** Projects/Engagements
- **Observation:** 39 project cards in a 3-column grid with uniform spacing. All cards look identical (title, badge, description, date, member count) — no visual hierarchy to distinguish important/recent/at-risk projects. The "Retainer" badge (outline teal) appears on some cards but is subtle.
- **Proposed Fix:** This is more UX than visual polish — skip for now.

### 12. My Work — Crowded Layout
- **Dimension:** spacing
- **Severity:** medium
- **Affected Pages:** My Work
- **Observation:** The My Work page packs a lot: KPI strip → week day selector → My Tasks tab/table (with sidebar panels: Time Breakdown, Today) → Available Tasks table → This Week + My Expenses cards at bottom. The right sidebar (Time Breakdown, Today) creates a narrow column that forces the main task table to be narrower.
- **Proposed Fix:** The spacing between the week day selector bars and the "My Tasks" section feels tight. Add more breathing room (increase gap from ~16px to ~24px).

### 13. Invoice Detail — Line Items Table Alignment
- **Dimension:** alignment
- **Severity:** low
- **Affected Pages:** Invoice detail
- **Observation:** The line items table has DESCRIPTION, PROJECT, QTY, RATE, AMOUNT, TAX columns. The AMOUNT column right-aligns values ("R 1 500,00") which is correct for financial data. The subtotal/tax/total summary rows right-align well. Minor: the "Standard (15%) R 225,00" tax text in the TAX column is long and could benefit from wrapping or abbreviation.
- **Proposed Fix:** Minor — tax column could be wider or text could be split into two lines.

### 14. Retainers — Double Filter Row
- **Dimension:** rhythm, spacing
- **Severity:** medium
- **Affected Pages:** Retainers
- **Observation:** There are two separate filter rows:
  1. "Filter: All | Active | Paused | Terminated" (pill buttons)
  2. "Active | Paused | Terminated | All" (underline tabs)
  These appear to do the same thing, creating confusion and visual noise.
- **Proposed Fix:** Remove one of the two filter rows — keep the underline tabs to match other pages' sub-navigation pattern.

### 15. Settings Templates — Table Clipping
- **Dimension:** alignment
- **Severity:** medium
- **Affected Pages:** Settings > Templates
- **Observation:** The templates page uses grouped tables (by category: Compliance, Cover Letter, Engagement Letter, Other, Project Summary, Report). The rightmost columns (SOURCE, STATUS, ACTIONS) are clipped at the page edge — the "ACTIONS" column header is cut to "AC..." and action buttons may be partially hidden. This is a responsive/width issue where the settings secondary sidebar + main sidebar eat into the available content width.
- **Proposed Fix:** Either make the table horizontally scrollable within its container, or reduce column count, or use a different layout for template lists.

### 16. Compliance — Lifecycle Cards Missing Subtle Issues
- **Dimension:** spacing
- **Severity:** low
- **Affected Pages:** Compliance
- **Observation:** The 5 lifecycle cards (Prospect: 40, Onboarding: 0, Active: 1, Dormant: 0, Offboarded: 7) are in a single row. Each card has icon + number + label. The cards have different colored icons (green, blue, orange, amber, red) which is good. However, the gap between the lifecycle cards row and the "Onboarding Pipeline" section below feels tight.
- **Proposed Fix:** Increase vertical spacing between the lifecycle card strip and the sections below.

---

## Priority Summary

### High-Impact Fixes (Shared Components)
1. **Filter tab consistency** (#3) — choose one pattern, apply everywhere
2. **KPI card standardization** (#2) — consistent height/padding across dashboard, proposals, retainers, compliance, invoices
3. **Page title spacing** (#1) — standardize title-to-content gap

### Medium-Impact Fixes (Layout/Page-Specific)
4. **Retainers double filter** (#14) — remove redundant filter row
5. **Settings templates clipping** (#15) — fix table overflow
6. **Dashboard card height balance** (#9) — even out the 2-column grid
7. **My Work spacing** (#12) — increase breathing room
8. **Action button bar alignment** (#6) — standardize detail page action bars

### Low-Impact Fixes (Polish)
9. **Empty state standardization** (#5) — consistent icon/text/CTA
10. **Table header alignment** (#4) — normalize across all tables
11. **Customers pagination** (#10) — add pagination for long tables
12. **Compliance section spacing** (#16) — increase gaps
