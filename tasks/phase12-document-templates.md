# Phase 12 — Document Templates & PDF Generation

Phase 12 adds a **document generation system** to DocTeams — professional, branded PDFs generated from Thymeleaf templates merged with entity data, custom fields, and org branding. This closes the gap between rich operational data (projects, customers, invoices, time entries) and client-facing deliverables. The design follows established patterns: JSONB for branding fields, template packs for seeding (Phase 11 field pack pattern), Thymeleaf rendering (Phase 10 invoice preview pattern), and OpenHTMLToPDF for pure-Java PDF generation.

**Architecture doc**: `architecture/phase12-document-templates.md`

**ADRs**: [ADR-056](../adr/ADR-056-pdf-engine-selection.md) (OpenHTMLToPDF), [ADR-057](../adr/ADR-057-template-storage.md) (database storage), [ADR-058](../adr/ADR-058-rendering-context-assembly.md) (context builder pattern), [ADR-059](../adr/ADR-059-template-customization-model.md) (clone-and-edit)

**CRITICAL MIGRATION NUMBER COORDINATION**: Phase 11 uses V24 for `field_definitions`. Phase 12's architecture doc also references V24, but this **WILL conflict**. Phase 12 must use **V25** for document templates tables.

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 93 | DocumentTemplate Entity Foundation | Backend | -- | L | 93A, 93B | **Done** (PRs #191, #192) |
| 94 | Rendering Pipeline & Generation | Backend | 93 | L | 94A, 94B | |
| 95 | Frontend — Template & Generation UI | Frontend | 93, 94 | L | 95A, 95B | |

## Dependency Graph

```
[E93A V25 Migration + Entity CRUD]──►[E93B Template Packs & Branding]
     (Backend)                              (Backend)
         │                                       │
         └───────────────┬───────────────────────┘
                         │
                         ▼
         [E94A TemplateContextBuilder]──►[E94B Generation & Tracking]
              (Backend)                        (Backend)
                  │                                │
                  └────────────┬───────────────────┘
                               │
                   ┌───────────┴────────────┐
                   ▼                        ▼
         [E95A Template Mgmt UI]    [E95B Generation UI]
              (Frontend)                 (Frontend)
```

**Parallel opportunities**:
- Epic 93A and 93B must run sequentially (93B extends OrgSettings and uses template entity)
- Epic 94A and 94B must run sequentially (94B depends on rendering pipeline)
- Epic 95A and 95B can run in parallel AFTER 94B is complete

## Implementation Order

### Stage 1: Foundation — Migration & Template CRUD (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 93 | 93A | **V25 migration** (document_templates, generated_documents tables, RLS policies, indexes). DocumentTemplate entity with TenantAware, @FilterDef/@Filter, slug auto-generation. GeneratedDocument entity stub (no generation logic yet). Repositories with JPQL findOneById. DocumentTemplateService CRUD (create, update, soft-delete, list). DocumentTemplateController with RBAC (admin/owner only for mutations). Integration tests for entity persistence, slug validation, CRUD operations, tenant isolation, RBAC (~10 tests). Foundation for all template work. |
| 1b | Epic 93 | 93B | TemplatePackSeeder service (follows Phase 11 FieldPackSeeder pattern). TemplatePackDefinition DTO for pack.json. Common template pack (3 templates: engagement-letter, project-summary, invoice-cover-letter) in src/main/resources/template-packs/common/. Clone endpoint (POST /api/templates/{id}/clone) and reset endpoint (POST /api/templates/{id}/reset). **OrgSettings extension**: ALTER TABLE org_settings ADD COLUMN logo_s3_key, brand_color, document_footer_text, template_pack_status JSONB. Logo upload endpoint (POST /api/settings/logo), delete endpoint, extend PUT/GET /api/settings. Integration tests for pack seeding, cloning, branding CRUD (~12 tests). Depends on 93A (template entity must exist). |

### Stage 2: Rendering & Generation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 94 | 94A | TemplateContextBuilder service — assembles Map<String, Object> context from primary entity and related entities. Three context builders: ProjectContextBuilder, CustomerContextBuilder, InvoiceContextBuilder (strategy pattern). Logo URL resolution (S3 pre-signed URL generation). Custom field value inclusion (customFields map from Phase 11 JSONB). Default document CSS (classpath resource). CSS merging logic (default + template custom CSS). **PdfRenderingService** — orchestrates Thymeleaf render + OpenHTMLToPDF conversion. Maven dependency: com.openhtmltopdf:openhtmltopdf-pdfbox. Preview endpoint (POST /api/templates/{id}/preview). Unit tests for context assembly (null safety, missing relationships). Integration tests for HTML rendering and PDF generation (~15 tests). Depends on 93A (template entity), 93B (OrgSettings branding). |
| 2b | Epic 94 | 94B | Extend GeneratedDocument entity with full fields. GeneratedDocumentRepository with listing queries (by entity, by template, by generator). Generate endpoint (POST /api/templates/{id}/generate) — PDF download and save-to-documents flows. S3 upload for generated PDFs. Document entity creation when saving to document system (reuse existing DocumentService). Generated document list endpoint (GET /api/generated-documents). Download endpoint (GET /api/generated-documents/{id}/download). Delete endpoint (DELETE /api/generated-documents/{id}). Filename generation: {template-slug}-{entity-name}-{date}.pdf. Context snapshot (JSONB) — capture key identifiers at generation time. Audit events: template.created, template.updated, template.deleted, template.cloned, document.generated. Notification: DOCUMENT_GENERATED event. Integration tests for generation flow, S3 upload, audit, notification (~12 tests). Depends on 94A (rendering pipeline). |

### Stage 3: Frontend UI (Parallel after 94B)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 95 | 95A | Template management page (/org/[slug]/settings/templates) — list templates grouped by category, source badges, action buttons. Template editor page (/org/[slug]/settings/templates/[id]/edit) — form fields, content textarea (monospace), CSS textarea, variable reference panel. Template creation page (/org/[slug]/settings/templates/new). Clone action (calls POST /api/templates/{id}/clone, redirects to editor). Reset to default action (calls POST /api/templates/{id}/reset, with confirmation dialog). Deactivate/activate toggle. Preview within editor (calls preview endpoint, shows rendered HTML in iframe or dialog). Branding settings section in org settings page — logo upload, brand color input, footer text textarea, live preview. Nav item in settings sidebar. API client functions for template endpoints. Frontend tests for template list, editor, branding form (~10 tests). Depends on 93A (backend CRUD), 93B (branding endpoints), 94A (preview endpoint). |
| 3b | Epic 95 | 95B | "Generate Document" dropdown/button on project detail page — lists available PROJECT-scoped templates. "Generate Document" dropdown/button on customer detail page — lists available CUSTOMER-scoped templates. "Generate Document" dropdown/button on invoice detail page — lists available INVOICE-scoped templates. Generation dialog — template preview (HTML iframe), "Download PDF" button, "Save to Documents" button. Generated documents list (tab or section on project/customer detail pages) — table with template name, generated by, date, file size, download/delete actions. API client functions for generation and generated-documents endpoints. Frontend tests for generation dialog, generated documents list (~10 tests). Depends on 94B (generation + tracking endpoints), 95A (template management for template data). Can run in parallel with 95A. |

### Timeline

```
Stage 1:  [93A] ──► [93B]                          ← foundation (sequential)
Stage 2:  [94A] ──► [94B]                          ← rendering + generation (sequential)
Stage 3:  [95A]  //  [95B]                         ← frontend (parallel)
```

**Critical path**: 93A → 93B → 94A → 94B → 95A/95B
**Parallelizable**: 95A and 95B can run in parallel after 94B is complete

---

## Epic 93: DocumentTemplate Entity Foundation

**Goal**: Establish the document template infrastructure — template entity with slug generation, template CRUD API, template pack seeding (following Phase 11 field pack pattern), clone-and-edit customization model, and org branding settings extension (logo, brand color, footer text). This epic provides the data model and management APIs for templates without implementing rendering or generation.

**References**: Architecture doc Sections 12.2.1 (DocumentTemplate entity), 12.2.2 (GeneratedDocument entity), 12.2.3 (OrgSettings extension), 12.3.1 (template management), 12.3.5 (template pack seeding), 12.8.1 (V25 migration). [ADR-057](../adr/ADR-057-template-storage.md), [ADR-059](../adr/ADR-059-template-customization-model.md).

**Dependencies**: None (new tables and package)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **93A** | 93.1-93.11 | V25 migration (document_templates, generated_documents tables, RLS, indexes), DocumentTemplate entity with slug auto-gen, GeneratedDocument entity (stub), repositories with JPQL findOneById, DocumentTemplateService CRUD, DocumentTemplateController with RBAC, integration tests (~10 tests) | **Done** (PR #191) |
| **93B** | 93.12-93.22 | TemplatePackSeeder service, TemplatePackDefinition DTO, common template pack (3 templates), clone/reset endpoints, OrgSettings extension (logo_s3_key, brand_color, document_footer_text, template_pack_status), logo upload/delete endpoints, extend settings API, integration tests for packs, cloning, branding (~12 tests) | **Done** (PR #192) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 93.1 | Create V25 migration file | 93A | **Done** | `db/migration/tenant/V25__create_document_templates.sql`. Create document_templates table (18 columns per Section 12.2.1: id, name, slug, description, category, primary_entity_type, content, css, source, source_template_id FK, pack_id, pack_template_key, active, sort_order, tenant_id, created_at, updated_at). Create generated_documents table (12 columns per Section 12.2.2: id, template_id FK, primary_entity_type, primary_entity_id, document_id FK nullable, file_name, s3_key, file_size, generated_by FK, context_snapshot JSONB, tenant_id, generated_at). Constraints: UNIQUE (tenant_id, slug) on document_templates, CHECK (category IN ...), CHECK (primary_entity_type IN ...), CHECK (source IN ...), CHECK (slug ~ '^[a-z][a-z0-9-]*$'), CHECK (pack_id and pack_template_key both null or both non-null). Indexes per Section 12.8.1 (category, entity_type, pack_id, tenant_id). RLS policies per established pattern. Also includes: ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS logo_s3_key, brand_color, document_footer_text, template_pack_status JSONB, plus brand_color CHECK constraint. Pattern: follow V24 (field_definitions) multi-table migration with RLS. |
| 93.2 | Create DocumentTemplate entity | 93A | **Done** | `template/DocumentTemplate.java` in new package. @Entity, @Table(name = "document_templates"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 18 fields. Protected no-arg constructor. Public constructor taking (entityType, name, slug, category, content). Slug auto-generation method: `generateSlug(String name)` — lowercase, replace spaces with hyphens, strip non-alphanumeric except hyphens, validate against ^[a-z][a-z0-9-]*$. Domain methods: updateContent(name, description, content, css), deactivate(). Pattern: follow Invoice entity (Phase 10). |
| 93.3 | Create GeneratedDocument entity (stub) | 93A | **Done** | `template/GeneratedDocument.java`. @Entity, @Table(name = "generated_documents"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 12 fields with @JdbcTypeCode(SqlTypes.JSON) for context_snapshot JSONB column. Protected no-arg constructor. Public constructor taking (templateId, primaryEntityType, primaryEntityId, fileName, s3Key, fileSize, generatedBy). Domain method: linkToDocument(UUID documentId). Pattern: follow TimeEntry entity (Phase 5). |
| 93.4 | Create DocumentTemplateRepository | 93A | **Done** | `template/DocumentTemplateRepository.java`. JpaRepository<DocumentTemplate, UUID>. JPQL: `findOneById`, `findByActiveTrueOrderBySortOrder`, `findByCategoryAndActiveTrueOrderBySortOrder`, `findByPrimaryEntityTypeAndActiveTrueOrderBySortOrder`, `findBySlug`. Pattern: follow FieldDefinitionRepository (Phase 11). |
| 93.5 | Create GeneratedDocumentRepository (stub) | 93A | **Done** | `template/GeneratedDocumentRepository.java`. JpaRepository<GeneratedDocument, UUID>. JPQL: `findOneById`, `findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc`. Pattern: simple repository. Full queries added in Epic 94B. |
| 93.6 | Create DocumentTemplateService | 93A | **Done** | `template/DocumentTemplateService.java`. @Service. Methods: listAll(), listByCategory(category), listByEntityType(entityType), getById(id), create(CreateTemplateRequest), update(id, UpdateTemplateRequest), deactivate(id). Auto-generate slug if null, check uniqueness, validate source=ORG_CUSTOM for create. RBAC: admin/owner only for mutations via RequestScopes.ORG_ROLE. Pattern: follow FieldDefinitionService (Phase 11). |
| 93.7 | Create DocumentTemplateController | 93A | **Done** | `template/DocumentTemplateController.java`. @RestController, @RequestMapping("/api/templates"). Endpoints: GET list (query params: category, primaryEntityType), GET /{id} detail, POST create, PUT /{id} update, DELETE /{id} soft-delete. DTO records: CreateTemplateRequest (7 fields), UpdateTemplateRequest (6 fields), TemplateListResponse (metadata only — excludes content/css), TemplateDetailResponse (all fields). @PreAuthorize on mutations. Pattern: follow InvoiceController (Phase 10). |
| 93.8 | Add slug generation unit tests | 93A | **Done** | `template/DocumentTemplateSlugTest.java` (~5 tests): generateSlug from name, special chars, regex validation, uniqueness. Pattern: unit test. |
| 93.9 | Add DocumentTemplate CRUD integration tests | 93A | **Done** | `template/DocumentTemplateIntegrationTest.java` (~8 tests): save/retrieve in dedicated and shared schema, findOneById respects @Filter, slug auto-gen, slug uniqueness, deactivate, admin CRUD, member 403. Pattern: follow FieldDefinitionIntegrationTest. |
| 93.10 | Add DocumentTemplateController integration tests | 93A | **Done** | `template/DocumentTemplateControllerTest.java` (~7 tests): GET list returns active, filter by category, filter by entity type, POST creates with slug, PUT updates, DELETE soft-deletes, list excludes content/css. Pattern: MockMvc. |
| 93.11 | Verify V25 migration runs cleanly | 93A | **Done** | Run `./mvnw clean test -q` and verify no migration errors. Check tables created in both dedicated and shared schemas. |
| 93.12 | Create TemplatePackSeeder service | 93B | **Done** | `template/TemplatePackSeeder.java`. @Service. Method: `seedPacksForTenant(String tenantId)` — scan classpath:template-packs/*/ for pack.json, parse TemplatePackDefinition, check OrgSettings.templatePackStatus for idempotency, create PLATFORM template records, update templatePackStatus. Pattern: follow FieldPackSeeder (Phase 11) exactly. |
| 93.13 | Create TemplatePackDefinition DTO | 93B | **Done** | `template/TemplatePackDefinition.java` record. Fields: packId, version, name, description, templates (List<TemplatePackTemplate>). TemplatePackTemplate: templateKey, name, category, primaryEntityType, contentFile, cssFile (nullable), description, sortOrder. Pattern: match FieldPackDefinition. |
| 93.14 | Create common template pack files | 93B | **Done** | `src/main/resources/template-packs/common/pack.json` + 3 HTML files: engagement-letter.html, project-summary.html, invoice-cover-letter.html. Stub templates with Thymeleaf placeholders (${project.name}, ${customer.name}, ${org.name}, ${org.logoUrl}). |
| 93.15 | Add template pack seeding integration tests | 93B | **Done** | `template/TemplatePackSeederTest.java` (~5 tests): seed creates 3 templates, idempotency, multi-pack, version tracking, source=PLATFORM set. |
| 93.16 | Add clone endpoint to DocumentTemplateService | 93B | **Done** | `cloneTemplate(UUID templateId)`: load template, verify source=PLATFORM, check no existing ORG_CUSTOM clone for same pack_template_key, create new ORG_CUSTOM with sourceTemplateId=original.id, slug="{original-slug}-custom". |
| 93.17 | Add reset endpoint to DocumentTemplateService | 93B | **Done** | `resetToDefault(UUID cloneId)`: load template, verify source=ORG_CUSTOM, verify sourceTemplateId not null, hard-delete clone. |
| 93.18 | Add clone/reset endpoints to DocumentTemplateController | 93B | **Done** | POST /api/templates/{id}/clone, POST /api/templates/{id}/reset. @PreAuthorize admin/owner. 409 Conflict if clone exists, 400 for invalid operations. |
| 93.19 | Extend OrgSettings entity with branding fields | 93B | **Done** | Modify `orgsettings/OrgSettings.java`. Add: logoS3Key, brandColor, documentFooterText, templatePackStatus (JSONB). Pattern: follow Phase 11 field_pack_status. |
| 93.20 | Add logo upload/delete endpoints to SettingsController | 93B | **Done** | POST /api/settings/logo (multipart, max 2MB, PNG/JPG/SVG, S3 upload to org/{tenantId}/branding/logo.{ext}). DELETE /api/settings/logo. Extend PUT/GET /api/settings with brandColor, documentFooterText, logoUrl (pre-signed). @PreAuthorize admin/owner. Pattern: follow document upload pattern (Phase 4). |
| 93.21 | Integrate TemplatePackSeeder into TenantProvisioningService | 93B | **Done** | Modify TenantProvisioningService to call `templatePackSeeder.seedPacksForTenant(tenantId)` after Flyway migrations. |
| 93.22 | Add branding and clone integration tests | 93B | **Done** | `orgsettings/BrandingIntegrationTest.java` (~7 tests): logo upload/delete, brandColor/footerText update, GET includes branding, invalid hex 400, file too large 400, member 403. `template/TemplateCloneResetTest.java` (~5 tests): clone PLATFORM, clone conflict 409, reset ORG_CUSTOM, reset non-clone 400. |

### Key Files

**Slice 93A — Create:**
- `backend/src/main/resources/db/migration/tenant/V25__create_document_templates.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateSlugTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateControllerTest.java`

**Slice 93A — Read for context:**
- `architecture/phase12-document-templates.md` Sections 12.2.1, 12.2.2, 12.3.1, 12.8.1
- `backend/src/main/resources/db/migration/tenant/V24__add_custom_fields_tags_views.sql` — Migration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` — Entity pattern
- `adr/ADR-057-template-storage.md`, `adr/ADR-059-template-customization-model.md`

**Slice 93B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackDefinition.java`
- `backend/src/main/resources/template-packs/common/pack.json`
- `backend/src/main/resources/template-packs/common/engagement-letter.html`
- `backend/src/main/resources/template-packs/common/project-summary.html`
- `backend/src/main/resources/template-packs/common/invoice-cover-letter.html`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeederTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplateCloneResetTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/orgsettings/BrandingIntegrationTest.java`

**Slice 93B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgsettings/OrgSettings.java` — Add branding fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgsettings/SettingsController.java` — Add logo upload/delete, extend PUT/GET
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` — Add clone/reset
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` — Add clone/reset endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — Call TemplatePackSeeder

### Architecture Decisions

- **ADR-057 (Template Storage)**: Templates stored as TEXT columns in PostgreSQL — tenant isolation automatic, transactional consistency, no deployment coupling.
- **ADR-059 (Template Customization Model)**: Clone-and-edit with separate ORG_CUSTOM records. PLATFORM templates immutable per-tenant, "Reset to Default" is a delete.
- **Migration V25**: Replaces V24 reference in architecture doc. OrgSettings ALTER included in same migration.
- **Template pack pattern**: Follows Phase 11 field pack pattern — JSON pack.json + content files on classpath, idempotency via OrgSettings JSONB tracking.

---

## Epic 94: Rendering Pipeline & Generation

**Goal**: Build the template rendering pipeline and document generation flow. TemplateContextBuilder assembles data from entities, custom fields, org settings, and S3 pre-signed URLs into a flat Map<String, Object>. PdfRenderingService orchestrates Thymeleaf → HTML → OpenHTMLToPDF → PDF bytes. GeneratedDocument tracks every PDF created, linking template, entity, generator, and S3 key. Preview and generation endpoints support download-only and save-to-documents flows. Audit and notification events complete the integration.

**References**: Architecture doc Sections 12.3.2 (document generation), 12.3.3 (rendering context assembly), 12.4.2 (preview/generation API), 12.4.3 (generated documents API), 12.5 (sequence diagrams), 12.6 (template content/CSS), 12.7 (notification/audit). [ADR-056](../adr/ADR-056-pdf-engine-selection.md), [ADR-058](../adr/ADR-058-rendering-context-assembly.md).

**Dependencies**: Epic 93 (template entity, OrgSettings branding)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **94A** | 94.1-94.12 | TemplateContextBuilder service with per-entity-type strategy (ProjectContextBuilder, CustomerContextBuilder, InvoiceContextBuilder), logo URL resolution, custom field flattening, default document CSS (classpath), CSS merging, PdfRenderingService (Thymeleaf + OpenHTMLToPDF), preview endpoint, unit tests for context assembly, integration tests for rendering (~15 tests) | |
| **94B** | 94.13-94.24 | Extend GeneratedDocumentRepository, GeneratedDocumentService, generation endpoint (download/save-to-documents flows), S3 upload, Document entity creation, generated documents list/download/delete endpoints, filename generation, context snapshot JSONB, audit events (template.created/.updated/.deleted/.cloned, document.generated), notification (DOCUMENT_GENERATED event), integration tests (~12 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 94.1 | Create TemplateContextBuilder interface | 94A | | `template/TemplateContextBuilder.java` interface. Method: `Map<String, Object> buildContext(String entityType, UUID entityId, UUID memberId)`. Three implementations: ProjectContextBuilder, CustomerContextBuilder, InvoiceContextBuilder (strategy pattern or switch). |
| 94.2 | Create ProjectContextBuilder | 94A | | `template/ProjectContextBuilder.java`. @Service. Builds Map with keys per Section 12.3.3: project.*, customer.*, lead.*, members[], org.*, budget.*, tags[], generatedAt, generatedBy.*. Loads project, customer, lead, members, budget, tags, OrgSettings. Resolves logoS3Key to pre-signed URL (5-min TTL). Flattens custom_fields JSONB. Null safety for missing customer/budget. |
| 94.3 | Create CustomerContextBuilder | 94A | | `template/CustomerContextBuilder.java`. @Service. Builds: customer.*, projects[] ({id, name, status}), org.*, tags[], generatedAt, generatedBy.*. Pattern: follow ProjectContextBuilder. |
| 94.4 | Create InvoiceContextBuilder | 94A | | `template/InvoiceContextBuilder.java`. @Service. Builds: invoice.*, lines[] ({description, quantity, unitPrice, amount}), customer.*, project.*, org.*, generatedAt, generatedBy.*. |
| 94.5 | Add default document CSS resource | 94A | | `src/main/resources/templates/document-default.css`. @page A4 margins, body font/color, .header border-bottom with brand-color, .footer 9pt, table styling. Per Section 12.6.2. |
| 94.6 | Create PdfRenderingService | 94A | | `template/PdfRenderingService.java`. @Service. Method: `PdfResult generatePdf(UUID templateId, UUID entityId, UUID memberId)`. Orchestrates: load template → build context → merge CSS → render Thymeleaf → convert HTML→PDF via OpenHTMLToPDF (PdfRendererBuilder). Returns PdfResult(byte[] pdfBytes, String fileName, String htmlPreview). Pattern: orchestrator service. |
| 94.7 | Add Maven dependency for OpenHTMLToPDF | 94A | | `pom.xml`: com.openhtmltopdf:openhtmltopdf-pdfbox:1.1.22. |
| 94.8 | Add preview endpoint to DocumentTemplateController | 94A | | POST /api/templates/{id}/preview ({entityId}). Returns HTML string with Content-Type: text/html. |
| 94.9 | Add TemplateContextBuilder unit tests | 94A | | ~15 tests across ProjectContextBuilderTest, CustomerContextBuilderTest, InvoiceContextBuilderTest: with/without customer, with/without budget, custom fields, logoUrl resolution, tags, members. Pattern: unit test with mocked services. |
| 94.10 | Add PdfRenderingService integration tests | 94A | | `template/PdfRenderingServiceTest.java` (~5 tests): valid PDF bytes, HTML preview content, logo image, CSS merging, filename format. |
| 94.11 | Add preview endpoint integration test | 94A | | `template/TemplatePreviewControllerTest.java` (~2 tests): preview returns HTML 200, includes project name and branding. |
| 94.12 | Verify OpenHTMLToPDF rendering | 94A | | Smoke test: generate PDF with logo, verify CSS applied. |
| 94.13 | Extend GeneratedDocumentRepository with full queries | 94B | | Add JPQL: findByTemplateIdOrderByGeneratedAtDesc, findByGeneratedByOrderByGeneratedAtDesc. |
| 94.14 | Create GeneratedDocumentService | 94B | | `template/GeneratedDocumentService.java`. @Service. CRUD: create(), listByEntity(), getById(), delete() (admin/owner only). |
| 94.15 | Add generation endpoint to DocumentTemplateController | 94B | | POST /api/templates/{id}/generate ({entityId, saveToDocuments}). Pipeline: render PDF → upload to S3 → build context snapshot → create GeneratedDocument → optionally create Document entity → publish DocumentGeneratedEvent → audit event → return PDF binary or 201 with metadata. |
| 94.16 | Create GeneratedDocumentController | 94B | | `template/GeneratedDocumentController.java`. @RestController, @RequestMapping("/api/generated-documents"). GET list (?entityType, ?entityId), GET /{id}/download (stream PDF from S3), DELETE /{id} (admin/owner). DTO: GeneratedDocumentListResponse (id, templateName, primaryEntityType, primaryEntityId, fileName, fileSize, generatedByName, generatedAt, documentId). |
| 94.17 | Add filename generation logic | 94B | | Method in PdfRenderingService: `generateFilename(slug, context)` → {slug}-{entity-name-slugified}-{yyyy-MM-dd}.pdf. |
| 94.18 | Add DocumentGeneratedEvent domain event | 94B | | `event/DocumentGeneratedEvent.java` record. Implements DomainEvent. Fields: templateName, primaryEntityType, primaryEntityId, fileName, generatedDocumentId + standard DomainEvent fields. Pattern: follow InvoiceStatusChangedEvent. |
| 94.19 | Add DocumentGeneratedEventHandler for notifications | 94B | | `notification/DocumentGeneratedEventHandler.java`. @Component. Creates notification for project lead (PROJECT-scoped) or admins (CUSTOMER/INVOICE). Type=DOCUMENT_GENERATED. Pattern: follow existing event handlers. |
| 94.20 | Add audit events for template lifecycle | 94B | | Modify DocumentTemplateService: audit template.created, template.updated, template.deleted, template.cloned. Use AuditService.createAuditEvent(). |
| 94.21 | Add audit event for document generation | 94B | | In generation endpoint: audit document.generated with details (template_name, entity_type, entity_id, file_name, file_size, save_to_documents). |
| 94.22 | Add generation flow integration tests | 94B | | `template/DocumentGenerationIntegrationTest.java` (~8 tests): download flow, save-to-documents flow, S3 upload, context snapshot, filename, entity access check, no access 403, missing template 404. |
| 94.23 | Add audit and notification integration tests | 94B | | `template/DocumentAuditNotificationTest.java` (~4 tests): template.created audit, template.cloned audit, document.generated audit, DOCUMENT_GENERATED notification for project lead. |
| 94.24 | Add GeneratedDocumentController integration tests | 94B | | `template/GeneratedDocumentControllerTest.java` (~5 tests): GET list, GET download, DELETE (admin), member cannot delete 403, list includes templateName/generatedByName. |

### Key Files

**Slice 94A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/InvoiceContextBuilder.java`
- `backend/src/main/resources/templates/document-default.css`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilderTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePreviewControllerTest.java`

**Slice 94A — Modify:**
- `backend/pom.xml` — Add OpenHTMLToPDF dependency
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` — Add preview endpoint

**Slice 94A — Read for context:**
- `architecture/phase12-document-templates.md` Sections 12.3.2, 12.3.3, 12.4.2, 12.5.1, 12.6
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — Thymeleaf rendering pattern
- `adr/ADR-056-pdf-engine-selection.md`, `adr/ADR-058-rendering-context-assembly.md`

**Slice 94B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DocumentGeneratedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/DocumentGeneratedEventHandler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentGenerationIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocumentAuditNotificationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentControllerTest.java`

**Slice 94B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocumentRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateController.java` — Add generation endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateService.java` — Add audit events
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` — Add filename generation

### Architecture Decisions

- **ADR-056 (PDF Engine)**: OpenHTMLToPDF — pure Java, no external process, CSS 2.1+, suitable for containerized deployment.
- **ADR-058 (Context Assembly)**: Builder produces `Map<String, Object>` — decouples from JPA entities, prevents lazy-loading, explicit data control.
- **CSS merging**: Default document CSS + template custom CSS at render time.
- **Notification strategy**: Only for saveToDocuments=true (persistent artifact). Download-only does not notify.
- **Audit integration**: Template lifecycle events + generation events all audited.

---

## Epic 95: Frontend — Template & Generation UI

**Goal**: Build the template management UI (settings page for admins to create/edit/clone/reset templates, branding settings for logo/color/footer) and the document generation UI (generate buttons on entity detail pages, generation dialog with preview, generated documents list).

**References**: Architecture doc Sections 12.3.1, 12.3.4, 12.4, 12.9 (capability slices 89A/89B).

**Dependencies**: Epic 93 (template CRUD + branding APIs), Epic 94 (preview + generation APIs)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **95A** | 95.1-95.10 | Template management page, template editor, template creation, clone/reset actions, deactivate toggle, preview in editor, branding settings section (logo upload, brand color, footer text), nav item, API client functions, frontend tests (~10 tests) | |
| **95B** | 95.11-95.20 | "Generate Document" dropdown on project/customer/invoice detail pages, generation dialog (preview iframe, download/save buttons), generated documents list, download/delete actions, API client functions, frontend tests (~10 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 95.1 | Create template management page | 95A | | `app/(app)/org/[slug]/settings/templates/page.tsx`. Server component. Fetch templates, group by category. Table: Name, Category, Entity Type, Source badge, Active status, Actions (Edit/Clone/Reset/Deactivate). Pattern: follow settings page pattern. |
| 95.2 | Create template editor page | 95A | | `app/(app)/org/[slug]/settings/templates/[id]/edit/page.tsx`. Server component fetches template detail. Renders TemplateEditorForm client component. |
| 95.3 | Create TemplateEditorForm component | 95A | | `components/templates/TemplateEditorForm.tsx`. Client component: name, description, content (monospace textarea), css (monospace textarea), variable reference panel (collapsible, per primaryEntityType), preview button, save button. Pattern: Shadcn Form + react-hook-form. |
| 95.4 | Create template creation page | 95A | | `app/(app)/org/[slug]/settings/templates/new/page.tsx`. Form: name, description, category dropdown, primaryEntityType dropdown, content, css. POST /api/templates → redirect to editor. |
| 95.5 | Add clone/reset/deactivate actions | 95A | | Clone (PLATFORM only → POST clone → toast → refetch), Reset (ORG_CUSTOM clones → confirmation dialog → POST reset), Deactivate toggle (PUT with active=false). |
| 95.6 | Add preview within editor | 95A | | Preview button in TemplateEditorForm. Entity picker → POST preview → display HTML in dialog iframe. |
| 95.7 | Create branding settings section | 95A | | Extend settings page: logo upload (file input, 2MB, PNG/JPG/SVG, POST /api/settings/logo, display + delete), brand color (color picker/hex input), document footer text (textarea). Save via PUT /api/settings. |
| 95.8 | Add nav item in settings sidebar | 95A | | Add "Templates" link to settings nav (admin/owner only). |
| 95.9 | Add template API client functions | 95A | | Extend `lib/api.ts`: fetchTemplates, fetchTemplateDetail, createTemplate, updateTemplate, deleteTemplate, cloneTemplate, resetTemplate, previewTemplate, uploadLogo, deleteLogo. |
| 95.10 | Add template management frontend tests | 95A | | ~10 tests: template list rendering, PLATFORM Clone action, ORG_CUSTOM Edit/Reset actions, clone API call, reset confirmation, editor form rendering, monospace textareas, variable reference panel, preview API call, save API call. Pattern: vitest + @testing-library/react. |
| 95.11 | Add "Generate Document" dropdown to project detail page | 95B | | Modify project detail page. Fetch PROJECT-scoped templates. Dropdown button (Shadcn DropdownMenu) → clicking template opens GenerateDocumentDialog. |
| 95.12 | Add "Generate Document" dropdown to customer detail page | 95B | | Modify customer detail page. Fetch CUSTOMER-scoped templates. Same dropdown pattern. |
| 95.13 | Add "Generate Document" dropdown to invoice detail page | 95B | | Modify invoice detail page. Fetch INVOICE-scoped templates. Same dropdown pattern. |
| 95.14 | Create GenerateDocumentDialog component | 95B | | `components/templates/GenerateDocumentDialog.tsx`. Props: templateId, entityId, entityType. Preview in iframe (POST preview). "Download PDF" (POST generate saveToDocuments=false → trigger download). "Save to Documents" (POST generate saveToDocuments=true → success toast → refetch list). |
| 95.15 | Create GeneratedDocumentsList component | 95B | | `components/templates/GeneratedDocumentsList.tsx`. Props: entityType, entityId. Fetch GET /api/generated-documents. Table: Template Name, Generated By, Date, File Size, Actions (Download, Delete-admin). |
| 95.16 | Integrate GeneratedDocumentsList into entity pages | 95B | | Add GeneratedDocumentsList to project/customer/invoice detail pages as tab or section. |
| 95.17 | Add generation API client functions | 95B | | Extend `lib/api.ts`: generateDocument, fetchGeneratedDocuments, downloadGeneratedDocument, deleteGeneratedDocument. |
| 95.18 | Add generation dialog frontend tests | 95B | | ~5 tests: iframe preview, download button, save button, success toast, error handling. |
| 95.19 | Add generated documents list frontend tests | 95B | | ~5 tests: list rendering, download action, delete confirmation, delete API call, admin-only delete button. |
| 95.20 | Add integration tests for entity page generation flow | 95B | | ~3 tests: project page dropdown, template opens dialog, generated list updates after save. |

### Key Files

**Slice 95A — Create:**
- `frontend/app/(app)/org/[slug]/settings/templates/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/templates/[id]/edit/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/templates/new/page.tsx`
- `frontend/components/templates/TemplateEditorForm.tsx`
- `frontend/components/templates/CreateTemplateForm.tsx`
- `frontend/components/templates/TemplateVariableReference.tsx`
- `frontend/__tests__/templates/TemplateManagementPage.test.tsx`
- `frontend/__tests__/templates/TemplateEditorForm.test.tsx`

**Slice 95A — Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — Add branding section
- `frontend/lib/api.ts` — Add template + branding API client functions
- `frontend/lib/nav-items.ts` (or settings layout) — Add Templates nav item

**Slice 95B — Create:**
- `frontend/components/templates/GenerateDocumentDialog.tsx`
- `frontend/components/templates/GeneratedDocumentsList.tsx`
- `frontend/__tests__/templates/GenerateDocumentDialog.test.tsx`
- `frontend/__tests__/templates/GeneratedDocumentsList.test.tsx`
- `frontend/__tests__/templates/ProjectGenerationFlow.test.tsx`

**Slice 95B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add Generate dropdown + GeneratedDocumentsList
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Add Generate dropdown + GeneratedDocumentsList
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` — Add Generate dropdown + GeneratedDocumentsList
- `frontend/lib/api.ts` — Add generation API client functions

### Architecture Decisions

- **Template management UX**: Settings page for admins/owners. PLATFORM templates immutable (clone to customize), ORG_CUSTOM editable. "Reset to Default" deletes clone, restoring platform original.
- **Branding settings**: Logo upload, brand color, footer text in org settings. Admin/owner only.
- **Generation UX**: Dropdown on entity pages lists scoped templates. Dialog shows HTML preview, two-action flow (Download or Save to Documents).
- **Variable reference panel**: Shows available variables by entity type — helps template authors.
