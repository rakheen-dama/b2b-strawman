You are a senior SaaS architect working on an existing multi-tenant "DocTeams" style platform.

The current system already has:

- Organizations as tenants (via Clerk Organizations), with dedicated schema-per-tenant isolation (Phase 13 eliminated shared schema).
- Projects, Customers, Tasks, TimeEntries, and Documents (with org/project/customer scopes).
- Internal staff users authenticated via Clerk, with org-scoped RBAC (admin, owner, member).
- Neon Postgres + S3 + Spring Boot 4 backend + Next.js 16 frontend, running on ECS/Fargate.
- **Time tracking** (Phase 5): `TimeEntry` entity with member, task, project, date, duration, and notes. Project time rollups and "My Work" cross-project dashboard.
- **Audit event infrastructure** (Phase 6): domain mutation logging with queryable API.
- **Comments, notifications, and activity feeds** (Phase 6.5): in-app notification system with `ApplicationEvent`-based fan-out, notification preferences, comment system on tasks/documents, project activity feed.
- **Rate cards, budgets & profitability** (Phase 8): `BillingRate` (3-level hierarchy: org-default -> project-override -> customer-override), `CostRate`, `ProjectBudget`, `OrgSettings` (default currency, logo, brand_color, footer text). Profitability reports (project, customer, org-level, utilization).
- **Operational dashboards** (Phase 9): company dashboard (KPIs, charts), project overview tab, personal dashboard, health scoring.
- **Invoicing & billing from time** (Phase 10): `Invoice`/`InvoiceLine` entities, draft-to-paid lifecycle, unbilled time management, HTML invoice preview via Thymeleaf, PDF generation via OpenHTMLToPDF.
- **Tags, custom fields & views** (Phase 11): `FieldDefinition`, `Tag`, `SavedView` entities with field packs per entity type.
- **Document templates & PDF generation** (Phase 12): `DocumentTemplate`, `GeneratedDocument` entities. Thymeleaf + OpenHTMLToPDF rendering pipeline. Template packs, clone/reset, org branding integration.
- **Customer compliance & lifecycle** (Phase 14): Customer lifecycle state machine (PROSPECT → ONBOARDING → ACTIVE → OFFBOARDING → OFFBOARDED → DORMANT), checklist template engine, compliance packs, data subject requests, retention policies.
- **Project templates & recurring schedules** (Phase 16): `ProjectTemplate`, `RecurringSchedule` entities with daily scheduler and name tokens.
- **Retainer agreements & billing** (Phase 17): `RetainerAgreement`, `RetainerPeriod` entities with hour banks, rollover policies, and period close → invoice generation.

For **Phase 19**, I want to add **Reporting & Data Export** — the infrastructure for running, rendering, and exporting tabular reports, plus three standard reports that exercise the framework end-to-end.

***

## Objective of Phase 19

Design and specify:

1. **Report definition framework** — a `ReportDefinition` entity that describes a report type (name, description, slug, category, parameter schema, Thymeleaf template reference). Seeded via a "standard reports" pack, similar to how template packs and field packs work.
2. **Report execution service** — a backend service that takes a report definition + user-supplied parameters (date range, grouping, filters), executes the appropriate query, assembles a report context (rows, summaries, metadata), and returns structured data.
3. **Report rendering pipeline** — reuses the existing Thymeleaf + OpenHTMLToPDF infrastructure from Phase 12. Each report has a Thymeleaf template that renders an HTML preview. The same HTML is fed to OpenHTMLToPDF for PDF export. CSV export is a separate code path (direct from structured data, no template needed).
4. **Reports page** — a new top-level page in the frontend navigation. Lists available reports by category. Each report has a parameter form (date range pickers, dropdowns for grouping/filtering), an in-page HTML preview, and export buttons (CSV, PDF).
5. **Three standard reports**:
   - **Timesheet Report** — time entries grouped by member, project, or date for a given date range. Shows hours, billable/non-billable split, task details. The most-used report in any professional services tool.
   - **Invoice Aging Report** — outstanding invoices grouped by age bucket (Current, 1–30 days, 31–60, 61–90, 90+ overdue). Shows invoice number, customer, amount, due date, days overdue. Essential for accounts receivable management.
   - **Project Profitability Report** — revenue vs. cost per project for a date range. Shows billable hours, revenue (from invoices or billable time × rate), cost (hours × cost rate), margin, margin %. Builds on Phase 8's profitability data but in exportable report form.
6. **Audit integration** — report generation events logged to the audit trail (who ran what report, when, with what parameters).

***

## Constraints and assumptions

1. **Architecture/stack constraints**

- Reports are **read-only aggregation** — no new domain entities beyond `ReportDefinition` (and optionally `ReportExecution` for audit/history tracking). No writes to existing tables.
- Report templates are stored in the database as Thymeleaf HTML (same as document templates from Phase 12), referenced by the `ReportDefinition`. This means report layouts are data, not code — new report types can be added via seed packs without deployment.
- The rendering pipeline **reuses** the existing `ThymeleafRenderingService` and `PdfRenderingService` from Phase 12. Do not duplicate this infrastructure. If minor adaptations are needed (e.g., a report-specific context builder interface), extend rather than fork.
- CSV export does NOT go through Thymeleaf. It's generated directly from the structured report data (list of rows + column definitions). Use a simple CSV writer — no need for a library like Apache Commons CSV; Java's built-in capabilities are sufficient.
- Report queries should use **Spring Data projections or native queries** as appropriate. For complex aggregations (aging buckets, profitability margins), native SQL is preferred for clarity and performance. Follow the existing pattern of `CAST(:param AS DATE)` for nullable date parameters.
- All report endpoints are tenant-scoped (schema isolation handles this automatically via the existing filter chain).
- Report parameter schemas should be flexible but simple — a JSON structure defining which parameters a report accepts (date range, grouping options, entity filters). The frontend renders the parameter form dynamically from this schema.

2. **Tenancy**

- Reports query data within the tenant's dedicated schema only. No cross-tenant reporting.
- The `ReportDefinition` entity lives in the tenant schema (seeded per tenant), allowing future customization per org.
- Report templates include org branding (logo, brand color, footer) from `OrgSettings`, consistent with document templates.

3. **Export formats**

- **HTML preview**: rendered in-page via a sandboxed iframe (consistent with the invoice preview approach from Phase 10). The iframe receives the rendered HTML from the backend.
- **PDF**: Thymeleaf HTML → OpenHTMLToPDF → streamed as download. Include org branding, report title, parameter summary (e.g., "Period: Jan 1 – Jan 31, 2026 | Grouped by: Member"), the data table, summary row, footer with generation timestamp and page numbers.
- **CSV**: structured data → CSV string → streamed as download. Column headers derived from the report definition. Suitable for import into Excel/Google Sheets.
- PDF and CSV downloads should use `Content-Disposition: attachment` with a filename like `timesheet-2026-01-01-to-2026-01-31.pdf`.

4. **Performance**

- Report queries may scan large datasets (all time entries for a year, all invoices). Use pagination for the HTML preview (default 50 rows per page) but generate the full dataset for PDF/CSV export.
- Consider adding database indexes if needed for common report query patterns (e.g., time entries by date range + project, invoices by status + due date). Check existing indexes before adding new ones.

5. **Seeding**

- A "standard reports" pack seeds the three report definitions and their Thymeleaf templates on tenant provisioning (same as compliance packs, field packs, and document template packs).
- The seed should be idempotent — running it again updates templates but doesn't duplicate definitions (use slug as the unique key).

***

## 1. Report Definition Entity & Framework

### Data model guidance

**ReportDefinition** — describes a report type:
- `id` (UUID, PK)
- `name` (String, required) — display name, e.g., "Timesheet Report"
- `slug` (String, unique) — URL-safe identifier, e.g., "timesheet"
- `description` (String) — brief description shown in the report list
- `category` (String/enum) — grouping for the reports page, e.g., "Time & Attendance", "Financial", "Project"
- `parameter_schema` (JSONB) — describes the parameters this report accepts. Example:
  ```json
  {
    "parameters": [
      { "name": "dateFrom", "type": "date", "label": "From Date", "required": true },
      { "name": "dateTo", "type": "date", "label": "To Date", "required": true },
      { "name": "groupBy", "type": "enum", "label": "Group By", "options": ["member", "project", "date"], "default": "member" },
      { "name": "projectId", "type": "uuid", "label": "Project", "required": false, "entityType": "project" },
      { "name": "memberId", "type": "uuid", "label": "Member", "required": false, "entityType": "member" }
    ]
  }
  ```
- `column_definitions` (JSONB) — describes the columns in the report output. Used by CSV export for headers and by the frontend for table rendering. Example:
  ```json
  {
    "columns": [
      { "key": "memberName", "label": "Team Member", "type": "string" },
      { "key": "totalHours", "label": "Total Hours", "type": "decimal", "format": "0.00" },
      { "key": "billableHours", "label": "Billable Hours", "type": "decimal", "format": "0.00" },
      { "key": "nonBillableHours", "label": "Non-Billable", "type": "decimal", "format": "0.00" }
    ]
  }
  ```
- `template_body` (Text) — the Thymeleaf HTML template for rendering the report. Stored in the database (same pattern as `DocumentTemplate.templateBody`).
- `is_system` (boolean, default true) — system-seeded reports can be reset to defaults but not deleted.
- `created_at`, `updated_at` (timestamps)

### API endpoints

- `GET /api/reports` — list all report definitions (grouped by category)
- `GET /api/reports/{slug}` — get a single report definition (including parameter schema and column definitions, but NOT the template body unless requested)
- `POST /api/reports/{slug}/execute` — execute a report with parameters, returns structured JSON data (rows + summary)
- `GET /api/reports/{slug}/preview?{params}` — returns rendered HTML preview (Thymeleaf-rendered)
- `GET /api/reports/{slug}/export/csv?{params}` — streams CSV download
- `GET /api/reports/{slug}/export/pdf?{params}` — streams PDF download

### Execution request body (for POST /execute)

```json
{
  "parameters": {
    "dateFrom": "2026-01-01",
    "dateTo": "2026-01-31",
    "groupBy": "member",
    "projectId": null,
    "memberId": null
  },
  "page": 0,
  "size": 50
}
```

### Execution response body

```json
{
  "reportName": "Timesheet Report",
  "parameters": { ... },
  "generatedAt": "2026-02-21T10:30:00Z",
  "columns": [ ... ],
  "rows": [
    { "memberName": "John Doe", "totalHours": 160.5, "billableHours": 140.0, "nonBillableHours": 20.5 }
  ],
  "summary": {
    "totalHours": 480.0,
    "billableHours": 410.0,
    "nonBillableHours": 70.0
  },
  "pagination": { "page": 0, "size": 50, "totalElements": 12, "totalPages": 1 }
}
```

***

## 2. Report Execution Service

### Architecture

- A `ReportExecutionService` that dispatches to report-specific query strategies based on the report slug.
- Each report type has a dedicated query class (e.g., `TimesheetReportQuery`, `InvoiceAgingReportQuery`, `ProjectProfitabilityReportQuery`) that:
  1. Validates and parses parameters
  2. Executes the SQL query (native or JPQL as appropriate)
  3. Returns structured data (`ReportResult` with rows, summary, and metadata)
- The dispatch mechanism should be extensible — adding a new report type means: (a) seed a `ReportDefinition`, (b) implement a `ReportQuery` strategy class, (c) register it. Consider using a `Map<String, ReportQuery>` bean or Spring's `@Component` with a slug identifier.

### Query contracts

Each report query class implements:
```java
public interface ReportQuery {
    String getSlug();
    ReportResult execute(Map<String, Object> parameters, Pageable pageable);
    ReportResult executeAll(Map<String, Object> parameters); // for export (no pagination)
}
```

***

## 3. Standard Reports

### 3a. Timesheet Report

**Parameters**: dateFrom (date, required), dateTo (date, required), groupBy (enum: member/project/date, default: member), projectId (uuid, optional filter), memberId (uuid, optional filter)

**Query logic**:
- Select time entries within the date range, optionally filtered by project and/or member.
- Group by the selected dimension.
- For each group: total hours, billable hours, non-billable hours, entry count.
- Detail rows within each group: date, task name, project name, member name, duration, billable flag, notes.
- Summary row: totals across all groups.

**Use cases**: weekly timesheets for payroll, project time audits, individual productivity review.

### 3b. Invoice Aging Report

**Parameters**: asOfDate (date, required, defaults to today), customerId (uuid, optional filter)

**Query logic**:
- Select invoices with status in (SENT, OVERDUE) — i.e., outstanding invoices.
- Calculate days overdue = asOfDate - dueDate (negative means not yet due).
- Bucket into: Current (not yet due), 1–30 days, 31–60 days, 61–90 days, 90+ days.
- For each bucket: count of invoices, total amount.
- Detail rows: invoice number, customer name, invoice date, due date, amount, days overdue, status.
- Summary row: total outstanding amount, breakdown by bucket.

**Use cases**: accounts receivable review, collections prioritization, cash flow forecasting.

### 3c. Project Profitability Report

**Parameters**: dateFrom (date, required), dateTo (date, required), projectId (uuid, optional filter), customerId (uuid, optional filter)

**Query logic**:
- For each project in the date range (optionally filtered):
  - Revenue: sum of `InvoiceLine.lineTotal` from invoices with status SENT or PAID, line-item dates within range. If no invoices exist, use billable time x snapshotted billing rate as estimated revenue.
  - Cost: sum of (time entry hours x cost rate) for entries in the date range.
  - Margin: revenue - cost.
  - Margin %: (margin / revenue) x 100.
  - Billable hours, non-billable hours, total hours.
- Summary row: totals and weighted average margin.

**Use cases**: project post-mortem, portfolio health review, identifying unprofitable engagements.

***

## 4. Rendering & Export Pipeline

### HTML Preview
- Reuse `ThymeleafRenderingService` from Phase 12.
- The report template receives a context with: `report` (metadata), `parameters` (what was selected), `rows` (data), `summary` (totals), `branding` (org logo, color, footer), `generatedAt` (timestamp).
- The frontend fetches the rendered HTML and displays it in a sandboxed iframe (consistent with the invoice preview approach from Phase 10).
- Pagination controls in the frontend (not in the rendered HTML).

### PDF Export
- Reuse `PdfRenderingService` from Phase 12.
- The PDF template should include: org logo + name header, report title, parameter summary (e.g., "Period: Jan 1 - Jan 31, 2026 | Grouped by: Member"), the data table, summary row, footer with generation timestamp and page numbers.
- Full dataset (no pagination) — the PDF contains all rows.
- Stream directly to the response (no S3 upload needed for reports — they're ephemeral, unlike generated documents).

### CSV Export
- No Thymeleaf template needed — generate directly from `ReportResult`.
- Use `column_definitions` from `ReportDefinition` for header row.
- One data row per result row, values formatted according to column type (dates as ISO, decimals with specified precision, strings as-is).
- Include a metadata header row: report name, parameters, generation date.
- Stream to response with `text/csv` content type.

***

## 5. Reports Frontend

### Navigation
- Add "Reports" to the main sidebar navigation, positioned after "Profitability" (or in the same "Analytics" group if one exists).
- Reports page URL: `/reports`.
- Individual report URL: `/reports/{slug}`.

### Reports List Page (`/reports`)
- Group reports by category (e.g., "Time & Attendance", "Financial", "Project").
- Each report shown as a card with name, description, and a "Run Report" button/link.
- Clean, simple layout — this page will grow as more reports are added.

### Report Detail Page (`/reports/{slug}`)
- **Parameter form** at the top: dynamically rendered from the report's `parameter_schema`. Field types:
  - `date` → date picker
  - `enum` → select/dropdown
  - `uuid` with `entityType` → combobox that searches projects/members/customers (reuse existing search components)
- **"Run Report" button** that executes the report and shows results.
- **Results area**:
  - Summary cards at the top (key totals from the summary object).
  - Data table below with sortable columns (client-side sort on the current page).
  - Pagination controls for the table.
- **Export buttons**: "Export CSV" and "Export PDF" — these trigger full (unpaginated) exports.
- **Loading state** while the report executes.

### Component reuse
- Reuse existing table components, date pickers, comboboxes from the design system.
- The parameter form renderer should be a reusable component — it takes a `parameter_schema` JSON and renders the appropriate form fields. This component will be reused as more reports are added.

***

## 6. Audit Integration

- Log a `REPORT_GENERATED` audit event when a report is executed or exported.
- Event details: report slug, parameters used, export format (preview/csv/pdf), row count.
- Use the existing `AuditService` and event builder pattern from Phase 6.

***

## Out of scope

- **Scheduled/emailed reports** — reports are on-demand only in this phase. Scheduled delivery is a future enhancement.
- **Custom report builder** — users cannot create new report definitions in this phase. They use the seeded standard reports. Custom reports are a future phase.
- **Report template editor** — the templates are seeded and can be edited by developers. A user-facing template editor (like the document template editor) is deferred.
- **Dashboard integration** — reports are standalone. Embedding report widgets in dashboards is a future enhancement.
- **Cross-tenant / org-level reporting** — reports are scoped to a single tenant. Platform-wide analytics for the SaaS operator are out of scope.
- **Drill-down / interactive reports** — clicking a row to navigate to the underlying entity is a nice-to-have for a future iteration, not this phase.
- **Report sharing / permissions** — all org members can run all reports. Role-based report access is deferred.

***

## ADR Topics to Address

1. **Report query strategy pattern** — how report-specific queries are registered and dispatched. Strategy pattern vs. Spring bean lookup vs. enum-based switch.
2. **Report template storage** — confirm reuse of the document template pattern (database-stored Thymeleaf). Address whether report templates should be a separate entity or a category within `DocumentTemplate`.
3. **CSV generation approach** — direct streaming vs. in-memory generation. Consider memory implications for large datasets.
4. **Parameter schema design** — JSON schema for report parameters. How much flexibility vs. simplicity. Whether to validate parameters server-side against the schema.

***

## Style and boundaries

- **Backend**: Follow existing Spring Boot 4 + Hibernate 7 patterns. Use native SQL for complex aggregations. Services follow the existing `@Service` + `@Transactional(readOnly = true)` pattern for read-only operations.
- **Frontend**: Follow existing Next.js 16 + Shadcn UI patterns. Server components for the report list page, client components for the interactive report detail page (parameter form + results).
- **Testing**: Integration tests for each report query (verify correct aggregation, filtering, grouping). Frontend tests for the parameter form renderer and report page. Test edge cases: empty results, single row, date boundary conditions.
- **Migration**: One migration file for the `ReportDefinition` table + seed data. Follow the existing migration numbering convention.
- **Seed data**: Idempotent seeder that creates the three standard report definitions with their Thymeleaf templates. Follow the pattern established by compliance packs and field packs.
