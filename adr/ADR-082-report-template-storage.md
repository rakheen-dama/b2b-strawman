# ADR-082: Report Template Storage

**Status**: Accepted

**Context**:

Phase 19 needs to store Thymeleaf HTML templates that render report output (tables, summaries, branding). Phase 12 already established a pattern for database-stored Thymeleaf templates via the `DocumentTemplate` entity, which stores `content` (HTML), `css`, and metadata. The question is whether report templates should reuse `DocumentTemplate` or live in a separate entity.

The key difference between document templates and report templates is the rendering context. Document templates are scoped to a primary entity type (`PROJECT`, `CUSTOMER`, `INVOICE`) and use entity-specific `TemplateContextBuilder` implementations that assemble context from JPA entities. Report templates are driven by aggregation query results — their context contains `rows` (a list of maps), `summary` (aggregate values), `parameters` (user inputs), and `columns` (metadata). These shapes are fundamentally incompatible.

**Options Considered**:

1. **Reuse DocumentTemplate with a "REPORT" category** -- Add `REPORT` to `TemplateCategory` and `REPORT` to `TemplateEntityType`. Store report templates as `DocumentTemplate` rows.
   - Pros: No new entity. Reuses existing template management UI and CRUD endpoints. Single table for all templates.
   - Cons: `DocumentTemplate.primaryEntityType` doesn't apply — reports don't have a primary entity. The existing `PdfRenderingService.generatePdf(templateId, entityId, memberId)` signature doesn't fit report execution (no single entityId). The template editor UI would need to be aware of two fundamentally different context shapes. `TemplateContextBuilder` pattern doesn't apply. Pollutes the document template list with non-document entries.

2. **Separate ReportDefinition entity with inline template_body (chosen)** -- A dedicated `ReportDefinition` entity that stores the template alongside report metadata (parameter schema, column definitions, slug). The template is in a `template_body` TEXT column.
   - Pros: Clean separation of concerns — document templates and report definitions have different lifecycles, different context shapes, and different rendering paths. The entity captures everything needed for a report in one place (parameters, columns, template, category). No modifications to existing Phase 12 code. New CRUD endpoints don't conflict with existing template management.
   - Cons: Duplicates some storage patterns (both entities have a TEXT column with Thymeleaf HTML). Two seed pack systems (TemplatePackSeeder + StandardReportPackSeeder).

3. **Classpath-only templates (no database storage)** -- Report templates stored as `.html` files in `src/main/resources/templates/reports/`. Referenced by filename from a report configuration (either Java enum or properties file).
   - Pros: Simple. Templates versioned with code. No database storage needed. Standard Thymeleaf template resolution.
   - Cons: Not customizable per-tenant — all orgs get identical templates. Cannot be edited without redeployment. Inconsistent with the database-stored pattern established by Phase 12. Breaks the seed pack philosophy where orgs can eventually customize their templates.

**Decision**: Option 2 -- Separate `ReportDefinition` entity with inline `template_body`.

**Rationale**:

Document templates and report definitions serve fundamentally different purposes despite both containing Thymeleaf HTML. A document template answers "given entity X, produce a formatted document" with context built from JPA entity traversal. A report definition answers "given these parameters, aggregate data across many entities and render the result" with context built from SQL query results. Forcing these into one entity would require the entity to be aware of both paradigms, adding complexity to `DocumentTemplate` (which is well-established and working) for no benefit.

The inline `template_body` approach mirrors `DocumentTemplate.content` — both are TEXT columns storing Thymeleaf HTML. The rendering path is identical: call `PdfRenderingService.renderThymeleaf(templateBody, contextMap)`. The difference is only in how `contextMap` is assembled (entity-based vs query-based), which is handled by `ReportRenderingService` rather than `TemplateContextBuilder`.

Storing templates in the database (rather than classpath) preserves the possibility of per-tenant template customization in a future phase. It also means the seed pack system controls when templates are updated — an org that has customized a template won't have it overwritten on deployment.

**Backend impact**: New `ReportDefinition` entity in `reporting/` package with `template_body` TEXT column. `ReportRenderingService` calls `PdfRenderingService.renderThymeleaf(definition.getTemplateBody(), contextMap)` directly. No changes to existing `DocumentTemplate` entity or `PdfRenderingService`. New `StandardReportPackSeeder` tracks application in `OrgSettings.reportPackStatus` JSONB field.

**Consequences**:

- `ReportDefinition` is a self-contained entity: metadata + parameters + columns + template in one row.
- Report template rendering calls `PdfRenderingService.renderThymeleaf()` directly — no new rendering infrastructure.
- Two distinct seed pack systems: `TemplatePackSeeder` (document templates) and `StandardReportPackSeeder` (report definitions). Both track application status in `OrgSettings`.
- The existing document template management UI and endpoints are not affected.
- Future: if a report template editor is added, it will be a separate UI (not shared with the document template editor) because the context variables and preview mechanism differ.
