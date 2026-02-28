# Phase 31 — Document System Redesign: Rich Editor & Unified UX

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. The document generation system was built across two phases:

- **Phase 12**: Document templates (Thymeleaf HTML stored in DB), PDF rendering via OpenHTMLToPDF, template packs with seeder, clone-and-edit customization, per-entity context builders (Project, Customer, Invoice), branding (logo, brand_color, footer), generated document tracking with S3 upload.
- **Phase 27**: Clause library (12 system clauses in "standard-clauses" pack), template-clause associations with ordering and required flags, clause resolution at generation time, clause assembly into template HTML via `${clauses}` placeholder, clause snapshots in generated documents.

**Current technical stack for documents:**
- **Storage**: `document_templates.content` is TEXT (raw Thymeleaf HTML), `clauses.body` is TEXT (Thymeleaf HTML fragments)
- **Rendering**: Thymeleaf `StringTemplateResolver` processes `${variable}` and `th:*` expressions against context maps built by `ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder`
- **Security**: `TemplateSecurityValidator` blocks SSTI patterns (OGNL injection, class access, preprocessing expressions)
- **Clause injection**: `ClauseAssembler` concatenates clause HTML into `<div class="clause-block">` wrappers, injected as `${clauses}` variable into template context. Templates place `<div th:utext="${clauses}"></div>` or clauses auto-append to bottom.
- **PDF**: OpenHTMLToPDF converts final HTML → PDF bytes
- **Frontend**: Template editor is a raw HTML textarea. Clause library shows titles and descriptions only — clause body content is not viewable without cloning. Generation dialog has clause checkboxes without content preview.

**The problem**: The document authoring experience is fundamentally broken:
1. Templates require editing raw HTML/Thymeleaf — non-technical users cannot participate
2. System clause content is invisible — users must clone to read what a clause says
3. Templates and clauses are disconnected — separate editor tabs, clauses dumped at a placeholder position
4. Preview requires a backend round-trip (Thymeleaf renders server-side only)
5. Thymeleaf is a full expression language requiring active SSTI defense — a problem that shouldn't exist

Documents are a revenue-generating product surface (engagement letters, proposals, NDAs, invoices). The current experience undermines the platform's premium positioning.

## Objective

Replace the Thymeleaf-based document authoring system with a rich text editor (Tiptap/ProseMirror) using structured JSON storage, eliminating the need for HTML editing and unifying the template + clause experience. After this phase:

1. **Templates are edited in a WYSIWYG rich text editor** — toolbar with formatting, tables, variable insertion, clause insertion. No HTML knowledge required.
2. **Clauses are visible everywhere** — inline content expansion in the clause library, full preview in the clause picker, content shown inside clause blocks in the template editor.
3. **Clauses are first-class document elements** — positioned precisely within the template as block nodes (not dumped at a placeholder), draggable for reorder, with inline read-only content preview.
4. **Variables are visual chips** — `[customer.name]` rendered as styled inline elements, inserted from a categorized picker, resolved to values during rendering.
5. **Storage is Tiptap JSON (JSONB)** — structured, queryable, lossless editor round-trip. Both templates and clauses use the same format.
6. **The rendering pipeline is a JSON tree walker** — no expression language, no injection surface, no SSTI defense needed. Variables are key-based map lookups.
7. **Client-side preview is instant** — the editor renders variables in the browser using fetched entity data. Backend calls only needed for PDF generation.
8. **Thymeleaf is removed** from document rendering (email templates in Phase 24 retain their separate Thymeleaf engine).

## Constraints & Assumptions

- **Tiptap (ProseMirror-based)** is the editor library. MIT licensed, headless (bring-your-own-UI, Shadcn-compatible), supports custom node types. Version: latest stable v2.x.
- **Tiptap JSON** is the storage format for both `document_templates.content` and `clauses.body`. Stored as JSONB in Postgres.
- **Clean migration, no dual-support.** The old Thymeleaf rendering path is deleted. There is no compatibility mode or feature flag to switch between renderers.
- **Context builders are unchanged.** `ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder` produce the same `Map<String, Object>` output. The new renderer consumes these maps identically.
- **OpenHTMLToPDF is unchanged.** The PDF engine still receives HTML. Only the HTML generation step changes (Thymeleaf → TiptapRenderer).
- **Email templates are unaffected.** The Thymeleaf engine used by Phase 24's email notification system is separate and stays.
- **Clause entity structure is unchanged** (fields, slug, source, category, pack system). Only the `body` column changes from TEXT (HTML) to JSONB (Tiptap JSON).
- **DocumentTemplate entity structure is unchanged** except `content` column (TEXT → JSONB) and `css` column stays TEXT.
- **GeneratedDocument, audit events, domain events, S3 upload** — all unchanged. The pipeline outputs the same artifacts (PDF bytes, HTML string for preview).
- **Single-user editing only.** No collaborative/real-time editing.
- **Flyway migration**: V48 (next available after Phase 29's V47).
- **TemplateClause join table is unchanged.** Clause-template associations, required flags, and ordering work the same way.

## Detailed Requirements

### 1. Tiptap Editor Foundation & Custom Nodes (Frontend)

**Problem:** No rich text editor exists in the frontend. The template editor is a raw HTML textarea.

**Requirements:**

#### 1a. Shared Editor Component
- Create a reusable `DocumentEditor` component in `frontend/src/components/editor/` based on Tiptap v2.
- The editor supports standard formatting: bold, italic, underline, headings (H1-H3), bullet lists, ordered lists, horizontal rules, links.
- The editor supports **tables**: insert table, add/remove rows/columns, cell merging. Tables render as HTML `<table>` elements.
- The editor toolbar follows Shadcn UI patterns — toolbar buttons, dropdown menus, separator groups.
- The editor is used for both template editing (full document scope) and clause editing (clause body scope) — same component, different toolbar configuration if needed.
- The editor serializes to/from Tiptap JSON (the canonical format). It must handle loading a JSON document and saving back to JSON without data loss.

#### 1b. Variable Node (Custom Inline Node)
- Create a custom Tiptap node type `variable`:
  - **Inline node** (renders within paragraph text)
  - **Atom** (non-editable — the node is a single unit, not character-editable)
  - **Attributes**: `key` (string, e.g., `"customer.name"`, `"org.brandColor"`)
  - **Editor rendering**: styled as a pill/chip (e.g., light blue background, rounded corners, monospace text showing the key name). Visually distinct from surrounding text.
  - **Click behavior**: clicking the chip opens a popover or selects it for deletion. Double-click or dedicated button opens the variable picker to change the key.
  - **Toolbar insertion**: a `{x} Variable` toolbar button opens a **variable picker dialog** — a categorized list of available variables (see section 2b for the backend endpoint). User clicks a variable → chip is inserted at cursor position.

#### 1c. Clause Block Node (Custom Block Node)
- Create a custom Tiptap node type `clauseBlock`:
  - **Block node** (renders as a full-width block element, not inline)
  - **Atom** (the clause content is read-only within the template editor — users edit clause text in the clause editor, not inline)
  - **Attributes**: `clauseId` (UUID), `slug` (string), `title` (string), `required` (boolean)
  - **Editor rendering**: styled as a card/panel:
    - Title bar with clause icon, clause title, required badge (if applicable), and a `[⋮]` menu button
    - Body area showing the clause content rendered as read-only rich text (the clause's Tiptap JSON rendered inline)
    - Visually distinct border/background (e.g., light gray background, left border accent)
  - **`[⋮]` menu actions**: Move up, Move down, Toggle required/optional, Replace with different clause (opens clause picker), Remove from template, Edit clause (opens clause editor in sheet/dialog)
  - **Drag-and-drop**: clause blocks are draggable for reordering within the document
  - **Toolbar insertion**: a `📄 Clause` toolbar button opens the **clause picker dialog** (section 5). Selected clause is inserted as a `clauseBlock` node at cursor position.
  - **Bottom action**: an `[+ Add Clause]` button rendered after the last clause block (or at bottom of document if no clauses) as a convenience insertion point.

#### 1d. Loop Table Node (Custom Block Node)
- Create a custom Tiptap node type `loopTable`:
  - **Block node**, **atom** (configured, not free-edited)
  - **Attributes**: `dataSource` (string, e.g., `"invoice.lines"`, `"members"`), `columns` (array of `{ header: string, key: string }`)
  - **Editor rendering**: shows a table with:
    - Header row using column headers
    - One or two placeholder rows showing column keys as faded text (e.g., `{description}`, `{quantity}`)
    - A "Data source: invoice.lines" label below the table
  - **Configuration**: click the table opens a config panel/popover:
    - Data source dropdown: predefined options based on entity type (e.g., for Invoice templates: `invoice.lines`, `invoice.taxBreakdown`; for Project templates: `members`, `rateCards`, `tags`)
    - Column list: add/remove/reorder columns, each with header text and data key
  - **Scope**: Loop tables are the ONLY iteration mechanism. No general-purpose loops. Data sources are predefined (not arbitrary expressions).

### 2. Backend TiptapRenderer & Variable Endpoint

**Problem:** The rendering pipeline uses Thymeleaf to process template HTML. This must be replaced with a JSON tree walker.

**Requirements:**

#### 2a. TiptapRenderer Service
- Create `TiptapRenderer` in the `template/` package. This replaces `PdfRenderingService.renderThymeleaf()`.
- **Input**: Tiptap JSON document (as Jackson `JsonNode` or parsed POJO), context map (`Map<String, Object>`), resolved clauses (list of `Clause` entities with Tiptap JSON bodies).
- **Output**: HTML string (complete document with DOCTYPE, head, styles, body).
- **Node rendering** — walk the JSON tree recursively, emitting HTML for each node type:

  | Tiptap Node | HTML Output |
  |---|---|
  | `doc` | Container (no tag, just renders children) |
  | `heading` (level 1-3) | `<h1>` / `<h2>` / `<h3>` |
  | `paragraph` | `<p>` |
  | `text` | Text content with marks applied (bold → `<strong>`, italic → `<em>`, underline → `<u>`, link → `<a href>`) |
  | `bulletList` | `<ul>` |
  | `orderedList` | `<ol>` |
  | `listItem` | `<li>` |
  | `table` | `<table>` |
  | `tableRow` | `<tr>` |
  | `tableCell` / `tableHeader` | `<td>` / `<th>` (with colspan/rowspan attrs) |
  | `horizontalRule` | `<hr>` |
  | `hardBreak` | `<br>` |
  | `variable` | Resolve `attrs.key` from context map. HTML-escape the value. Render as plain text (no wrapper in output HTML — the chip is editor-only). If key doesn't resolve → empty string (graceful degradation, no error). |
  | `clauseBlock` | Load clause by `attrs.clauseId`. Render the clause's Tiptap JSON body recursively (same renderer, same context map). Wrap output in `<div class="clause-block" data-clause-slug="{slug}">`. If clause not found → render `<!-- clause not found: {slug} -->` comment. |
  | `loopTable` | Read `attrs.dataSource` from context map (expects a `List<Map<String, Object>>`). Emit `<table>` with `<thead>` from `attrs.columns[].header` and one `<tr>` per list item with `<td>` per column resolved from item map using `attrs.columns[].key`. If data source is empty/null → render table header only with an empty body. |

- **Variable resolution**: simple dot-path lookup on the context map. `"customer.name"` → `context.get("customer")` cast to `Map`, then `.get("name")`. Any null segment returns empty string. All values HTML-escaped.
- **Document assembly**: Wrap rendered body in a complete HTML document:
  ```html
  <!DOCTYPE html>
  <html><head>
    <meta charset="UTF-8">
    <style>{defaultCss}\n{templateCss}</style>
  </head><body>
    {renderedContent}
  </body></html>
  ```
  Default CSS loaded from `classpath:templates/document-default.css` (same file as today).
- **No Thymeleaf dependency.** The renderer is pure Java string building from JSON input. No expression language, no template engine library.

#### 2b. Variable Metadata Endpoint
- New endpoint: `GET /api/templates/variables?entityType={PROJECT|CUSTOMER|INVOICE}`
- Returns a structured list of available variables grouped by entity, suitable for powering the frontend variable picker.
- Response format:
  ```json
  {
    "groups": [
      {
        "label": "Project",
        "prefix": "project",
        "variables": [
          { "key": "project.name", "label": "Project Name", "type": "text" },
          { "key": "project.description", "label": "Description", "type": "text" },
          { "key": "project.startDate", "label": "Start Date", "type": "date" },
          { "key": "project.dueDate", "label": "Due Date", "type": "date" }
        ]
      },
      {
        "label": "Customer",
        "prefix": "customer",
        "variables": [
          { "key": "customer.name", "label": "Customer Name", "type": "text" },
          { "key": "customer.email", "label": "Email", "type": "text" }
        ]
      },
      {
        "label": "Organization",
        "prefix": "org",
        "variables": [
          { "key": "org.name", "label": "Organization Name", "type": "text" },
          { "key": "org.brandColor", "label": "Brand Color", "type": "text" },
          { "key": "org.documentFooterText", "label": "Footer Text", "type": "text" },
          { "key": "org.logoUrl", "label": "Logo URL", "type": "url" }
        ]
      },
      {
        "label": "Generated",
        "prefix": "",
        "variables": [
          { "key": "generatedAt", "label": "Generation Date", "type": "datetime" },
          { "key": "generatedBy.name", "label": "Generated By", "type": "text" }
        ]
      }
    ],
    "loopSources": [
      { "key": "members", "label": "Project Members", "entityTypes": ["PROJECT"], "fields": ["name", "email", "role"] },
      { "key": "invoice.lines", "label": "Invoice Lines", "entityTypes": ["INVOICE"], "fields": ["description", "quantity", "unitPrice", "amount"] },
      { "key": "tags", "label": "Tags", "entityTypes": ["PROJECT", "CUSTOMER"], "fields": ["name", "color"] }
    ]
  }
  ```
- The variable list is derived from the existing context builders — it's a **metadata reflection** of what the builders produce. Maintain this list in a static configuration class (not introspected dynamically from builder code).
- Filter by `entityType` — only show variables relevant to the selected template entity type (e.g., don't show `invoice.*` variables for a PROJECT template).

#### 2c. Update PdfRenderingService
- Replace the `renderThymeleaf()` call chain with `TiptapRenderer.render()`.
- `generatePdf(templateId, entityId, memberId, resolvedClauses)`:
  1. Load template (now has JSONB `content`)
  2. Build context via appropriate `TemplateContextBuilder` (unchanged)
  3. Call `TiptapRenderer.render(template.content, contextMap, resolvedClauses)` → HTML string
  4. Call `htmlToPdf(html)` → PDF bytes (OpenHTMLToPDF, unchanged)
- `previewHtml()` — same but stops after HTML generation (no PDF conversion). Unchanged conceptually.
- **Delete**: `renderThymeleaf()`, `injectClauseContext()`, `renderFragment()`, all Thymeleaf engine setup code, `LenientStandardDialect`, `LenientOGNLEvaluator`.
- **Delete**: `TemplateSecurityValidator` (no expression language = no injection surface).
- **Delete**: `ClauseAssembler` (clause rendering is now part of the tree walk in TiptapRenderer).

#### 2d. Update Template and Clause CRUD Endpoints
- Template create/update endpoints: accept `content` as JSON object (Tiptap document) instead of HTML string. Store in JSONB column.
- Clause create/update endpoints: accept `body` as JSON object instead of HTML string. Store in JSONB column.
- Template GET endpoints: return `content` as JSON object.
- Clause GET endpoints: return `body` as JSON object.
- No format validation beyond valid JSON — the editor enforces document structure. Optionally validate that the root node is `type: "doc"`.

### 3. Database Migration & Pack Conversion

**Problem:** Existing templates store Thymeleaf HTML in TEXT columns. Must migrate to Tiptap JSON in JSONB columns.

**Requirements:**

#### 3a. Flyway Migration (V48)
- Add `content_json JSONB` column to `document_templates` (nullable initially).
- Add `body_json JSONB` column to `clauses` (nullable initially).
- Add `legacy_content TEXT` column to `document_templates` (nullable, for unconvertible content).
- Add `legacy_body TEXT` column to `clauses` (nullable, for unconvertible content).
- For **PLATFORM-source templates** and **SYSTEM-source clauses**: set `content_json` / `body_json` to NULL initially — the pack seeders will re-seed with JSON format (see 3b). Copy current HTML to `legacy_content` / `legacy_body` as backup.
- For **ORG_CUSTOM/CLONED/CUSTOM content**: run a PL/pgSQL conversion function that performs best-effort HTML → Tiptap JSON conversion. The converter should handle:
  - Basic HTML elements: `<p>`, `<h1>`-`<h3>`, `<strong>`, `<em>`, `<u>`, `<a>`, `<ul>`, `<ol>`, `<li>`, `<table>`, `<tr>`, `<td>`, `<th>`, `<hr>`, `<br>`
  - Thymeleaf expressions: `<span th:text="${key}">placeholder</span>` → variable node with `key` attribute
  - Thymeleaf `th:utext="${clauses}"` → skip (clause blocks are now positioned explicitly in the editor)
  - Content that doesn't parse cleanly: store original HTML in `legacy_content` / `legacy_body`, set JSON column to a single-node document containing a `legacyHtml` custom node (the editor can render this as a "migration needed" block).
- After conversion: drop old `content` TEXT column, rename `content_json` → `content`. Same for `body` / `body_json`.
- Keep `legacy_content` / `legacy_body` columns permanently (they're backup, not operational).

#### 3b. Template Pack Conversion
- Convert `classpath:template-packs/common/` templates from `.html` files to `.json` files (Tiptap JSON format).
- Each template JSON file contains the full Tiptap document structure with variable nodes replacing Thymeleaf expressions and loop table nodes replacing `th:each` constructs.
- Update `TemplatePackSeeder` to read `.json` content files. The pack.json `contentFile` field now references `.json` files (e.g., `"contentFile": "engagement-letter.json"`).
- The seeder stores the JSON content directly in the JSONB `content` column.

#### 3c. Clause Pack Conversion
- Convert `classpath:clause-packs/standard-clauses/pack.json` — clause `body` fields change from HTML strings to Tiptap JSON objects.
- Update `ClausePackSeeder` to store JSON bodies in the JSONB `body` column.
- All 12 system clauses converted: variable references change from `<span th:text="${org.name}">Firm</span>` to `variable` nodes with `key: "org.name"`.

### 4. Template Editor Page Rewrite (Frontend)

**Problem:** The template editor is a raw HTML textarea with a separate Clauses tab. Templates and clauses are disconnected.

**Requirements:**

#### 4a. Unified Editor Page
- Rewrite `/settings/templates/[id]` page:
  - **Top bar**: back link to template list, template name (editable inline), Save button
  - **Settings section** (collapsible panel): name, category (dropdown), entity type (dropdown), description (textarea). Same fields as today, just reorganized.
  - **Editor body**: the `DocumentEditor` component (from section 1a) filling the main content area. Loads the template's Tiptap JSON content. No tabs — the document IS the editor surface.
  - **Footer area**: "Preview with data" button (see 4c)
- Remove the separate "Content" and "Clauses" tabs. Clauses are now block nodes within the editor content.
- Remove the CSS textarea. Template CSS stays as a field but moves to the Settings section (collapsible, advanced).
- The template's `TemplateClause` associations are derived from the clause block nodes present in the Tiptap JSON content. When saving: extract all `clauseBlock` nodes from the document, sync `template_clauses` table to match (create/delete/reorder associations). The JSON document is the source of truth for which clauses are included and their order.

#### 4b. Clause Block Interactions (within template editor)
- Clicking `📄 Clause` in toolbar opens the clause picker dialog (section 5).
- Clause blocks render with full clause content visible (read-only).
- The `[⋮]` menu on each clause block:
  - **Move up / Move down**: reorders within the document
  - **Required / Optional toggle**: sets the `required` attribute on the clause block node
  - **Replace clause**: opens clause picker, swaps this block for a different clause
  - **Remove**: deletes the clause block node from the document
  - **Edit clause**: opens a sheet/dialog with the clause editor (same `DocumentEditor` component, clause-body scope). Saves update the clause entity (affects all templates using this clause). Show a warning: "Editing this clause will affect all templates that use it."
- `[+ Add Clause]` convenience button at the bottom of the editor (below all content) opens the clause picker.

#### 4c. Client-Side Preview
- "Preview with data" button in footer:
  1. Opens an entity picker (select a real project/customer/invoice from existing data)
  2. Fetches entity data from existing REST APIs (project detail, customer detail, invoice detail)
  3. Renders the Tiptap JSON with variables resolved client-side — variable chips replaced with actual values, clause blocks rendered with resolved content, loop tables populated with real data
  4. Displays in a preview panel/modal (styled to approximate PDF output — white background, document margins, print-friendly typography)
- This is **client-side only** — no backend call. Instant feedback.
- "Download PDF" button in preview triggers backend PDF generation (existing flow, updated to use new renderer).

### 5. Clause Picker Dialog (Frontend)

**Problem:** When adding clauses to a template (or selecting clauses at generation time), users cannot see clause content.

**Requirements:**
- Master-detail layout dialog:
  - **Left panel**: categorized clause list with search input. Categories as collapsible sections. Each clause shows title, source badge (System/Custom/Cloned), category. Clauses already in the template are marked (checkmark or disabled).
  - **Right panel**: when a clause is selected on the left, shows full rendered content (the clause's Tiptap JSON rendered as rich text), plus metadata (category, source, description, "Used in N templates" count).
- **Action button**: "Add Clause" — inserts the selected clause as a `clauseBlock` node.
- Used in two contexts:
  1. Template editor toolbar (inserts at cursor position)
  2. Generation dialog clause step (adds to generation clause list)

### 6. Clause Library Page Rewrite (Frontend)

**Problem:** Clause content is invisible. System clauses require cloning to read.

**Requirements:**

#### 6a. Clause List with Content Visibility
- Rewrite `/settings/clauses` page:
  - Group by category with collapsible headers (unchanged)
  - Each clause card shows: title, source badge, description
  - **New: "Show content" toggle** (chevron/expand) on every clause — expands to show the clause body rendered as rich text (Tiptap JSON → rendered display). Works for System clauses too (read-only).
  - **"Used in" indicator**: shows count of templates using this clause. Click to see template names.
  - Search by title and filter by category/source (unchanged)
  - "New Clause" button (opens clause editor)
- `[⋮]` menu per clause:
  - System: Clone, Preview (full-page preview with entity data)
  - Custom/Cloned: Edit, Clone, Preview, Deactivate
  - Edit opens clause editor (section 6b)

#### 6b. Clause Editor
- Opens in a sheet or dialog (not a new page).
- Contains:
  - Title, slug (read-only, auto-generated), category (combobox), description fields at top
  - `DocumentEditor` component for the clause body (same editor component, may have reduced toolbar — e.g., no clause insertion within a clause)
  - Preview panel: "Preview with data" button (same as template preview — pick entity, resolve variables client-side)
  - Save / Cancel buttons
- System clauses: editor opens in read-only mode with a "Clone to customize" action bar.

### 7. Generation Dialog Update (Frontend)

**Problem:** The generation dialog shows clause checkboxes without content preview.

**Requirements:**
- Update `GenerateDocumentDialog` clause selection step:
  - Each clause in the list shows title, category badge, **and an expand toggle to show full content** (same as clause library).
  - Required clauses: checked, cannot uncheck, [Required] badge
  - Optional clauses: checked by default, can uncheck
  - Reorder via drag or up/down buttons
  - "Add from library" button opens clause picker dialog (section 5)
- Preview step: rendered HTML preview with clauses composed in (client-side render for speed, with option to fetch backend PDF preview)
- Generate step: unchanged (sends clause selection to backend)

### 8. Integration Test Migration

**Problem:** Existing integration tests create templates with HTML content strings and clauses with HTML bodies.

**Requirements:**
- Update all template-related test helpers and test data builders to produce Tiptap JSON instead of HTML strings.
- Create a `TestDocumentBuilder` utility that provides convenient methods for constructing Tiptap JSON documents in tests:
  - `TestDocumentBuilder.doc().heading("Title").paragraph("Body text").variable("customer.name").clauseBlock(clauseId, "slug").build()` → produces valid Tiptap JSON
- Update `PdfRenderingService` integration tests to verify the new rendering pipeline produces correct HTML from JSON input.
- Add **visual regression tests** for the 3 platform templates: generate PDFs from the new JSON templates and verify they are structurally equivalent to the old Thymeleaf-rendered output (same headings, paragraphs, tables, clause sections).
- Update acceptance workflow tests (Phase 28) that generate documents — ensure they work with the new pipeline.
- Delete Thymeleaf-specific test infrastructure (template security validator tests, SSTI test cases, Thymeleaf fragment rendering tests).

### 9. Cleanup & Deletion

**Problem:** Dead code from the Thymeleaf era must be removed.

**Requirements:**
- Delete from `template/` package:
  - Thymeleaf engine configuration (StringTemplateResolver setup, dialect registration)
  - `LenientStandardDialect` class
  - `LenientOGNLEvaluator` class
  - `TemplateSecurityValidator` class and its tests
  - `PdfRenderingService.renderThymeleaf()`, `renderFragment()`, `injectClauseContext()` methods
- Delete from `clause/` package:
  - `ClauseAssembler` class (clause rendering is now in TiptapRenderer)
- Remove Thymeleaf dependency from `pom.xml` **only if** no other code depends on it. Check: email template rendering (Phase 24) may use its own Thymeleaf engine — if so, the dependency stays but document-rendering code no longer uses it.
- Delete template `.html` files from `classpath:template-packs/` (replaced by `.json` files).

## Out of Scope

- Custom fields UX overhaul (separate phase)
- Proposal entity / engagement pipeline (Phase 32, builds on this)
- E-signature / acceptance workflow changes (stays as-is, consumes new PDF output)
- New template categories, packs, or clause packs
- Collaborative / multi-user editing
- Template versioning, diff, or change tracking
- Email template migration (Phase 24 email templates keep their Thymeleaf engine)
- Clause nesting (clauses referencing other clauses)
- Clause approval workflows
- General-purpose loops beyond tables (loop tables scoped to predefined data sources only)
- Custom CSS editor (CSS field stays as plain text in settings; no syntax-highlighted editor)

## ADR Topics

The architect should produce ADRs for:

1. **Editor library selection** — Tiptap (ProseMirror) as the document editor. Alternatives considered: BlockNote (AGPL license concern), Plate/Slate (smaller ecosystem), Markdown + Handlebars (formatting limitations, round-trip lossyness). Key factors: MIT license, custom node extensibility, Shadcn compatibility, ecosystem maturity.

2. **Document storage format** — Tiptap JSON (JSONB) replacing Thymeleaf HTML (TEXT). Alternatives: Markdown with Handlebars, keep HTML with WYSIWYG editor on top. Key factors: lossless editor round-trip, structured queryability, elimination of expression language attack surface, clause blocks as typed nodes in document tree.

3. **Rendering pipeline architecture** — Custom TiptapRenderer (JSON tree walker) replacing Thymeleaf. Key factors: variable resolution as map lookups (not expression evaluation), clause rendering as tree recursion (not string injection), loop tables as predefined data source iteration (not arbitrary expressions), HTML output consumed by unchanged OpenHTMLToPDF pipeline.

4. **Migration strategy for existing content** — Clean cut vs. dual-support period. Platform content hand-converted via new pack files. Org-custom content best-effort converted with legacy fallback column. Alternatives: dual rendering (maintain both paths), forced re-authoring (no auto-conversion). Key factors: clean codebase (no dual paths), user agency (legacy fallback + editor re-authoring), finite platform content (3 templates, 12 clauses).

5. **Template-clause association source of truth** — Tiptap document JSON as source of truth (clause blocks in document define associations) vs. separate TemplateClause table as source of truth. Decision: document JSON is primary (what you see in the editor IS the clause configuration), TemplateClause table synced on save for query efficiency. Key factors: WYSIWYG consistency (editor content = truth), query needs (find templates using clause X), backward compatibility with ClauseResolver.

## Style & Boundaries

- New frontend editor components go in `frontend/src/components/editor/` — `DocumentEditor`, `VariablePicker`, `ClausePicker`, `LoopTableConfig`, and custom Tiptap extensions.
- `TiptapRenderer` goes in the existing `template/` package alongside `PdfRenderingService`.
- Variable metadata configuration goes in `template/` package (static class defining available variables per entity type).
- Tiptap npm packages: `@tiptap/react`, `@tiptap/starter-kit`, `@tiptap/extension-table`, `@tiptap/extension-link`, `@tiptap/extension-placeholder`, plus custom extensions for variable/clauseBlock/loopTable nodes.
- Frontend tests: component tests for the editor, variable picker, clause picker. Snapshot tests for custom node rendering.
- Backend tests: unit tests for TiptapRenderer (JSON → HTML for each node type), integration tests for the full pipeline (JSON → HTML → PDF), migration verification tests.
- The `DocumentEditor` component is designed for reuse — Phase 32 (Proposals) will use the same editor for proposal content authoring.
