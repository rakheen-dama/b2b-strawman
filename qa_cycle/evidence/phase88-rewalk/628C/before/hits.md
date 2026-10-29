# 628C reproduce — BEFORE (existing tenant `tenant_7d218705360b`, org `e2e-test-org`, e2e stack :3001/:8081, observed 2026-10-28 ~23:00Z, 1440x900)

Paths: `$R` = backend/src/main/resources, `$RS` = backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/StandardReportPackSeeder.java

## A. Observed in the browser (seeded content on the existing tenant)

| # | Surface | Exact string seen | Source file:line | Screenshot |
|---|---------|-------------------|------------------|------------|
| 1 | Settings › Matters › Custom fields › Matters › field Category (description) | `Project category or type` | `$R/field-packs/common-project.json:16` | 01 |
| 2 | Custom fields › Field Groups (name) | `Project Info` | `$R/field-packs/common-project.json:7` | 01 |
| 3 | Custom fields › Field Groups (description) | `Common project metadata fields` | `$R/field-packs/common-project.json:8` | 01 |
| 4 | Custom fields › Field Groups (description) | `South African conveyancing-specific fields for property transfer matters (projects with matter_type = CONVEYANCING)` | `$R/field-packs/conveyancing-za-project.json:9` | 01 |
| 5 | Custom fields › Field Groups (description) | `South African legal-specific fields for matter (project) entities` | `$R/field-packs/legal-za-project.json:9` | 01 |
| 6 | Reports list — report name, report-detail h1, last breadcrumb | `Invoice Aging Report` | `$RS:259` | 02, 05 |
| 7 | Reports list + detail — description | `Outstanding invoices grouped by age bucket.` | `$RS:265` | 02, 05 |
| 8 | Reports list + detail h1 + last breadcrumb | `Project Profitability Report` | `$RS:331` | 02, 04 |
| 9 | Reports list + detail — description | `Revenue vs. cost per project for a date range.` | `$RS:337` | 02, 04 |
| 10 | Reports list + Timesheet detail — description | `Time entries grouped by member, project, or date for a given period.` | `$RS:221` | 02, 03 |
| 11 | Reports list — category heading | `Project` (category key `PROJECT`) | **Java label map** `reporting/ReportExportService.java:26` (`"PROJECT", "Project"`); key at `$RS:333` | 02 |
| 12 | Timesheet + Profitability detail — parameter label | `Project` (combobox placeholder already "Select a matter...") | `$RS:162`, `$RS:283` | 03, 04 |
| 13 | Profitability + Invoice Aging detail — parameter label | `Customer` (placeholder already "Select a client...") | `$RS:294`, `$RS:238` | 04, 05 |
| 14 | Timesheet detail — Group By option (raw enum shown) | `project` | `$RS:153` (`List.of("member", "project", "date")`) | 03 |
| 15 | Profitability run — result column header | `PROJECT` / `CUSTOMER` | `$RS:304`, `$RS:305` | 04 |
| 16 | Invoice Aging run — result column header | `INVOICE #` / `CUSTOMER` | `$RS:248`, `$RS:249` | 05 |
| 17 | Settings › Documents › Templates (name) + matter "Generate Document" menu | `Project Summary Report` | `$R/template-packs/common/pack.json:19` | 06, 09 |
| 18 | Templates (description) | `Project summary report template` | `$R/template-packs/common/pack.json:24` | 06 |
| 19 | Templates (name) | `Invoice Cover Letter` | `$R/template-packs/common/pack.json:29` | 06 |
| 20 | Templates (description) | `Cover letter for invoices` | `$R/template-packs/common/pack.json:34` | 06 |
| 21 | Settings › Connections › Automations (description) | `Notifies admins when a project budget threshold is reached` | `$R/automation-templates/common.json:101` | 08 |
| 22 | Automations (name) | `Overdue Invoice Reminder` | `$R/automation-templates/common.json:67` | 08 |
| 23 | Automations (description) | `Sends email to customer when invoice becomes overdue, then notifies project owner after 7 days` | `$R/automation-templates/common.json:68` | 08 |

**23 distinct legacy strings observed in the UI** (rows 1–23). Row 11 is from Java code, not a pack.

## B. In seeded data but NOT observed (latent; the UI shows no rule editor / no such email or notification was sent)

| Surface (when it fires) | String | Source |
|---|---|---|
| Report PDF/CSV export body (template_body stored per tenant) | `<th>Project</th>`, `<th>Customer</th>`, `<th>Invoice #</th>`, `Customer:` filter line, `N invoices` bucket counts, fallback `<title>/<h1>` text (overridden by `report.name`), sample `Project Alpha` | `$RS:468,506,517,525-545,552,553,603,639,651,681,682,693` |
| Automation `overdue-invoice-reminder` SEND_EMAIL to client | subject `Invoice {{invoice.invoiceNumber}} is overdue`; body `Your invoice … is now overdue` | `$R/automation-templates/common.json:78,79` |
| same rule, SEND_NOTIFICATION | `Invoice … still overdue` / `Invoice … for {{customer.name}} has been overdue for 7 days.` | `common.json:89,90` |
| `budget-alert-escalation` notification | `Project {{project.name}} has reached its budget threshold.` | `common.json:112` |
| (`{{project.name}}`/`{{customer.name}}` tokens only — render as names, not legacy words) | `Budget alert: {{project.name}}`, `Welcome to {{project.name}}`, `Complete onboarding: {{customer.name}}` | `common.json:111,133,134`; `legal-za.json:19-20` |

## C. Emails (`$R/templates/email/*`) — NOT REPRODUCED in existing mail

e2e Mailpit is **:8026** (4 msgs); :8025 is the KC dev stack (27 msgs). Both were scanned (see mailpit.txt). The only regex hit is the portal URL path `/invoices/<id>` in the "View Fee Note" CTA (a route path, allow-list class). The `invoiceTerm`-driven templates already render "Fee Note".
Latent rendered literals that would show if those emails were sent (none were):
- `notification-budget.html:7` "The project" + fallback `'your project'`; `:25` CTA `View Project` (no `th:text`, so the literal renders)
- `notification-document.html:9` and `notification-task.html:10` "in project" + `'your project'`
- `notification-invoice.html:8` fallback `'a customer'`
- `collection-reminder.html:9` "an invoice" (natural-template default, replaced by `th:utext=${reminderBodyHtml}`, so it only shows if the body is null)
- Natural-template defaults hidden by `th:text` (not rendered): `invoice-delivery.html:2,7,15,52`; `notification-invoice.html:2,7,17,40`; `collection-reminder.html:18,60`; `cash-digest.html:76,94`; `portal-weekly-digest.html:13`.

## D. Clean (NOT REPRODUCED)
- Request templates (3, list + items): no hits (07; SQL Q5/Q5b)
- Custom fields › Tasks / Clients / Fee notes tabs: no hits
- Checklist templates/items, template_tasks, Settings › Connections › Packs, matter Details/Overview, in-app notifications list: no hits
- Project templates: only the `{customer}` naming token (`name_pattern`), which is not shown as a word
