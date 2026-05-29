# Phase 77 — Customer Detail Page Redesign (Header Card + Grouped Tabs)

## System Context

Kazi is a multi-tenant B2B practice-management platform. The **customer/client detail page** (`/org/[slug]/customers/[id]`) is visited constantly — practitioners open it to check client status, review compliance progress, manage documents, view financials, and handle lifecycle transitions.

The current layout has the same problems the matter detail page had before Phase 73:

- **7 action buttons sprawl horizontally** next to the customer name: Summarise Activity, Change Status, Generate Document, Export Data, Anonymize, Edit, Archive. On narrower viewports they wrap into 2+ rows or get clipped.
- **Metadata wall before tabs.** The page stacks Address card → Primary Contact card → Business Details card → Field Groups selector → Custom Field Section → Tags → Trust Balance → Setup Progress → Unbilled Time → Template Readiness → Lifecycle Action Prompt — all vertically before the tab bar is visible. A customer with multiple field groups pushes tabs below the fold.
- **11 flat tabs.** Projects, Documents, Onboarding, Invoices, Retainer, Requests, Rates, Generated Docs, Financials, Trust, Audit in a single horizontal row. Many are conditionally visible (module-gated, admin-only, lifecycle-dependent), but a full accounting-za tenant still sees 8–10 tabs.
- **No content hierarchy.** Setup guidance cards, action prompts, and the AI FICA panel are interspersed with metadata sections rather than grouped logically.

### What the Matters page already has (Phase 73 + post-73)

The matter detail page was redesigned through Phase 73 (sidebar + grouped tabs) and then refined post-73 (sidebar replaced with header card + Details/Fields tabs). The final pattern:

- `MatterHeaderCard` — compact card: name, status badge, work type, reference number, customer link.
- `GroupedTabBar` — 23 sub-tabs collapsed into 7 logical groups using dropdown sub-navigation.
- `OverflowActionsMenu` — single `...` dropdown containing all secondary actions.
- Details and Fields as dedicated tabs instead of inline sections.
- Overview tab with KPI dashboard.

The customer page must adopt the same final pattern for consistency.

### Predecessor components this phase restructures (frontend only)

- **Main page component:** `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (~983 lines, server component)
- **Tab orchestrator:** `frontend/components/customers/customer-tabs.tsx` (Framer Motion underline, 11 flat tab definitions)
- **Promoted fields:** `frontend/components/customers/customer-address-block.tsx`, `customer-contact-card.tsx`
- **Custom fields:** `frontend/components/field-definitions/CustomFieldSection.tsx`
- **Field group selector:** `frontend/components/field-definitions/FieldGroupSelector.tsx`
- **Tags:** `frontend/components/tags/TagInput.tsx`
- **Setup guidance:** `frontend/components/setup/` (SetupProgressCard, ActionCard, TemplateReadinessCard)
- **AI panels:** `frontend/components/assistant/queue/pending-suggestions-widget.tsx`, `frontend/components/ai/fica-verification-panel.tsx`
- **All panel components** imported by CustomerTabs (CustomerProjectsPanel, CustomerDocumentsPanel, CustomerInvoicesTab, CustomerRetainerTab, CustomerRatesTab, CustomerFinancialsTab, CustomerAuditTab, ChecklistInstancePanel, RequestList, GeneratedDocumentsList, TrustBalanceCard)

### What this phase does NOT change

- **No backend changes.** No new API endpoints, no entity changes, no migrations. All data fetching stays the same.
- **No new features.** This is a layout restructure. Every current capability remains — just reorganised.
- **No matter detail page changes.** That page already has the target pattern.
- **No customer list page changes.** The list page is a separate concern.

## Objective

Redesign the customer detail page from a **vertical-stack layout** to a **header card + grouped tabs** layout that:

1. Puts customer identity into a compact `ClientHeaderCard` (name, status badges, email, lifecycle).
2. Moves all metadata (address, contact, business details, custom fields, tags) into a **Details** tab group.
3. Groups 11 flat tabs into 5 logical categories with grouped sub-navigation (reusing `GroupedTabBar`).
4. Adds an **Overview** tab with setup guidance, unbilled time, retainer summary, and lifecycle prompts.
5. Consolidates 7 action buttons into 1 smart primary action + overflow menu.

## Constraints & Assumptions

- **Frontend-only phase.** No `backend/` changes. The server component in `page.tsx` continues to fetch all the same data; only the rendering structure changes.
- **Reuse existing components.** `GroupedTabBar` from `components/projects/grouped-tab-bar.tsx` should be shared (or extracted to a shared location) and used by both Matters and Customers. Same for `OverflowActionsMenu` pattern.
- **Shadcn UI + Radix primitives.** Use existing design system components.
- **URL-based tab state preserved.** The `?tab=<id>` URL parameter continues to work. Grouped tabs support `?tab=invoices` resolving to the Finance group → Invoices sub-tab.
- **Module-gating preserved.** Trust tab remains gated by trust_accounting module. Onboarding tab gated by lifecycle status. Financials/Rates/Invoices gated by admin role. If all items in a group are gated off, the group hides.
- **No breaking changes to data fetching.** The page.tsx server component fetches all panel data upfront. This phase restructures how that data flows to the new layout, not what is fetched.
- **Keyboard navigation preserved.** `GroupedTabBar` already supports keyboard navigation (arrow keys, Enter/Space, Escape).
- **Responsive behavior.** Same breakpoint patterns as the Matters page — mobile-first, desktop enhancement.

---

## Section 1 — Client Header Card

### 1.1 Component

New component: `ClientHeaderCard` (mirrors `MatterHeaderCard` pattern).

```
┌──────────────────────────────────────────────────────┐
│ Kgosi Holdings (Pty) Ltd                             │
│ [Active] [Prospect] [KYC ✓] [Xero ↗]                │
│ finance@kgosi-holdings.co.za · +27-11-555-0301       │
│ Since May 23, 2026 · 3 engagements                   │
│                               [Start Onboarding] [⋯] │
└──────────────────────────────────────────────────────┘
```

Props (all serializable — no functions, no components):
- `customerId: string`
- `customerName: string`
- `customerStatus: CustomerStatus` (ACTIVE / ARCHIVED)
- `lifecycleStatus: LifecycleStatus | null`
- `email: string | null`
- `phone: string | null`
- `lifecycleStatusChangedAt: string | null`
- `linkedProjectCount: number`
- `kycSummary: KycSummary | null`
- `xeroConnected: boolean`
- `slug: string`
- `isAdmin: boolean`
- `isOwner: boolean`

### 1.2 Header content

**Row 1 — Name:** `font-display text-xl font-semibold`. Line-clamped to 2 lines with tooltip for overflow.

**Row 2 — Status badges:** Inline badge row:
- Customer status badge (Active/Archived)
- Lifecycle status badge (Prospect/Onboarding/Active/Dormant/Offboarding/Offboarded/Anonymized)
- KYC status badge (if configured)
- Xero connection badge (if connected)
- Completeness ring (if required fields exist)

**Row 3 — Contact summary:** Email + phone (if set), separated by middot.

**Row 4 — Context line:** "Since {date}" (lifecycle duration) + "{N} engagements" count.

**Row 5 — Actions (right-aligned):** Primary action button + overflow menu trigger.

### 1.3 Smart primary action

The visible primary action button changes based on lifecycle state:

| Lifecycle Status | Primary Action | Button Style |
|-----------------|----------------|--------------|
| PROSPECT | Start Onboarding | accent (teal) |
| ONBOARDING | Activate Customer | accent (teal) |
| ONBOARDING (blockers) | Activate Customer (disabled, tooltip shows blockers) | outline, disabled |
| ACTIVE | Edit | outline |
| DORMANT | Edit | outline |
| OFFBOARDING | Complete Offboarding | accent |
| OFFBOARDED | — (no primary) | — |
| ANONYMIZED | — (no primary, read-only) | — |
| ARCHIVED | Restore | outline |

### 1.4 Overflow actions menu

New component: `ClientOverflowMenu` (follows `OverflowActionsMenu` pattern).

`...` (MoreHorizontal icon) dropdown button, right-aligned in header card.

Menu structure:
```
Edit Client
Summarise Activity              (AI capability)
───────────────
Generate Document  →            (submenu: template list)
Run Conflict Check              (module: conflict_check)
Verify KYC                      (KYC configured + not verified)
───────────────
Export Data
───────────────
Anonymize                       (owner only, destructive)
Archive                         (admin, destructive)
```

Items are gated by the same permission/module/lifecycle checks as the current buttons. Destructive items at the bottom, separated by a divider. Anonymize and Archive use red text styling.

Items hidden when customer is ANONYMIZED (read-only state): Edit, Generate Document, Conflict Check, Verify KYC, Anonymize, Archive.

---

## Section 2 — Grouped Tab Bar & Tab Groups

### 2.1 Tab group definitions

Reuse `GroupedTabBar` from `components/projects/grouped-tab-bar.tsx`. Define customer-specific tab groups in a new constant file: `lib/constants/customer-tab-groups.ts`.

| Group | Sub-tabs | Visibility | Notes |
|-------|----------|------------|-------|
| **Details** | Details, Fields, Tags | Always | Details = address + contact + business details. Fields = custom fields + field group selector. Tags = tag input. |
| **Overview** | (standalone tab) | Always | Setup guidance, unbilled time, retainer summary, lifecycle prompt, AI panels. Default tab on page load. |
| **Work** | Projects, Documents, Generated Docs | Always | Core operational content. |
| **Finance** | Invoices, Rates, Retainer, Financials, Trust | Conditional | Invoices: admin. Rates: admin. Retainer: non-terminal lifecycle or has retainers. Financials: admin. Trust: trust_accounting module. If no sub-tabs visible (non-admin, no modules), group hides. |
| **Compliance** | Onboarding, Requests | Conditional | Onboarding: ONBOARDING lifecycle or has checklists. Requests: always. If only Requests visible, render as plain tab. |
| **Activity** | Audit | Conditional | TEAM_OVERSIGHT capability. Standalone tab (no dropdown). |

### 2.2 URL state

- All existing tab IDs continue to work: `?tab=projects`, `?tab=documents`, `?tab=invoices`, `?tab=onboarding`, `?tab=retainer`, `?tab=requests`, `?tab=rates`, `?tab=generated`, `?tab=financials`, `?tab=trust`, `?tab=audit`.
- New tab IDs: `?tab=details`, `?tab=fields`, `?tab=tags`, `?tab=overview`.
- Default tab (no `?tab=` param): **overview**.
- Group-level aliases: `?tab=work` → Projects, `?tab=finance` → first visible Finance sub-tab, `?tab=compliance` → first visible Compliance sub-tab.

### 2.3 Component structure

New component: `CustomerGroupedTabs` (client component).

This component:
1. Receives all tab panel content as props (same pattern as current `CustomerTabs`).
2. Renders `GroupedTabBar` with the customer tab group configuration.
3. Manages active tab state via URL search params.
4. Conditionally renders the active tab's panel content.

The existing `CustomerTabs` component is replaced by `CustomerGroupedTabs`.

### 2.4 Sharing GroupedTabBar

`GroupedTabBar` currently lives in `components/projects/`. It should be moved (or re-exported) to a shared location since both Matters and Customers use it:

Option A: Move to `components/ui/grouped-tab-bar.tsx` (Shadcn-adjacent shared component).
Option B: Move to `components/shared/grouped-tab-bar.tsx`.
Option C: Keep in `components/projects/` and import from there (pragmatic, less churn).

Prefer Option A or B — the component is now cross-domain.

---

## Section 3 — Details Tab Group

### 3.1 Details sub-tab

Renders the metadata currently inline on the page:

- **Address block** (`CustomerAddressBlock`)
- **Primary Contact card** (`CustomerContactCard`)
- **Business Details card** (registration number, tax number, entity type, financial year end)

Layout: same 2-column grid for Address + Contact, Business Details below. Identical content and styling to current page, just inside a tab panel instead of inline.

### 3.2 Fields sub-tab

- **Field Group Selector** (`FieldGroupSelector`)
- **Custom Field Section** (`CustomFieldSection`)

Same components, same props, just rendered inside the Fields tab panel.

### 3.3 Tags sub-tab

- **Tag Input** (`TagInput`)

Same component. Optionally, if Tags sub-tab feels too thin on its own, merge it into the Fields sub-tab (Fields & Tags).

---

## Section 4 — Overview Tab

### 4.1 Overview content

The Overview tab consolidates setup guidance, action prompts, and summary metrics into a single dashboard view. This is the **default tab** when navigating to a customer detail page.

**Layout:** Vertical stack of cards/sections. Single-screen-height on desktop for typical content.

**Sections (top to bottom):**

**A. Client Readiness** (if `customerReadiness` data exists)
- `SetupProgressCard` component — same as current, showing completion percentage and step checklist.
- Includes `contextGroups` for expandable required fields display.
- Includes `activationBlockers` if present.

**B. Lifecycle Action Prompt** (conditional)
- "Ready to start onboarding?" or "All items verified — Activate Customer" action cards.
- Same `ActionCard` component as current.

**C. Financial Summary** (2-column grid on desktop)
- **Unbilled Time card** — amount, hours, "Create Invoice" / "View Time" links. Same `ActionCard` as current.
- **Retainer Status card** (if active retainer) — retainer amount, status, period usage percentage. New card, data from existing `activeRetainer` + `retainerPeriods`.

**D. Template Readiness** (if templates exist)
- `TemplateReadinessCard` — same component as current.

**E. Pending AI Suggestions** (if any)
- `PendingSuggestionsWidget` — moved from page bottom to Overview tab.

**F. FICA AI Verification Panel** (if AI capable + not anonymized)
- `FicaVerificationPanel` — moved from page bottom to Overview tab.

### 4.2 Overview when setup is complete

When all readiness steps are complete, no lifecycle prompt is pending, and no unbilled time exists:
- Show a clean summary: client name, lifecycle badge, linked projects count, last activity date.
- "Everything looks good" state — not an empty tab.

---

## Section 5 — Anonymized Info Banner

### 5.1 Banner placement

The anonymized customer banner currently sits between the header and promoted fields. In the new layout:
- Render it **between the header card and the tab bar** — full-width, always visible regardless of active tab.
- Same styling and content as current.

---

## Section 6 — Migration Path

### 6.1 Component refactoring strategy

1. **Create `ClientHeaderCard`.** Extract name, badges, email, phone, lifecycle from the current header section. Add smart primary action and overflow menu.
2. **Create `ClientOverflowMenu`.** Move all 7 action buttons into a dropdown menu with proper gating.
3. **Create customer tab group constants.** Define `CUSTOMER_TAB_GROUPS` in `lib/constants/customer-tab-groups.ts` following the same shape as `TAB_GROUPS` for Matters.
4. **Create `CustomerGroupedTabs`.** Replace `CustomerTabs` with the grouped version, passing all panel content as props.
5. **Create Overview tab content.** Move setup guidance, action prompts, unbilled time, template readiness, and AI panels from the main page flow into the Overview tab.
6. **Create Details tab.** Move Address, Contact, Business Details into a Details tab panel.
7. **Create Fields tab.** Move FieldGroupSelector + CustomFieldSection into a Fields tab panel.
8. **Slim down `page.tsx`.** The server component still fetches everything, but renders: back link → `ClientHeaderCard` → (optional anonymized banner) → `CustomerGroupedTabs` with all panels as props.

### 6.2 Shared component extraction

Extract `GroupedTabBar` and related utilities to a shared location:
- `components/shared/grouped-tab-bar.tsx` (or `components/ui/`)
- `lib/constants/tab-groups.ts` → rename to `lib/constants/project-tab-groups.ts` and update imports. Create parallel `customer-tab-groups.ts`.
- Shared utilities: `resolveTabFromUrl()`, `getGroupForTab()` — parameterise by tab group config.

### 6.3 QA testplan audit — detailed impact analysis

Layout changes affect every QA script that interacts with the customer detail page. The existing `qa/testplan/MIGRATION-NOTES.md` documents the Matter page migration (Phase 73); a parallel **Customer Detail Migration** section must be added to that file.

#### 6.3.1 Testplan files requiring updates

| File | Impact | What changes |
|------|--------|-------------|
| `qa/testplan/48-lifecycle-script.md` | HIGH | Steps 1.6, 1.7, 1.14, 1.25, 75.3, 75.9, 90.11 reference customer detail tabs, lifecycle transition buttons, Generate Document button |
| `qa/testplan/regression-test-suite.md` | HIGH | Customer CRUD (line 166), lifecycle badges (line 178), Generate Document (line 366), Export/Anonymize (lines 478–481), page objects (lines 711–721) |
| `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` | HIGH | Field promotion checkpoints (line 132, 401), Onboarding tab (line 204), client detail screenshots (line 250), onboarding flow (line 320) |
| `qa/testplan/demos/legal-za-90day-keycloak.md` | HIGH | Field promotion checkpoint (lines 150, 157–158), lifecycle badge (lines 218, 230) |
| `qa/testplan/demos/admin-audit-30day-keycloak.md` | MEDIUM | Customer Audit tab (lines 195–207) — tab now under Activity group, selector changes |
| `qa/testplan/legal-onboarding-keycloak.md` | MEDIUM | Client detail page references (lines 239, 252, 325, 393), lifecycle badge (line 239) |
| `qa/testplan/qa-legal-lifecycle-test-plan.md` | MEDIUM | Client detail (lines 72, 125, 446, 544), lifecycle badge, Generate Document |
| `qa/testplan/phase49-document-content-verification.md` | MEDIUM | Custom fields section (lines 142, 203, 251–252), Requests tab (lines 521, 560), Generate Document (lines 375, 395) |
| `qa/testplan/phase74-ai-lifecycle-scenario.md` | LOW | "Verify with AI" button on compliance panel (line 106) — now in Overview tab |
| `qa/testplan/demo-readiness-keycloak-master.md` | LOW | Customer detail page field references (line 199) |

#### 6.3.2 Selector migration patterns

Mirroring the `MIGRATION-NOTES.md` format for Matters, the Customer Detail migration must document:

**Tab navigation changes:**

| Old (flat tab) | New group | New tab ID | New navigation |
|---|---|---|---|
| Projects | Work | projects | `tab-group-work` → `tab-item-projects` |
| Documents | Work | documents | `tab-group-work` → `tab-item-documents` |
| Generated Docs | Work | generated | `tab-group-work` → `tab-item-generated` |
| Onboarding | Compliance | onboarding | `tab-group-compliance` → `tab-item-onboarding` |
| Invoices | Finance | invoices | `tab-group-finance` → `tab-item-invoices` |
| Retainer | Finance | retainer | `tab-group-finance` → `tab-item-retainer` |
| Requests | Compliance | requests | `tab-group-compliance` → `tab-item-requests` |
| Rates | Finance | rates | `tab-group-finance` → `tab-item-rates` |
| Financials | Finance | financials | `tab-group-finance` → `tab-item-financials` |
| Trust | Finance | trust | `tab-group-finance` → `tab-item-trust` |
| Audit | Activity (standalone) | audit | `tab-group-activity` click |
| _(new)_ Details | Details | details | `tab-group-details` → `tab-item-details` |
| _(new)_ Fields | Details | fields | `tab-group-details` → `tab-item-fields` |
| _(new)_ Tags | Details | tags | `tab-group-details` → `tab-item-tags` |
| _(new)_ Overview | Overview (standalone) | overview | `tab-group-overview` click |

**Action button migration:**

| Old Location | Old Selector | New Location | New Selector |
|---|---|---|---|
| Header row | `page.getByText('Edit')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` → `page.getByText(/edit/i)` |
| Header row | Lifecycle transition dropdown | Header card | `page.getByTestId('client-header-card').getByRole('button', { name: /start onboarding/i })` |
| Header row | `page.getByText('Generate Document')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` → `page.getByText(/generate document/i)` |
| Header row | `page.getByText('Export Data')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` → `page.getByText(/export/i)` |
| Header row | `page.getByText('Anonymize')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` → `page.getByText(/anonymize/i)` |
| Header row | `page.getByText('Archive')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` → `page.getByText(/archive/i)` |
| Header row | `page.getByText('Summarise')` | Overflow menu | `page.getByTestId('client-overflow-trigger').click()` → `page.getByText(/summarise/i)` |

**Content relocation:**

| Content | Old location | New location |
|---|---|---|
| Address block, Contact card, Business Details | Inline above tabs | Details tab (`tab-group-details` → `tab-item-details`) |
| Custom fields section | Inline above tabs | Fields tab (`tab-group-details` → `tab-item-fields`) |
| Tags section | Inline above tabs | Tags tab (`tab-group-details` → `tab-item-tags`) |
| Setup Progress card | Inline above tabs | Overview tab |
| Unbilled Time card | Inline above tabs | Overview tab |
| Template Readiness card | Inline above tabs | Overview tab |
| Lifecycle Action Prompt | Inline above tabs | Overview tab |
| Pending AI Suggestions | Below tabs | Overview tab |
| FICA Verification Panel | Below tabs | Overview tab |

#### 6.3.3 Field promotion checkpoint updates

Several testplans verify that promoted field slugs render "inline at the top of the detail page, NOT in the sidebar CustomFieldSection." Post-Phase 77:
- Promoted fields still render as first-class fields on the **Details tab** (address, contact, business details cards).
- The assertion changes from "visible on page load" to "visible when Details tab is active."
- Steps referencing "verify promoted fields inline" must add a navigation step: open Details group → Details sub-tab first.

Affected files:
- `demos/legal-za-90day-keycloak.md` lines 150, 157–158
- `demos/accounting-za-90day-keycloak-v2.md` lines 132, 401, 415

### 6.4 No feature flags

Direct replacement. Rollback via git revert if needed.

---

## Section 7 — Phase 75 Sharding Impact on QA Testplans

Phase 75 introduced database sharding (job queue fanout + shard-aware DB resolver). While primarily a backend infrastructure change, two QA testplan references are affected:

### 7.1 Direct testplan references

| File | Lines | What references sharding | Impact |
|---|---|---|---|
| `qa/testplan/legal-onboarding-keycloak.md` | 482, 511 | Direct SQL query: `SELECT * FROM public.org_schema_mapping;` | LOW — query still works but now returns a `shard_id` column. Update step to mention the new column or add a verification: "verify `shard_id` is `primary` for the new tenant." |

### 7.2 Indirect impacts (no testplan changes needed)

- **Seed scripts** (`compose/seed/seed.sh`, `compose/seed/lifecycle-test.sh`): Provision via `/internal/orgs/provision` API. The API signature hasn't changed — shard assignment defaults to `primary` when `kazi.sharding.enabled=false` (default for dev/E2E stacks). No testplan changes needed unless E2E environments enable multi-shard.
- **E2E Docker stack**: Runs single Postgres. Sharding is disabled by default. No changes needed.
- **Backend test suite**: Phase 75 already shipped its own test suite (`ShardAwareFlywayTest`, `EndToEndMultiShardTest`, `ShardIsolationTest`, `ShardMigrationDataSourceTest`). These are backend regression gates, not QA testplan items.
- **`TenantScopedRunner.forEachTenant()`**: Scheduler methods migrated to job queue enqueue pattern. Existing lifecycle/QA scripts don't test scheduler internals — they test user-facing behaviour, which is unchanged.

### 7.3 Recommendation

The sharding changes have minimal QA testplan impact because:
1. Sharding is disabled by default (`kazi.sharding.enabled=false`)
2. Single-shard behaviour is backward-compatible
3. QA scripts test user-facing behaviour, not infrastructure routing

The only required update is adding `shard_id` column awareness to the `org_schema_mapping` SQL query in `legal-onboarding-keycloak.md`. This is a one-line note, not a structural change.

---

## Out of Scope

- **Customer list page redesign.** The list page layout is a separate concern.
- **Matter detail page changes.** Already has the target pattern.
- **Backend changes.** No new APIs, no entity changes, no migrations.
- **New data or metrics.** The Overview tab uses existing data — no new aggregation endpoints.
- **Drag-and-drop tab reordering.** Tab groups are fixed in code.
- **User-configurable tab groups.** No per-user or per-org tab customisation.
- **Mobile-native layouts.** Standard responsive web — no bottom tabs, no swipe navigation.
- **Dashboard analytics.** No charts, trend graphs, or date-range pickers on the Overview. That's a reporting feature.

---

## ADR Topics

- **ADR-298: Shared GroupedTabBar pattern across entity detail pages.** Documents the extraction of `GroupedTabBar` from the projects domain to a shared component. Records the decision on shared location, tab group configuration shape, and how entity-specific tab groups compose with the shared bar. Establishes the convention that any entity detail page with >6 visible tabs should use this pattern.

- **ADR-299: Header card + grouped tabs as the standard entity detail layout.** Documents the evolution from Phase 73 (sidebar) to the current header card pattern and why it was chosen. Records the decision that all primary entity detail pages (Matters, Customers, and future entities) follow this same layout: compact header card → grouped tab bar → tab content. Establishes when a sidebar layout is appropriate (settings pages, dashboards) vs. when header card + tabs is preferred (entity detail pages).

---

## Style and Boundaries

- Read `frontend/CLAUDE.md` before touching any component.
- All new components use Shadcn UI primitives (Card, Badge, DropdownMenu, Tooltip).
- Tailwind v4 utility classes only — no custom CSS files.
- Reuse colour tokens from the design system (slate palette, teal accents).
- Test the layout with: (a) short customer names ("John Smith"), (b) long customer names ("Kgosi Holdings International (Pty) Ltd — Johannesburg Regional Office"), (c) no custom fields, (d) 3+ field groups, (e) all modules enabled (max tabs), (f) non-admin user (min tabs), (g) anonymized customer (read-only state), (h) PROSPECT vs. ACTIVE vs. OFFBOARDED lifecycle states.
- The page must remain a server component at the top level (data fetching). Client components for interactive elements (grouped tab bar, overflow menu).
- Preserve the Dialog Trigger Composition pattern from `frontend/CLAUDE.md` — no adjacent `asChild` triggers. The overflow menu eliminates this risk by consolidating all actions into a single dropdown.
