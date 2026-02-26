# Phase 27 — Document Clauses

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with a mature document generation pipeline (Phase 12) that supports Thymeleaf-based templates, PDF rendering via OpenHTMLToPDF, template packs, clone-and-edit, and per-entity context builders. The platform currently supports three template entity types (PROJECT, CUSTOMER, INVOICE) with context builders that assemble flat `Map<String, Object>` variables for Thymeleaf rendering.

**What exists and what this phase changes:**

Document generation today is a single-shot flow: user selects a template, the system renders it with entity context (project/customer/invoice data + custom fields + org branding), and produces a PDF. Templates are monolithic HTML — if an engagement letter needs different terms for different clients, the only option is to clone the entire template and edit it. There's no reusable content block system.

This phase adds a **clause library** — reusable, variable-aware text blocks that can be composed into documents at generation time. Templates declare which clauses they suggest, but users can override the selection per-document.

**Existing infrastructure this phase builds on:**

- **DocumentTemplate entity** (`template/DocumentTemplate.java`): Fields include `name`, `slug`, `description`, `category` (TemplateCategory enum), `primaryEntityType` (PROJECT/CUSTOMER/INVOICE), `content` (Thymeleaf HTML), `css`, `source` (SYSTEM/CLONED/CUSTOM), `sourceTemplateId`, `packId`, `active`, `sortOrder`, `requiredContextFields` (JSONB list), `appliedFieldGroups` (JSONB list). Has clone/reset functionality. Slugs are auto-generated and unique per tenant.
- **TemplateContextBuilder interface** (`template/TemplateContextBuilder.java`): Strategy pattern — `ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder` each produce `Map<String, Object>` for their entity type. Context includes entity fields, org branding (logo, brand_color, footer), custom field values, related entities (e.g., project's customer, invoice's lines).
- **PdfRenderingService** (`template/PdfRenderingService.java`): Takes HTML string → produces PDF bytes via OpenHTMLToPDF. Used by `GeneratedDocumentService` to render and upload to S3.
- **GeneratedDocument entity** (`template/GeneratedDocument.java`): Tracks generated PDFs — `templateId`, `primaryEntityType`, `primaryEntityId`, `documentId`, `fileName`, `s3Key`, `fileSize`, `generatedBy`, timestamps. Links generated output back to the source template and entity.
- **TemplatePackSeeder** (`template/TemplatePackSeeder.java`): Reads pack definitions from `classpath:template-packs/*/pack.json`. Idempotent, tracks applied packs in `OrgSettings.templatePackStatus`. New packs are auto-applied on tenant startup.
- **Template validation** (Phase 23): `requiredContextFields` on templates, validation warnings on drafts, blocks on final generation for missing required fields.
- **GenerateDocumentDialog** (`frontend/components/templates/GenerateDocumentDialog.tsx`): Current generation UX — opens dialog, loads HTML preview, shows validation warnings, offers Download PDF and Save to Documents buttons. Single-step flow with no clause selection.
- **GenerateDocumentDropdown** (`frontend/components/templates/GenerateDocumentDropdown.tsx`): Dropdown menu on project/customer/invoice detail pages listing available templates for the entity type. Clicking a template opens GenerateDocumentDialog.
- **OrgSettings** (`settings/OrgSettings.java`): Has `templatePackStatus` (JSONB tracking which packs have been applied), plus branding fields used in template rendering context.
- **Flyway migrations**: Current latest is V42 (Phase 25). Next available is V43.
- **Thymeleaf rendering**: Templates use standard Thymeleaf syntax (`${variable}`, `th:each`, `th:if`). A `LenientStandardDialect` and `LenientOGNLEvaluator` handle missing variables gracefully (render blank instead of erroring). A `TemplateSecurityValidator` blocks dangerous Thymeleaf constructs (SSTI mitigation).

## Objective

Add a clause library system that allows orgs to maintain reusable text blocks and compose them into documents at generation time. After this phase:

- Orgs have a centralized clause library with categorized, reusable text blocks
- A "Standard Clauses" system pack seeds ~12 professional-services clauses on tenant provisioning
- Clauses support Thymeleaf variables (same context as the parent template — customer name, project name, dates, custom fields, org branding)
- Template authors configure which clauses a template suggests (with ordering and required/optional flags)
- At document generation time, users see a clause picker: template-suggested clauses are pre-checked, and users can toggle, reorder, and browse the full clause library to add extras
- Selected clause content is rendered inline and baked into the generated PDF — no live references in output documents
- Clause content is **snapshotted** at generation time, so subsequent edits to a clause don't affect previously generated documents

## Constraints & Assumptions

- **Clauses are tenant-scoped** (schema-per-tenant, no multitenancy boilerplate per Phase 13). System-seeded clauses are cloned into each tenant's schema (like template packs).
- **Clauses render inside the template's Thymeleaf context.** A clause body is a Thymeleaf fragment — it has access to the same variables as the parent template. The rendering pipeline resolves clause content first, injects it into the template HTML at marked positions, then renders the combined template as one unit.
- **Single-level composition only.** Clauses cannot reference other clauses (no nesting). A clause body is self-contained HTML/Thymeleaf.
- **No versioning or diff tracking for v1.** Clause edits overwrite the current content. Historical clause content is preserved only as snapshots in generated documents. Formal version history is a future phase.
- **No approval workflows.** Any ADMIN/OWNER can create, edit, or delete clauses. No draft/published lifecycle on clauses themselves.
- **Clause content goes through the same security validation as templates** — `TemplateSecurityValidator` checks clause bodies for dangerous Thymeleaf constructs.
- **The clause picker adds a step to the generation dialog** but doesn't change the fundamental flow: select template → (NEW) configure clauses → preview → generate PDF.
- **Backward compatibility.** Existing templates without clause configurations continue to work exactly as before — the clause picker step is skipped if a template has no associated clauses.

## Detailed Requirements

### 1. Clause Entity & Repository

**Problem:** There is no concept of reusable content blocks. Templates are monolithic.

**Requirements:**
- Create a `Clause` entity in a new `clause/` package:
  - `UUID id`
  - `String title` — display name (e.g., "Standard Payment Terms", "Confidentiality"). Max 200 characters, required.
  - `String slug` — URL-safe identifier, auto-generated from title (same pattern as `DocumentTemplate.generateSlug()`). Unique per tenant. Max 200 characters.
  - `String description` — optional summary of what this clause covers. Max 500 characters.
  - `String body` — the clause content as HTML/Thymeleaf. Supports the same Thymeleaf syntax as templates (`${variable}`, `th:if`, `th:each`, etc.). Required, TEXT column.
  - `String category` — free-text category for grouping (e.g., "Payment", "Liability", "Engagement", "Confidentiality", "General"). Max 100 characters, required.
  - `ClauseSource source` — enum: `SYSTEM` (from pack seeder, not editable), `CLONED` (cloned from system clause, editable), `CUSTOM` (org-created). Same pattern as `TemplateSource`.
  - `UUID sourceClauseId` — if `CLONED`, references the original system clause. Nullable.
  - `String packId` — if from a pack, the pack identifier (e.g., "standard-clauses"). Max 100 characters. Nullable.
  - `boolean active` — soft-active flag (default true). Inactive clauses are hidden from the picker but preserved on existing template associations.
  - `int sortOrder` — display ordering within category (default 0).
  - `Instant createdAt`, `Instant updatedAt`
- `ClauseRepository` — standard JPA repository with:
  - `findByActiveTrueOrderByCategoryAscSortOrderAsc()` — for the clause library listing
  - `findBySlug(String slug)` — for slug lookups
  - `findByCategoryAndActiveTrueOrderBySortOrderAsc(String category)` — filtered by category
  - `findByPackIdAndSourceAndActiveTrue(String packId, ClauseSource source)` — for pack management

### 2. Clause Service & CRUD API

**Problem:** No way to create, edit, list, or manage clauses.

**Requirements:**
- `ClauseService` with:
  - `createClause(CreateClauseRequest)` — validates title non-blank, generates slug (unique per tenant), validates body through `TemplateSecurityValidator`, sets `source = CUSTOM`.
  - `updateClause(UUID id, UpdateClauseRequest)` — validates same rules. SYSTEM clauses cannot be edited (throw 400 with message "System clauses cannot be edited. Clone this clause to customize it."). CLONED and CUSTOM clauses are editable.
  - `deleteClause(UUID id)` — soft-delete (sets `active = false`). If the clause is referenced by any `TemplateClause` associations (section 3), return a 409 with the count of affected templates and a message: "This clause is used by {N} template(s). Remove it from those templates first, or deactivate it instead." Deactivation (setting active=false) is always allowed.
  - `deactivateClause(UUID id)` — sets `active = false`. Always succeeds. Deactivated clauses remain on existing `TemplateClause` associations but are hidden from the clause picker's "browse library" view.
  - `cloneClause(UUID id)` — creates a copy with `source = CLONED`, `sourceClauseId = original.id`, title = "Copy of {original.title}", generates new unique slug. Returns the new clause. Primarily used to customize system clauses.
  - `listClauses(boolean includeInactive)` — returns all clauses grouped by category, ordered by category then sortOrder.
  - `listCategories()` — returns distinct category values for the clause library filter.
  - `getById(UUID id)` — returns full clause detail.
  - `previewClause(UUID clauseId, UUID entityId, TemplateEntityType entityType)` — renders the clause body with a real entity's context (for the clause editor preview). Uses the appropriate `TemplateContextBuilder` to build context, then renders just the clause body as a Thymeleaf fragment. Returns HTML.
- REST controller: `ClauseController` at `/api/clauses`:
  - `GET /api/clauses` — list clauses (query params: `includeInactive` default false, `category` optional filter)
  - `GET /api/clauses/categories` — list distinct categories
  - `GET /api/clauses/{id}` — get clause detail
  - `POST /api/clauses` — create clause (ADMIN/OWNER only)
  - `PUT /api/clauses/{id}` — update clause (ADMIN/OWNER only)
  - `DELETE /api/clauses/{id}` — delete clause (ADMIN/OWNER only, fails if referenced)
  - `POST /api/clauses/{id}/deactivate` — deactivate clause (ADMIN/OWNER only)
  - `POST /api/clauses/{id}/clone` — clone clause (ADMIN/OWNER only)
  - `POST /api/clauses/{id}/preview` — preview clause with entity context (ADMIN/OWNER only, request body: `{ entityId, entityType }`)
- Publish audit events for clause create/update/delete/clone/deactivate.

### 3. Template-Clause Association (TemplateClause)

**Problem:** Templates have no way to declare which clauses they suggest for generation.

**Requirements:**
- Create a `TemplateClause` join entity in the `clause/` package:
  - `UUID id`
  - `UUID templateId` — references `DocumentTemplate`
  - `UUID clauseId` — references `Clause`
  - `int sortOrder` — position in the clause list for this template (0-based)
  - `boolean required` — if true, the clause cannot be unchecked during generation (it's always included)
  - `Instant createdAt`
  - Unique constraint on `(templateId, clauseId)` — a clause can only be associated with a template once.
- `TemplateClauseRepository` with:
  - `findByTemplateIdOrderBySortOrderAsc(UUID templateId)` — for loading a template's clause configuration
  - `findByClauseId(UUID clauseId)` — for checking if a clause is referenced (deletion guard)
  - `deleteByTemplateIdAndClauseId(UUID templateId, UUID clauseId)` — for removing an association
- `TemplateClauseService` with:
  - `getTemplateClauses(UUID templateId)` — returns the template's clause list with full clause details (title, category, body preview snippet, required flag, sort order).
  - `setTemplateClauses(UUID templateId, List<TemplateClauseRequest> clauses)` — replaces the template's clause list entirely. Each request item has `clauseId`, `sortOrder`, `required`. Validates all clause IDs exist and are active. This is a "save all" operation (delete old associations, insert new ones in a transaction).
  - `addClauseToTemplate(UUID templateId, UUID clauseId, boolean required)` — adds a single clause at the end of the list. Returns 409 if already associated.
  - `removeClauseFromTemplate(UUID templateId, UUID clauseId)` — removes the association.
- REST endpoints (nested under templates):
  - `GET /api/templates/{templateId}/clauses` — list template's clause configuration (MEMBER+)
  - `PUT /api/templates/{templateId}/clauses` — replace template's clause list (ADMIN/OWNER only)
  - `POST /api/templates/{templateId}/clauses` — add a clause to template (ADMIN/OWNER only)
  - `DELETE /api/templates/{templateId}/clauses/{clauseId}` — remove clause from template (ADMIN/OWNER only)

### 4. Generation-Time Clause Resolution

**Problem:** The document generation pipeline has no concept of clauses. Templates render as monolithic HTML.

**Requirements:**

#### 4a. Generation Request Extension
- Extend the document generation API (`POST /api/templates/{templateId}/generate`) to accept an optional `clauses` parameter: a list of `{ clauseId, sortOrder }` objects specifying which clauses to include and in what order.
- If `clauses` is not provided in the request: fall back to the template's default clause list (from `TemplateClause` associations), with all non-required clauses included by default.
- If `clauses` is provided: use exactly the specified clauses in the specified order. Validate that all required clauses (from `TemplateClause` where `required = true`) are present — return 422 if a required clause is missing.
- If the template has no `TemplateClause` associations and no `clauses` parameter: generate without clauses (backward compatible).

#### 4b. Clause Content Assembly
- Before Thymeleaf rendering, assemble the clause content into the template:
  - For each selected clause (in order): retrieve the clause body.
  - Run each clause body through `TemplateSecurityValidator` (defense in depth — validates at save time AND render time).
  - Concatenate clause bodies with a `<div class="clause-block" data-clause-slug="{slug}">` wrapper for CSS targeting.
  - Inject the assembled clause HTML into the template context as a `clauses` variable: `${clauses}` renders the full assembled clause block. Also provide `${clauseCount}` (integer) for conditional display.
  - Template authors place `<div th:utext="${clauses}"></div>` where they want clauses to appear. If a template doesn't include this placeholder, clauses are appended after the main template content.
- **Snapshot behavior**: The clause body text at render time is what goes into the generated document. The `GeneratedDocument` entity should store the clause selection as metadata:
  - Add a `JSONB` column `clauseSnapshots` to `GeneratedDocument`: an array of `{ clauseId, slug, title, sortOrder }` recording which clauses were included. This is metadata for traceability, not the full clause text (the text is baked into the rendered HTML/PDF).

#### 4c. Preview Extension
- The template preview endpoint (`POST /api/templates/{templateId}/preview`) should also accept optional `clauses` parameter (same format as generation).
- Preview renders with clauses included, so users see the full composed document before generating.

### 5. Standard Clauses Pack

**Problem:** New tenants start with no clauses. Providing a starter set reduces time-to-value.

**Requirements:**
- Create a clause pack at `classpath:clause-packs/standard-clauses/pack.json` (analogous to `template-packs/`).
- Pack definition format:
  ```json
  {
    "id": "standard-clauses",
    "name": "Standard Professional Services Clauses",
    "description": "Common clauses for engagement letters, proposals, and service agreements.",
    "clauses": [
      {
        "title": "Standard Payment Terms",
        "slug": "payment-terms",
        "category": "Payment",
        "description": "Net 30 payment terms with late payment provisions.",
        "body": "<p>Payment is due within 30 days of the invoice date...</p>",
        "sortOrder": 0
      }
    ]
  }
  ```
- **Clauses to include** (~12, organized by category):

  **Payment:**
  - Standard Payment Terms — Net 30 with late payment interest provision
  - Fee Schedule — describes how fees are calculated (hourly rates, fixed fees)

  **Engagement:**
  - Engagement Acceptance — terms of engagement acceptance and commencement
  - Scope of Work — defines scope boundaries and change request process
  - Client Responsibilities — client's obligations (timely info, access, decisions)

  **Liability:**
  - Limitation of Liability — caps liability, excludes consequential damages
  - Professional Indemnity — references professional indemnity insurance

  **Confidentiality:**
  - Confidentiality — mutual confidentiality obligations

  **Termination:**
  - Termination — termination notice, work-in-progress settlement
  - Force Majeure — force majeure events and obligations

  **General:**
  - Dispute Resolution — mediation-first, then arbitration
  - Document Retention — record retention period and client access rights

- Clause bodies should be professional but generic (not jurisdiction-specific). Use Thymeleaf variables where natural:
  - `${org.name}` — the service provider's organization name
  - `${customer.name}` — the client's name
  - `${org.settings.defaultCurrency}` — for currency references
  - Date variables from the template context
- **ClausePackSeeder** — follows the same pattern as `TemplatePackSeeder`:
  - Reads from `classpath:clause-packs/*/pack.json`
  - Idempotent: tracks applied packs in `OrgSettings` (add a `clausePackStatus` JSONB map, same pattern as `templatePackStatus`)
  - Creates clauses with `source = SYSTEM`, `packId` set
  - Runs on tenant provisioning and on existing tenant startup (for retroactive application)
- **Link standard clauses to existing template pack templates**: After seeding clauses, automatically create `TemplateClause` associations between the seeded engagement-letter-type templates (if they exist) and relevant clauses. The architect should decide which templates get which default clauses — this is a best-effort convenience, not a hard requirement.

### 6. Frontend — Clause Library Management

**Problem:** No UI for managing clauses.

**Requirements:**
- Add a "Clauses" page accessible from Settings (or as a top-level settings sub-page, similar to the Templates settings page).
- **Clause list view**:
  - Group clauses by category with collapsible category headers
  - Each clause shows: title, category badge, source badge (System/Custom), description snippet, active/inactive status
  - System clauses have a "Clone" action (not edit/delete). Cloned clauses show "(Custom)" source badge.
  - Custom/Cloned clauses have Edit and Deactivate actions
  - Filter by category (dropdown) and search by title (text input)
  - "New Clause" button (opens create dialog)
- **Clause create/edit dialog**:
  - Fields: Title (text), Category (combobox — allows typing new category or selecting existing), Description (textarea), Body (large textarea or HTML editor)
  - The body field should be a code-style textarea (monospace font, generous height) since clause content is HTML/Thymeleaf. A rich-text editor is out of scope for v1 — use a plain textarea with syntax hints.
  - "Preview" button: renders the clause body with a sample entity context (the API endpoint from 2. `previewClause`). Opens a preview panel/modal showing the rendered HTML.
  - Save button calls create/update API
- **Clause detail view** (optional — could be inline expansion or a separate page):
  - Shows full clause body (rendered or raw HTML toggle)
  - Shows which templates use this clause (list of template names linked to template editor)

### 7. Frontend — Template Clause Configuration

**Problem:** Template authors have no way to configure which clauses a template suggests.

**Requirements:**
- Add a "Clauses" tab to the template editor page (alongside the existing content/CSS/settings tabs).
- **Template Clauses tab**:
  - Shows the template's current clause list as an ordered list with:
    - Clause title and category badge
    - Required toggle (checkbox/switch per clause)
    - Reorder controls (drag handle or up/down buttons)
    - Remove button (X icon)
  - "Add Clause" button opens a clause picker dialog:
    - Shows all active clauses from the org's library, grouped by category
    - Clauses already associated with this template are shown as disabled/checked
    - User can select one or more clauses and confirm
    - Selected clauses are added at the end of the list
  - "Save" button persists the full clause list via `PUT /api/templates/{templateId}/clauses`
  - Changes are not auto-saved — user must explicitly save (consistent with the template editor pattern)

### 8. Frontend — Generation Dialog Clause Picker

**Problem:** The generation dialog is a single-step preview-and-generate flow. Clauses need a selection step.

**Requirements:**
- Modify `GenerateDocumentDialog` to add a **clause selection step** before the preview, but **only when the selected template has associated clauses**:
  - **Step 1 (NEW — Clause Selection)**: Shown only if the template has `TemplateClause` associations.
    - Load the template's clause list via `GET /api/templates/{templateId}/clauses`
    - Display clauses as a checklist:
      - Required clauses: checked and disabled (cannot uncheck)
      - Optional clauses: checked by default (can uncheck)
      - Each row shows: checkbox, clause title, category badge, short description
    - "Browse Library" button at the bottom: opens a secondary picker showing all active org clauses not already in the list. User can add extras.
    - Reorder controls (up/down or drag) for changing clause order in this generation
    - "Next: Preview" button proceeds to Step 2
  - **Step 2 (Preview — existing, enhanced)**: Same as current behavior, but the preview request now includes the selected clauses. The rendered HTML shows the document with clauses composed in.
  - **Step 3 (Generate — existing)**: Download PDF / Save to Documents. The generation request includes the selected clauses.
- **No-clause fast path**: If the template has no `TemplateClause` associations, skip Step 1 entirely. The dialog behaves exactly as it does today (backward compatible).
- **Dialog state management**: The clause selection persists within the dialog session. If the user goes back from Preview to Clause Selection, their selections are preserved.

### 9. Audit Events

**Requirements:**
- Record audit events for:
  - `clause.created` — clause created (details: title, category, source)
  - `clause.updated` — clause modified (details: changed fields)
  - `clause.deleted` — clause soft-deleted
  - `clause.cloned` — clause cloned (details: source clause title, new clause title)
  - `clause.deactivated` — clause deactivated
  - `template_clause.configured` — template's clause list updated (details: template name, clause count)
  - `document.generated_with_clauses` — document generated with clause selection (details: template name, clause slugs). This augments the existing generation audit event rather than replacing it.

### 10. Notification Events

**Requirements:**
- No new notification types for v1. Clause management is an admin function — the existing audit trail provides sufficient traceability.
- **Future consideration**: If clause approval workflows are added later, notifications would be needed for "clause pending approval" and "clause approved/rejected."

## Out of Scope

- Rich-text / WYSIWYG clause editor (v1 uses plain HTML textarea)
- Clause version history and diff tracking
- Clause approval workflows (draft → approved → published lifecycle)
- Clause nesting (clauses referencing other clauses)
- Clause-level access control (all clauses are visible to all org members)
- Clause analytics (which clauses are most used)
- Drag-and-drop clause reordering in the generated PDF (reordering is in the picker, not the output)
- Per-customer clause overrides (e.g., "always include indemnity clause for Customer X")
- Clause groups / bundles (e.g., "Legal Bundle" = confidentiality + liability + termination)
- Conditional clause inclusion based on template variables (e.g., "include indemnity clause only if project value > X")
- Portal-side clause visibility (portal users see the final rendered document, not individual clauses)

## ADR Topics

The architect should produce ADRs for:

1. **Clause rendering strategy** — how clause content is injected into templates. Options: (a) string concatenation before Thymeleaf rendering (clause bodies assembled into a single `clauses` variable, template uses `th:utext="${clauses}"`), (b) Thymeleaf fragment inclusion (`th:replace` / `th:insert` referencing clause templates by name), (c) two-pass rendering (render clauses individually first, then inject rendered HTML into template). Consider: variable scoping (do clauses share the template's full context?), security validation surface, performance, and error handling if a clause has a Thymeleaf syntax error.

2. **Clause snapshot depth** — what gets stored when a document is generated with clauses. Options: (a) clause IDs + metadata only (the rendered HTML/PDF is the snapshot of content), (b) full clause body text stored in GeneratedDocument alongside the rendered output (enables "what did this clause say when we generated?" without re-rendering), (c) content hash for change detection. Consider: storage cost, auditability requirements, and whether "did this clause change since we generated?" is a v1 need.

3. **Template-clause placeholder strategy** — how templates declare where clauses should appear. Options: (a) single `${clauses}` variable that renders all selected clauses as a block, (b) named insertion points (`${clause('payment-terms')}`) allowing templates to place specific clauses at specific positions, (c) both (a global `${clauses}` block plus optional named overrides). Consider: template author complexity, backward compatibility, and whether named insertion points conflict with the per-document picker model (where the user might not include the named clause).

## Style & Boundaries

- The `Clause` entity and `TemplateClause` join entity go in a new `clause/` package — conceptually related to templates but a distinct domain.
- `ClauseController` follows the thin-controller pattern: pure delegation to `ClauseService`, no business logic.
- Clause body validation reuses `TemplateSecurityValidator` (SSTI mitigation) — no new security validation needed.
- Clause slug generation reuses the same pattern as `DocumentTemplate.generateSlug()` — extract to a shared utility if not already.
- The `ClausePackSeeder` follows the exact same pattern as `TemplatePackSeeder` — classpath resource reading, JSON deserialization, idempotent tracking via `OrgSettings`.
- Frontend clause management pages follow the same component patterns as the template management pages (settings sub-page, data table, create/edit dialogs).
- The generation dialog clause picker should be a clear, optional step — not a modal-within-a-modal. Consider a multi-step dialog (stepper) or an expandable section.
- Flyway migration: V43 (next available after Phase 25's V42). Creates `clauses` table and `template_clauses` join table, adds `clause_snapshots` JSONB column to `generated_documents`, adds `clause_pack_status` JSONB column to `org_settings`.
- All monetary/text calculations use the same patterns as existing code — no new frameworks or libraries.
