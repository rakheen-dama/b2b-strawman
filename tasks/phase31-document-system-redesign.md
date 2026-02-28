# Phase 31 -- Document System Redesign: Rich Editor & Unified UX

Phase 31 replaces the Thymeleaf-based document authoring system with a Tiptap/ProseMirror rich text editor backed by structured JSON storage. Templates shift from raw HTML textareas to WYSIWYG editing with visual variable chips, inline clause blocks, and client-side preview. The rendering pipeline changes from Thymeleaf expression evaluation to a custom JSON tree walker (`TiptapRenderer`), eliminating the SSTI attack surface entirely. Storage migrates from TEXT columns to JSONB columns (V48 migration). This is a clean-cut rewrite with no dual-support period -- Thymeleaf document-rendering code is deleted.

**Architecture doc**: `architecture/phase31-document-system-redesign.md`

**ADRs**: [ADR-119](../adr/ADR-119-editor-library-selection.md) (Tiptap editor), [ADR-120](../adr/ADR-120-document-storage-format.md) (JSONB storage), [ADR-121](../adr/ADR-121-rendering-pipeline-architecture.md) (TiptapRenderer), [ADR-122](../adr/ADR-122-content-migration-strategy.md) (clean cut migration), [ADR-123](../adr/ADR-123-template-clause-association-source-of-truth.md) (document JSON as source of truth)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 209 | Database Migration & Pack Conversion | Backend | -- | L | 209A, 209B | **Done** (PRs #428, #429) |
| 210 | TiptapRenderer & Variable Endpoint | Backend | 209 | L | 210A, 210B | **Done** (PRs #430, #431) |
| 211 | Entity Updates & Template-Clause Sync | Backend | 209 | M | 211A, 211B | **Done** (PRs #432, #433) |
| 212 | Rendering Pipeline Switch & Legacy Import | Backend | 210, 211 | M | 212A, 212B | **Done** (PRs #434, #435) |
| 213 | Tiptap Editor Foundation | Frontend | -- | L | 213A, 213B, 213C | |
| 214 | Template Editor Rewrite | Frontend | 210B, 211, 213 | L | 214A, 214B | |
| 215 | Clause Library & Editor Rewrite | Frontend | 213 | M | 215A, 215B | |
| 216 | Generation Dialog & Preview | Frontend | 213, 214 | M | 216A, 216B | |
| 217 | Backend Test Migration & Cleanup | Backend | 212 | M | 217A, 217B | |

## Dependency Graph

```
[E209A V48 Migration + Pack JSON]──►[E209B Clause Pack + Seeder Updates]
     (Backend)                              (Backend)
         │                                       │
         └─────────────┬─────────────────────────┘
                        │
              ┌─────────┴──────────┐
              ▼                    ▼
[E210A TiptapRenderer]    [E211A Entity JSONB Updates]
     (Backend)                  (Backend)
         │                         │
         ▼                         ▼
[E210B Variable Endpoint] [E211B Template-Clause Sync]
     (Backend)                  (Backend)
         │                         │
         └──────────┬──────────────┘
                    │
                    ▼
        [E212A Pipeline Switch]
              (Backend)
                    │
                    ▼
        [E212B Legacy Import]──────────────────────────────────────┐
              (Backend)                                            │
                    │                                              │
                    ▼                                              │
        [E217A Integration Test Migration]──►[E217B Thymeleaf Code Deletion]
              (Backend)                            (Backend)
                                                       │
                                                       │
[E213A Tiptap npm + Core Editor]   ◄───── (Independent of backend) │
     (Frontend)                                                    │
         │                                                         │
         ▼                                                         │
[E213B Custom Nodes: Variable + LoopTable]                         │
     (Frontend)                                                    │
         │                                                         │
         ▼                                                         │
[E213C Custom Node: ClauseBlock]                                   │
     (Frontend)                                                    │
         │                                                         │
         ├─────────────────────────┬───────────────────┐           │
         ▼                        ▼                    ▼           │
[E214A Template Editor Page] [E215A Clause Library] [E216A Gen Dialog]
     (Frontend)                (Frontend)             (Frontend)   │
         │                        │                                │
         ▼                        ▼                                │
[E214B Template Save + Sync] [E215B Clause Editor]                 │
     (Frontend)                (Frontend)                          │
         │                                                         │
         ▼                                                         │
[E216B Client-Side Preview]                                        │
     (Frontend)                                                    │
```

**Parallel opportunities**:
- Epic 213 (frontend editor foundation) can run in PARALLEL with Epics 209-212 (all backend)
- Epic 210 and 211 can run in parallel (both depend on 209 only)
- Epic 215 (clause library) can run in parallel with Epic 214 (template editor), both after 213
- Epic 216A (generation dialog) can run in parallel with 214 and 215

## Implementation Order

### Stage 1: Database Foundation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 209 | 209A | V48 Flyway migration (column additions, PL/pgSQL converter, column swap). Template pack JSON conversion (3 files). Pack.json update. Must run first -- changes column types. | **Done** (PR #428) |
| 1b | Epic 209 | 209B | Clause pack JSON conversion (12 clauses). TemplatePackSeeder and ClausePackSeeder updates to read JSON. Must follow 209A (migration changes column types). | **Done** (PR #429) |

### Stage 2: Backend Core (Parallel Tracks)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 210 | 210A | TiptapRenderer service (JSON tree walker, ~250 LOC). Unit tests for all node types. Independent of entity changes. | **Done** (PR #430) |
| 2b | Epic 210 | 210B | VariableMetadataRegistry + GET /api/templates/variables endpoint. Integration tests. Depends on 210A (same package). | **Done** (PR #431) |
| 2a' | Epic 211 | 211A | DocumentTemplate + Clause entity JSONB annotation changes. Controller DTO updates (content/body as Object). CRUD integration test updates. **Can run in parallel with 210A/210B.** | **Done** (PR #432) |
| 2b' | Epic 211 | 211B | TemplateClauseSync service (extract clauseBlock nodes, diff + flush). Integration tests. Depends on 211A (entity must accept JSONB). | **Done** (PR #433) |

### Stage 3: Pipeline Integration (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 212 | 212A | Wire TiptapRenderer into PdfRenderingService. Replace renderThymeleaf() call chain. Update GeneratedDocumentService. Integration tests for full pipeline. | **Done** (PR #434) |
| 3b | Epic 212 | 212B | LegacyContentImporter service (Jsoup-based HTML-to-Tiptap converter, startup runner). Integration tests. | **Done** (PR #435) |

### Stage 4: Frontend Editor Foundation (Parallel with Stage 1-3)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 213 | 213A | Install Tiptap npm packages. Create DocumentEditor + EditorToolbar components (standard formatting only). Component tests. | **Done** (PR #436) |
| 4b | Epic 213 | 213B | Variable extension + VariableNodeView + VariablePicker dialog. LoopTable extension + LoopTableNodeView + LoopTableConfig. Component tests. | **Done** (PR #437) |
| 4c | Epic 213 | 213C | ClauseBlock extension + ClauseBlockNodeView (card with title bar, read-only content, menu). ClausePicker dialog (master-detail). Component tests. |

### Stage 5: Frontend Page Rewrites (After Stage 3 + 4)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5a | Epic 214 | 214A | Template editor page rewrite (/settings/templates/[id]/edit). Unified layout with DocumentEditor (no tabs). Settings panel. actions.ts updates for JSON content. |
| 5b | Epic 214 | 214B | Template save with TemplateClause sync (frontend sends JSON, backend syncs). Variable picker integration (fetch from /api/templates/variables). New template page update. |
| 5a' | Epic 215 | 215A | Clause library page rewrite (/settings/clauses). Content expand/collapse for every clause. "Used in" indicator. Inline Tiptap JSON rendering. **Can parallel with 214.** |
| 5b' | Epic 215 | 215B | Clause editor sheet with DocumentEditor (clause scope). Create + edit flows. System clause read-only + "Clone to customize". |

### Stage 6: Generation & Preview (After Stage 5)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 6a | Epic 216 | 216A | Generation dialog update: clause content expand, reorder, "Add from library" with ClausePicker. |
| 6b | Epic 216 | 216B | Client-side preview: entity picker, client-side Tiptap JSON renderer, PreviewPanel component. |

### Stage 7: Test Migration & Cleanup (After Stage 3)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 7a | Epic 217 | 217A | TestDocumentBuilder utility. Update all template/clause test helpers to produce Tiptap JSON. Visual regression tests for 3 platform templates. |
| 7b | Epic 217 | 217B | Delete Thymeleaf classes (LenientStandardDialect, LenientOGNLEvaluator, TemplateSecurityValidator, ClauseAssembler). Delete .html pack files. Delete dead tests. Remove dead frontend components. |

### Timeline

```
Stage 1:  [209A] ──► [209B]                                      ← DB migration (sequential)
Stage 2:  [210A] ──► [210B]  //  [211A] ──► [211B]              ← backend core (2 parallel tracks)
Stage 3:  [212A] ──► [212B]                                      ← pipeline (sequential, after Stage 2)
Stage 4:  [213A] ──► [213B] ──► [213C]                           ← editor foundation (parallel with 1-3)
Stage 5:  [214A] ──► [214B]  //  [215A] ──► [215B]              ← page rewrites (parallel tracks)
Stage 6:  [216A]  //  [216B]                                     ← gen dialog + preview (parallel)
Stage 7:  [217A] ──► [217B]                                      ← cleanup (sequential, after Stage 3)
```

**Critical path**: 209A -> 209B -> 210A -> 212A -> 217A -> 217B
**Maximum parallelism**: Stages 1-3 (backend) run fully parallel with Stage 4 (frontend foundation)

---

## Epic 209: Database Migration & Pack Conversion

**Goal**: Execute the V48 Flyway migration that changes `document_templates.content` from TEXT to JSONB and `clauses.body` from TEXT to JSONB. Convert platform template pack files from Thymeleaf HTML to Tiptap JSON. Update pack seeders to handle JSON format. This epic must complete before any other backend epic because it changes the column types that all other code depends on.

**References**: Architecture doc Sections 31.2, 31.3e, 31.3f, 31.7. [ADR-120](../adr/ADR-120-document-storage-format.md), [ADR-122](../adr/ADR-122-content-migration-strategy.md).

**Dependencies**: None (first epic)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **209A** | 209.1-209.7 | V48 migration (add JSONB columns, PL/pgSQL converter, column swap, legacy backup). Convert 3 template pack HTML files to JSON equivalents. Update pack.json contentFile references. Migration verification tests (~6 tests). | **Done** (PR #428) |
| **209B** | 209.8-209.13 | Convert 12 clause bodies in standard-clauses pack.json from HTML strings to Tiptap JSON objects. Update TemplatePackSeeder to read .json files and store JSONB. Update ClausePackSeeder to parse JSON body objects. Pack seeder integration tests (~6 tests). | **Done** (PR #429) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 209.1 | Create V48 Flyway migration | 209A | | `backend/src/main/resources/db/migration/tenant/V48__document_tiptap_migration.sql`. Steps: (1) ADD COLUMN content_json JSONB, legacy_content TEXT to document_templates. (2) ADD COLUMN body_json JSONB, legacy_body TEXT to clauses. (3) UPDATE PLATFORM templates: copy content to legacy_content, set content_json = NULL. (4) UPDATE SYSTEM clauses: copy body to legacy_body, set body_json = NULL. (5) PL/pgSQL function convert_html_to_tiptap_json() for ORG_CUSTOM/CLONED content. (6) Apply converter to org-custom templates and clauses. (7) DROP old TEXT columns, RENAME _json to canonical names. (8) Drop converter function. Pattern: follow V47 migration structure. See architecture doc Section 31.7 for full SQL. |
| 209.2 | Create engagement-letter.json | 209A | | `backend/src/main/resources/template-packs/common/engagement-letter.json`. Hand-convert `engagement-letter.html` to Tiptap JSON. Replace `th:text="${key}"` with variable nodes, `th:each` with loopTable nodes, HTML elements with Tiptap node types. See architecture doc Section 31.2 "Full Document Example" for format. |
| 209.3 | Create project-summary.json | 209A | | `backend/src/main/resources/template-packs/common/project-summary.json`. Hand-convert from `project-summary.html`. Same conversion approach as 209.2. |
| 209.4 | Create invoice-cover-letter.json | 209A | | `backend/src/main/resources/template-packs/common/invoice-cover-letter.json`. Hand-convert from `invoice-cover-letter.html`. Same conversion approach. |
| 209.5 | Update pack.json contentFile references | 209A | | `backend/src/main/resources/template-packs/common/pack.json`. Change `"contentFile": "engagement-letter.html"` to `"contentFile": "engagement-letter.json"`, and same for project-summary and invoice-cover-letter. |
| 209.6 | Create V48 migration verification test | 209A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/V48MigrationTest.java` (~4 tests). Verify: migration runs cleanly, content column is JSONB type, legacy_content preserves original HTML for PLATFORM templates, body column is JSONB type for clauses. Pattern: follow `projecttemplate/V30MigrationTest.java`. |
| 209.7 | Verify JSON pack files are valid | 209A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackJsonValidationTest.java` (~3 tests). Load each .json file, parse as Jackson JsonNode, verify root node has `type: "doc"` and `content` array. Verify variable nodes have `key` attribute. |
| 209.8 | Convert standard-clauses pack.json bodies to JSON | 209B | | `backend/src/main/resources/clause-packs/standard-clauses/pack.json`. All 12 clause `body` fields change from HTML strings to Tiptap JSON objects. `<span th:text="${org.name}">Firm</span>` becomes `{ "type": "variable", "attrs": { "key": "org.name" } }`. See architecture doc Section 31.3e for before/after format. |
| 209.9 | Update TemplatePackSeeder for JSON | 209B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java`. Change content file reading: parse .json files as `Map<String, Object>` via Jackson ObjectMapper instead of reading as String. Store as JSONB in `content` column. Pattern: existing seeder reads file -> store as String; now reads file -> parse JSON -> store as Map. |
| 209.10 | Update TemplatePackDefinition/TemplatePackTemplate | 209B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackDefinition.java` and `TemplatePackTemplate.java`. No field changes needed (contentFile is already a string reference). Verify content type handling is compatible with JSONB storage. |
| 209.11 | Update ClausePackSeeder for JSON | 209B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClausePackSeeder.java`. Change body parsing: `body` in pack.json is now a JSON object, not a string. Parse as `Map<String, Object>` and store in JSONB column. Pattern: follow TemplatePackSeeder JSON update. |
| 209.12 | Update TemplatePackSeeder integration tests | 209B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeederTest.java`. Update existing tests: seeded templates now have JSONB content (Map, not String). Verify JSON structure after seeding. Add test: seeded content has `type: "doc"` root node. (~3 updated tests, ~2 new tests). |
| 209.13 | Update ClausePackSeeder integration tests | 209B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/ClausePackSeederIntegrationTest.java`. Update: seeded clauses have JSONB body. Verify JSON structure. (~2 updated tests, ~1 new test). |

### Key Files

**Slice 209A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V48__document_tiptap_migration.sql`
- `backend/src/main/resources/template-packs/common/engagement-letter.json`
- `backend/src/main/resources/template-packs/common/project-summary.json`
- `backend/src/main/resources/template-packs/common/invoice-cover-letter.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/V48MigrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackJsonValidationTest.java`

**Slice 209A -- Modify:**
- `backend/src/main/resources/template-packs/common/pack.json`

**Slice 209A -- Read for context:**
- `backend/src/main/resources/template-packs/common/engagement-letter.html` (source for conversion)
- `backend/src/main/resources/template-packs/common/project-summary.html`
- `backend/src/main/resources/template-packs/common/invoice-cover-letter.html`
- `backend/src/main/resources/db/migration/tenant/V47__entity_lifecycle_integrity.sql` (migration pattern)
- `architecture/phase31-document-system-redesign.md` Section 31.7

**Slice 209B -- Modify:**
- `backend/src/main/resources/clause-packs/standard-clauses/pack.json`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClausePackSeeder.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeederTest.java` (update)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/ClausePackSeederIntegrationTest.java` (update)

### Architecture Decisions
- V48 is the next available tenant migration number (V47 used by Phase 29)
- Platform content set to NULL in migration, re-seeded by updated pack seeders
- Org-custom content wrapped in `legacyHtml` node by PL/pgSQL function (full conversion done by application-layer importer in Epic 212B)
- Legacy columns preserved permanently as safety net
- Template .html files replaced with .json files (old files deleted in Epic 217B)

---

## Epic 210: TiptapRenderer & Variable Endpoint

**Goal**: Create the backend TiptapRenderer service that walks Tiptap JSON trees and emits HTML, replacing Thymeleaf rendering. Add the VariableMetadataRegistry and the `GET /api/templates/variables` endpoint that powers the frontend variable picker.

**References**: Architecture doc Sections 31.3a, 31.4 (variable metadata endpoint), 31.8 (TiptapRenderer code pattern). [ADR-121](../adr/ADR-121-rendering-pipeline-architecture.md).

**Dependencies**: Epic 209 (migration must complete for JSONB columns to exist)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **210A** | 210.1-210.6 | TiptapRenderer service (JSON tree walker, all node types, variable resolution, clause block recursion, loop table iteration, mark handling). Unit tests for every node type (~15 tests). | **Done** (PR #430) |
| **210B** | 210.7-210.11 | VariableMetadataRegistry (static configuration per entity type). Variable metadata controller endpoint. TestDocumentBuilder utility class. Integration tests (~6 tests). | **Done** (PR #431) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 210.1 | Create TiptapRenderer service | 210A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java`. @Service. Primary method: `render(Map<String, Object> document, Map<String, Object> context, Map<UUID, Clause> clauses, String templateCss)` returns complete HTML document string. Loads default CSS from `classpath:templates/document-default.css`. Recursive `renderNode()` method with switch on node type. See architecture doc Section 31.8 for full code pattern (~250-300 LOC). |
| 210.2 | Implement standard node rendering | 210A | | In TiptapRenderer: doc, heading (h1-h3), paragraph, text (with marks), bulletList, orderedList, listItem, table, tableRow, tableCell, tableHeader, horizontalRule, hardBreak, legacyHtml. Helper methods: `renderChildren()`, `renderText()` (applies bold/italic/underline/link marks), `wrapTag()`, `renderTableCell()` (colspan/rowspan). |
| 210.3 | Implement variable resolution | 210A | | In TiptapRenderer: `resolveVariable(String key, Map<String, Object> context)`. Dot-path lookup (split on ".", walk Map chain). Any null segment returns empty string. HTML-escape final value via `HtmlUtils.htmlEscape()`. |
| 210.4 | Implement clauseBlock rendering | 210A | | In TiptapRenderer: when node type is `clauseBlock`, look up clause by UUID in clauses map. If found, render clause.getBody() recursively with same context, wrap in `<div class="clause-block" data-clause-slug="{slug}">`. If not found, emit HTML comment. Add circular reference guard (depth limit or visited set). |
| 210.5 | Implement loopTable rendering | 210A | | In TiptapRenderer: `renderLoopTable()` method. Resolve `attrs.dataSource` from context (expects `List<Map<String, Object>>`). Emit `<table><thead>` from column headers, `<tbody>` with one `<tr>` per list item. Each `<td>` resolves `attrs.columns[].key` from item map. Empty/null data source renders header only with empty tbody. |
| 210.6 | Create TiptapRenderer unit tests | 210A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TiptapRendererTest.java` (~15 tests). Test each node type: paragraph, heading levels, text with marks (bold, italic, underline, link, combined), bullet list, ordered list, table with colspan/rowspan, horizontal rule, hard break. Variable resolution: simple key, nested key, missing key returns empty, null-safe. ClauseBlock: found clause, missing clause. LoopTable: with data, empty data, missing data source. LegacyHtml passthrough. Full document assembly with DOCTYPE/style. |
| 210.7 | Create VariableMetadataRegistry | 210B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java`. Static configuration class. Method: `getVariableGroups(TemplateEntityType entityType)` returns groups of variables relevant to the entity type. Method: `getLoopSources(TemplateEntityType entityType)` returns available loop data sources. See architecture doc Section 31.4 for response shape. Variables derived from existing context builders (ProjectContextBuilder, CustomerContextBuilder, InvoiceContextBuilder). ~100 LOC. |
| 210.8 | Add variable metadata endpoint to controller | 210B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java`. New endpoint: `GET /api/templates/variables?entityType={PROJECT|CUSTOMER|INVOICE}`. Returns `VariableMetadataResponse` record with `groups` and `loopSources` lists. No ADMIN restriction (any authenticated member). |
| 210.9 | Create VariableMetadataResponse DTOs | 210B | | Records in controller or `template/dto/`: `VariableMetadataResponse(List<VariableGroup> groups, List<LoopSource> loopSources)`, `VariableGroup(String label, String prefix, List<VariableInfo> variables)`, `VariableInfo(String key, String label, String type)`, `LoopSource(String key, String label, List<String> entityTypes, List<String> fields)`. |
| 210.10 | Create TestDocumentBuilder utility | 210B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TestDocumentBuilder.java`. Fluent builder for constructing Tiptap JSON in tests. Methods: `doc()`, `heading(level, text)`, `paragraph(text)`, `variable(key)`, `clauseBlock(clauseId, slug, title, required)`, `loopTable(dataSource, columns)`, `text(content)`, `bold(content)`, `build()` -> `Map<String, Object>`. Pattern: follow TestCustomerFactory. |
| 210.11 | Create variable metadata integration tests | 210B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataEndpointTest.java` (~6 tests). GET returns variables for PROJECT entity type, CUSTOMER entity type, INVOICE entity type. Loop sources filtered by entity type. Invalid entity type returns 400. Any member can access (no ADMIN restriction). Pattern: MockMvc. |

### Key Files

**Slice 210A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TiptapRendererTest.java`

**Slice 210A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` (rendering patterns to replace)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/Clause.java` (clause body field access)
- `backend/src/main/resources/templates/document-default.css` (CSS loaded by renderer)
- `architecture/phase31-document-system-redesign.md` Section 31.3a, 31.8

**Slice 210B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TestDocumentBuilder.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataEndpointTest.java`

**Slice 210B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` (add endpoint)

**Slice 210B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilder.java` (variable list source)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/InvoiceContextBuilder.java`

### Architecture Decisions
- TiptapRenderer is pure Java string building -- no template engine library dependency
- Variable resolution uses dot-path map lookup -- no expression language, no injection surface
- Clause block rendering is recursive (same renderer, same context) with depth guard
- VariableMetadataRegistry is manually curated, not dynamically introspected from context builders

---

## Epic 211: Entity Updates & Template-Clause Sync

**Goal**: Update DocumentTemplate and Clause entities to use JSONB column annotations for `content` and `body` fields. Update all CRUD DTOs to accept/return JSON objects. Implement the TemplateClauseSync algorithm that extracts clauseBlock nodes from saved template documents and syncs the template_clauses join table.

**References**: Architecture doc Sections 31.2 (entity changes), 31.3b (CRUD updates), 31.3c (clause CRUD), 31.8 (sync algorithm). [ADR-123](../adr/ADR-123-template-clause-association-source-of-truth.md).

**Dependencies**: Epic 209 (columns must be JSONB before entities can be annotated)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **211A** | 211.1-211.7 | DocumentTemplate entity: change content from String/TEXT to Map/JSONB, add legacyContent. Clause entity: change body from String/TEXT to Map/JSONB, add legacyBody. Update all controller DTOs. Update existing CRUD integration tests (~8 updated tests, ~4 new tests). | **Done** (PR #432) |
| **211B** | 211.8-211.12 | TemplateClauseSync service: extract clauseBlock nodes from document JSON, diff against template_clauses table, create/delete/update. Wire into DocumentTemplateService.update(). Integration tests (~6 tests). | **Done** (PR #433) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 211.1 | Update DocumentTemplate entity for JSONB | 211A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java`. Change `content` field: `String` -> `Map<String, Object>` with `@JdbcTypeCode(SqlTypes.JSON)`, `@Column(name = "content", columnDefinition = "jsonb")`. Make nullable (PLATFORM templates NULL until seeder runs). Add `legacyContent` field (`String`, nullable TEXT). Update constructor and `updateContent()` domain method. |
| 211.2 | Update Clause entity for JSONB | 211A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/Clause.java`. Change `body` field: `String` -> `Map<String, Object>` with `@JdbcTypeCode(SqlTypes.JSON)`, `@Column(name = "body", columnDefinition = "jsonb")`. Add `legacyBody` field. Update constructor and update methods. |
| 211.3 | Update DocumentTemplateController DTOs | 211A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java`. In CreateTemplateRequest and UpdateTemplateRequest: change `content` field type from `String` to `Map<String, Object>`. In TemplateDetailResponse: change `content` to `Map<String, Object>`, add `legacyContent` field (String, nullable). Validate root node `type: "doc"` on create/update. |
| 211.4 | Update ClauseController DTOs | 211A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseController.java`. Change `body` field type from `String` to `Map<String, Object>` in create/update requests and detail responses. Add `legacyBody` field to detail response. |
| 211.5 | Update DocumentTemplateService | 211A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java`. Update create() and update() to handle Map<String, Object> content. No functional change beyond type alignment. |
| 211.6 | Update ClauseService | 211A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseService.java`. Update create() and update() to handle Map<String, Object> body. |
| 211.7 | Update CRUD integration tests | 211A | | Update: `template/DocumentTemplateControllerTest.java` (~4 tests updated to send/expect JSON content), `clause/ClauseControllerIntegrationTest.java` (~4 tests updated). Add: TemplateCrudJsonTest (~2 tests: create with JSON content, update content, verify round-trip), ClauseCrudJsonTest (~2 tests: create with JSON body, verify round-trip). Use TestDocumentBuilder from 210B if available, otherwise construct JSON maps manually. |
| 211.8 | Create TemplateClauseSync logic | 211B | | Add to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/TemplateClauseService.java` (or new class `TemplateClauseSync.java`). Method: `syncClausesFromDocument(UUID templateId, Map<String, Object> documentJson)`. Algorithm: (1) DFS extract clauseBlock nodes, (2) build (clauseId, sortOrder, required) tuples, (3) load existing TemplateClause records, (4) diff: delete removed, create added, update changed. See architecture doc Section 31.8 for code pattern. |
| 211.9 | Create clauseBlock extraction utility | 211B | | Helper method: `extractClauseBlocks(Map<String, Object> node, List<ClauseBlockRef> result)`. Recursive DFS on `content` array. When `type: "clauseBlock"`, extract clauseId, required from attrs. Record: `ClauseBlockRef(UUID clauseId, boolean required)`. |
| 211.10 | Wire sync into DocumentTemplateService.update() | 211B | | In `DocumentTemplateService.update()`: after saving content, call `templateClauseSync.syncClausesFromDocument(templateId, content)`. Run in same transaction. |
| 211.11 | Deprecate direct TemplateClause manipulation endpoints | 211B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/TemplateClauseController.java`. Add `@Deprecated` to POST and DELETE endpoints. Add Javadoc noting that associations are now synced from document JSON on template save per ADR-123. Endpoints remain functional for backward compatibility. |
| 211.12 | Create TemplateClauseSync integration tests | 211B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/TemplateClauseSyncTest.java` (~6 tests). Save template with 2 clauseBlock nodes -> 2 TemplateClause rows created. Remove one clauseBlock -> row deleted. Add clauseBlock -> row created. Reorder clauseBlocks -> sortOrder updated. Toggle required -> required flag updated. Save with no clauseBlocks -> all rows deleted. |

### Key Files

**Slice 211A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/Clause.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateControllerTest.java` (update)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/ClauseControllerIntegrationTest.java` (update)

**Slice 211B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/TemplateClauseService.java` (or create TemplateClauseSync.java)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` (wire sync)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/TemplateClauseController.java` (deprecate)

**Slice 211B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/TemplateClauseSyncTest.java`

### Architecture Decisions
- Document JSON is the source of truth for clause associations (ADR-123)
- TemplateClause table is synced on save as a materialized query index
- Sync runs in the same transaction as content save -- table is always consistent after save
- Direct TemplateClause manipulation endpoints deprecated, not removed

---

## Epic 212: Rendering Pipeline Switch & Legacy Import

**Goal**: Wire the TiptapRenderer into the existing PdfRenderingService and GeneratedDocumentService, replacing the Thymeleaf rendering call chain. Create the LegacyContentImporter that converts "simple" legacy HTML content to proper Tiptap JSON at application startup.

**References**: Architecture doc Sections 31.3a, 31.3d, 31.3f (Phase B), 31.8.

**Dependencies**: Epic 210 (TiptapRenderer must exist), Epic 211 (entities must use JSONB)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **212A** | 212.1-212.5 | Replace PdfRenderingService.renderThymeleaf() with TiptapRenderer.render(). Update GeneratedDocumentService to pass JSONB content. Update preview endpoint. Integration tests for full pipeline (~6 tests). | **Done** (PR #434) |
| **212B** | 212.6-212.10 | LegacyContentImporter service using Jsoup. Startup runner that converts simple legacyHtml nodes. Idempotent. Integration tests (~5 tests). | **Done** (PR #435) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 212.1 | Update PdfRenderingService to use TiptapRenderer | 212A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java`. Inject `TiptapRenderer`. Replace `renderThymeleaf()` call chain: (1) Load template content (now Map<String, Object>), (2) Build context via TemplateContextBuilder (unchanged), (3) Resolve clauses via ClauseResolver (unchanged) -> build `Map<UUID, Clause>` from list, (4) Call `tiptapRenderer.render(template.getContent(), contextMap, clausesMap, template.getCss())`, (5) Pass HTML to `htmlToPdf()` (unchanged). |
| 212.2 | Update GeneratedDocumentService | 212A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java`. If it delegates to PdfRenderingService, changes should be minimal -- the service calls remain the same, only the internal rendering step changed. Update clause_snapshots to store Tiptap JSON bodies (Map, not String) in the context snapshot. |
| 212.3 | Update preview endpoint | 212A | | In `PdfRenderingService` or controller: `previewHtml()` method now calls `TiptapRenderer.render()` instead of Thymeleaf. Returns HTML string (unchanged response format). |
| 212.4 | Update PdfRenderingService integration tests | 212A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingServiceTest.java`. Update: create templates with Tiptap JSON content (use TestDocumentBuilder). Verify HTML output from render(). Verify PDF generation produces valid PDF bytes. Add: test with variable nodes, test with clauseBlock nodes, test with loopTable node. (~4 updated tests, ~3 new tests). |
| 212.5 | Update DocumentGenerationIntegrationTest | 212A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentGenerationIntegrationTest.java`. Update template creation to use Tiptap JSON content. Verify full generation flow (template + clauses + context -> PDF + S3 upload + GeneratedDocument record). Verify clause_snapshots contain Tiptap JSON. (~3 updated tests). |
| 212.6 | Create LegacyContentImporter service | 212B | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/LegacyContentImporter.java`. @Service. Uses Jsoup (already a transitive dependency of OpenHTMLToPDF) to parse HTML DOM and convert to Tiptap JSON nodes. Conversion mapping: `<p>` -> paragraph, `<h1>`-`<h3>` -> heading, `<strong>` -> bold mark, `<em>` -> italic mark, `<u>` -> underline mark, `<a>` -> link mark, `<ul>/<ol>` -> lists, `<table>` -> table, `<hr>` -> horizontalRule, `<br>` -> hardBreak, `<span th:text="${key}">` -> variable node. |
| 212.7 | Create startup runner for LegacyContentImporter | 212B | | Create `LegacyContentImportRunner` (@Component, implements ApplicationRunner or @EventListener(ApplicationReadyEvent)). Scans templates/clauses for content containing `legacyHtml` nodes with `complexity: "simple"`. Calls LegacyContentImporter for each. Runs asynchronously (@Async). Idempotent: sets `converted: true` attribute on processed nodes. Does not block application startup. |
| 212.8 | Implement Jsoup-to-Tiptap conversion logic | 212B | | In LegacyContentImporter: method `convertHtml(String html) -> Map<String, Object>`. Walk Jsoup Document/Elements tree. Map HTML elements to Tiptap node types. Handle text nodes with marks. Handle nested elements (e.g., `<li><strong>text</strong></li>`). Return Tiptap JSON document map. Content that fails conversion: remain as legacyHtml node, log warning. |
| 212.9 | Create LegacyContentImporter unit tests | 212B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/LegacyContentImporterTest.java` (~8 tests). Test: paragraph conversion, heading levels, bold/italic/underline marks, link marks, lists, tables, Thymeleaf th:text variable extraction, combined formatting, unconvertible content returns empty/fallback. |
| 212.10 | Create startup runner integration test | 212B | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/LegacyContentImportRunnerTest.java` (~3 tests). Insert template with legacyHtml content (complexity: "simple") into DB, run importer, verify content is converted to proper Tiptap JSON nodes. Verify "complex" content is skipped. Verify idempotency (second run does nothing). |

### Key Files

**Slice 212A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentGenerationIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePreviewControllerTest.java`

**Slice 212A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TiptapRenderer.java` (created in 210A)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseResolver.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateContextBuilder.java`

**Slice 212B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/LegacyContentImporter.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/LegacyContentImporterTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/LegacyContentImportRunnerTest.java`

### Architecture Decisions
- PDF engine (OpenHTMLToPDF) is unchanged -- only the HTML generation step changes
- Context builders are unchanged -- they produce the same Map<String, Object> output
- ClauseResolver is unchanged -- it reads from template_clauses table (synced by Epic 211B)
- LegacyContentImporter runs asynchronously at startup -- does not block readiness
- Jsoup is already available as a transitive dependency (no new dependency)

---

## Epic 213: Tiptap Editor Foundation

**Goal**: Install Tiptap npm packages and create the core DocumentEditor component with standard formatting support, plus all three custom Tiptap extensions (variable, clauseBlock, loopTable) with their React node views and associated picker/config dialogs.

**References**: Architecture doc Section 31.6 (frontend architecture, component tree, extensions, npm packages). [ADR-119](../adr/ADR-119-editor-library-selection.md).

**Dependencies**: None (frontend work, independent of backend)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **213A** | 213.1-213.7 | Install Tiptap npm packages. Create DocumentEditor component with EditorToolbar (standard formatting: bold, italic, underline, headings, lists, tables, horizontal rule, links). Component tests (~6 tests). | **Done** (PR #436) |
| **213B** | 213.8-213.15 | Variable extension + VariableNodeView (styled chip). VariablePicker dialog (categorized list). LoopTable extension + LoopTableNodeView (placeholder table). LoopTableConfig popover. Component tests (~6 tests). | **Done** (PR #437) |
| **213C** | 213.16-213.22 | ClauseBlock extension + ClauseBlockNodeView (card with title, read-only content, context menu). ClausePicker dialog (master-detail layout with content preview). Component tests (~6 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 213.1 | Install Tiptap npm packages | 213A | | Run `pnpm add @tiptap/react @tiptap/starter-kit @tiptap/extension-table @tiptap/extension-link @tiptap/extension-underline @tiptap/extension-placeholder @tiptap/pm`. These are the core packages. Custom extensions built in-house. |
| 213.2 | Create DocumentEditor component | 213A | | `frontend/components/editor/DocumentEditor.tsx`. Props: `content` (Tiptap JSON), `onUpdate` (callback with JSON), `scope` ("template" \| "clause"), `editable` (boolean, default true). Uses `useEditor` hook with StarterKit + Table + Link + Underline + Placeholder extensions. `editor.getJSON()` for serialization, `editor.commands.setContent(json)` for loading. Dynamic import wrapper for code-splitting (editor routes only). |
| 213.3 | Create EditorToolbar component | 213A | | `frontend/components/editor/EditorToolbar.tsx`. Shadcn UI toolbar buttons: Bold (B), Italic (I), Underline (U), heading dropdown (H1/H2/H3), Bullet list, Ordered list, Table insert, Horizontal rule, Link. Separator groups between formatting/structure/insert. Uses `editor.chain().focus().toggleBold().run()` pattern. Active state highlighting (button variant changes when mark/node is active). |
| 213.4 | Create editor CSS styles | 213A | | Editor content area styling: prose-like typography matching the Signal Deck design system. Content area: white background, document-like padding, teal accent for selection. Toolbar: compact, border-b, bg-slate-50. Uses Tailwind CSS classes + Tiptap's `.ProseMirror` class overrides. |
| 213.5 | Create editor index/barrel exports | 213A | | `frontend/components/editor/index.ts`. Export DocumentEditor, EditorToolbar, and future extension components. |
| 213.6 | Create DocumentEditor component tests | 213A | | `frontend/__tests__/components/editor/DocumentEditor.test.tsx` (~4 tests). Renders with empty content. Loads Tiptap JSON content. Calls onUpdate when content changes. Respects editable=false (read-only mode). Pattern: follow existing component test patterns in `__tests__/`. |
| 213.7 | Create EditorToolbar component tests | 213A | | `frontend/__tests__/components/editor/EditorToolbar.test.tsx` (~2 tests). Toolbar buttons render. Bold button toggles bold mark (verify via editor state). |
| 213.8 | Create variable Tiptap extension | 213B | | `frontend/components/editor/extensions/variable.ts`. Custom Node extension: name "variable", group "inline", inline true, atom true. Attributes: `key` (string, required). parseHTML: `[data-variable-key]` span. renderHTML: span with data attribute. addNodeView: renders VariableNodeView React component. |
| 213.9 | Create VariableNodeView | 213B | | `frontend/components/editor/node-views/VariableNodeView.tsx`. React node view component. Renders as styled pill/chip: light blue (teal-50) background, rounded-md, px-1.5 py-0.5, monospace text (font-mono text-xs), border teal-200. Shows `{key}` text (e.g., "customer.name"). Selectable on click (ProseMirror selection). Non-editable (atom). |
| 213.10 | Create VariablePicker dialog | 213B | | `frontend/components/editor/VariablePicker.tsx`. Shadcn Dialog component. Props: `entityType` (from template), `onSelect(key: string)`, `open`/`onOpenChange`. Fetches variable metadata from `GET /api/templates/variables?entityType=X`. Groups displayed as collapsible sections with prefix labels. Each variable shows key + label. Click inserts variable node. Search input filters across all groups. |
| 213.11 | Create loopTable Tiptap extension | 213B | | `frontend/components/editor/extensions/loopTable.ts`. Custom Node extension: name "loopTable", group "block", atom true. Attributes: `dataSource` (string), `columns` (array of {header, key}). addNodeView: renders LoopTableNodeView. |
| 213.12 | Create LoopTableNodeView | 213B | | `frontend/components/editor/node-views/LoopTableNodeView.tsx`. Renders table with: thead with column headers, 1-2 tbody rows with `{key}` faded text placeholders, "Data source: {dataSource}" label below in text-xs text-muted-foreground. Click opens LoopTableConfig popover. Styled with Shadcn Table component. |
| 213.13 | Create LoopTableConfig popover | 213B | | `frontend/components/editor/LoopTableConfig.tsx`. Shadcn Popover. Data source: combobox with options from variable metadata `loopSources` (filtered by entity type). Columns list: add/remove/reorder, each with header text input and data key combobox (from loopSource fields). "Apply" button updates node attributes. |
| 213.14 | Create VariablePicker component tests | 213B | | `frontend/__tests__/components/editor/VariablePicker.test.tsx` (~3 tests). Renders with variable groups. Search filters variables. Selection calls onSelect with key. |
| 213.15 | Create LoopTable component tests | 213B | | `frontend/__tests__/components/editor/LoopTableNodeView.test.tsx` (~3 tests). Renders table with columns. Shows placeholder rows. Shows data source label. |
| 213.16 | Create clauseBlock Tiptap extension | 213C | | `frontend/components/editor/extensions/clauseBlock.ts`. Custom Node extension: name "clauseBlock", group "block", atom true, draggable true. Attributes: `clauseId` (UUID string), `slug` (string), `title` (string), `required` (boolean). addNodeView: renders ClauseBlockNodeView. |
| 213.17 | Create ClauseBlockNodeView | 213C | | `frontend/components/editor/node-views/ClauseBlockNodeView.tsx`. Renders as card: title bar (FileText icon + title + required badge (if true) + DropdownMenu trigger), body area showing clause content (read-only rendered Tiptap JSON -- fetched from API and cached), left border accent (border-l-4 border-teal-500), bg-slate-50. Drag handle via ProseMirror drag API. Menu items: Move up, Move down, Toggle required/optional, Replace clause (opens ClausePicker), Remove from template, Edit clause (opens clause editor). |
| 213.18 | Create clause content fetching hook | 213C | | `frontend/components/editor/hooks/useClauseContent.ts`. Custom hook: `useClauseContent(clauseId: string)`. Fetches clause detail from `GET /api/clauses/{id}`. Returns `{ body, title, isLoading }`. Caches results in a Map to avoid re-fetching for the same clauseId. Used by ClauseBlockNodeView. |
| 213.19 | Create ClausePicker dialog | 213C | | `frontend/components/editor/ClausePicker.tsx`. Shadcn Dialog, master-detail layout. Left panel: categorized clause list (fetches from `GET /api/clauses`), search input, category collapsible sections, source badges (System/Custom/Cloned), "already in template" checkmark indicator. Right panel: selected clause full rendered content (Tiptap JSON -> rich text), metadata (category, source, description, "Used in N templates" count). "Add Clause" button: calls `onSelect(clause)` -> inserts clauseBlock node at cursor position. |
| 213.20 | Create ClauseBlockNodeView component tests | 213C | | `frontend/__tests__/components/editor/ClauseBlockNodeView.test.tsx` (~3 tests). Renders title bar with title and required badge. Shows context menu items. Renders loading state while fetching content. |
| 213.21 | Create ClausePicker component tests | 213C | | `frontend/__tests__/components/editor/ClausePicker.test.tsx` (~3 tests). Renders categorized clause list. Selecting a clause shows content in detail panel. "Add Clause" button calls onSelect. |
| 213.22 | Create editor extensions barrel export | 213C | | `frontend/components/editor/extensions/index.ts`. Export variable, clauseBlock, loopTable extensions. Update DocumentEditor to include all three extensions when `scope: "template"`. Exclude clauseBlock and loopTable when `scope: "clause"`. |

### Key Files

**Slice 213A -- Create:**
- `frontend/components/editor/DocumentEditor.tsx`
- `frontend/components/editor/EditorToolbar.tsx`
- `frontend/components/editor/index.ts`
- `frontend/__tests__/components/editor/DocumentEditor.test.tsx`
- `frontend/__tests__/components/editor/EditorToolbar.test.tsx`

**Slice 213A -- Modify:**
- `frontend/package.json` (add Tiptap dependencies)

**Slice 213B -- Create:**
- `frontend/components/editor/extensions/variable.ts`
- `frontend/components/editor/node-views/VariableNodeView.tsx`
- `frontend/components/editor/VariablePicker.tsx`
- `frontend/components/editor/extensions/loopTable.ts`
- `frontend/components/editor/node-views/LoopTableNodeView.tsx`
- `frontend/components/editor/LoopTableConfig.tsx`
- `frontend/__tests__/components/editor/VariablePicker.test.tsx`
- `frontend/__tests__/components/editor/LoopTableNodeView.test.tsx`

**Slice 213C -- Create:**
- `frontend/components/editor/extensions/clauseBlock.ts`
- `frontend/components/editor/node-views/ClauseBlockNodeView.tsx`
- `frontend/components/editor/hooks/useClauseContent.ts`
- `frontend/components/editor/ClausePicker.tsx`
- `frontend/components/editor/extensions/index.ts`
- `frontend/__tests__/components/editor/ClauseBlockNodeView.test.tsx`
- `frontend/__tests__/components/editor/ClausePicker.test.tsx`

**Read for context:**
- `frontend/components/clauses/clause-form-dialog.tsx` (existing clause UI patterns)
- `frontend/components/ui/` (Shadcn UI component imports)
- `frontend/CLAUDE.md` (conventions)

### Architecture Decisions
- Tiptap packages dynamically imported to avoid loading editor bundle on non-authoring pages
- DocumentEditor accepts `scope` prop to control toolbar and extension sets
- Clause content fetched and cached per clauseId in ClauseBlockNodeView
- ClausePicker reused in template editor toolbar and generation dialog

---

## Epic 214: Template Editor Rewrite

**Goal**: Rewrite the template editor page to use the DocumentEditor component instead of the HTML textarea. Remove the separate Clauses tab (clauses are now inline block nodes). Wire the template save flow to send JSON content and trigger TemplateClause sync on the backend.

**References**: Architecture doc Sections 31.6 (page rewrites), 31.5 (sequence diagrams).

**Dependencies**: Epic 210B (variable endpoint for picker), Epic 211 (JSONB endpoints + clause sync), Epic 213 (editor components)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **214A** | 214.1-214.6 | Template editor page rewrite: unified layout with DocumentEditor, settings panel (collapsible), remove tabs. Template creation page update. Actions.ts updates for JSON content. Component tests (~4 tests). | |
| **214B** | 214.7-214.11 | Variable picker integration (toolbar -> fetch from /api/templates/variables), clause picker integration (toolbar -> inserts clauseBlock), template save sends JSON content (backend syncs TemplateClause). New template page. Tests (~4 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 214.1 | Rewrite template editor page layout | 214A | | `frontend/app/(app)/org/[slug]/settings/templates/[id]/edit/page.tsx`. New layout: top bar (back link to template list, inline-editable template name, Save button), collapsible settings panel (name, category dropdown, entity type dropdown, description, CSS textarea in "Advanced" accordion), DocumentEditor filling main content area (scope="template"). Remove existing tabs (Content/Clauses). Load template via existing `getTemplate(id)` action, pass Tiptap JSON content to editor. |
| 214.2 | Update template detail loading | 214A | | The template detail endpoint now returns `content` as a JSON object. Update the page's data fetching to pass the JSON content directly to `DocumentEditor`'s `content` prop. Handle `legacyContent` field: if template has non-null `legacyContent` and content contains a `legacyHtml` node, show a "Migration needed" info banner with the original HTML visible in a collapsible reference panel. |
| 214.3 | Update template actions.ts for JSON content | 214A | | `frontend/app/(app)/org/[slug]/settings/templates/actions.ts`. Update `createTemplate()` and `updateTemplate()` server actions: `content` field is now `Record<string, unknown>` (JSON object) instead of `string`. Update `fetchTemplate()` return type. Add `fetchVariableMetadata(entityType: string)` action calling `GET /api/templates/variables`. |
| 214.4 | Remove template-clauses-tab component | 214A | | Delete `frontend/components/templates/template-clauses-tab.tsx` (or mark for deletion in 217B). Clauses are now inline clauseBlock nodes in the editor document. The separate tab is no longer needed. |
| 214.5 | Remove TemplateVariableReference component | 214A | | Delete `frontend/components/templates/TemplateVariableReference.tsx` (or mark for deletion in 217B). Replaced by VariablePicker from the editor toolbar. |
| 214.6 | Create template editor page tests | 214A | | `frontend/__tests__/app/settings/templates/editor.test.tsx` (~4 tests). Page renders with DocumentEditor. Settings panel toggles. Save button sends JSON content. Legacy content banner shows when applicable. |
| 214.7 | Wire VariablePicker to template editor toolbar | 214B | | In template editor page: pass `entityType` from template to DocumentEditor. In EditorToolbar: "{x} Variable" button opens VariablePicker dialog. On variable selection, insert variable node at cursor via `editor.chain().focus().insertContent({ type: 'variable', attrs: { key } }).run()`. |
| 214.8 | Wire ClausePicker to template editor toolbar | 214B | | In EditorToolbar: "Clause" button opens ClausePicker dialog. On clause selection, insert clauseBlock node at cursor. Also render an "[+ Add Clause]" button below the editor content as a convenience insertion point. |
| 214.9 | Implement template save with JSON content | 214B | | Template save flow: get JSON from editor via `editor.getJSON()`, call `updateTemplate(id, { content: json, ...otherFields })`. Backend syncs TemplateClause table from clauseBlock nodes (handled by 211B). Show success toast on save. |
| 214.10 | Update template creation page | 214B | | `frontend/app/(app)/org/[slug]/settings/templates/new/page.tsx`. Replace HTML textarea with DocumentEditor (empty initial content). Category and entity type selection drives VariablePicker entity type. Save calls `createTemplate()` with JSON content. |
| 214.11 | Create template save integration tests | 214B | | `frontend/__tests__/app/settings/templates/save.test.tsx` (~4 tests). Save sends JSON content to API. Variable insertion via picker appears in saved content. Clause block insertion appears in saved content. New template creation with editor content. |

### Key Files

**Slice 214A -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/templates/[id]/edit/page.tsx` (rewrite)
- `frontend/app/(app)/org/[slug]/settings/templates/actions.ts` (update)

**Slice 214A -- Delete (or mark for 217B):**
- `frontend/components/templates/template-clauses-tab.tsx`
- `frontend/components/templates/TemplateVariableReference.tsx`

**Slice 214A -- Create:**
- `frontend/__tests__/app/settings/templates/editor.test.tsx`

**Slice 214B -- Modify:**
- `frontend/components/editor/EditorToolbar.tsx` (wire variable/clause pickers)
- `frontend/components/editor/DocumentEditor.tsx` (accept entityType prop)
- `frontend/app/(app)/org/[slug]/settings/templates/new/page.tsx` (rewrite)

**Slice 214B -- Create:**
- `frontend/__tests__/app/settings/templates/save.test.tsx`

### Architecture Decisions
- No separate Clauses tab -- clauses are inline clauseBlock nodes in the document
- Template save is a single API call -- backend handles TemplateClause sync
- Entity type drives VariablePicker content -- passed from template settings

---

## Epic 215: Clause Library & Editor Rewrite

**Goal**: Rewrite the clause library page to show clause content inline (expandable) with rich text rendering. Replace the clause form dialog textarea with the DocumentEditor component (clause scope). Add "Used in N templates" indicator.

**References**: Architecture doc Sections 31.6 (page rewrites).

**Dependencies**: Epic 213 (editor components)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **215A** | 215.1-215.6 | Clause library page rewrite: content expand/collapse for every clause (rendered Tiptap JSON), "Used in N templates" indicator, system clause visibility. Component tests (~4 tests). | |
| **215B** | 215.7-215.11 | Clause editor sheet with DocumentEditor (clause scope). Create + edit flows. System clause read-only mode with "Clone to customize" action. Component tests (~4 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 215.1 | Rewrite clauses-content.tsx with content expansion | 215A | | `frontend/components/clauses/clauses-content.tsx`. Each clause card: title, source badge, description, chevron toggle to expand/collapse body content. Expanded body: render Tiptap JSON as read-only rich text using a read-only DocumentEditor instance (editable=false) or a lightweight renderer. Works for System clauses too (body is now JSONB). Group by category with collapsible headers (unchanged). |
| 215.2 | Add "Used in" indicator | 215A | | Each clause card shows "Used in N templates" count. Fetch from clause detail API (if it includes template count) or a dedicated endpoint. Click shows template names in a popover. If no endpoint exists, derive from template-clause associations via a new lightweight endpoint or include in clause list response. |
| 215.3 | Update clause list API client | 215A | | `frontend/lib/actions/clause-actions.ts`. Update clause list/detail response types: `body` is now `Record<string, unknown>` (Tiptap JSON) instead of `string`. Add `legacyBody` field to detail response type. |
| 215.4 | Add "migration needed" badge for legacy content | 215A | | If a clause's body contains a `legacyHtml` node type, show a small "Migration needed" badge next to the title. Expanding the content shows the legacyHtml rendered as-is with a note: "This clause was auto-migrated. Open in editor to review and save." |
| 215.5 | Update clause library page | 215A | | `frontend/app/(app)/org/[slug]/settings/clauses/page.tsx`. Use updated clauses-content.tsx. Search and category filter unchanged. "New Clause" button opens clause editor (215B). Context menu: System clauses get Clone + Preview; Custom/Cloned get Edit + Clone + Preview + Deactivate. |
| 215.6 | Create clause library component tests | 215A | | `frontend/__tests__/components/clauses/clauses-content.test.tsx` (~4 tests). Clause cards render with expand toggle. Expanding shows rendered body content. "Used in" indicator shows count. Legacy badge appears for legacyHtml content. |
| 215.7 | Create clause editor sheet | 215B | | `frontend/components/clauses/clause-editor-sheet.tsx`. Shadcn Sheet (or Dialog). Contents: title input, slug (read-only, auto-generated), category combobox, description textarea. DocumentEditor component for clause body (scope="clause" -- no clause insertion or loop table in toolbar). Variables ARE allowed (resolve from same context map). Save and Cancel buttons. |
| 215.8 | Implement clause create flow | 215B | | "New Clause" button opens clause editor sheet with empty fields. On save: call `createClause({ title, category, description, body: editor.getJSON() })`. Close sheet and refresh list. |
| 215.9 | Implement clause edit flow | 215B | | Edit action opens clause editor sheet with pre-filled fields and content. On save: call `updateClause(id, { title, category, description, body: editor.getJSON() })`. Show warning toast: "Editing this clause will affect all templates that use it." |
| 215.10 | Implement system clause read-only mode | 215B | | When editing a System clause: editor opens in read-only mode (editable=false). Show an action bar at top: "This is a system clause. Clone to customize." with a "Clone" button. Clone creates a CLONED copy and opens it in editable mode. |
| 215.11 | Create clause editor component tests | 215B | | `frontend/__tests__/components/clauses/clause-editor-sheet.test.tsx` (~4 tests). Sheet renders with DocumentEditor. Save sends JSON body. System clause is read-only with clone action. Edit shows warning about shared content. |

### Key Files

**Slice 215A -- Modify:**
- `frontend/components/clauses/clauses-content.tsx` (rewrite)
- `frontend/app/(app)/org/[slug]/settings/clauses/page.tsx` (update)
- `frontend/lib/actions/clause-actions.ts` (update types)

**Slice 215A -- Create:**
- `frontend/__tests__/components/clauses/clauses-content.test.tsx`

**Slice 215B -- Create:**
- `frontend/components/clauses/clause-editor-sheet.tsx`
- `frontend/__tests__/components/clauses/clause-editor-sheet.test.tsx`

**Slice 215B -- Modify/Delete:**
- `frontend/components/clauses/clause-form-dialog.tsx` (replace with clause-editor-sheet or delete)

### Architecture Decisions
- Clause body rendered using read-only DocumentEditor instance for consistency
- Clause scope toolbar excludes clauseBlock and loopTable extensions
- System clause read-only + clone-to-customize preserves system content integrity

---

## Epic 216: Generation Dialog & Preview

**Goal**: Update the generation dialog to show clause content inline with expand toggles, reorder support, and "Add from library" using the ClausePicker. Add client-side preview with entity data resolution.

**References**: Architecture doc Sections 31.6 (page rewrites), 31.5 (client-side preview sequence).

**Dependencies**: Epic 213 (editor components), Epic 214 (template editor integration)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **216A** | 216.1-216.5 | Generation dialog clause step update: content expand toggle, required badges, reorder via drag/up-down, "Add from library" button opens ClausePicker. Component tests (~4 tests). | |
| **216B** | 216.6-216.11 | Client-side preview: entity picker, client-side Tiptap JSON renderer (TypeScript mirror of TiptapRenderer), PreviewPanel component with document-like styling. Component tests (~4 tests). | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 216.1 | Update generation clause step with content visibility | 216A | | `frontend/components/templates/generation-clause-step.tsx`. Each clause row: title, category badge, expand toggle (chevron) to show full clause body (rendered Tiptap JSON via read-only DocumentEditor). Required clauses: checked + disabled + [Required] badge. Optional clauses: checked by default, can uncheck. |
| 216.2 | Add clause reorder to generation dialog | 216A | | In generation clause step: up/down arrow buttons for each clause row, or drag-and-drop reorder. Reordering updates the clause selection order sent to the generation API. |
| 216.3 | Add "Add from library" button | 216A | | Button below the clause list opens ClausePicker dialog. Selected clause is added to the generation clause list (appended at bottom). Already-included clauses marked in the picker. |
| 216.4 | Update GenerateDocumentDialog | 216A | | `frontend/components/templates/GenerateDocumentDialog.tsx`. If this dialog wraps the clause step, update to use new clause step. Ensure clause content expansion doesn't break dialog height (use scroll area). |
| 216.5 | Create generation dialog tests | 216A | | `frontend/__tests__/components/templates/generation-clause-step.test.tsx` (~4 tests). Clause list renders with expand toggle. Expanding shows content. Required clauses cannot be unchecked. "Add from library" opens ClausePicker. |
| 216.6 | Create client-side Tiptap JSON renderer | 216B | | `frontend/components/editor/client-renderer.ts`. TypeScript function `renderTiptapToHtml(doc: TiptapDoc, context: Record<string, unknown>, clauses: Map<string, TiptapDoc>): string`. Mirror of backend TiptapRenderer logic: walk JSON tree, resolve variable nodes from context via dot-path lookup, render clauseBlock nodes with clause body, render loopTable nodes with collection data. Output: HTML string. |
| 216.7 | Create PreviewPanel component | 216B | | `frontend/components/editor/PreviewPanel.tsx`. Displays rendered HTML in a document-like view: white background, max-w-[210mm] centered, p-8 for margins, print-friendly typography (serif headings, sans body), border shadow for page edge effect. Uses `dangerouslySetInnerHTML` with sanitized output (or an iframe). |
| 216.8 | Create entity picker component | 216B | | `frontend/components/editor/EntityPicker.tsx`. Shadcn Dialog or Combobox. Based on template entity type (PROJECT/CUSTOMER/INVOICE), fetches list of entities from existing REST APIs (GET /api/projects, GET /api/customers, GET /api/invoices). User selects one. Returns entity detail data for preview context. |
| 216.9 | Wire preview into template editor | 216B | | In template editor page (214A): "Preview with data" button in footer. Click: (1) opens EntityPicker, (2) user selects entity, (3) fetch entity detail from existing API, (4) build context map from entity data, (5) render using client-side renderer, (6) show in PreviewPanel. "Download PDF" button in preview triggers backend generation flow. |
| 216.10 | Wire preview into clause editor | 216B | | In clause editor sheet (215B): "Preview with data" button. Same flow as template preview but renders clause body only (not full document). |
| 216.11 | Create preview component tests | 216B | | `frontend/__tests__/components/editor/PreviewPanel.test.tsx` (~4 tests). PreviewPanel renders HTML content. EntityPicker shows entity list. Client-side renderer resolves variables. Preview button triggers entity selection flow. |

### Key Files

**Slice 216A -- Modify:**
- `frontend/components/templates/generation-clause-step.tsx` (update)
- `frontend/components/templates/GenerateDocumentDialog.tsx` (update)

**Slice 216A -- Create:**
- `frontend/__tests__/components/templates/generation-clause-step.test.tsx`

**Slice 216B -- Create:**
- `frontend/components/editor/client-renderer.ts`
- `frontend/components/editor/PreviewPanel.tsx`
- `frontend/components/editor/EntityPicker.tsx`
- `frontend/__tests__/components/editor/PreviewPanel.test.tsx`

**Slice 216B -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/templates/[id]/edit/page.tsx` (add preview button)
- `frontend/components/clauses/clause-editor-sheet.tsx` (add preview button)

### Architecture Decisions
- Client-side preview is entirely browser-based -- no backend call for HTML preview
- Client-side renderer is a TypeScript mirror of backend TiptapRenderer (same logic)
- PDF preview still requires backend (OpenHTMLToPDF is server-side)
- PreviewPanel uses document-like styling to approximate PDF output

---

## Epic 217: Backend Test Migration & Cleanup

**Goal**: Migrate all remaining backend tests to use Tiptap JSON content. Create visual regression tests for the 3 platform templates. Delete all Thymeleaf-era code (classes, methods, resource files, tests, dead frontend components).

**References**: Architecture doc Section 31.9 (deletion inventory).

**Dependencies**: Epic 212 (pipeline must be switched before cleanup)

**Scope**: Backend + Frontend (cleanup only)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **217A** | 217.1-217.6 | Update remaining test helpers to produce Tiptap JSON. Visual regression tests for 3 platform templates (compare new pipeline HTML to reference output). Update acceptance workflow tests. (~10 tests updated, ~3 new tests). | |
| **217B** | 217.7-217.14 | Delete Thymeleaf backend classes (LenientStandardDialect, LenientOGNLEvaluator, TemplateSecurityValidator, ClauseAssembler). Delete dead PdfRenderingService methods. Delete .html template pack files. Delete dead frontend components. Delete dead tests. | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 217.1 | Update all template test data builders | 217A | | Search for test files that create DocumentTemplate with String content. Update to use TestDocumentBuilder (from 210B) to produce Tiptap JSON. Files to check: `TemplateCloneResetTest.java`, `TemplateValidationIntegrationTest.java`, `DocumentAuditNotificationTest.java`, any test factory methods. |
| 217.2 | Update all clause test data builders | 217A | | Search for test files that create Clause with String body. Update to use Tiptap JSON maps. Files to check: `ClauseTest.java`, `ClauseServiceTest.java`, `ClauseResolverTest.java`, `TemplateClauseServiceTest.java`, `ClauseRepositoryIntegrationTest.java`. |
| 217.3 | Create visual regression tests | 217A | | `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/VisualRegressionTest.java` (~3 tests). For each platform template (engagement-letter, project-summary, invoice-cover-letter): (1) Load JSON pack template, (2) Build test context with known data, (3) Render via TiptapRenderer, (4) Assert output HTML contains expected structure (headings, paragraphs, variable values, clause sections, loop table rows). Not pixel-level comparison -- structural equivalence assertions. |
| 217.4 | Update acceptance workflow tests | 217A | | If Phase 28 acceptance tests generate documents, update them to work with the new pipeline. Templates in these tests should use Tiptap JSON content. Files: search for `acceptance/` test files that reference template/document generation. |
| 217.5 | Update context builder tests | 217A | | `CustomerContextBuilderTest.java`, `InvoiceContextBuilderTest.java`, `ProjectContextBuilderTest.java`. These should be unaffected (context builders produce Map<String, Object> regardless of renderer). Verify they still pass. No changes expected. |
| 217.6 | Verify all tests pass | 217A | | Run `./mvnw clean verify -q`. All backend tests must pass. Fix any remaining test failures from the JSON transition. |
| 217.7 | Delete LenientStandardDialect | 217B | | Delete `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/LenientStandardDialect.java`. |
| 217.8 | Delete LenientOGNLEvaluator | 217B | | Delete `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/LenientOGNLEvaluator.java`. |
| 217.9 | Delete TemplateSecurityValidator and tests | 217B | | Delete `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateSecurityValidator.java`, `TemplateSecurityException.java`. Delete `backend/src/test/java/.../template/TemplateValidationIntegrationTest.java` (or the SSTI-specific tests within it). |
| 217.10 | Delete ClauseAssembler and tests | 217B | | Delete `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseAssembler.java`. Delete `backend/src/test/java/.../clause/ClauseAssemblerTest.java`. |
| 217.11 | Clean PdfRenderingService of dead methods | 217B | | Remove `renderThymeleaf()`, `renderFragment()`, `injectClauseContext()`, Thymeleaf TemplateEngine field and setup code from PdfRenderingService. Verify `TemplateValidationService.java` -- if it validates HTML content or calls TemplateSecurityValidator, remove those methods. Do NOT remove the Thymeleaf Maven dependency if email templates (Phase 24) use it. |
| 217.12 | Delete .html template pack files | 217B | | Delete `backend/src/main/resources/template-packs/common/engagement-letter.html`, `project-summary.html`, `invoice-cover-letter.html`. These were replaced by .json files in 209A. |
| 217.13 | Delete dead frontend components | 217B | | Delete (if not already deleted in 214A): `frontend/components/templates/template-clauses-tab.tsx`, `frontend/components/templates/TemplateVariableReference.tsx`. Delete `frontend/components/clauses/clause-form-dialog.tsx` (replaced by clause-editor-sheet in 215B). Delete `frontend/components/clauses/clause-preview-panel.tsx` (replaced by inline content in clauses-content.tsx). |
| 217.14 | Verify clean build | 217B | | Run `./mvnw clean verify -q` (backend). Run `pnpm build && pnpm test` (frontend). All tests must pass. No compilation errors from deleted code. |

### Key Files

**Slice 217A -- Modify:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplateCloneResetTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentAuditNotificationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/ClauseTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/ClauseServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/ClauseResolverTest.java`

**Slice 217A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/VisualRegressionTest.java`

**Slice 217B -- Delete:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/LenientStandardDialect.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/LenientOGNLEvaluator.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateSecurityValidator.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateSecurityException.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseAssembler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/clause/ClauseAssemblerTest.java`
- `backend/src/main/resources/template-packs/common/engagement-letter.html`
- `backend/src/main/resources/template-packs/common/project-summary.html`
- `backend/src/main/resources/template-packs/common/invoice-cover-letter.html`
- `frontend/components/templates/template-clauses-tab.tsx`
- `frontend/components/templates/TemplateVariableReference.tsx`
- `frontend/components/clauses/clause-form-dialog.tsx`
- `frontend/components/clauses/clause-preview-panel.tsx`

**Slice 217B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` (remove dead methods)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateValidationService.java` (remove SSTI validation)

### Architecture Decisions
- Visual regression tests compare structural HTML output, not pixel-level PDF comparison
- Thymeleaf Maven dependency stays if Phase 24 email templates use it
- Cleanup is the last epic to avoid breaking anything while other slices are in flight

---

## Summary

| Metric | Count |
|--------|-------|
| Epics | 9 (209-217) |
| Slices | 18 (209A-217B) |
| Backend slices | 10 |
| Frontend slices | 8 |
| New backend files | ~10 (TiptapRenderer, VariableMetadataRegistry, LegacyContentImporter, migration, JSON pack files, test utilities) |
| Modified backend files | ~15 (entities, controllers, services, seeders, existing tests) |
| Deleted backend files | ~8 (Thymeleaf classes, ClauseAssembler, HTML pack files, dead tests) |
| New frontend files | ~20 (editor components, extensions, node views, pickers, tests) |
| Modified frontend files | ~10 (template pages, clause pages, generation dialog, action files) |
| Deleted frontend files | ~4 (template-clauses-tab, TemplateVariableReference, clause-form-dialog, clause-preview-panel) |
| V48 migration | 1 (document_templates + clauses column type change) |

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` - Central rendering orchestration; must switch from Thymeleaf to TiptapRenderer
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` - Entity column type change from String/TEXT to Map/JSONB
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/Clause.java` - Entity column type change from String/TEXT to Map/JSONB
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/settings/templates/[id]/edit/page.tsx` - Template editor page to rewrite with DocumentEditor
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase31-document-system-redesign.md` - Full architecture reference for all slices