# Phase 42 — Word Template Pipeline (DOCX Upload, Merge & Generation)

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 41, the platform has:

- **Document template system (Phase 12 + Phase 31)**: Tiptap/ProseMirror WYSIWYG editor with JSONB storage. Templates contain visual variable chips (e.g., `{{customer.name}}`) and inline clause blocks. `TiptapRenderer` walks the JSON tree, resolves variables, and produces HTML. `PdfRenderingService` converts HTML → PDF via OpenHTMLToPDF. Templates are scoped to entity types: PROJECT, CUSTOMER, INVOICE.
- **Variable resolution infrastructure**: `VariableMetadataRegistry` defines available variables per entity type (project, customer, invoice, org). Three `TemplateContextBuilder` implementations (Project, Customer, Invoice) produce `Map<String, Object>` contexts from entity data. `TemplateContextHelper` provides shared org context (name, currency, branding, logo).
- **Document generation flow**: User selects template + entity → backend resolves context via `TemplateContextBuilder` → `TiptapRenderer` resolves variables in JSONB → HTML output → OpenHTMLToPDF → PDF bytes → S3 upload → `GeneratedDocument` record.
- **Custom fields (Phase 11)**: Tenant-defined fields on projects, customers, and tasks. Custom field values are included in template context builders as `customFields.*` variables.
- **Clause library (Phase 27)**: Reusable content blocks that can be inserted into Tiptap templates. Clauses are stored as Tiptap JSONB and resolved during rendering.
- **S3 storage**: All generated documents stored in S3. Presigned URLs for download.

**Gap**: The Tiptap editor is powerful for creating structured documents from scratch, but many firms have existing Word templates they've refined over years — engagement letters, court documents, contracts, compliance forms. These firms don't want to recreate their templates in a WYSIWYG editor. They want to upload their `.docx` files and have the system fill in the data. This is particularly true for law firms (court-standard document formats), accounting firms (audit report templates), and any firm with brand-controlled document layouts created by their marketing team in Word.

## Objective

Build a **Word template upload and merge pipeline** that allows firms to:

1. **Upload `.docx` templates** containing merge fields (e.g., `{{customer.name}}`, `{{project.name}}`) — the same variable syntax used by the Tiptap editor.
2. **Auto-discover merge fields** from the uploaded document — the system parses the `.docx` XML and extracts all `{{variable}}` placeholders.
3. **Validate merge fields** against the `VariableMetadataRegistry` — flag any unrecognised variables so the user can fix them before generation.
4. **Generate filled `.docx` documents** — merge entity data into the template, preserving all Word formatting, styles, headers/footers, images, and page layout exactly as designed.
5. **Optionally convert to PDF** — offer PDF as a secondary output format (via LibreOffice headless or docx4j's PDF export), but `.docx` is the primary output.
6. **Store and track generated documents** alongside Tiptap-generated documents — reuse the existing `GeneratedDocument` entity and S3 storage.

The firm's experience: upload once, generate many times, get back a perfectly formatted Word document with all their data filled in.

## Constraints & Assumptions

- **Reuse existing variable system.** Word templates use the same `{{entity.field}}` syntax and resolve against the same `TemplateContextBuilder` output. No new variable format.
- **Reuse existing context builders.** `ProjectContextBuilder`, `CustomerContextBuilder`, `InvoiceContextBuilder`, and `TemplateContextHelper` are used unchanged. The Word pipeline is a new *output adapter*, not a new data pipeline.
- **No in-app Word editing.** Users design their templates in Microsoft Word (or Google Docs exported as `.docx`). The platform is a merge engine, not an editor replacement.
- **No real-time collaboration.** Upload → merge → download. The generated document is a snapshot.
- **Apache POI for `.docx` processing.** Apache POI (XWPF) is the mature, well-supported Java library for `.docx` manipulation. It handles paragraphs, tables, headers/footers, images, and styles. No need for docx4j unless POI proves insufficient for a specific feature.
- **PDF conversion is optional and best-effort.** Use LibreOffice headless (`soffice --convert-to pdf`) if available, or docx4j's PDF export as a fallback. PDF fidelity from Word conversion is never perfect — document this clearly. If neither is available, skip PDF output gracefully.
- **`.docx` only — no `.doc` (legacy binary format).** The legacy `.doc` format is not supported. Only Office Open XML (`.docx`).
- **Template file stored in S3.** The uploaded `.docx` file is stored in S3 (same bucket as generated documents). The `DocumentTemplate` entity references the S3 key.
- **Maximum template file size: 10 MB.** Reasonable limit for Word documents with embedded images.
- **Merge field discovery runs on upload.** When a user uploads a `.docx`, the backend immediately parses it and returns the list of discovered fields. This happens synchronously — `.docx` parsing is fast.
- **Existing `DocumentTemplate` entity is extended**, not replaced. A template can be either Tiptap-based (JSONB content) or Word-based (S3-stored `.docx`). The `format` field distinguishes them.

---

## Section 1 — DocumentTemplate Entity Extension

### New Fields

The existing `DocumentTemplate` entity gains:

```
DocumentTemplate (extended):
  ...existing fields (id, name, slug, description, category, content JSONB, entity_type, etc.)...
  format              VARCHAR(10) NOT NULL DEFAULT 'TIPTAP'  — 'TIPTAP' or 'DOCX'
  docx_s3_key         VARCHAR(500) NULL  — S3 key for uploaded .docx template file
  docx_file_name      VARCHAR(255) NULL  — original filename for display
  docx_file_size      BIGINT NULL  — file size in bytes
  discovered_fields   JSONB NULL  — array of discovered merge field paths, e.g., ["customer.name", "project.name"]
```

### Format Enum

```
TemplateFormat:
  TIPTAP  — existing Tiptap JSONB content, rendered via TiptapRenderer → HTML → PDF
  DOCX    — uploaded .docx file stored in S3, rendered via Apache POI merge
```

### Validation Rules

- If `format = TIPTAP`: `content` (JSONB) is required, `docx_s3_key` must be null.
- If `format = DOCX`: `docx_s3_key` is required, `content` may be null (or contain a snapshot summary for search).
- `entity_type` works the same for both formats — determines which `TemplateContextBuilder` resolves variables.
- `category` works the same for both formats.
- A template's `format` cannot be changed after creation (TIPTAP stays TIPTAP, DOCX stays DOCX). Users create a new template if they want to switch formats.

### Migration

Single Flyway migration adding the new columns. Default `format = 'TIPTAP'` for all existing templates. New columns are nullable to avoid breaking existing data.

---

## Section 2 — DOCX Upload & Field Discovery

### Upload Flow

```
1. User clicks "Upload Word Template" on the template list page
2. Frontend sends the .docx file via multipart upload
3. Backend:
   a. Validates file type (.docx MIME type) and size (≤ 10 MB)
   b. Uploads to S3 with key: templates/{tenantId}/{templateId}/template.docx
   c. Parses the .docx to discover merge fields
   d. Creates DocumentTemplate record with format=DOCX, discovered_fields, S3 key
   e. Returns the template record with discovered fields and validation status
4. Frontend displays discovered fields with validation indicators
```

### Field Discovery Algorithm

Parse the `.docx` XML (Apache POI XWPF) and extract all text matching the `{{...}}` pattern:

1. Iterate all body paragraphs (`XWPFParagraph`), table cells (`XWPFTableCell`), headers, and footers.
2. **Critical: handle split runs.** Word frequently splits `{{customer.name}}` across multiple XML runs (e.g., `{{`, `customer`, `.name}}`). The parser must concatenate adjacent runs, find `{{...}}` patterns in the combined text, then record which runs contain merge fields.
3. Extract the variable path (e.g., `customer.name` from `{{customer.name}}`).
4. Deduplicate — a variable used 5 times in the document appears once in `discovered_fields`.
5. Validate each field against `VariableMetadataRegistry` for the template's `entity_type`. Mark each as `valid` or `unknown`.

### Field Discovery Response

```json
{
  "discoveredFields": [
    { "path": "customer.name", "status": "VALID", "label": "Customer Name" },
    { "path": "project.name", "status": "VALID", "label": "Project Name" },
    { "path": "org.brandColor", "status": "VALID", "label": "Brand Color" },
    { "path": "custom.myField", "status": "VALID", "label": "My Custom Field" },
    { "path": "customer.phone", "status": "UNKNOWN", "label": null }
  ],
  "validCount": 4,
  "unknownCount": 1
}
```

Templates with unknown fields can still be saved and used — unknown fields render as empty strings (same behavior as Tiptap templates with missing variables). A warning is shown but generation is not blocked.

### Re-upload / Replace

Users can replace the `.docx` file for an existing DOCX template. This:
1. Uploads the new file to S3 (overwriting the old key)
2. Re-runs field discovery
3. Updates `discovered_fields`, `docx_file_name`, `docx_file_size`
4. Does NOT change the template ID, slug, or any generated documents that reference it

---

## Section 3 — DOCX Merge & Generation

### Merge Pipeline

```
1. User selects a DOCX template + entity (same UI flow as Tiptap generation)
2. Backend:
   a. Load DocumentTemplate (verify format=DOCX)
   b. Download .docx from S3
   c. Resolve context via the appropriate TemplateContextBuilder (same as PDF pipeline)
   d. Open .docx with Apache POI (XWPFDocument)
   e. Walk all paragraphs, tables, headers, footers
   f. For each {{variable}} found, replace with the resolved value from context
   g. Handle split runs: reconstruct the merge field across runs, replace with resolved value, clean up empty runs
   h. Write the merged XWPFDocument to byte array
   i. Upload merged .docx to S3
   j. Optionally generate PDF (if requested and converter available)
   k. Create GeneratedDocument record
   l. Return download URL
```

### Variable Resolution

The merge engine resolves variables using the same `Map<String, Object>` context from `TemplateContextBuilder`:

- `{{customer.name}}` → `context.get("customer")` → `((Map) customer).get("name")`
- `{{org.defaultCurrency}}` → `context.get("org")` → `((Map) org).get("defaultCurrency")`
- `{{customFields.myField}}` → `context.get("customFields")` → `((Map) customFields).get("myField")`
- Nested paths resolved by dot-splitting and walking the map hierarchy.
- Missing/null values render as empty string (not `{{variable}}`).
- Date/number formatting: values are inserted as plain text. Formatting is controlled by the Word template's own formatting (font, size, etc.), not by the merge engine. If a value is a date or number, it's converted to string using the same formatters as the Tiptap pipeline.

### Loop/Repeat Support (Stretch Goal)

Word templates may contain tables where rows should repeat for each item in a collection (e.g., invoice line items). This is a stretch goal — implement only if time permits:

- A table row containing `{{#each invoice.lines}}` ... `{{/each}}` markers
- The merge engine duplicates the row for each item in the collection
- Variables within the loop resolve against the loop item (e.g., `{{description}}`, `{{amount}}`)

If not implemented in this phase, document it as a known limitation: "Loop/repeat for table rows is not yet supported in Word templates. Use the Tiptap editor for templates that need dynamic table rows."

---

## Section 4 — API Endpoints

### New Endpoints

```
POST   /api/templates/docx/upload          — Upload a .docx template file (multipart)
PUT    /api/templates/{id}/docx/replace     — Replace the .docx file for an existing DOCX template
GET    /api/templates/{id}/docx/fields      — Get discovered merge fields with validation status
GET    /api/templates/{id}/docx/download     — Download the original .docx template file
POST   /api/templates/{id}/generate-docx    — Generate a filled .docx from template + entity
```

### Modified Endpoints

```
GET    /api/templates                       — List all templates (add format field to response)
GET    /api/templates/{id}                  — Get template detail (add format, docx fields to response)
POST   /api/templates/{id}/generate         — Existing PDF generation (unchanged, Tiptap only)
DELETE /api/templates/{id}                  — Delete template (also deletes S3 .docx file if DOCX format)
```

### Request/Response Shapes

```json
// POST /api/templates/docx/upload (multipart)
// Form fields:
//   file: the .docx file
//   name: "Engagement Letter"
//   description: "Standard engagement letter for new clients" (optional)
//   category: "LETTER"
//   entityType: "CUSTOMER"

// Response:
{
  "id": "uuid",
  "name": "Engagement Letter",
  "slug": "engagement-letter",
  "format": "DOCX",
  "category": "LETTER",
  "entityType": "CUSTOMER",
  "docxFileName": "engagement-letter-v3.docx",
  "docxFileSize": 245760,
  "discoveredFields": {
    "fields": [
      { "path": "customer.name", "status": "VALID", "label": "Customer Name" },
      { "path": "customer.email", "status": "VALID", "label": "Customer Email" },
      { "path": "org.name", "status": "VALID", "label": "Organisation Name" }
    ],
    "validCount": 3,
    "unknownCount": 0
  },
  "createdAt": "2026-03-08T10:00:00Z"
}

// POST /api/templates/{id}/generate-docx
{
  "entityId": "uuid-of-customer",
  "outputFormat": "DOCX"          // or "PDF" or "BOTH"
}

// Response:
{
  "id": "uuid-of-generated-doc",
  "templateId": "uuid",
  "templateName": "Engagement Letter",
  "format": "DOCX",
  "fileName": "engagement-letter-acme-corp-2026-03-08.docx",
  "downloadUrl": "https://s3.../presigned",
  "pdfDownloadUrl": "https://s3.../presigned",   // null if PDF not requested or not available
  "generatedAt": "2026-03-08T10:15:00Z"
}
```

### Authorization

All DOCX template endpoints follow the same authorization as existing template endpoints — Admin/Owner only (or `@RequiresCapability` if Phase 41 is complete).

---

## Section 5 — GeneratedDocument Extension

### Extended Fields

The existing `GeneratedDocument` entity gains:

```
GeneratedDocument (extended):
  ...existing fields (id, template_id, entity_type, entity_id, s3_key, file_name, etc.)...
  output_format       VARCHAR(10) NOT NULL DEFAULT 'PDF'  — 'PDF' or 'DOCX'
  docx_s3_key         VARCHAR(500) NULL  — S3 key for generated .docx (if output is DOCX or BOTH)
  pdf_s3_key          VARCHAR(500) NULL  — S3 key for generated .pdf (existing field, renamed for clarity)
```

### Generated File Naming

Generated documents are named with a descriptive pattern:
```
{template-slug}-{entity-name}-{date}.docx
```
Example: `engagement-letter-acme-corp-2026-03-08.docx`

Entity name is sanitised (lowercase, spaces to hyphens, special chars removed).

---

## Section 6 — Frontend: Template List & Upload

### Template List Page Changes

The existing template list page gains:

- **Format badge** on each template card: "Tiptap" (blue) or "Word" (green, with Word icon)
- **"Upload Word Template" button** alongside the existing "Create Template" button
- **Filter by format** — optional dropdown: All / Tiptap / Word
- Word templates show file name + size instead of content preview

### Upload Dialog

```
┌──────────────────────────────────────────────────┐
│  Upload Word Template                            │
│                                                  │
│  Name: [_____________________________]           │
│  Description: [______________________] (optional)│
│  Category: [ Letter ▾ ]                          │
│  Entity Type: [ Customer ▾ ]                     │
│                                                  │
│  ┌──────────────────────────────────────────────┐│
│  │                                              ││
│  │   Drag & drop your .docx file here           ││
│  │   or click to browse                         ││
│  │                                              ││
│  │   Max 10 MB. Only .docx files accepted.      ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  [ Upload & Analyse ]  [ Cancel ]                │
└──────────────────────────────────────────────────┘
```

### Field Discovery Results (shown after upload)

```
┌──────────────────────────────────────────────────┐
│  engagement-letter-v3.docx  (240 KB)             │
│                                                  │
│  Discovered Merge Fields                         │
│  ┌──────────────────────────────────────────────┐│
│  │ ✓ customer.name          Customer Name       ││
│  │ ✓ customer.email         Customer Email       ││
│  │ ✓ org.name               Organisation Name    ││
│  │ ✓ org.documentFooterText Footer Text          ││
│  │ ⚠ customer.phone         Unknown field        ││
│  └──────────────────────────────────────────────┘│
│                                                  │
│  4 valid fields, 1 unknown                       │
│  Unknown fields will render as empty text.       │
│                                                  │
│  [ Save Template ]  [ Re-upload ]  [ Cancel ]    │
└──────────────────────────────────────────────────┘
```

### Template Detail Page (DOCX variant)

For DOCX templates, the detail page shows:
- Template metadata (name, category, entity type, description)
- File info (name, size, upload date)
- Discovered fields list with validation status
- "Download Template" button (downloads the original `.docx`)
- "Replace File" button (re-upload flow)
- "Generate Document" button (same entity selector as Tiptap, but generates `.docx`)

No Tiptap editor is shown for DOCX templates.

---

## Section 7 — Frontend: Generation Dialog Changes

### Unified Generation Entry Point

The "Generate Document" dropdown on project/customer/invoice pages shows both Tiptap and DOCX templates, distinguished by format badge:

```
Generate Document ▾
├── 📄 Project Summary Report (Tiptap) → PDF
├── 📄 Engagement Letter (Word) → DOCX
├── 📄 NDA Agreement (Word) → DOCX / PDF
└── 📄 Invoice Template (Tiptap) → PDF
```

### DOCX Generation Dialog

```
┌──────────────────────────────────────────────────┐
│  Generate: Engagement Letter                     │
│  Format: Word Template                           │
│                                                  │
│  Output Format                                   │
│  ○ Word Document (.docx)                         │
│  ○ PDF                                           │
│  ○ Both                                          │
│                                                  │
│  [ Generate ]  [ Cancel ]                        │
│                                                  │
│  ─── After generation ───                        │
│                                                  │
│  ✓ Generated successfully                        │
│  📥 Download engagement-letter-acme-2026-03.docx │
│  📥 Download engagement-letter-acme-2026-03.pdf  │
│                                                  │
│  Document saved to generated documents.          │
└──────────────────────────────────────────────────┘
```

No HTML preview for DOCX generation — the preview step from Tiptap generation is skipped. The user trusts their Word template's layout.

---

## Section 8 — DOCX Processing Service

### DocxMergeService

A new service in the `template/` package that handles all `.docx` operations:

```
DocxMergeService:
  discoverFields(InputStream docx) → List<DiscoveredField>
  merge(InputStream template, Map<String, Object> context) → byte[] (merged .docx)
```

### Split Run Handling (Critical Implementation Detail)

Microsoft Word stores text in "runs" — inline spans with formatting. Word frequently splits what appears as continuous text (`{{customer.name}}`) into multiple runs:

```xml
<w:r><w:t>{{</w:t></w:r>
<w:r><w:rPr><w:b/></w:rPr><w:t>customer</w:t></w:r>
<w:r><w:t>.name}}</w:t></w:r>
```

The merge engine must:
1. Concatenate all runs in a paragraph to find `{{...}}` patterns in the combined text.
2. Track which runs contain parts of each merge field.
3. Replace the content: put the resolved value in the first run, clear the remaining runs (preserving their XML structure to avoid corrupting the document).
4. Preserve the formatting of the first run containing the merge field.

This is the single hardest part of the implementation. Test extensively with real-world Word documents that have mixed formatting within merge fields.

### PDF Conversion (Optional)

If PDF output is requested:
1. Preferred: LibreOffice headless (`soffice --headless --convert-to pdf input.docx`). High fidelity, handles complex formatting.
2. Fallback: docx4j's PDF export (lower fidelity, but no external dependency).
3. If neither available: return DOCX only, log a warning.

For local dev, LibreOffice is not required — PDF conversion is a nice-to-have. Document the setup in CLAUDE.md.

---

## Section 9 — Audit & Notifications

### Audit Events

- `DOCX_TEMPLATE_UPLOADED` — Word template uploaded, details: `{templateId, fileName, fileSize, fieldCount}`
- `DOCX_TEMPLATE_REPLACED` — Word template file replaced, details: `{templateId, oldFileName, newFileName}`
- `DOCX_DOCUMENT_GENERATED` — Filled document generated, details: `{templateId, entityType, entityId, outputFormat}`

Follow existing `AuditEventBuilder` pattern.

### Notifications

- No new notification types. Document generation already has notification support from Phase 12.

---

## Section 10 — Variable Syntax Guide (User-Facing)

### Help Text for Template Authors

Provide a downloadable "merge field reference" that template authors can use when designing Word templates. This should be accessible from the upload dialog and the template detail page.

Content:
- List of all available variables per entity type (from `VariableMetadataRegistry`)
- Syntax guide: `{{variable.path}}` — must be typed as continuous text, no spaces inside braces
- Tip: "Type the merge field in one go. If Word splits it across formatting runs, select the entire field and re-type it in one action."
- Tip: "Use a simple font (e.g., Calibri) for merge fields. Avoid applying mixed formatting within the `{{ }}` braces."
- Example template download (a simple `.docx` with common merge fields pre-inserted)

### Variable Reference Endpoint

```
GET /api/templates/variables?entityType=CUSTOMER
```

Returns the full variable list for the entity type (already exists via `VariableMetadataRegistry`). The frontend renders this as a copy-paste reference panel.

---

## Out of Scope

- **In-app Word editing** — users edit in Microsoft Word or Google Docs, not in the browser
- **Real-time collaboration** — upload → generate → download, no live co-editing
- **`.doc` format (legacy binary)** — only `.docx` (Office Open XML) is supported
- **Loop/repeat in tables** — iterating over collections (e.g., invoice line items) in Word tables is a stretch goal. If not implemented, document as limitation.
- **Conditional sections** — `{{#if}}` / `{{#unless}}` blocks in Word templates. Future enhancement.
- **Image merge fields** — inserting images (e.g., logo, signature) as merge fields. Org logo is handled by the Word template's own image, not by merge.
- **Word Add-in / sidebar** — a Microsoft Office Add-in is a separate initiative, not part of this phase
- **Template versioning** — replacing a `.docx` file overwrites the previous version. Version history is future.

## ADR Topics

- **ADR: DOCX processing library selection** — why Apache POI (XWPF) over docx4j, docx-templater (Node), or Aspose. Trade-offs: maturity, Java-native, open source, split-run handling complexity.
- **ADR: PDF conversion strategy** — why LibreOffice headless as primary with docx4j fallback. Trade-offs: fidelity vs. deployment complexity vs. licensing.
- **ADR: Template format coexistence** — why a single `DocumentTemplate` entity with a `format` discriminator rather than separate entities. Trade-offs: simplicity vs. type safety.

## Style & Boundaries

- The `DocxMergeService` should be a standalone service with no dependency on `TiptapRenderer` or `PdfRenderingService`. The shared layer is `TemplateContextBuilder` — both pipelines consume the same context map.
- Follow existing controller discipline — controllers delegate to a single service method.
- File upload uses Spring's `MultipartFile` with size validation in the controller config.
- S3 operations reuse the existing `StorageService`.
- Frontend components follow existing Shadcn UI patterns. The upload component should use a drag-and-drop zone consistent with the existing document upload UX.
- Tests must include real `.docx` files as test resources — do not rely on programmatically created documents only. Include at least one template with split runs, one with headers/footers, and one with tables.
