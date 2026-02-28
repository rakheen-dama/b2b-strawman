# Document System Redesign — Rich Editor & Unified UX

**Date**: 2026-02-28
**Phase**: 31
**Status**: Design approved

## Problem Statement

The document template and clause system (built in Phases 12 and 27) has a fundamentally broken authoring experience:

1. **Templates are raw HTML/Thymeleaf** — users edit `<p th:text="${customer.name}">` to customize documents. Non-technical users cannot participate.
2. **Clauses are invisible** — system clauses have content that users literally cannot read without cloning them first. The clause library shows titles and descriptions only.
3. **Templates and clauses are disconnected** — the template editor has a separate "Clauses" tab where users link clauses by checkbox without seeing content. Clause position in the document is determined by a `${clauses}` placeholder that auto-appends to the bottom.
4. **Preview requires backend round-trips** — Thymeleaf can only render server-side, so every preview is an API call.
5. **No expression safety by design** — Thymeleaf is a full expression language (OGNL), requiring a `TemplateSecurityValidator` to block SSTI attacks. This is defense-in-depth against a problem that shouldn't exist.

The result: a key product surface (engagement letters, proposals, NDAs, invoices) feels like a developer tool, not a premium SaaS experience.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Editor library | **Tiptap** (ProseMirror-based) | MIT, largest ecosystem, custom node support, Shadcn-compatible, used by Notion/GitLab/Linear |
| Storage format | **Tiptap JSON** (JSONB in Postgres) | Lossless editor round-trip, structured (queryable), no format translation bugs |
| Clause format | **Also Tiptap JSON** | One editor, one renderer, one format. Consistent authoring for templates and clauses |
| Template engine | **Custom TiptapRenderer** (replaces Thymeleaf) | JSON tree walk with key-based variable resolution. No expression language = no injection surface |
| Clause management | **Library stays, visibility fixed** | Clause Library page remains as management surface. Content becomes readable everywhere (inline expand, picker preview, generation dialog) |
| PDF pipeline | **JSON → HTML → PDF** (OpenHTMLToPDF unchanged) | Only the HTML generation step changes. PDF engine stays |
| Migration | **Clean cut, no dual-support** | Platform packs hand-converted to JSON. Org-custom content best-effort converted with fallback |

## Architecture

### New Rendering Pipeline

```
Template (Tiptap JSON in DB)
  → TiptapRenderer (Java) walks JSON node tree
    → "variable" nodes  → resolve key from context map, HTML-escape
    → "clauseBlock" nodes → load clause JSON, render recursively, wrap in div
    → "loopTable" nodes  → iterate context collection, emit <table> rows
    → all other nodes    → standard HTML elements (h1-h3, p, table, ul, etc.)
  → Assemble full HTML document (DOCTYPE, head, styles, body)
  → OpenHTMLToPDF → PDF bytes
```

### What Dies

- `PdfRenderingService.renderThymeleaf()` — replaced by `TiptapRenderer.render()`
- `TemplateSecurityValidator` — no expression language, no injection surface
- `ClauseAssembler` — clause rendering built into tree walk
- `PdfRenderingService.injectClauseContext()` — clauses are document tree nodes
- `LenientStandardDialect` / `LenientOGNLEvaluator` — Thymeleaf internals
- Thymeleaf dependency (for document rendering; email templates in Phase 24 retain their own Thymeleaf engine)

### What Survives Unchanged

- `ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder` — same interface, same output maps
- `TemplateContextHelper` — org context, branding, generated-by
- `ClauseResolver` — resolves which clauses to include, validates required clauses
- `OpenHTMLToPDF` — HTML → PDF conversion
- `GeneratedDocumentService` — orchestration, S3 upload, audit trail, domain events
- `GeneratedDocument` entity — clauseSnapshots, contextSnapshot, warnings all preserved

### Custom Tiptap Node Types

| Node | Type | Purpose | Editor Behavior |
|---|---|---|---|
| `variable` | Inline | Renders as styled chip: `[customer.name]` | Non-editable atom. Click to pick from variable list. Resolved to value during rendering. |
| `clauseBlock` | Block | Renders as styled card with title + clause content | Content read-only in template editor. Shows clause body inline. Draggable for reorder. Menu: move, replace, remove, mark required. |
| `loopTable` | Block | Data-driven table (rate cards, invoice lines) | Configurable: pick data source + define columns. Renders as table with sample/placeholder rows in editor. |

### Variable Resolution

Variables are simple dot-path map lookups, not expression evaluation:

```java
// "customer.name" → context.get("customer") → ((Map) result).get("name")
// Returns "" if any segment is null (no exceptions, no error output)
// HTML-escaped automatically
```

New endpoint to power the variable picker:
```
GET /api/templates/variables?entityType=PROJECT
→ { "project": ["name", "description", "startDate", ...],
    "customer": ["name", "email", ...],
    "org": ["name", "brandColor", ...], ... }
```

### Loop Tables

Replaces Thymeleaf `th:each` for repeating table rows. Scoped to tables only (v1).

```json
{
  "type": "loopTable",
  "attrs": {
    "dataSource": "invoice.lines",
    "columns": [
      { "header": "Description", "key": "description" },
      { "header": "Qty", "key": "quantity" },
      { "header": "Rate", "key": "unitPrice" },
      { "header": "Amount", "key": "amount" }
    ]
  }
}
```

The renderer iterates the collection at `contextMap["invoice"]["lines"]` and emits one `<tr>` per item with `<td>` per column.

### Schema Changes

```sql
-- document_templates: content TEXT (Thymeleaf HTML) → content JSONB (Tiptap JSON)
-- document_templates: css TEXT stays as-is
-- clauses: body TEXT (Thymeleaf HTML) → body JSONB (Tiptap JSON)
```

Single Flyway migration:
1. Add `content_json JSONB` / `body_json JSONB` columns
2. Convert platform templates (3) and system clauses (12) via PL/pgSQL
3. Best-effort convert ORG_CUSTOM / CLONED / CUSTOM content (HTML → JSON parser)
4. Store unconvertible originals in `legacy_content TEXT` column
5. Drop old columns, rename new ones

## Frontend Components

### Template Editor Page (`/settings/templates/[id]`)

Single unified editing surface. No tabs.

- **Top bar**: back link, template name, save button
- **Settings section** (collapsible): name, category, entity type, description
- **Toolbar**: formatting (B, I, U, H1-H3), table, variable picker, clause inserter
- **Editor body**: WYSIWYG content with:
  - Variables rendered as inline chips (styled pills)
  - Clauses rendered as block cards (title bar + read-only content + menu)
  - Loop tables rendered with placeholder/sample rows
  - Drag-and-drop reordering of clause blocks
- **Footer**: "Preview with data" button → pick entity → client-side rendered preview

### Clause Library Page (`/settings/clauses`)

- **"Show content" toggle** on every clause (including system clauses) — expands to show rendered body inline
- **Search + category/source filters** — unchanged
- **`[⋮]` menu**: System → Clone, Preview. Custom/Cloned → Edit, Preview, Delete
- **"Used in" indicator** — shows which templates reference this clause
- **Edit** opens Tiptap editor in a sheet/dialog (same editor component, clause scope)

### Clause Picker Dialog

Master-detail layout used from template editor toolbar and generation dialog:
- **Left panel**: categorized clause list with search
- **Right panel**: full rendered content of selected clause + metadata (category, source, usage count)
- **Action**: "Add Clause" inserts clauseBlock node at cursor position

### Generation Dialog

Same conceptual flow, improved visibility:
1. Pick template (unchanged)
2. Review clauses — full content visible, expand/collapse, reorder, add from library
3. Preview — client-side HTML preview (instant, no backend call)
4. Generate — backend PDF generation + S3 upload + audit (unchanged)

### Client-Side Preview

Frontend can render Tiptap JSON → HTML in the browser:
- Variable chips show `[customer.name]` in edit mode
- "Preview with data" fetches real entity data via existing REST APIs, resolves variables client-side
- Instant feedback loop — no backend round-trip for HTML preview
- PDF preview still requires backend call (OpenHTMLToPDF is server-side)

## Migration Strategy

### Platform Content (We Control)

- 3 template packs → hand-convert `.html` files to `.json` (Tiptap format)
- 12 system clause bodies → hand-convert HTML to Tiptap JSON in pack definition
- Pack seeders updated to read JSON format
- Zero risk — we control the exact content

### Org-Custom Content (User-Created)

- Best-effort HTML → Tiptap JSON parser in the Flyway migration
- For content that doesn't convert cleanly: store original in `legacy_content` column
- Surface "Migration needed" badge in UI — user opens editor, sees content as raw-HTML fallback node, can re-author
- Honest approach — we don't pretend arbitrary HTML round-trips perfectly

### Existing Integrations

- **Acceptance workflow (Phase 28)**: generates PDFs from templates → consumes new pipeline output, no changes needed
- **Portal document display**: serves generated PDFs → unchanged (PDF is PDF)
- **Report generation (Phase 19)**: separate rendering path → unaffected

## Epic Breakdown

| Epic | Name | Scope | Effort | Deps |
|------|------|-------|--------|------|
| 209 | Tiptap Editor Foundation + Custom Nodes | Frontend | L | — |
| 210 | Backend TiptapRenderer + Variable Endpoint | Backend | L | — |
| 211 | Database Migration + Pack Conversion | Backend | M | 210 |
| 212 | Template Editor Page Rewrite | Frontend | L | 209 |
| 213 | Clause Library + Clause Editor Rewrite | Frontend | M | 209 |
| 214 | Generation Dialog + Client-Side Preview | Frontend | M | 209, 210 |
| 215 | Integration Test Migration + Cleanup | Backend | M | 210, 211 |

**Critical path**: 209 and 210 run in parallel (frontend + backend foundations).

**Parallel tracks after foundations**:
- Track A (Frontend): 212 (template editor) → 214 (generation dialog)
- Track B (Frontend): 213 (clause library + editor)
- Track C (Backend): 211 (migration) → 215 (test cleanup)

**Estimated**: ~7 epics, ~16-20 slices.

## Out of Scope

- Custom fields UX overhaul (separate phase)
- Proposal entity / engagement pipeline (Phase 32, builds on this)
- E-signature workflow changes (stays as-is, consumes new PDF output)
- New template categories or packs (just migrate existing)
- Collaborative / multi-user editing
- Template versioning or diff
- Email template migration (Phase 24 email templates retain their own Thymeleaf engine)

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ORG_CUSTOM HTML doesn't convert cleanly | Medium | Low | `legacy_content` fallback + "Migration needed" UI badge |
| Tiptap JSON → HTML output differs from Thymeleaf HTML output (PDF styling breaks) | Medium | Medium | Keep same CSS; write visual regression tests comparing old vs new PDF output for platform templates |
| Loop table UX is complex to build | Low | Medium | Scope to tables-only for v1; predefined data sources (no arbitrary nesting) |
| Tiptap bundle size | Low | Low | Tree-shake extensions; lazy-load editor on template/clause pages only |
