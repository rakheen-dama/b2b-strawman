# Fix Spec: TERM-CYCLE57 — §90.1 firm-side terminology basket (Day 90 polish sweep)

## Problem

The cycle-57 Day 90 walk closed §90.1 (terminology sweep, firm-side) as **PARTIAL** with a fixed list of placeholder + label leaks where the legal-vertical mapping is not applied. None are demo blockers; all are visible-copy polish gaps that QA has flagged across cycles 1, 55, and 57.

Evidence: `qa_cycle/checkpoint-results/day-90.md §90.1`, `cycle57-day90-90.1-firm-dashboard.yml`, `cycle57-day90-90.2-firm-new-matter-dialog.yml`, `cycle57-day90-90.1-firm-fee-notes.yml`.

The §90.1 walk recorded these residuals (LOW severity, tracked):

| Surface | Finding |
|---|---|
| Dashboard `Project Health` widget | heading "Project Health" + column header "Project" + sort-control aria-label "Sort by project name" + "No matching projects" empty-state |
| Fee Notes list `/invoices` | column header "Customer"; breadcrumb "fee notes" rendered lowercase |
| Create Matter dialog (`CreateProjectDialog`) | placeholders "My Project", "A brief description of the project...", label "Customer (optional)", placeholder "e.g. Consulting, Litigation" |
| Edit Matter dialog (`EditProjectDialog`) | hard-coded "Edit Project" / "Update your project's details." dialog title + description, label "Customer (optional)" |
| New Client dialog (`CreateCustomerDialog`) | email placeholder `customer@example.com` |
| Conflict Check form (`ConflictCheckForm`) | label "Customer (optional)" |
| Adverse-party / disbursement / proposal / retainer / data-request dialogs | label "Customer" hard-coded |
| `NewFromTemplateDialog` (matter-from-template) | placeholder "e.g. PRJ-2026-001"; "Optional description..." |

Out of scope (per user direction):
- URL slugs `/projects`, `/customers`, `/invoices` — UNCHANGED, internal route names only.
- Fee Notes "Help: Invoice lifecycle" tooltip — could not locate hard-coded string in source; tracked separately as a follow-up if QA reproduces in next cycle.
- Portal `/proposals` and similar — Day 90 §90.10 graded **PASS — IMPROVED** (sidebar already uses "Matters" / "Fee Notes"); not part of this basket.

## Root Cause

Two mechanisms:

1. **Bypass of `useTerminology()` hook.** The legal-za vertical maps `Project → Matter`, `Customer → Client`, `Invoice → Fee Note`, and ~25 other terms in `frontend/lib/terminology-map.ts:40-85`. Every site listed above either renders a hard-coded English noun or wires a placeholder/aria-label that is never passed through `t()`.

2. **Breadcrumb segment mismatch.** `frontend/components/breadcrumbs.tsx:8-50` (`SEGMENT_LABELS`) has `projects: "Projects"` / `customers: "Customers"` but no `invoices: "Invoices"` entry. When a user lands on `/org/{slug}/invoices`, the breadcrumb component falls through to `t(segment)` = `t("invoices")` = `"fee notes"` (lowercase from the map). The fix is to add `invoices: "Invoices"` so the lookup returns "Invoices" → `t("Invoices")` → "Fee Notes" (sentence-case).

## Fix Inventory

Frontend-only. Each item is a label-text replacement; no logic, no schema, no backend change. URL slugs untouched.

### 1. Dashboard `Project Health` widget — bypass of `t()`

`frontend/components/dashboard/project-health-widget.tsx`

| Line | Old | New |
|---|---|---|
| 120 | `<CardTitle className="text-sm font-medium">Project Health</CardTitle>` | `<CardTitle className="text-sm font-medium">{t("Project Health")}</CardTitle>` |
| 148 | `aria-label="Sort by project name"` | `aria-label={\`Sort by ${t("project")} name\`}` |
| 153 | `Project` (column header text) | `{t("Project")}` |
| 180 | `<p ...>No matching projects</p>` | `<p ...>{\`No matching ${t("projects")}\`}</p>` |

The component already calls `useTerminology()` at line 62 (`const { t } = useTerminology();`). The line 68 case (empty-state CardTitle) already uses `t("Project Health")` — only the populated-state branch leaked. Tests at `frontend/__tests__/dashboard/company-dashboard.test.tsx:299` assert `"Project Health"` — when no terminology provider is mounted, `t()` is identity, so the test continues to pass unchanged (verified via `lib/terminology.tsx:43-46` `DEFAULT_TERMINOLOGY` fallback).

### 2. Fee Notes list — column header "Customer" + lowercase "fee notes" breadcrumb

`frontend/app/(app)/org/[slug]/invoices/page.tsx`

| Line | Old | New |
|---|---|---|
| 223 | `Customer` (raw text inside `<th>`) | `<TerminologyHeading term="Customer" />` (component already imported and used at line 220 for "Invoice") |

`frontend/components/breadcrumbs.tsx`

| Line | Old | New |
|---|---|---|
| 50 (insert) | n/a | Add entry `invoices: "Invoices",` to `SEGMENT_LABELS` map (after `"client-ledgers": "Client Ledgers"`) |

After this fix the breadcrumb chain for `/invoices` resolves to `SEGMENT_LABELS["invoices"]` = `"Invoices"` → `t("Invoices")` = `"Fee Notes"` (legal-za map). Sentence-case restored.

### 3. Create Matter dialog (`CreateProjectDialog`) — placeholders + label

`frontend/components/projects/create-project-dialog.tsx`

| Line | Old | New |
|---|---|---|
| 139 | `placeholder="My Project"` | `placeholder={t("project.namePlaceholder")}` — see new map entry below |
| 156 | `placeholder="A brief description of the project..."` | `placeholder={\`A brief description of the ${t("project")}...\`}` |
| 187 | `<FormLabel>Customer <span ...>(optional)</span></FormLabel>` | `<FormLabel>{t("Customer")} <span ...>(optional)</span></FormLabel>` |
| 256 | `placeholder="e.g. Consulting, Litigation"` | **Keep as-is** — work-type field is generic, copy already references "Litigation" which is legal-flavoured. Drop the field-level fix; no edit needed. |

For row 139, the cleanest path is to add a new key to the terminology map and use it. Concretely, in `lib/terminology-map.ts`:
- Generic (no profile): `"project.namePlaceholder": "e.g. Q4 Roadmap"` (or fall back to "" — the placeholder is decorative).
- legal-za: `"project.namePlaceholder": "e.g. Dlamini v Road Accident Fund"` (mirrors the actual cycle 57 RAF matter name; resonant for the 90-day demo persona).
- accounting-za: `"project.namePlaceholder": "e.g. FY2026 Audit"`.
- consulting-za: `"project.namePlaceholder": "e.g. Q4 Strategy Engagement"`.

Alternative (lower effort): drop the placeholder entirely (`placeholder=""`) and rely on the `<FormLabel>Name</FormLabel>`. Lower polish, lower spec churn. **Recommended path**: add the map key — under 6 lines added, drives all three verticals + the generic SaaS template fork.

### 4. Edit Matter dialog (`EditProjectDialog`) — title, description, label

`frontend/components/projects/edit-project-dialog.tsx`

The dialog is a hidden gap on the §90.1 walk because cycle 57 only opened the Create dialog; the Edit dialog has the same structural leak. Walking it would expose the same finding next cycle. Sweep it now while we're in the file.

| Line | Old | New |
|---|---|---|
| 113 | `<DialogTitle>Edit Project</DialogTitle>` | `<DialogTitle>{\`Edit ${t("Project")}\`}</DialogTitle>` |
| 114 | `<DialogDescription>Update your project's details.</DialogDescription>` | `<DialogDescription>{\`Update your ${t("project")}'s details.\`}</DialogDescription>` |
| 168 | `<FormLabel>Customer <span ...>(optional)</span></FormLabel>` | `<FormLabel>{t("Customer")} <span ...>(optional)</span></FormLabel>` |

`useTerminology()` is already imported and called in this file (`const { t } = useTerminology();` at the top of the component) — no new hook import/call needed; the line edits above just route the literals through the existing `t()` resolver.

### 5. New Client dialog (`CreateCustomerDialog`) — email placeholder

`frontend/components/customers/create-customer-dialog.tsx`

| Line | Old | New |
|---|---|---|
| 345 | `placeholder="customer@example.com"` | `placeholder={\`${t("customer")}@example.com\`}` |

The component already uses `useTerminology()` (verified line 282 — `t("Create Customer")`). The placeholder leakage is incidental.

### 6. Conflict Check form

`frontend/components/legal/conflict-check-form.tsx`

| Line | Old | New |
|---|---|---|
| 189 | `<FormLabel>Customer (optional)</FormLabel>` | `<FormLabel>{t("Customer")} (optional)</FormLabel>` |

Add `useTerminology()` hook import + call (component does not currently use it). Note: this form is in `components/legal/` — almost always rendered on the legal-vertical, so the leak is highly visible.

### 7. Surprise leakage — sweep while in scope

These were uncovered by the broad grep `grep -rn ">Customer<\|>Customers<\|>Project<\|>Projects<"` — same root cause (hook bypass), same one-line fix per occurrence. Optional but cheap to sweep with the rest:

| File | Line | Old | New |
|---|---|---|---|
| `components/proposals/create-proposal-dialog.tsx` | 226 | `<FormLabel>Customer</FormLabel>` | `<FormLabel>{t("Customer")}</FormLabel>` |
| `components/legal/create-disbursement-dialog.tsx` | 372 | `<FormLabel>Customer</FormLabel>` | `<FormLabel>{t("Customer")}</FormLabel>` |
| `components/legal/link-adverse-party-to-project-dialog.tsx` | 171 | `<FormLabel>Customer *</FormLabel>` | `<FormLabel>{t("Customer")} *</FormLabel>` |
| `components/legal/link-adverse-party-dialog.tsx` | 155 | `<FormLabel>Customer *</FormLabel>` | `<FormLabel>{t("Customer")} *</FormLabel>` |
| `components/retainers/create-retainer-dialog.tsx` | 157 | `<Label>Customer</Label>` | `<Label>{t("Customer")}</Label>` |
| `components/schedules/ScheduleCreateDialog.tsx` | 151 | `<Label htmlFor="schedule-customer">Customer</Label>` | `<Label htmlFor="schedule-customer">{t("Customer")}</Label>` |
| `components/compliance/CreateDataRequestDialog.tsx` | 138 | `<Label>Customer</Label>` | `<Label>{t("Customer")}</Label>` |
| `components/billing-runs/cherry-pick-retainer-section.tsx` | 30 | `<th ...>Customer</th>` | `<th ...><TerminologyHeading term="Customer" /></th>` |
| `components/profitability/customer-financials-tab.tsx` | 61 | `<TableHead>Project</TableHead>` | `<TableHead><TerminologyHeading term="Project" /></TableHead>` |
| `components/time-tracking/csv-import-dialog.tsx` | 370 | `<TableHead className="text-xs">Project</TableHead>` | `<TableHead className="text-xs"><TerminologyHeading term="Project" /></TableHead>` |

Each file needs the `useTerminology()` import (or `TerminologyHeading` import) added — pattern is identical to existing usages and well-rehearsed in this codebase.

**Decision rationale (sweep vs scope-limit)**: The §90.1 walk only enumerated visible leaks on the lifecycle scenario path (Sipho's RAF matter, Dashboard, Fee Notes list, Create Matter dialog). The dialogs in §7 sit on adjacent flows (matter-disbursement, mandate, retainer, scheduled jobs, billing run cherry-pick, profitability tab, CSV import) that the demo persona may exercise off-script. Including them costs ~10 single-line edits, lifts the demo polish floor, and avoids a recurrence on cycle 58/59 walks that exercise these flows. **Recommend sweeping**; if Dev pushes back on scope, the §1-§6 set is the strict §90.1 walk minimum.

### 8. NewFromTemplateDialog — placeholder

`frontend/components/templates/NewFromTemplateDialog.tsx`

| Line | Old | New |
|---|---|---|
| 307 | `placeholder="Optional description..."` | (leave — generic copy is fine) |
| 369 | `placeholder="e.g. PRJ-2026-001"` | `placeholder={t("project.referencePlaceholder") ?? "e.g. PRJ-2026-001"}` |

Same map-key mechanism as §3. legal-za: `"project.referencePlaceholder": "e.g. RAF-2026-001"`. Optional — placeholder is low-signal; only worth doing if §3 lands the new map-key pattern.

The same `e.g. PRJ-2026-001` placeholder also exists at `create-project-dialog.tsx:217` and `edit-project-dialog.tsx:198` — apply the same map-key fix to all three sites or none. Recommend all three.

## Decision Rationale Where Ambiguous

- **Dashboard widget heading: "Matter Health" (selected) vs "Active Matters"**
  Map already says `Project Health → Matter Health`. The widget renders rows with health badges, progress bars, hours/tasks counts — the dominant content is *health status*, not *matter listing*. Keep the existing map → "Matter Health". (`Active Matters` is the metrics-strip KPI tile label at `metrics-strip.tsx:67`, a separate widget, already wired through `t("Active Projects")` correctly.)

- **Email placeholder `customer@example.com`**: Could pick `client@example.com`, `client@firm.co.za`, `sipho@example.com`. Picked `${t("customer")}@example.com` (= `client@example.com` on legal-za) because it is data-driven from the existing map and works for all three vertical profiles + the generic SaaS template fork without adding new keys. Domain stays `example.com` per RFC-2606 reserved namespace — never sends to a real address even if a user copy-pastes the placeholder.

- **Matter name placeholder**: chose "Dlamini v Road Accident Fund" for legal-za to mirror the demo dataset (Sipho's RAF matter is the cycle-57 hero matter at `cc390c4f-…`); resonant for the 90-day demo persona. Reasonable alternatives: "Mthembu v Standard Bank", "Estate Late Khumalo" — picked Dlamini for direct demo-script alignment.

- **`e.g. Consulting, Litigation` work-type placeholder**: keep. "Litigation" is already legal-flavoured; replacing with a vertical-specific list ("Litigation, Conveyancing, Estates, Family Law") would be aspirational scope (not a leak — it's working copy). Per user direction, "reasonable already, keep or drop"; chose keep.

- **Sweep §7 vs scope-limit to §1-§6**: see decision rationale embedded in §7 above. Recommend sweep; under 30-minute incremental effort.

## Files to Modify

Required (§1-§6 — strict §90.1 walk minimum):
- `frontend/components/dashboard/project-health-widget.tsx`
- `frontend/app/(app)/org/[slug]/invoices/page.tsx`
- `frontend/components/breadcrumbs.tsx`
- `frontend/components/projects/create-project-dialog.tsx`
- `frontend/components/projects/edit-project-dialog.tsx` (hook already present; route literals through existing `t()`)
- `frontend/components/customers/create-customer-dialog.tsx`
- `frontend/components/legal/conflict-check-form.tsx` (add `useTerminology` import)
- `frontend/lib/terminology-map.ts` (new keys: `project.namePlaceholder`, `project.referencePlaceholder` in the 3 profile maps; identity fallback unchanged)

Sweep (§7-§8 — recommended):
- `frontend/components/proposals/create-proposal-dialog.tsx`
- `frontend/components/legal/create-disbursement-dialog.tsx`
- `frontend/components/legal/link-adverse-party-to-project-dialog.tsx`
- `frontend/components/legal/link-adverse-party-dialog.tsx`
- `frontend/components/retainers/create-retainer-dialog.tsx`
- `frontend/components/schedules/ScheduleCreateDialog.tsx`
- `frontend/components/compliance/CreateDataRequestDialog.tsx`
- `frontend/components/billing-runs/cherry-pick-retainer-section.tsx`
- `frontend/components/profitability/customer-financials-tab.tsx`
- `frontend/components/time-tracking/csv-import-dialog.tsx`
- `frontend/components/templates/NewFromTemplateDialog.tsx` (placeholder only)

Files NOT modified:
- URL routes, route handlers, server actions — UNCHANGED.
- Backend Java — UNCHANGED.
- Tests — terminology lookups fall back to identity in test renders (no `TerminologyProvider` mounted), so existing assertions like `expect(screen.getByText("Project Health"))` keep passing without modification. New tests are not required for label-only changes; the type system is the safety net.

## Estimated Effort

**S** — 30-45 minutes for required scope (§1-§6); +20 minutes for the §7-§8 sweep. Each edit is a one-line text replacement or hook-wiring boilerplate (`import` + `const { t } = useTerminology();`). No logic, no tests beyond `pnpm run lint && pnpm run build`. HMR picks up changes — no service restart needed (frontend port 3000).

## Test Plan (QA verification on the cycle-58 retest cycle)

QA will, on a Bob admin session in `/org/{slug}/dashboard` and follow-on flows, capture YAML snapshots and confirm:

1. **Dashboard `/dashboard`** — `Project Health` widget renders **"Matter Health"** as the CardTitle in the populated state. Column-header "Project" reads **"Matter"**. Sort-by aria-label reads **"Sort by matter name"**. Empty-state copy reads **"No matching matters"**. Evidence: snapshot `cycle58-day90-90.1-firm-dashboard.yml` containing `text: Matter Health`, `columnheader "Sort by matter name"`, `text: Matter`.

2. **Fee Notes list `/invoices`** — table column header reads **"Client"** (was "Customer"). Breadcrumb at top of page reads **"Mathebula & Partners › Fee Notes"** (sentence-case, was lowercase "fee notes"). Evidence: snapshot containing `columnheader "Client"` and `link "Fee Notes"` (NOT `"fee notes"`).

3. **Create Matter dialog** — open from `/projects` page → `+ New Matter` button → assert: dialog placeholder for Name field reads **"e.g. Dlamini v Road Accident Fund"** (or whichever string the new map key resolves to on legal-za). Description placeholder reads **"A brief description of the matter..."**. Customer FormLabel reads **"Client (optional)"**. Reference Number placeholder reads **"e.g. RAF-2026-001"**. Evidence: snapshot of dialog DOM with these placeholders + label.

4. **Edit Matter dialog** — from a matter detail page, open Edit dialog → assert: dialog title reads **"Edit Matter"**, description reads **"Update your matter's details."**, Customer label reads **"Client (optional)"**.

5. **Create Client dialog** — open from `/customers` page → `+ New Client` button → assert: email placeholder reads **"client@example.com"**.

6. **Conflict Check form** `/conflict-check` — Customer combobox label reads **"Client (optional)"**.

7. **Sweep verification (§7)** — open each of the 10 sites in §7; confirm "Customer" / "Project" labels are now **"Client"** / **"Matter"**. Site list: New Engagement Letter dialog (matter detail), New Disbursement dialog, Link Adverse Party dialogs (×2), New Mandate dialog, ScheduleCreate dialog, CreateDataRequest dialog (Compliance), Billing Runs cherry-pick table, Customer Profitability tab, Time-Tracking CSV import dialog.

8. **Regression invariants**:
   - URL routes UNCHANGED — `/projects`, `/customers`, `/invoices` still resolve, sidebar links still navigate. (`grep -rn "/projects\|/customers\|/invoices" frontend/lib/nav-items.ts` shows no diff post-fix.)
   - Cross-vertical fork test: switch `org_settings.vertical_profile` to `accounting-za` → same surfaces render **"Engagement Health"**, **"Engagement (optional)"**, **"Engagement Letters"**. Confirms the fix is data-driven, not hard-coded to legal-za.
   - Generic / no-profile fork: when `vertical_profile=NULL`, surfaces render the original English defaults (**"Project Health"**, **"Customer (optional)"**, **"Invoices"**) — `t()` falls through to identity. Confirms no regression for the SaaS template fork.

9. **Build + lint gate**:
   - `pnpm run lint` — 0 errors.
   - `pnpm run build` — succeeds.
   - `pnpm test` — Vitest suite green; the existing assertion `expect(screen.getByText("Project Health"))` at `__tests__/dashboard/company-dashboard.test.tsx:299` keeps passing because the test does not mount `TerminologyProvider`, so `t()` returns identity — verified by `lib/terminology.tsx:43-46` `DEFAULT_TERMINOLOGY` fallback contract.

## Severity / Demo Impact

- **Day 90 blocker**: NO. §90.1 already PARTIAL-with-carry-forward in the cycle 57 walk; demo passed without this fix. This is post-Day-90 polish.
- **E.10 isolation**: NOT AT RISK — label-only, no data-plane change.
- **E.14 audit completeness**: NOT AT RISK — no audit-event emissions touched.
- **E.15 demo polish**: **IMPROVED** — every "Customer" → "Client", every "Project" → "Matter" surfaced in the firm UI is on-brand for a legal practice management product. Cumulative impact on the polish floor is high relative to effort.

## Defer / Now Decision Hint

**Recommend FIX NOW (this cycle, slice 1).** Effort is S, risk is near-zero (no logic), rollback is `git revert`, the basket is bounded (no follow-on cascades), and the polish lift is meaningful for the legal-vertical demo. Per the §90.1 walk, this has been carried-forward across cycles 1, 55, and 57 — landing it in cycle 58 closes a 4-cycle-old residual.

## Notes

- **Frontend-only**. No backend, no migration, no Java touched.
- **URL slugs UNCHANGED** — `/projects`, `/customers`, `/invoices` are internal route names and stay intact. Refactoring slugs would require route renames + redirects + nav-item updates + sidebar order — explicitly out of scope.
- **Pattern reuse**: every fix uses the established `useTerminology()` / `TerminologyHeading` / `TerminologyText` primitives in `frontend/lib/terminology.tsx` and `frontend/components/terminology-{heading,text}.tsx`. No new infrastructure.
- **Map extension**: the new `project.namePlaceholder` / `project.referencePlaceholder` keys land in `frontend/lib/terminology-map.ts` — keep dotted notation consistent with future `*.placeholder` style (never used today, but a sensible namespace as the codebase grows).
- **Tracker id**: `TERM-CYCLE57` (no GAP-L-NN assigned per orchestrator direction — GAP-L-101 left free for the §85.3 retest spec). If the tracker prefers GAP-L-numbered ids only, rename to `GAP-L-102` and re-link from this filename.
- **Out of scope follow-ups** (not part of this spec):
  - Fee Notes "Help: Invoice lifecycle" tooltip — string not located in source via grep; tracked as a §90.1 carry-forward investigation if QA reproduces it on cycle 58.
  - "PROJECT_LEAD" select option in `SaveAsTemplateDialog.tsx:261` and `TemplateEditor.tsx:477` — enum-display label, not a user-facing copy issue (renders inside an admin template editor, surfaces the underlying ROLE enum).
  - "Project Template ID" label in `automations/action-form.tsx:335` — admin Automations builder field; deeper than label fix (refers to the data-model `project_templates.id`); leave for an automations-rebrand epic.
  - "Customer data anonymized" / "Customer PII will be anonymized" copy in compliance dialogs (`anonymize-customer-dialog.tsx:199-275`, `DeletionConfirmDialog.tsx:90-124`) — deferred; touches GDPR/POPIA-coded copy where "Customer" maps to a regulatory data-subject term, not the CRM "Client" entity. Separate spec recommended.
