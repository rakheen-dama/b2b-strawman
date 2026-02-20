# Accepted Gap Analysis — Grouped Briefs

> Generated: 2026-02-20
> Source: `gap-analysis.md` (34 findings) reviewed against codebase, requirements, architecture docs, and ADRs
> Reviewer verdict: 4 code fixes, 1 ADR-level decision, 26 walkthrough updates

---

## Group A — Task Detail Experience (ADR Required)

**Scope:** Findings #2, #3, #5, #6 from gap-analysis.md
**Severity:** High — tasks are the daily driver for end users
**Type:** Architecture decision + frontend implementation

### Problem Statement

Tasks are the most-used entity in the system (staff interact with them daily), yet they have the weakest frontend surface. The current implementation uses inline row expansion inside the project detail page's task list. This was a reasonable v1 choice (ADR-019, Phase 5 task 45.5), but it has created a cascade of integration gaps:

1. **No assignee at creation** (#2): The `CreateTaskDialog` has no assignee selector. The backend fully supports `assigneeId` on the Task entity, and leads/admins can assign via `PUT /api/tasks/{id}`. The claim-based workflow (ADR-019) is intentional for self-service, but pre-assignment at creation is a legitimate admin need.

2. **No dedicated task detail page** (#3): The inline expanded row shows time entries and comments. There is no `/org/[slug]/projects/[id]/tasks/[taskId]` route. Phase 5 chose "expandable row" over "detail page" (task 45.5). The architecture docs use "task detail view" to mean the expanded row, not a page.

3. **Tags & custom fields not on tasks** (#5): Phase 11 is marked complete, but the frontend integration for tasks was skipped. The backend is fully wired — `Task.java` has `custom_fields` (JSONB) and `applied_field_groups` columns, and `EntityTag` covers tasks polymorphically. The Phase 11 plan (task 92.4) assumed a `tasks/[id]/page.tsx` that doesn't exist. `TagInput` and `CustomFieldSection` are wired to projects and customers but not tasks.

4. **Saved views not on tasks** (#6): The backend fully supports `entityType="TASK"` saved views — `TaskController` accepts `?view={viewId}`, `ViewFilterIntegrationTest` exercises TASK views. The frontend `EntityType` type includes `"TASK"`. But `ViewSelectorClient` is only wired to the projects and customers list pages, not to any task-displaying surface.

### Decision Required (ADR)

The root cause is architectural: **should tasks get a dedicated detail page, or should the inline expansion be enhanced?**

| Option | Pros | Cons |
|--------|------|------|
| **A: Dedicated task detail page** (`/projects/[id]/tasks/[taskId]`) | Clean surface for tags, custom fields, comments, time entries, attachments. Linkable URL. Room to grow. | Navigation friction (leaves task list context). More routes to maintain. |
| **B: Enhanced inline expansion** | User stays in list context. No new routes. | Gets cramped with tags + custom fields + saved views. Mobile UX degrades. |
| **C: Slide-over panel** (drawer from right) | Best of both — stays on page, but has full detail real estate. Common in project management tools (Linear, Asana). | More complex component. Needs responsive handling. |

### Files Affected

| File | Change |
|------|--------|
| `frontend/components/tasks/create-task-dialog.tsx` | Add optional assignee `<select>` for leads/admins (populate from project members) |
| `frontend/components/tasks/task-list-panel.tsx` | Wire `ViewSelectorClient` in header; if Option B, add `TagInput` + `CustomFieldSection` to expanded row |
| `frontend/app/(app)/org/[slug]/projects/[id]/tasks/[taskId]/page.tsx` | **New file** if Option A or C: task detail page/panel with tags, custom fields, comments, time entries |
| `frontend/app/(app)/org/[slug]/my-work/page.tsx` | Wire `ViewSelectorClient entityType="TASK"` |
| Backend | Zero changes needed — all backend support is already complete |

### Backend Readiness Checklist

- [x] `Task.java` has `assigneeId`, `customFields`, `appliedFieldGroups` columns
- [x] `TaskController` accepts `?view={viewId}` for saved views
- [x] `EntityTag` supports `entityType="TASK"` polymorphically
- [x] `ViewFilterIntegrationTest` covers TASK saved views
- [x] `PUT /api/tasks/{id}` accepts `assigneeId` for direct assignment
- [x] `POST /api/tasks/{id}/claim` and `/release` for self-service workflow

---

## Group B — Invoice Creation Entry Points (Code Fix)

**Scope:** Findings #1, #8 from gap-analysis.md
**Severity:** Critical (#1 is a 404) + High (#8 is a missing affordance)
**Type:** Frontend bug fix

### Problem Statement

Two `ActionCard` components link to `/org/${slug}/invoices/new` — a route that does not exist. Clicking "Create Invoice" from the project overview or customer overview produces a 404. Meanwhile, the invoices list page has no creation entry point at all.

Invoice creation is architecturally customer-scoped (ADR-049, ADR-050) — you need a specific customer's unbilled time to generate line items. The working entry point is `InvoiceGenerationDialog` on the customer's Invoices tab. The broken links bypass this.

### Broken Links

| File | Line | Current href | Problem |
|------|------|-------------|---------|
| `frontend/components/projects/overview-tab.tsx` | 176 | `/org/${slug}/invoices/new?projectId=${projectId}` | 404 — route doesn't exist |
| `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` | 495 | `/org/${slug}/invoices/new?customerId=${id}` | 404 — route doesn't exist |

### Fix

**Option 1 (Minimal — recommended):** Change both ActionCard `href` links to navigate to the customer's Invoices tab where `InvoiceGenerationDialog` lives:
- Customer overview card: `href: ?tab=invoices` (same page, switch tab)
- Project overview card: `href: /org/${slug}/customers/${customerId}?tab=invoices` (needs `customerId` — may require threading it through props or fetching from project context)

**Option 2 (Better UX):** Replace `href` with an `onClick` that opens `InvoiceGenerationDialog` inline. This requires making the dialog available outside the customer Invoices tab (e.g., a shared component that accepts `customerId` as a prop). The project overview card would need to resolve which customer to invoice (projects can have multiple customers).

**Option 3 (Invoices list page button):** Add a "New Invoice" button to the invoices list page that opens a customer-selection step first, then the generation dialog. Architecture doc Section 11.13.4 deliberately omits this, but it could improve discoverability.

### Files to Modify

| File | Change |
|------|--------|
| `frontend/components/projects/overview-tab.tsx` | Fix ActionCard href (line 176) |
| `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` | Fix ActionCard href (line 495) |
| `frontend/app/(app)/org/[slug]/invoices/page.tsx` | Optional: add "New Invoice" button with customer selector |

---

## Group C — Comment Visibility Enum Mismatch (Code Fix)

**Scope:** Finding #11 from gap-analysis.md (hidden bug discovered during review)
**Severity:** High — security-relevant (customer-visible comments)
**Type:** Backend bug fix

### Problem Statement

ADR-037 specifies two visibility values: `INTERNAL` and `SHARED`. The frontend `<select>` uses `INTERNAL`/`SHARED`. But the backend `CommentService` validates and gates against `INTERNAL`/`EXTERNAL`.

This creates two bugs:

1. **Privilege bypass on create:** The backend checks `"EXTERNAL".equals(resolvedVisibility)` to enforce the lead/admin gate. But the frontend sends `"SHARED"`, so this check never triggers — any member could create a `SHARED` comment if the frontend toggle were shown to them (currently hidden by `canManageVisibility`, so the gate is only client-side).

2. **Validation rejection on update:** The backend validates `!"INTERNAL".equals(visibility) && !"EXTERNAL".equals(visibility)` — this would reject `"SHARED"` as invalid, breaking visibility changes from the frontend.

### Fix

Replace `"EXTERNAL"` with `"SHARED"` in `CommentService.java`:

| File | Line | Current | Fix |
|------|------|---------|-----|
| `backend/.../comment/CommentService.java` | ~80 | `"EXTERNAL".equals(resolvedVisibility)` | `"SHARED".equals(resolvedVisibility)` |
| `backend/.../comment/CommentService.java` | ~159 | `!"EXTERNAL".equals(visibility)` | `!"SHARED".equals(visibility)` |

Also audit any test files that use `"EXTERNAL"` for comment visibility and update them to `"SHARED"`.

### Verification

- Existing tests should be updated and re-run
- Write a test that proves a regular member cannot create a `SHARED` comment (currently this check is frontend-only)

---

## Group D — Retainer UX Polish (Code Fix)

**Scope:** Findings #20, #23 from gap-analysis.md
**Severity:** Medium
**Type:** Frontend enhancement

### Problem Statement

Two related retainer UX gaps where the backend is fully wired but the frontend doesn't surface the information:

1. **No inline alert banners at 80%/100% consumption** (#20): The backend `RetainerConsumptionListener` fires edge-triggered notifications at 80% and 100% thresholds. The progress bar changes color (green/amber/red). But there are no visible `<Alert>` banners on the retainer detail page. This was scoped to Epic 128G (not yet implemented).

2. **No navigation to draft invoice after period close** (#23): When closing a retainer period, the backend returns `PeriodCloseResult` with `generatedInvoice.id`. The server action surfaces this as `result.data`. But `ClosePeriodDialog` only checks `result.success` and closes — it ignores the invoice ID entirely. The user must manually find the invoice in the period history table.

### Fix

**Finding #20 — Alert banners:**

In the retainer detail page (`frontend/app/(app)/org/[slug]/retainers/[id]/page.tsx`), add conditional alerts:

```tsx
{consumptionPercent >= 100 && (
  <Alert variant="destructive">Retainer fully consumed — hours are now in overage.</Alert>
)}
{consumptionPercent >= 80 && consumptionPercent < 100 && (
  <Alert variant="warning">Retainer at {consumptionPercent}% capacity — approaching limit.</Alert>
)}
```

**Finding #23 — Post-close navigation:**

In `ClosePeriodDialog`, after successful close:

```tsx
if (result.success && result.data?.generatedInvoice?.id) {
  router.push(`/org/${slug}/invoices/${result.data.generatedInvoice.id}`);
} else {
  onOpenChange(false);
}
```

Or show a toast with a "View Invoice" link if navigation feels too aggressive.

### Files to Modify

| File | Change |
|------|--------|
| `frontend/app/(app)/org/[slug]/retainers/[id]/page.tsx` | Add conditional `<Alert>` banners based on consumption % |
| `frontend/components/retainers/close-period-dialog.tsx` | Use `result.data.generatedInvoice.id` for post-close navigation |

---

## Group E — Template Preview UX (Low Priority Enhancement)

**Scope:** Finding #7 from gap-analysis.md
**Severity:** Low — affects template authors (admins only), not end users
**Type:** Frontend enhancement

### Problem Statement

The template preview dialog (`TemplatePreviewDialog.tsx`) requires typing a raw entity UUID to preview a template. The requirements specified "sample/placeholder data" for editor preview. The primary preview path (generation dialog on entity detail pages) doesn't have this problem — the entity is pre-known from context.

### Fix (if prioritized)

Replace the `<Input>` in `TemplatePreviewDialog.tsx` with a `Command`/Combobox component that searches entities by name:
- Fetch entities from the existing list API (`/api/projects`, `/api/customers`, `/api/invoices`) based on `entityType`
- Display as searchable dropdown with name + ID preview
- On select, populate the entity ID for the preview call

### Files to Modify

| File | Change |
|------|--------|
| `frontend/components/templates/TemplatePreviewDialog.tsx` | Replace `<Input>` with entity search `<Command>` component |

---

## Group F — Walkthrough Documentation Drift (26 items)

**Scope:** Findings #4, #8, #9, #10, #12, #13, #14, #15, #16, #17, #18, #19, #21, #22, #24, #25, #26, #27, #28, #29, #30, #31, #32, #33, #34
**Severity:** Low — no code changes needed
**Type:** Documentation update

### Problem Statement

The product walkthrough (`docs/product-walkthrough.md`) was written from an early spec or aspirational design. The implementation evolved — often for the better — creating label mismatches, flow differences, and omitted features. None of these require code changes.

### Categories

**Label/button mismatches (fix text in walkthrough):**
- #10: "Add Customer" → actual is "New Customer"
- #26: "Start Checklist" → actual is "Manually Add Checklist"
- #27: "Utilization rate" → actual is "Billable %"
- #33: "Export Data" → actual is "Generate Export"

**UX flow differs from description (fix description):**
- #13: Cost rates are in a tab, not "scroll down"
- #14: Rate creation is per-member inline, not a top-level button
- #15: Upload auto-completes, no "Confirm" step
- #16: Redirect to `/create-org`, not empty dashboard with prompt
- #17: Tab order is Overview/Documents/Members/Customers (not Customers/Members)
- #18: Lead transfer via dropdown menu, not role badge click
- #19: Invoice dialog is pre-scoped to customer, no selector needed

**Missing/incorrect feature descriptions (update walkthrough):**
- #4: KPIs are Active Projects, Hours Logged, Billable %, Overdue Tasks, Avg. Margin (not "Total Revenue")
- #8: No "New Invoice" on list page — by design (customer-scoped creation)
- #9: No "Contact Name" field — single `name` field by design
- #12: Email toggles show "Coming soon" — intentionally stubbed
- #21: Name pattern lives on the template, not the schedule
- #22: No read-only template detail page — edit page serves both purposes
- #24: Lifecycle includes DORMANT and OFFBOARDING states
- #25: Customer detail has a "Retainer" tab (conditional)
- #28: Health signals are budget, overdue tasks, activity recency, task count (not "team capacity")
- #29: Duration is two fields (Hours + Minutes), not single field with unit toggle
- #30: Log Time form has a Billable checkbox (walkthrough omits it)
- #31: Budget form has Currency and Notes fields (walkthrough omits them)
- #32: 9 custom field types implemented, not 3
- #34: Financials/Rates/Generated Docs tabs are role-gated (walkthrough doesn't mention)

### Action

A single pass through `docs/product-walkthrough.md` updating all 26 items. No code changes required.

---

## Priority Order

| Priority | Group | Effort | Impact |
|----------|-------|--------|--------|
| 1 | **B — Invoice links** | Small (fix 2 hrefs) | Eliminates 404s |
| 2 | **C — Comment enum** | Small (2 string replacements + tests) | Fixes security-relevant bug |
| 3 | **D — Retainer UX** | Small (2 components) | Immediate UX improvement |
| 4 | **A — Task experience** | Large (ADR + implementation) | High daily-use impact |
| 5 | **E — Template preview** | Medium (new component) | Low-frequency admin feature |
| 6 | **F — Walkthrough** | Medium (documentation pass) | No user-facing impact |
