# Reporting

**Bounded context:** see [`10-bounded-contexts.md` § reporting](../10-bounded-contexts.md). **Module-page convention:** every claim anchored to source.

## 1. Purpose

Parameterised report execution with **CSV** and **PDF** export, plus an HTML **preview** path. A `ReportDefinition` is the blueprint: it carries the report's metadata (name, slug, category, description), a JSON `parameter_schema` (form contract), a JSON `column_definitions` (output contract), and an inline Thymeleaf `template_body` that drives PDF rendering. Reports are **system-provided** today (`isSystem = true` per `→ reporting/ReportDefinition.java:47`) — seeded per-tenant by `StandardReportPackSeeder` `→ reporting/StandardReportPackSeeder.java:17`. The entity carries an `isSystem` boolean to leave room for future tenant-defined definitions, but no controller or service currently writes a non-system row.

Execution is dispatched via a **strategy pattern** (ADR-081): each report slug has a matching `ReportQuery` `@Component` `→ reporting/ReportQuery.java:6`; `ReportExecutionService` builds a `Map<String, ReportQuery>` from the injected list at startup and fails fast on duplicate slugs `→ reporting/ReportExecutionService.java:27-41`. PDF rendering reuses the document-pipeline Thymeleaf engine (ADR-082); CSV streams row-by-row through `BufferedWriter` to the servlet output stream (ADR-083). Reports are distinct from dashboards (aggregation widgets, not in this module).

## 2. Entities owned

| Entity | Table | Source |
|--------|-------|--------|
| `ReportDefinition` | `report_definitions` | `→ reporting/ReportDefinition.java:17` |

Columns of note: `slug` (couples a row to a `ReportQuery` bean), `category` (one of `TIME_ATTENDANCE` / `FINANCIAL` / `PROJECT` per `ReportExportService.CATEGORY_LABELS` `→ reporting/ReportExportService.java:22-26`), `parameter_schema` (jsonb, ADR-084 shape), `column_definitions` (jsonb, drives both API response columns and CSV/PDF rendering), `template_body` (Thymeleaf HTML, TEXT), `is_system`, `sort_order`.

Mutators on the entity are deliberately narrow: `updateTemplate(...)` and `updateDefinition(...)` exist solely for seed-pack upsert `→ reporting/ReportDefinition.java:80,86` — there is no full-update API surface.

The three seeded definitions today are `timesheet` (TIME_ATTENDANCE), `invoice-aging` (FINANCIAL), `project-profitability` (PROJECT) `→ reporting/StandardReportPackSeeder.java:55-57`.

## 3. REST surface

`ReportingController` mounts at `/api/report-definitions` `→ reporting/ReportingController.java:22`. Six endpoints:

| Verb + Path | Purpose | Source |
|---|---|---|
| `GET /api/report-definitions` | List, grouped by category | `ReportingController.java:30` |
| `GET /api/report-definitions/{slug}` | Detail (definition + parameter schema + column definitions) | `ReportingController.java:35` |
| `POST /api/report-definitions/{slug}/execute` | Paged execution; body `{parameters, page, size}` (size capped at 500 via `@Max(500)`) | `ReportingController.java:41` |
| `GET /api/report-definitions/{slug}/preview` | HTML preview (text/html) | `ReportingController.java:48` |
| `GET /api/report-definitions/{slug}/export/pdf` | PDF download | `ReportingController.java:56` |
| `GET /api/report-definitions/{slug}/export/csv` | CSV streamed download | `ReportingController.java:66` |

The execute endpoint is the only POST (parameters can be complex / contain UUIDs, so query-string params don't suit). The preview/PDF/CSV endpoints accept parameters via `@RequestParam Map<String, Object>`.

`ReportingController` is one of the documented violators of the thin-controller rule per `backend/CLAUDE.md` (the CSV endpoint passes `response.getOutputStream()` and orchestrates `writeCsvAndAudit`). The orchestration was deliberately pushed *down* into `ReportExportService` `→ reporting/ReportExportService.java:20` to keep the controller as close to delegation as a streaming response permits — but the CSV path still has the OutputStream plumbing in the controller. Tracked under TD-009.

## 4. Frontend pages / components

| Route | Purpose | Source |
|---|---|---|
| `/org/[slug]/reports` | Report catalogue + run-report flow | `frontend/app/(app)/org/[slug]/reports/page.tsx` |
| `/org/[slug]/profitability` | Org-wide profitability + utilization (separate page, fed from same endpoints + dashboard data) | `frontend/app/(app)/org/[slug]/profitability/page.tsx` |
| `/org/[slug]/trust-accounting/reports` | Legal-vertical trust-specific report catalogue | `frontend/app/(app)/org/[slug]/trust-accounting/reports/page.tsx` |

The `/reports` page is **capability-gated**: it returns 403 unless `capData.capabilities.includes("FINANCIAL_VISIBILITY")` `→ frontend/app/(app)/org/[slug]/reports/page.tsx:18`. The trust-accounting reports page is a separate surface that filters/scopes to trust-related definitions.

Discovery anchor: `_discovery/A2-frontend-map.md:160-179` ; `_discovery/A2-frontend-map.md:314` lists `/api/reports` in the frontend api wrapper layer.

## 5. Domain events

**None emitted.** Reporting is read-only. There is no entry on the `DomainEvent` permit list for reports. Audit events *are* recorded per execution and per export — see §6.

## 6. Cross-cutting touchpoints

**Capability gate.** The frontend gates `/reports` on `FINANCIAL_VISIBILITY` `→ reports/page.tsx:18`. The backend reporting controllers carry no `@RequiresCapability` annotations today (verified by absence of matches in `reporting/*.java`); the gate is therefore frontend-enforced (page-level). This is a known asymmetry — see Open Questions §10.

**Audit (per ADR-264 audit-export-is-auditable).** Every execution and export writes an audit event:

- `REPORT_GENERATED` on each `execute(...)` — recorded inside the same transaction as the read `→ reporting/ReportExecutionService.java:62-77`. Includes slug, parameters, format = `"preview"`, rowCount.
- `REPORT_EXPORTED` on PDF export `→ reporting/ReportExportService.java:116-131` (format = `"pdf"`).
- `REPORT_EXPORTED` on CSV export `→ reporting/ReportExportService.java:165-180`, written in a `finally` block so a stream failure mid-write still records the export attempt.

**Strategy-pattern wiring (ADR-081).** Adding a new report requires (1) a new `@Component implements ReportQuery` and (2) a `ReportDefinition` seed entry — no framework changes. Boot-time validation rejects duplicate slugs `→ reporting/ReportExecutionService.java:32-41`.

**Parameter schema (ADR-084).** `parameter_schema` is a custom typed JSON structure (NOT JSON Schema draft-07): `{parameters: [{name, type, label, required, options?, default?, entityType?}]}`. Types in scope today: `date`, `enum`, `uuid`. The `entityType` field on `uuid` parameters (`project`, `member`, `customer`) tells the frontend which combobox to render. Server-side validation lives in `ReportExecutionService` (declared in ADR-084 §Backend impact; the dispatch path in `ReportExecutionService.execute(...)` is the integration point).

**Column definitions.** `column_definitions.columns[]` is a list of `{key, label, type, format?}` rows. `ReportExecutionService.toResponse(...)` parses this into `ColumnDefinition` records `→ reporting/ReportExecutionService.java:107-123` `→ reporting/ColumnDefinition.java`. The same column list drives the API response, the CSV header row, and the Thymeleaf `${columns}` loop in the PDF template.

**PDF rendering pipeline (Thymeleaf, NOT Tiptap).** Per ADR-082 §Backend impact, `ReportRenderingService` calls `PdfRenderingService.renderThymeleaf(definition.getTemplateBody(), contextMap)` — the same engine the document pipeline uses. The contradiction with ADR-263 (Tiptap-based document rendering) is real and worth resolving: documents render Tiptap → HTML → PDF, while *reports* short-circuit straight from Thymeleaf → HTML → PDF because their context is `{rows, summary, parameters, columns, branding}` (see seeded templates `→ reporting/StandardReportPackSeeder.java:292-681`), not Tiptap document JSON. Tracked as Open Question §10.

**CSV streaming (ADR-083).** `ReportRenderingService.writeCsv(...)` wraps `response.getOutputStream()` in `OutputStreamWriter (UTF-8)` + `BufferedWriter`, writes header + rows with custom RFC-4180 escaping. No `Content-Length` header (chunked). No Apache Commons CSV dependency.

**Pack-seeded.** `StandardReportPackSeeder` runs at provisioning time and is idempotent — it tracks application via `OrgSettings.reportPackStatus` JSONB `→ reporting/StandardReportPackSeeder.java:65-71`. Pack ID = `standard-reports`, version = `1`. On re-application it upserts (calls `existing.updateTemplate(...)`), so deploy-time template fixes propagate to existing tenants.

## 7. Vertical specifics

The three seeded reports (timesheet, invoice-aging, project-profitability) are **universal** — every tenant gets them regardless of vertical profile. The seeder makes no profile branching `→ reporting/StandardReportPackSeeder.java:55-57`.

**Trust-accounting reports** are a **legal-vertical-only** surface served from a separate frontend page `/trust-accounting/reports` (see `_discovery/A2-frontend-map.md:178-179`). Those are anchored in the trust-accounting module — see [`30-modules/trust-accounting.md`](trust-accounting.md) and [`60-verticals/legal-za.md`](../60-verticals/legal-za.md). They share the `report-definitions` execution path but are filtered/scoped at the page level.

Vertical-specific reports could ship as packs (the `StandardReportPackSeeder` shape generalises to a per-vertical seeder), but **no vertical-specific report pack exists yet** — `PackType` does not include a `REPORT` value at the time of writing. This is recorded as a future gap in §10.

Terminology overlay flows through naturally: report column labels are stored in `column_definitions` and re-rendered by the frontend's `TerminologyProvider`, but the seeded definitions today use universal labels ("Project", "Customer", "Member", "Invoice") rather than overridden ones — the frontend rewrites at render time based on vertical profile.

## 8. Active ADRs

| ADR | Decision | Anchor |
|---|---|---|
| **ADR-081** | Spring `@Component` auto-collection with slug-based dispatch — adding a report = one new `@Component` + one seed row | `adr/ADR-081-report-query-strategy-pattern.md` |
| **ADR-082** | Separate `ReportDefinition` entity (NOT extending `DocumentTemplate`); inline `template_body` TEXT column; same Thymeleaf engine | `adr/ADR-082-report-template-storage.md` |
| **ADR-083** | Streaming CSV via `BufferedWriter` to `ServletOutputStream`; custom RFC-4180 escape, no library | `adr/ADR-083-csv-generation-approach.md` |
| **ADR-084** | Custom typed JSON parameter schema (not JSON Schema draft-07); types `date`/`enum`/`uuid`; `entityType` extension on UUIDs | `adr/ADR-084-parameter-schema-design.md` |

Adjacent: ADR-264 (audit-export-is-auditable) — reporting honours this via the `REPORT_EXPORTED` audit event paired with `REPORT_GENERATED`; ADR-263 (Tiptap document pipeline) — reporting deliberately diverges from this and uses Thymeleaf directly, see §6.

## 9. Key flows

**No dedicated flow page yet.** The execution and export sequences are short and live entirely inside `ReportExportService` (`execute` / `exportAsPdf` / `exportAsCsv` in `→ reporting/ReportExportService.java:46-182`). They follow the same shape: `findBySlug → executeForExport → render → audit`. If a flow page is added later it would document the strategy dispatch + parameter validation + audit sequence; until then the source files are short enough to read directly.

For the legal-vertical trust report flow, see flows under [`30-modules/trust-accounting.md`](trust-accounting.md).

## 10. Open questions / known fragility

- **Backend capability gate is missing.** `FINANCIAL_VISIBILITY` is enforced at the frontend page level only (`reports/page.tsx:18`); no `@RequiresCapability` is on `ReportingController`. A non-FIN_VIS member who calls `/api/report-definitions/*` directly is not blocked at the controller. This is asymmetric with the rest of the codebase (which gates server-side first, page-side second). Decide: add `@RequiresCapability(FINANCIAL_VISIBILITY)` to all six methods, or accept the page-level gate as authoritative.

- **PDF pipeline contradicts ADR-263 for reports.** Reports render Thymeleaf → HTML → PDF directly (§6), while documents follow Tiptap → HTML → PDF. Two engines in the codebase, both producing PDFs. Acceptable per ADR-082's rationale (different context shapes) but worth a callout in `90-adr-index.md` so future readers don't try to converge them.

- **Large-report performance is not bounded.** `executeAll(...)` (used by both PDF and CSV) materialises the full result set in memory before rendering — only paged execution streams. A 50-person org running a year-of-time timesheet with no project filter could realistically produce 50K+ rows. ADR-083 designed CSV writing for streaming, but CSV reads from an in-memory `ReportResult.rows()` first. Add a hard row-cap or a true row-by-row query stream before a customer hits this.

- **Tenant-defined reports are not safe yet.** The entity carries `isSystem = true` everywhere because there is no admin UI for tenant-authored definitions. If/when added: tenants must NOT be allowed to author SQL — the `ReportQuery` bean (Java) is the SQL author; tenants can only choose existing slugs or pick column subsets / parameter values. The `parameter_schema` is the authoritative form contract. Keep this rule explicit before any tenant-facing CRUD lands.

- **No `REPORT` `PackType`.** Vertical-specific reports cannot ship as content packs today; only the universal `standard-reports` pack exists, hard-coded as a `@Service` rather than a `PackInstaller`. To add a legal-za-specific timesheet (e.g. matter-scoped tariff hours), either add a `REPORT` pack type or extend the existing seeder to switch on vertical profile. The latter is a lower-cost first step.

- **CSV mid-stream failures already on the wire.** Per ADR-083 §Consequences: if a query fails after headers are sent, the client gets a partial CSV with HTTP 200. Acceptable but undocumented in the UI — surface a "verify row count" hint, or move expensive computation before `response.setContentType` so failures still produce a 5xx.

- **Preview HTML returns raw template output.** `previewReport` returns `text/html` directly (`ReportingController.java:48`). If a future report template includes user-controllable values without escaping, this is the path that would surface XSS. Thymeleaf escapes by default, but tenant-authored templates (when introduced) need a sanitisation step before storage.
