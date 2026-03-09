# Phase 42 — Word Template Pipeline

Phase 42 adds a **Word document (.docx) template pipeline** to the DocTeams platform. Users can upload existing `.docx` files containing `{{variable}}` merge fields, the system auto-discovers those fields, and generates filled documents preserving all Word formatting, styles, headers/footers, images, and page layout. This is a second output adapter alongside the existing Tiptap/HTML/PDF pipeline from Phase 12. Both pipelines share the same `TemplateContextBuilder` data layer; the difference is how the resolved context is merged into the template and what format comes out.

**Architecture doc**: `architecture/phase42-word-template-pipeline.md`

**ADRs**: 164 (DOCX processing library — Apache POI), 165 (PDF conversion strategy — LibreOffice + docx4j fallback), 166 (template format coexistence — single entity with discriminator)

**Dependencies on prior phases**:
- Phase 12 (Document Templates): `DocumentTemplate`, `GeneratedDocument`, `TemplateContextBuilder`, `VariableMetadataRegistry`, `GeneratedDocumentService`, `StorageService`
- Phase 11 (Custom Fields): Custom field values in template context as `customFields.*`

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 321 | Entity Extension, Enums & Migration | Backend | — | M | 321A, 321B | **Done** (PRs #613, #614) |
| 322 | DocxMergeService — Field Discovery & Split-Run Engine | Backend | 321 | L | 322A, 322B | **Done** (PRs #615, #616) |
| 323 | DOCX Upload, Replace & Download Endpoints | Backend | 321, 322 | M | 323A, 323B | |
| 324 | DOCX Generation Pipeline & PDF Conversion | Backend | 322, 323 | M | 324A, 324B | |
| 325 | Frontend — Upload & Template Management | Frontend | 323 | M | 325A, 325B | |
| 326 | Frontend — Generation Dialog & Integration | Frontend | 324, 325 | M | 326A, 326B | |

---

## Dependency Graph

```
BACKEND TRACK
─────────────────────────────────────────────────────

[E321A TemplateFormat +
 OutputFormat enums,
 DocumentTemplate extension
 (5 fields), V65 migration]
        |
[E321B GeneratedDocument
 extension (2 fields),
 format-aware validation
 in service, list/detail
 DTO updates, format filter
 + integration tests]
        |
        +───────────────────────────────+
        |                               |
[E322A DocxMergeService                 |
 core: paragraph concat,               |
 split-run algorithm,                   |
 replaceAcrossRuns,                     |
 resolveVariable,                       |
 test .docx resources                   |
 + unit tests]                          |
        |                               |
[E322B discoverFields,                  |
 DocxFieldValidator,                    |
 headers/footers/tables,               |
 edge cases                             |
 + integration tests]                   |
        |                               |
[E323A POST upload endpoint,         [E323B GET fields,
 multipart validation,                GET download,
 S3 storage, field discovery           PUT replace endpoint,
 + audit event                         re-discovery, audit
 + integration tests]                  + integration tests]
        |                               |
        +───────────────────────────────+
        |
[E324A POST generate-docx
 endpoint, context builder
 integration, DocxMerge
 invocation, S3 upload,
 GeneratedDocument creation
 + audit + integration tests]
        |
[E324B PdfConversionService
 (LibreOffice headless +
 docx4j fallback + graceful
 degradation), output format
 handling, dual-download URLs
 + integration tests]

FRONTEND TRACK (after E323 backend APIs ready)
───────────────────────────────────────────────

[E325A TypeScript types,
 API client functions,
 Upload Word Template dialog
 (drag-drop, metadata fields,
 10MB validation)
 + tests]
        |
[E325B Template list format
 badge + filter, template
 detail DOCX variant (file
 info, discovered fields,
 replace file, download),
 variable reference panel
 + tests]
        |
[E326A GenerateDocumentDropdown
 DOCX support, DOCX generation
 dialog (output format selector,
 generate, download links),
 server action
 + tests]
        |
[E326B Generated documents list
 output format badge, dual
 download links, PDF warning,
 format indicator on entity
 pages
 + tests]
```

**Parallel opportunities**:
- E321A/B are sequential (enums/entity before service validation).
- E322A/B are sequential (core algorithm before discovery + edge cases).
- E323A/B can run in parallel after E322B (upload vs read/replace are independent endpoint groups).
- E324A/B are sequential (generation endpoint before PDF conversion).
- E325A/B are sequential (types/upload before list/detail).
- E326A/B are sequential (generation dialog before document list updates).
- E325A can start as soon as E323A is merged (upload API available).

---

## Implementation Order

### Stage 0: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 321 | 321A | `TemplateFormat` enum, `OutputFormat` enum, extend `DocumentTemplate` entity with 5 new fields (`format`, `docxS3Key`, `docxFileName`, `docxFileSize`, `discoveredFields`), V65 tenant migration (ALTER TABLE x2, CHECK constraints, index). ~6 new/modified files. Backend only. | **Done** (PR #613) |
| 0b | 321 | 321B | Extend `GeneratedDocument` entity with 2 fields (`outputFormat`, `docxS3Key`), format-aware validation in `DocumentTemplateService`, update list/detail DTOs to include format + DOCX fields, add `?format=` query param to list endpoint, update delete to clean up S3 `.docx`. ~6 modified files + ~8 tests. Backend only. | **Done** (PR #614) |

### Stage 1: DOCX Processing Engine

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 322 | 322A | `DocxMergeService` core: `mergeParagraph()` with run concatenation, `{{...}}` regex, right-to-left `replaceAcrossRuns()`, `resolveVariable()` dot-path navigation. Apache POI dependency. Test `.docx` resources (4 files). ~4 new files + ~10 unit tests. Backend only. | **Done** (PR #615) |
| 1b | 322 | 322B | `discoverFields()` method, `DocxFieldValidator` (validates against `VariableMetadataRegistry`), headers/footers/tables processing in merge, `mergeTable()` recursive, edge cases (empty fields, no-fields doc, single-run fields). ~3 new/modified files + ~10 tests. Backend only. | **Done** (PR #616) |

### Stage 2: Upload & CRUD Endpoints (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 323 | 323A | `POST /api/templates/docx/upload` endpoint (multipart), MIME type + size validation, S3 upload at `org/{tenantId}/templates/{templateId}/template.docx`, field discovery integration, `DOCX_TEMPLATE_UPLOADED` audit event. ~4 new/modified files + ~8 tests. Backend only. | **Done** (PR #617) |
| 2b (parallel) | 323 | 323B | `GET /api/templates/{id}/docx/fields` endpoint, `GET /api/templates/{id}/docx/download` endpoint (presigned URL redirect), `PUT /api/templates/{id}/docx/replace` endpoint (multipart, S3 overwrite, re-discovery), `DOCX_TEMPLATE_REPLACED` audit event. ~3 modified files + ~8 tests. Backend only. | |

### Stage 3: Generation Pipeline

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 324 | 324A | `POST /api/templates/{id}/generate-docx` endpoint, `TemplateContextBuilder` integration (reuse existing), `DocxMergeService.merge()` invocation, merged `.docx` S3 upload, `GeneratedDocument` creation, file naming (`{slug}-{entity}-{date}.docx`), `DOCX_DOCUMENT_GENERATED` audit event. ~4 new/modified files + ~8 tests. Backend only. | |
| 3b | 324 | 324B | `PdfConversionService`: LibreOffice headless via `ProcessBuilder` (30s timeout), docx4j fallback (optional dependency), graceful degradation (return DOCX-only + warning), `outputFormat` handling (DOCX/PDF/BOTH), dual presigned URL generation. Spring multipart config. ~4 new/modified files + ~6 tests. Backend only. | |

### Stage 4: Frontend — Upload & Management

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 325 | 325A | TypeScript types (`TemplateFormat`, `OutputFormat`, `DiscoveredField`, `DiscoveredFieldsResult`), API client functions (`uploadDocxTemplate`, `replaceDocxFile`, `getDocxFields`, `downloadDocxTemplate`), "Upload Word Template" dialog (drag-drop, name/description/category/entityType fields, 10MB validation, field discovery results display). ~6 new files + ~5 tests. Frontend only. | |
| 4b | 325 | 325B | Template list: format badge (Tiptap/Word), file name + size for DOCX, format filter dropdown. Template detail DOCX variant: file info panel, discovered fields table (valid/unknown badges), "Replace File" button + flow, "Download Template" button, variable reference panel. ~6 modified files + ~5 tests. Frontend only. | |

### Stage 5: Frontend — Generation & Polish

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 326 | 326A | `GenerateDocumentDropdown` shows DOCX templates with Word icon badge. DOCX generation dialog (no HTML preview): output format selector (Word/PDF/Both), generate button, success state with download links, PDF unavailability warning. Server action `generateDocxAction`. ~5 new/modified files + ~5 tests. Frontend only. | |
| 5b | 326 | 326B | Generated documents list: output format badge, dual download links (DOCX + PDF), format indicator on project/customer/invoice detail pages. Update existing `GeneratedDocumentsList` component for new fields. ~4 modified files + ~4 tests. Frontend only. | |

### Timeline

```
Stage 0: [321A] -> [321B]                                           (sequential)
Stage 1: [322A] -> [322B]                                           (sequential, after 321B)
Stage 2: [323A] // [323B]                                           (parallel, after 322B)
Stage 3: [324A] -> [324B]                                           (sequential, after 323A+B)
Stage 4: [325A] -> [325B]                                           (sequential, after 323A APIs exist)
Stage 5: [326A] -> [326B]                                           (sequential, after 324B + 325B)
```

**Critical path**: 321A -> 321B -> 322A -> 322B -> 323A -> 324A -> 324B -> 326A -> 326B (9 slices sequential).

**Fastest path with parallelism**: 12 slices total, 9 on critical path. Stage 2 (2 slices) runs fully parallel. Stage 4 can overlap with Stage 3.

---

## Epic 321: Entity Extension, Enums & Migration

**Goal**: Add the `TemplateFormat` and `OutputFormat` enums, extend `DocumentTemplate` and `GeneratedDocument` entities with DOCX-specific fields, write the V65 Flyway migration, and update existing list/detail responses to include the new format field. This provides the data foundation for all subsequent DOCX work.

**References**: Architecture doc Sections 11.2.1, 11.2.2, 11.2.3, 11.7.

**Dependencies**: None — extends existing entities in tenant schema.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **321A** | 321.1--321.5 | `TemplateFormat` enum (`TIPTAP`, `DOCX`), `OutputFormat` enum (`PDF`, `DOCX`), extend `DocumentTemplate` entity with `format`, `docxS3Key`, `docxFileName`, `docxFileSize`, `discoveredFields` (JSONB) fields. V65 tenant migration (2 ALTER TABLEs, CHECK constraints, index). ~6 new/modified files. Backend only. | **Done** (PR #613) |
| **321B** | 321.6--321.12 | Extend `GeneratedDocument` with `outputFormat` and `docxS3Key`. Format-aware validation in `DocumentTemplateService`. Update list/detail DTO responses to include `format`, DOCX fields. Add `?format=` query param filter. Update delete logic to clean S3 `.docx` file. ~6 modified files + 1 test file (~8 tests). Backend only. | **Done** (PR #614) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 321.1 | Create `TemplateFormat` enum | 321A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateFormat.java`. Values: `TIPTAP`, `DOCX`. Pattern: `backend/.../template/TemplateCategory.java` for enum conventions in the template package. |
| 321.2 | Create `OutputFormat` enum | 321A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/OutputFormat.java`. Values: `PDF`, `DOCX`. Same package as `TemplateFormat`. |
| 321.3 | Extend `DocumentTemplate` entity with DOCX fields | 321A | 321.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java`. Add fields: `@Enumerated(EnumType.STRING) @Column(name = "format", nullable = false) private TemplateFormat format = TemplateFormat.TIPTAP;`, `@Column(name = "docx_s3_key", length = 500) private String docxS3Key;`, `@Column(name = "docx_file_name") private String docxFileName;`, `@Column(name = "docx_file_size") private Long docxFileSize;`, `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "discovered_fields", columnDefinition = "jsonb") private List<Map<String, Object>> discoveredFields;`. Add getters/setters. |
| 321.4 | Create V65 tenant migration | 321A | | New file: `backend/src/main/resources/db/migration/tenant/V65__add_docx_template_support.sql`. DDL: (1) ALTER TABLE `document_templates` ADD COLUMN `format` VARCHAR(10) NOT NULL DEFAULT 'TIPTAP'. (2) ADD COLUMN `docx_s3_key` VARCHAR(500). (3) ADD COLUMN `docx_file_name` VARCHAR(255). (4) ADD COLUMN `docx_file_size` BIGINT. (5) ADD COLUMN `discovered_fields` JSONB. (6) ADD CONSTRAINT `chk_template_format` CHECK (format IN ('TIPTAP', 'DOCX')). (7) CREATE INDEX `idx_document_templates_format` ON document_templates (format). Pattern: `backend/.../db/migration/tenant/V64__add_project_naming_pattern.sql` for latest migration numbering. |
| 321.5 | Add GeneratedDocument DOCX columns to V65 migration | 321A | 321.4 | Part of V65 file: (8) ALTER TABLE `generated_documents` ADD COLUMN `output_format` VARCHAR(10) NOT NULL DEFAULT 'PDF'. (9) ADD COLUMN `docx_s3_key` VARCHAR(500). (10) ADD CONSTRAINT `chk_output_format` CHECK (output_format IN ('PDF', 'DOCX')). |
| 321.6 | Extend `GeneratedDocument` entity with DOCX fields | 321B | 321A | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java`. Add: `@Enumerated(EnumType.STRING) @Column(name = "output_format", nullable = false) private OutputFormat outputFormat = OutputFormat.PDF;`, `@Column(name = "docx_s3_key", length = 500) private String docxS3Key;`. Add getters/setters. |
| 321.7 | Add format-aware validation to `DocumentTemplateService` | 321B | 321.3 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java`. Add validation: if `format = TIPTAP` then `content` required and `docxS3Key` must be null; if `format = DOCX` then `docxS3Key` required (after upload) and `content` may be null. Add `validateFormatConsistency(DocumentTemplate)` private method. Enforce on create/update paths. |
| 321.8 | Update list DTO to include format and DOCX fields | 321B | 321.3 | Modify the template list response DTO (in `DocumentTemplateController` or `DocumentTemplateService` response mapping). Add `format`, `docxFileName`, `docxFileSize` fields. Existing Tiptap templates return `format: "TIPTAP"` with null DOCX fields. Pattern: existing response mapping in `DocumentTemplateController`. |
| 321.9 | Update detail DTO to include discoveredFields | 321B | 321.8 | Modify the template detail response mapping. Add `discoveredFields` to the detail response. For TIPTAP templates, `discoveredFields` is null. |
| 321.10 | Add `?format=` query parameter to list endpoint | 321B | 321.8 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` and `DocumentTemplateRepository.java`. Add `findByFormat(TemplateFormat format)` or `findByFormatAndActiveTrue(TemplateFormat format)` to repository. Add optional `@RequestParam(required = false) TemplateFormat format` to list endpoint. Service applies filter when present. |
| 321.11 | Update delete logic for DOCX S3 cleanup | 321B | 321.7 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java`. On template delete, if `format = DOCX` and `docxS3Key` is not null, call `storageService.delete(docxS3Key)` to clean up the S3 file. Pattern: existing delete logic in `DocumentTemplateService`. |
| 321.12 | Write integration tests for entity extension | 321B | 321.10 | New/modified test file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateFormatTest.java`. Tests (~8): (1) `createTemplate_defaultFormatIsTiptap`; (2) `createTemplate_docxFormat_setsFields`; (3) `listTemplates_filterByFormat_returnsFiltered`; (4) `listTemplates_noFilter_returnsBothFormats`; (5) `detailTemplate_docxFormat_includesDiscoveredFields`; (6) `validateFormat_tiptapWithDocxS3Key_throws`; (7) `validateFormat_docxWithoutS3Key_allowsPreUpload`; (8) `deleteTemplate_docxFormat_deletesS3File`. Use `@SpringBootTest @Import(TestcontainersConfiguration.class)`. |

### Key Files

**Slice 321A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateFormat.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/OutputFormat.java`
- `backend/src/main/resources/db/migration/tenant/V65__add_docx_template_support.sql`

**Slice 321A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` — add 5 DOCX fields

**Slice 321A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateCategory.java` — enum pattern
- `backend/src/main/resources/db/migration/tenant/V64__add_project_naming_pattern.sql` — latest migration

**Slice 321B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java` — add 2 fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` — format validation, delete cleanup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` — format filter param
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateRepository.java` — findByFormat

**Slice 321B — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateFormatTest.java`

**Slice 321B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` — generation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java` — variable validation reference

### Architecture Decisions

- **Single entity with format discriminator** (ADR-166): `DocumentTemplate` gains a `format` column rather than creating a separate `DocxTemplate` entity. Both formats share metadata (name, slug, category, entity type).
- **V65 migration**: Next available tenant migration number (V64 is `add_project_naming_pattern`). Safe for existing data — `format` defaults to `TIPTAP`, `output_format` defaults to `PDF`, all new columns nullable.
- **`format` is immutable**: A TIPTAP template cannot become DOCX. Users create a new template to switch formats.
- **JSONB for `discoveredFields`**: Stores the full field list as a JSON array, enabling frontend display without re-parsing the `.docx`.

---

## Epic 322: DocxMergeService — Field Discovery & Split-Run Engine

**Goal**: Implement the core DOCX processing engine using Apache POI. This includes the split-run merge algorithm (the hardest part of Phase 42), field discovery, variable resolution, and processing across document body, headers, footers, and tables.

**References**: Architecture doc Sections 11.6.1--11.6.6, 11.8.1.

**Dependencies**: Epic 321 (entity extension — needs `TemplateFormat` enum and `discoveredFields` type).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **322A** | 322.1--322.8 | Apache POI dependency. `DocxMergeService` core: `mergeParagraph()` with run concatenation + offset tracking, `{{...}}` regex matching, right-to-left `replaceAcrossRuns()`, `resolveVariable()` dot-path navigation, `merge()` for document body only. Test `.docx` resource files (4 files). ~4 new files + ~10 unit tests. Backend only. | **Done** (PR #615) |
| **322B** | 322.9--322.15 | `discoverFields()` method on `DocxMergeService`, `DocxFieldValidator` (validates against `VariableMetadataRegistry`), extend `merge()` for headers/footers/tables, `mergeTable()` with recursive nested table support, edge cases (empty fields, no-fields doc, single-run). Additional test `.docx` resources. ~3 new/modified files + ~10 tests. Backend only. | **Done** (PR #616) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 322.1 | Add Apache POI dependency to pom.xml | 322A | | Modify: `backend/pom.xml`. Add `<dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId><version>5.4.0</version></dependency>`. Check https://poi.apache.org/ for latest 5.x stable. |
| 322.2 | Create `DocxMergeService` with `mergeParagraph()` | 322A | 322.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocxMergeService.java`. `@Service`. Core method `mergeParagraph(XWPFParagraph, Map<String, Object>)`: (1) Build concatenated text from runs with offset tracking (`runStartOffsets` array). (2) Find all `{{...}}` matches via `Pattern.compile("\\{\\{([^}]+)\\}\\}")`. (3) Process matches right-to-left. (4) For each match, resolve variable and call `replaceAcrossRuns()`. Follow pseudocode in architecture doc Section 11.6.6. |
| 322.3 | Implement `replaceAcrossRuns()` | 322A | 322.2 | Add to `DocxMergeService`. `replaceAcrossRuns(List<XWPFRun>, int[], int matchStart, int matchEnd, String resolved)`: (1) Find first run containing `matchStart` and last run containing `matchEnd - 1`. (2) First run: replace from match offset to run end with resolved value. (3) Middle runs: set text to empty string. (4) Last run: remove from start to match end offset. Handle single-run case (first == last). Preserve `<w:rPr>` formatting on all runs. |
| 322.4 | Implement `resolveVariable()` | 322A | | Add to `DocxMergeService`. `resolveVariable(String path, Map<String, Object> context)`: dot-path navigation. Split path on `.`, walk nested maps. Missing keys at any level return empty string. Null values return empty string. Non-map intermediate values return empty string. Call `toString()` on final value. |
| 322.5 | Implement `merge()` for document body | 322A | 322.2, 322.4 | Add to `DocxMergeService`. `merge(InputStream templateStream, Map<String, Object> context) -> byte[]`: (1) Open `XWPFDocument`. (2) Process all body paragraphs via `mergeParagraph()`. (3) Write to `ByteArrayOutputStream`. (4) Return bytes. Body-only in this slice; headers/footers/tables added in 322B. |
| 322.6 | Create `FieldMatch` record | 322A | | Add inner record to `DocxMergeService`: `record FieldMatch(int start, int end, String path) {}`. Used by `mergeParagraph()` to track regex matches. |
| 322.7 | Create test `.docx` resource files | 322A | | New directory + files: `backend/src/test/resources/docx/`. Create programmatically via Apache POI in a test setup helper OR commit real `.docx` files. Minimum 4 files: `simple-merge.docx` (fields in single runs), `split-runs.docx` (fields across multiple runs), `multiple-fields.docx` (2+ fields in one paragraph), `no-fields.docx` (no merge fields). Use Apache POI `XWPFDocument` API to create these programmatically in a test helper class. |
| 322.8 | Write unit tests for merge engine core | 322A | 322.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxMergeServiceTest.java`. Tests (~10): (1) `merge_singleRunField_replacesCorrectly`; (2) `merge_splitRunField_replacesAcrossRuns`; (3) `merge_multipleFieldsInParagraph_replacesAll`; (4) `merge_missingVariable_replacesWithEmpty`; (5) `merge_nullValue_replacesWithEmpty`; (6) `merge_nestedDotPath_resolvesCorrectly`; (7) `merge_noFields_returnsUnchanged`; (8) `resolveVariable_deeplyNested_works`; (9) `resolveVariable_missingIntermediateKey_returnsEmpty`; (10) `merge_preservesFormatting_afterReplace`. Use `@SpringBootTest` OR plain unit test (no Spring context needed for pure POI logic). |
| 322.9 | Implement `discoverFields()` | 322B | 322A | Add to `DocxMergeService`. `discoverFields(InputStream docxStream) -> List<String>`: (1) Open `XWPFDocument`. (2) Walk all paragraphs (body, headers, footers, table cells). (3) For each paragraph, concatenate run text, find `{{...}}` patterns. (4) Extract field paths, deduplicate, sort alphabetically. (5) Return unique field path list. Reuses the same concatenation + regex logic from `mergeParagraph()` — extract to a shared `extractFieldPaths(XWPFParagraph)` method. |
| 322.10 | Create `DocxFieldValidator` | 322B | 322.9 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocxFieldValidator.java`. `@Service`. Constructor injects `VariableMetadataRegistry`. Method: `validateFields(List<String> fieldPaths, TemplateEntityType entityType) -> List<Map<String, Object>>`. For each field path, check if `VariableMetadataRegistry` has metadata for the entity type. Return list of `{path, status ("VALID"/"UNKNOWN"), label}` maps. `label` comes from registry metadata for VALID fields, null for UNKNOWN. |
| 322.11 | Extend `merge()` for headers and footers | 322B | 322.5 | Modify: `backend/.../template/DocxMergeService.java`. In `merge()`, after body paragraphs, iterate `doc.getHeaderList()` and `doc.getFooterList()`. For each header/footer, process all paragraphs and tables. |
| 322.12 | Implement `mergeTable()` with recursive nested table support | 322B | 322.11 | Add to `DocxMergeService`. `mergeTable(XWPFTable, Map<String, Object>)`: iterate rows -> cells -> paragraphs -> `mergeParagraph()`. For nested tables, recursively call `mergeTable()` on `cell.getTables()`. Also process tables in headers/footers. |
| 322.13 | Create additional test `.docx` resources | 322B | | Add to `backend/src/test/resources/docx/`: `headers-footers.docx` (fields in header/footer), `table-fields.docx` (fields in table cells), `mixed-formatting.docx` (bold/italic within field runs). Programmatically created via POI or committed as real files. |
| 322.14 | Write tests for field discovery | 322B | 322.10 | Add to `DocxMergeServiceTest.java` or new file `DocxFieldValidatorTest.java`. Tests (~5): (1) `discoverFields_simpleDoc_findsAllFields`; (2) `discoverFields_splitRuns_findsFields`; (3) `discoverFields_headersFooters_findsFields`; (4) `discoverFields_tableCells_findsFields`; (5) `discoverFields_noFields_returnsEmpty`. |
| 322.15 | Write tests for header/footer/table merge and edge cases | 322B | 322.12 | Add to `DocxMergeServiceTest.java`. Tests (~5): (1) `merge_headerField_replacesInHeader`; (2) `merge_footerField_replacesInFooter`; (3) `merge_tableField_replacesInCell`; (4) `merge_emptyField_ignored`; (5) `validateFields_mixedValidUnknown_returnsCorrectStatuses`. Integration test with `VariableMetadataRegistry` (needs `@SpringBootTest`). |

### Key Files

**Slice 322A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocxMergeService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxMergeServiceTest.java`
- `backend/src/test/resources/docx/simple-merge.docx` (+ 3 more test resources)

**Slice 322A — Modify:**
- `backend/pom.xml` — add Apache POI dependency

**Slice 322A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java` — variable paths reference
- Architecture doc Section 11.6 — algorithm pseudocode

**Slice 322B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocxFieldValidator.java`
- `backend/src/test/resources/docx/headers-footers.docx` (+ 2 more test resources)

**Slice 322B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocxMergeService.java` — add headers/footers/tables, discoverFields
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxMergeServiceTest.java` — additional tests

**Slice 322B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java` — validation integration
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateValidationService.java` — validation pattern reference

### Architecture Decisions

- **Apache POI (XWPF)** selected for DOCX processing (ADR-164). Provides direct run-level access needed for split-run handling.
- **Right-to-left processing** for multiple fields in one paragraph — avoids offset recalculation after each replacement.
- **Split-run algorithm**: concatenate all run text, find patterns in concatenated text, map offsets back to runs. First run gets resolved value, middle runs cleared, last run trimmed. ~200-300 lines of core logic.
- **Test `.docx` files**: Programmatically created via Apache POI to ensure predictable run boundaries. Real Word documents should also be tested manually.

---

## Epic 323: DOCX Upload, Replace & Download Endpoints

**Goal**: Add the REST endpoints for uploading `.docx` template files, replacing files, downloading template files, and viewing discovered fields. These endpoints integrate `DocxMergeService.discoverFields()` with S3 storage and field validation.

**References**: Architecture doc Sections 11.3.1, 11.3.3, 11.4.1, 11.8.4, 11.8.7, 11.9.

**Dependencies**: Epic 321 (entity extension), Epic 322 (DocxMergeService for field discovery).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **323A** | 323.1--323.7 | `POST /api/templates/docx/upload` endpoint on existing `DocumentTemplateController` (multipart), MIME type + size validation in service, S3 upload, `discoverFields()` + `validateFields()` integration, slug generation, `DOCX_TEMPLATE_UPLOADED` audit event. ~4 modified files + ~8 tests. Backend only. | **Done** (PR #617) |
| **323B** | 323.8--323.13 | `GET /api/templates/{id}/docx/fields` endpoint, `GET /api/templates/{id}/docx/download` (presigned URL redirect), `PUT /api/templates/{id}/docx/replace` endpoint (multipart, S3 overwrite, re-discovery), `DOCX_TEMPLATE_REPLACED` audit event. ~3 modified files + ~8 tests. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 323.1 | Add multipart config for DOCX upload | 323A | | Modify: `backend/src/main/resources/application.yml`. Add or update `spring.servlet.multipart.max-file-size: 10MB` and `spring.servlet.multipart.max-request-size: 15MB`. Check if existing config already sets these (Phase 12 may have). |
| 323.2 | Add DOCX upload method to `DocumentTemplateService` | 323A | 322B | Modify: `backend/.../template/DocumentTemplateService.java`. New method: `uploadDocxTemplate(MultipartFile file, String name, String description, String category, String entityType)`. Steps: (1) Validate MIME type = `application/vnd.openxmlformats-officedocument.wordprocessingml.document`. (2) Validate size <= 10MB. (3) Generate slug. (4) Check slug uniqueness. (5) Create `DocumentTemplate` with `format=DOCX`. (6) Upload to S3 at `org/{tenantId}/templates/{templateId}/template.docx`. (7) Call `docxMergeService.discoverFields()`. (8) Call `docxFieldValidator.validateFields()`. (9) Set `docxS3Key`, `docxFileName`, `docxFileSize`, `discoveredFields`. (10) Save and return. Constructor inject `DocxMergeService`, `DocxFieldValidator`. |
| 323.3 | Add `POST /api/templates/docx/upload` to controller | 323A | 323.2 | Modify: `backend/.../template/DocumentTemplateController.java`. Add endpoint: `@PostMapping(value = "/docx/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`. Accept `@RequestParam("file") MultipartFile file` plus `name`, `description`, `category`, `entityType` form params. Delegate to `documentTemplateService.uploadDocxTemplate()`. Return `ResponseEntity.status(HttpStatus.CREATED).body(response)`. `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")`. Pure delegation — no logic in controller. |
| 323.4 | Build S3 key pattern for template files | 323A | 323.2 | Add to `DocumentTemplateService`. S3 key format: `org/{tenantId}/templates/{templateId}/template.docx`. Resolve `tenantId` from `RequestScopes.TENANT_ID`. Use `StorageService.upload(key, bytes, contentType)`. |
| 323.5 | Add audit event for DOCX upload | 323A | 323.2 | In `DocumentTemplateService.uploadDocxTemplate()`, publish `DOCX_TEMPLATE_UPLOADED` audit event via `AuditEventBuilder`. Details: `{templateId, fileName, fileSize, fieldCount, unknownFieldCount}`. Pattern: existing audit events in `DocumentTemplateService` or `GeneratedDocumentService`. |
| 323.6 | Handle corrupt `.docx` file gracefully | 323A | 323.2 | In `DocumentTemplateService.uploadDocxTemplate()`, wrap `discoverFields()` call in try-catch for Apache POI exceptions (`InvalidFormatException`, `POIXMLException`). On parse failure, throw `InvalidStateException("Corrupt file", "The uploaded file could not be parsed as a valid .docx document")`. |
| 323.7 | Write integration tests for upload endpoint | 323A | 323.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxUploadEndpointTest.java`. Tests (~8): (1) `upload_validDocx_returns201WithFields`; (2) `upload_invalidMimeType_returns400`; (3) `upload_tooLargeFile_returns400`; (4) `upload_missingName_returns400`; (5) `upload_duplicateSlug_returns409`; (6) `upload_corruptDocx_returns400`; (7) `upload_asMember_returns403`; (8) `upload_validDocx_createsAuditEvent`. Use MockMvc with `MockMultipartFile`. |
| 323.8 | Add `GET /api/templates/{id}/docx/fields` endpoint | 323B | 321B | Modify: `backend/.../template/DocumentTemplateController.java`. Add endpoint: `@GetMapping("/{id}/docx/fields")`. Load template, verify `format=DOCX`, return `discoveredFields` from entity. Service method: `getDocxFields(UUID templateId)`. Throws `ResourceNotFoundException` if not found, `InvalidStateException` if not DOCX format. |
| 323.9 | Add `GET /api/templates/{id}/docx/download` endpoint | 323B | 321B | Modify: `backend/.../template/DocumentTemplateController.java`. Add endpoint: `@GetMapping("/{id}/docx/download")`. Load template, verify `format=DOCX` and `docxS3Key` exists. Generate presigned download URL via `StorageService`. Return `302 Found` redirect to presigned URL. Alternative: stream bytes directly. Prefer redirect to avoid backend memory pressure. |
| 323.10 | Add `PUT /api/templates/{id}/docx/replace` to service | 323B | 323.2 | Modify: `backend/.../template/DocumentTemplateService.java`. New method: `replaceDocxFile(UUID templateId, MultipartFile file)`. Steps: (1) Load template, verify `format=DOCX`. (2) Validate file (same rules as upload). (3) Upload to S3, overwriting existing key. (4) Re-run `discoverFields()` + `validateFields()`. (5) Update `docxFileName`, `docxFileSize`, `discoveredFields`, `updatedAt`. (6) Publish `DOCX_TEMPLATE_REPLACED` audit event with `{templateId, oldFileName, newFileName, fieldCount, unknownFieldCount}`. |
| 323.11 | Add `PUT /api/templates/{id}/docx/replace` to controller | 323B | 323.10 | Modify: `backend/.../template/DocumentTemplateController.java`. Add endpoint: `@PutMapping(value = "/{id}/docx/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`. Accept `@PathVariable UUID id`, `@RequestParam("file") MultipartFile file`. Delegate to service. `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")`. |
| 323.12 | Write integration tests for fields/download/replace | 323B | 323.11 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxEndpointsTest.java`. Tests (~8): (1) `getFields_docxTemplate_returnsFieldList`; (2) `getFields_tiptapTemplate_returns409`; (3) `getFields_notFound_returns404`; (4) `download_docxTemplate_returns302Redirect`; (5) `download_tiptapTemplate_returns409`; (6) `replace_validDocx_updatesFieldsAndFile`; (7) `replace_asMember_returns403`; (8) `replace_createsAuditEvent`. Use MockMvc. Requires a pre-created DOCX template (use upload in `@BeforeEach` or direct DB insert + S3 mock). |
| 323.13 | Ensure controller remains thin (no business logic) | 323B | 323.11 | Review all new endpoints in `DocumentTemplateController`. Each must be a one-liner delegation to service. No `if/else`, no validation, no S3 calls, no audit logging in controller. All logic in `DocumentTemplateService`. Per `backend/CLAUDE.md` controller discipline rules. |

### Key Files

**Slice 323A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` — uploadDocxTemplate, validation, S3, audit
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` — POST upload endpoint
- `backend/src/main/resources/application.yml` — multipart config (if not already set)

**Slice 323A — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxUploadEndpointTest.java`

**Slice 323A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` — existing create/update patterns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/` — StorageService API
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/` — AuditEventBuilder pattern

**Slice 323B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` — 3 new endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` — replaceDocxFile, getDocxFields

**Slice 323B — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxEndpointsTest.java`

**Slice 323B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentController.java` — presigned URL pattern

### Architecture Decisions

- **All DOCX endpoints on existing `DocumentTemplateController`**: One controller per resource (project convention). DOCX endpoints grouped within the class.
- **Presigned URL redirect for download**: Returns `302 Found` to avoid backend memory pressure with large files.
- **Validation in service, not controller**: Per `backend/CLAUDE.md` controller discipline. Controller is pure delegation.
- **S3 key pattern**: `org/{tenantId}/templates/{templateId}/template.docx` — cleanup is straightforward (delete prefix on template delete).

---

## Epic 324: DOCX Generation Pipeline & PDF Conversion

**Goal**: Implement the end-to-end generation flow: load DOCX template from S3, resolve context via existing builders, merge with `DocxMergeService`, upload merged document to S3, optionally convert to PDF, create `GeneratedDocument` record, and return download URLs.

**References**: Architecture doc Sections 11.3.2, 11.4.1 (generate-docx endpoint), 11.8.2, 11.8.5, 11.8.7, 11.8.8.

**Dependencies**: Epic 322 (DocxMergeService), Epic 323 (upload pipeline for template S3 storage).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **324A** | 324.1--324.7 | `POST /api/templates/{id}/generate-docx` endpoint, `TemplateContextBuilder` integration (reuse existing), `DocxMergeService.merge()` invocation, merged `.docx` S3 upload, `GeneratedDocument` creation with `outputFormat`, file naming (`{slug}-{entity}-{date}.docx`), presigned URL generation, `DOCX_DOCUMENT_GENERATED` audit event. ~4 modified files + ~8 tests. Backend only. | |
| **324B** | 324.8--324.14 | `PdfConversionService`: LibreOffice headless via `ProcessBuilder` (30s timeout, startup availability check), docx4j fallback (optional dependency check), graceful degradation (DOCX-only + warning). `outputFormat` handling for `DOCX`/`PDF`/`BOTH`. Dual presigned URLs. ~4 new/modified files + ~6 tests. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 324.1 | Add generate-docx method to `GeneratedDocumentService` | 324A | 322A, 321B | Modify: `backend/.../template/GeneratedDocumentService.java`. New method: `generateDocx(UUID templateId, UUID entityId, String outputFormat)`. Steps: (1) Load `DocumentTemplate`, verify `format=DOCX`. (2) Download template `.docx` from S3 via `StorageService.download(docxS3Key)`. (3) Determine `TemplateContextBuilder` from `primaryEntityType` (same dispatch as existing Tiptap generation). (4) Build context: `builder.buildContext(entityId, memberId)`. (5) Call `docxMergeService.merge(templateStream, context)`. (6) Generate file name via `buildFileName(template.getSlug(), entityName, date, "docx")`. (7) Upload merged bytes to S3 at `org/{tenantId}/generated/{fileName}`. (8) Create `GeneratedDocument` record. (9) Generate presigned download URL. (10) Return result. |
| 324.2 | Implement generated file naming | 324A | | Add to `GeneratedDocumentService` or utility. Pattern: `{template-slug}-{entity-name}-{date}.{ext}`. Entity name sanitised: lowercase, spaces to hyphens, special chars removed, truncated to 50 chars. Date format: `yyyy-MM-dd`. E.g., `engagement-letter-acme-corp-2026-03-08.docx`. |
| 324.3 | Add `POST /api/templates/{id}/generate-docx` to controller | 324A | 324.1 | Modify: `backend/.../template/DocumentTemplateController.java`. Add endpoint. Request body: `record GenerateDocxRequest(UUID entityId, String outputFormat) {}`. Delegate to `generatedDocumentService.generateDocx()`. Return `ResponseEntity.ok(result)`. `@PreAuthorize("isAuthenticated()")`. Pure delegation. |
| 324.4 | Create generation result DTO | 324A | | Add record: `GenerateDocxResult(UUID id, UUID templateId, String templateName, String outputFormat, String fileName, String downloadUrl, String pdfDownloadUrl, Long fileSize, Instant generatedAt, List<String> warnings)`. Use in controller response. Can be a nested record in the controller or in the template package. |
| 324.5 | Add `DOCX_DOCUMENT_GENERATED` audit event | 324A | 324.1 | In `GeneratedDocumentService.generateDocx()`, publish audit event via `AuditEventBuilder`. Details: `{templateId, entityType, entityId, outputFormat, fileName}`. Pattern: existing generation audit in `GeneratedDocumentService`. |
| 324.6 | Handle entity name resolution for file naming | 324A | 324.2 | In `generateDocx()`, resolve entity name from context map: `context.get("project")` -> `((Map) result).get("name")` or similar per entity type. If name not available, use entity ID as fallback. Use for file naming only. |
| 324.7 | Write integration tests for generation endpoint | 324A | 324.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxGenerationTest.java`. Tests (~8): (1) `generateDocx_validTemplate_returnsMergedDocument`; (2) `generateDocx_tiptapTemplate_returns409`; (3) `generateDocx_templateNotFound_returns404`; (4) `generateDocx_entityNotFound_returns404`; (5) `generateDocx_setsOutputFormatDocx`; (6) `generateDocx_createsGeneratedDocumentRecord`; (7) `generateDocx_createsAuditEvent`; (8) `generateDocx_fileNameFollowsPattern`. Requires pre-uploaded DOCX template in S3 (via `@BeforeEach` upload or mock). |
| 324.8 | Create `PdfConversionService` | 324B | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfConversionService.java`. `@Service`. Method: `convertToPdf(byte[] docxBytes) -> Optional<byte[]>`. Returns empty Optional if conversion unavailable. |
| 324.9 | Implement LibreOffice headless conversion | 324B | 324.8 | Add to `PdfConversionService`. `convertViaLibreOffice(byte[] docxBytes) -> Optional<byte[]>`: (1) Write `docxBytes` to temp file. (2) `ProcessBuilder` with `soffice --headless --convert-to pdf --outdir {tmpDir} {tmpFile}`. (3) Set 30-second timeout. (4) Read output PDF from `{tmpDir}/{filename}.pdf`. (5) Clean up temp files in `finally`. (6) Return PDF bytes. On failure (process timeout, non-zero exit, missing binary), return empty Optional and log warning. |
| 324.10 | Implement LibreOffice availability check | 324B | 324.9 | Add `@PostConstruct` method to `PdfConversionService`. Check if `soffice` binary exists via `ProcessBuilder("which", "soffice")` (macOS/Linux) or `ProcessBuilder("where", "soffice")` (Windows). Set `libreOfficeAvailable` boolean flag. Log info/warning at startup. |
| 324.11 | Add docx4j fallback (optional) | 324B | 324.8 | Add to `PdfConversionService`. `convertViaDocx4j(byte[] docxBytes) -> Optional<byte[]>`: Try to load `org.docx4j.Docx4J` class via reflection (optional dependency). If available, use `Docx4J.toPDF(WordprocessingMLPackage)`. On failure, return empty Optional. Add `docx4j-export-fo` as `<optional>true</optional>` in `pom.xml`. |
| 324.12 | Implement `convertToPdf()` cascade with graceful degradation | 324B | 324.9, 324.11 | In `PdfConversionService.convertToPdf()`: (1) Try LibreOffice if available. (2) If failed/unavailable, try docx4j. (3) If both fail, return empty Optional. Caller adds warning to result: "PDF conversion unavailable. DOCX output returned instead." |
| 324.13 | Integrate `PdfConversionService` into generation flow | 324B | 324.12, 324A | Modify: `backend/.../template/GeneratedDocumentService.java`. In `generateDocx()`: if `outputFormat` is `PDF` or `BOTH`, call `pdfConversionService.convertToPdf(mergedDocxBytes)`. If PDF bytes present, upload to S3 as `.pdf`. Set `pdfDownloadUrl` in result. If unavailable, set warning and return DOCX only. For `BOTH`, store both S3 keys on `GeneratedDocument`. |
| 324.14 | Write integration tests for PDF conversion | 324B | 324.13 | New file or extend `DocxGenerationTest.java`. Tests (~6): (1) `generateDocx_outputFormatDocx_noPdfGenerated`; (2) `generateDocx_outputFormatPdf_converterUnavailable_returnsDocxWithWarning`; (3) `generateDocx_outputFormatBoth_returnsBothUrls` (if converter available); (4) `pdfConversion_libreOfficeUnavailable_fallsToDocx4j`; (5) `pdfConversion_bothUnavailable_returnsEmpty`; (6) `generateDocx_outputFormatPdf_converterAvailable_returnsPdf` (mocked). Mock `PdfConversionService` for most tests. One test with real (optional) LibreOffice. |

### Key Files

**Slice 324A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` — generateDocx method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` — generate-docx endpoint

**Slice 324A — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxGenerationTest.java`

**Slice 324A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` — existing Tiptap generation flow
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilder.java` — context builder pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java` — context builder pattern

**Slice 324B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfConversionService.java`

**Slice 324B — Modify:**
- `backend/pom.xml` — docx4j optional dependency
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java` — PDF conversion integration

**Slice 324B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` — existing PDF generation pattern

### Architecture Decisions

- **LibreOffice headless as primary, docx4j fallback** (ADR-165). PDF conversion is best-effort and optional — never fails a generation request.
- **Graceful degradation**: Try LibreOffice -> try docx4j -> return DOCX only with warning.
- **ProcessBuilder with 30s timeout**: Prevents LibreOffice hangs from blocking the request thread.
- **docx4j as optional dependency**: Only loaded via reflection. Not required at runtime.
- **File naming convention**: `{slug}-{entity-name}-{date}.{ext}` — consistent with Tiptap-generated PDFs.

---

## Epic 325: Frontend — Upload & Template Management

**Goal**: Add the frontend UI for uploading `.docx` templates, viewing field discovery results, managing DOCX templates in the list and detail pages, and replacing/downloading template files.

**References**: Architecture doc Sections 11.10 (Slice D).

**Dependencies**: Epic 323 (backend upload/replace/download/fields APIs).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **325A** | 325.1--325.7 | TypeScript types (`TemplateFormat`, `OutputFormat`, `DiscoveredField`, `DiscoveredFieldsResult`), API client functions for DOCX endpoints, "Upload Word Template" dialog with drag-drop zone, name/description/category/entityType form fields, 10MB client-side validation, field discovery results display after upload. Server action `uploadDocxTemplateAction`. ~6 new files + ~5 tests. Frontend only. | |
| **325B** | 325.8--325.14 | Template list: format badge (Tiptap blue/Word green), file name + size for DOCX rows, format filter dropdown (All/Tiptap/Word). Template detail DOCX variant: file info panel, discovered fields table (valid/unknown badges + counts), "Replace File" button + dialog, "Download Template" button. Variable reference panel. ~6 modified files + ~5 tests. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 325.1 | Add TypeScript types for DOCX templates | 325A | | Modify: `frontend/app/(app)/org/[slug]/settings/templates/actions.ts` or create `frontend/lib/template-types.ts`. Add types: `type TemplateFormat = "TIPTAP" \| "DOCX"`, `type OutputFormat = "PDF" \| "DOCX"`, `interface DiscoveredField { path: string; status: "VALID" \| "UNKNOWN"; label: string \| null }`, `interface DiscoveredFieldsResult { fields: DiscoveredField[]; validCount: number; unknownCount: number }`. Extend existing template interfaces with `format`, `docxFileName`, `docxFileSize`, `discoveredFields` fields. |
| 325.2 | Add API client functions for DOCX endpoints | 325A | 325.1 | Modify: `frontend/app/(app)/org/[slug]/settings/templates/actions.ts`. Add server actions: `uploadDocxTemplateAction(formData: FormData)` (POST multipart to `/api/templates/docx/upload`), `getDocxFieldsAction(templateId: string)` (GET `/api/templates/{id}/docx/fields`), `downloadDocxTemplateAction(templateId: string)` (GET `/api/templates/{id}/docx/download`), `replaceDocxFileAction(templateId: string, formData: FormData)` (PUT multipart). Use `apiClient` from `lib/api.ts`. For file uploads, use `fetch` with `FormData` (not JSON). |
| 325.3 | Create "Upload Word Template" dialog component | 325A | 325.2 | New file: `frontend/app/(app)/org/[slug]/settings/templates/UploadDocxDialog.tsx`. `"use client"`. Dialog with: (1) Drag-drop zone for `.docx` file (accept `.docx` only). (2) File name display + size. (3) Client-side validation: 10MB max, `.docx` extension. (4) Name, description, category dropdown, entity type dropdown. (5) Upload button (calls `uploadDocxTemplateAction`). (6) Loading state during upload. (7) Success state: show field discovery results. Pattern: existing `CreateTemplateForm.tsx` for form layout. Use `Dialog` from `@/components/ui/dialog`. |
| 325.4 | Create field discovery results component | 325A | 325.1 | New file: `frontend/app/(app)/org/[slug]/settings/templates/FieldDiscoveryResults.tsx`. Displays: list of discovered fields with path + badge (green "Valid" / amber "Unknown"), summary counts ("3 valid, 1 unknown"), warning text if unknown fields present ("Unknown fields will render as empty text"). Reusable — shown in upload dialog (325.3) and template detail (325.8). |
| 325.5 | Add "Upload Word Template" button to template list page | 325A | 325.3 | Modify: `frontend/app/(app)/org/[slug]/settings/templates/page.tsx` or `templates-content.tsx`. Add button alongside existing "Create Template" button. Button opens `UploadDocxDialog`. Use `FileUp` or `FileText` icon from lucide-react. |
| 325.6 | Handle upload success with redirect to template detail | 325A | 325.5 | In `UploadDocxDialog`, on successful upload, show field discovery results with a "View Template" button that navigates to `settings/templates/{id}`. Use `router.push()`. Refresh template list via `router.refresh()` or `revalidatePath`. |
| 325.7 | Write tests for upload dialog | 325A | 325.3 | New file: `frontend/__tests__/UploadDocxDialog.test.tsx`. Tests (~5): (1) `renders_uploadZone_and_formFields`; (2) `validates_fileTooLarge_showsError`; (3) `validates_nonDocxFile_showsError`; (4) `submits_formData_onUpload`; (5) `shows_fieldDiscoveryResults_afterSuccess`. Mock server actions. Use `@testing-library/react`. |
| 325.8 | Add format badge to template list | 325B | 325.1 | Modify: `frontend/app/(app)/org/[slug]/settings/templates/TemplateList.tsx`. For each template, show format badge next to the name: "Tiptap" (blue/slate badge) or "Word" (green badge, `FileText` icon). For DOCX templates, show `docxFileName` and human-readable file size below the name. Use existing `Badge` component variants. |
| 325.9 | Add format filter dropdown to template list | 325B | 325.8 | Modify: `frontend/app/(app)/org/[slug]/settings/templates/page.tsx` or `templates-content.tsx`. Add a filter dropdown: "All Formats" / "Tiptap" / "Word". Pass `?format=` query param to the list API call. Use `Select` component from Shadcn. |
| 325.10 | Create DOCX template detail variant | 325B | 325.4 | Modify: `frontend/app/(app)/org/[slug]/settings/templates/[id]/page.tsx`. When template `format === "DOCX"`, render DOCX-specific detail layout instead of Tiptap editor. Show: file info panel (file name, file size, upload date), discovered fields via `FieldDiscoveryResults` component. Hide Tiptap editor, CSS panel. Keep shared metadata (name, description, category, entity type, active status). |
| 325.11 | Add "Replace File" flow to DOCX detail | 325B | 325.10 | Add to DOCX template detail page. "Replace File" button opens a dialog similar to upload (drag-drop zone + file validation). Calls `replaceDocxFileAction`. On success, refreshes field discovery results. Show confirmation before replace: "This will update the template file. Existing generated documents are not affected." |
| 325.12 | Add "Download Template" button to DOCX detail | 325B | 325.10 | Add to DOCX template detail page. "Download Template" button calls `downloadDocxTemplateAction`. Opens the presigned URL in a new tab or triggers browser download. Use `window.open(url, '_blank')` for redirect-based download. |
| 325.13 | Add variable reference panel | 325B | 325.10 | Add to DOCX template detail page. Show available variables for the template's entity type (from `GET /api/templates/variables?entityType={type}` — existing endpoint). Display as a copyable list of `{{variable.path}}` strings. Include "Copy" button per variable. Help users know which merge fields are available. Pattern: similar panel may already exist in Tiptap editor — reuse or extract shared component. |
| 325.14 | Write tests for template list and detail DOCX variant | 325B | 325.10 | New file: `frontend/__tests__/DocxTemplateManagement.test.tsx`. Tests (~5): (1) `templateList_showsFormatBadge_forDocxAndTiptap`; (2) `templateList_filterByFormat_filtersCorrectly`; (3) `templateDetail_docxFormat_showsFileInfo`; (4) `templateDetail_docxFormat_showsDiscoveredFields`; (5) `templateDetail_docxFormat_hidesEditor`. Mock API responses. |

### Key Files

**Slice 325A — Create:**
- `frontend/app/(app)/org/[slug]/settings/templates/UploadDocxDialog.tsx`
- `frontend/app/(app)/org/[slug]/settings/templates/FieldDiscoveryResults.tsx`
- `frontend/__tests__/UploadDocxDialog.test.tsx`

**Slice 325A — Modify:**
- `frontend/app/(app)/org/[slug]/settings/templates/actions.ts` — server actions + types
- `frontend/app/(app)/org/[slug]/settings/templates/page.tsx` — upload button
- `frontend/app/(app)/org/[slug]/settings/templates/templates-content.tsx` — upload button (if here)

**Slice 325A — Read for context:**
- `frontend/app/(app)/org/[slug]/settings/templates/CreateTemplateForm.tsx` — form pattern
- `frontend/lib/api.ts` — API client pattern

**Slice 325B — Modify:**
- `frontend/app/(app)/org/[slug]/settings/templates/TemplateList.tsx` — format badge + filter
- `frontend/app/(app)/org/[slug]/settings/templates/[id]/page.tsx` — DOCX detail variant
- `frontend/app/(app)/org/[slug]/settings/templates/page.tsx` — format filter

**Slice 325B — Create:**
- `frontend/__tests__/DocxTemplateManagement.test.tsx`

**Slice 325B — Read for context:**
- `frontend/app/(app)/org/[slug]/settings/templates/TemplateEditor.tsx` — Tiptap editor (to understand what to hide for DOCX)
- `frontend/components/ui/badge.tsx` — badge variants
- `frontend/components/ui/dialog.tsx` — dialog pattern

### Architecture Decisions

- **Format badge colors**: Tiptap = slate/blue (existing feel), Word = green (differentiator). Uses existing `Badge` component.
- **Upload via FormData**: Multipart uploads use `fetch` with `FormData`, not the standard JSON `apiClient`. Server action wraps this.
- **DOCX detail hides Tiptap editor**: Clean separation — DOCX templates show file info + fields, not the rich text editor.
- **Variable reference panel reuse**: May extract from existing Tiptap editor if a similar panel exists there. Otherwise, new component.

---

## Epic 326: Frontend — Generation Dialog & Integration

**Goal**: Enable DOCX document generation from the frontend. Add DOCX templates to the generation dropdown, implement a DOCX-specific generation dialog (no HTML preview), handle output format selection, and update the generated documents list to show format badges and dual download links.

**References**: Architecture doc Sections 11.10 (Slice E).

**Dependencies**: Epic 324 (backend generation API), Epic 325 (frontend types and upload UI).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **326A** | 326.1--326.6 | `GenerateDocumentDropdown` shows DOCX templates with Word icon. DOCX generation dialog: output format selector (Word .docx / PDF / Both), generate button, loading state, success with download links, PDF unavailability warning. Server action `generateDocxAction`. ~5 new/modified files + ~5 tests. Frontend only. | |
| **326B** | 326.7--326.11 | Generated documents list: output format badge, dual download links (DOCX + PDF when both available), format indicator on project/customer/invoice detail generate dropdowns. ~4 modified files + ~4 tests. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 326.1 | Add `generateDocxAction` server action | 326A | 325.1 | Modify: `frontend/app/(app)/org/[slug]/settings/templates/actions.ts` (or `documents/actions.ts` depending on existing generation action location). Add: `generateDocxAction(templateId: string, entityId: string, outputFormat: string)` — POST to `/api/templates/{id}/generate-docx` with `{entityId, outputFormat}`. Returns `GenerateDocxResult` type. |
| 326.2 | Update `GenerateDocumentDropdown` for DOCX templates | 326A | 325.1 | Modify: `frontend/app/(app)/org/[slug]/settings/templates/GenerateDocumentDropdown.tsx`. Templates now have a `format` field. Show format icon next to each template: `FileText` (lucide) for DOCX, existing icon for Tiptap. When a DOCX template is selected, open `GenerateDocxDialog` instead of existing `GenerateDocumentDialog`. |
| 326.3 | Create `GenerateDocxDialog` component | 326A | 326.1 | New file: `frontend/app/(app)/org/[slug]/settings/templates/GenerateDocxDialog.tsx`. `"use client"`. Dialog flow: (1) Template name + entity info shown. (2) Output format selector: radio group with "Word Document (.docx)", "PDF", "Both (Word + PDF)". Default to "Word Document". (3) "Generate" button. (4) Loading state with spinner. (5) Success state: download link(s). If PDF unavailable, show warning: "PDF conversion is not available. Your document has been generated as .docx". No HTML preview step (unlike Tiptap). Pattern: existing `GenerateDocumentDialog.tsx` for dialog structure. |
| 326.4 | Implement download links in generation success state | 326A | 326.3 | In `GenerateDocxDialog` success state: show download button(s). If `downloadUrl` present, show "Download .docx" button. If `pdfDownloadUrl` present, show "Download PDF" button. Both open presigned URLs in new tab. If `warnings` array non-empty, show amber alert with warning messages. |
| 326.5 | Handle output format "Both" correctly | 326A | 326.3 | In `GenerateDocxDialog`, when "Both" selected: pass `outputFormat: "BOTH"` to action. Response may contain `downloadUrl` (DOCX) and `pdfDownloadUrl` (PDF). Show both download buttons. If PDF conversion failed, show DOCX download only with warning. |
| 326.6 | Write tests for generation dialog | 326A | 326.3 | New file: `frontend/__tests__/GenerateDocxDialog.test.tsx`. Tests (~5): (1) `renders_outputFormatSelector`; (2) `submits_withDocxFormat_callsAction`; (3) `showsDownloadLinks_onSuccess`; (4) `showsWarning_whenPdfUnavailable`; (5) `showsBothDownloads_whenBothFormat`. Mock server actions and API responses. |
| 326.7 | Add output format badge to generated documents list | 326B | 325.1 | Modify: `frontend/app/(app)/org/[slug]/settings/templates/GeneratedDocumentsList.tsx`. Extend generated document type with `outputFormat`, `docxS3Key`, `pdfDownloadUrl`. Show format badge: "PDF" (existing) or "DOCX" (new). |
| 326.8 | Add dual download links to generated documents list | 326B | 326.7 | Modify: `GeneratedDocumentsList.tsx`. For DOCX-generated documents, show download icon/button. If both DOCX and PDF URLs available (outputFormat was BOTH), show two download buttons: ".docx" and ".pdf". Pattern: existing download link in the list. |
| 326.9 | Update entity page generate dropdowns | 326B | 326.2 | Verify that `GenerateDocumentDropdown` on project detail, customer detail, and invoice detail pages correctly shows DOCX templates with format badge. These pages already use `GenerateDocumentDropdown` — the changes from 326.2 should propagate. If the dropdown is duplicated in multiple places, ensure consistency. Modify if needed: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`, `customers/[id]/page.tsx`, `invoices/[id]/page.tsx`. |
| 326.10 | Add format indicator to generated documents on entity pages | 326B | 326.7 | If entity detail pages (project, customer, invoice) show a "Generated Documents" section/tab, ensure the format badge appears there too. The `GeneratedDocumentsList` component should be shared, so changes from 326.7 propagate. Verify and adjust if layout differs per page. |
| 326.11 | Write tests for generated documents list updates | 326B | 326.8 | New file: `frontend/__tests__/GeneratedDocumentsListDocx.test.tsx`. Tests (~4): (1) `showsFormatBadge_forDocxDocument`; (2) `showsFormatBadge_forPdfDocument`; (3) `showsDualDownloads_whenBothAvailable`; (4) `showsSingleDownload_whenDocxOnly`. Mock API responses with both format types. |

### Key Files

**Slice 326A — Create:**
- `frontend/app/(app)/org/[slug]/settings/templates/GenerateDocxDialog.tsx`
- `frontend/__tests__/GenerateDocxDialog.test.tsx`

**Slice 326A — Modify:**
- `frontend/app/(app)/org/[slug]/settings/templates/actions.ts` — generateDocxAction
- `frontend/app/(app)/org/[slug]/settings/templates/GenerateDocumentDropdown.tsx` — DOCX format routing

**Slice 326A — Read for context:**
- `frontend/app/(app)/org/[slug]/settings/templates/GenerateDocumentDialog.tsx` — existing generation dialog pattern
- `frontend/components/ui/dialog.tsx` — dialog component

**Slice 326B — Modify:**
- `frontend/app/(app)/org/[slug]/settings/templates/GeneratedDocumentsList.tsx` — format badge, dual downloads
- `frontend/app/(app)/org/[slug]/settings/templates/GenerateDocumentDropdown.tsx` — verify entity page propagation

**Slice 326B — Create:**
- `frontend/__tests__/GeneratedDocumentsListDocx.test.tsx`

**Slice 326B — Read for context:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — entity page generate dropdown usage
- `frontend/app/(app)/org/[slug]/customers/` — customer page structure

### Architecture Decisions

- **Separate dialog for DOCX generation**: No HTML preview step — DOCX users trust their Word layout. Simpler flow: select format -> generate -> download.
- **Output format as radio group**: Clear UX for Word/PDF/Both selection. Defaults to Word (.docx) since that is the natural output for a Word template.
- **PDF warning pattern**: Non-blocking amber alert when PDF conversion unavailable. User still gets their DOCX. Follows graceful degradation principle from ADR-165.
- **Shared `GeneratedDocumentsList` component**: Changes propagate to all entity pages automatically.

---

