You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with schema-per-tenant isolation (dedicated schema for every tenant, no shared schema path).
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, and notes. Project time rollups and "My Work" cross-project dashboard.
- **Audit event infrastructure** (Phase 6): domain mutation logging with queryable API.
- **Comments, notifications, and activity feeds** (Phase 6.5): in-app notification system with `ApplicationEvent`-based fan-out, notification preferences, comment system on tasks/documents, project activity feed.
- **Customer portal backend** (Phase 7): magic links, read-model schema, portal contacts, portal APIs.
- **Rate cards, budgets & profitability** (Phase 8): `BillingRate` (3-level hierarchy: org-default → project-override → customer-override), `CostRate`, `ProjectBudget`, `OrgSettings` (default currency). Time entries have `billable` flag, `billing_rate_snapshot`, `cost_rate_snapshot`. Profitability reports.
- **Operational dashboards** (Phase 9): company dashboard with health scoring, project overview, personal "My Work" dashboard.
- **Invoicing & billing from time** (Phase 10): Invoice/InvoiceLine entities, draft-to-paid lifecycle, unbilled time management, PSP adapter seam, HTML invoice preview via Thymeleaf.
- **Tags, custom fields & views** (Phase 11): `FieldDefinition`, `FieldGroup`, `Tag`, `EntityTag`, `SavedView` entities. JSONB custom field values on projects, tasks, and customers. Platform-shipped field packs with per-tenant seeding. Saved filtered views with custom column selection.
- **Document templates & PDF generation** (Phase 12): `DocumentTemplate`, `GeneratedDocument` entities. Thymeleaf + OpenHTMLToPDF rendering pipeline. Template packs (seed data). Org branding (logo, brand color, footer text on OrgSettings).
- **Dedicated schema for all tenants** (Phase 13): Eliminated shared-schema path entirely. All tenants use dedicated schemas. No `tenant_id` columns, no `@Filter`/`@FilterDef`, no `TenantAware` interface. Schema boundary provides isolation.
- **Customer compliance & lifecycle** (Phase 14, in progress): Customer lifecycle state machine (Prospect → Onboarding → Active → Dormant → Offboarded), checklist engine (`ChecklistTemplate` → `ChecklistInstance` → `ChecklistItem`), compliance packs (SA FICA, generic onboarding), data subject request handling, retention policies.

For **Phase 15**, I want to add **Contextual Actions & Setup Guidance** — making entity detail pages aware of what's missing and what to do next. The platform has accumulated many powerful features across 14 phases, but they're siloed across tabs and pages. Users must already know the system to use it well. This phase surfaces the right action at the right moment on entity detail pages.

***

## Objective of Phase 15

Design and specify:

1. **Setup status aggregation backend** — a read-only service layer that computes completeness for projects and customers by querying existing systems (custom fields, rate cards, budgets, team members, compliance checklists). No new database tables — this is pure aggregation over existing data.
2. **Project setup guidance** — a "Setup" card on the project detail page showing which configuration steps are complete/incomplete (customer assigned, rate card configured, budget set, team members added, required custom fields filled), with actionable links to resolve each gap.
3. **Customer readiness status** — same pattern for the customer detail page (required custom fields filled, compliance checklist progress, lifecycle status, linked projects).
4. **Custom field value cards** — display custom field values as read-only cards on entity detail pages (not just in a separate tab), with unfilled required fields highlighted and "Fill now" links.
5. **Contextual document generation** — smart "Generate document" actions that appear on project and customer detail pages when prerequisites are met (e.g., "Generate engagement letter" when customer fields are sufficient, "Generate invoice" when unbilled time exists). Buttons disabled with explanatory tooltips when prerequisites are not met.
6. **Unbilled time prompts** — action cards on project and customer detail pages showing unbilled time amounts with direct links to invoice generation.
7. **Empty state guidance** — meaningful empty states across the app that explain what to do next with quick-action buttons (not just "No items yet").

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Keep the existing stack:
    - Spring Boot 4 / Java 25.
    - Neon Postgres (dedicated schema per tenant).
    - Next.js 16 frontend with Shadcn UI.
- **No new database entities.** This phase is a pure aggregation + UI layer over existing data. No Flyway migrations, no new tables, no new columns.
- **Hardcoded-but-smart setup checks, not a configurable engine.** The system knows that a project needs a rate card, budget, team members, and filled custom fields. These checks are implemented as service methods, not as admin-configurable "setup step" definitions. This avoids adding a fourth configurable engine (after custom fields, compliance checklists, and document templates). The existing engines already provide the configurability — setup status just reads from them.
- **No dashboard changes.** Contextual actions live on entity detail pages where the user is already looking at the entity. The existing company and personal dashboards are not modified.
- **No form rework.** Custom fields stay in their current edit flow. This phase surfaces field values on detail pages (read-only) and links to the existing edit mechanisms.

2. **Tenancy**

- All new backend endpoints follow the existing dedicated-schema tenant isolation model. No special tenancy considerations — queries run against the tenant's schema automatically.

3. **Permissions model**

- **Setup status endpoints**: Same permissions as the parent entity. If you can view a project, you can see its setup status. If you can view a customer, you can see their readiness status.
- **Contextual actions**: The UI shows actions only to users who have permission to perform them. E.g., "Set rate card" link appears only for admins/owners. "Generate invoice" appears only for users with invoice creation permission.

4. **Relationship to existing entities**

- **Project**: Setup status reads from `ProjectBudget`, `BillingRate`, `ProjectMember`, `FieldDefinition` + custom field values. No Project entity changes.
- **Customer**: Readiness status reads from lifecycle status (Phase 14), `ChecklistInstance` completion (Phase 14), `FieldDefinition` + custom field values. No Customer entity changes.
- **FieldDefinition / FieldGroup** (Phase 11): Setup status checks which required fields have values set. Uses existing `FieldDefinition.required` flag and `custom_field_values` JSONB on the entity.
- **BillingRate** (Phase 8): Setup status checks whether a project-level or customer-level rate override exists, or whether the org default is sufficient.
- **ProjectBudget** (Phase 8): Setup status checks whether a budget has been configured for the project.
- **DocumentTemplate** (Phase 12): Contextual generation checks which templates are applicable to the entity type and whether their required context fields are populated.
- **Invoice / TimeEntry** (Phase 10): Unbilled time prompts query for time entries where `invoice_id IS NULL AND billable = true`.
- **ChecklistInstance** (Phase 14): Customer readiness includes checklist completion percentage.

5. **Out of scope for Phase 15**

- Admin configuration UI for setup steps (which steps apply, custom ordering, per-entity-type configuration). The checks are hardcoded and sufficient for all foreseeable vertical forks.
- Dashboard modifications (company dashboard, personal dashboard, "My Work"). Entity detail pages only.
- Custom field inline editing in create/edit forms. Fields are displayed read-only on detail pages with links to the existing edit flow.
- Workflow automation or triggers (e.g., "when all setup steps complete, send a notification"). This is a display layer, not a workflow engine.
- Setup status persistence or history (tracking when a project became "fully set up"). The status is computed on-the-fly from current data.

***

## What I want you to produce

Produce a **self-contained markdown document** that can be added as `architecture/phase15-contextual-actions-setup-guidance.md`, plus ADRs for key decisions.

### 1. Setup status aggregation backend

Design the **setup status** service layer:

1. **ProjectSetupStatusService**

    A service that computes project setup completeness. Returns a `ProjectSetupStatus` record containing:

    - `customerAssigned` (boolean) — does the project have a linked customer?
    - `rateCardConfigured` (boolean) — does the project have at least one billing rate (project-level override or org default)?
    - `budgetConfigured` (boolean) — does the project have a `ProjectBudget` record?
    - `teamAssigned` (boolean) — does the project have at least one `ProjectMember` beyond the creator?
    - `requiredFieldsFilled` (object) — for each required custom field on the PROJECT entity type: field name, whether it has a value. Summary: `filledCount` / `totalRequired`.
    - `overallComplete` (boolean) — all checks pass.
    - `completionPercentage` (int) — weighted percentage (each check is equal weight, custom fields count as one check collectively).

    This service calls existing repositories — no new database queries beyond what already exists. It aggregates results from `CustomerRepository`, `BillingRateRepository`, `ProjectBudgetRepository`, `ProjectMemberRepository`, and `FieldDefinitionRepository` + the project's `customFieldValues`.

2. **CustomerReadinessService**

    A service that computes customer readiness. Returns a `CustomerReadiness` record containing:

    - `lifecycleStatus` (enum) — current lifecycle status from Customer entity.
    - `checklistProgress` (object, nullable) — if a checklist instance exists: `completedItems` / `totalRequiredItems`, `percentComplete`. Null if no checklist.
    - `requiredFieldsFilled` (object) — same pattern as project: field name, has value, summary counts.
    - `hasLinkedProjects` (boolean) — does this customer have at least one project?
    - `overallReadiness` (string) — computed summary: "Complete", "In Progress", "Needs Attention".

3. **UnbilledTimeSummaryService**

    A service that computes unbilled time amounts. Can be scoped to a project or a customer.

    - `projectUnbilledTime(projectId)` — returns: `totalHours`, `totalAmount` (hours × billing rate snapshot), `entryCount`.
    - `customerUnbilledTime(customerId)` — aggregated across all projects linked to the customer.

    This builds on existing queries — `TimeEntryRepository` already supports filtering by `billable = true` and `invoice_id IS NULL`. The new service adds the monetary aggregation using `billing_rate_snapshot`.

4. **DocumentGenerationReadiness**

    A service that checks whether a document template's required context fields are available for a given entity.

    - `checkTemplateReadiness(templateId, entityType, entityId)` — returns: `ready` (boolean), `missingFields` (list of field names that are required by the template's context builder but not yet populated on the entity).

    This works with the existing template context builder pattern (Phase 12). Each context builder (ProjectContextBuilder, CustomerContextBuilder, InvoiceContextBuilder) already assembles a data map — this service checks if the required keys would be populated.

5. **API Endpoints**

    - `GET /api/projects/{id}/setup-status` — returns `ProjectSetupStatus`. Same auth as project detail.
    - `GET /api/customers/{id}/readiness` — returns `CustomerReadiness`. Same auth as customer detail.
    - `GET /api/projects/{id}/unbilled-summary` — returns unbilled time summary for the project.
    - `GET /api/customers/{id}/unbilled-summary` — returns unbilled time summary aggregated across the customer's projects.
    - `GET /api/document-templates/readiness?entityType={type}&entityId={id}` — returns readiness status for all applicable templates for the given entity. Returns a list of `{ templateId, templateName, ready, missingFields }`.

    All endpoints return JSON response bodies. No mutations — these are all GET endpoints.

### 2. Project detail page enhancement

Design the **project setup guidance** UI:

1. **Setup Progress Card**

    - Positioned prominently on the project detail page — either at the top of the page (before tabs) or as the first card in the "Overview" tab.
    - Shows a progress indicator: "3 of 5 setup steps complete" with a visual progress bar.
    - Lists each setup step with status:
        - ✅ "Customer assigned" — or ⚠️ "No customer assigned" with "Assign Customer" link
        - ✅ "Rate card configured" — or ⚠️ "No rate card" with "Configure Rates" link
        - ✅ "Budget set" — or ⚠️ "No budget configured" with "Set Budget" link
        - ✅ "Team members added" — or ⚠️ "No team members" with "Add Members" link
        - ✅ "Required fields filled (4/4)" — or ⚠️ "Required fields incomplete (2/4)" with "Fill Fields" link
    - Each link navigates to the relevant tab/section or opens the relevant dialog.
    - **Auto-hides when all steps are complete** — replaced by a subtle "Setup complete ✓" badge. Can be expanded if the user wants to review.
    - Uses existing Shadcn Card, Progress, and Badge components.

2. **Contextual Action Cards**

    - **Unbilled time card**: Appears when the project has unbilled time entries. Shows: "R12,400 unbilled (23.5 hours)" with a "Create Invoice" button that navigates to invoice creation pre-filled with the project.
    - **Document generation card**: Shows available templates with readiness status. "Generate Engagement Letter ✓ Ready" (clickable) vs. "Generate Project Proposal ⚠️ Missing: customer address" (disabled, tooltip explains what's missing).

3. **Custom Field Values Section**

    - Below the setup card or in a dedicated section, show custom field values as a compact card grid.
    - Each field: label, value (or "Not set" in muted text for empty fields).
    - Required fields that are empty are highlighted with a subtle warning style.
    - "Edit Fields" link at the top that navigates to the custom fields edit view.
    - Only shows fields that have the PROJECT entity type. Grouped by FieldGroup if groups exist.

### 3. Customer detail page enhancement

Design the **customer readiness** UI:

1. **Readiness Card**

    - Same pattern as project setup card but tailored to customers.
    - Steps:
        - Lifecycle status badge (colour-coded, same as Phase 14 design)
        - Checklist progress: "Onboarding: 3 of 5 items complete" with progress bar — or "No checklist" / "Onboarding complete ✓"
        - Required fields: "4 of 6 filled" with link to fill
        - Linked projects: "2 active projects" or "No projects linked" with "Create Project" link

2. **Contextual Action Cards**

    - **Unbilled time card**: Aggregated across the customer's projects. "R28,500 unbilled across 3 projects" with "View Unbilled" link.
    - **Document generation card**: Same pattern as project — shows applicable templates with readiness.
    - **Lifecycle action prompt**: If the customer is in PROSPECT status: "Ready to start onboarding? Start Onboarding →". If ONBOARDING with complete checklist: "All items verified — Activate Customer →".

3. **Custom Field Values Section**

    - Same pattern as project — compact card grid of custom field values.
    - For customers, this is especially valuable because compliance packs (Phase 14) seed customer-specific fields (e.g., ID number, risk rating) that need to be visible at a glance.

### 4. Contextual document generation

Design the **smart document generation** UI:

1. **Template readiness check**

    - When rendering the document generation section on a detail page, call the readiness endpoint for all applicable templates.
    - Group templates by readiness: "Ready to generate" (green) vs. "Missing prerequisites" (amber).
    - Ready templates show a "Generate" button that opens the existing generation dialog (Phase 12).
    - Not-ready templates show a disabled button with a tooltip listing missing fields: "Fill these fields first: Customer Address, Tax Number".

2. **Inline generation trigger**

    - On the project page: "Generate" dropdown in the page header actions (alongside existing actions like Edit, Delete).
    - On the customer page: same pattern.
    - The dropdown lists applicable templates with readiness indicators inline.

3. **Invoice generation shortcut**

    - On the project page, if unbilled time exists: a prominent "Generate Invoice" action that navigates directly to the invoice creation flow pre-populated with the project's unbilled time entries.
    - On the customer page: "Generate Invoice" with a project selector (since a customer may have multiple projects with unbilled time).

### 5. Unbilled time prompts

Design the **unbilled time** UI:

1. **Project unbilled time card**

    - Appears on the project detail page (Overview tab or Financials tab) when `entryCount > 0`.
    - Shows: total hours, total amount (formatted with org currency), entry count.
    - Primary action: "Create Invoice" → navigates to invoice creation.
    - Secondary action: "View Entries" → navigates to time entries filtered to unbilled.
    - Styling: uses a subtle highlight/accent background to draw attention without being intrusive.

2. **Customer unbilled time card**

    - Appears on the customer detail page when any linked project has unbilled time.
    - Shows: aggregated total hours and amount across all projects, with per-project breakdown.
    - Primary action: "Create Invoice" → opens invoice creation with project selector.

3. **Empty state when no unbilled time**

    - Do not show the card at all — no "You have no unbilled time" message. Only show when there's something actionable.

### 6. Empty state guidance

Design **meaningful empty states** for key sections across the app:

1. **Empty state pattern**

    Each empty state should include:
    - An icon or illustration (use existing Lucide icons, not custom illustrations).
    - A heading explaining what this section is for (1 line).
    - A subtext explaining the first step to take (1-2 lines).
    - A primary action button to perform that first step.

2. **Specific empty states to implement**

    - **Project tasks tab** (no tasks): "Tasks help you track work on this project. Create your first task to get started." → "Add Task" button.
    - **Project time entries** (no time logged): "Track time spent on this project to enable billing and profitability tracking." → "Log Time" button.
    - **Project documents** (no documents): "Upload proposals, contracts, and deliverables for this project." → "Upload Document" button.
    - **Project team** (no members beyond creator): "Add team members to collaborate on this project." → "Add Member" button.
    - **Customer projects** (no linked projects): "Create a project for this customer to start tracking work." → "Create Project" button.
    - **Customer documents** (no documents): "Upload contracts, ID documents, and correspondence for this customer." → "Upload Document" button.
    - **Invoice list** (no invoices): "Generate invoices from tracked time to bill your customers." → "Create Invoice" button.
    - **Custom fields** (no values set on entity): "Custom fields let you track additional information specific to your workflow." → "Fill Fields" button.

3. **Implementation approach**

    - Create a reusable `EmptyState` component that accepts: `icon`, `title`, `description`, `actionLabel`, `actionHref` (or `onAction` callback).
    - Replace existing "No items" text in each section with the `EmptyState` component.
    - The component should be compact — not a full-page empty state, but an inline card that fits within a tab panel or list container.

### 7. API endpoints summary

All endpoints for this phase are **read-only GET requests** that aggregate existing data:

1. **Project setup status**
    - `GET /api/projects/{id}/setup-status`
    - Auth: valid Clerk JWT + project access (same as `GET /api/projects/{id}`).
    - Response: `{ customerAssigned, rateCardConfigured, budgetConfigured, teamAssigned, requiredFields: { filled, total, fields: [{ name, slug, filled }] }, completionPercentage, overallComplete }`

2. **Customer readiness**
    - `GET /api/customers/{id}/readiness`
    - Auth: valid Clerk JWT + customer access.
    - Response: `{ lifecycleStatus, checklistProgress: { completed, total, percentComplete } | null, requiredFields: { filled, total, fields: [...] }, hasLinkedProjects, overallReadiness }`

3. **Project unbilled summary**
    - `GET /api/projects/{id}/unbilled-summary`
    - Auth: valid Clerk JWT + project access.
    - Response: `{ totalHours, totalAmount, currency, entryCount }`

4. **Customer unbilled summary**
    - `GET /api/customers/{id}/unbilled-summary`
    - Auth: valid Clerk JWT + customer access.
    - Response: `{ totalHours, totalAmount, currency, entryCount, byProject: [{ projectId, projectName, hours, amount, entryCount }] }`

5. **Document template readiness**
    - `GET /api/document-templates/readiness?entityType={PROJECT|CUSTOMER}&entityId={uuid}`
    - Auth: valid Clerk JWT + entity access.
    - Response: `{ templates: [{ templateId, templateName, templateSlug, ready, missingFields: [string] }] }`

### 8. ADRs for key decisions

Add ADR-style sections for:

1. **Hardcoded setup checks vs. configurable setup engine**:
    - Why setup completeness checks are implemented as service methods with hardcoded knowledge of what constitutes a "fully set up" project/customer, rather than an admin-configurable "setup step" system.
    - The platform already has three configurable engines (custom fields, compliance checklists, document templates). Adding a fourth would increase admin cognitive load with diminishing returns. The definition of "fully set up" is stable across verticals — what varies is which custom fields and checklists are seeded, which is already handled by existing pack systems.
    - Trade-off: if a vertical fork needs a completely different definition of "fully set up," the fork developer modifies the service class. This is acceptable because setup definitions change rarely and the service is small.

2. **Computed status vs. persisted status**:
    - Why setup status and readiness are computed on-the-fly from existing data rather than persisted in a new table.
    - Computed status is always consistent with reality — no sync bugs, no stale data, no event handlers to maintain. The aggregation queries are lightweight (existing indexed columns, small result sets).
    - Trade-off: every detail page load triggers a few extra queries. But these are simple indexed lookups, and the result can be cached in the frontend (SWR/React Query) for the session.

3. **Entity detail pages vs. dashboard as action surface**:
    - Why contextual actions live on entity detail pages rather than a central "action items" dashboard.
    - Entity detail pages are where the user is already looking at the entity — the context is immediate. A dashboard requires the user to navigate away, lose context, and then navigate back. Detail page actions have a shorter cognitive loop: see problem → fix problem → stay on page.
    - The existing dashboards (Phase 9) already serve the "cross-entity overview" role. This phase complements them with entity-specific depth.

Use the same ADR format as previous phases (Status, Context, Options, Decision, Rationale, Consequences).

***

## Style and boundaries

- **No new database tables or migrations.** This phase is a pure read/aggregation layer over existing data, plus frontend UI enhancements. If an implementation requires a new column or table, it's out of scope — find a way to derive the information from existing data.
- **Hardcoded is fine.** The `ProjectSetupStatusService` knows that a project needs a customer, rates, budget, team, and fields. This knowledge is in code, not configuration. When a vertical fork needs different checks, the fork developer modifies the service — this is a 10-line change, not a framework redesign.
- **Progressive disclosure.** Setup cards are prominent when there's work to do, but collapse or hide when everything is complete. The goal is to guide new users without annoying experienced ones.
- **Actionable, not informational.** Every status indicator must link to the action that resolves it. "No rate card configured" without a link to configure one is useless. Every ⚠️ must pair with a →.
- **Respect existing navigation.** Don't move existing UI elements or restructure page layouts. Add setup cards and action cards into existing page structures. The project detail page already has an Overview tab — add the setup card there.
- **Consistent component patterns.** Create reusable components (`SetupProgressCard`, `ActionCard`, `EmptyState`, `FieldValueGrid`) that work across both project and customer detail pages. Don't implement bespoke UI for each entity type.
- **No workflow automation.** This phase surfaces information and links to actions. It does not automatically perform actions (no "auto-create invoice when unbilled time exceeds threshold"). The user decides and acts.
- All new backend services follow existing patterns — `@Service`, `@Transactional(readOnly = true)`, constructor injection, `ScopedValue` for tenant context.
- Frontend additions use the existing Shadcn UI component library and the project's Tailwind v4 design tokens.
- Keep API responses flat and simple. No nested pagination, no polymorphic response types. Each endpoint returns a single JSON object with scalar or array fields.

Return a single markdown document as your answer, ready to be added as `architecture/phase15-contextual-actions-setup-guidance.md` and ADRs.
