# Bug Tracker

Bugs found during product walkthrough testing. Each entry has enough context for an agent to investigate and fix independently.

**Format**: Each bug gets an ID (BUG-NNN), severity, affected area, description, root cause (if known), and fix guidance.

**Severities**: `critical` (blocks core flow), `high` (feature broken), `medium` (works but wrong), `low` (cosmetic/minor)

---

## BUG-001: New customers default to ACTIVE, bypassing onboarding lifecycle

**Severity**: high
**Area**: Backend — Customer creation
**Found in**: Walkthrough Chapter 2 (Onboarding Your First Client)

**Description**: When creating a customer via the UI, the customer is immediately set to `ACTIVE` lifecycle status. The PROSPECT and ONBOARDING stages are never reachable through normal creation flow, making the entire onboarding lifecycle (checklists, FICA compliance, transition prompts) unreachable for new customers.

**Root Cause**: `Customer.java` line 94 — the constructor hardcodes `this.lifecycleStatus = LifecycleStatus.ACTIVE`. The `CustomerService.createCustomer()` (line 118) uses this constructor without passing a lifecycle status. A second constructor accepting `LifecycleStatus` exists (line 97) but is never called from the service.

**Affected Files**:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` (line 94)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` (line 118)
- Possibly `frontend/components/customers/create-customer-dialog.tsx` (no lifecycle field in create form)

**Fix Guidance**:
1. Change default in `Customer` constructor from `LifecycleStatus.ACTIVE` to `LifecycleStatus.PROSPECT`
2. Optionally add a lifecycle status dropdown to the create customer dialog (Prospect as default, Active as shortcut for existing clients being imported)
3. Check and update any tests that assert `ACTIVE` as the default lifecycle status after creation
4. Verify the `LifecycleTransitionDropdown` on the customer detail page renders correctly for PROSPECT status (it currently gates on `customer.status === "ACTIVE" && customer.lifecycleStatus` — the `customer.status` here is the archive status, not lifecycle, so this should still work)

**Impact**: The entire Phase 14 (Customer Compliance & Lifecycle) onboarding flow is effectively dead code for newly created customers. Existing customers in the database are also all ACTIVE.

---

## BUG-002: Settings items (templates, compliance packs, checklists) cannot be opened or viewed

**Severity**: medium
**Area**: Frontend — Settings pages (templates, compliance, checklists)
**Found in**: Settings page walkthrough

**Description**: On the Settings page, seeded document templates, compliance packs, and checklists are listed but cannot be clicked to view their contents. All items should be viewable (read-only for seeded, editable for custom). Currently none of them link to a detail/view page. Custom checklists are also non-editable despite being user-created.

**Root Cause**: All three Settings sub-pages were built as "list-only" — table rows render item names as plain `<span>` text with no click handler or navigation link. Detail/view pages either don't exist or exist but aren't linked from the list.

**Sub-items**:

### A) Document Templates — no click-to-view, PLATFORM templates have no "View" action

- `frontend/app/(app)/org/[slug]/settings/templates/templates-content.tsx` line 101: template name is `<span className="font-medium">{template.name}</span>` — not a `<Link>`.
- `frontend/components/templates/TemplateActionsMenu.tsx` line 86-95: "Edit" action only shows for `ORG_CUSTOM` templates. PLATFORM templates only get "Clone & Customize" — no "View" option.
- The edit page exists at `settings/templates/[id]/edit/page.tsx` and works, but is unreachable from the list for PLATFORM templates.
- Backend API `GET /api/document-templates/{id}` exists and returns full detail including template body.

### B) Compliance Packs — static display, no detail view

- `frontend/app/(app)/org/[slug]/settings/compliance/page.tsx` lines 82-116: packs render as static `<div>`s showing only `packId` and version. No click handler, no expand, no detail view.
- Pack contents are spread across seeded entities (checklist templates, field definitions, retention overrides) via `CompliancePackDefinition` records.
- No backend API exists to retrieve a pack's full definition — the pack JSON lives on the classpath (`CompliancePackSeeder`), not exposed via REST.

### C) Checklists — no detail/edit page, items not viewable

- `frontend/app/(app)/org/[slug]/settings/checklists/page.tsx` lines 111-157: rows show item count (`template.items.length`) but items aren't viewable. Template name is plain text, not a link.
- `frontend/components/compliance/ChecklistTemplateActions.tsx`: only offers Clone and Deactivate — no View or Edit action.
- A `new` page exists for creating templates, but no `[id]` or `[id]/edit` route exists for viewing/editing.
- Backend API `GET /api/checklist-templates/{id}` exists and returns `ChecklistTemplateResponse` including the full `items` list.

**Affected Files**:
- `frontend/app/(app)/org/[slug]/settings/templates/templates-content.tsx` — make template name a link to detail page
- `frontend/app/(app)/org/[slug]/settings/templates/[id]/edit/page.tsx` — add read-only mode for PLATFORM templates (or create a separate `[id]/page.tsx` view route)
- `frontend/components/templates/TemplateActionsMenu.tsx` — add "View" action for PLATFORM templates
- `frontend/app/(app)/org/[slug]/settings/checklists/page.tsx` — make checklist name a link
- `frontend/app/(app)/org/[slug]/settings/checklists/[id]/page.tsx` — NEW: detail/view page showing items (read-only for PLATFORM, editable for ORG_CUSTOM)
- `frontend/components/compliance/ChecklistTemplateActions.tsx` — add View/Edit actions
- `frontend/app/(app)/org/[slug]/settings/compliance/page.tsx` — make pack clickable, expand to show contents

**Fix Guidance**:

**A) Document Templates**:
1. In `templates-content.tsx`, wrap the template name `<span>` in a `<Link href={/org/${slug}/settings/templates/${template.id}/edit}>` for all templates (custom and platform).
2. In the existing `[id]/edit/page.tsx`, detect if the template is PLATFORM source and render the `TemplateEditorForm` in read-only mode (disable save button, make fields non-editable). Alternatively, create a separate `[id]/page.tsx` as a view-only route and keep `[id]/edit/` for custom templates only.
3. In `TemplateActionsMenu.tsx`, add a "View" menu item for PLATFORM templates that navigates to the detail page (same route as edit, but read-only).

**B) Compliance Packs**:
1. Create a backend endpoint `GET /api/compliance-packs/{packId}` that reads the pack JSON from classpath and returns its contents (checklist template items, field definitions, retention overrides).
2. In `compliance/page.tsx`, make the pack row clickable — either navigate to a detail page or expand inline to show the pack's checklist items, field definitions, and retention overrides.
3. Alternative (simpler): since pack contents are already seeded as checklist templates and field definitions, link from the pack to the relevant checklists/fields settings pages filtered by `packId`.

**C) Checklists**:
1. Create `frontend/app/(app)/org/[slug]/settings/checklists/[id]/page.tsx` — a detail page that fetches `GET /api/checklist-templates/{id}` and displays the template's items in an ordered list (name, description, required flag, document requirement).
2. For `ORG_CUSTOM` templates, make items editable (inline edit or separate edit form). For `PLATFORM` templates, render read-only.
3. In `checklists/page.tsx`, wrap the template name in a `<Link>` to the new detail page.
4. In `ChecklistTemplateActions.tsx`, add a "View" button (or make it "Edit" for custom templates).
5. Add a frontend API function `getChecklistTemplate(id)` in `lib/api.ts` to call `GET /api/checklist-templates/{id}`.

**Impact**: Users cannot inspect the contents of seeded items to understand what they comprise. Custom checklists cannot be edited after creation. This undermines trust in the compliance setup — users see "5 items" but can't verify what those items are.

---

## BUG-003: Template preview requires raw UUID input instead of entity picker dropdown

**Severity**: medium
**Area**: Frontend — Template editor preview
**Found in**: Settings > Templates > Edit > Preview

**Description**: When previewing a document template, the user must manually type or paste a UUID into a text input to select the entity (project, customer, or invoice) for rendering context. Users don't have UUIDs memorised — they need a searchable dropdown that shows entity names. Additionally, the UI labels (e.g. "Project ID") imply only that entity's data is used, when in fact the backend context builders cascade and resolve related entities (e.g. a PROJECT template also resolves customer, lead, members, budget, org, and tags).

**Root Cause**: `frontend/components/templates/TemplatePreviewDialog.tsx` lines 73-87 — renders a plain `<Input>` for UUID entry with label `{entityLabel} ID`. No API call is made to fetch a list of selectable entities. The backend context builders (`ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder`) already resolve all related entities from the primary entity ID, so the data produced is complete — the problem is purely the input UX.

**Affected Files**:
- `frontend/components/templates/TemplatePreviewDialog.tsx` — replace UUID input with entity picker dropdown
- Possibly `frontend/lib/api.ts` — may need to add/reuse API functions for listing entities in a select-friendly format

**Fix Guidance**:
1. In `TemplatePreviewDialog.tsx`, replace the `<Input>` (line 77) with a searchable `<Combobox>` or `<Command>`-based dropdown (Shadcn Command component is already in the project).
2. Fetch the appropriate entity list based on `entityType`:
   - `PROJECT` → `GET /api/projects` (already used on Projects page)
   - `CUSTOMER` → `GET /api/customers` (already used on Customers page)
   - `INVOICE` → `GET /api/invoices` (already used on Invoices page)
3. Display entity names (and relevant secondary info like customer email or project description) in the dropdown options. Store the selected entity's UUID as the value passed to `previewTemplateAction`.
4. Change the label from `{entityLabel} ID` to `Select a {entityLabel}` to communicate intent.
5. Optionally, add a helper note below the dropdown: "The preview will include all related data (customer, members, org settings, etc.) resolved from the selected {entityLabel}." This addresses the perception that only one entity's data is used.
6. The backend needs no changes — `POST /api/templates/{id}/preview` with `{ entityId }` already works correctly.

**Impact**: Users cannot practically use the template preview feature without developer tools or database access to look up UUIDs. The feature exists but is effectively unusable for non-technical users.

---

## BUG-004: Retainer consumption stays at zero — silent date-range mismatch + no feedback

**Severity**: high
**Area**: Both — Retainer consumption / time tracking
**Found in**: Walkthrough — logging time against a retainer customer's task

**Description**: After logging 2 sets of hours against a task on a project linked to a retainer customer (40h HOUR_BANK), the retainer summary still shows "0 of 40 hours" consumed. Page refresh doesn't help — consumed hours remain 0 in the database.

**Root Cause**: The `RetainerConsumptionListener` calculates consumption via a native SQL query (`sumConsumedMinutes`) that filters time entries by date range: `te.date >= :periodStart AND te.date < :periodEnd`. The period dates are derived from the retainer's `startDate` + frequency (e.g., MONTHLY → `[startDate, startDate+1month)`). **If time entries fall outside this range, the sum is 0 and the listener silently updates consumption to 0.**

Three compounding issues:

1. **Date-range mismatch (primary cause)**: If the retainer start date doesn't encompass today's date within its period window, time logged today scores 0. Example: retainer startDate = 2026-03-01 (MONTHLY) → period = [Mar 1, Apr 1). Time logged on Feb 21 → outside range → sum = 0. There is NO validation or warning when logging time outside the active retainer period.

2. **Silent failure pattern**: `RetainerConsumptionListener.onTimeEntryChanged()` wraps all logic in a try/catch that swallows exceptions (ADR-074 "self-healing"). If the listener fails OR computes 0, the user gets identical feedback: nothing. There's no distinction between "consumption updated to 0 because no billable hours in range" and "consumption update failed."

3. **No frontend feedback loop**: After creating a time entry, the frontend doesn't revalidate retainer data, show consumption impact, or warn when the entry date falls outside the retainer period.

The consumption query itself is correct (tested in `RetainerConsumptionListenerTest` — 10 passing tests). The issue is the gap between what the user expects (log time → hours decrement) and what happens when dates don't align.

**Affected Files**:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerConsumptionListener.java` — needs to surface when consumption is 0 despite time entries existing
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodRepository.java` — `sumConsumedMinutes()` query (line 36-49) — correct but silent on date exclusions
- `frontend/components/tasks/log-time-dialog.tsx` — no retainer context shown, no warning for out-of-period dates
- `frontend/app/(app)/org/[slug]/projects/[id]/time-entry-actions.ts` — no revalidation of retainer data after time entry creation
- `frontend/components/customers/retainer-progress.tsx` — stale display, no refresh trigger

**Fix Guidance**:

**Backend (consumption reliability)**:
1. In `RetainerConsumptionListener.handleTimeEntryChanged()`, after step 4 (consumption query), add an INFO log that includes the period date range and the time entry count/sum. This makes "0 because out of range" visible in logs vs "0 because query failed."
2. Consider adding a validation in `TimeEntryService.createTimeEntry()`: when the task's project is linked to a customer with an active retainer, check if the time entry's date falls within the current open period. If not, log a warning. This doesn't block creation — just surfaces the mismatch.

**Frontend (feedback loop)**:
3. In `time-entry-actions.ts`, after a successful `createTimeEntry` call, add `revalidatePath` for the project page and customer pages to force server components to refetch retainer data.
4. In the `LogTimeDialog`, when the task's project has a retainer customer, fetch the current period dates and show a subtle indicator: "Current retainer period: Feb 1 – Mar 1". If the selected date falls outside this range, show an amber warning: "This date is outside the current retainer period — hours won't count toward the retainer."
5. After successful time entry creation, show a toast with retainer impact: "Logged 2h — retainer: 4h / 40h consumed" (requires a follow-up API call to `GET /api/customers/{id}/retainer-summary`).

**Data fix (if applicable)**:
6. If the retainer was created with wrong start date, user can edit the retainer's start date from the retainer detail page (if the period hasn't been closed). This will recompute the period range. Alternatively, create a new retainer with the correct start date.

**Impact**: The retainer feature appears completely broken — users log time and nothing changes. The actual computation is correct, but the lack of date-range feedback makes it seem like the system doesn't track consumption at all. This undermines trust in the entire billing/retainer system.

---

## BUG-005: Report parameter form requires raw UUID input instead of entity picker

**Severity**: high
**Area**: Frontend — Reports
**Found in**: Walkthrough — generating a report (e.g., Timesheet, Profitability)

**Description**: When running a report that accepts project or member parameters, the form displays raw text input fields asking for UUIDs (e.g., "Project ID (UUID)"). Users don't know UUIDs — they need searchable dropdowns showing entity names. The backend parameter schema already includes an `entityType` field ("project", "member") that could drive entity pickers, but the frontend ignores it.

**Root Cause**: `frontend/components/reports/report-parameter-form.tsx` lines 116-126 — all `uuid`-type parameters render as `<Input type="text">` with placeholder `{entityType ?? "Entity"} ID (UUID)`. The `entityType` field from the parameter schema is used only in the placeholder text, not to select a picker component.

The backend `StandardReportPackSeeder.java` seeds parameters with both `type: "uuid"` and `entityType: "project"` / `"member"`, and the frontend `ParameterDefinition` TypeScript interface includes `entityType?: string` — but the form component never branches on it.

**Affected Files**:
- `frontend/components/reports/report-parameter-form.tsx` — replace `<Input>` with entity picker for uuid params with entityType
- `frontend/lib/api/reports.ts` — `ParameterDefinition` interface already has `entityType`, no changes needed
- `frontend/__tests__/report-parameter-form.test.tsx` — update tests to expect combobox instead of text input for entity-typed uuid params

**Fix Guidance**:
1. In `report-parameter-form.tsx`, for `param.type === "uuid"` parameters that have `param.entityType`, render a searchable `<Combobox>` (Shadcn Command-based) instead of `<Input>`.
2. The combobox should fetch entities based on `entityType`:
   - `"project"` → `GET /api/projects` — display project name
   - `"member"` → `GET /api/members` — display member name + email
   - `"customer"` → `GET /api/customers` — display customer name
3. Selected entity's UUID becomes the parameter value (same as what the text input would have accepted).
4. For uuid params WITHOUT `entityType`, keep the text input as fallback.
5. Use the existing `useEffect` + `fetch` pattern or a client-side API helper. The entity lists are already fetched on other pages, so the API endpoints exist.
6. Update tests to verify that uuid+entityType params render a combobox with entity names, and that selecting an entity sets the correct UUID value.

**Impact**: The reports feature is effectively unusable for non-technical users. All standard reports (Timesheet, Profitability by Project/Customer) require project/member selection, and users cannot provide UUIDs. This is the same class of issue as BUG-003 (template preview UUID input) — entity pickers were not implemented for UUID parameter types.

---

## BUG-006: Org admin displayed as "Member" on project member list

**Severity**: low
**Area**: Both — Project members display
**Found in**: Walkthrough — viewing project members as an admin

**Description**: When an org-level admin views a project's member list, they appear with a "Member" badge instead of an "Admin" designation. This is confusing because admins have full project access but their elevated role isn't visible in the project context.

**Root Cause**: The project member list query (`ProjectMemberRepository.findProjectMembersWithDetails()`) only returns entries from the `project_members` table. Org admins get implicit project access via `ProjectAccessService` (which checks org role before project role), but they may not have a row in `project_members`. If they were explicitly added, they get the `"member"` project role. The frontend `ROLE_BADGE` map in `project-members-panel.tsx` only knows `"lead"` and `"member"` — there's no admin badge. When `projectRole` is null or unrecognized, `DEFAULT_BADGE` renders "Member".

**Affected Files**:
- `frontend/components/projects/project-members-panel.tsx` — `ROLE_BADGE` map needs "admin" entry; display logic needs to consider org role
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberController.java` — member list endpoint could include org role info
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberRepository.java` — query only returns explicit project members

**Fix Guidance**:
1. Decide on the desired UX: (a) admins always appear in project member lists with an "Admin" badge, (b) admins only appear if explicitly added but show "Admin" badge, or (c) keep current behavior and mark this as "won't fix".
2. If option (a): modify the member list endpoint to also return org admins/owners who are not explicit project members, with a synthetic `projectRole = "admin"`. Add an `"admin"` entry to the frontend `ROLE_BADGE` map with an appropriate variant.
3. If option (b): when an admin is added to a project, store their org role as metadata. In the frontend, check if the member's org role is "admin"/"owner" and override the badge display.
4. Add `"admin"` and `"owner"` to the `ROLE_BADGE` map in `project-members-panel.tsx` with distinct styling (e.g., different color badge).
5. Consider whether org owners should also get special treatment.

**Impact**: Cosmetic/UX issue only. Actual permissions are correct — admins can still manage the project. But it's confusing for the admin themselves and for other team members who see an admin listed as "Member".

---

## BUG-007: Project template editor cannot add sub-tasks (task items)

**Severity**: high
**Area**: Frontend — Project templates
**Found in**: Walkthrough — Settings > Project Templates > Edit > Tasks

**Description**: When editing a project template's tasks, there is no way to add sub-tasks (task items / checklist items) to individual tasks. The backend fully supports template task items (`TemplateTaskItem` entity, create/update/duplicate operations), but the frontend editor doesn't expose this functionality. Existing templates with items (e.g., from `saveFromProject`) will silently lose their items when edited and saved.

**Root Cause**: The frontend `TemplateEditor.tsx` component and its TypeScript types are missing task item support:

1. `frontend/lib/api/templates.ts` — `TemplateTaskResponse` and `TemplateTaskRequest` interfaces don't include an `items` field, even though the backend returns and accepts them.
2. `frontend/components/templates/TemplateEditor.tsx` — `TaskRow` interface (line 23-30) has no `items` property. The `addTask()` function creates tasks without items. The save payload (lines 118-132) doesn't include items. No UI exists for adding/editing/removing sub-tasks.
3. The backend `TemplateTaskResponse` includes `List<TemplateTaskItemResponse> items` and `TemplateTaskRequest` accepts `List<TemplateTaskItemRequest> items`, but these are silently dropped by the frontend.

**Affected Files**:
- `frontend/lib/api/templates.ts` — add `items` field to `TemplateTaskRequest` and `TemplateTaskResponse`
- `frontend/components/templates/TemplateEditor.tsx` — add `items` to `TaskRow` interface, add sub-task management UI, include items in save payload
- `frontend/__tests__/template-editor.test.tsx` — add tests for sub-task add/edit/delete/reorder

**Fix Guidance**:
1. In `frontend/lib/api/templates.ts`, add to `TemplateTaskResponse`:
   ```typescript
   items: Array<{ id: string; name: string; sortOrder: number }>;
   ```
   And to `TemplateTaskRequest`:
   ```typescript
   items?: Array<{ name: string; sortOrder: number }>;
   ```
2. In `TemplateEditor.tsx`, extend the `TaskRow` interface to include `items: Array<{ id?: string; name: string; sortOrder: number }>`.
3. When loading a template, map `task.items` into the `TaskRow` state.
4. Add an expandable sub-section within each task card showing its items as a simple list.
5. Add an "Add Item" button per task that appends a new item with an inline text input.
6. Items should support: add, rename (inline edit), delete, reorder (move up/down).
7. When saving, include `items` in each task's payload sent to `PUT /api/project-templates/{id}`.
8. Add tests verifying: items display on load, add item, delete item, items included in save payload.

**Impact**: Project templates cannot define task-level checklists or sub-tasks. This means templates created from existing projects (via "Save as Template") will lose their task items when re-opened for editing. The template-to-project flow may also create projects without expected sub-tasks.

---

## BUG-008: Retainer detail page crashes for FIXED_FEE retainers (null allocatedHours)

**Severity**: critical
**Area**: Frontend — Retainer detail page
**Found in**: Walkthrough — clicking into a FIXED_FEE retainer

**Description**: Navigating to a FIXED_FEE retainer's detail page crashes with a React render error. The `ClosePeriodDialog` component calls `.toFixed(1)` on `period.allocatedHours`, which is `null` for FIXED_FEE retainers (they use a flat fee, not allocated hours). The error is: `TypeError: Cannot read properties of null (reading 'toFixed')`.

**Root Cause**: `frontend/components/retainers/close-period-dialog.tsx` line 104 — `{period.allocatedHours.toFixed(1)}h` crashes when `allocatedHours` is `null`. The `PeriodSummary` TypeScript interface declares `allocatedHours: number` (non-nullable), but the backend returns `null` for FIXED_FEE retainer periods. The type definition is wrong.

Multiple null-unsafe accesses in the same component:
- Line 104: `period.allocatedHours.toFixed(1)` — **crashes**
- Line 41: `period.overageHours > 0` — could be null for FIXED_FEE
- Line 44: `period.remainingHours` — could be null for FIXED_FEE
- Line 112: `period.consumedHours.toFixed(1)` — could be null

The `RetainerDetailActions` component (line 210) checks `currentPeriod && (...)` before rendering `ClosePeriodDialog`, but it doesn't check the retainer type. The dialog renders unconditionally for any retainer with an open period, including FIXED_FEE.

**Affected Files**:
- `frontend/lib/api/retainers.ts` — `PeriodSummary.allocatedHours` should be `number | null`, same for `remainingHours`, `overageHours`, `baseAllocatedHours`
- `frontend/components/retainers/close-period-dialog.tsx` — null-guard all hour fields; conditionally hide hour-based rows for FIXED_FEE retainers
- `frontend/components/retainers/retainer-detail-actions.tsx` — consider passing retainer type to dialog for conditional rendering

**Fix Guidance**:
1. In `frontend/lib/api/retainers.ts`, change `PeriodSummary` fields to nullable:
   ```typescript
   allocatedHours: number | null;
   baseAllocatedHours: number | null;
   remainingHours: number | null;
   overageHours: number | null;
   ```
2. In `close-period-dialog.tsx`, null-guard all hour accesses:
   - Line 104: `{period.allocatedHours?.toFixed(1) ?? "N/A"}h` or conditionally hide the "Allocated Hours" row when `allocatedHours` is null.
   - Line 112: `{period.consumedHours?.toFixed(1) ?? "0.0"}h`
   - Line 41: `const hasOverage = (period.overageHours ?? 0) > 0;`
   - Line 44: `const remainingHours = period.remainingHours ?? 0;`
3. For FIXED_FEE retainers, the "Allocated Hours" and "Remaining Hours" rows are meaningless — conditionally hide them. Check `retainer.type === "FIXED_FEE"` and only show "Consumed Hours" and "Period Fee".
4. Update rollover preview section (lines 127-131) to also null-guard — rollover is an HOUR_BANK concept, not applicable to FIXED_FEE.
5. Fix any other components that render `PeriodSummary` fields — search for `.allocatedHours`, `.remainingHours`, `.overageHours` across the frontend.
6. Add a test case: render `ClosePeriodDialog` with a FIXED_FEE period (null hours fields) and verify it doesn't crash.

**Impact**: The retainer detail page is completely broken for FIXED_FEE retainers. Users cannot view, manage, or close FIXED_FEE retainer periods. Since the error occurs during React render, it's an unrecoverable crash — the entire page fails to load.

---

## BUG-009: Portal magic link not testable — dev link not displayed, wrong URL format, e2e profile excluded

**Severity**: medium
**Area**: Both — Portal authentication (dev/test flow)
**Found in**: Manual testing — portal login at localhost:3002

**Description**: When testing the customer portal magic link flow, three issues compound to make it untestable:
1. The portal login page sends `{ email, orgId }` to the backend and receives `{ message, magicLink }`, but **ignores the `magicLink` field** — always showing "Check your email" with no way to retrieve the link (email isn't integrated yet).
2. The backend generates the dev magic link URL as `/portal/login?token=...&orgId=...` — but the portal login page doesn't read a `token` query param. The correct exchange page is at `/auth/exchange?token=...&orgId=...`.
3. The `isDevProfile()` gate in `PortalAuthController` only checks for `local`, `test`, `dev` profiles — but the e2e mock-auth stack uses the `e2e` profile, so `magicLink` is always `null` in the e2e environment.

Additionally, navigating directly to `/login` without `?orgId=...` shows the form but silently fails on submit (backend rejects `@NotBlank orgId`). This is by design (invite-only portal per ADR-079), but there's no user-facing error explaining why.

**Root Cause**:
- `portal/app/login/page.tsx` lines 66-82: reads API response but never checks for `magicLink` field
- `backend/.../portal/PortalAuthController.java` line 107: generates `/portal/login?token=...` instead of `/auth/exchange?token=...`
- `backend/.../portal/PortalAuthController.java` line 197: `isDevProfile()` doesn't include `"e2e"` profile

**Affected Files**:
- `portal/app/login/page.tsx` — display `magicLink` from response in dev mode
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalAuthController.java` — fix URL format, add `e2e` to profile check

**Fix Guidance**:
1. In `portal/app/login/page.tsx`, after `response.json()`, check for `data.magicLink`. If present, store it in state and display it on the success screen in a dev-mode amber callout box with a clickable link.
2. In `PortalAuthController.java` line 107, change the magic link URL from `/portal/login?token=...&orgId=...` to `/auth/exchange?token=...&orgId=...` (matches the portal's actual exchange page route).
3. In `PortalAuthController.java` `isDevProfile()`, add `"e2e"` to the profile check: `"local".equals(profile) || "test".equals(profile) || "dev".equals(profile) || "e2e".equals(profile)`.
4. Optionally: when `orgId` is missing from the login page URL, show an informational message like "You need an invitation link from your service provider to access this portal" instead of showing the form.

**Impact**: The portal login flow is untestable without email integration. Developers and testers cannot exercise the magic link exchange flow in local or e2e environments. **Status: Fixed** — all three issues resolved in this session.

---

## BUG-010: Dashboard crashes on portal customer comments — null actorName in activity widget

**Severity**: critical
**Area**: Both — Dashboard activity feed + portal comment audit events
**Found in**: Manual testing — adding a comment from portal, then viewing main app dashboard

**Description**: After a customer posts a comment via the portal (localhost:3002), the main app dashboard (localhost:3000/3001) crashes with `TypeError: null is not an object (evaluating 'name.split')` in the `RecentActivityWidget`. The `getInitials()` function receives `null` for the actor's name.

**Root Cause**: The audit event infrastructure assumes all actors are internal members. Two separate resolution paths both fail for portal actors:

1. **Dashboard queries** (`AuditEventRepository.java` lines 105-151): Both `findCrossProjectActivity()` and `findCrossProjectActivityForMember()` use `LEFT JOIN members m ON ae.actor_id = m.id` to resolve `m.name AS actorName`. Portal comments set `actorId` to the **customer UUID** (not a member UUID), so the join returns `NULL`.

2. **Activity feed** (`ActivityMessageFormatter.java` line 165-171): `resolveActorName()` looks up `actorMap.get(actorId)` where `actorMap` is built from `memberRepository.findAllById()`. Portal customer UUIDs aren't in the members table, so the lookup returns `null`, and the fallback was `actorId.toString()` (a raw UUID, not a name).

3. **Frontend** (`recent-activity-widget.tsx` line 43): `getInitials(name: string)` calls `name.split(" ")` without null-guarding, crashing when `actorName` is `null`.

The `PortalCommentService.java` has `authorName` available when creating the audit event but doesn't store it — only `actorId` and `actorType("PORTAL_USER")` are persisted.

**Affected Files**:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalCommentService.java` — store `actor_name` in audit event details JSONB
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventRepository.java` — update both cross-project queries to COALESCE with `details->>'actor_name'`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java` — fallback to `details.actor_name` for non-member actors
- `frontend/components/dashboard/recent-activity-widget.tsx` — null-guard `getInitials()`

**Fix Guidance**:
1. In `PortalCommentService.java`, add `"actor_name", authorName` to the `Map.of(...)` passed to `.details()` in the audit event builder. This embeds the customer's display name in the JSONB details.
2. In `AuditEventRepository.java`, change both queries from `m.name AS actorName` to `COALESCE(m.name, ae.details->>'actor_name', 'Unknown') AS actorName`. This falls through: member name → details actor_name → "Unknown".
3. In `ActivityMessageFormatter.java`, update `resolveActorName()` to accept the event's `details` map. If the member lookup fails, check `details.get("actor_name")` before falling back to "Unknown".
4. In `recent-activity-widget.tsx`, add a null guard to `getInitials()`: `if (!name) return "?"`.
5. Update `ActivityMessageFormatterTest.java`: change the "unknown actor falls back to UUID" test to expect "Unknown", and add a new test for portal user actor name resolution from details.
6. Note: existing portal comment audit events (created before the fix) will show "Unknown" since they lack `actor_name` in details. New portal comments will show the customer's name.

**Impact**: The main app dashboard is completely broken (white screen crash) for any org where a portal customer has posted a comment. Since the crash is in React render, the entire dashboard page fails — not just the activity widget. **Status: Fixed** — all four files updated in this session.

---

## BUG-011: Comments don't flow between main app and customer portal in either direction

**Severity**: high
**Area**: Both — Comment sync between main app and portal read model
**Found in**: Manual testing — adding comments from portal and main app

**Description**: Two broken comment flows:

1. **Main app → Portal**: When a staff member creates a comment with `SHARED` visibility on a task or document, it does NOT appear on the customer portal. The customer sees no comments from the org.
2. **Portal → Main app**: When a portal customer posts a comment on a project, it does NOT appear in the main app's task/document comment section. Staff see no customer comments.

Comments are silently lost in both directions — no errors, no warnings. Both sides think they posted successfully, but the other side never sees it.

**Root Cause**: Two separate architectural gaps:

### Gap A: Main app → Portal (no event handler)

The `PortalEventHandler` (`customerbackend/handler/PortalEventHandler.java`) handles events for documents, tasks, time entries, invoices, customer-project links, and project updates — but has **NO handler for `CommentCreatedEvent`**. When `CommentService.createComment()` publishes a `CommentCreatedEvent` with `visibility = "SHARED"`, no listener picks it up to sync to the portal read model.

The portal reads comments from `portal.portal_comments` (a separate schema/table managed by `PortalReadModelRepository`). Without a handler to call `readModelRepo.upsertPortalComment()`, SHARED comments from the main app never reach the portal.

Relevant code:
- `CommentService.java` lines 112-126: publishes `CommentCreatedEvent` with visibility, entityType, entityId
- `PortalEventHandler.java`: handles 10+ event types but NO comment events
- `PortalReadModelRepository.java` lines 98-117: `upsertPortalComment()` method exists but is only called from `PortalCommentService` (portal-originated comments)

### Gap B: Portal → Main app (entityType mismatch)

Portal comments are created with `entityType = "PROJECT"` and `entityId = projectId` (`PortalCommentService.java` line 52):
```java
var comment = new Comment("PROJECT", projectId, projectId, authorId, content, "SHARED");
```

But the main app queries comments by specific entity — `entityType = "TASK"` + `entityId = taskId`, or `entityType = "DOCUMENT"` + `entityId = docId` (`CommentRepository.java` lines 15-27):
```sql
SELECT c FROM Comment c
WHERE c.entityType = :entityType AND c.entityId = :entityId AND c.projectId = :projectId
```

Portal comments with `entityType = "PROJECT"` never match task-level or document-level queries. The main app has no concept of "project-level comments" in its UI — comments are always attached to tasks or documents.

Additionally, `CommentService.createComment()` (line 71-73) explicitly rejects non-TASK/DOCUMENT entity types:
```java
if (!"TASK".equals(entityType) && !"DOCUMENT".equals(entityType)) {
    throw new InvalidStateException("entityType must be TASK or DOCUMENT");
}
```

**Affected Files**:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` — add `onCommentCreated()` handler
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/PortalCommentService.java` — reconsider entityType for portal comments
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentRepository.java` — may need a project-level comment query
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java` — may need to accept "PROJECT" entityType or add separate method
- `frontend/components/projects/` — may need a project-level comments section to show portal customer comments

**Fix Guidance**:

**Part A — Main app SHARED comments → Portal:**
1. In `PortalEventHandler`, add a new `@TransactionalEventListener` for `CommentCreatedEvent`:
   - Check if `event.getVisibility()` is `"SHARED"` — if not, return early.
   - Resolve the author's display name from `memberId` (use the `actorName` field already on the event).
   - Look up linked customers for the project via `readModelRepo.findCustomerIdsByProjectId()`.
   - If any customers are linked, call `readModelRepo.upsertPortalComment()` with the comment's ID, orgId, projectId, authorName, body, and createdAt.
2. Also handle comment visibility changes: if a comment's visibility changes FROM `SHARED`, delete it from `portal.portal_comments`. If it changes TO `SHARED`, insert it.
3. Handle comment deletion: if a SHARED comment is deleted, remove it from the portal read model.

**Part B — Portal comments → Main app (design decision needed):**

This requires a product decision — pick one approach:

**Option 1 (Recommended): Project-level comment thread on main app**
- Keep portal comments as `entityType = "PROJECT"` (they're project-level discussions, not task-specific).
- Add a `findByProjectIdAndEntityType` query to `CommentRepository`.
- Add a "Customer Comments" section to the project detail page in the main app frontend that queries `entityType = "PROJECT"`.
- Add a `source` field to the `Comment` entity (`INTERNAL` / `PORTAL`) to style portal comments differently (e.g., with a "Customer" badge).

**Option 2: Attach portal comments to specific tasks**
- Change the portal UI to let customers comment on specific tasks (not just the project).
- Modify `PortalCommentService` to accept `entityType = "TASK"` and `entityId = taskId`.
- Portal comments would then appear alongside staff comments on the task detail page.
- Requires the portal to expose task-level comment UI.

**Option 3: Dual-write with project-level display**
- Keep portal comments dual-written to both tenant `comments` table and portal read model (as they already are).
- Add a `source` column to the `comments` table to distinguish origin.
- Show project-level comments in a dedicated tab/section on the project detail page in the main app.

**Regardless of option chosen:**
- Add a `source` field to the `Comment` entity to distinguish internal vs portal comments.
- Consider adding `entity_type` and `entity_id` columns to `portal.portal_comments` if task-level portal comments are needed in the future.

**Impact**: The comment feature appears to work on both sides (no errors), but is a one-way dead end in both directions. Staff and customers cannot communicate through comments despite this being a core portal collaboration feature. The two halves of the comment system were built independently (Phase 6.5 for comments, Phase 7 for portal backend, Phase 22 for portal frontend) and never connected.

---

## BUG-NNN: [Short description]

**Severity**: [critical/high/medium/low]
**Area**: [Backend/Frontend/Both] — [specific domain]
**Found in**: [Walkthrough chapter or scenario card]

**Description**: [What you observed vs. what should happen]

**Root Cause**: [If known — file, line, reason. Otherwise "Unknown — needs investigation"]

**Affected Files**:
- [file path and what needs changing]

**Fix Guidance**:
1. [Step-by-step fix instructions for the agent]

**Impact**: [What's broken because of this bug]

-->
