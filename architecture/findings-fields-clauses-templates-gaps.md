# Findings: Custom Fields, Clauses & Document Templates — Integration Gaps

**Date**: 2026-03-08
**Status**: For Review
**Audience**: Technical Architect

---

## 1. Executive Summary

The custom fields system (Phase 11/23), clause library (Phase 27/31), and document template engine (Phase 12/31) are each well-designed in isolation. However, the integration seams between these systems have significant gaps that prevent the end-to-end experience from being premium-grade. The most critical issue is that custom fields are technically available in the rendering context but practically unreachable from the template and clause editors — the plumbing is connected but there are no faucets.

---

## 2. Current Architecture

### Data Flow

```
FieldDefinition → JSONB on Entity → ContextBuilder → TiptapRenderer → PDF
                                          ↑
                               Clause (Tiptap JSON body)
                                          ↑
                            TemplateClause (join table, synced from doc JSON)
                                          ↑
                           DocumentTemplate (Tiptap JSON content)
```

### Component Summary

| Layer | Key Files | Status |
|-------|-----------|--------|
| Custom Fields | `FieldDefinition`, `FieldPackSeeder`, JSONB on `Project`/`Customer`/`Task` | Complete (Phase 11, 23) |
| Clauses | `Clause` (Tiptap JSON body), `TemplateClause`, `ClauseResolver`, `ClausePackSeeder` | Complete (Phase 27, 31) |
| Document Templates | `DocumentTemplate` (Tiptap JSON content), `TiptapRenderer`, `PdfRenderingService` | Complete (Phase 12, 31) |
| Variable System | `VariableExtension` (Tiptap inline node), `VariableMetadataRegistry` (static), `resolveVariable()` (dot-path) | Complete |
| Rendering | Backend `TiptapRenderer` + frontend `client-renderer.ts` (mirrored) | Complete |

### Relevant ADRs

- **ADR-052**: JSONB vs EAV for custom field storage (chose JSONB on entity tables)
- **ADR-053**: Field pack seeding strategy (per-tenant copies at provisioning)
- **ADR-093**: Template required fields (soft references by slug, JSONB array on DocumentTemplate)
- **ADR-104**: Clause rendering strategy (string concatenation before single Thymeleaf pass → now Tiptap tree walk)
- **ADR-106**: Template-clause placeholder strategy (single global `${clauses}` → now inline clauseBlock nodes)
- **ADR-123**: Template-clause association source of truth (document JSON primary, table synced on save)

---

## 3. Gaps

### GAP-1: Custom Fields Invisible in Variable Picker

**Severity**: CRITICAL

**Description**: The `VariableMetadataRegistry` is entirely static/hardcoded. It registers fixed variables (`project.name`, `customer.email`, `org.name`, etc.) but never queries the tenant's `FieldDefinition` records.

**Evidence**:
- `VariableMetadataRegistry.java` — constructor registers hardcoded groups, no repository injection
- `ProjectContextBuilder.java:64` puts `project.customFields` into the rendering context as a `Map<String, Object>`
- `resolveVariable("customer.customFields.tax_number", context)` in `TiptapRenderer.java:220-230` resolves correctly via dot-path traversal
- But the Variable Picker dialog (`VariablePicker.tsx`) only shows what the metadata endpoint returns — custom fields are absent

**Impact**: Custom fields are technically renderable but practically undiscoverable. Users cannot insert custom field references from the template or clause editor without manually typing the dot-path (e.g., `customer.customFields.tax_number`), which is undocumented.

**This is the single largest gap — it makes the fields→documents pipeline feel broken.**

---

### GAP-2: Clause Editor Has No Variable Picker

**Severity**: CRITICAL

**Description**: The clause editor uses `scope="clause"` in `DocumentEditor.tsx`, which includes `VariableExtension` — but the `EditorToolbar` conditionally hides the variable picker button when no `entityType` prop is provided (`EditorToolbar.tsx:186`). Since clauses are entity-type-agnostic, no `entityType` is passed.

**Evidence**:
- `DocumentEditor.tsx:37-38` — clause scope includes `VariableExtension` but not `ClauseBlockExtension` or `LoopTableExtension`
- `EditorToolbar.tsx:186` — `{entityType && ( <> ... variable picker ... </> )}` — hidden without entityType
- Clause authors must memorize variable key paths or copy them from elsewhere

**Impact**: Clause authoring is blind-variable-entry. The seeded standard clauses (`standard-clauses/pack.json`) use variables like `org.name` and `customer.name`, but a user editing or creating clauses has no picker to discover these.

---

### GAP-3: Variable Nodes Stripped from Clause Previews

**Severity**: MEDIUM

**Description**: `extractTextFromBody()` in `tiptap-utils.ts` walks the Tiptap JSON tree and extracts only `text` nodes. Variable nodes (`type: "variable"`) are silently skipped, producing garbled preview text.

**Evidence**:
- `tiptap-utils.ts:11-18` — only processes `child.text`, ignores variable nodes
- `ClauseBlockNodeView.tsx:32-33` — uses `extractTextFromBody` for inline preview
- `ClausePicker.tsx:110-112` — uses `extractTextFromBody` for picker right-panel preview

**Rendered preview**: "All invoices issued by  to  are payable within thirty (30) days..."
**Expected preview**: "All invoices issued by {org.name} to {customer.name} are payable within thirty (30) days..."

**Impact**: Users can't visually verify clause content when inserting or reviewing clauses in the template editor.

---

### GAP-4: No Value Formatting in Renderer

**Severity**: MEDIUM

**Description**: `resolveVariable()` (both `TiptapRenderer.java:220-230` and `client-renderer.ts:79-92`) performs raw `String.valueOf(current)` / `String(current)` with no type-aware formatting.

**Affected field types**:
- **Currency**: renders `50000.00` instead of `$50,000.00`
- **Date**: renders ISO timestamp `2026-03-08T14:30:00Z` instead of locale-formatted date
- **Phone**: renders as-stored, no formatting
- **Dropdown**: renders value code (`US`) not display label (`United States`)

**Evidence**: The `VariableMetadataRegistry` already carries type hints (`"currency"`, `"date"`, `"string"`) on each `VariableInfo`, but neither renderer consumes them.

**Impact**: Generated documents look unprofessional. Financial documents with raw number formatting undermine credibility.

---

### GAP-5: No Invoice Custom Fields

**Severity**: MEDIUM

**Description**: The field pack system ships `common-project.json`, `common-customer.json`, and `common-task.json` — but no `common-invoice.json`. Phase 23's architecture doc mentions extending invoices with custom fields, but implementation appears incomplete.

**Evidence**:
- `backend/src/main/resources/field-packs/` — three JSON files, none for invoices
- `VariableMetadataRegistry.java:119-167` — invoice group has no `invoice.customFields.*` entries
- `InvoiceContextBuilder` likely doesn't include custom fields in the context map (same pattern as `ProjectContextBuilder` but without the `customFields` line)

**Impact**: Invoices — the most document-heavy entity — can't use custom fields in templates.

---

### GAP-6: Clause Preview Doesn't Resolve Variables to Placeholders

**Severity**: MEDIUM

**Description**: Both the inline `ClauseBlockNodeView` and the `ClausePicker` preview panel show clause content via `extractTextFromBody()`. Variables are stripped (see GAP-3), and there is no rendering context available to show even placeholder representations.

**Impact**: Users choosing which clauses to insert have no idea what the clause will look like with data substituted.

---

### GAP-7: Project Naming Doesn't Leverage Custom Fields

**Severity**: LOW

**Description**: Projects have a freeform `name` string. The `common-project.json` field pack includes `reference_number`, but there's no auto-naming pattern system (e.g., `{reference_number} - {customer.name} - Tax Return 2026`).

**Evidence**:
- Project creation is a simple name input
- Project templates (Phase 16) copy the name literally with no variable substitution
- Generated document filenames use `templateSlug-projectName-date.pdf` — no custom field incorporation

**Impact**: Professional services firms need structured, consistent project naming. Without auto-naming, project lists become inconsistent.

---

### GAP-8: No Loop Table Support for Custom Fields

**Severity**: LOW

**Description**: The `LoopTableExtension` and `renderLoopTable()` support data sources like `members`, `tags`, `lines` — but custom field collections aren't exposed as loop sources.

**Impact**: If a project has multi-value custom fields, they can't be rendered as tables in documents.

---

### GAP-9: Stale Clause Titles in Template JSON

**Severity**: LOW

**Description**: When a `clauseBlock` node is inserted, the clause title is snapshotted in the Tiptap JSON `attrs.title`. If the clause title is later edited in the library, the template's clauseBlock node retains the old title. `TemplateClauseSync` (ADR-123) syncs the join table from document JSON but doesn't update embedded title attributes.

**Impact**: Template editor shows outdated clause titles after library edits.

---

## 4. UX Gaps

| ID | Gap | Description |
|----|-----|-------------|
| UX-1 | No inline missing-data indicators | `requiredContextFields` validation only runs at generation time. The template editor has no inline indicator showing which variables reference missing or empty data for a given entity. |
| UX-2 | No field pack → template pack linkage UX | Field packs and template packs are provisioned independently. There's no visible connection showing which field packs a template expects. |
| UX-3 | No "used in" indicator for fields | When editing a field definition, there's no indicator showing which templates or clauses reference it. |
| UX-4 | No conditional content | No `if/else` block node in Tiptap. Can't conditionally show sections based on field values (e.g., show VAT clause only if `customer.customFields.country == "ZA"`). |
| UX-5 | No template editor live preview with real data | The template editor has no mechanism to pick a sample entity and render a live preview with actual data substituted. |

---

## 5. Root Cause

The custom field system (Phase 11/23) and document system (Phase 12/27/31) were designed and built in separate phases with clean internal APIs but a missing integration layer. Specifically:

- **The `VariableMetadataRegistry` is static while everything else is dynamic.** Field definitions are tenant-scoped, dynamically created, and stored in the database. But the variable picker reads from a hardcoded Java class that was written once and never connected to the field definition system.
- **The clause editor was scoped too narrowly.** Clauses are entity-type-agnostic by design (reusable across PROJECT, CUSTOMER, INVOICE templates), which created an impedance mismatch with the variable picker that requires an entity type.
- **The text extraction utility was written for simple previews** and never extended to handle the custom node types (variable, clauseBlock) that make the Tiptap editor powerful.

---

## 6. Recommended Fix Priority

| Priority | Gap | Estimated Effort | Rationale |
|----------|-----|-----------------|-----------|
| **P0** | GAP-1: Dynamic variable metadata | 1 slice | Unblocks the entire fields→documents pipeline. Without this, custom fields are unusable in templates. |
| **P0** | GAP-2: Variable picker in clause editor | 0.5 slice | Makes clause authoring usable. Can be bundled with GAP-1. |
| **P1** | GAP-3: Fix `extractTextFromBody` for variables | Tiny (< 1 hour) | Clause previews become readable. |
| **P1** | GAP-4: Value formatting in renderer | 1 slice | Professional document output. Affects every generated document. |
| **P2** | GAP-5: Invoice custom fields | 1 slice | Completeness for the most document-heavy entity. |
| **P2** | GAP-7: Project auto-naming patterns | 1 slice | Consistency for professional services workflows. |
| **P2** | UX-5: Template editor live preview | 1 slice | Polish — lets template authors see real output while editing. |
| **P3** | UX-4: Conditional content | 2 slices | Power feature — if/else blocks in Tiptap for field-value predicates. |
| **P3** | GAP-8: Loop table custom field sources | 0.5 slice | Niche but needed for multi-value fields. |
| **P3** | GAP-9: Stale clause titles | 0.5 slice | Cosmetic but confusing. |

**Total estimated effort for P0+P1**: ~3 slices (1 phase or part of a phase)

---

## 7. Architectural Considerations for the Architect

1. **Dynamic metadata endpoint**: Should the backend `/api/templates/variables` endpoint query `FieldDefinitionRepository` per-request, or should it cache tenant field definitions with invalidation? Given the expected scale (hundreds of fields per tenant), per-request is fine — the query is indexed and the result is small.

2. **Clause editor entity type**: How should the clause variable picker work given clauses are entity-agnostic? Options:
   - (a) Show a union of all entity types' variables with group labels indicating scope
   - (b) Let the user toggle entity type in the clause editor toolbar
   - (c) Show only "universal" variables (`org.*`, `generatedAt`, etc.) plus a note about entity-specific variables

   Recommendation: option (a) — show all, grouped. Clauses that reference `invoice.total` simply won't resolve when used in a PROJECT template, which is already the behavior.

3. **Value formatting**: Should formatting metadata live on the `variable` Tiptap node attrs (e.g., `{ key: "budget.amount", format: "currency" }`) or be inferred from the `FieldDefinition.fieldType`? Node-level format attrs give template authors control (same field, different format in different places) but add complexity. Inferring from field type is simpler but rigid.

4. **Conditional content**: A proper `if` block node is a significant Tiptap extension. An interim approach is a `conditionalBlock` node that evaluates a simple predicate (`field == value`, `field != null`) and shows/hides its children during rendering. This keeps the editor simple while enabling the most common use case.

5. **ADR impact**: GAP-1 and GAP-2 don't require new ADRs — they're implementation completions of existing architecture. GAP-4 (formatting) and UX-4 (conditional content) likely warrant ADRs for the rendering model changes.

---

## Appendix: Key Source Files Referenced

| File | Relevance |
|------|-----------|
| `backend/.../template/VariableMetadataRegistry.java` | Static variable registry — GAP-1 root cause |
| `backend/.../template/TiptapRenderer.java` | Backend rendering — GAP-4 formatting target |
| `backend/.../template/ProjectContextBuilder.java` | Context assembly — shows customFields ARE included |
| `backend/.../clause/ClauseResolver.java` | Clause selection logic |
| `backend/.../clause/Clause.java` | Clause entity — Tiptap JSON body |
| `backend/.../fielddefinition/FieldDefinition.java` | Field definition entity with types, options, packs |
| `backend/src/main/resources/field-packs/*.json` | Seeded field packs (3 packs, no invoice pack) |
| `backend/src/main/resources/clause-packs/standard-clauses/pack.json` | Seeded clause pack (12 clauses) |
| `frontend/components/editor/DocumentEditor.tsx` | Editor with scope-based extension loading |
| `frontend/components/editor/EditorToolbar.tsx` | Toolbar — GAP-2 conditional picker visibility |
| `frontend/components/editor/VariablePicker.tsx` | Variable picker dialog — consumes metadata endpoint |
| `frontend/components/editor/ClausePicker.tsx` | Clause picker dialog — GAP-6 preview issue |
| `frontend/components/editor/extensions/variable.ts` | Tiptap variable node extension |
| `frontend/components/editor/node-views/VariableNodeView.tsx` | Variable pill rendering |
| `frontend/components/editor/node-views/ClauseBlockNodeView.tsx` | Clause block rendering — GAP-3 preview |
| `frontend/components/editor/client-renderer.ts` | Frontend HTML renderer — mirrors backend |
| `frontend/lib/tiptap-utils.ts` | Text extraction — GAP-3 root cause |
