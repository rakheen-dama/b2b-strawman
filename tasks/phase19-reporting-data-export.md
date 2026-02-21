# Phase 19 — Reporting & Data Export

Phase 19 adds a **reporting and data export framework** to the DocTeams platform. The platform already captures rich operational data across time entries, invoices, projects, budgets, and billing rates. Phase 19 closes the gap between this raw data and distributable outputs by introducing a `ReportDefinition` entity, a strategy-based execution framework, three standard report implementations (Timesheet, Invoice Aging, Project Profitability), and a rendering pipeline that streams branded HTML previews, PDF downloads, and CSV exports. A new Reports section in the sidebar surfaces this capability to all org members.

This phase **extends** the existing `report/` package (Phase 8 profitability endpoints) without modifying it. All new code lives in a new `reporting/` package with non-conflicting URL paths (`/api/report-definitions/**`).

**Architecture doc**: `architecture/phase19-reporting-data-export.md`

**ADRs**:
- [ADR-081](../adr/ADR-081-report-query-strategy-pattern.md) — Report Query Strategy Pattern (Spring `@Component` auto-collection)
- [ADR-082](../adr/ADR-082-report-template-storage.md) — Report Template Storage (separate `ReportDefinition` entity with inline `template_body`)
- [ADR-083](../adr/ADR-083-csv-generation-approach.md) — CSV Generation Approach (streaming via `BufferedWriter` to `ServletOutputStream`)
- [ADR-084](../adr/ADR-084-parameter-schema-design.md) — Parameter Schema Design (custom JSON schema with typed parameter definitions)

**MIGRATION**: `V35__create_report_definitions.sql` — 1 new table (`report_definitions`) + 1 `ALTER TABLE org_settings` (add `report_pack_status` JSONB column).

**Dependencies on prior phases**: Phase 5 (TimeEntry entity — Timesheet report queries time entries), Phase 6 (AuditService and AuditEventBuilder — REPORT_GENERATED and REPORT_EXPORTED events), Phase 8 (OrgSettings for branding, ReportRepository for profitability delegation), Phase 10 (Invoice entity — Invoice Aging report queries invoices), Phase 12 (PdfRenderingService — report rendering pipeline reuses `renderThymeleaf()` and `htmlToPdf()` directly).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 133 | ReportDefinition Entity Foundation | Backend | — | M | 133A, 133B | **Done** (PRs #283, #284) |
| 134 | Report Execution Framework + Timesheet Query | Backend | 133 | M | 134A, 134B | **Done** (PRs #285, #286) |
| 135 | Invoice Aging + Project Profitability Queries | Backend | 134 | M | 135A | **Done** (PR #287) |
| 136 | Rendering & Export Pipeline | Backend | 134 | M | 136A, 136B | |
| 137 | Reports Frontend | Frontend | 133, 134 | M | 137A, 137B | |

---

## Dependency Graph

```
[E133A V35 Migration + ReportDefinition Entity + Repo + OrgSettings extension]
                    |
[E133B StandardReportPackSeeder + GET /api/report-definitions + GET /api/report-definitions/{slug}]
                    |
          +---------+---------+
          |                   |
          v                   v
[E134A ReportQuery interface  [E137A API client + reports list page + sidebar nav]
 ReportResult record           (can start after 133B API is stable)
 ReportExecutionService]
          |
[E134B TimesheetReportQuery
 POST /execute endpoint
 REPORT_GENERATED audit]
          |
    +-----+------+----------+
    |            |          |
    v            v          v
[E135A      [E136A      [E137B
 InvoiceAging Rendering   Report detail page
 + Project    Service +   + ReportRunner +
 Profitability HTML prev  ReportParameterForm
 Queries]     + PDF exp]  + ReportResults +
                |          export buttons]
            [E136B
             CSV export]
```

**Parallel opportunities**:
- After Epic 133 completes: Epic 137A (API client + list page) can start in parallel with Epic 134.
- After Epic 134B completes: Epics 135A, 136A, and 136B are independent and can run in parallel — they add new query implementations and rendering modes that don't conflict.
- Epic 137B (detail page + interactive runner) requires 134B's execute endpoint to exist but can be scaffolded with mock data while 134B is in progress.

---

## Implementation Order

### Stage 1: Foundation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 133 | 133A | V35 migration + `ReportDefinition` entity + `ReportDefinitionRepository` + `OrgSettings.reportPackStatus` field. Foundation for all other slices. | **Done** (PR #283) |
| 1b | Epic 133 | 133B | `StandardReportPackSeeder` with all 3 definitions and templates + list/detail GET endpoints. Establishes the API surface that the frontend reads. | **Done** (PR #284) |

### Stage 2: Execution Framework (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 134 | 134A | `ReportQuery` interface + `ReportResult` record + `ReportExecutionService` with slug dispatch and startup validation. Core dispatch framework. | **Done** (PR #285) |
| 2b | Epic 134 | 134B | `TimesheetReportQuery` (all grouping modes) + `POST /api/report-definitions/{slug}/execute` endpoint + `REPORT_GENERATED` audit. First end-to-end executable report. | **Done** (PR #286) |

### Stage 3: Parallel Backend Tracks (After 134B)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a (parallel) | Epic 135 | 135A | `InvoiceAgingReportQuery` + `ProjectProfitabilityReportQuery`. Two query implementations that register themselves via `@Component` — no framework changes. Can run in parallel with 136A/136B. | **Done** (PR #287) |
| 3b (parallel) | Epic 136 | 136A | `ReportRenderingService` + HTML preview endpoint + PDF export endpoint + `REPORT_EXPORTED` audit. Can run in parallel with 135A. |
| 3c (parallel) | Epic 136 | 136B | CSV export: `writeCsv()` in `ReportRenderingService` + CSV export endpoint + RFC 4180 escaping. Depends on 136A (service exists), can run in parallel with 135A. |

### Stage 4: Frontend (After Stages 1–3)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 137 | 137A | API client functions (`lib/api/reports.ts`) + Reports list page (`/reports`) with category grouping + sidebar nav entry. Can start after 133B. |
| 4b | Epic 137 | 137B | Report detail page (`/reports/[reportSlug]`) + `ReportRunner` client component + `ReportParameterForm` + `ReportResults` + export buttons. Requires 134B execute endpoint. |

### Timeline

```
Stage 1:  [133A] --> [133B]
Stage 2:  [134A] --> [134B]
Stage 3:  [135A] // [136A] --> [136B]   (three parallel tracks)
Stage 4:  [137A] --> [137B]             (137A can start after 133B, 137B after 134B)
```

**Critical path**: 133A → 133B → 134A → 134B → 137A → 137B

---

## Epic 133: ReportDefinition Entity Foundation

**Goal**: Create the V35 database migration, the `ReportDefinition` JPA entity, `ReportDefinitionRepository`, the `OrgSettings.reportPackStatus` JSONB extension, the `StandardReportPackSeeder` with all three report definitions and their Thymeleaf templates, and the two read-only list/detail REST endpoints. No execution logic is included — this epic is the data foundation and catalog API.

**References**: Architecture doc Sections 19.2 (Domain Model), 19.5.1 (Report Definition Endpoints), 19.8 (Database Migration), 19.9 (Seed Pack). [ADR-082](../adr/ADR-082-report-template-storage.md).

**Dependencies**: None (foundation epic).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **133A** | 133.1–133.6 | V35 migration (1 new table + 1 ALTER on `org_settings`), `ReportDefinition` entity (all 11 fields, JSONB mappings, `updateTemplate()` method), `ReportDefinitionRepository` (with `findBySlug()`), `OrgSettings` extension (`reportPackStatus` JSONB field + `recordReportPackApplication()` method). ~5 files created/modified. | **Done** (PR #283) |
| **133B** | 133.7–133.14 | `StandardReportPackSeeder` seeding all 3 report definitions with full Thymeleaf templates and correct parameter/column JSON. `ReportingController` with `GET /api/report-definitions` (categorized list) and `GET /api/report-definitions/{slug}` (detail). `TenantProvisioningService` integration. `SecurityConfig` update. Integration tests (~8 tests). ~6 files created/modified. | **Done** (PR #284) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 133.1 | Create V35 migration | 133A | | `backend/src/main/resources/db/migration/tenant/V35__create_report_definitions.sql`. Creates `report_definitions` table with all 11 columns per architecture doc Section 19.8.1 (UUID PK with `gen_random_uuid()`, name VARCHAR(200) NOT NULL, slug VARCHAR(100) NOT NULL, description TEXT, category VARCHAR(50) NOT NULL, parameter_schema JSONB NOT NULL DEFAULT `'{"parameters":[]}'`, column_definitions JSONB NOT NULL DEFAULT `'{"columns":[]}'`, template_body TEXT NOT NULL, is_system BOOLEAN NOT NULL DEFAULT true, sort_order INTEGER NOT NULL DEFAULT 0, created_at and updated_at TIMESTAMPTZ NOT NULL). UNIQUE constraint `uq_report_definitions_slug` on slug. Indexes: `idx_report_definitions_category`, `idx_report_definitions_system`. Also: `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS report_pack_status JSONB`. Copy SQL verbatim from architecture doc Section 19.8.1–19.8.2. Pattern: `backend/src/main/resources/db/migration/tenant/V31__create_retainer_tables.sql`. |
| 133.2 | Create `ReportDefinition` entity | 133A | 133.1 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportDefinition.java`. All 11 fields per architecture doc Section 19.2.1. JSONB fields use `@JdbcTypeCode(SqlTypes.JSON)` with `Map<String, Object>`. `@Column(name = "template_body", nullable = false, columnDefinition = "TEXT")`. Include `updateTemplate(String templateBody)` method that sets `this.templateBody = templateBody; this.updatedAt = Instant.now()`. Protected no-arg constructor. Full constructor sets `isSystem = true`, `sortOrder = 0`, `createdAt = Instant.now()`, `updatedAt = Instant.now()`. Copy exact entity code from architecture doc Section 19.2.1. Pattern: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java`. |
| 133.3 | Create `ReportDefinitionRepository` | 133A | 133.2 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportDefinitionRepository.java`. `JpaRepository<ReportDefinition, UUID>`. Custom method: `Optional<ReportDefinition> findBySlug(String slug)`. Optional second method: `List<ReportDefinition> findAllByOrderByCategoryAscSortOrderAsc()` for deterministic list ordering. Pattern: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateRepository.java`. |
| 133.4 | Extend `OrgSettings` entity | 133A | 133.1 | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add field: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "report_pack_status", columnDefinition = "jsonb") private Map<String, Object> reportPackStatus`. Add method: `public void recordReportPackApplication(String packId, int version)` that stores `{"packId": packId, "version": version, "appliedAt": Instant.now().toString()}` into `this.reportPackStatus`. Follow the exact same pattern as `templatePackStatus` field and `recordTemplatePackApplication()` method already present on `OrgSettings`. |
| 133.5 | Create V35 migration verification test | 133A | 133.1–133.3 | `backend/src/test/java/.../reporting/V35MigrationTest.java`. Tests: migration applies cleanly; save and retrieve a `ReportDefinition` (verify JSONB fields round-trip); slug uniqueness constraint enforced (save duplicate slug → `DataIntegrityViolationException`); `org_settings.report_pack_status` is nullable. ~4 integration tests. |
| 133.6 | Create `ColumnDefinition` record | 133A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ColumnDefinition.java`. Java record: `public record ColumnDefinition(String key, String label, String type, String format)` with compact constructor and a convenience canonical `ColumnDefinition(String key, String label, String type)` that delegates with `format = null`. Used by `ReportRenderingService` (136A) — defining here ensures it's available to all slices. Copy from architecture doc Section 19.2.3. |
| 133.7 | Create `StandardReportPackSeeder` | 133B | 133A | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/StandardReportPackSeeder.java`. Constants: `PACK_ID = "standard-reports"`, `PACK_VERSION = 1`. Implement `seedForTenant(String tenantId, String orgId)` that uses `ScopedValue.where(RequestScopes.TENANT_ID, tenantId).run(() -> transactionTemplate.executeWithoutResult(tx -> doSeed()))`. `doSeed()` loads-or-creates `OrgSettings`, calls `isPackAlreadyApplied()` (checks if `reportPackStatus` map contains packId with correct version), calls `upsertReport()` for all 3 definitions, then calls `settings.recordReportPackApplication(PACK_ID, PACK_VERSION)`. `upsertReport()` uses `findBySlug().ifPresentOrElse(existing -> { existing.updateTemplate(...); repo.save(existing); }, () -> repo.save(def))`. Copy exact seeder code from architecture doc Section 19.9.1. Pattern: follow `TemplatePackSeeder.java` in `template/` package. |
| 133.8 | Define timesheet report definition constant | 133B | 133.7 | Private method `timesheetDefinition()` in `StandardReportPackSeeder`. Creates `ReportDefinition` with: name="Timesheet Report", slug="timesheet", category="TIME_ATTENDANCE", sortOrder=10. `parameterSchema` Map from architecture doc Section 19.2.2 (5 parameters: dateFrom, dateTo, groupBy, projectId, memberId). `columnDefinitions` Map from architecture doc Section 19.5.1 Timesheet column definitions (5 columns: groupLabel, totalHours, billableHours, nonBillableHours, entryCount). `templateBody` = full Thymeleaf HTML from architecture doc Section 19.9.2. |
| 133.9 | Define invoice aging report definition constant | 133B | 133.7 | Private method `invoiceAgingDefinition()` in `StandardReportPackSeeder`. Creates `ReportDefinition` with: name="Invoice Aging Report", slug="invoice-aging", category="FINANCIAL", sortOrder=10. `parameterSchema` with 2 parameters: asOfDate (date, required), customerId (uuid, optional, entityType="customer"). `columnDefinitions` from architecture doc Section 19.5.1 Invoice Aging column definitions (8 columns). `templateBody` = full Thymeleaf HTML from architecture doc Section 19.9.3. |
| 133.10 | Define project profitability report definition constant | 133B | 133.7 | Private method `projectProfitabilityDefinition()` in `StandardReportPackSeeder`. Creates `ReportDefinition` with: name="Project Profitability Report", slug="project-profitability", category="PROJECT", sortOrder=10. `parameterSchema` with 4 parameters: dateFrom (date, required), dateTo (date, required), projectId (uuid, optional, entityType="project"), customerId (uuid, optional, entityType="customer"). `columnDefinitions` from architecture doc Section 19.5.1 Project Profitability column definitions (8 columns). `templateBody` = full Thymeleaf HTML from architecture doc Section 19.9.4. |
| 133.11 | Create `ReportingController` (list + detail endpoints) | 133B | 133.3, 133.7 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportingController.java`. Two endpoints per architecture doc Section 19.5.1. `GET /api/report-definitions`: calls `findAllByOrderByCategoryAscSortOrderAsc()`, groups by category using `Collectors.groupingBy()`, maps to response shape `{categories: [{category, label, reports: [{slug, name, description}]}]}`. Category-to-label mapping: `TIME_ATTENDANCE` → "Time & Attendance", `FINANCIAL` → "Financial", `PROJECT` → "Project". `GET /api/report-definitions/{slug}`: calls `findBySlug()` (throws `ResourceNotFoundException` if absent), returns detail response excluding `templateBody` but including `parameterSchema`, `columnDefinitions`, `isSystem`. Auth: `@PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")` on both. Pattern: `InvoiceController.java`. |
| 133.12 | Update `SecurityConfig` | 133B | 133.11 | Add `/api/report-definitions/**` to authenticated endpoint list in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java`. Follow the exact pattern of existing entries (e.g., `/api/invoices/**`). |
| 133.13 | Integrate seeder into `TenantProvisioningService` | 133B | 133.7 | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`. Inject `StandardReportPackSeeder` and call `standardReportPackSeeder.seedForTenant(tenantId, orgId)` after `templatePackSeeder.seedForTenant()` (after migration runs). Follow the exact same call pattern as the existing `TemplatePackSeeder` integration. |
| 133.14 | Write seeder and controller integration tests | 133B | 133.11–133.13 | `backend/src/test/java/.../reporting/StandardReportPackSeederTest.java`: idempotent seeding (call twice → still 3 definitions, `reportPackStatus` has correct packId/version), all 3 definitions present with correct slugs/categories, `upsertReport` updates templateBody on re-seed. ~5 tests. `backend/src/test/java/.../reporting/ReportingControllerTest.java` (initial tests for list/detail only — more tests added in 134B): GET list returns 3 reports in 3 categories (200), GET list has correct category labels, GET detail by slug returns parameterSchema and columnDefinitions but not templateBody (200), GET detail unknown slug returns 404, both endpoints require authentication (401 without JWT). ~5 tests. |

### Key Files

**Slice 133A — Create:**
- `backend/src/main/resources/db/migration/tenant/V35__create_report_definitions.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportDefinition.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportDefinitionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ColumnDefinition.java`
- `backend/src/test/java/.../reporting/V35MigrationTest.java`

**Slice 133A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — add `reportPackStatus` field and `recordReportPackApplication()` method

**Slice 133B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/StandardReportPackSeeder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportingController.java`
- `backend/src/test/java/.../reporting/StandardReportPackSeederTest.java`
- `backend/src/test/java/.../reporting/ReportingControllerTest.java`

**Slice 133B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` — add `/api/report-definitions/**`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — call `StandardReportPackSeeder.seedForTenant()`

**Read for context:**
- `architecture/phase19-reporting-data-export.md` Sections 19.2, 19.5.1, 19.8, 19.9
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` — entity with JSONB fields pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateRepository.java` — repository pattern with `findBySlug()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` — seeder pattern (idempotent, OrgSettings tracking)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — entity to extend (follow `templatePackStatus` pattern)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — where to add seeder call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` — REST controller with `@PreAuthorize` pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` — endpoint security config
- `backend/src/main/resources/db/migration/tenant/V31__create_retainer_tables.sql` — migration file format reference

### Architecture Decisions

- **Package `reporting/`**: New top-level feature package (`io.b2mash.b2b.b2bstrawman.reporting`). Distinct from `report/` (Phase 8 profitability). Naming deliberately pluralized to avoid single-letter ambiguity.
- **`ColumnDefinition` record in 133A**: Defined in the entity foundation slice because it is a shared data type referenced by both the rendering service (136A) and the seeder (133B). Placing it in 133A ensures all downstream slices have a clean import without circular dependencies.
- **`reportPackStatus` as JSONB on `OrgSettings`**: Follows `templatePackStatus` (Phase 12) — same entity, same field pattern, same `recordPackApplication()` method shape. Enables idempotent re-seeding and future version upgrades without a separate tracking table.
- **`findAllByOrderByCategoryAscSortOrderAsc()`**: Deterministic ordering in the repository eliminates the need for service-layer sorting. The category-to-label mapping is a controller concern (presentation), not a persistence concern.
- **`templateBody` excluded from list/detail responses**: The list response is a catalog (slug, name, description, category). The detail response provides `parameterSchema` and `columnDefinitions` for form rendering. `templateBody` is only needed server-side during rendering — exposing it to clients leaks internal template code unnecessarily.

---

## Epic 134: Report Execution Framework + Timesheet Query

**Goal**: Establish the strategy-based dispatch framework (`ReportQuery` interface, `ReportResult` record, `ReportExecutionService`) and implement the first concrete report (`TimesheetReportQuery`) end-to-end through the `POST /api/report-definitions/{slug}/execute` endpoint with audit logging. After this epic, the full JSON execution pipeline is working for the Timesheet report.

**References**: Architecture doc Sections 19.3 (Report Execution Framework), 19.3.5 (Timesheet Report Query), 19.5.2 (Execution Endpoints), 19.10 (Audit Integration). [ADR-081](../adr/ADR-081-report-query-strategy-pattern.md), [ADR-084](../adr/ADR-084-parameter-schema-design.md).

**Dependencies**: Epic 133 (entity, repository, OrgSettings extension, list/detail endpoints).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **134A** | 134.1–134.6 | `ReportQuery` strategy interface, `ReportResult` record, `ReportExecutionService` (slug-based dispatch, startup validation, `execute()` + `executeForExport()` methods, audit integration). No concrete query implementations yet. ~3 files created. | **Done** (PR #285) |
| **134B** | 134.7–134.16 | `TimesheetReportQuery` with all three grouping modes (member/project/date), optional filters (projectId, memberId), date filtering, billable/non-billable split, summary computation. `POST /api/report-definitions/{slug}/execute` controller endpoint added to `ReportingController`. `REPORT_GENERATED` audit event. Integration tests for query and execute endpoint (~16 tests). ~3 files created/modified. | **Done** (PR #286) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 134.1 | Create `ReportQuery` interface | 134A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportQuery.java`. Public interface with 3 methods: `String getSlug()`, `ReportResult execute(Map<String, Object> parameters, Pageable pageable)`, `ReportResult executeAll(Map<String, Object> parameters)`. Copy exact interface from architecture doc Section 19.3.1. No Spring annotations on the interface itself. |
| 134.2 | Create `ReportResult` record | 134A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportResult.java`. Public record: `record ReportResult(List<Map<String, Object>> rows, Map<String, Object> summary, long totalElements, int totalPages)`. Convenience constructor `ReportResult(List<Map<String, Object>> rows, Map<String, Object> summary)` that delegates with `rows.size()` for totalElements and `1` for totalPages. Copy exact record from architecture doc Section 19.3.2. |
| 134.3 | Create `ReportExecutionService` — core dispatch | 134A | 134.1, 134.2 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportExecutionService.java`. Constructor injects `List<ReportQuery> queries`, `ReportDefinitionRepository`, `AuditService`. Build `Map<String, ReportQuery> queryMap` using `queries.stream().collect(Collectors.toMap(ReportQuery::getSlug, Function.identity()))`. CRITICAL: Add startup validation — if two beans return same slug, throw `IllegalStateException("Duplicate ReportQuery slug: " + slug)`. Per ADR-081. |
| 134.4 | Implement `execute()` method on `ReportExecutionService` | 134A | 134.3 | Implement `@Transactional(readOnly = true) ReportExecutionResponse execute(String slug, Map<String, Object> parameters, Pageable pageable)`. Steps: (1) `findBySlug()` → throws `ResourceNotFoundException` if absent, (2) lookup queryMap → throws `InvalidStateException` if no implementation, (3) call `query.execute(parameters, pageable)`, (4) audit `REPORT_GENERATED` with format="preview" + rowCount, (5) return `toResponse(definition, parameters, result, pageable)`. `ReportExecutionResponse` is an inner record or separate file: `{String reportName, Map<String,Object> parameters, String generatedAt, List<ColumnDefinition> columns, List<Map<String,Object>> rows, Map<String,Object> summary, PaginationInfo pagination}`. See architecture doc Section 19.5.2. |
| 134.5 | Implement `executeForExport()` method on `ReportExecutionService` | 134A | 134.3 | `@Transactional(readOnly = true) ReportResult executeForExport(String slug, Map<String, Object> parameters)`. Steps: (1) `findBySlug()` → throws `ResourceNotFoundException`, (2) lookup queryMap → throws `InvalidStateException`, (3) call `query.executeAll(parameters)`. Note: audit logging for export is done in the controller (after rendering completes) because the controller knows the format (pdf/csv). No audit event here. Copy method from architecture doc Section 19.3.3. |
| 134.6 | Create `ReportExecutionResponse` response record | 134A | 134.4 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportExecutionResponse.java`. Record with fields: `String reportName`, `Map<String, Object> parameters`, `String generatedAt`, `List<ColumnDefinition> columns`, `List<Map<String, Object>> rows`, `Map<String, Object> summary`, nested `PaginationInfo` record (`int page`, `int size`, `long totalElements`, `int totalPages`). Shape matches architecture doc Section 19.5.2 execute response. |
| 134.7 | Create `TimesheetReportQuery` — skeleton and parameter parsing | 134B | 134A | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/TimesheetReportQuery.java`. `@Component`. `@Override public String getSlug() { return "timesheet"; }`. Add `EntityManager` injection for native queries. Private helper methods: `parseDate(Map<String, Object> params, String key) → LocalDate`, `parseUuid(Map<String, Object> params, String key) → UUID` (returns null if absent/null). Per architecture doc Section 19.3.5. |
| 134.8 | Implement timesheet SQL for member grouping | 134B | 134.7 | In `TimesheetReportQuery`. Native SQL query from architecture doc Section 19.3.5 grouped by `m.id, m.name`. Use `EntityManager.createNativeQuery(sql)`. Parameters bound with `CAST(:dateFrom AS DATE)` and `CAST(:dateTo AS DATE)` pattern (from existing codebase lessons — avoids PostgreSQL type inference failure on nullable params). `CAST(:projectId AS UUID) IS NULL OR t.project_id = CAST(:projectId AS UUID)` for optional filter. Returns list of `Object[]` rows, map each to `Map<String, Object>` with keys: memberName, totalHours, billableHours, nonBillableHours, entryCount. `groupLabel` = memberName. |
| 134.9 | Implement timesheet SQL for project and date grouping | 134B | 134.8 | In `TimesheetReportQuery`. Two additional query variants. Project grouping: `GROUP BY t.project_id, p.name` (JOIN projects p). Date grouping: `GROUP BY te.date ORDER BY te.date`. `getSlugGroupBy()` private method switches SQL string based on `parameters.get("groupBy")` value (`"member"`, `"project"`, `"date"`). Default to member grouping if groupBy absent. `groupLabel` key maps to member name, project name, or date string respectively. |
| 134.10 | Implement `execute()` with pagination | 134B | 134.9 | In `TimesheetReportQuery`. `execute(Map<String, Object> parameters, Pageable pageable)`: run the full query to get all rows, then apply pagination manually (`rows.subList(offset, Math.min(offset + size, rows.size()))`). Return `ReportResult(pagedRows, summary, totalRows, totalPages)`. Manual in-memory pagination is correct for this scale — report datasets are bounded by the tenant's data. |
| 134.11 | Implement `executeAll()` | 134B | 134.9 | `executeAll(Map<String, Object> parameters)`: same query without pagination. Returns full dataset for export. Returns `ReportResult(allRows, summary)` using the convenience constructor. |
| 134.12 | Implement summary computation for timesheet | 134B | 134.10 | Private `computeSummary(List<Map<String, Object>> rows)` in `TimesheetReportQuery`. Aggregates: `totalHours` = sum of row totalHours (using `rows.stream().mapToDouble(r -> ((Number)r.get("totalHours")).doubleValue()).sum()`), `billableHours`, `nonBillableHours`, `entryCount`. Returns `Map<String, Object>` with these 4 keys. |
| 134.13 | Add `POST /execute` endpoint to `ReportingController` | 134B | 134.6 | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportingController.java`. Add: `POST /api/report-definitions/{slug}/execute`. Request body: `record ExecuteReportRequest(Map<String, Object> parameters, int page, int size)`. Construct `PageRequest.of(request.page(), request.size())`. Delegate to `reportExecutionService.execute(slug, parameters, pageable)`. Auth: `@PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")`. Return 200 with `ReportExecutionResponse`. |
| 134.14 | Write `TimesheetReportQueryTest` integration tests | 134B | 134.12 | `backend/src/test/java/.../reporting/TimesheetReportQueryTest.java`. Requires: create member, project, time entries spanning a date range. Tests: groupBy=member returns aggregated rows by member; groupBy=project returns aggregated rows by project; groupBy=date returns one row per date; dateFrom/dateTo filter correctly (entries on boundary dates included); projectId filter returns only entries for that project; memberId filter returns only entries for that member; empty date range returns empty rows with zeroed summary; billableHours vs nonBillableHours split is correct. ~9 integration tests using `ScopedValue.where(RequestScopes.TENANT_ID, schema).run(() -> { ... })`. |
| 134.15 | Write `ReportExecutionServiceTest` integration tests | 134B | 134.4, 134.5 | `backend/src/test/java/.../reporting/ReportExecutionServiceTest.java`. Tests: execute with valid slug dispatches to correct query; execute with unknown slug throws `ResourceNotFoundException` (definition not in DB); execute with slug in DB but no `@Component` implementation throws `InvalidStateException`; `REPORT_GENERATED` audit event is persisted after execute; duplicate slug in query list causes `IllegalStateException` at startup (test this via a test-only `@Component`). ~5 integration tests. |
| 134.16 | Extend `ReportingControllerTest` — execute endpoint | 134B | 134.13 | Add to `backend/src/test/java/.../reporting/ReportingControllerTest.java` (file created in 133B). Tests: POST execute with valid slug and parameters returns 200 with correct shape (rows, summary, pagination, reportName); POST execute with unknown slug returns 404; POST execute without authentication returns 401; POST execute with page/size parameters returns correct page slice. ~4 tests. |

### Key Files

**Slice 134A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportQuery.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportExecutionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportExecutionResponse.java`

**Slice 134B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/TimesheetReportQuery.java`
- `backend/src/test/java/.../reporting/TimesheetReportQueryTest.java`
- `backend/src/test/java/.../reporting/ReportExecutionServiceTest.java`

**Slice 134B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportingController.java` — add POST /execute endpoint

**Read for context:**
- `architecture/phase19-reporting-data-export.md` Sections 19.3, 19.3.5, 19.5.2, 19.10
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` — pattern for collecting `List<TemplateContextBuilder>` via constructor injection (same dispatch pattern)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — audit event logging
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` — builder pattern for audit events
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportRepository.java` — existing native SQL aggregation pattern (EntityManager-based)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` — time entry entity (fields: date, durationMinutes, billable, memberId, taskId)
- `backend/CLAUDE.md` — `CAST(:param AS DATE)` pattern for nullable date params in native queries

### Architecture Decisions

- **`execute()` returns `ReportExecutionResponse`, `executeForExport()` returns `ReportResult`**: The execute endpoint needs a rich response (columns metadata, pagination, reportName) for the frontend table. The export endpoints only need the raw rows and summary to feed the rendering pipeline. Keeping these separate avoids enriching export responses unnecessarily.
- **Startup validation for duplicate slugs**: `IllegalStateException` thrown in the `ReportExecutionService` constructor if two `@Component` beans share a slug. This fails fast during application startup (before any request), making the bug immediately visible in logs. Per ADR-081.
- **Manual in-memory pagination for query results**: Native SQL result sets are loaded in full, then sliced in Java. This is intentional for this scale — reports aggregate across a bounded tenant dataset, and applying LIMIT/OFFSET to aggregation queries is complex when the grouping dimension may change. The full dataset is typically dozens to hundreds of rows, not millions.
- **`CAST(:param AS DATE)` for nullable date parameters**: Consistent with codebase lesson — PostgreSQL cannot infer the type of nullable prepared statement parameters. Use `CAST(:dateFrom AS DATE) IS NULL OR te.date >= CAST(:dateFrom AS DATE)` pattern from `TimeEntry` native queries.
- **Audit format="preview" for JSON execute**: The execute endpoint (JSON response) uses format="preview" in the audit detail. The rendering endpoints (HTML/PDF/CSV) set their specific formats. This allows the audit log to distinguish between data access and formatted export.

---

## Epic 135: Invoice Aging + Project Profitability Queries

**Goal**: Add the two remaining `ReportQuery` implementations: `InvoiceAgingReportQuery` (with bucket classification, age band mapping, and customer filter) and `ProjectProfitabilityReportQuery` (which delegates to the existing `ReportRepository` for revenue/cost data and reshapes results). Both register automatically via `@Component` — no framework changes required. After this epic, all three standard reports are executable.

**References**: Architecture doc Sections 19.3.6 (Invoice Aging Query), 19.3.7 (Project Profitability Query), 19.3.4 (Integration with existing `report/` package). [ADR-081](../adr/ADR-081-report-query-strategy-pattern.md).

**Dependencies**: Epic 134 (dispatch framework + `ReportQuery` interface + `ReportResult` record).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **135A** | 135.1–135.12 | `InvoiceAgingReportQuery` (native SQL with age buckets, bucket-to-label mapping, summary aggregation, customer filter), `ProjectProfitabilityReportQuery` (delegation to `ReportRepository.getOrgProjectRevenue()` + `getOrgProjectCost()`, row merging, margin computation, optional project/customer filter). Integration tests for both queries (~15 tests). ~4 files created. | **Done** (PR #287) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 135.1 | Create `InvoiceAgingReportQuery` — skeleton and parameter parsing | 135A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/InvoiceAgingReportQuery.java`. `@Component`. `@Override public String getSlug() { return "invoice-aging"; }`. Inject `EntityManager`. Private helpers: `parseDate(params, key) → LocalDate`, `parseUuid(params, key) → UUID`. |
| 135.2 | Implement invoice aging native SQL | 135A | 135.1 | In `InvoiceAgingReportQuery`. Native SQL from architecture doc Section 19.3.6. Key elements: `CAST(:asOfDate AS DATE) - i.due_date AS days_overdue`, CASE expression for `age_bucket` (CURRENT, 1_30, 31_60, 61_90, 90_PLUS), `WHERE i.status IN ('SENT', 'OVERDUE')`, optional `CAST(:customerId AS UUID) IS NULL OR i.customer_id = CAST(:customerId AS UUID)`. Result rows are `Object[]` — map to `Map<String, Object>` with keys: invoiceId, invoiceNumber, customerName, issueDate, dueDate, amount, currency, status, daysOverdue, ageBucket (raw), ageBucketLabel (mapped). |
| 135.3 | Implement age bucket label mapping | 135A | 135.2 | Private `mapBucketLabel(String bucket)` in `InvoiceAgingReportQuery`. Maps: `"CURRENT"` → `"Current"`, `"1_30"` → `"1-30 Days"`, `"31_60"` → `"31-60 Days"`, `"61_90"` → `"61-90 Days"`, `"90_PLUS"` → `"90+ Days"`. Store `ageBucketLabel` (display) in the row map — this is what the frontend table and CSV export use. Per architecture doc Section 19.3.6. |
| 135.4 | Implement invoice aging summary computation | 135A | 135.3 | Private `computeSummary(List<Map<String, Object>> rows)` in `InvoiceAgingReportQuery`. Aggregates per bucket: `currentCount`, `currentAmount`, `bucket1_30Count`, `bucket1_30Amount`, `bucket31_60Count`, `bucket31_60Amount`, `bucket61_90Count`, `bucket61_90Amount`, `bucket90PlusCount`, `bucket90PlusAmount`. Plus overall `totalAmount` (sum of all amounts) and `totalCount`. Use `rows.stream()` with `groupingBy` on `ageBucket`, then reduce. Return `Map<String, Object>` with these 12 keys. |
| 135.5 | Implement `execute()` and `executeAll()` for invoice aging | 135A | 135.4 | In `InvoiceAgingReportQuery`. `execute()`: run query, compute summary, apply in-memory pagination. `executeAll()`: same without pagination. Standard pattern following `TimesheetReportQuery`. |
| 135.6 | Write `InvoiceAgingReportQueryTest` integration tests | 135A | 135.5 | `backend/src/test/java/.../reporting/InvoiceAgingReportQueryTest.java`. Requires: create customer, invoices with status SENT and OVERDUE, varying due dates. Tests: invoice with due date in future returns CURRENT bucket; invoice overdue 15 days returns 1-30 bucket; invoice overdue 45 days returns 31-60 bucket; invoice overdue 75 days returns 61-90 bucket; invoice overdue 100 days returns 90+ bucket; summary counts and amounts are correct per bucket; customer filter returns only invoices for that customer; DRAFT/PAID invoices excluded from results; empty result (no outstanding invoices) returns empty rows with zeroed summary. ~9 integration tests. |
| 135.7 | Create `ProjectProfitabilityReportQuery` — skeleton and parameter parsing | 135A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ProjectProfitabilityReportQuery.java`. `@Component`. `@Override public String getSlug() { return "project-profitability"; }`. Inject `ReportRepository` (from `report/` package) and `ProjectRepository`. Private helpers: `parseDate(params, key) → LocalDate`, `parseUuid(params, key) → UUID`. |
| 135.8 | Implement profitability data delegation | 135A | 135.7 | In `ProjectProfitabilityReportQuery`. Call `reportRepository.getOrgProjectRevenue(from, to, customerId)` and `reportRepository.getOrgProjectCost(from, to, customerId)` — these already exist in the Phase 8 `ReportRepository`. Per architecture doc Section 19.3.7. Cross-reference signature of these methods in `report/ReportRepository.java` before writing. Return their projection types. |
| 135.9 | Implement `mergeToRows()` method | 135A | 135.8 | Private `mergeToRows(List<?> revenueList, List<?> costList, UUID projectId)` in `ProjectProfitabilityReportQuery`. Zip revenue and cost by project ID (use a `Map<UUID, Map<String, Object>>` keyed by projectId). For each project: extract projectName, customerName, currency, billableHours, revenue, cost. Compute margin = revenue - cost. Compute marginPercent = (cost > 0) ? (margin / revenue) * 100 : 0. Apply optional `projectId` filter — if non-null, filter to just that project. Each output row is `Map<String, Object>` with keys: projectName, customerName, currency, billableHours, revenue, cost, margin, marginPercent. Use `BigDecimal` arithmetic for precision. |
| 135.10 | Implement profitability summary computation | 135A | 135.9 | Private `computeSummary(List<Map<String, Object>> rows)` in `ProjectProfitabilityReportQuery`. Aggregates: totalBillableHours, totalRevenue, totalCost, totalMargin. `avgMarginPercent = totalRevenue > 0 ? (totalMargin / totalRevenue) * 100 : 0`. Return `Map<String, Object>` with 5 keys. |
| 135.11 | Implement `execute()` and `executeAll()` for profitability | 135A | 135.10 | Standard pagination pattern following `TimesheetReportQuery`. `execute()` paginates in-memory after merging. `executeAll()` returns all rows. |
| 135.12 | Write `ProjectProfitabilityReportQueryTest` integration tests | 135A | 135.11 | `backend/src/test/java/.../reporting/ProjectProfitabilityReportQueryTest.java`. Requires: create members, projects, customers, billing rates, cost rates, time entries (billable). Tests: margin computed correctly (revenue - cost); marginPercent computed correctly; summary totalRevenue/totalCost/totalMargin correct; projectId filter returns only that project's row; customerId filter returns only projects for that customer; project with no revenue has margin = 0 or negative; project with no cost has margin = revenue. ~7 integration tests. Note: this test requires the Phase 8 billing rate and cost rate infrastructure — use existing `TestCustomerFactory` and create billing rates as needed. |

### Key Files

**Slice 135A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/InvoiceAgingReportQuery.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ProjectProfitabilityReportQuery.java`
- `backend/src/test/java/.../reporting/InvoiceAgingReportQueryTest.java`
- `backend/src/test/java/.../reporting/ProjectProfitabilityReportQueryTest.java`

**Read for context:**
- `architecture/phase19-reporting-data-export.md` Sections 19.3.6, 19.3.7, 19.3.4
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/TimesheetReportQuery.java` — direct pattern to follow (created in 134B)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportRepository.java` — existing methods to delegate to (`getOrgProjectRevenue`, `getOrgProjectCost`) — READ SIGNATURES before writing 135.8
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java` — `mergeToCurrencyBreakdowns()` method for row merging pattern (margin computation)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` — invoice entity field names (invoiceNumber, customerName, issueDate, dueDate, total, currency, status)
- `backend/CLAUDE.md` — `CAST(:param AS DATE)` pattern, `CAST(:param AS UUID)` pattern

### Architecture Decisions

- **Single slice for two queries**: Both queries are independent `@Component` beans. Each is approximately 100–150 lines of Java + test file. Combining them into one slice is safe because they don't share state — they are parallel implementations of the same interface. Splitting would create two half-sized slices without clear benefit.
- **`ProjectProfitabilityReportQuery` delegates to `ReportRepository`**: The Phase 8 `ReportRepository` contains complex revenue/cost aggregation SQL that took considerable effort to develop and test. Reusing it via delegation avoids duplicating this complexity. The report query only adds the merging/pagination/summary layer. Per architecture doc Section 19.3.7 and 19.3.4.
- **Age bucket computation in application layer**: The CASE expression in SQL produces `age_bucket` strings. Label mapping (`"1_30"` → `"1-30 Days"`) is a presentation concern handled in Java, not SQL. This keeps the SQL readable and makes label changes require no query modifications.
- **`BigDecimal` arithmetic for profitability**: Revenue, cost, and margin are monetary values. Using `double` division for marginPercent would introduce floating-point errors. Use `BigDecimal.divide()` with `RoundingMode.HALF_UP` and appropriate scale. Cast to `double` only when placing into the row `Map<String, Object>` for JSON serialization compatibility.

---

## Epic 136: Rendering & Export Pipeline

**Goal**: Implement `ReportRenderingService` (which delegates to the existing `PdfRenderingService`) and add the HTML preview, PDF export, and CSV export endpoints to `ReportingController`. After this epic, all three export formats are available for any executable report.

**References**: Architecture doc Sections 19.4 (Rendering & Export Pipeline), 19.5.2 (Export Endpoints), 19.10 (Audit Integration). [ADR-083](../adr/ADR-083-csv-generation-approach.md).

**Dependencies**: Epic 134 (execution framework — `ReportExecutionService.executeForExport()` must exist).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **136A** | 136.1–136.9 | `ReportRenderingService` with `buildContext()`, `renderHtml()`, `renderPdf()`. `GET /api/report-definitions/{slug}/preview` (HTML) and `GET /api/report-definitions/{slug}/export/pdf` (PDF download) endpoints. `REPORT_EXPORTED` audit event. Content-Disposition filename generation. Integration tests for HTML and PDF (~8 tests). ~3 files created/modified. |  |
| **136B** | 136.10–136.16 | Add `writeCsv()` to `ReportRenderingService`. `GET /api/report-definitions/{slug}/export/csv` endpoint. RFC 4180 escaping, metadata header rows, streaming to `ServletOutputStream`. Integration tests for CSV (~7 tests). ~1 file modified. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 136.1 | Create `ReportRenderingService` — skeleton and dependencies | 136A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportRenderingService.java`. `@Service`. Inject `PdfRenderingService`, `OrgSettingsRepository`, `ReportDefinitionRepository`. Import `ColumnDefinition` (from 133A). |
| 136.2 | Implement `buildContext()` method | 136A | 136.1 | Private `Map<String, Object> buildContext(ReportDefinition definition, ReportResult result, Map<String, Object> parameters)`. Steps: load `OrgSettings` via `orgSettingsRepository.findForCurrentTenant().orElse(null)`. Assemble context map with keys: `report` (Map with name, description), `parameters`, `rows`, `summary`, `generatedAt` (pre-formatted String using `DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneOffset.UTC)`), `columns` (parsed from `getColumns(definition)`). If settings non-null and has branding: add `branding` map with `logoS3Key`, `brandColor`, `footerText`. Copy exactly from architecture doc Section 19.4.1. |
| 136.3 | Implement `getColumns()` helper | 136A | 136.2 | Private `List<ColumnDefinition> getColumns(ReportDefinition definition)`. Parses `definition.getColumnDefinitions()` — cast `columns` list, map each entry to `new ColumnDefinition(key, label, type, format)`. Copy from architecture doc Section 19.2.3. Note: `@SuppressWarnings("unchecked")` required. |
| 136.4 | Implement `renderHtml()` method | 136A | 136.2, 136.3 | `public String renderHtml(ReportDefinition definition, ReportResult result, Map<String, Object> parameters)`. Calls `buildContext()`, then `pdfRenderingService.renderThymeleaf(definition.getTemplateBody(), context)`. Returns rendered HTML string. Note from architecture doc Section 19.4.1: report templates are full HTML documents (not fragments), so no `wrapHtml()` call needed — unlike document templates. |
| 136.5 | Implement `renderPdf()` method | 136A | 136.4 | `public byte[] renderPdf(ReportDefinition definition, ReportResult result, Map<String, Object> parameters)`. Calls `renderHtml()` then `pdfRenderingService.htmlToPdf(html)`. Returns byte array. |
| 136.6 | Implement `generateFilename()` helper | 136A | | Private `String generateFilename(String slug, Map<String, Object> parameters, String extension)`. If parameters contain `dateFrom` and `dateTo`: `{slug}-{dateFrom}-to-{dateTo}.{ext}`. If parameters contain `asOfDate`: `{slug}-{asOfDate}.{ext}`. Otherwise: `{slug}.{ext}`. Per architecture doc Section 19.4.3. |
| 136.7 | Add HTML preview endpoint to `ReportingController` | 136A | 136.4, 136.6 | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportingController.java`. Add `GET /api/report-definitions/{slug}/preview`. Accepts parameters as `@RequestParam Map<String, Object> parameters`. Steps: (1) load definition via `reportDefinitionRepository.findBySlug()` (throws 404 if absent), (2) call `reportExecutionService.execute(slug, params, PageRequest.of(0, 50))` (paginated to 50 rows for preview), (3) call `reportRenderingService.renderHtml()`, (4) return `ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html)`. No audit event for preview (only for execute and export). Auth: MEMBER+. |
| 136.8 | Add PDF export endpoint to `ReportingController` | 136A | 136.5, 136.6 | Modify `ReportingController`. Add `GET /api/report-definitions/{slug}/export/pdf`. Full dataset (calls `executeForExport()`). Steps: (1) load definition, (2) call `executeForExport()`, (3) call `renderPdf()`, (4) audit `REPORT_EXPORTED` with format="pdf" + rowCount, (5) return `ResponseEntity` with `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="..."` (use `generateFilename(slug, parameters, "pdf")`). Return `byte[]` body. Auth: MEMBER+. |
| 136.9 | Write HTML and PDF rendering integration tests | 136A | 136.7, 136.8 | `backend/src/test/java/.../reporting/ReportRenderingServiceTest.java`: Tests: `renderHtml()` returns non-empty string containing report name; HTML contains branding color when OrgSettings has brandColor; HTML contains org footer text; HTML omits branding block when no OrgSettings. `backend/src/test/java/.../reporting/ReportingControllerTest.java` additions: GET preview with valid slug + params returns 200 with `Content-Type: text/html`; GET preview with unknown slug returns 404; GET export/pdf returns 200 with `Content-Type: application/pdf` and `Content-Disposition: attachment; filename=...`; REPORT_EXPORTED audit event persisted after PDF export. ~8 tests total. |
| 136.10 | Implement `writeCsv()` method | 136B | 136.3 | In `ReportRenderingService`. Add `public void writeCsv(ReportDefinition definition, ReportResult result, Map<String, Object> parameters, OutputStream outputStream) throws IOException`. Steps: wrap in `BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))`. Write 3 metadata comment lines: `# {reportName}`, `# Generated: {Instant.now()}`, `# Parameters: {formatted params}`. Write column header row (labels joined by comma, each escaped). Write each data row (values by column key, formatted and escaped). Call `writer.flush()`. Copy exactly from architecture doc Section 19.4.1. |
| 136.11 | Implement `escapeCsv()` helper | 136B | 136.10 | Private `String escapeCsv(String value)` in `ReportRenderingService`. If value is null: return `""`. If value contains comma, double-quote, or newline: wrap in double quotes, escape internal double quotes as `""` (i.e., `value.replace("\"", "\"\"")` then wrap with `"\"" + escaped + "\""`). Otherwise return value as-is. RFC 4180 compliant. Per ADR-083. |
| 136.12 | Implement `formatValue()` helper | 136B | 136.11 | Private `String formatValue(Object value, String type, String format)` in `ReportRenderingService`. Cases: value null → return `""`. type `decimal` with format → format as decimal (use `String.format("%.Xf", value)` or `BigDecimal` scale based on format string "0.00"). type `integer` → `String.valueOf(((Number)value).longValue())`. type `date` → `value.toString()` (already String or LocalDate.toString()). type `currency` with format → same as decimal. type `string` → `value.toString()`. Default → `value.toString()`. |
| 136.13 | Implement `formatParametersForCsv()` helper | 136B | | Private `String formatParametersForCsv(Map<String, Object> parameters)`. Joins non-null parameter entries as `key=value` pairs separated by `; `. Used in metadata header row. |
| 136.14 | Add CSV export endpoint to `ReportingController` | 136B | 136.10 | Modify `ReportingController`. Add `GET /api/report-definitions/{slug}/export/csv`. Full dataset (`executeForExport()`). Inject `HttpServletResponse`. Steps: (1) load definition, (2) call `executeForExport()`, (3) set response `Content-Type: text/csv; charset=UTF-8` and `Content-Disposition: attachment; filename="{generateFilename(slug, params, 'csv')}"`, (4) call `reportRenderingService.writeCsv(definition, result, parameters, response.getOutputStream())`, (5) audit `REPORT_EXPORTED` with format="csv" + rowCount. Auth: MEMBER+. Note: set headers BEFORE writing to stream — cannot change headers after body write starts. |
| 136.15 | Write CSV integration tests | 136B | 136.14 | In `ReportRenderingServiceTest`. Tests: `writeCsv()` produces correct header columns in order; data rows contain correct values; null values render as empty string; values containing commas are double-quoted (e.g., customer name "Acme, Inc" → `"Acme, Inc"`); values containing double-quotes are escaped (value `He said "hi"` → `"He said ""hi""`); metadata lines start with `#`. In `ReportingControllerTest`: GET export/csv returns 200 with `Content-Type: text/csv; charset=UTF-8`; Content-Disposition header has correct filename format; REPORT_EXPORTED audit event with format=csv persisted. ~7 tests. |
| 136.16 | Verify all 136 tests pass | 136B | 136.9, 136.15 | Run: `./mvnw test -pl backend -Dtest="ReportRenderingServiceTest,ReportingControllerTest" -q`. All tests pass. Verify no regressions in `StandardReportPackSeederTest`, `TimesheetReportQueryTest`. |

### Key Files

**Slice 136A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportRenderingService.java`
- `backend/src/test/java/.../reporting/ReportRenderingServiceTest.java`

**Slice 136A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportingController.java` — add preview + PDF export endpoints

**Slice 136B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportRenderingService.java` — add `writeCsv()`, `escapeCsv()`, `formatValue()`, `formatParametersForCsv()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportingController.java` — add CSV export endpoint

**Read for context:**
- `architecture/phase19-reporting-data-export.md` Sections 19.4, 19.5.2, 19.10
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/PdfRenderingService.java` — `renderThymeleaf()` and `htmlToPdf()` signatures (these are called directly)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsRepository.java` — `findForCurrentTenant()` method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` — builder pattern for REPORT_EXPORTED event
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` — PDF download pattern (byte[] + Content-Disposition header)

### Architecture Decisions

- **Split into 136A (HTML+PDF) and 136B (CSV)**: HTML preview and PDF export are tightly coupled — PDF calls `renderHtml()`. CSV is a completely independent code path (no Thymeleaf, no PDF engine). Splitting keeps each slice under 10 files and ~400 lines of new code.
- **`renderHtml()` does NOT call `wrapHtml()`**: Document templates in Phase 12 are HTML body fragments that `PdfRenderingService.wrapHtml()` wraps with a full HTML shell. Report templates (seeded in 133B) are already full HTML documents with `<html>`, `<head>`, `<style>`, `<body>`. No wrapping needed. This is documented in the architecture doc Section 19.4.1 inline comment.
- **No S3 upload for PDF reports**: Phase 12 tracks generated documents in a `GeneratedDocument` entity and uploads PDFs to S3. Reports are ephemeral — they represent a point-in-time aggregation and are cheap to re-generate. Streaming directly to the response avoids S3 storage costs and `GeneratedDocument` tracking complexity. The audit trail via `AuditEvent` (REPORT_EXPORTED) is sufficient. Per architecture doc Section 19.4.3.
- **CSV: set headers before `writeCsv()` call**: `HttpServletResponse` headers must be set before the response body writing begins. Once `writeCsv()` starts writing to `getOutputStream()`, the response is committed and headers cannot be modified.
- **`formatValue()` type dispatch**: Using a `switch` or `if-chain` on the `type` field from `ColumnDefinition`. Using `BigDecimal` or `String.format()` for decimal formatting avoids `double` precision issues in CSV output.

---

## Epic 137: Reports Frontend

**Goal**: Build the Reports section of the frontend: a sidebar navigation entry, a reports list page grouped by category, and a dynamic report detail page with an interactive parameter form, results table, summary cards, and export buttons. The detail page uses a server component shell (fetches report definition) and a client component (`ReportRunner`) for the interactive execution loop.

**References**: Architecture doc Sections 19.7 (Frontend Design), 19.5.1–19.5.2 (API responses). [ADR-084](../adr/ADR-084-parameter-schema-design.md) (parameter schema drives form rendering).

**Dependencies**: Epic 133 (list/detail endpoints must be live), Epic 134 (execute endpoint must be live for 137B).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **137A** | 137.1–137.8 | API client functions (`lib/api/reports.ts`), TypeScript interfaces, "Reports" sidebar nav entry, reports list page (`/reports`) with category grouping and report cards. Frontend tests (~6 tests). ~4 files created/modified. |  |
| **137B** | 137.9–137.18 | Report detail page (`/reports/[reportSlug]`), `ReportRunner` client component (form + results + export), `ReportParameterForm` (dynamic field rendering), `ReportResults` (summary cards + data table + pagination). Frontend tests (~10 tests). ~6 files created. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 137.1 | Define TypeScript interfaces for reports API | 137A | | `frontend/lib/api/reports.ts` (create file). Define interfaces: `ReportListItem { slug: string; name: string; description: string }`, `ReportCategory { category: string; label: string; reports: ReportListItem[] }`, `ReportListResponse { categories: ReportCategory[] }`, `ParameterDefinition { name: string; type: 'date' \| 'enum' \| 'uuid'; label: string; required?: boolean; options?: string[]; default?: string; entityType?: string }`, `ParameterSchema { parameters: ParameterDefinition[] }`, `ColumnDefinition { key: string; label: string; type: string; format?: string }`, `ColumnDefinitions { columns: ColumnDefinition[] }`, `ReportDefinitionDetail { slug: string; name: string; description: string; category: string; parameterSchema: ParameterSchema; columnDefinitions: ColumnDefinitions; isSystem: boolean }`, `ReportRow = Record<string, unknown>`, `PaginationInfo { page: number; size: number; totalElements: number; totalPages: number }`, `ReportExecutionResponse { reportName: string; parameters: Record<string, unknown>; generatedAt: string; columns: ColumnDefinition[]; rows: ReportRow[]; summary: Record<string, unknown>; pagination: PaginationInfo }`. |
| 137.2 | Implement API client functions | 137A | 137.1 | In `frontend/lib/api/reports.ts`. Functions: `async function getReportDefinitions(token: string): Promise<ReportListResponse>` — calls `GET /api/report-definitions`. `async function getReportDefinition(slug: string, token: string): Promise<ReportDefinitionDetail>` — calls `GET /api/report-definitions/{slug}`. `async function executeReport(slug: string, parameters: Record<string, unknown>, page: number, size: number, token: string): Promise<ReportExecutionResponse>` — calls `POST /api/report-definitions/{slug}/execute`. Use `apiFetch()` from `@/lib/api` (or equivalent existing API client helper). Pattern: `frontend/lib/api/` files from Phase 17 (`retainers.ts`). |
| 137.3 | Add "Reports" sidebar nav entry | 137A | | Modify `frontend/lib/nav-items.ts`. Add entry: `{ label: "Reports", href: (slug: string) => \`/org/${slug}/reports\`, icon: BarChart3 }`. Position after "Profitability" entry. Import `BarChart3` from `lucide-react`. Per architecture doc Section 19.7.1. |
| 137.4 | Create reports list page (server component) | 137A | 137.2 | `frontend/app/(app)/org/[slug]/reports/page.tsx`. Server component. Async params: `const { slug } = await params`. Fetch token: `const token = await auth().getToken()`. Call `getReportDefinitions(token)`. Render: page header "Reports", then for each category: section with category label as `<h2>`, 2-3 column grid of report cards. Each report card: name (bold), description (muted text), "Run Report" link to `/org/${slug}/reports/${report.slug}`. Use `<Card>` from `@/components/ui/card` with hover elevation. Use slate color classes. Pattern: `frontend/app/(app)/org/[slug]/profitability/page.tsx` for server component data fetching structure. |
| 137.5 | Create reports list loading skeleton | 137A | 137.4 | `frontend/app/(app)/org/[slug]/reports/loading.tsx`. Renders skeleton UI for the report list — skeleton card grid matching the expected layout. Use `<Skeleton>` from `@/components/ui/skeleton`. Pattern: other `loading.tsx` files in the app. |
| 137.6 | Write `ReportsPage.test.tsx` | 137A | 137.4 | `frontend/__tests__/ReportsPage.test.tsx`. Mock `getReportDefinitions` to return 3 categories with 1 report each. Tests: renders 3 category section headings ("Time & Attendance", "Financial", "Project"); renders report cards with name and description; each report card links to `/org/test-org/reports/{slug}`; "Run Report" link is present for each report. ~4 vitest tests. Mock `@/lib/api/reports` and `next/navigation` (for `params`). Use `@testing-library/react` + `happy-dom`. |
| 137.7 | Write API client unit tests | 137A | 137.2 | `frontend/__tests__/reports-api.test.ts`. Mock `fetch`. Tests: `getReportDefinitions` calls correct URL with Bearer header; `getReportDefinition` calls correct URL with slug interpolated; `executeReport` POSTs to correct URL with correct body shape. ~3 tests. |
| 137.8 | Verify 137A lint and tests pass | 137A | 137.6, 137.7 | Run `pnpm lint` (no new ESLint errors) and `pnpm test` (all 137A tests pass). Confirm sidebar nav compiles without errors. |
| 137.9 | Create `ReportParameterForm` client component | 137B | 137.1 | `frontend/components/reports/report-parameter-form.tsx`. `"use client"`. Props interface: `{ schema: ParameterSchema; onSubmit: (parameters: Record<string, unknown>) => void; isLoading: boolean }`. Internal state: `Record<string, unknown>` for current field values, initialized from `default` values in schema. Render logic: for each parameter in `schema.parameters`: switch on `parameter.type`. `date` → Shadcn `<Input type="date">` (use native date input or Shadcn DatePicker if available). `enum` → Shadcn `<Select>` with `parameter.options` as items. `uuid` with `entityType` = "project" → placeholder `<Input type="text" placeholder="Project ID (UUID)">` (simplified — no live search combobox in this phase). `uuid` with other entityType → same. Required field validation: on submit, check all `required: true` parameters have values, display inline error if not. "Run Report" submit button with `isLoading` spinner state. Use `cn()` for class composition. Pattern: existing dialog form components in `components/invoices/` or `components/projects/`. |
| 137.10 | Create `ReportResults` client component — summary cards | 137B | 137.1 | `frontend/components/reports/report-results.tsx`. `"use client"`. Props: `{ response: ReportExecutionResponse \| null; isLoading: boolean }`. Summary section: if `response.summary` has entries, render a row of stat cards. Each stat card: label (from a predefined map or capitalized key) and value (formatted per column type). Use `<Card>` with centered `font-mono tabular-nums` value display. If `isLoading`: show skeleton cards. If `response` is null: show nothing or empty state. |
| 137.11 | Create `ReportResults` client component — data table | 137B | 137.10 | In `frontend/components/reports/report-results.tsx`. Data table section below summary cards. Use Shadcn `<Table>` component (`components/ui/table.tsx`). Column headers from `response.columns` array. Data rows from `response.rows`. Cell formatting per `column.type`: `decimal`/`currency` → `Number(value).toFixed(2)` right-aligned; `integer` → `String(value)` right-aligned; `date` → formatted date string; `string` → as-is. Null/undefined values → `"—"` (em-dash). Pagination controls below table: show "Page X of Y" and Previous/Next buttons (client-side — call parent callback with new page number). |
| 137.12 | Create `ReportRunner` client component | 137B | 137.9, 137.11 | `frontend/components/reports/report-runner.tsx`. `"use client"`. Props: `{ definition: ReportDefinitionDetail; orgSlug: string }`. Internal state: `response: ReportExecutionResponse \| null`, `isLoading: boolean`, `currentPage: number`, `hasRun: boolean` (tracks whether report has been executed at least once — controls export button state). Methods: `runReport(parameters, page)` — calls `executeReport()` API function, sets response. `handlePageChange(newPage)` — re-runs with same parameters and new page. On mount: do NOT auto-run (user must click "Run Report"). Render: `<ReportParameterForm>` at top, then `<ReportResults>` below. Export buttons in page header (enabled only if `hasRun`): "Export CSV" and "Export PDF" as direct download links using current parameters as query string. |
| 137.13 | Create report detail page (server component shell) | 137B | 137.2, 137.12 | `frontend/app/(app)/org/[slug]/reports/[reportSlug]/page.tsx`. Server component. Async params: `const { slug, reportSlug } = await params`. Fetch token, call `getReportDefinition(reportSlug, token)`. If not found (catch 404): call `notFound()`. Render: page header with `definition.name` + `definition.description`. Render `<ReportRunner definition={definition} orgSlug={slug} />` below header. Pattern: any existing page with server component + embedded client component. |
| 137.14 | Create report detail loading skeleton | 137B | 137.13 | `frontend/app/(app)/org/[slug]/reports/[reportSlug]/loading.tsx`. Skeleton for the detail page: header skeleton (title + description), parameter form skeleton (date pickers + button outlines), results area skeleton. Pattern: other `loading.tsx` files. |
| 137.15 | Write `ReportParameterForm.test.tsx` | 137B | 137.9 | `frontend/__tests__/ReportParameterForm.test.tsx`. Schema fixture with date, enum, uuid parameters. Tests: renders a date input for type="date" parameters; renders a select for type="enum" parameters with correct options; renders a text input for type="uuid" parameters; required field validation — submit with empty required field shows error message; valid submission calls `onSubmit` with correct parameter map; enum default value is pre-selected; disabled state when `isLoading=true`. `afterEach(() => cleanup())` for Radix components. ~7 tests. |
| 137.16 | Write `ReportResults.test.tsx` | 137B | 137.10, 137.11 | `frontend/__tests__/ReportResults.test.tsx`. `ReportExecutionResponse` fixture with 3 rows and a summary. Tests: renders summary stat cards for each summary key; renders column headers matching `response.columns`; renders correct number of data rows; decimal values are formatted to 2 decimal places; null values render as em-dash; pagination shows "Page 1 of 2" for `totalPages=2`; Previous button disabled on page 1; Next button enabled when `totalPages > 1`. `afterEach(() => cleanup())`. ~7 tests. |
| 137.17 | Write `ReportRunner.test.tsx` | 137B | 137.12 | `frontend/__tests__/ReportRunner.test.tsx`. Mock `executeReport` API function. Tests: export buttons disabled initially (`hasRun=false`); after clicking "Run Report" (triggers `executeReport` mock), export buttons become enabled (`hasRun=true`); loading spinner appears during execution; `ReportResults` renders with response data after execution; page change triggers re-execution with new page number. `afterEach(() => cleanup())`. ~5 tests. |
| 137.18 | Verify all 137B lint and tests pass | 137B | 137.15–137.17 | Run `pnpm lint` and `pnpm test`. All 137B tests pass. No TypeScript errors in new components. No "use client" on server components. |

### Key Files

**Slice 137A — Create:**
- `frontend/lib/api/reports.ts`
- `frontend/app/(app)/org/[slug]/reports/page.tsx`
- `frontend/app/(app)/org/[slug]/reports/loading.tsx`
- `frontend/__tests__/ReportsPage.test.tsx`
- `frontend/__tests__/reports-api.test.ts`

**Slice 137A — Modify:**
- `frontend/lib/nav-items.ts` — add "Reports" nav entry

**Slice 137B — Create:**
- `frontend/components/reports/report-parameter-form.tsx`
- `frontend/components/reports/report-results.tsx`
- `frontend/components/reports/report-runner.tsx`
- `frontend/app/(app)/org/[slug]/reports/[reportSlug]/page.tsx`
- `frontend/app/(app)/org/[slug]/reports/[reportSlug]/loading.tsx`
- `frontend/__tests__/ReportParameterForm.test.tsx`
- `frontend/__tests__/ReportResults.test.tsx`
- `frontend/__tests__/ReportRunner.test.tsx`

**Read for context:**
- `architecture/phase19-reporting-data-export.md` Sections 19.7, 19.5.1, 19.5.2
- `frontend/lib/nav-items.ts` — nav item format to match (add after "Profitability")
- `frontend/app/(app)/org/[slug]/profitability/page.tsx` — server component data fetch + render pattern
- `frontend/components/ui/table.tsx` — Shadcn table component (headers, rows, cells)
- `frontend/components/ui/card.tsx` — card component (used for stat cards)
- `frontend/components/ui/select.tsx` — Shadcn Select (used in parameter form for enum type)
- `frontend/lib/api.ts` — base API client (apiFetch pattern)
- `frontend/__tests__/` — existing test files for style reference (afterEach cleanup pattern)
- `frontend/CLAUDE.md` — await params, "use client" rules, slate color classes

### Architecture Decisions

- **137A before 137B**: The list page (137A) can start as soon as 133B's list endpoint is live. The detail page (137B) needs 134B's execute endpoint. Staging these as separate slices allows frontend work to start a full stage earlier in parallel.
- **Export buttons as `<a>` links with `download` attribute**: PDF/CSV exports are `GET` requests that the browser handles natively as file downloads (`Content-Disposition: attachment`). Using `<a href="..." download>` is simpler than a client-side fetch + blob pattern and works without JavaScript for graceful degradation. Query parameters carry the report parameters. This is consistent with Phase 10 invoice PDF download approach.
- **`uuid` parameter renders as plain text input**: A full entity combobox (live search against `/api/projects` etc.) is a significant component. For Phase 19, a plain text UUID input is acceptable — users who know the entity UUID can paste it. A live combobox can be added in a future phase when the full entity picker library is established.
- **`hasRun` state controls export button enablement**: Export links are generated from the current parameter values in state. Before the report has been run, the exports would produce results for potentially empty/default parameters. Disabling them until `hasRun=true` prevents confusing empty exports.
- **No auto-run on page load**: Reports should be explicitly triggered. Auto-running on mount would consume server resources for every page visit, even when the user wants to adjust parameters first. Consistent with standard reporting UX (Looker, Metabase pattern).
- **`ReportParameterForm` uses React controlled state**: All field values are in component state, not a form library. The schema is small (3–5 parameters), making a full form library (react-hook-form) unnecessary overhead for this use case.

---

## Testing Summary

### Backend Tests by Slice

| Slice | Test File(s) | Test Count | Coverage |
|-------|-------------|------------|----------|
| 133A | `V35MigrationTest` | ~4 | Migration, JSONB round-trip, unique constraint |
| 133B | `StandardReportPackSeederTest`, `ReportingControllerTest` (partial) | ~10 | Idempotent seeding, list/detail endpoints, 404 |
| 134A | — | — | Framework tested via 134B |
| 134B | `TimesheetReportQueryTest`, `ReportExecutionServiceTest`, `ReportingControllerTest` additions | ~18 | All grouping modes, filters, dispatch, audit, pagination |
| 135A | `InvoiceAgingReportQueryTest`, `ProjectProfitabilityReportQueryTest` | ~16 | Bucket classification, margins, customer/project filters |
| 136A | `ReportRenderingServiceTest`, `ReportingControllerTest` additions | ~8 | HTML rendering, branding, PDF headers, audit |
| 136B | `ReportRenderingServiceTest` additions, `ReportingControllerTest` additions | ~7 | CSV format, escaping, headers, audit |

### Frontend Tests by Slice

| Slice | Test File(s) | Test Count | Coverage |
|-------|-------------|------------|----------|
| 137A | `ReportsPage.test.tsx`, `reports-api.test.ts` | ~7 | Category grouping, report cards, API calls |
| 137B | `ReportParameterForm.test.tsx`, `ReportResults.test.tsx`, `ReportRunner.test.tsx` | ~19 | Dynamic form rendering, table display, export button state |

**Total estimated new tests**: ~63 backend integration tests + ~26 frontend unit tests = ~89 new tests across the phase.
