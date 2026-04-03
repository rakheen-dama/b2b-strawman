# Fields-Clauses-Templates Integration — Handover Doc

## Branch & Worktree

- **Branch:** `feat/fields-clauses-templates-integration`
- **Worktree:** `.worktrees/fields-clauses-templates-integration`
- **Base SHA:** `dddbc40f` (main)
- **Plan file:** `documentation/plans/2026-03-08-fields-clauses-templates-integration.md`
- **Findings doc:** `architecture/findings-fields-clauses-templates-gaps.md`

## Completed Tasks (7 of 9 implementable)

| Task | Gap | Commits | Status |
|------|-----|---------|--------|
| Task 1: Dynamic Variable Metadata Registry | GAP-1 (P0) | `fc037120`, `5f1a4c6f` | Done + reviewed |
| Task 2: Variable Picker in Clause Editor | GAP-2 (P0) | `71ff57fc`, `11a504c6` | Done + reviewed |
| Task 3: Fix extractTextFromBody | GAP-3 (P1) | `65f78be2` | Done + reviewed |
| Task 4: Value Formatting in Renderers | GAP-4 (P1) | `7ecb7518`, `3763d224` | Done + reviewed |
| Task 5: Invoice Custom Fields Pack | GAP-5 (P2) | `6d881fd9` | Done + reviewed |
| Task 7: Stale Clause Titles | GAP-9 (P3) | `6a70c605`, `d47e1254` | Done + reviewed |
| Task 8: Project Auto-Naming Patterns | GAP-7 (P2) | `749e6727` | Done, review in progress |

**All backend + frontend tests pass.** 11 commits on the branch.

## Remaining Tasks

### Task 10: Inline Missing-Data Indicators (UX-1 — P2)
- Add `MissingVariablesContext` React context
- Update `VariableNodeView` to show amber warning styling for missing variables
- See plan Task 10 for details

### Task 11: Template Editor Live Preview (UX-5 — P2)
- Entity picker + `renderTiptapToHtml` with real data
- `TemplatePreviewPanel` component with iframe preview
- See plan Task 11 for details

### Tasks 12-14 (P3 — defer)
- UX-2: Field pack → template pack linkage
- UX-3: "Used in" indicator for fields
- UX-4: Conditional content blocks (needs own ADR, 2 slices)

## Review Status for Task 8

Two background review agents were dispatched:
- Spec compliance reviewer — checking all 7 requirements
- Code quality reviewer — checking ProjectNameResolver, API changes, frontend

If reviews pass: mark Task 8 complete, proceed to Task 10.
If reviews have issues: fix issues via resume agent, re-review, then proceed.

## Workflow

Using **subagent-driven development**:
1. Dispatch implementer subagent per task
2. Spec compliance review
3. Code quality review (use `superpowers:code-reviewer` agent)
4. Fix review issues → re-review → mark complete
5. After all tasks: final review, then PR via `superpowers:finishing-a-development-branch`

## Key Architecture Context

### Variable Metadata Flow (after Task 1)
```
FieldDefinition (DB, per-tenant) → VariableMetadataRegistry.getVariables()
  → appends dynamic custom field groups to static groups
  → GET /api/templates/variables?entityType=PROJECT
  → VariablePicker.tsx (fetches + renders groups)
```

### Rendering Flow (after Task 4)
```
PdfRenderingService.buildFormatHints() → Map<String, String> (variable key → type hint)
  → TiptapRenderer.render(doc, context, clauses, css, formatHints)
  → resolveVariable() → VariableFormatter.format(value, typeHint)
  → currency: $50,000.00 | date: 8 March 2026 | number: 1,234,567
```

### Clause Title Enrichment (after Task 7)
```
DocumentTemplateService.getById() → enrichClauseTitles()
  → extractClauseIds() (DFS) → clauseRepository.findAllById() (batch)
  → walkAndUpdateTitles() (structural copy, replaces attrs.title)
  → response.withContent(enrichedContent)
```

### Project Auto-Naming (after Task 8)
```
ProjectService.createProject() → orgSettingsRepository.findForCurrentTenant()
  → if projectNamingPattern set → ProjectNameResolver.resolve(pattern, name, customFields, customerName)
  → replaces {name}, {customer.name}, {custom_field_slug} tokens
```

### Clause Editor (after Task 2)
- `EditorToolbar` accepts `scope` prop ("template" | "clause")
- Clause scope: shows variable picker (all entity types merged), hides clause picker
- `fetchAllVariableMetadata()` merges variables from PROJECT + CUSTOMER + INVOICE

### Text Extraction (after Task 3)
- `extractTextFromBody()` now handles variable nodes → renders as `{key}` placeholders
- Fixes both ClauseBlockNodeView and ClausePicker previews

## File Locations (Key Modified Files)

**Backend:**
- `backend/.../template/VariableMetadataRegistry.java` — dynamic custom field groups
- `backend/.../template/VariableFormatter.java` — NEW, type-aware formatting
- `backend/.../template/TiptapRenderer.java` — formatHints threading
- `backend/.../template/PdfRenderingService.java` — buildFormatHints()
- `backend/.../template/DocumentTemplateService.java` — clause title enrichment on GET
- `backend/.../template/DocumentTemplateController.java` — withContent() on TemplateDetailResponse
- `backend/.../project/ProjectNameResolver.java` — NEW, naming pattern resolution
- `backend/.../project/ProjectService.java` — auto-naming integration
- `backend/.../settings/OrgSettings.java` — projectNamingPattern field
- `backend/.../settings/OrgSettingsService.java` — settings API for naming pattern
- `backend/.../settings/OrgSettingsController.java` — SettingsResponse + UpdateSettingsRequest
- `backend/src/main/resources/field-packs/common-invoice.json` — NEW
- `backend/src/main/resources/db/migration/tenant/V64__add_project_naming_pattern.sql` — NEW

**Frontend:**
- `frontend/components/editor/actions.ts` — fetchAllVariableMetadata()
- `frontend/components/editor/VariablePicker.tsx` — optional entityType
- `frontend/components/editor/EditorToolbar.tsx` — scope prop
- `frontend/components/editor/DocumentEditor.tsx` — passes scope
- `frontend/components/editor/client-renderer.ts` — formatValue(), formatHints
- `frontend/lib/tiptap-utils.ts` — variable node handling in extractTextFromBody
- `frontend/app/(app)/org/[slug]/settings/project-naming/` — NEW, naming pattern settings
- `frontend/components/project-naming/project-naming-settings.tsx` — NEW

**Tests:**
- `backend/.../template/VariableMetadataEndpointTest.java` — 11 tests
- `backend/.../template/VariableFormatterTest.java` — 17 tests
- `backend/.../template/TiptapRendererTest.java` — 4 new format tests
- `backend/.../fielddefinition/FieldPackJsonValidationTest.java` — 6 tests
- `backend/.../template/ClauseTitleEnrichmentTest.java` — 4 tests (happy path + edge cases)
- `backend/.../project/ProjectNameResolverTest.java` — 10 tests
- `frontend/lib/__tests__/tiptap-utils.test.ts` — 6 tests
- `frontend/__tests__/components/editor/PreviewPanel.test.ts` — 14 format tests
