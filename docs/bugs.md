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

<!-- TEMPLATE — copy this for new bugs:

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
