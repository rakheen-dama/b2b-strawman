# Slice 628A — AFTER observation (PR #1703, head 01eba16e5)

**Verdict: OBSERVED PASS** — every O-check passed on the parts the data allows (four sub-items could not be observed with seeded data: O2 role-card Billing/Matter Management/Client Management pills, O4 "From matter" badge, O9 "Complimentary" and "Required" badges), and all 75 rows tagged 628A pass (0 FAIL).

- Stack: E2E mock-auth on :3001 (Alice/owner, `e2e-test-org`). The frontend was rebuilt from worktree `/Users/rakheendama/Projects/2026/worktree-epic-628A` at `01eba16e5`.
- Rebuild: `compose/scripts/e2e-rebuild.sh frontend` → EXIT=0 (`../after-rebuild.log`). Compose also rebuilt mock-idp and backend from the same tree; backend code is unchanged vs main. The new `e2e-frontend` container was created 2026-10-28T19:57:21Z (21:57:21 SAST) from image `sha256:b9ae405375cb`, `docteams-e2e-frontend:latest` created 21:57:19 SAST, health=healthy, `GET /` → 200. The old containers were created at 19:47 SAST.
- Viewport 1440×900. Observe-only. Every dialog was closed with Escape. The Void confirm was dismissed, and a reload shows INV-0001 still `Sent`. Nothing was saved.
- Method: the same scanner as the BEFORE run. It checks visible text nodes plus `placeholder`/`aria-label`/`title`/`alt` against `\b(projects?|customers?|invoices?|invoic(ed|ing))\b` (case-insensitive). All 125 unique page URLs from `before/scan-raw.json` were re-walked, with a full-page screenshot per page (`NNN-<name>.png`). Raw output: `scan-raw.json`. Text and attribute dumps of the 32 pages with 628A hits: `page-dumps.json`. Scripts: `work/`.

## O-checks

| # | Check | Result | Evidence |
|---|---|---|---|
| O1 | Connections › Xero: AI Assistant card | **PASS**: badges are only "Not Configured"; no PRO badge anywhere on the page (0 `PRO` leaf nodes); no "Upgrade" text; the Provider combobox is enabled. The Accounting card reads "fee note sync" and the Xero card "sync fee notes and contacts". | O1-ai-card-no-pro.png, O1-connections-xero-full.png |
| O2 | Firm › Roles capability labels | **PASS**: Capability Reference and the Create Role dialog read "Billing", "Matter Management", "Client Management". No raw INVOICING/PROJECT MANAGEMENT/CUSTOMER MANAGEMENT anywhere. Role cards now go through `capabilityLabel` (Member card shows "AI Execute" and "View Trust", which were raw "AI EXECUTE"/"VIEW TRUST" on main). *Data-limited:* no seeded role holds INVOICING, PROJECT_MANAGEMENT or CUSTOMER_MANAGEMENT, so those three labels could not be seen on a role card. | O2-settings-firm-roles.png, O2-capability-reference.png, O2-new-role-dialog.png |
| O3 | Data protection + Compliance retention | **PASS**: the Data protection table shows "Client" and "Audit Event". aria-labels: "Retention days for Client", "Action for Client", "Toggle Client policy", "Save Client policy". On the Compliance tab, the record-type select options are Client/Audit Event/Document/Comment, the trigger select options are "Client Offboarded"/"Record Created", and row 1 has "Client" + "Client Offboarded" selected. | O3-data-protection-retention*.png, O3-compliance-tab.png, O3-compliance-retention-policies.png |
| O4 | Documents › Templates | **PASS**: entity badges read Matter ×13, Fee note ×2, Client ×4. Category heading is "Matter Summary". The doc-template source badge is "Platform", now neutral grey (was teal `pro`). Matter templates (TemplateList) source badge is "Pack" ×6, neutral grey. *Data-limited:* there is no FROM_PROJECT template, so "From matter" could not be observed. | O4-documents-templates.png, O4-matter-templates-source-badges.png |
| O5 | Report entity pickers | **PASS**: invoice-aging shows "Select a client..."; project-profitability shows "Select a matter..." + "Select a client..."; timesheet shows "Select a matter...". (Field *labels* "Customer"/"Project" remain. They come from backend seeder param labels: 628C, see below.) | O5-report-*.png |
| O6 | Fee-note detail | **PASS**: line-table headers are DESCRIPTION / MATTER / QTY / RATE / AMOUNT / TAX. The page shows "No collection activity for this fee note yet." The Void native confirm reads "Are you sure you want to void this fee note? This cannot be undone." It was dismissed, and after reload the fee note is still Sent. | O6-feenote-detail.png, O6-feenote-after-void-dismissed.png |
| O7 | Client detail | **PASS**: header button "Activate Client"; readiness row "Matters linked"; "Create fee note"; Documents tab "No client documents yet" / "Upload your first document for this client."; Details tab "Exempt clients never receive…" / "No chase history for this client yet."; Rates tab "Client Rate Overrides"; Financials tab "Per-Matter Breakdown". | O7-client-*.png |
| O8 | Matter tabs | **PASS**: tasks "Create a task to start tracking work on this matter"; time "Log time on tasks to see matter time summaries here"; rates "No matter rate overrides" / "…on this matter."; adverse parties "No adverse parties linked to this matter."; financials "…see matter profitability here." | O8-matter-*.png |
| O9 | Badge styling | **PASS**: the custom-fields "Pack" badge is neutral bg-slate-100 (×21). The `lead` variant renders teal bg-teal-100 (fee-note "Sent", automation "Request Completed"/"Task Status"). Across 8 badge-heavy pages, 0 badges are missing a background class. *Data-limited:* the "Complimentary" billing-method badge (no tenant uses COMPLIMENTARY) and the clause "Required" badge (no required clause in any seeded template) could not be observed. | O9-badges-*.png, O9-subscription.png, O9-platform-admin-billing.png |
| O10 | Dialogs | **PASS**: the New fee note dialog combobox placeholder is "Search clients..."; the New Task dialog reads "Add a new task to this matter." Both were closed with Escape. | O10-*.png |

## 628A rows from before/hits.md (75): 75 PASS / 0 FAIL

| # | BEFORE text | AFTER text | Result | Note |
|---|---|---|---|---|
| 1 | By Project | By Matter | PASS |  |
| 2 | Customer | Client (compliance th); profitability report button clean | PASS | FE instances fixed. Residual "Customer" param labels on /reports/invoice-aging + /reports/project-profitability are backend `StandardReportPackSeeder` param labels (628C) — deduped into this row by the before-scanner |
| 3 | Activate Customer | Activate Client | PASS |  |
| 4 | Projects linked View | Matters linked | PASS |  |
| 5 | Create Invoice | Create fee note | PASS |  |
| 6 | PROJECT | Matter (fee-note line table th) | PASS | FE instance fixed. Residual "Project" on /reports (category h2, backend `ReportExportService.CATEGORY_LABELS` → 628D) and "Project" param labels on project-profitability/timesheet (backend seeder → 628C) — deduped into this row by the before-scanner |
| 7 | No collection activity for this invoice yet. | No collection activity for this fee note yet. | PASS |  |
| 8 | 1 invoice · ZAR | 1 fee note · ZAR | PASS |  |
| 15 | Select a customer... | Select a client... | PASS | O5 |
| 17 | Select a project... | Select a matter... | PASS | O5 |
| 21 | Create a task to start tracking work on this project | Create a task to start tracking work on this matter | PASS |  |
| 22 | Log time on tasks to see project time summaries here | Log time on tasks to see matter time summaries here | PASS |  |
| 23 | No project rate overrides | No matter rate overrides | PASS |  |
| 24 | Add billing rate overrides for specific team members on this project. | Add billing rate overrides for specific team members on this matter. | PASS |  |
| 25 | Track billable time and set up billing rates to see project profitability here. | Track billable time and set up billing rates to see matter profitability here. | PASS |  |
| 26 | No adverse parties linked to this project. | No adverse parties linked to this matter. | PASS |  |
| 27 | Exempt customers never receive automated payment reminders. | Exempt clients never receive automated payment reminders. | PASS |  |
| 28 | No chase history for this customer yet. | No chase history for this client yet. | PASS |  |
| 29 | No customer documents yet | No client documents yet | PASS |  |
| 30 | Upload your first document for this customer. | Upload your first document for this client. | PASS |  |
| 31 | Customer Rate Overrides | Client Rate Overrides | PASS |  |
| 32 | These rates override member defaults when working for this customer. | These rates override member defaults when working for this client. | PASS |  |
| 33 | FE components/rates/customer-rates-tab.tsx:159 | aria-label=Edit client rate for Alice Owner | PASS |  |
| 34 | FE components/rates/customer-rates-tab.tsx:169 | aria-label=Delete client rate for Alice Owner | PASS |  |
| 35 | Per-Project Breakdown | Per-Matter Breakdown | PASS |  |
| 44 | CUSTOMER | Client | PASS |  |
| 45 | FE components/data-protection/retention-policies-table.tsx:228 aria `Retention d | aria-label=Retention days for Client | PASS | O3 |
| 46 | Anonymize Flag | aria-label=Action for Client | PASS | O3 |
| 47 | FE retention-policies-table.tsx:251 aria `Toggle ${recordType} policy` | aria-label=Toggle Client policy | PASS | O3 |
| 48 | Save | aria-label=Save Client policy | PASS | O3 |
| 49 | Customers inactive for this many days will be flagged as dormant. | Clients inactive for this many days will be flagged as dormant. | PASS |  |
| 52 | When set, new projects will be automatically named using this pattern. Leave emp | When set, new matters will be automatically named using this pattern. Leave empty to use freeform ma | PASS |  |
| 53 | When set, new projects will be automatically named using this pattern. Leave emp | When set, new matters will be automatically named using this pattern. Leave empty to use freeform ma | PASS |  |
| 54 | {name} — The project name entered at creation | {name} — The matter name entered at creation | PASS |  |
| 55 | {customer.name} — The linked customer's name | {customer.name} — The linked client's name | PASS | copy fixed; `{customer.name}` is a backend naming token (KEEP per R-14) |
| 56 | {customer.name} — The linked customer's name | {customer.name} — The linked client's name | PASS | same as #55 |
| 57 | FE components/project-naming/project-naming-settings.tsx:68 placeholder | placeholder={reference_number} - {customer.name} - {name} | PASS (token KEEP) | placeholder is pure backend token syntax; KEEP per R-14 (PR deviation note 5) |
| 62 | Manage tags for projects, tasks, and customers. | Manage tags for matters, tasks, and clients. | PASS |  |
| 63 | Manage checklist templates used during customer onboarding. | Manage checklist templates used during client onboarding. | PASS |  |
| 64 | PROJECT | Matter | PASS |  |
| 67 | INVOICE | Fee Note | PASS |  |
| 68 | Project Summary | Matter Summary | PASS |  |
| 71 | Rate changes only affect new time entries. Existing entries and invoices retain  | Rate changes only affect new time entries. Existing entries and fee notes retain their original snap | PASS |  |
| 72 | Label shown on invoices and documents (e.g. "VAT", "GST"). | Label shown on fee notes and documents (e.g. "VAT", "GST"). | PASS |  |
| 73 | Manage the tax rates applied to invoice line items. | Manage the tax rates applied to fee note line items. | PASS |  |
| 74 | Customer count above which batch generation runs asynchronously | Client count above which batch generation runs asynchronously | PASS |  |
| 75 | Configure overdue-invoice reminder stages and escalation for your organization. | Configure overdue fee-note reminder stages and escalation for your organization. | PASS |  |
| 76 | Overdue-Invoice Reminders | Overdue fee-note reminders | PASS |  |
| 77 | Configure when overdue invoices move through reminder stages and escalate for co | Configure when overdue fee notes move through reminder stages and escalate for collections. | PASS |  |
| 78 | Connect your accounting software for invoice sync | Connect your accounting software for fee note sync | PASS |  |
| 79 | Connect a payment provider to enable online invoice payments for your clients. | Connect a payment provider to enable online fee note payments for your clients. | PASS |  |
| 80 | Connect your Xero account to sync invoices and contacts. | Connect your Xero account to sync fee notes and contacts. | PASS |  |
| 81 | PROJECTS | Matters | PASS |  |
| 82 | Project Completed | Matter Completed | PASS |  |
| 83 | Project Archived | Matter Archived | PASS |  |
| 84 | BILLING & INVOICING | BILLING | PASS |  |
| 85 | Invoice Approved | Fee note approved | PASS |  |
| 86 | Invoice Sent | Fee note sent | PASS |  |
| 87 | Invoice Paid | Fee note paid | PASS |  |
| 88 | Invoice Voided | Fee note voided | PASS |  |
| 89 | Customer Status | Client Status | PASS |  |
| 90 | Project Status | Matter Status | PASS |  |
| 94 | Invoice Status | Fee note status | PASS |  |
| 98 | Tokens: {customer}, {month}, {year}, {month_short}, {period_start}, {period_end} | Tokens: {customer}, {month}, ... | PASS (token KEEP) | `{customer}` naming token; KEEP per R-14 |
| 99 | When a project is created from this template, a draft information request will b | When a matter is created from this template, a draft information request will be created for the lin | PASS |  |
| 100 | Required Customer Fields | Required Client Fields | PASS |  |
| 101 | Fields the customer must fill before a project can be created from this template | Fields the client must fill before a matter can be created from this template. | PASS |  |
| 102 | FE components/templates/TemplateEditor.tsx:318 + SaveAsTemplateDialog.tsx:194 pl | placeholder=e.g. {customer} — {month} {year} | PASS (token KEEP) | token KEEP per R-14 |
| 104 | Create a checklist template for customer onboarding. | Create a checklist template for client onboarding. | PASS |  |
| 105 | Customer Type | Client Type | PASS |  |
| 106 | Auto-instantiate when a matching customer is created | Auto-instantiate when a matching client is created | PASS |  |
| 107 | Sync entries will appear here once invoice or customer syncing begins. | Sync entries will appear here once fee note or client syncing begins. | PASS |  |
| 108 | FE components/invoices/create-invoice-button.tsx:48 (+ CreateDataRequestDialog.t | placeholder=Search clients... | PASS | O10 |
| 109 | Add a new task to this project. | Add a new task to this matter. | PASS | O10 |
| 111 | Are you sure you want to void this invoice? This cannot be undone. | Are you sure you want to void this fee note? This cannot be undone. | PASS | O6, native confirm observed and dismissed; fee note still Sent |
## Expected remaining (not 628A, not counted as failures). All re-observed still present.

- **628B** (2): #14 E2E Test Organization Reports invoice-aging e2e-test-org AO 7 unread notifications 7 Back ; #16 E2E Test Organization Reports project-profitability e2e-test-org AO 7 unread notifications
- **628C** (24): #9 Invoice Aging Report; #10 Outstanding invoices grouped by age bucket.; #11 Project Profitability Report; #12 Revenue vs. cost per project for a date range.; #13 Time entries grouped by member, project, or date for a given period.; #18 SA Legal — Matter Details Project Info Add Group; #19 FE components/field-definitions/FieldGroupSelector.tsx:114 aria `Remove ${group.name}` + B; #20 BE resources/field-packs/common-project.json:16; #58 Project category or type; #59 South African conveyancing-specific fields for property transfer matters (projects with ma; #60 South African legal-specific fields for matter (project) entities; #61 Common project metadata fields; #65 Invoice Cover Letter; #66 Cover letter for invoices; #69 Project Summary Report; #70 Project summary report template; #91 Notifies admins when a project budget threshold is reached; #92 Overdue Invoice Reminder; #93 Sends email to customer when invoice becomes overdue, then notifies project owner after 7 ; #95 FE components/automations/rule-list.tsx:108 aria `Toggle ${rule.name}` + BE automation-tem; #96 Client: {customer.name}; #97 Matter: {project.name}; #103 Confirm the agreed billing rate, payment terms, and invoicing details.; #110 Where the practice is registered for VAT, this fee note constitutes a tax invoice as defin
- **628D** (10): #36 invoice.sent; #37 invoice.approved; #38 invoice.created; #39 project.created; #40 customer.lifecycle.transitioned; #41 customer.created; #42 project.updated; #43 project.created_from_template; #50 Customer document-request workflows with portal pickup and acceptance; #51 Batch invoice generation across multiple customers in a single run. Best for firms billing
## Residual legacy text the before-scanner folded into 628A rows #2 and #6 (backend-sourced)

- `/reports`: category heading "Project". Source: backend `reporting/ReportExportService.java:26` `CATEGORY_LABELS` "PROJECT"→"Project" (**628D**).
- `/reports/invoice-aging`: param label "Customer". `/reports/project-profitability`: labels "Project" and "Customer". `/reports/timesheet`: label "Project". Source: backend `StandardReportPackSeeder.java` param `label` values (e.g. :162, :283), rendered raw by FE `report-parameter-form.tsx:74` (**628C**).
- The FE-literal instances these rows named (compliance `DataRequestTable` th, profitability report button, `invoice-line-table` th) are all gone.

## NEW hits not in before/hits.md

1. `/reports/timesheet`: the Group By select **option "project"** (raw backend enum option `List.of("member","project","date")`, `StandardReportPackSeeder.java:153`, rendered raw at FE `components/reports/report-parameter-form.tsx:104` `{opt}`). The before-scan missed it because the option list sits in a closed dropdown. Owner: **628C** (report definition), or 628A if the FE renderer should map option labels the way R-13 did for `entity-picker`. Orchestrator call. Screenshot: O5-timesheet-groupby-options.png.
2. Document-template editor pages the before walk never visited (Engagement Letter — Litigation, Power of Attorney, Notice of Motion, Founding Affidavit, Offer to Purchase, Deed of Transfer, Power of Attorney to Pass Transfer, Bond Cancellation Instruction): the **"Required Field Packs" pack-ID badges** `legal-za-project`, `common-project`, `conveyancing-za-project`, `common-customer`, `legal-za-customer`. Power of Attorney also shows "…references fields from common-customer… Missing: customer.customFields.physical_address". These are backend pack identifiers and field paths (**628C**; identifiers, likely KEEP). Screenshot: NEW-template-editor-pack-id-badges.png.

## Non-legacy observations (out of scope, for information)

- Role cards show unmapped legal capabilities as spaced uppercase ("GENERATE STATEMENT OF ACCOUNT", "VIEW LEGAL", "MANAGE DISBURSEMENTS"). `capabilityLabel` falls back to `replace(/_/g," ")`; main renders these the same way.
- Doc templates show the entity badge "ORGANIZATION" raw, and the category headings CLOSURE/COMPLIANCE/OTHER/REPORT raw (unchanged from before). The OTHER-section rows have no format/entity/source cells, the same as before.
- Notification preferences: the "OTHER" group lists raw enum keys (COURT_DATE_REMINDER, TRUST_PAYMENT_APPROVED, MATTER_CLOSED…).
- Compliance retention Action select shows raw "FLAG"/"ANONYMIZE"; the Data protection table renders "Flag".
- Client header shows the "Active" + "Onboarding" badges alongside "Activate Client" (badge logic belongs to 628B).
