# 640B SEL-COMPAT — before (main d2a360527)

Scope: every `data-testid` and visible string rendered by the nine Home widgets in
`frontend/components/dashboard/` (plus the Home cell wrapper in
`frontend/app/(app)/org/[slug]/dashboard/page.tsx`), grepped against `frontend/e2e/tests/**` and
`frontend/e2e/helpers/**`.

Method: `grep -rnE` over both trees for (1) all testids below, (2) all card titles, empty-state /
error copy, header/footer link labels, column/legend labels. Comment-only hits excluded.

## 1. Selectors the e2e suites DO depend on (must survive 640B)

| Selector | Spec file:line | Rendered at |
|---|---|---|
| `#main-content` → `getByTestId("home-page")` (visible) | `e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts:51-53` (helper `homePage`), used at `:98`, `:327` | `app/(app)/org/[slug]/dashboard/page.tsx:173` |
| `#main-content` → `getByTestId("home-page")` (visible) | `e2e/tests/legal-lifecycle/day-90-review.spec.ts:221-222`, `:395` | `dashboard/page.tsx:173` |
| `home-page[data-audience="owner"]` for Alice | `e2e/tests/legal-lifecycle/day-90-review.spec.ts:223` | `dashboard/page.tsx:173` (`data-audience={plan.audience}`) |
| `[data-testid^="home-cell-"]` count > 0 inside home-page | `e2e/tests/legal-lifecycle/day-90-review.spec.ts:224` | `dashboard/page.tsx:184` (cell wrapper, not inside any widget file) |
| `#main-content h1` text `/^\s*Home\s*$/` | `e2e/tests/legal-depth-ii/null-prefetch-probe.spec.ts:66` | `DashboardHeader` (not a widget) |

None of these live inside the nine widget files. 640B is selector-safe as long as the page keeps
`home-page` + `data-audience` + the `home-cell-<widget>` wrapper and the "Home" h1.

Full-page Home screenshots taken by the suites (opt-in baseline compare only with `E2E_VISUAL=1`,
`e2e/helpers/screenshot.ts:36-81`; default run just writes PNGs — they will change visually):
`day-00-dashboard-legal-nav-active` (`day-00-firm-setup.spec.ts:330`),
`day-90-dashboard-overview-active` (`day-90-review.spec.ts:227`),
`day-90-rbac-comparison-loaded` + curated `role-comparison-carol-alice` (`day-90-review.spec.ts:398-399`).

## 2. Widget testids / copy with NO e2e reference (free to change, vitest only)

| Widget file | testids rendered (file:line) | Visible copy (file:line) | e2e hits |
|---|---|---|---|
| `home-diary-widget.tsx` | `home-diary-widget` :23, `todays-diary` :34 (also `todays-agenda.tsx:54`), `todays-diary-overdue` (`todays-agenda.tsx:143`) | "Today's diary" :25, "Open diary" :28; list copy in `todays-agenda.tsx`: "Nothing in the diary today" :113, "Today's diary could not be loaded." :111, "Couldn't identify your member profile." / "Open the Diary" :102-107, "+N more" :135, "N overdue" :146 | none |
| `my-tasks-widget.tsx` | `my-tasks-widget` :42, `my-tasks-headline` :61 | "My tasks" :44, "Open my work" :47, "Nothing assigned to you. …" :56, "open task(s), N overdue" :63-64, aria "My open tasks" :67, "Overdue" badge :82, "No due date" :84, error "Your tasks could not be loaded…" :53 | none |
| `unbilled-wip-widget.tsx` | `unbilled-wip-widget` (+`data-variant` personal/firm) :41/:85, `unbilled-wip-headline` :59/:106, `unbilled-disbursements` :143 | "My unbilled time" :43, "Open timesheet" :46, "No unbilled time. …" :55, "Billable time not yet on a fee note" :63; "Unbilled WIP" :87, "Open unbilled work" :91, "Nothing unbilled. …" :101, "… of unbilled time" :110, aria "Unbilled WIP by fee earner" :114, "Oldest from" :123, "N more fee earner(s)" :138, "Disbursements not yet billed" :146 | none (`legal-depth-ii/day-05-disbursements.spec.ts:436,446` hit the string `matter-unbilled-disbursements` — a matter-page screenshot name / FIXME id, not this testid) |
| `debtors-widget.tsx` | `debtors-widget` :45, `debtors-headline` :62 | "Debtors" :47, "Open collections" :50, "No client owes fees." :58, "client owes fees"/"clients owe fees" :64, aria "Largest debtors" :67, "Not yet overdue"/"N days overdue" :34-35, meter label "Aging of X's outstanding fees" :87, legend "Current/30 days/60 days/90+ days" :21-24, aria "Aging legend" :99 | none ("Debtors" only in `navigation/sidebar-navigation.spec.ts:57` — scoped to the sidebar nav, not Home) |
| `trust-position-widget.tsx` | `trust-position-widget` :38, `trust-position-headline` :53 | "Trust position" :40, "Open reconciliation" :43, "Balanced" :57, "Difference R…" :59, "Bank cashbook" :67, "Client ledgers" :71, "Last reconciliation" :75, "Awaiting approval" :79, "Never reconciled"/"Period to …" :24-27, "Primary of N trust accounts" :86 | none ("Client ledgers" in `day-00-firm-setup.spec.ts:318` and `sidebar-navigation.spec.ts:64` is scoped to the sidebar `nav`; "Awaiting approval by another member" in `day-60-interest.spec.ts:161,173` is the interest-run page) |
| `fica-outstanding-widget.tsx` | `fica-outstanding-widget` :29, `fica-outstanding-headline` :54 | "FICA outstanding" :31, "View all" :39, "Open compliance" :42, "Every active client has completed FICA." :50, "of N active client(s) without completed FICA" :58-59, aria "Clients with FICA outstanding" :62, "Not started"/"In progress" :12-13, "N day(s)" :72, footer "Incomplete profiles" :83 | none |
| `matter-health-widget.tsx` | `matter-health-panel` :64/:116 | "Matter Health" :66/:118, empty "No matters yet" / "Create a matter to start tracking health status." :71-72, tabs "All/At Risk/Over estimate" :23-25, headers "Matter/Status/Progress/Hours/Tasks" :151-173, aria "Sort by …" :146/:159, "No matching matters" :178, "View all matters →" :223 | none |
| `recent-activity-widget.tsx` | `recent-activity-widget` :83 (**populated state only** — the empty :67 and error :52 cards carry no testid) | "Recent Activity" :54/:69/:85, "Unable to load activity. Please try again." :58, "No recent activity" / "Activity will appear as your team works on matters." :74-75, "View all activity →" :126 (no-op button) | none |
| `sensitive-events-widget.tsx` | `sensitive-events-widget` :97, `sensitive-events-counts` :106, `sensitive-count-{NOTICE,WARNING,CRITICAL}` :111, `sensitive-events-empty` :120, `sensitive-row-<id>` :131, `sensitive-events-view-all` :160 | "Sensitive events" :101, "Last 7 days" :102, "No sensitive events in the last 7 days." :121, "View all →" :161 | none (`admin-audit-30day/admin-audit-lifecycle.spec.ts:84` is a comment; that test is `test.skip(true, …)` and only asserts the audit-log h1) |

`frontend/e2e/helpers/**`: no hits for any widget testid or copy.

## 3. Conclusion

- No e2e spec or helper selects into any of the nine widgets. The only Home contract is the page
  shell: `home-page`, `data-audience`, `home-cell-*` (count > 0), and the "Home" h1.
- 640B may rename/restructure widget-internal testids and copy without breaking e2e; vitest
  (`frontend/__tests__/dashboard/**`) is the only consumer of widget testids and must be updated in
  the same PR.
- Note for 640B: `recent-activity-widget` testid is absent in the empty/error states (inconsistent
  with every other widget, which keeps its root testid in all states).
