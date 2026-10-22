# R8 — Terminology + shell census (Phase 87 capstone follow-up)

Read-only research dossier, repo `b2b-strawman` @ `main` (50b88ed5c), 2026-10-22.
Scope: gap rows G-A2, G-A3, G-A4, G-A5, G-A7, G-B1, G-B4, G-C3, G-C12 (breadcrumb part), G-D10, G-D15, G-D16, G-D17, G-D18, G-D20 in `qa_cycle/evidence/phase87-capstone/gaps.md` plus the attribution table at its foot. The QA walk sampled; this is the full census.

Method: `rg` word-boundary grep for `Project(s)|project(s)|Customer(s)|customer(s)|Invoice(s)|invoice(s)|Engagement(s)|Expense(s)|Budget|Rate card|Action Item|Dashboard` over `frontend/{app,components,lib,hooks}`, `portal/{app,components,lib}`, `packages/`, `backend/src/main/java`, `backend/src/main/resources/{field-packs,template-packs,clause-packs,automation-templates,compliance-packs,request-packs,project-template-packs,templates}`; test files excluded; then two filter passes (identifier/import/href/test-id/SWR-key/route-constant lines dropped; only quoted literals, JSX text and `label:/title:/description:/placeholder:/aria-label/toast/setError` survive) and a manual exclusion list. Raw hit files and the generator script are in the scratchpad (`raw-*.txt`, `vis3-*.txt`, `vis-backend.txt`).

Replacement rules used for the "proposed" column (mechanical; judgment calls are in the note column):
Project→Matter · Customer→Client · Invoice→Fee note (number stays `INV-%04d`, path stays `/api/invoices`, `/invoices` routes stay) · invoiced→billed · Invoicing→Billing · Expense→Disbursement · Budget→Fee estimate ("Over Budget"→"Over estimate") · engagement (count)→matter · Rate card→Billing rate (see G-D17 note; "Tariff" is reserved for LSSA tariffs) · Dashboard→Home. `Engagement letter`, `ENGAGEMENT_LETTER`, "tax invoice" (fee-note-za.json:171, VAT Act s20) and AI-spend "budget" were excluded as legitimate legal/statutory wording. `Action Item` had zero hits anywhere.

## 0. Counts

| Source kind | Rows | Where |
|---|---|---|
| Frontend JSX literal | 148 | §1.1 |
| Frontend label map (`{ value, label }`, `X: { label }`, enum→label) | 57 | §1.1 |
| Frontend error / toast / zod message (`setError`, `error:`, `toast.*`, `confirm()`) | 33 | §1.1 |
| Frontend JSX prop (`title=`, `description=`, `placeholder=`, `aria-label`, `emptyMessage=`) | 23 | §1.1 |
| Portal (all kinds) | 5 (inside the 261 above) | §1.2 |
| Backend pack JSON (field packs, template packs, automation templates, clause packs, email HTML) | 24 | §1.3 |
| Backend label/registry (VerticalModuleRegistry, AuditEventTypeRegistry, StandardReportPackSeeder, VariableMetadataRegistry group names, PrerequisiteContext, EmailTemplate, NotificationService titles, ActivityMessageFormatter fallback) | 26 (+ 32 raw audit event types with no human message) | §1.4 |
| Backend 404 titles (`ResourceNotFoundException("Customer"/"Project"/"Invoice"/"Expense", id)` → "Customer not found" / "No customer found with id …") | 132 call sites, 4 distinct strings | §1.5 |
| Backend conflict / validation / forbidden messages that surface as `result.error` in the UI | 146 | §1.5 |
| **Total user-visible copy sites** | **261 frontend+portal + 24 pack + 26 registry + 278 backend messages = 589** | |

Dead remnant found on the way: `frontend/lib/messages/en-ZA-accounting/common.json` (`"projects": "Engagements"`) is not imported anywhere (`lib/messages/index.ts` loads only `en/*`); delete it.

Notes on what the mechanical "proposed" column cannot decide (product calls):
1. Xero copy (`XeroSettingsForm.tsx:83,134,140,158`, `XeroConnectionCard.tsx:109`, `XeroTaxMappingEditor.tsx:164`, `AccountingIntegrationCard.tsx:48`, `sync-log/*`): "invoice" there is the Xero object the fee note syncs to. Suggest "fee note" in the firm-facing sentence, keep "Xero invoice" where the Xero object is meant.
2. `invoice-draft-form.tsx` / `invoice-line-table.tsx:11` `EXPENSE: "Expenses"` group heading on a fee note: Phase 87 word is Disbursement.
3. "Project Lead" role option (`TemplateEditor.tsx:506`, `SaveAsTemplateDialog.tsx:270`, `role-dialog.tsx:123`): Phase 87 lists use "Fee earner"/"Lead"; proposal "Matter lead".
4. `ReportExportService.java:26` and `StandardReportPackSeeder.java:110/231` category "PROJECT" → label "Project": the category key is stored; only the label changes ("Matter").
5. Marketing landing page (`components/marketing/*`, 8 hits) is public-site copy, outside the app shell; listed in §1.6, not counted above.
6. MCP tool descriptions (`mcp/tool/*.java`, `mcp/resource/ClientResource.java`) say "clients (customers)" / "matters (projects)" deliberately for Claude; excluded.

## 1. User-visible copy census

### 1.1 Frontend + portal (261 rows, 129 files)

Columns: current string is the visible text (quoted literal or JSX text, trimmed); the proposed column is the mechanical rule output and may need a human pass for grammar.

| # | file:line | current string | proposed legal string | source kind | note |
|---|---|---|---|---|---|
| 1 | `frontend/app/(app)/org/[slug]/clients/[id]/data-protection-actions.ts:28` | Only admins and owners can export customer data. | Only admins and owners can export client data. | error/toast |  |
| 2 | `frontend/app/(app)/org/[slug]/clients/[id]/data-protection-actions.ts:69` | Only owners can anonymize customer data. | Only owners can anonymize client data. | error/toast |  |
| 3 | `frontend/app/(app)/org/[slug]/clients/[id]/error.tsx:16` | Unable to load customer data. Please try again. | Unable to load client data. Please try again. | JSX literal |  |
| 4 | `frontend/app/(app)/org/[slug]/clients/[id]/invoice-actions.ts:63` | Failed to validate invoice generation. | Failed to validate fee note generation. | error/toast |  |
| 5 | `frontend/app/(app)/org/[slug]/clients/[id]/invoice-actions.ts:80` | Failed to create invoice draft. | Failed to create fee note draft. | error/toast |  |
| 6 | `frontend/app/(app)/org/[slug]/clients/[id]/lifecycle-actions.ts:24` | Only admins and owners can manage customer lifecycle. | Only admins and owners can manage client lifecycle. | error/toast |  |
| 7 | `frontend/app/(app)/org/[slug]/clients/[id]/page.tsx:365` | Projects linked | Matters linked | label map |  |
| 8 | `frontend/app/(app)/org/[slug]/clients/[id]/page.tsx:433` | Move this customer to Onboarding to begin compliance checklists. | Move this client to Onboarding to begin compliance checklists. | JSX literal |  |
| 9 | `frontend/app/(app)/org/[slug]/clients/[id]/page.tsx:448` | All items verified — Activate Customer | All items verified — Activate Client | JSX literal |  |
| 10 | `frontend/app/(app)/org/[slug]/clients/[id]/page.tsx:453` | Onboarding checklist is complete. This customer is ready to be activated. | Onboarding checklist is complete. This client is ready to be activated. | JSX literal |  |
| 11 | `frontend/app/(app)/org/[slug]/clients/[id]/page.tsx:454` | No onboarding checklist assigned. This customer is ready to be activated. | No onboarding checklist assigned. This client is ready to be activated. | JSX literal |  |
| 12 | `frontend/app/(app)/org/[slug]/clients/[id]/page.tsx:455` | Activate Customer | Activate Client | JSX literal |  |
| 13 | `frontend/app/(app)/org/[slug]/clients/[id]/page.tsx:550` | No chase history for this customer yet. | No chase history for this client yet. | JSX prop |  |
| 14 | `frontend/app/(app)/org/[slug]/clients/actions.ts:100` | Customer name is required. | Client name is required. | error/toast |  |
| 15 | `frontend/app/(app)/org/[slug]/clients/actions.ts:103` | Customer email is required. | Client email is required. | error/toast |  |
| 16 | `frontend/app/(app)/org/[slug]/clients/actions.ts:160` | Customer name is required. | Client name is required. | error/toast |  |
| 17 | `frontend/app/(app)/org/[slug]/clients/actions.ts:163` | Customer email is required. | Client email is required. | error/toast |  |
| 18 | `frontend/app/(app)/org/[slug]/clients/error.tsx:16` | Unable to load customer data. Please try again. | Unable to load client data. Please try again. | JSX literal |  |
| 19 | `frontend/app/(app)/org/[slug]/compliance/actions.ts:102` | Only admins and owners can mark customers as dormant. | Only admins and owners can mark clients as dormant. | error/toast |  |
| 20 | `frontend/app/(app)/org/[slug]/compliance/actions.ts:117` | Failed to mark customer as dormant. | Failed to mark client as dormant. | error/toast |  |
| 21 | `frontend/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker.tsx:136` | } to invoice | } to fee note | JSX literal |  |
| 22 | `frontend/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker.tsx:164` | Select unbilled, approved disbursements to add as invoice lines. | Select unbilled, approved disbursements to add as fee note lines. | JSX literal |  |
| 23 | `frontend/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker.tsx:171` | This invoice is not linked to a matter. Select a matter on the invoice to add | This fee note is not linked to a matter. Select a matter on the fee note to add | JSX literal |  |
| 24 | `frontend/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker.tsx:283` | Add to Invoice | Add to Fee note | JSX literal |  |
| 25 | `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx:200` | No collection activity for this invoice yet. | No collection activity for this fee note yet. | JSX prop |  |
| 26 | `frontend/app/(app)/org/[slug]/invoices/error.tsx:15` | Unable to load invoice data. Please try again. | Unable to load fee note data. Please try again. | JSX literal |  |
| 27 | `frontend/app/(app)/org/[slug]/matters/[id]/page.tsx:131` | Customer assigned | Client assigned | label map |  |
| 28 | `frontend/app/(app)/org/[slug]/matters/[id]/page.tsx:136` | Rate card configured | Billing rate configured | label map |  |
| 29 | `frontend/app/(app)/org/[slug]/matters/[id]/page.tsx:142` | Budget set | Fee estimate set | label map |  |
| 30 | `frontend/app/(app)/org/[slug]/matters/actions.ts:45` | Invalid project priority. | Invalid matter priority. | error/toast |  |
| 31 | `frontend/app/(app)/org/[slug]/matters/actions.ts:50` | Project name is required. | Matter name is required. | error/toast |  |
| 32 | `frontend/app/(app)/org/[slug]/matters/actions.ts:99` | Invalid project priority. | Invalid matter priority. | error/toast |  |
| 33 | `frontend/app/(app)/org/[slug]/matters/actions.ts:107` | Project name is required. | Matter name is required. | error/toast |  |
| 34 | `frontend/app/(app)/org/[slug]/matters/new/intake-actions.ts:47` | Intake pre-flight check failed. Ensure customer is selected and description is at least 20 characters. | Intake pre-flight check failed. Ensure client is selected and description is at least 20 characters. | JSX literal |  |
| 35 | `frontend/app/(app)/org/[slug]/settings/connections/_tabs/xero-actions.ts:129` | Customers have already been imported from Xero. | Clients have already been imported from Xero. | error/toast |  |
| 36 | `frontend/app/(app)/org/[slug]/settings/connections/_tabs/xero.tsx:41` | Connect your accounting software for invoice sync | Connect your accounting software for fee note sync | JSX literal |  |
| 37 | `frontend/app/(app)/org/[slug]/settings/connections/_tabs/xero.tsx:61` | Accept online payments from customers | Accept online payments from clients | JSX literal |  |
| 38 | `frontend/app/(app)/org/[slug]/settings/connections/xero/sync-log/page.tsx:132` | Sync entries will appear here once invoice or customer syncing begins. | Sync entries will appear here once fee note or client syncing begins. | JSX literal |  |
| 39 | `frontend/app/(app)/org/[slug]/settings/connections/xero/sync-log/sync-log-filters.tsx:33` | Invoice | Fee note | label map |  |
| 40 | `frontend/app/(app)/org/[slug]/settings/connections/xero/sync-log/sync-log-filters.tsx:34` | Customer | Client | label map |  |
| 41 | `frontend/app/(app)/org/[slug]/settings/documents/_tabs/UploadDocxDialog.tsx:31` | Project Summary | Matter Summary | label map |  |
| 42 | `frontend/app/(app)/org/[slug]/settings/documents/_tabs/UploadDocxDialog.tsx:36` | Project | Matter | label map |  |
| 43 | `frontend/app/(app)/org/[slug]/settings/documents/_tabs/UploadDocxDialog.tsx:37` | Customer | Client | label map |  |
| 44 | `frontend/app/(app)/org/[slug]/settings/documents/_tabs/UploadDocxDialog.tsx:38` | Invoice | Fee note | label map |  |
| 45 | `frontend/app/(app)/org/[slug]/settings/documents/_tabs/templates-content.tsx:42` | Project Summary | Matter Summary | label map |  |
| 46 | `frontend/app/(app)/org/[slug]/settings/fees/_tabs/collections.tsx:32` | Configure overdue-invoice reminder stages and escalation for your organization. | Configure overdue-fee note reminder stages and escalation for your organization. | JSX literal |  |
| 47 | `frontend/app/(app)/org/[slug]/settings/fees/_tabs/tax-rate-table.tsx:69` | be available for new invoice lines. | be available for new fee note lines. | JSX literal | statutory VAT context; UI rule says Fee note |
| 48 | `frontend/app/(app)/org/[slug]/settings/fees/_tabs/tax-rate-table.tsx:95` | Manage the tax rates applied to invoice line items. | Manage the tax rates applied to fee note line items. | JSX literal | statutory VAT context; UI rule says Fee note |
| 49 | `frontend/app/(app)/org/[slug]/settings/matters/_tabs/checklists.tsx:40` | Manage checklist templates used during customer onboarding. | Manage checklist templates used during client onboarding. | JSX literal |  |
| 50 | `frontend/app/(app)/org/[slug]/settings/matters/_tabs/checklists.tsx:60` | Create your first checklist template to streamline customer onboarding. | Create your first checklist template to streamline client onboarding. | JSX literal |  |
| 51 | `frontend/app/(app)/org/[slug]/settings/matters/_tabs/naming-actions.ts:26` | Only admins and owners can update project naming settings. | Only admins and owners can update matter naming settings. | error/toast |  |
| 52 | `frontend/app/(app)/org/[slug]/settings/matters/_tabs/naming-actions.ts:46` | Only admins and owners can update project naming settings. | Only admins and owners can update matter naming settings. | error/toast |  |
| 53 | `frontend/app/(app)/org/[slug]/settings/matters/_tabs/tags.tsx:29` | Manage tags for projects, tasks, and customers. | Manage tags for matters, tasks, and clients. | JSX literal |  |
| 54 | `frontend/app/(app)/org/[slug]/settings/matters/_tabs/templates-actions.ts:183` | You do not have permission to create projects from templates. | You do not have permission to create matters from templates. | error/toast |  |
| 55 | `frontend/app/(app)/org/[slug]/settings/matters/checklists/new/page.tsx:28` | Create a checklist template for customer onboarding. | Create a checklist template for client onboarding. | JSX literal |  |
| 56 | `frontend/components/acceptance/SendForAcceptanceDialog.tsx:73` | Could not load portal contacts. Please configure them in the customer's portal settings. | Could not load portal contacts. Please configure them in the client's portal settings. | JSX literal |  |
| 57 | `frontend/components/ai/ai-profile-form.tsx:535` | Budget (ZAR | (keep) | JSX literal | AI spend budget — KEEP (not a fee estimate) |
| 58 | `frontend/components/ai/fica-verification-panel.tsx:45` | Upload at least one document for this customer before running AI verification. | Upload at least one document for this client before running AI verification. | JSX literal |  |
| 59 | `frontend/components/ai/matter-intake-panel.tsx:42` | Select a customer before running AI intake. | Select a client before running AI intake. | JSX literal |  |
| 60 | `frontend/components/automations/trigger-type-badge.tsx:9` | Project Status | Matter Status | label map | G-A4 automation trigger label map |
| 61 | `frontend/components/automations/trigger-type-badge.tsx:10` | Customer Status | Client Status | label map | G-A4 automation trigger label map |
| 62 | `frontend/components/automations/trigger-type-badge.tsx:11` | Invoice Status | Fee note Status | label map | G-A4 automation trigger label map |
| 63 | `frontend/components/automations/trigger-type-badge.tsx:13` | Budget Threshold | Fee estimate Threshold | label map | G-A4 automation trigger label map |
| 64 | `frontend/components/billing-runs/billing-run-detail-actions.tsx:107` | generated invoices will remain but the run will be marked as cancelled. | generated fee notes will remain but the run will be marked as cancelled. | JSX literal |  |
| 65 | `frontend/components/billing-runs/billing-run-items-table.tsx:34` | Expense Amount | Disbursement Amount | JSX literal |  |
| 66 | `frontend/components/billing-runs/customer-selection-step.tsx:44` | Failed to load customer data. | Failed to load client data. | error/toast |  |
| 67 | `frontend/components/billing-runs/customer-selection-step.tsx:124` | No customers with unbilled work found for this period. | No clients with unbilled work found for this period. | JSX literal |  |
| 68 | `frontend/components/billing-runs/customer-selection-step.tsx:135` | Select all customers | Select all clients | JSX prop |  |
| 69 | `frontend/components/billing-runs/review-drafts-step.tsx:207` | Failed to save invoice changes. | Failed to save fee note changes. | error/toast |  |
| 70 | `frontend/components/billing-runs/review-drafts-step.tsx:443` | No invoices generated. | No fee notes generated. | JSX literal |  |
| 71 | `frontend/components/billing-runs/review-drafts-step.tsx:502` | Edit Invoice | Edit Fee note | JSX literal |  |
| 72 | `frontend/components/billing-runs/review-drafts-step.tsx:503` | Invoice | Fee note | JSX literal |  |
| 73 | `frontend/components/billing-runs/send-step.tsx:184` | Please wait while invoices are being sent. | Please wait while fee notes are being sent. | JSX literal |  |
| 74 | `frontend/components/billing-runs/send-step.tsx:242` | No approved invoices to send. | No approved fee notes to send. | JSX literal |  |
| 75 | `frontend/components/breadcrumbs.tsx:9` | Dashboard | Home | JSX literal | G-A2 |
| 76 | `frontend/components/budget/budget-config-dialog.tsx:66` | Budget hours must be a positive number. | Fee estimate hours must be a positive number. | error/toast |  |
| 77 | `frontend/components/budget/budget-config-dialog.tsx:71` | Budget amount must be a positive number. | Fee estimate amount must be a positive number. | error/toast |  |
| 78 | `frontend/components/budget/budget-config-dialog.tsx:115` | Edit Budget | Edit Fee estimate | JSX literal |  |
| 79 | `frontend/components/budget/budget-config-dialog.tsx:119` | Configure budget limits for this project. Set hours, amount, or both. | Configure fee estimate limits for this matter. Set hours, amount, or both. | JSX literal |  |
| 80 | `frontend/components/budget/budget-config-dialog.tsx:202` | Save Budget | Save Fee estimate | JSX literal |  |
| 81 | `frontend/components/budget/budget-panel.tsx:29` | Over Budget | Over estimate | label map |  |
| 82 | `frontend/components/budget/budget-panel.tsx:119` | Budget Status | Fee estimate Status | JSX literal |  |
| 83 | `frontend/components/budget/budget-panel.tsx:162` | Budget | Fee estimate | JSX prop |  |
| 84 | `frontend/components/budget/budget-panel.tsx:185` | Budget | Fee estimate | JSX prop |  |
| 85 | `frontend/components/budget/delete-budget-dialog.tsx:59` | This will remove the budget configuration for this project. Time entries and billing | This will remove the fee estimate configuration for this matter. Time entries and billing | JSX literal |  |
| 86 | `frontend/components/budget/delete-budget-dialog.tsx:69` | Delete Budget | Delete Fee estimate | JSX literal |  |
| 87 | `frontend/components/clauses/clause-editor-sheet.tsx:53` | Project | Matter | label map |  |
| 88 | `frontend/components/clauses/clause-editor-sheet.tsx:54` | Customer | Client | label map |  |
| 89 | `frontend/components/clauses/clause-editor-sheet.tsx:55` | Invoice | Fee note | label map |  |
| 90 | `frontend/components/comments/add-comment-form.tsx:89` | Customer visible | Client visible | JSX literal |  |
| 91 | `frontend/components/comments/edit-comment-dialog.tsx:132` | Customer visible | Client visible | JSX literal |  |
| 92 | `frontend/components/compliance/ComplianceSettingsForm.tsx:70` | Customers inactive for this many days will be flagged as dormant. | Clients inactive for this many days will be flagged as dormant. | JSX literal |  |
| 93 | `frontend/components/compliance/CreateChecklistTemplateForm.tsx:162` | Auto-instantiate when a matching customer is created | Auto-instantiate when a matching client is created | JSX literal |  |
| 94 | `frontend/components/compliance/CreateDataRequestDialog.tsx:168` | No customers found. | No clients found. | JSX literal |  |
| 95 | `frontend/components/compliance/DeletionConfirmDialog.tsx:84` | The customer data has been anonymized. | The client data has been anonymized. | JSX literal |  |
| 96 | `frontend/components/compliance/DeletionConfirmDialog.tsx:120` | Customer PII will be anonymized | Client PII will be anonymized | JSX literal |  |
| 97 | `frontend/components/compliance/DeletionConfirmDialog.tsx:123` | Financial records (invoices, time entries) will be preserved | Financial records (fee notes, time entries) will be preserved | JSX literal |  |
| 98 | `frontend/components/compliance/EditChecklistTemplateForm.tsx:178` | Auto-instantiate when a matching customer is created | Auto-instantiate when a matching client is created | JSX literal |  |
| 99 | `frontend/components/compliance/LifecycleTransitionDropdown.tsx:37` | Offboard Customer | Offboard Client | JSX literal |  |
| 100 | `frontend/components/compliance/TransitionConfirmDialog.tsx:43` | This will move the customer to Onboarding status and automatically create compliance checklists. | This will move the client to Onboarding status and automatically create compliance checklists. | JSX literal |  |
| 101 | `frontend/components/compliance/TransitionConfirmDialog.tsx:67` | This will begin the offboarding process. The customer will become read-only and no new work can be created. | This will begin the offboarding process. The client will become read-only and no new work can be created. | JSX literal |  |
| 102 | `frontend/components/compliance/TransitionConfirmDialog.tsx:75` | This will mark the customer as fully Offboarded. This is a permanent status change. | This will mark the client as fully Offboarded. This is a permanent status change. | JSX literal |  |
| 103 | `frontend/components/customers/anonymize-customer-dialog.tsx:208` | Customer PII will be anonymized | Client PII will be anonymized | JSX literal |  |
| 104 | `frontend/components/customers/anonymize-customer-dialog.tsx:212` | Financial records (invoices) will be preserved | Financial records (fee notes) will be preserved | JSX literal |  |
| 105 | `frontend/components/customers/anonymize-customer-dialog.tsx:229` | Customer request | Client request | JSX literal |  |
| 106 | `frontend/components/customers/anonymize-customer-dialog.tsx:277` | Customer data has been anonymized successfully. | Client data has been anonymized successfully. | JSX literal |  |
| 107 | `frontend/components/customers/archive-customer-dialog.tsx:110` | project links will be preserved but they will be hidden from active customer lists. | matter links will be preserved but they will be hidden from active client lists. | JSX literal |  |
| 108 | `frontend/components/customers/client-header-card.tsx:38` | Activate Customer | Activate Client | label map |  |
| 109 | `frontend/components/customers/client-header-card.tsx:148` | engagement | matter | JSX literal | G-B1 sampled site |
| 110 | `frontend/components/customers/collections-exemption-toggle.tsx:61` | Exempt customers never receive automated payment reminders. | Exempt clients never receive automated payment reminders. | JSX literal |  |
| 111 | `frontend/components/customers/data-export-dialog.tsx:94` | Export all customer data including profile information, documents, time entries | Export all client data including profile information, documents, time entries | JSX literal |  |
| 112 | `frontend/components/customers/data-export-dialog.tsx:95` | invoices, and comments into a downloadable archive. | fee notes, and comments into a downloadable archive. | JSX literal |  |
| 113 | `frontend/components/customers/data-export-dialog.tsx:112` | Gathering and packaging customer data... | Gathering and packaging client data... | JSX literal |  |
| 114 | `frontend/components/customers/edit-customer-dialog.tsx:190` | Edit Customer | Edit Client | JSX literal |  |
| 115 | `frontend/components/customers/edit-customer-dialog.tsx:191` | Update customer information. | Update client information. | JSX literal |  |
| 116 | `frontend/components/dashboard/incomplete-profiles-widget.tsx:46` | All customer profiles are complete. | All client profiles are complete. | JSX literal |  |
| 117 | `frontend/components/dashboard/incomplete-profiles-widget.tsx:104` | View all customers &rarr | View all clients &rarr | JSX literal |  |
| 118 | `frontend/components/dashboard/information-requests-widget.tsx:119` | View customer requests &rarr | View client requests &rarr | JSX literal |  |
| 119 | `frontend/components/dashboard/matter-health-widget.tsx:25` | Over Budget | Over estimate | label map |  |
| 120 | `frontend/components/data-protection/log-dsar-dialog.tsx:191` | Customer ID | Client ID | JSX literal |  |
| 121 | `frontend/components/data-protection/log-dsar-dialog.tsx:194` | Customer UUID (required by backend) | Client UUID (required by backend) | JSX prop |  |
| 122 | `frontend/components/data-protection/log-dsar-dialog.tsx:201` | Enter the UUID of the linked customer record. | Enter the UUID of the linked client record. | JSX literal |  |
| 123 | `frontend/components/documents/customer-document-upload.tsx:283` | Upload Customer Document | Upload Client Document | JSX literal |  |
| 124 | `frontend/components/documents/customer-documents-panel.tsx:85` | No customer documents yet | No client documents yet | JSX prop |  |
| 125 | `frontend/components/documents/documents-panel.tsx:123` | Project | Matter | label map |  |
| 126 | `frontend/components/documents/documents-panel.tsx:124` | Customer | Client | label map |  |
| 127 | `frontend/components/documents/visibility-toggle.tsx:31` | Visible to customer portal. Click to make internal only. | Visible to client portal. Click to make internal only. | JSX literal |  |
| 128 | `frontend/components/editor/EntityPicker.tsx:81` | Project | Matter | JSX literal |  |
| 129 | `frontend/components/field-definitions/FieldGroupDialog.tsx:37` | Projects | Matters | label map |  |
| 130 | `frontend/components/field-definitions/FieldGroupDialog.tsx:39` | Customers | Clients | label map |  |
| 131 | `frontend/components/field-definitions/field-definition-constants.ts:4` | Projects | Matters | label map |  |
| 132 | `frontend/components/field-definitions/field-definition-constants.ts:6` | Customers | Clients | label map |  |
| 133 | `frontend/components/integrations/AccountingIntegrationCard.tsx:48` | Connect your accounting software for invoice sync | Connect your accounting software for fee note sync | JSX literal |  |
| 134 | `frontend/components/integrations/PaymentIntegrationCard.tsx:235` | Accept online payments from customers | Accept online payments from clients | JSX literal |  |
| 135 | `frontend/components/integrations/PaymentIntegrationCard.tsx:236` | Connect a payment provider to enable online invoice payments for your clients. | Connect a payment provider to enable online fee note payments for your clients. | JSX literal |  |
| 136 | `frontend/components/integrations/SyncLogTable.tsx:38` | Invoice | Fee note | label map |  |
| 137 | `frontend/components/integrations/SyncLogTable.tsx:39` | Customer | Client | label map |  |
| 138 | `frontend/components/integrations/XeroConnectionCard.tsx:109` | Connect your Xero account to sync invoices and contacts. | Connect your Xero account to sync fee notes and contacts. | JSX literal |  |
| 139 | `frontend/components/integrations/XeroCustomerImport.tsx:27` | Imported ${result.data.created} customers from Xero. | Imported ${result.data.created} clients from Xero. | error/toast | Xero contacts → clients |
| 140 | `frontend/components/integrations/XeroCustomerImport.tsx:86` | This will import all contacts from your connected Xero organization as customers in | This will import all contacts from your connected Xero organization as clients in | JSX literal | Xero contacts → clients |
| 141 | `frontend/components/integrations/XeroCustomerImport.tsx:98` | Import Customers from Xero | Import Clients from Xero | JSX literal | Xero contacts → clients |
| 142 | `frontend/components/integrations/XeroSettingsForm.tsx:83` | Configure how invoices and payments sync with Xero. | Configure how fee notes and payments sync with Xero. | JSX literal | Xero sync copy — "invoice" is the Xero object; wording decision (suggest "fee note") |
| 143 | `frontend/components/integrations/XeroSettingsForm.tsx:134` | On approval — push to Xero when invoice is approved | On approval — push to Xero when fee note is approved | JSX literal | Xero sync copy — "invoice" is the Xero object; wording decision (suggest "fee note") |
| 144 | `frontend/components/integrations/XeroSettingsForm.tsx:140` | On send — push to Xero when invoice is sent to customer | On send — push to Xero when fee note is sent to client | JSX literal | Xero sync copy — "invoice" is the Xero object; wording decision (suggest "fee note") |
| 145 | `frontend/components/integrations/XeroSettingsForm.tsx:158` | Automatically sync invoices and payments with Xero. | Automatically sync fee notes and payments with Xero. | JSX literal | Xero sync copy — "invoice" is the Xero object; wording decision (suggest "fee note") |
| 146 | `frontend/components/integrations/XeroTaxMappingEditor.tsx:164` | Map Kazi tax modes to Xero tax codes for accurate invoice syncing. | Map Kazi tax modes to Xero tax codes for accurate fee note syncing. | JSX literal |  |
| 147 | `frontend/components/invoices/create-invoice-button.tsx:50` | No customers found. | No clients found. | JSX literal |  |
| 148 | `frontend/components/invoices/invoice-generation-dialog.tsx:266` | This draft can be saved, but the invoice cannot be sent until a tax number is | This draft can be saved, but the fee note cannot be sent until a tax number is | JSX literal |  |
| 149 | `frontend/components/invoices/invoice-generation-dialog.tsx:267` | added to the customer profile. | added to the client profile. | JSX literal |  |
| 150 | `frontend/components/invoices/invoice-generation-dialog.tsx:420` | Select All Expenses | Select All Disbursements | JSX literal |  |
| 151 | `frontend/components/invoices/invoice-line-table.tsx:11` | Expenses | Disbursements | label map |  |
| 152 | `frontend/components/invoices/unbilled-summary.tsx:245` | Expense Subtotal | Disbursement Subtotal | JSX literal |  |
| 153 | `frontend/components/invoices/use-invoice-generation.ts:263` | Failed to create invoice draft. | Failed to create fee note draft. | error/toast |  |
| 154 | `frontend/components/legal/create-disbursement-dialog.tsx:381` | Loading customers... | Loading clients... | JSX literal |  |
| 155 | `frontend/components/legal/create-disbursement-dialog.tsx:629` | Invoice or reference number | Fee note or reference number | JSX prop |  |
| 156 | `frontend/components/legal/link-adverse-party-dialog.tsx:162` | -- Select customer -- | -- Select client -- | JSX literal |  |
| 157 | `frontend/components/legal/link-adverse-party-to-project-dialog.tsx:178` | -- Select customer -- | -- Select client -- | JSX literal |  |
| 158 | `frontend/components/legal/project-adverse-parties-tab.tsx:68` | Party unlinked from project | Party unlinked from matter | error/toast |  |
| 159 | `frontend/components/legal/project-court-dates-tab.tsx:50` | project-prescription-trackers-${projectId} | matter-prescription-trackers-${projectId} | JSX literal |  |
| 160 | `frontend/components/legal/tariff-line-dialog.tsx:137` | } to invoice | } to fee note | JSX literal |  |
| 161 | `frontend/components/legal/tariff-line-dialog.tsx:262` | Add to Invoice | Add to Fee note | JSX literal |  |
| 162 | `frontend/components/my-work/available-task-list.tsx:71` | Unassigned tasks in your projects will appear here | Unassigned tasks in your matters will appear here | JSX prop |  |
| 163 | `frontend/components/notifications/notification-preferences-form.tsx:18` | Project Completed | Matter Completed | label map |  |
| 164 | `frontend/components/notifications/notification-preferences-form.tsx:19` | Project Archived | Matter Archived | label map |  |
| 165 | `frontend/components/notifications/notification-preferences-form.tsx:20` | Projects | Matters | label map |  |
| 166 | `frontend/components/notifications/notification-preferences-form.tsx:28` | Budget Alert | Fee estimate Alert | label map | BUDGET_ALERT is the fee-estimate alert |
| 167 | `frontend/components/notifications/notification-preferences-form.tsx:29` | Invoice Approved | Fee note Approved | label map |  |
| 168 | `frontend/components/notifications/notification-preferences-form.tsx:30` | Invoice Sent | Fee note Sent | label map |  |
| 169 | `frontend/components/notifications/notification-preferences-form.tsx:31` | Invoice Paid | Fee note Paid | label map |  |
| 170 | `frontend/components/notifications/notification-preferences-form.tsx:32` | Invoice Voided | Fee note Voided | label map |  |
| 171 | `frontend/components/notifications/notification-preferences-form.tsx:104` | Projects | Matters | JSX literal |  |
| 172 | `frontend/components/prerequisite/prerequisite-violation-list.tsx:33` | Customer | Client | label map |  |
| 173 | `frontend/components/prerequisite/prerequisite-violation-list.tsx:34` | Project | Matter | label map |  |
| 174 | `frontend/components/prerequisite/prerequisite-violation-list.tsx:35` | Invoice | Fee note | label map |  |
| 175 | `frontend/components/prerequisite/types.ts:11` | Customer Activation | Client Activation | label map |  |
| 176 | `frontend/components/prerequisite/types.ts:12` | Invoice Generation | Fee note Generation | label map |  |
| 177 | `frontend/components/prerequisite/types.ts:14` | Project Creation | Matter Creation | label map |  |
| 178 | `frontend/components/profitability/customer-financials-tab.tsx:56` | Per-Project Breakdown | Per-Matter Breakdown | JSX literal |  |
| 179 | `frontend/components/profitability/customer-financials-tab.tsx:121` | Track billable time and set up billing rates to see customer profitability here. | Track billable time and set up billing rates to see client profitability here. | JSX prop |  |
| 180 | `frontend/components/profitability/customer-profitability-section.tsx:148` | Failed to load customer profitability data. | Failed to load client profitability data. | error/toast |  |
| 181 | `frontend/components/profitability/customer-profitability-section.tsx:193` | No customer profitability data for this period | No client profitability data for this period | JSX literal |  |
| 182 | `frontend/components/profitability/project-financials-tab.tsx:40` | Track billable time and set up billing rates to see project profitability here. | Track billable time and set up billing rates to see matter profitability here. | JSX prop |  |
| 183 | `frontend/components/profitability/project-profitability-table.tsx:145` | No project profitability data for this period | No matter profitability data for this period | JSX literal |  |
| 184 | `frontend/components/project-naming/project-naming-settings.tsx:40` | Project naming pattern updated. | Matter naming pattern updated. | error/toast |  |
| 185 | `frontend/components/project-naming/project-naming-settings.tsx:59` | When set, new projects will be automatically named using this pattern. Leave empty to use | When set, new matters will be automatically named using this pattern. Leave empty to use | JSX literal |  |
| 186 | `frontend/components/project-naming/project-naming-settings.tsx:60` | freeform project names. | freeform matter names. | JSX literal |  |
| 187 | `frontend/components/project-naming/project-naming-settings.tsx:82` | — The project name entered at creation | — The matter name entered at creation | JSX literal |  |
| 188 | `frontend/components/project-naming/project-naming-settings.tsx:88` | — The linked customer&apos;s name | — The linked client&apos;s name | JSX literal |  |
| 189 | `frontend/components/projects/archived-project-banner.tsx:38` | This project is archived. It is read-only. | This matter is archived. It is read-only. | JSX literal |  |
| 190 | `frontend/components/projects/complete-project-dialog.tsx:101` | time entries. Completing the project will not affect these entries, but they will | time entries. Completing the matter will not affect these entries, but they will | JSX literal |  |
| 191 | `frontend/components/projects/kpi-dashboard.tsx:169` | Budget consumed | Fee estimate consumed | label map |  |
| 192 | `frontend/components/projects/project-comments-section.tsx:58` | No customer comments yet. Comments shared between your team and customers will appear | No client comments yet. Comments shared between your team and clients will appear | JSX literal |  |
| 193 | `frontend/components/projects/project-comments-section.tsx:87` | Reply to the customer thread (visible to all linked customers)... | Reply to the client thread (visible to all linked clients)... | JSX prop |  |
| 194 | `frontend/components/projects/project-members-panel.tsx:130` | Add team members to collaborate on this project | Add team members to collaborate on this matter | JSX prop |  |
| 195 | `frontend/components/projects/time-summary-panel.tsx:100` | Log time on tasks to see project time summaries here | Log time on tasks to see matter time summaries here | JSX prop |  |
| 196 | `frontend/components/rates/add-project-rate-dialog.tsx:112` | Add Project Rate Override | Add Matter Rate Override | JSX literal |  |
| 197 | `frontend/components/rates/add-project-rate-dialog.tsx:114` | Create a project-specific billing rate override for a team member. | Create a matter-specific billing rate override for a team member. | JSX literal |  |
| 198 | `frontend/components/rates/customer-rates-tab.tsx:74` | No customer rate overrides | No client rate overrides | JSX prop |  |
| 199 | `frontend/components/rates/customer-rates-tab.tsx:96` | Customer Rate Overrides | Client Rate Overrides | JSX literal |  |
| 200 | `frontend/components/rates/customer-rates-tab.tsx:159` | Edit customer rate for ${rate.memberName} | Edit client rate for ${rate.memberName} | JSX prop |  |
| 201 | `frontend/components/rates/customer-rates-tab.tsx:169` | Delete customer rate for ${rate.memberName} | Delete client rate for ${rate.memberName} | JSX prop |  |
| 202 | `frontend/components/rates/customer-rates-tab.tsx:296` | Add Customer Rate Override | Add Client Rate Override | JSX literal |  |
| 203 | `frontend/components/rates/customer-rates-tab.tsx:298` | Create a customer-specific billing rate override for a team member. | Create a client-specific billing rate override for a team member. | JSX literal |  |
| 204 | `frontend/components/rates/customer-rates-tab.tsx:482` | Edit Customer Rate Override | Edit Client Rate Override | JSX literal |  |
| 205 | `frontend/components/rates/member-rates-table.tsx:147` | Rate changes only affect new time entries. Existing entries and invoices retain their | Rate changes only affect new time entries. Existing entries and fee notes retain their | JSX literal |  |
| 206 | `frontend/components/rates/project-rates-tab.tsx:73` | No project rate overrides | No matter rate overrides | JSX prop |  |
| 207 | `frontend/components/rates/project-rates-tab.tsx:98` | Project Rate Overrides | Matter Rate Overrides | JSX literal |  |
| 208 | `frontend/components/rates/project-rates-tab.tsx:163` | Edit project rate for ${rate.memberName} | Edit matter rate for ${rate.memberName} | JSX prop |  |
| 209 | `frontend/components/rates/project-rates-tab.tsx:173` | Delete project rate for ${rate.memberName} | Delete matter rate for ${rate.memberName} | JSX prop |  |
| 210 | `frontend/components/rates/project-rates-tab.tsx:285` | Edit Project Rate Override | Edit Matter Rate Override | JSX literal |  |
| 211 | `frontend/components/roles/role-dialog.tsx:123` | e.g. Bookkeeper, Project Lead | e.g. Bookkeeper, Matter Lead | JSX prop |  |
| 212 | `frontend/components/settings/TaxSettingsForm.tsx:111` | Label shown on invoices and documents (e.g. &quot;VAT&quot;, &quot;GST&quot;). | Label shown on fee notes and documents (e.g. &quot;VAT&quot;, &quot;GST&quot;). | JSX literal | G-A4 sampled site |
| 213 | `frontend/components/settings/batch-billing-settings.tsx:106` | Customer count above which batch generation runs asynchronously | Client count above which batch generation runs asynchronously | JSX literal |  |
| 214 | `frontend/components/settings/collections-settings-form.tsx:109` | Overdue-Invoice Reminders | Overdue-Fee note Reminders | JSX literal |  |
| 215 | `frontend/components/settings/collections-settings-form.tsx:112` | Configure when overdue invoices move through reminder stages and escalate for collections. | Configure when overdue fee notes move through reminder stages and escalate for collections. | JSX literal |  |
| 216 | `frontend/components/settings/org-documents-section.tsx:35` | Project | Matter | label map |  |
| 217 | `frontend/components/settings/org-documents-section.tsx:36` | Customer | Client | label map |  |
| 218 | `frontend/components/settings/time-tracking-settings-form.tsx:225` | Default Expense Markup | Default Disbursement Markup | JSX literal |  |
| 219 | `frontend/components/settings/time-tracking-settings-form.tsx:236` | Default Expense Markup (% | Default Disbursement Markup (% | JSX literal |  |
| 220 | `frontend/components/tags/DeleteTagDialog.tsx:56` | from all projects, tasks, and customers that use it. | from all matters, tasks, and clients that use it. | JSX literal |  |
| 221 | `frontend/components/tasks/log-time-dialog.tsx:32` | project override | matter override | JSX literal |  |
| 222 | `frontend/components/tasks/log-time-dialog.tsx:34` | customer override | client override | JSX literal |  |
| 223 | `frontend/components/tasks/log-time-dialog.tsx:291` | rate. Set up rates in Project Settings &gt; Rates. | rate. Set up rates in Matter Settings &gt; Rates. | JSX literal | "Project Settings > Rates" — path no longer exists; say "Settings › Fees & Trust › Tariffs" |
| 224 | `frontend/components/tasks/task-list-panel.tsx:312` | Create a task to start tracking work on this project | Create a task to start tracking work on this matter | JSX prop |  |
| 225 | `frontend/components/tasks/time-entry-list.tsx:220` | the invoice to unlock. | the fee note to unlock. | JSX literal |  |
| 226 | `frontend/components/tasks/time-entry-list.tsx:240` | the invoice to unlock. | the fee note to unlock. | JSX literal |  |
| 227 | `frontend/components/templates/CreateTemplateForm.tsx:18` | Project Summary | Matter Summary | label map |  |
| 228 | `frontend/components/templates/CreateTemplateForm.tsx:23` | Project | Matter | label map |  |
| 229 | `frontend/components/templates/CreateTemplateForm.tsx:24` | Customer | Client | label map |  |
| 230 | `frontend/components/templates/CreateTemplateForm.tsx:25` | Invoice | Fee note | label map |  |
| 231 | `frontend/components/templates/SaveAsTemplateDialog.tsx:152` | Create a reusable project template from this project&apos;s tasks and tags. | Create a reusable matter template from this matter&apos;s tasks and tags. | JSX literal |  |
| 232 | `frontend/components/templates/SaveAsTemplateDialog.tsx:270` | Project Lead | Matter Lead | JSX literal |  |
| 233 | `frontend/components/templates/TemplateEditor.tsx:411` | When a project is created from this template, a draft information request will be | When a matter is created from this template, a draft information request will be | JSX literal |  |
| 234 | `frontend/components/templates/TemplateEditor.tsx:430` | No tasks yet. Add a task to define the project structure. | No tasks yet. Add a task to define the matter structure. | JSX literal |  |
| 235 | `frontend/components/templates/TemplateEditor.tsx:506` | Project Lead | Matter Lead | JSX literal |  |
| 236 | `frontend/components/templates/TemplateEditor.tsx:639` | Required Customer Fields | Required Client Fields | JSX literal |  |
| 237 | `frontend/components/templates/TemplateEditor.tsx:642` | Fields the customer must fill before a project can be created from this template. | Fields the client must fill before a matter can be created from this template. | JSX literal |  |
| 238 | `frontend/components/templates/TemplateList.tsx:60` | No project templates yet. | No matter templates yet. | JSX literal |  |
| 239 | `frontend/components/templates/TemplateList.tsx:63` | Create your first template to standardize how projects are structured. | Create your first template to standardize how matters are structured. | JSX literal |  |
| 240 | `frontend/components/templates/TemplateList.tsx:117` | From Project | From Matter | JSX literal |  |
| 241 | `frontend/components/templates/TemplatePreviewDialog.tsx:82` | Project | Matter | JSX literal |  |
| 242 | `frontend/components/templates/TemplatePreviewDialog.tsx:220` | Preview includes all related data (customer, members, org settings, etc. | Preview includes all related data (client, members, org settings, etc. | JSX literal |  |
| 243 | `frontend/components/templates/template-editor-settings.tsx:20` | Project Summary | Matter Summary | label map |  |
| 244 | `frontend/components/templates/template-editor-settings.tsx:25` | Project | Matter | label map |  |
| 245 | `frontend/components/templates/template-editor-settings.tsx:26` | Customer | Client | label map |  |
| 246 | `frontend/components/templates/template-editor-settings.tsx:27` | Invoice | Fee note | label map |  |
| 247 | `frontend/components/trust/RecordFeeTransferDialog.tsx:112` | Invoice ID | Fee note ID | JSX literal |  |
| 248 | `frontend/components/trust/RecordFeeTransferDialog.tsx:114` | Invoice UUID | Fee note UUID | JSX prop |  |
| 249 | `frontend/lib/capabilities.tsx:31` | Create, edit, and send invoices to customers | Create, edit, and send fee notes to clients | JSX literal |  |
| 250 | `frontend/lib/capabilities.tsx:35` | Project Management | Matter Management | label map |  |
| 251 | `frontend/lib/capabilities.tsx:36` | Create and manage projects, tasks, and documents | Create and manage matters, tasks, and documents | JSX literal |  |
| 252 | `frontend/lib/capabilities.tsx:45` | Customer Management | Client Management | label map |  |
| 253 | `frontend/lib/capabilities.tsx:46` | Create and manage customer records and relationships | Create and manage client records and relationships | JSX literal |  |
| 254 | `frontend/lib/schemas/ai-profile.ts:33` | Budget must be zero or positive | (keep) | error/toast | AI spend budget — KEEP |
| 255 | `portal/app/(authenticated)/invoices/[id]/page.tsx:45` | Failed to load invoice | Failed to load fee note | error/toast |  |
| 256 | `portal/app/(authenticated)/invoices/[id]/page.tsx:87` | if (!invoice) return null | if (!fee note) return null | JSX literal |  |
| 257 | `portal/app/(authenticated)/invoices/page.tsx:34` | Failed to load invoices | Failed to load fee notes | error/toast |  |
| 258 | `portal/app/(authenticated)/matters/[id]/page.tsx:80` | Failed to load project | Failed to load matter | error/toast |  |
| 259 | `portal/app/(authenticated)/matters/page.tsx:59` | Failed to load projects | Failed to load matters | error/toast |  |
| 260 | `portal/components/status-badge.tsx:27` | project | matter | JSX literal |  |
| 261 | `portal/components/status-badge.tsx:37` | project | matter | JSX literal |  |

### 1.2 Portal (subset of 1.1, listed again for the portal PR)

| file:line | current | proposed | kind |
|---|---|---|---|
| `portal/app/(authenticated)/matters/page.tsx:59` | Failed to load projects | Failed to load matters | error |
| `portal/app/(authenticated)/matters/[id]/page.tsx:80` | Failed to load project | Failed to load matter | error |
| `portal/app/(authenticated)/invoices/page.tsx:34` | Failed to load invoices | Failed to load fee notes | error |
| `portal/app/(authenticated)/invoices/[id]/page.tsx:45` | Failed to load invoice | Failed to load fee note | error |
| `portal/app/(authenticated)/profile/page.tsx:149` | `<dt>Customer</dt>` | Client | JSX literal |

`packages/`: 0 user-visible hits.

### 1.3 Backend pack JSON / templates (24 rows)

These are seeded per tenant. `AbstractPackSeeder.java:143-153`: installed packs are never re-applied (only `reconcileExistingPack`, a no-op default), and `StandardReportPackSeeder.java:50` skips when the pack is already applied — so editing the JSON fixes new tenants only; existing tenants (incl. the walked `legal-za` demo tenant) need either a pack version bump + reconcile that updates names/descriptions, or a one-off data migration (`field_groups.name/description`, `field_definitions.description`, `document_templates.name/description`, `automation_rules.name/description`, `report_definitions.name/description`).

| # | file:line | current string | proposed legal string | source kind |
|---|---|---|---|---|
| 1 | `resources/field-packs/common-project.json:7` | Project Info | Matter info | pack JSON (field group name; G-A5) |
| 2 | `resources/field-packs/common-project.json:8` | Common project metadata fields | Common matter metadata fields | pack JSON (G-A5) |
| 3 | `resources/field-packs/common-project.json:16` | Project category or type | Matter category or type | pack JSON (field description; G-A5) |
| 4 | `resources/field-packs/legal-za-project.json:9` | South African legal-specific fields for matter (project) entities | South African legal-specific fields for matters | pack JSON (G-A5) |
| 5 | `resources/field-packs/conveyancing-za-project.json:9` | … property transfer matters (projects with matter_type = CONVEYANCING) | … property transfer matters (matter type Conveyancing) | pack JSON (G-A5) |
| 6 | `resources/template-packs/common/pack.json:19` | Project Summary Report | Matter Summary Report | pack JSON (document template name; G-B1 "Generate Document" menu) |
| 7 | `resources/template-packs/common/pack.json:24` | Project summary report template | Matter summary report template | pack JSON |
| 8 | `resources/template-packs/common/pack.json:29` | Invoice Cover Letter | Fee Note Cover Letter | pack JSON (G-C3 "Generate Document" menu) — `VisualRegressionTest.java:104,143` and `InvoiceCoverLetterRenderIntegrationTest.java` pin the h2 text |
| 9 | `resources/template-packs/common/pack.json:34` | Cover letter for invoices | Cover letter for fee notes | pack JSON |
| 10 | `resources/clause-packs/legal-za-clauses/pack.json:56` | Invoices shall be rendered monthly or at the conclusion of the matter … within thirty (30) days of the date of the invoice … | Fee notes shall be rendered monthly … of the date of the fee note … | pack JSON (clause body; G-B1 engagement-letter fee clause) — clause text is legal drafting; confirm with product |
| 11 | `resources/clause-packs/standard-clauses/pack.json:18` | All invoices issued by | All fee notes issued by | pack JSON (clause body) |
| 12 | `resources/clause-packs/standard-clauses/pack.json:22` | … payable within thirty (30) days of the invoice date … | … of the fee note date … | pack JSON |
| 13 | `resources/clause-packs/standard-clauses/pack.json:28` | … bank account details provided on the invoice … | … provided on the fee note … | pack JSON |
| 14 | `resources/clause-packs/standard-clauses/pack.json:55` | … will be invoiced at cost … | … will be billed at cost … | pack JSON |
| 15 | `resources/automation-templates/common.json:30` | Overdue Invoice Reminder | Overdue fee note reminder | pack JSON (rule name; G-A4) |
| 16 | `resources/automation-templates/common.json:31` | Sends email to customer when invoice becomes overdue, then notifies project owner after 7 days | Sends email to client when a fee note becomes overdue, then notifies the matter lead after 7 days | pack JSON (G-A4) |
| 17 | `resources/automation-templates/common.json:63` | Budget Alert Escalation | Fee estimate alert escalation | pack JSON |
| 18 | `resources/automation-templates/common.json:64` | Notifies admins when a project budget threshold is reached | Notifies admins when a matter fee estimate threshold is reached | pack JSON |
| 19 | `resources/automation-templates/common.json:108` | Notifies project owner when a document is accepted | Notifies the matter lead when a document is accepted | pack JSON |
| 20 | `resources/templates/email/notification-budget.html:2` | Budget Alert | Fee estimate alert | email HTML |
| 21 | `resources/templates/email/notification-budget.html:25` | View Project | View matter | email HTML (button) |
| 22 | `resources/templates/email/notification-invoice.html:40` | View Invoice | View fee note | email HTML (button) — the same template already receives `invoiceTerm` from `EmailTerminology` ("Fee Note" on legal-za) for the subject; the button is hard-coded |
| 23 | `resources/templates/email/invoice-delivery.html:52` | View Invoice | View fee note | email HTML |
| 24 | `resources/templates/email/collection-reminder.html:9,60` | Our records show an invoice on your account remains unpaid. / View Invoice | … a fee note on your account … / View fee note | email HTML |

Also in automation JSON but not copy: `triggerType: "INVOICE_STATUS_CHANGED"/"PROJECT_STATUS_CHANGED"/"CUSTOMER_STATUS_CHANGED"` are stable keys; their *labels* live in `frontend/components/automations/trigger-type-badge.tsx:9-13` (in §1.1). `automation-templates/common.json:84` slug `new-project-welcome` is a key, not copy. `project-template-packs/legal-za.json` `namePattern: "{customer} - RAF Claim"` is a template variable, not copy. `compliance-packs/*` and `request-packs/*`: 0 hits. `templates/invoice-preview.html:240` "Invoice: INV-0001" is sample markup in the preview page (check whether `InvoiceRenderingService.java:120` substitutes `invoiceTerm` there — it does for the term; line 240 is static).

### 1.4 Backend labels / registries (26 rows + the activity-feed gap)

| # | file:line | current string | proposed legal string | source kind |
|---|---|---|---|---|
| 1 | `verticals/VerticalModuleRegistry.java:102` | Bulk Billing Runs | Fee runs | module name (Features tab card title) |
| 2 | `verticals/VerticalModuleRegistry.java:103` | Batch invoice generation across multiple customers in a single run. Best for firms billing 10+ clients per cycle. | Batch fee-note generation across multiple clients in a single run … | module description (G-A4 Features tab) |
| 3 | `verticals/VerticalModuleRegistry.java:108` | NavItem "/invoices/billing-runs" → "Billing Runs" | Fee runs | module nav label (sidebar uses `nav-items.ts` "Fee runs"; this label is what the Features card lists) |
| 4 | `verticals/VerticalModuleRegistry.java:115` | Customer document-request workflows with portal pickup and acceptance | Client document-request workflows with portal pickup and acceptance | module description (G-A4) |
| 5 | `verticals/VerticalModuleRegistry.java:67-72` | Court Calendar / "Court date tracking and deadline management" / NavItem "/court-calendar" | Diary (route `/court-calendar` was replaced by `/diary` in 613D; the NavItem path is dead) | module name + stale nav path |
| 6 | `audit/AuditEventTypeRegistry.java:110` | `"invoice.*" → "Invoice"` | Register `invoice.created/approved/sent/paid/voided/updated/deleted/payment_reversed` → "Fee note created" … and the wildcard → "Fee note" | audit label (G-C3: fee-note Audit trail rows all read "Invoice") |
| 7 | `audit/AuditEventTypeRegistry.java:301,310` | `defaultFor()` → `titleCase(eventType)` → "Project Reopened", "Project Completed", "Customer Lifecycle Transitioned", "Customer Created" … | Register explicit entries for `project.*` ("Matter …") and `customer.*` ("Client …") so the fallback never title-cases a legacy literal (ADR-342 pins the stored literals `project`/`customer`, so the fix is label-only) | audit label (G-D10) |
| 8 | `audit/DatabaseAuditService.java:522,549` | entity-type facet `titleCase(row.getEntityType())` → "Project", "Customer", "Invoice" | map `project→Matter, customer→Client, invoice→Fee note, expense→Disbursement` before title-casing | audit filter facet labels (G-D6 adjacent) |
| 9 | `reporting/StandardReportPackSeeder.java:207` | Invoice Aging Report | Fee note aging report | report name (G-C3/G-D10 Reports index) |
| 10 | `reporting/StandardReportPackSeeder.java:213` | Outstanding invoices grouped by age bucket. | Outstanding fee notes grouped by age bucket. | report description |
| 11 | `reporting/StandardReportPackSeeder.java:279` | Project Profitability Report | Matter profitability report | report name |
| 12 | `reporting/StandardReportPackSeeder.java:285` | Revenue vs. cost per project for a date range. | Revenue vs. cost per matter for a date range. | report description |
| 13 | `reporting/StandardReportPackSeeder.java:169` | Time entries grouped by member, project, or date for a given period. | … by member, matter, or date … | report description |
| 14 | `reporting/StandardReportPackSeeder.java:110,231` | parameter label "Project" | Matter | report parameter label |
| 15 | `reporting/StandardReportPackSeeder.java:186,242` | parameter label "Customer" | Client | report parameter label |
| 16 | `reporting/StandardReportPackSeeder.java:196` | column "Invoice #" | Fee note # | report column header |
| 17 | `reporting/StandardReportPackSeeder.java:197,253` | column "Customer" | Client | report column header |
| 18 | `reporting/StandardReportPackSeeder.java:252` | column "Project" | Matter | report column header |
| 19 | `reporting/ReportExportService.java:26` | `"PROJECT" → "Project"` category label | Matter | category label (`ReportingControllerTest.java:138` pins "Project") |
| 20 | `template/VariableMetadataRegistry.java:117,295` | group "Project" | Matter | template-variable picker group |
| 21 | `template/VariableMetadataRegistry.java:131,192,272` | group "Customer" | Client | template-variable picker group (`VariableMetadataEndpointTest.java:66-136` pins) |
| 22 | `template/VariableMetadataRegistry.java:163` | group "Budget" | Fee estimate | template-variable picker group |
| 23 | `template/VariableMetadataRegistry.java:180` | group "Project Members" | Matter members | template-variable picker group |
| 24 | `template/VariableMetadataRegistry.java:217,232,251,255,315` | "Invoice Summary", "Invoices", "Invoice", "Invoice Number", "Invoice Lines" | Fee note summary / Fee notes / Fee note / Fee note number / Fee note lines | template-variable picker |
| 25 | `prerequisite/PrerequisiteContext.java:5-8` | Customer Activation / Invoice Generation / Project Creation | Client activation / Fee note generation / Matter creation | enum label (mirrored in `frontend/components/prerequisite/types.ts:11-14`) |
| 26 | `notification/template/EmailTemplate.java:22,24` | "New document in your project" / "You've been added to a project" (+ bodies "View the project in Kazi") | … matter … | email subject/body |
| 27 | `notification/NotificationService.java:531` | `Project "%s" has reached %s%% of its %s budget` | `Matter "%s" has reached %s%% of its %s fee estimate` | in-app notification title (`EmailNotificationChannelIntegrationTest.java:240` pins) |
| 28 | `notification/NotificationService.java:559` | `Invoice %s for %s has been approved` | `Fee note %s for %s has been approved` | notification title (`InvoiceNotificationIntegrationTest.java:193` asserts on "has been approved" only) — lines 575/590 already use the `EmailTerminology` term (`%s %s for %s has been sent/paid`); 559 does not |
| 29 | `notification/channel/EmailNotificationChannel.java:252,276` | links `orgAppUrl + "/projects/" + id` | `/matters/` (frontend redirect covers it today — G-C5) | email link |
| 30 | `activity/ActivityMessageFormatter.java:153` | default `"%s performed %s on %s"` → "Alice performed invoice.sent on invoice", "… project.reopened on project" (G-D10 Home recent activity) | add cases; 32 emitted event types in the project/customer/invoice/expense/budget families have no case: `budget.deleted customer.archived customer.created customer.lifecycle.auto_dormancy customer.lifecycle.transitioned customer.linked customer.unarchived customer.unlinked customer.updated expense.created expense.deleted expense.restored expense.updated expense.written_off invoice.approved invoice.created invoice.custom_fields_updated invoice.deleted invoice.field_groups_updated invoice.paid invoice.payment_partially_reversed invoice.payment_reversed invoice.sent invoice.updated invoice.voided project.archived project.completed project.created project.created_from_template project.deleted project.reopened project.updated` (computed: 274 emitted `.eventType("…")` literals vs 56 `case` labels) | activity feed message builder |
| 31 | `activity/ActivityMessageFormatter.java:78,81` | "added %s to the project" / "removed %s from the project" | … the matter | activity message |
| 32 | `activity/ActivityMessageFormatter.java:120,123,126` | "an overdue invoice" / "An overdue invoice" | … fee note | activity message |

### 1.5 Backend messages that reach the UI (278 rows)

How they surface: `ResourceNotFoundException(resourceType, id)` (`exception/ResourceNotFoundException.java:9-14`) builds title `"<Type> not found"` and detail `"No <type> found with id <id>"`; the ProblemDetail title/detail is what the frontend server actions put into `result.error` and what dialogs render via `setError`. `ResourceConflictException(title, detail)`, `InvalidStateException`, `ValidationException`, `ForbiddenException` behave the same. Because the detail lower-cases the type, `"Fee note"` → `"No fee note found with id …"` works unchanged.

The 132 `ResourceNotFoundException` sites reduce to four strings: `"Customer"` (≈70 sites) → `"Client"`, `"Project"` (≈45) → `"Matter"`, `"Invoice"` (≈20) → `"Fee note"`, `"Expense"` (10, `expense/ExpenseService.java`, `invoice/InvoiceCreationService.java:932`, `InvoiceTransitionService.java:151`) → `"Disbursement"`. The elegant fix is one constant per entity (e.g. `ResourceNotFoundException.CLIENT = "Client"`) and a sed over the call sites, plus the pinned tests listed in §8.

Full list (generated; sorted by path):

| # | file:line | current string | proposed legal string | source kind |
|---|---|---|---|---|
| 1 | `acceptance/AcceptanceService.java:143` | Portal contact does not belong to customer | Portal contact does not belong to client | backend message |
| 2 | `acceptance/AcceptanceService.java:144` | The selected contact is not associated with the document's customer | The selected contact is not associated with the document's client | backend message |
| 3 | `acceptance/AcceptanceService.java:941` | No customer linked to project | No client linked to matter | backend message |
| 4 | `acceptance/AcceptanceService.java:942` | Cannot send acceptance: project has no linked customer | Cannot send acceptance: matter has no linked client | backend message |
| 5 | `audit/AuditEventTypeRegistry.java:110` | invoice.* / Invoice | fee note.* / Fee note | backend label/registry |
| 6 | `billingrate/BillingRateService.java:368` | Only admins, owners, or project leads can manage billing rates | Only admins, owners, or matter leads can manage billing rates | backend message |
| 7 | `billingrate/BillingRateService.java:377` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 8 | `billingrate/BillingRateService.java:387` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 9 | `billingrun/BillingRunSelectionService.java:283` | Cannot exclude customer | Cannot exclude client | backend message |
| 10 | `billingrun/BillingRunSelectionService.java:317` | Cannot include customer | Cannot include client | backend message |
| 11 | `checklist/ChecklistInstanceService.java:91` | A checklist instance already exists for this customer and template | A checklist instance already exists for this client and template | backend message |
| 12 | `checklist/ChecklistInstanceService.java:167` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 13 | `checklist/ChecklistInstanceService.java:391` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 14 | `checklist/ChecklistInstanceService.java:526` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 15 | `checklist/ChecklistInstanceService.java:559` | Customer \ | Client \ | backend message |
| 16 | `collections/AiReminderComposer.java:100` | Collection reminder draft for invoice  | Collection reminder draft for fee note  | backend message |
| 17 | `collections/CollectionReminderSendService.java:99` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 18 | `collections/CollectionsReadService.java:165` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 19 | `collections/CollectionsReadService.java:181` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 20 | `comment/CommentService.java:107` | Invalid visibility for project comment | Invalid visibility for matter comment | backend message |
| 21 | `comment/CommentService.java:112` | Invalid entity for project comment | Invalid entity for matter comment | backend message |
| 22 | `compliance/CustomerLifecycleGuard.java:76` |  for customer in  |  for client in  | backend message |
| 23 | `compliance/CustomerLifecycleService.java:115` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 24 | `compliance/CustomerLifecycleService.java:355` | Cannot activate customer — one or more onboarding checklists are not yet completed | Cannot activate client — one or more onboarding checklists are not yet completed | backend message |
| 25 | `correspondence/CorrespondenceService.java:172` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 26 | `customer/CustomerProjectService.java:64` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 27 | `customer/CustomerProjectService.java:72` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 28 | `customer/CustomerProjectService.java:80` | Customer already linked | Client already linked | backend message |
| 29 | `customer/CustomerProjectService.java:81` | Customer  /  is already linked to project  | Client  /  is already linked to matter  | backend message |
| 30 | `customer/CustomerProjectService.java:109` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 31 | `customer/CustomerProjectService.java:114` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 32 | `customer/CustomerProjectService.java:151` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 33 | `customer/CustomerService.java:379` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 34 | `customer/CustomerService.java:464` | Customer email conflict / A customer with email  | Client email conflict / A client with email  | backend message |
| 35 | `customer/CustomerService.java:615` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 36 | `customer/CustomerService.java:620` | Customer email conflict / A customer with email  | Client email conflict / A client with email  | backend message |
| 37 | `customer/CustomerService.java:727` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 38 | `customer/CustomerService.java:766` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 39 | `customer/CustomerService.java:775` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 40 | `customer/CustomerService.java:783` | Unlink all projects first. | Unlink all matters first. | backend message |
| 41 | `customer/CustomerService.java:787` | Void or delete all invoices first. | Void or delete all fee notes first. | backend message |
| 42 | `customer/CustomerService.java:824` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 43 | `customer/CustomerService.java:829` | Customer is not archived (current status:  | Client is not archived (current status:  | backend message |
| 44 | `customerbackend/controller/PortalCommentController.java:66` | Project is archived / Project is archived. No modifications allowed. | Matter is archived / Matter is archived. No modifications allowed. | backend message |
| 45 | `customerbackend/service/PortalReadModelService.java:71` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 46 | `customerbackend/service/PortalReadModelService.java:83` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 47 | `customerbackend/service/PortalReadModelService.java:94` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 48 | `customerbackend/service/PortalReadModelService.java:110` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 49 | `customerbackend/service/PortalReadModelService.java:134` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 50 | `customerbackend/service/PortalReadModelService.java:151` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 51 | `customerbackend/service/PortalReadModelService.java:155` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 52 | `customerbackend/service/PortalReadModelService.java:186` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 53 | `customerbackend/service/PortalReadModelService.java:189` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 54 | `customerbackend/service/PortalTrustLedgerService.java:177` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 55 | `dashboard/ProjectHealthCalculator.java:45` | Over budget | Over fee estimate | backend message |
| 56 | `dashboard/ProjectHealthCalculator.java:54` | Budget %d%% consumed but only %d%% of tasks complete | Fee estimate %d%% consumed but only %d%% of tasks complete | backend message |
| 57 | `datarequest/DataAnonymizationService.java:114` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 58 | `datarequest/DataAnonymizationService.java:119` | Customer name mismatch | Client name mismatch | backend message |
| 59 | `datarequest/DataAnonymizationService.java:120` | Provided name does not match the customer record. Please confirm the exact customer name. | Provided name does not match the client record. Please confirm the exact client name. | backend message |
| 60 | `datarequest/DataAnonymizationService.java:125` | Anonymized Customer  | Anonymized Client  | backend message |
| 61 | `datarequest/DataAnonymizationService.java:215` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 62 | `datarequest/DataAnonymizationService.java:220` | Customer is already anonymized | Client is already anonymized | backend message |
| 63 | `datarequest/DataAnonymizationService.java:221` | Customer  | Client  | backend message |
| 64 | `datarequest/DataAnonymizationService.java:227` | Confirmation name does not match customer name | Confirmation name does not match client name | backend message |
| 65 | `datarequest/DataAnonymizationService.java:228` | The provided confirmation name does not match the customer name | The provided confirmation name does not match the client name | backend message |
| 66 | `datarequest/DataAnonymizationService.java:239` | Anonymized Customer  | Anonymized Client  | backend message |
| 67 | `datarequest/DataAnonymizationService.java:330` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 68 | `datarequest/DataExportService.java:181` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 69 | `datarequest/DataSubjectRequestService.java:68` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 70 | `document/DocumentService.java:258` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 71 | `exception/InvoiceValidationFailedException.java:20` | Invoice validation failed | Fee note validation failed | backend message |
| 72 | `exception/InvoiceValidationFailedException.java:21` | Invoice has validation issues that must be resolved or overridden before sending. | Fee note has validation issues that must be resolved or overridden before sending. | backend message |
| 73 | `expense/ExpenseService.java:170` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 74 | `expense/ExpenseService.java:173` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 75 | `expense/ExpenseService.java:216` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 76 | `expense/ExpenseService.java:256` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 77 | `expense/ExpenseService.java:259` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 78 | `expense/ExpenseService.java:265` | Expense is billed / Expense is part of an invoice. Void the invoice to unlock. | Disbursement is billed / Disbursement is part of an fee note. Void the fee note to unlock. | backend message |
| 79 | `expense/ExpenseService.java:299` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 80 | `expense/ExpenseService.java:302` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 81 | `expense/ExpenseService.java:311` | Expense is billed / Cannot write off a billed expense. Void the invoice first. | Disbursement is billed / Cannot write off a billed disbursement. Void the fee note first. | backend message |
| 82 | `expense/ExpenseService.java:342` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 83 | `expense/ExpenseService.java:345` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 84 | `expense/ExpenseService.java:391` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 85 | `expense/ExpenseService.java:397` | Expense is billed / Expense is part of an invoice. Void the invoice to unlock. | Disbursement is billed / Disbursement is part of an fee note. Void the fee note to unlock. | backend message |
| 86 | `expense/ExpenseService.java:474` | Only the creator or a project lead/admin/owner can modify this expense | Only the creator or a matter lead/admin/owner can modify this disbursement | backend message |
| 87 | `informationrequest/InformationRequestService.java:219` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 88 | `informationrequest/InformationRequestService.java:224` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 89 | `informationrequest/InformationRequestService.java:292` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 90 | `informationrequest/InformationRequestService.java:297` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 91 | `informationrequest/InformationRequestService.java:620` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 92 | `informationrequest/InformationRequestService.java:1020` | Portal contact does not belong to the specified customer | Portal contact does not belong to the specified client | backend message |
| 93 | `integration/accounting/sync/AccountingSyncService.java:117` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 94 | `integration/accounting/sync/AccountingSyncService.java:122` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 95 | `integration/accounting/sync/AccountingSyncService.java:136` | Org settings not configured; cannot verify invoice currency | Org settings not configured; cannot verify fee note currency | backend message |
| 96 | `integration/accounting/sync/AccountingSyncService.java:152` | Invoice currency %s does not match org default %s | Fee note currency %s does not match org default %s | backend message |
| 97 | `integration/accounting/sync/AccountingSyncService.java:359` | Xero payment currency %s does not match Kazi invoice currency %s | Xero payment currency %s does not match Kazi fee note currency %s | backend message |
| 98 | `integration/accounting/sync/TrustBoundaryGuard.java:125` | Customer '%s' has active trust balance of %s | Client '%s' has active trust balance of %s | backend message |
| 99 | `integration/accounting/xero/XeroAccountingProvider.java:160` | Invoices | Fee notes | backend message |
| 100 | `integration/accounting/xero/XeroCustomerImportService.java:199` | Customer table too large for import | Client table too large for import | backend message |
| 101 | `integration/accounting/xero/XeroCustomerImportService.java:200` | Customer table has  | Client table has  | backend message |
| 102 | `integration/accounting/xero/XeroCustomerImportService.java:318` | Customer import already completed | Client import already completed | backend message |
| 103 | `integration/email/EmailWebhookService.java:225` | Invoice email bounced | Fee note email bounced | backend message |
| 104 | `integration/email/EmailWebhookService.java:232` | An invoice email could not be delivered. Please check the email address and resend. | An fee note email could not be delivered. Please check the email address and resend. | backend message |
| 105 | `invoice/InvoiceCreationService.java:287` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 106 | `invoice/InvoiceCreationService.java:291` | Invoice not editable | Fee note not editable | backend message |
| 107 | `invoice/InvoiceCreationService.java:292` | Disbursement lines can only be added to draft invoices (current status= | Disbursement lines can only be added to draft fee notes (current status= | backend message |
| 108 | `invoice/InvoiceCreationService.java:334` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 109 | `invoice/InvoiceCreationService.java:340` | Tax amount cannot be manually set when invoice lines have tax rates applied. | Tax amount cannot be manually set when fee note lines have tax rates applied. | backend message |
| 110 | `invoice/InvoiceCreationService.java:357` | Invoice not editable | Fee note not editable | backend message |
| 111 | `invoice/InvoiceCreationService.java:384` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 112 | `invoice/InvoiceCreationService.java:388` | Invoice not deletable / Only draft invoices can be deleted | Fee note not deletable / Only draft fee notes can be deleted | backend message |
| 113 | `invoice/InvoiceCreationService.java:464` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 114 | `invoice/InvoiceCreationService.java:468` | Invoice not editable / Line items can only be added to draft invoices | Fee note not editable / Line items can only be added to draft fee notes | backend message |
| 115 | `invoice/InvoiceCreationService.java:562` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 116 | `invoice/InvoiceCreationService.java:566` | Invoice not editable / Line items can only be updated on draft invoices | Fee note not editable / Line items can only be updated on draft fee notes | backend message |
| 117 | `invoice/InvoiceCreationService.java:618` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 118 | `invoice/InvoiceCreationService.java:703` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 119 | `invoice/InvoiceCreationService.java:707` | Invoice not editable / Custom fields can only be updated on draft invoices | Fee note not editable / Custom fields can only be updated on draft fee notes | backend message |
| 120 | `invoice/InvoiceCreationService.java:741` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 121 | `invoice/InvoiceCreationService.java:745` | Invoice not editable / Field groups can only be updated on draft invoices | Fee note not editable / Field groups can only be updated on draft fee notes | backend message |
| 122 | `invoice/InvoiceCreationService.java:786` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 123 | `invoice/InvoiceCreationService.java:790` | Customer not active / Customer must be in ACTIVE status to create an invoice | Client not active / Client must be in ACTIVE status to create an fee note | backend message |
| 124 | `invoice/InvoiceCreationService.java:828` |  is already linked to an invoice |  is already linked to an fee note | backend message |
| 125 | `invoice/InvoiceCreationService.java:857` | Time entry not linked to customer | Time entry not linked to client | backend message |
| 126 | `invoice/InvoiceCreationService.java:932` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 127 | `invoice/InvoiceCreationService.java:936` | Expense not billable / Expense  | Disbursement not billable / Disbursement  | backend message |
| 128 | `invoice/InvoiceCreationService.java:940` | Expense already invoiced | Disbursement already billed | backend message |
| 129 | `invoice/InvoiceCreationService.java:941` | Expense  /  is already linked to an invoice | Disbursement  /  is already linked to an fee note | backend message |
| 130 | `invoice/InvoiceCreationService.java:946` | Expense not linked to customer | Disbursement not linked to client | backend message |
| 131 | `invoice/InvoiceCreationService.java:947` | Expense  /  belongs to a project not linked to customer  | Disbursement  /  belongs to a matter not linked to client  | backend message |
| 132 | `invoice/InvoiceCreationService.java:1018` | Disbursement not linked to customer | Disbursement not linked to client | backend message |
| 133 | `invoice/InvoiceRenderingService.java:70` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 134 | `invoice/InvoiceService.java:115` | Invoice | Fee note | backend message |
| 135 | `invoice/InvoiceService.java:127` | Invoice not found / No invoice found with number  | Fee note not found / No fee note found with number  | backend message |
| 136 | `invoice/InvoiceService.java:138` | Invoice | Fee note | backend message |
| 137 | `invoice/InvoiceTransitionService.java:101` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 138 | `invoice/InvoiceTransitionService.java:106` | Invoice must have at least one line item before approval | Fee note must have at least one line item before approval | backend message |
| 139 | `invoice/InvoiceTransitionService.java:119` |  is already linked to another invoice |  is already linked to another fee note | backend message |
| 140 | `invoice/InvoiceTransitionService.java:151` | Expense not found / No expense found with id … | Disbursement not found / No disbursement found with id … | backend message (404 title) |
| 141 | `invoice/InvoiceTransitionService.java:154` | Expense already billed | Disbursement already billed | backend message |
| 142 | `invoice/InvoiceTransitionService.java:155` | Expense  /  is already linked to another invoice | Disbursement  /  is already linked to another fee note | backend message |
| 143 | `invoice/InvoiceTransitionService.java:212` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 144 | `invoice/InvoiceTransitionService.java:315` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 145 | `invoice/InvoiceTransitionService.java:319` | Only sent invoices can be paid | Only sent fee notes can be paid | backend message |
| 146 | `invoice/InvoiceTransitionService.java:332` | Payment for invoice  | Payment for fee note  | backend message |
| 147 | `invoice/InvoiceTransitionService.java:439` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 148 | `invoice/InvoiceTransitionService.java:447` | Only sent invoices can be paid | Only sent fee notes can be paid | backend message |
| 149 | `invoice/InvoiceTransitionService.java:530` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 150 | `invoice/InvoiceTransitionService.java:534` | Only sent invoices can have their payment link refreshed | Only sent fee notes can have their payment link refreshed | backend message |
| 151 | `invoice/InvoiceTransitionService.java:568` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 152 | `invoice/InvoiceTransitionService.java:573` | Only paid invoices can have a payment reversed (current status:  | Only paid fee notes can have a payment reversed (current status:  | backend message |
| 153 | `invoice/InvoiceTransitionService.java:586` |  does not belong to invoice  |  does not belong to fee note  | backend message |
| 154 | `invoice/InvoiceTransitionService.java:755` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 155 | `invoice/InvoiceValidationService.java:105` | All customer required fields are filled | All client required fields are filled | backend message |
| 156 | `invoice/InvoiceValidationService.java:135` | Customer tax number is set | Client tax number is set | backend message |
| 157 | `invoice/PaymentReconciliationService.java:218` | Online payment for Invoice  | Online payment for Fee note  | backend message |
| 158 | `invoice/PaymentReconciliationService.java:255` | Payment link for Invoice  | Payment link for Fee note  | backend message |
| 159 | `invoice/UnbilledTimeService.java:178` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 160 | `member/ProjectAccessService.java:61` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 161 | `member/ProjectAccessService.java:75` | Cannot edit project / You do not have permission to edit project  | Cannot edit matter / You do not have permission to edit matter  | backend message |
| 162 | `member/ProjectMemberService.java:62` | Member already on project | Member already on matter | backend message |
| 163 | `member/ProjectMemberService.java:63` |  is already a member of project  |  is already a member of matter  | backend message |
| 164 | `member/ProjectMemberService.java:113` | Project member not found | Matter member not found | backend message |
| 165 | `member/ProjectMemberService.java:114` |  is not a member of project  |  is not a member of matter  | backend message |
| 166 | `member/ProjectMemberService.java:118` | Cannot remove project lead | Cannot remove matter lead | backend message |
| 167 | `member/ProjectMemberService.java:146` |  is not a member of project  |  is not a member of matter  | backend message |
| 168 | `member/ProjectMemberService.java:150` | Not the project lead | Not the matter lead | backend message |
| 169 | `member/ProjectMemberService.java:151` |  is not the lead of project  |  is not the lead of matter  | backend message |
| 170 | `member/ProjectMemberService.java:161` |  is not a member of project  |  is not a member of matter  | backend message |
| 171 | `notification/NotificationService.java:531` | Project \ /  has reached %s%% of its %s budget | Matter \ /  has reached %s%% of its %s fee estimate | backend label/registry |
| 172 | `notification/NotificationService.java:559` | Invoice %s for %s has been approved | Fee note %s for %s has been approved | backend label/registry |
| 173 | `notification/template/EmailTemplate.java:22` | New document in your project | New document in your matter | backend label/registry |
| 174 | `notification/template/EmailTemplate.java:24` | You've been added to a project / Hi,\n\n%s\n\nView the project in Kazi.\n\nBest,\nKazi | You've been added to a matter / Hi,\n\n%s\n\nView the matter in Kazi.\n\nBest,\nKazi | backend label/registry |
| 175 | `portal/PortalAuthService.java:156` | Customer account no longer exists | Client account no longer exists | backend message |
| 176 | `portal/PortalAuthService.java:159` | Customer account is no longer active | Client account is no longer active | backend message |
| 177 | `portal/PortalContactService.java:38` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 178 | `portal/PortalContactService.java:44` | A contact with this email already exists for this customer | A contact with this email already exists for this client | backend message |
| 179 | `portal/PortalContactService.java:63` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 180 | `portal/PortalQueryService.java:66` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 181 | `portal/PortalQueryService.java:229` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 182 | `prerequisite/PrerequisiteContext.java:5` | Customer Activation | Client Activation | backend label/registry |
| 183 | `prerequisite/PrerequisiteContext.java:6` | Invoice Generation | Fee note Generation | backend label/registry |
| 184 | `prerequisite/PrerequisiteContext.java:8` | Project Creation | Matter Creation | backend label/registry |
| 185 | `prerequisite/PrerequisiteService.java:138` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 186 | `prerequisite/PrerequisiteService.java:188` | Customer must have an email address or portal contact for invoice delivery | Client must have an email address or portal contact for fee note delivery | backend message |
| 187 | `prerequisite/PrerequisiteService.java:193` | Add a portal contact with email on the customer detail page, | Add a portal contact with email on the client detail page, | backend message |
| 188 | `prerequisite/PrerequisiteService.java:232` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 189 | `prerequisite/PrerequisiteService.java:240` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 190 | `prerequisite/PrerequisiteService.java:275` |  field on the customer profile |  field on the client profile | backend message |
| 191 | `prerequisite/StructuralPrerequisiteCheck.java:123` |  field on the customer profile |  field on the client profile | backend message |
| 192 | `prerequisite/StructuralPrerequisiteCheck.java:166` |  field on the customer profile |  field on the client profile | backend message |
| 193 | `project/Project.java:332` | Invalid project state /  project in status  | Invalid matter state /  matter in status  | backend message |
| 194 | `project/ProjectDeletionGuard.java:40` | Delete or cancel all tasks before deleting the project. | Delete or cancel all tasks before deleting the matter. | backend message |
| 195 | `project/ProjectDeletionGuard.java:44` | Remove all time entries before deleting the project. | Remove all time entries before deleting the matter. | backend message |
| 196 | `project/ProjectDeletionGuard.java:48` | Void or delete all invoices before deleting the project. | Void or delete all fee notes before deleting the matter. | backend message |
| 197 | `project/ProjectDeletionGuard.java:52` | Delete all documents before deleting the project. | Delete all documents before deleting the matter. | backend message |
| 198 | `project/ProjectFieldService.java:125` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 199 | `project/ProjectFieldService.java:150` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 200 | `project/ProjectLifecycleGuard.java:29` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 201 | `project/ProjectLifecycleGuard.java:32` | Project is archived / Project is archived. No modifications allowed. | Matter is archived / Matter is archived. No modifications allowed. | backend message |
| 202 | `project/ProjectLifecycleGuard.java:44` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 203 | `project/ProjectLifecycleGuard.java:47` | Project is not active | Matter is not active | backend message |
| 204 | `project/ProjectLifecycleGuard.java:48` | Project must be in ACTIVE status. Current status:  | Matter must be in ACTIVE status. Current status:  | backend message |
| 205 | `project/ProjectService.java:334` | Invalid project status | Invalid matter status | backend message |
| 206 | `project/ProjectService.java:335` | Invalid project status: ' | Invalid matter status: ' | backend message |
| 207 | `project/ProjectService.java:600` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 208 | `project/ProjectService.java:813` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 209 | `project/ProjectService.java:902` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 210 | `project/ProjectService.java:915` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 211 | `project/ProjectService.java:920` | Cannot delete project | Cannot delete matter | backend message |
| 212 | `project/ProjectService.java:921` | Cannot delete a %s project. Reopen the project first. | Cannot delete a %s matter. Reopen the matter first. | backend message |
| 213 | `project/ProjectService.java:951` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 214 | `project/ProjectService.java:961` | Cannot complete project | Cannot complete matter | backend message |
| 215 | `project/ProjectService.java:962` | Project has %d open task(s). Complete or cancel all tasks before completing the project. | Matter has %d open task(s). Complete or cancel all tasks before completing the matter. | backend message |
| 216 | `project/ProjectService.java:973` | Project has %d unbilled time entry/entries (%.1f hours). Acknowledge to proceed. | Matter has %d unbilled time entry/entries (%.1f hours). Acknowledge to proceed. | backend message |
| 217 | `project/ProjectService.java:1032` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 218 | `project/ProjectService.java:1082` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 219 | `project/ProjectUpcomingDeadlinesService.java:58` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 220 | `projecttemplate/ProjectTemplateService.java:391` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 221 | `projecttemplate/ProjectTemplateService.java:498` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 222 | `projecttemplate/ProjectTemplateService.java:869` | Field definition is not a customer field:  | Field definition is not a client field:  | backend message |
| 223 | `projecttemplate/ProjectTemplateService.java:892` | Cannot save project as template | Cannot save matter as template | backend message |
| 224 | `projecttemplate/ProjectTemplateService.java:893` | Only admins, owners, or project leads may save a project as a | Only admins, owners, or matter leads may save a matter as a | backend message |
| 225 | `projecttemplate/ProjectTemplateService.java:897` | Cannot save project as template | Cannot save matter as template | backend message |
| 226 | `projecttemplate/ProjectTemplateService.java:898` | Only admins, owners, or project leads may save a project as a template. | Only admins, owners, or matter leads may save a matter as a template. | backend message |
| 227 | `report/ReportService.java:71` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 228 | `report/ReportService.java:96` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 229 | `reporting/StandardReportPackSeeder.java:110` | Project | Matter | backend label/registry |
| 230 | `reporting/StandardReportPackSeeder.java:169` | Time entries grouped by member, project, or date for a given period. | Time entries grouped by member, matter, or date for a given period. | backend label/registry |
| 231 | `reporting/StandardReportPackSeeder.java:186` | Customer | Client | backend label/registry |
| 232 | `reporting/StandardReportPackSeeder.java:207` | Invoice Aging Report | Fee note Aging Report | backend label/registry |
| 233 | `reporting/StandardReportPackSeeder.java:213` | Outstanding invoices grouped by age bucket. | Outstanding fee notes grouped by age bucket. | backend label/registry |
| 234 | `reporting/StandardReportPackSeeder.java:231` | Project | Matter | backend label/registry |
| 235 | `reporting/StandardReportPackSeeder.java:242` | Customer | Client | backend label/registry |
| 236 | `reporting/StandardReportPackSeeder.java:279` | Project Profitability Report | Matter Profitability Report | backend label/registry |
| 237 | `reporting/StandardReportPackSeeder.java:285` | Revenue vs. cost per project for a date range. | Revenue vs. cost per matter for a date range. | backend label/registry |
| 238 | `retention/RetentionService.java:341` | Anonymized Customer  | Anonymized Client  | backend message |
| 239 | `setupstatus/CustomerReadinessService.java:53` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 240 | `setupstatus/CustomerReadinessService.java:190` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 241 | `setupstatus/DocumentGenerationReadinessService.java:129` | Project Name | Matter Name | backend message |
| 242 | `setupstatus/DocumentGenerationReadinessService.java:139` | Customer Name | Client Name | backend message |
| 243 | `setupstatus/DocumentGenerationReadinessService.java:142` | Customer Email | Client Email | backend message |
| 244 | `setupstatus/DocumentGenerationReadinessService.java:151` | Invoice Number | Fee note Number | backend message |
| 245 | `setupstatus/DocumentGenerationReadinessService.java:155` | Customer Name | Client Name | backend message |
| 246 | `setupstatus/DocumentGenerationReadinessService.java:159` | Invoice Lines | Fee note Lines | backend message |
| 247 | `setupstatus/ProjectSetupStatusService.java:49` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 248 | `setupstatus/UnbilledTimeSummaryService.java:41` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 249 | `setupstatus/UnbilledTimeSummaryService.java:90` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 250 | `tax/TaxRateService.java:189` |  draft invoice line(s) |  draft fee note line(s) | backend message |
| 251 | `template/CustomerContextBuilder.java:59` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 252 | `template/InvoiceContextBuilder.java:54` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 253 | `template/ProjectContextBuilder.java:62` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 254 | `template/VariableMetadataRegistry.java:117` | Project | Matter | backend label/registry |
| 255 | `template/VariableMetadataRegistry.java:163` | Budget | Fee estimate | backend label/registry |
| 256 | `template/VariableMetadataRegistry.java:180` | Project Members | Matter Members | backend label/registry |
| 257 | `template/VariableMetadataRegistry.java:192` | Customer | Client | backend label/registry |
| 258 | `template/VariableMetadataRegistry.java:217` | Invoice Summary | Fee note Summary | backend label/registry |
| 259 | `template/VariableMetadataRegistry.java:232` | Invoices | Fee notes | backend label/registry |
| 260 | `template/VariableMetadataRegistry.java:315` | Invoice Lines | Fee note Lines | backend label/registry |
| 261 | `timeentry/RateSnapshotService.java:42` | No rate card found. This time entry will generate a zero-amount invoice line item. | No rate card found. This time entry will generate a zero-amount fee note line item. | backend message |
| 262 | `timeentry/TimeEntryService.java:181` | Time entry is part of an invoice. Void the invoice to unlock. | Time entry is part of an fee note. Void the fee note to unlock. | backend message |
| 263 | `timeentry/TimeEntryService.java:234` | Time entry is part of an invoice. Void the invoice to unlock. | Time entry is part of an fee note. Void the fee note to unlock. | backend message |
| 264 | `timeentry/TimeEntryService.java:386` | Time entry is part of an invoice. Void the invoice to unlock. | Time entry is part of an fee note. Void the fee note to unlock. | backend message |
| 265 | `timeentry/TimeEntryService.java:430` | Per-member time breakdown is restricted to project leads, org admins, and org owners | Per-member time breakdown is restricted to matter leads, org admins, and org owners | backend message |
| 266 | `timeentry/TimeEntryService.java:623` | Only the creator or a project lead/admin/owner can modify this time entry | Only the creator or a matter lead/admin/owner can modify this time entry | backend message |
| 267 | `verticals/VerticalModuleRegistry.java:103` | Batch invoice generation across multiple customers in a single run. Best for firms | Batch fee note generation across multiple clients in a single run. Best for firms | backend label/registry |
| 268 | `verticals/VerticalModuleRegistry.java:115` | Customer document-request workflows with portal pickup and acceptance | Client document-request workflows with portal pickup and acceptance | backend label/registry |
| 269 | `verticals/legal/closure/MatterClosureContextBuilder.java:79` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 270 | `verticals/legal/closure/MatterClosureService.java:477` | Project has no retention anchor — it was never closed. | Matter has no retention anchor — it was never closed. | backend message |
| 271 | `verticals/legal/closure/MatterClosureService.java:554` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 272 | `verticals/legal/conflictcheck/AdversePartyService.java:213` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 273 | `verticals/legal/conflictcheck/AdversePartyService.java:218` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 274 | `verticals/legal/courtcalendar/CourtCalendarService.java:161` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 275 | `verticals/legal/courtcalendar/PrescriptionTrackerService.java:100` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 276 | `verticals/legal/disbursement/DisbursementService.java:119` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 277 | `verticals/legal/disbursement/DisbursementService.java:122` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 278 | `verticals/legal/disbursement/DisbursementService.java:365` | Invalid invoice line | Invalid fee note line | backend message |
| 279 | `verticals/legal/disbursement/DisbursementService.java:534` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 280 | `verticals/legal/disbursement/DisbursementService.java:557` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 281 | `verticals/legal/disbursement/DisbursementService.java:670` | Trust transaction project mismatch | Trust transaction matter mismatch | backend message |
| 282 | `verticals/legal/disbursement/DisbursementService.java:671` | Linked trust transaction belongs to a different project | Linked trust transaction belongs to a different matter | backend message |
| 283 | `verticals/legal/statement/StatementOfAccountContextBuilder.java:176` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 284 | `verticals/legal/statement/StatementOfAccountContextBuilder.java:245` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 285 | `verticals/legal/statement/StatementService.java:115` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 286 | `verticals/legal/statement/StatementService.java:273` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 287 | `verticals/legal/statement/StatementService.java:304` | Project not found / No project found with id … | Matter not found / No matter found with id … | backend message (404 title) |
| 288 | `verticals/legal/trustaccounting/interest/InterestService.java:535` | No client ledger card found for customer  | No client ledger card found for client  | backend message |
| 289 | `verticals/legal/trustaccounting/investment/TrustInvestmentService.java:117` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 290 | `verticals/legal/trustaccounting/ledger/ClientLedgerService.java:104` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 291 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:221` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 292 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:307` | Source and target customer cannot be the same | Source and target client cannot be the same | backend message |
| 293 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:313` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 294 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:317` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 295 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:480` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 296 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:549` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 297 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:554` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 298 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:558` | Invoice does not belong to the specified customer | Fee note does not belong to the specified client | backend message |
| 299 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:565` | Invoice must be in APPROVED or SENT status, current status:  | Fee note must be in APPROVED or SENT status, current status:  | backend message |
| 300 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:646` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 301 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:813` | Customer not found / No customer found with id … | Client not found / No client found with id … | backend message (404 title) |
| 302 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:860` | Invoice not found / No invoice found with id … | Fee note not found / No fee note found with id … | backend message (404 title) |
| 303 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:863` | Invoice already paid | Fee note already paid | backend message |
| 304 | `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:864` | Cannot complete fee transfer — invoice is already in PAID status | Cannot complete fee transfer — fee note is already in PAID status | backend message |

### 1.6 Marketing landing page (public site — not in the app shell; separate decision)

`frontend/components/marketing/features-section.tsx:18,21,33,35,41,63,66`, `integrations-section.tsx:17,45`, `ai-section.tsx:27`, `testimonials-section.tsx:4` ("DocTeams replaced three separate tools…" — also the retired product name, which `portal/lib/brand.ts` says must never appear in source). 11 lines; same substitution rules apply if the landing page is in scope.

## 2. G-B1 — the double status badge on the client header

`frontend/components/customers/client-header-card.tsx:120-129` renders, in one row:

1. `<Badge data-testid="customer-status-badge">{STATUS_BADGE[customerStatus].label}</Badge>` — the *record* status (`ClientStatus`: ACTIVE / ARCHIVED …), label "Active".
2. `{lifecycleStatus && <LifecycleStatusBadge status={lifecycleStatus} />}` — the *lifecycle* status (PROSPECT / ONBOARDING / ACTIVE / DORMANT / OFFBOARDING / OFFBOARDED / ANONYMIZED), label "Prospect" / "Active".

So a non-archived prospect reads "Active Prospect" and an activated client reads "Active Active". The record-status badge is redundant whenever `lifecycleStatus` is non-null and `customerStatus === "ACTIVE"`: the only information it adds is "archived", which the page already shows through `getSmartPrimaryAction` ("Restore") and the archived banner. Proposed rule: render the record-status badge only when `customerStatus !== "ACTIVE"` (i.e. "Archived"), otherwise show the lifecycle badge alone. Tests that pin the pair: `frontend/__tests__/components/customers/client-header-card.test.tsx` (`customer-status-badge` / `lifecycle-badge` test ids) and `frontend/__tests__/customer-detail-action-row.test.tsx`; the e2e `customers/customer-lifecycle.spec.ts` reads the lifecycle badge only.

Same file, two more copy sites already in §1.1: `:38` "Activate Customer" (smart action for ONBOARDING) and `:148` `{n} engagement(s)` (counts `linkedProjectCount`, i.e. matters) → "{n} matter(s)".

## 3. Breadcrumbs (G-A2, G-B4, G-C12, G-D10)

Source: `frontend/components/breadcrumbs.tsx`. Label = `SEGMENT_LABELS[segment] ?? (isUuid(segment) && parent ? PARENT_SEGMENT_FALLBACKS[parent] ?? segment : segment)` (`:132-136`). Anything not in `SEGMENT_LABELS` renders the raw slug; any UUID whose parent is not in `PARENT_SEGMENT_FALLBACKS` renders the raw UUID. `components/__tests__/breadcrumbs-routes.test.ts:85` fails on a label whose segment does not exist on disk, and `:96` requires every settings segment to have a label — so the proposed map below only adds segments that exist as directories (verified against the tree).

Route tree under `app/(app)/org/[slug]/` (directories, excluding `_tabs`/`(components)`) with today's crumb and the proposal:

| Route segment path | Crumb today (last segment) | Problem | Proposed label |
|---|---|---|---|
| `dashboard` | **Dashboard** | h1 + nav say "Home" (G-A2, Phase-87-introduced) | `dashboard: "Home"` |
| `my-work` / `my-work/timesheet` | My Work / Timesheet | "My Work" vs nav "My work" (sentence case rule, `settings-nav.ts:19`) | `"my-work": "My work"` |
| `diary` | Diary | ok | — |
| `matters` | Matters | ok | — |
| `matters/[id]` | **Matter** (generic) | G-B4: no name | entity name — see below |
| `matters/[id]/(tabs)` | (route group, no segment) | — | — |
| `matters/new` | **new** | raw slug (G-D10) | `new: "New"` — the label is shared by every `/new` (billing-runs/new, reconciliation/new, settings/…/new); "New" reads correctly in each ("Matters › New", "Checklists › New") |
| `conflict-check` | Conflict Check | ok (title case; nav says "Conflict check") | `"Conflict check"` |
| `legal` | Legal (non-linkable) | ok | — |
| `legal/adverse-parties` | Adverse parties | ok | — |
| `legal/disbursements` | Disbursements | ok | — |
| `legal/disbursements/[id]` | **raw UUID** | G-C12: no parent fallback | `PARENT_SEGMENT_FALLBACKS.disbursements = "Disbursement"` (+ entity name, below) |
| `legal/tariffs` | Tariffs | ok | — |
| `ai` / `ai/reviews` | AI / Reviews | nav says "AI reviews" | `reviews: "AI reviews"` optional |
| `clients` | Clients | ok | — |
| `clients/[id]` | **Client** (generic) | same class as G-B4 | entity name |
| `compliance` | Compliance | nav says "FICA & compliance" | `compliance: "FICA & compliance"` optional |
| `compliance/requests` | **requests** | raw slug (G-D10) | `requests: "Data requests"` |
| `compliance/requests/[id]` | **raw UUID** | no fallback | `PARENT_SEGMENT_FALLBACKS.requests = "Data request"` |
| `information-requests` / `[id]` | Information requests (non-linkable) / **raw UUID** | no fallback | `PARENT_SEGMENT_FALLBACKS["information-requests"] = "Information request"` |
| `documents` | Documents | ok | — |
| `invoices` | Fee notes | ok | — |
| `invoices/[id]` | Fee note (generic) | class G-B4 | entity: the fee-note number `INV-0002` |
| `invoices/[id]/edit` | **edit** | raw slug | `edit: "Edit"` (shared with `settings/documents/templates/[id]/edit`, `settings/matters/checklists/[id]/edit`) |
| `invoices/unbilled` | Unbilled work | ok | — |
| `invoices/billing-runs` | **billing-runs** | raw slug (G-D10) | `"billing-runs": "Fee runs"` (nav label) |
| `invoices/billing-runs/[id]` | **raw UUID** | no fallback | `PARENT_SEGMENT_FALLBACKS["billing-runs"] = "Fee run"` |
| `invoices/billing-runs/new` | **new** | raw slug | `new: "New"` |
| `invoices/collections` | Collections | nav says "Debtors" (G-D10 nav/h1 mismatch, §4) | follow whichever word wins in §4 |
| `profitability` | Profitability | ok | — |
| `reports` / `reports/[reportSlug]` / `reports/profitability` | Reports / **raw slug** (`timesheet-report` etc.) / Profitability | report slug is not a UUID so no fallback fires | add `PARENT_SEGMENT_FALLBACKS.reports = "Report"` AND widen the fallback test from `isUuid` to "parent has a `[param]` child" (or pass the report name — below) |
| `notifications` | Notifications | ok | — |
| `team` | Team | ok | — |
| `trust-accounting` | Trust Accounting | nav zone says "Trust" / "Overview" | `"Trust accounting"` (sentence case) |
| `trust-accounting/transactions` | **transactions** | raw (G-D10) | `transactions: "Transactions"` |
| `trust-accounting/client-ledgers` / `[clientId]` | Client Ledgers / Client Ledger | ok (title case → "Client ledgers") | sentence case optional |
| `trust-accounting/reconciliation` / `reconciliation/new` | **reconciliation** / **new** | raw (G-D10) | `reconciliation: "Reconciliation"`, `new: "New"` |
| `trust-accounting/interest` | **interest** | raw (G-D10) | `interest: "Interest"` |
| `trust-accounting/investments` | **investments** | raw (G-D10) | `investments: "Investments"` |
| `trust-accounting/reports` | Reports | ok (nav says "Trust reports"; the `reports` key is shared with `/reports`, so leave) | — |
| `settings` … families | Settings / Firm / Matters / Documents / Fees & Trust / Connections | ok | — |
| `settings/matters/checklists/[id]`, `settings/matters/request-templates/[id]`, `settings/matters/templates/[id]`, `settings/documents/templates/[id]` | **raw UUID** | no fallback for `checklists`, `request-templates`, `templates` | `PARENT_SEGMENT_FALLBACKS.checklists = "Checklist template"`, `"request-templates": "Request template"`, `templates: "Template"` |
| `settings/*/new` | **new** | raw | `new: "New"` |
| `settings/connections/xero/sync-log`, `…/ai/history`, `…/automations/executions` | Sync log / History / Executions | ok | — |

Complete proposed additions to `SEGMENT_LABELS` (all segments verified on disk): `dashboard: "Home"`, `new: "New"`, `edit: "Edit"`, `requests: "Data requests"`, `"billing-runs": "Fee runs"`, `transactions: "Transactions"`, `reconciliation: "Reconciliation"`, `interest: "Interest"`, `investments: "Investments"`, plus sentence-case fixes (`"my-work": "My work"`, `"trust-accounting": "Trust accounting"`, `"conflict-check": "Conflict check"`, `"client-ledgers": "Client ledgers"`). Proposed additions to `PARENT_SEGMENT_FALLBACKS`: `disbursements: "Disbursement"`, `requests: "Data request"`, `"information-requests": "Information request"`, `"billing-runs": "Fee run"`, `checklists: "Checklist template"`, `"request-templates": "Request template"`, `templates: "Template"`, `reports: "Report"` (needs the non-UUID param case). `breadcrumbs.test.tsx` does not assert "Dashboard" (it asserts Settings/Connections/Matters/Checklists/Automations/Legal/AI), so only `breadcrumbs-routes.test.ts`'s on-disk invariant constrains the change.

Entity names in the last crumb: `Breadcrumbs` is a pathname-only client component mounted once in `org/[slug]/layout.tsx:140`; nothing passes it a name. Both entity pages already hold the name and already publish it client-side through `<RecordRecentItem href label={project.name} />` (`matters/[id]/page.tsx:407`) and `label={customer.name}` (`clients/[id]/page.tsx:478`) into `RecentItemsProvider` (which is a sibling provider above the shell, `layout.tsx:121`). Cheapest correct path: add a `BreadcrumbLabelProvider` (or extend `RecentItemsProvider`) with `setLabel(href, label)`; `RecordRecentItem` already runs on every entity page with `{href, label}`, so `Breadcrumbs` can resolve `href → label` for its last crumb with no page edits — matters, clients, fee notes (`invoices/[id]/page.tsx` has `invoice.invoiceNumber`), disbursements (`legal/disbursements/[id]/page.tsx`), reports (`reports/[reportSlug]`) just need to render `RecordRecentItem` (check which already do: matters and clients do; grep `RecordRecentItem` for the rest). Breadcrumb would then read "Mathebula & Partners › Matters › Dlamini v RAF" and, for G-B4's wish "Clients › Sipho Dlamini › Matter", a client-path crumb needs the matter's `clientId`/name — the matter page has `customers[0].name` (`:434`), so the provider could also accept an optional parent `{href,label}`.

## 4. Settings nav labels (G-D17, G-D18) — `frontend/lib/settings-nav.ts`

31 tabs, five families; each tab's content component is the loader in `app/(app)/org/[slug]/settings/<family>/_tabs/index.ts`.

| Family | id | label | description | content loader (`_tabs/`) | Mismatch |
|---|---|---|---|---|---|
| Firm | general | General | Update firm name, logo, currency and branding | `firm/_tabs/general.tsx` `renderGeneralTab` | — |
| Firm | roles | Roles | Define custom roles and manage team permissions | `roles.tsx` | — |
| Firm | audit-trail | Audit trail | Inspect a paginated feed of audit events for the firm | `audit-trail.tsx` (→ `audit-log-client.tsx`) | — |
| Firm | data-protection | Data protection | Jurisdiction, information officer, retention, processing register and PAIA manual | `data-protection.tsx` | — |
| Firm | data-requests | Data requests | Track and manage data subject access requests | `data-requests.tsx` | — |
| Firm | compliance | Compliance | Configure retention policies, dormancy thresholds, and data request deadlines | `compliance.tsx` (ComplianceSettingsForm: "Customers inactive for this many days…") | copy in §1.1 |
| Firm | features | Features | Enable additional features for your firm | `features.tsx` (module cards from `VerticalModuleRegistry`) | module copy §1.4 |
| Firm | subscription | Subscription | View your billing method and status | `subscription.tsx` | — |
| Matters | templates | Templates | Create and manage reusable matter templates | `matters/_tabs/templates.tsx` (→ `TemplateList.tsx`: "No project templates yet", SOURCE column) | §1.1, §6 |
| Matters | naming | Naming | Configure auto-naming patterns for new matters | `naming.tsx` (→ `project-naming-settings.tsx`: "new projects will be automatically named…", `{customer.name}` placeholder) | §1.1 |
| Matters | custom-fields | Custom fields | Define custom fields and groups for matters, tasks, and clients | `custom-fields.tsx` (→ `custom-fields-content.tsx:255-262` renders pack `group.name/description` raw) | G-A5 pack JSON §1.3 |
| Matters | tags | Tags | Manage firm-wide tags for matters, tasks, and clients | `tags.tsx:29` "Manage tags for projects, tasks, and customers." | §1.1 (tab description already legal; in-page h1 subtitle is not) |
| Matters | checklists | Checklists | Manage checklist templates for client onboarding | `checklists.tsx:40,60` "customer onboarding" | §1.1 |
| Matters | request-templates | Request templates | Create and manage reusable information request templates | `request-templates.tsx` | — |
| Matters | request-settings | Request settings | Configure the default reminder interval for information requests | `request-settings.tsx` | — |
| Documents | templates | Templates | Manage document templates and branding | `documents/_tabs/templates.tsx` (→ `templates-content.tsx`: `PROJECT_SUMMARY: "Project Summary"`, `UploadDocxDialog` entity options "Project/Customer/Invoice") | §1.1; G-D15 overflow §7 |
| Documents | clauses | Clauses | Manage the reusable clause library for document generation | `clauses.tsx` (→ `clause-editor-sheet.tsx:53-55` entity labels) | §1.1 |
| Documents | acceptance | Acceptance | Configure the default expiry period for document acceptance requests | `acceptance.tsx` | — |
| Documents | email | Email | View email delivery logs, stats, and rate limits | `email.tsx` | — |
| Fees & Trust | **tariffs** | **Tariffs** | Manage billing rates, cost rates, and default currency | `fees/_tabs/tariffs.tsx` — h1 **"Rates & Currency"** (`:19-21`, `:50-52`), member billing/cost rates, default currency | **G-D17**: label says Tariffs, page says Rates & Currency; LSSA tariffs live at `/legal/tariffs` (nav "Tariffs" under Fees) and the matter Fees › "Tariffs" sub-tab is rate overrides (`matter-tab-groups.ts`). Architecture named it "Tariffs (ex Rates)" (`architecture/phase87-legal-native-shell.md:973,1010`). Options: (a) rename the tab + h1 to "Rates" / "Billing rates" and keep "Tariffs" for LSSA; (b) keep "Tariffs" and retitle the h1 "Tariffs" with the description. Either way the tab label and h1 must agree. Tests pinning: `__tests__/settings/fees-family.test.tsx` (22 hits), `lib/__tests__/settings-nav.test.ts`, `components/__tests__/settings-sidebar.test.tsx`, `command-palette-dialog.test.tsx` |
| Fees & Trust | tax | Tax | Configure tax registration, labels, and inclusive pricing | `tax.tsx` (→ `TaxSettingsForm.tsx:111`, `tax-rate-table.tsx:69,95`) | G-A4 §1.1 |
| Fees & Trust | time-recording | Time recording | Configure time reminders and the default markup | `time-recording.tsx` (→ `time-tracking-settings-form.tsx:225,236` "Default Expense Markup") | §1.1 |
| Fees & Trust | fee-runs | Fee runs | Configure async thresholds, email rate limits, and the default billing currency | `fee-runs.tsx` (→ `batch-billing-settings.tsx:106` "Customer count…") | §1.1 |
| Fees & Trust | collections | Collections | Configure overdue fee-note reminder stages and escalation | `collections.tsx:32` "overdue-invoice reminder stages" (→ `collections-settings-form.tsx:109,112` "Overdue-Invoice Reminders") | §1.1 |
| Fees & Trust | trust-accounting | Trust accounting | Manage trust accounts, approval settings, and LPFF rates | `trust-accounting.tsx` | — |
| Connections | **xero** | **Xero** | Connect Xero and view its sync status | `connections/_tabs/xero.tsx` — renders `DOMAIN_CONFIG` cards for **Accounting (Xero), AI Assistant, Document Signing, Email Delivery, Payment Gateway, KYC Verification** (`:38-72`, `:190`) then the Xero connection/settings/import/tax-mapping sections | **G-D18**: one tab labelled "Xero" is the whole former Integrations hub. Options: (a) relabel `xero` → "Integrations" (keep `id: "xero"` so `?tab=xero` redirects/palette/specs survive, or add a redirect alias); (b) split: `integrations` (cards) + `xero` (Xero only), which 616D's redirect map (`legacy-redirects.ts` → `?tab=xero`) would need to follow. Tests pinning: `__tests__/settings/connections-family.test.tsx` (13), `__tests__/app/settings/integrations.test.tsx` (42 — the old hub test), `__tests__/app/settings/integrations/sync-log-back-link.test.tsx` |
| Connections | mcp | MCP | Connect your firm's own Claude to Kazi over MCP | `mcp.tsx` | — |
| Connections | ai | AI | Configure AI profile, model preference, and usage budget | `ai.tsx` (→ `ai-profile-form.tsx` "Monthly Budget" — AI spend, keep) | — |
| Connections | notifications | Notifications | Configure notification preferences | `notifications.tsx` (→ `notification-preferences-form.tsx:18-32` "Project Completed", "Invoice Sent", category "Billing & Invoicing") | §1.1 |
| Connections | packs | Packs | Install and manage practice packs | `packs.tsx` | — |
| Connections | automations | Automations | Enable or disable the automation rules installed with your practice packs | `automations.tsx` (→ `trigger-type-badge.tsx:9-13` "Project Status / Customer Status / Invoice Status / Budget Threshold"; rule names from automation JSON §1.3) | G-A4 |

Nav item vs page h1 mismatches (`frontend/lib/nav-items.ts` vs `page.tsx`):

| Nav label (`nav-items.ts`) | Route | Page h1 | Fix |
|---|---|---|---|
| Fee runs (`:195`) | `/invoices/billing-runs` | **"Fee notes"** (`billing-runs/page.tsx:55`) and **"Billing Runs"** on the non-admin branch (`:27`); buttons "New Billing Run" (`:60`, `billing-runs/new/page.tsx:27,38`), detail h1 "Billing Run" (`billing-runs/[id]/page.tsx:33`), empty state "No billing runs" (`:103`) | h1 "Fee runs" on both branches; "New fee run"; detail "Fee run"; "No fee runs yet" |
| Debtors (`:203`) | `/invoices/collections` | **"Collections"** (`collections/page.tsx:87`); breadcrumb `collections: "Collections"`; settings tab "Collections" (keywords `debtors`) | pick one word. "Debtors" is the SA practice term (Home widget is "Debtors", 614B); suggest h1 "Debtors", crumb `collections: "Debtors"`, settings tab stays "Collections" (it configures the reminder policy) or also "Debtors" |
| Home (`:84`) | `/dashboard` | "Home" | breadcrumb only (G-A2, §3) |
| Conflict check / Adverse parties / AI reviews (`:120,127,135`) | `/conflict-check`, `/legal/adverse-parties`, `/ai/reviews` | "Adverse Parties" (`adverse-parties/page.tsx:40`), breadcrumb "Conflict Check" | sentence case |

## 5. G-D16 — tier / plan remnants

`frontend/components/integrations/IntegrationCard.tsx`: `tier?: string` prop (`:62,72`), `isStarter = domain === "AI" && tier === "STARTER"` (`:84`), `{domain === "AI" && tier !== "PRO" && <Badge variant="pro">PRO</Badge>}` (`:182`), the STARTER upsell block "AI Assistant requires the PRO plan" / "Upgrade to Pro" (`:195-205`), and six `disabled={… || isStarter}` guards (`:221,253,261,269,290,320`). The caller `app/(app)/org/[slug]/settings/connections/_tabs/xero.tsx:83-113` derives `tier` from `billing.status` (`"PRO"` when ACTIVE/TRIALING/PENDING_CANCELLATION, else `"STARTER"`) and passes it only for the AI card (`:190`). Because the walked tenant's subscription is flat/manual, `tier` resolved to STARTER → the PRO badge and the disabled AI controls. Removal set: the prop, `isStarter`, the badge, the upsell block, the six guards, the `tier` derivation in `xero.tsx`, and `Badge` variants `starter`/`pro` in `@b2mash/ui` if nothing else uses them (`custom-fields-content.tsx:264` uses `variant="pro"` for the "Pack" badge — rename that variant or keep it as a neutral style). Tests: `__tests__/integrations/ai-integration-card.test.tsx` (5 hits), `__tests__/app/settings/integrations.test.tsx` (42 hits incl. PRO/Upgrade), `__tests__/lib/types/integrations.test.ts` (2), `__tests__/settings/subscription.test.tsx` (1). Other `tier|PRO|Upgrade|plan` hits in `frontend/`, `portal/`, `packages/` are false positives (`StarterKit` tiptap import, an "unlimited members" comment in `invite-member-form.tsx:377`, `FicaStatusCard.tsx:15` comment). The `starter`/`pro` Badge variants are also listed in `frontend/CLAUDE.md` ("Badge — Semantic variants: … `starter`, `pro`").

## 6. G-A7 — `TemplateList.tsx` source label

`frontend/components/templates/TemplateList.tsx:116-120`: `template.source === "FROM_PROJECT" ? <Badge variant="lead">From Project</Badge> : <Badge variant="neutral">Manual</Badge>`. The frontend type `lib/api/templates.ts:38` declares `source: "MANUAL" | "FROM_PROJECT"` — it does not know about the third value. Backend: there is **no enum** on the matter-template entity; `ProjectTemplateService.java:151,200,313` writes `"MANUAL"`, `:403,468` writes `"FROM_PROJECT"`, and `seeder/ProjectTemplatePackSeeder.java:18,101` writes `"SEEDER"` (the walked DB has `project_templates.source = SEEDER` for all six pack templates). (`template/TemplateSource.java` and `informationrequest/TemplateSource.java` are enums for *document* and *request* templates, not matter templates.) Fix: widen the type to `"MANUAL" | "FROM_PROJECT" | "SEEDER"` and map `SEEDER → "Pack"` (`variant="pro"`-style as the custom-fields "Pack" badge), `FROM_PROJECT → "From matter"`, `MANUAL → "Manual"`. Test: `__tests__/templates/TemplateList.test.tsx` (3 hits on "From Project"/"Manual").

## 7. Layout (G-D15, G-D20, G-A3)

### 7a. Shell chain and the missing `min-w-0` (G-D15 and the 615C/616A workarounds)

Chain: `app/(app)/org/[slug]/layout.tsx:127-157` →
```
<div className="flex min-h-screen">                       // :128 row: sidebar + content
  <DesktopSidebar … />                                    // aside sticky w-60 bg-slate-950 (desktop-sidebar.tsx:56)
  <div className="flex flex-1 flex-col">                  // :131  ← no min-w-0
    <header className="sticky top-0 … px-4 md:px-6">      // :132 breadcrumbs + bell + avatar (the clipped controls in shot 11)
    <main id="main-content" className="bg-background flex-1">           // :146 ← no min-w-0 / no overflow
      <div className="mx-auto max-w-7xl px-6 py-6 lg:px-10">{children}</div>   // :149
```
A flex item's default `min-width: auto` equals its content's min-content width. With `w-60` sidebar + `flex-1` column and no `min-w-0`, a table whose min-content width exceeds `viewport − 240px` widens the column instead of overflowing inside it, so `document.documentElement.scrollWidth` grows (1519 at 1440 px in shot 11), the sticky header stretches with it and its right-aligned avatar/bell land off-screen. Every inner `overflow-x-auto` wrapper (e.g. `settings/documents/_tabs/templates-content.tsx:128`, `TemplateList.tsx:70`, `settings/matters/_tabs/checklists.tsx:64`, `request-templates.tsx:68`, `tags-content.tsx:60`, `tax-rate-table.tsx:125`, `org-documents-section.tsx:77`) is inert for the same reason — an overflow box only scrolls when an ancestor constrains its width.

Local workarounds already shipped (evidence of the pattern):
- 616A `frontend/components/settings/settings-family-tabs.tsx:79-85` — comment: "`@container` (inline-size containment) keeps the `w-max` strip's width out of the page's min-content width — **the app shell's flex column has no `min-w-0`**, so without it the strip widened the whole page (1024px viewport: document 1250px wide)". It wraps the tab strip in `@container -mx-1 overflow-x-auto`.
- 616A `app/(app)/org/[slug]/settings/layout.tsx:17` — `<div className="min-w-0 flex-1">{children}</div>` beside the settings rail (works for the settings column, but the outer shell column above it still has no `min-w-0`, so a table wider than the viewport still pushes the page: that is G-D15).
- 615C `app/(app)/org/[slug]/matters/page.tsx:215` and 615D `clients/page.tsx:184` — `<div className="overflow-x-auto pb-1">` around the list tables; 615C `components/matters/matters-table.tsx:121` `max-w-[14rem] min-w-0` and 615D `clients/clients-table.tsx:162,174` `min-w-0 … overflow-hidden` per cell.

One-line structural fix: add `min-w-0` to the content column (`layout.tsx:131` → `flex min-w-0 flex-1 flex-col`) and to `<main>` (`:146` → `min-w-0`). Then the inner `overflow-x-auto` wrappers scroll in place at 1440 px, the header stays within the viewport, and the `@container` and per-page wrappers become belt-and-braces rather than load-bearing. `mobile-sidebar.tsx` is inside the header and unaffected. A/B to prove G-D15: `document.documentElement.scrollWidth` at 1440×900 on `/settings/documents?tab=templates` before/after.

### 7b. G-D20 — Diary week-view title truncation

`frontend/components/diary/diary-week-view.tsx:30-31`: `WEEK_GRID = "grid grid-cols-1 gap-2 md:grid-cols-7"`, `DAY_CARD = "gap-0 py-0 md:min-h-24"`. At 1440 px the content column is ≈1120 px wide (max-w-7xl minus gutters), so seven columns ≈ 150 px each; each day `Card` renders `DiaryEventRow` (`diary-event-row.tsx:88-125`): a `Link` with `flex min-w-0 flex-1 items-start gap-2 … pl-2 pr-2`, a `size-4` kind icon, then `<span className="min-w-0 flex-1">` → `<span className="flex items-baseline gap-2">` → time (`font-mono text-xs`) + **title `<span className="truncate font-medium">`** (`:108-113`). The title shares its row with the time string and is `truncate`d (single line, ellipsis), so in a 150 px column minus padding, icon and time, "G…" is all that fits; the second line (`:116-122`: subtype, `truncate` matter name, reference, fee earner) is `flex-wrap`, so it wraps but the title does not. The same row component serves Day/List views (`showDate`), where width is ample.

Proposal: give `DiaryEventRow` a `density="compact"` prop used by the week view (or branch on a `week` boolean): stack time above title (`flex-col` instead of `flex items-baseline gap-2`), replace `truncate` on the title with `line-clamp-2 break-words`, drop the fee-earner/reference tokens in compact mode (keep them in the `title=` tooltip / `aria-label`), and set `DAY_CARD` to allow `min-w-0`. Alternatively render week events as a count pill + popover when the column is narrower than ~180 px (`@container` query on the day card). Tests touching the row: `frontend/__tests__/diary/*` (not pinned to the class list; check `diary-week-view` snapshot tests before changing structure).

### 7c. G-A3 — sidebar org name in `var(--brand-color)`

`frontend/components/desktop-sidebar.tsx:72-77`:
```
<span className="truncate text-xs font-medium opacity-80" style={{ color: "var(--brand-color)" }}>{orgName ?? slug}</span>
```
on `bg-slate-950` (`:56`); `--brand-color` is set on `document.documentElement` from `OrgSettings.brandColor` (`:42-54`, GAP-L-26) with no contrast guard, so a dark brand (`#1B3A4B`, as in the walk) at `opacity-80` on near-black is illegible. `mobile-sidebar.tsx` receives the same `brandColor` prop (check for the same `style`). `KAZI` wordmark stays white.

Contrast-safe options (pick one; (1) is the smallest change):
1. Never use the brand colour as text on the dark rail. Render the org name `text-white/80` and show the brand as an accent: a 3 px left border / 8 px dot / avatar ring (`style={{ borderColor: "var(--brand-color)" }}` or `backgroundColor`) next to the name. Brand still visible, text always readable.
2. Compute contrast client-side (relative luminance per WCAG 2.x from the hex; `#1B3A4B` ≈ 0.04 → contrast vs `slate-950` ≈ 1.3:1) and fall back to `text-white` when `< 4.5:1`; a 10-line helper in `lib/` with a unit test — keep the brand colour only when it passes.
3. Blend: `color-mix(in oklch, var(--brand-color) 40%, white)` via a `--brand-on-dark` token computed in CSS, which lifts any brand colour to a legible tint on the dark rail without JS (Tailwind v4 / modern browsers support `color-mix`).
Either way also drop `opacity-80` (it only lowers contrast). Test: `frontend/components/__tests__/desktop-sidebar.test.tsx` (check whether it asserts the inline `style`).

## 8. Tests that assert on the strings / labels in this dossier

Method: fixed-string grep for ~70 census strings over `*.test.ts(x)` / `*.spec.ts` in `frontend/`, `portal/`, `packages/` and a targeted grep over `backend/src/test`. Unit/vitest hits by file (count = matching lines; the full line list follows):

| Test file | hits |
|---|---|
| `frontend/__tests__/app/settings/integrations.test.tsx` | 42 |
| `frontend/__tests__/settings/fees-family.test.tsx` | 22 |
| `frontend/__tests__/settings/connections-family.test.tsx` | 13 |
| `frontend/components/__tests__/nav-zone.test.tsx` | 11 |
| `frontend/__tests__/components/projects/kpi-dashboard.test.tsx` | 11 |
| `frontend/__tests__/components/customers/edit-customer-dialog.test.tsx` | 10 |
| `frontend/__tests__/customer-detail-action-row.test.tsx` | 8 |
| `frontend/components/__tests__/command-palette-dialog.test.tsx` | 8 |
| `frontend/lib/__tests__/nav-items.test.ts` | 7 |
| `frontend/__tests__/app/settings/integrations/sync-log-back-link.test.tsx` | 7 |
| `frontend/__tests__/components/projects/overview-tab-setup.test.tsx` | 7 |
| `frontend/__tests__/budget-panel.test.tsx` | 6 |
| `frontend/__tests__/notifications/notification-preferences.test.tsx` | 5 |
| `frontend/__tests__/integrations/ai-integration-card.test.tsx` | 5 |
| `frontend/__tests__/app/customers/lifecycle-transition.test.tsx` | 5 |
| `frontend/__tests__/field-definitions/FieldDefinitionDialog.test.tsx` | 5 |
| `frontend/lib/__tests__/settings-nav.test.ts` | 4 |
| `frontend/__tests__/role-dialog.test.tsx` | 4 |
| `frontend/__tests__/team/member-role-management.test.tsx` | 4 |
| `frontend/components/__tests__/breadcrumbs.test.tsx` | 4 |
| `frontend/__tests__/trust-dashboard.test.tsx` | 4 |
| `frontend/__tests__/comments/comment-section.test.tsx` | 3 |
| `frontend/__tests__/module-gating.test.tsx` | 3 |
| `frontend/__tests__/settings/firm-family.test.tsx` | 3 |
| `frontend/__tests__/legal/legal-wire-bodies.test.ts` | 3 |
| `frontend/__tests__/templates/TemplateList.test.tsx` | 3 |
| `frontend/components/__tests__/settings-sidebar.test.tsx` | 3 |
| `frontend/__tests__/components/customers/client-header-card.test.tsx` | 3 |
| `frontend/__tests__/trust-coexistence.test.tsx` | 2 |
| `frontend/__tests__/profitability-page.test.tsx` | 2 |
| `frontend/__tests__/compliance/LifecycleTransitionDropdown.test.tsx` | 2 |
| `frontend/__tests__/legal/project-legal-tabs.test.tsx` | 2 |
| `frontend/__tests__/lib/types/integrations.test.ts` | 2 |
| `frontend/__tests__/components/my-work/weekly-time-summary.test.tsx` | 2 |
| `frontend/__tests__/components/invoices/invoice-generation-prereq.test.tsx` | 2 |
| `frontend/__tests__/components/customers/customer-detail-integration.test.tsx` | 2 |
| `frontend/components/invoices/__tests__/unbilled-summary-expenses.test.tsx` | 2 |
| `frontend/__tests__/components/customers/customer-setup-guidance.test.tsx` | 2 |
| `portal/app/login/__tests__/page.test.tsx` | 1 |
| `portal/lib/__tests__/auth.test.ts` | 1 |
| `frontend/__tests__/project-rates.test.tsx` | 1 |
| `frontend/__tests__/invoices/invoice-generation-with-expenses.test.tsx` | 1 |
| `frontend/__tests__/invoices/invoice-generation-validation.test.tsx` | 1 |
| `frontend/__tests__/customer-rates.test.tsx` | 1 |
| `frontend/__tests__/billing-admin.test.tsx` | 1 |
| `frontend/__tests__/settings/subscription.test.tsx` | 1 |
| `frontend/__tests__/project-lifecycle-detail.test.tsx` | 1 |
| `frontend/__tests__/settings/time-recording-settings.test.tsx` | 1 |
| `frontend/__tests__/legal/tariff-schedule.test.tsx` | 1 |
| `frontend/__tests__/lib/constants/matter-tab-groups.test.ts` | 1 |
| `frontend/__tests__/lib/constants/client-tab-groups.test.ts` | 1 |
| `frontend/__tests__/automations/rule-list.test.tsx` | 1 |
| `frontend/__tests__/invite-role-selection.test.tsx` | 1 |
| `frontend/__tests__/customer-financials.test.tsx` | 1 |
| `frontend/__tests__/components/documents/customer-documents-panel.test.tsx` | 1 |
| `frontend/__tests__/components/prerequisite/prerequisite-modal.test.tsx` | 1 |
| `frontend/__tests__/components/customers/completeness-visibility.test.tsx` | 1 |
| `frontend/__tests__/components/customers/customer-contact-card.test.tsx` | 1 |
| `frontend/__tests__/components/projects/overflow-actions-menu.test.tsx` | 1 |
| `frontend/__tests__/components/dialog-family-ssr.snapshot.test.tsx` | 1 |
| `frontend/__tests__/components/customers/create-customer-dialog.test.tsx` | 1 |
| `frontend/__tests__/components/customers/client-overview-tab.test.tsx` | 1 |
| `frontend/__tests__/components/customers/customer-address-block.test.tsx` | 1 |
| `frontend/__tests__/components/customers/archive-customer-dialog.test.tsx` | 1 |

Backend tests pinned to strings this PR would change:

| Test | Pins |
|---|---|
| `project/ProjectIntegrationTest.java:323,337,346` | ProblemDetail title "Project not found" |
| `customer/CustomerIntegrationTest.java:356`, `customer/CustomerCollectionsExemptionTest.java:163`, `verticals/legal/trustaccounting/ledger/ClientLedgerControllerTest.java:200` | "Customer not found" |
| `collections/CollectionsReadApiTest.java:327`, `invoice/InvoiceSentDocumentPersistenceIntegrationTest.java:249` | "Invoice not found" |
| `customerbackend/controller/PortalTrustControllerIntegrationTest.java:298,314` | "Project not found" (portal) |
| `notification/channel/EmailNotificationChannelIntegrationTest.java:240` | `Project "Website Redesign" has reached 85% of its time budget` |
| `notification/InvoiceNotificationIntegrationTest.java:193` | title contains "has been approved" (safe) |
| `notification/template/EmailContextBuilderTest.java:84,130` | `invoiceTerm` = "Invoice" for non-legal profile (EmailTerminology; legal-za → "Fee Note") |
| `reporting/ReportingControllerTest.java:138` | category label "Project" |
| `template/VariableMetadataEndpointTest.java:66,70,100,131,135,136` | group labels "Project"/"Customer"/"Invoice" |
| `template/VisualRegressionTest.java:104,143` | `<h2>Project Summary Report</h2>`, `<h2>Invoice Cover Letter</h2>` |
| `template/InvoiceCoverLetterRenderIntegrationTest.java` (LZKC-010) | pack template "Invoice Cover Letter" by name |
| `template/TemplateFieldPackLinkageTest.java:77`, `clause/ClauseRepositoryIntegrationTest.java:127` | fixture text only |
| `audit/EntityTypeLiteralStabilityTest` (ADR-342) | stored entity literals `project`/`customer` — must NOT change; only labels change |

Playwright specs with hits (run on the e2e stack after the fix): `e2e/tests/legal-lifecycle/day-90-review.spec.ts` (3), `day-00-firm-setup.spec.ts` (1), `legal-depth-ii/day-14-conveyancing.spec.ts` (2), `finance/profitability.spec.ts` (2), `demo-full-90day.spec.ts` (2), `projects/project-budget.spec.ts` (1), `navigation/sidebar-navigation.spec.ts` (1), `customers/customer-lifecycle.spec.ts` (1), `demo-recording.spec.ts` (1), `null-prefetch-probe.spec.ts` (1 — "dashboard" route only).

## Appendix A — full vitest/Playwright hit lines (259)

```
portal/app/login/__tests__/page.test.tsx:77:            message: "If an account exists, a link has been sent.",
portal/lib/__tests__/auth.test.ts:26:const mockCustomer: CustomerInfo = {
frontend/__tests__/trust-coexistence.test.tsx:63:  fetchDashboardData: vi.fn().mockResolvedValue(null),
frontend/__tests__/trust-coexistence.test.tsx:171:    // Simulate a client ledger with a deposit that has been approved,
frontend/__tests__/profitability-page.test.tsx:193:      // Beta Project has null margin/marginPercent
frontend/__tests__/profitability-page.test.tsx:221:      // Beta Project has no customer
frontend/lib/__tests__/nav-items.test.ts:127:    ["Fee runs", "INVOICING", "bulk_billing"],
frontend/lib/__tests__/nav-items.test.ts:128:    ["Debtors", "INVOICING", undefined],
frontend/lib/__tests__/nav-items.test.ts:130:    ["Tariffs", undefined, "lssa_tariff"],
frontend/lib/__tests__/nav-items.test.ts:141:  it("Fees: Unbilled work first, then Fee notes, Fee runs, Debtors, Disbursements, Tariffs (612B)", () => {
frontend/lib/__tests__/nav-items.test.ts:145:      "Fee runs",
frontend/lib/__tests__/nav-items.test.ts:146:      "Debtors",
frontend/lib/__tests__/nav-items.test.ts:148:      "Tariffs",
frontend/__tests__/project-rates.test.tsx:99:    expect(screen.getByText("Project Rate Overrides")).toBeInTheDocument();
frontend/lib/__tests__/settings-nav.test.ts:47:  // D20 (616C): Tariffs is the Fees family's first tab and its former page let FINANCIAL_VISIBILITY
frontend/lib/__tests__/settings-nav.test.ts:282:  it("keeps Tariffs (the Fees first tab) open to members", () => {
frontend/lib/__tests__/settings-nav.test.ts:290:  it("falls a member back to Xero for the gated Connections tabs", () => {
frontend/lib/__tests__/settings-nav.test.ts:298:  it("keeps Xero (the Connections first tab) and Notifications open to members", () => {
frontend/__tests__/notifications/notification-preferences.test.tsx:50:    expect(screen.getByText("Invoice Approved")).toBeInTheDocument();
frontend/__tests__/notifications/notification-preferences.test.tsx:51:    expect(screen.getByText("Invoice Sent")).toBeInTheDocument();
frontend/__tests__/notifications/notification-preferences.test.tsx:52:    expect(screen.getByText("Invoice Paid")).toBeInTheDocument();
frontend/__tests__/notifications/notification-preferences.test.tsx:53:    expect(screen.getByText("Invoice Voided")).toBeInTheDocument();
frontend/__tests__/notifications/notification-preferences.test.tsx:65:    expect(screen.getByText("Billing & Invoicing")).toBeInTheDocument();
frontend/__tests__/invoices/invoice-generation-with-expenses.test.tsx:88:describe("Invoice Generation — expense selection", () => {
frontend/__tests__/comments/comment-section.test.tsx:61:  it("shows 'Customer visible' badge for SHARED comments", () => {
frontend/__tests__/comments/comment-section.test.tsx:72:    expect(screen.getByText("Customer visible")).toBeInTheDocument();
frontend/__tests__/comments/comment-section.test.tsx:128:    expect(screen.getByText("Customer visible")).toBeInTheDocument();
frontend/__tests__/invoices/invoice-generation-validation.test.tsx:89:describe("Invoice Generation Validation", () => {
frontend/__tests__/module-gating.test.tsx:52:  it("Fee runs nav item exists in the fees zone with requiredModule: bulk_billing", () => {
frontend/__tests__/module-gating.test.tsx:54:    const billingRunsItem = feesZone?.items.find((i) => i.label === "Fee runs");
frontend/__tests__/module-gating.test.tsx:59:  it("Fees & Trust › Fee runs settings tab has requiredModule: bulk_billing", () => {
frontend/__tests__/budget-panel.test.tsx:66:  it("hides Set Budget button for non-managers in empty state", () => {
frontend/__tests__/budget-panel.test.tsx:94:    expect(screen.getByText("Budget Status")).toBeInTheDocument();
frontend/__tests__/budget-panel.test.tsx:166:    // Should show Over Budget badge
frontend/__tests__/budget-panel.test.tsx:167:    const overBudgetBadges = screen.getAllByText("Over Budget");
frontend/__tests__/budget-panel.test.tsx:220:      expect(screen.getByRole("heading", { name: "Edit Budget" })).toBeInTheDocument();
frontend/__tests__/budget-panel.test.tsx:237:    expect(screen.getByText("Budget Status")).toBeInTheDocument();
frontend/__tests__/integrations/ai-integration-card.test.tsx:42:  tier: "PRO",
frontend/__tests__/integrations/ai-integration-card.test.tsx:70:    expect(screen.getByText("PRO")).toBeInTheDocument();
frontend/__tests__/integrations/ai-integration-card.test.tsx:74:    render(<IntegrationCard {...defaultProps} tier="PRO" integration={null} />);
frontend/__tests__/integrations/ai-integration-card.test.tsx:76:    expect(screen.queryByText("PRO")).not.toBeInTheDocument();
frontend/__tests__/integrations/ai-integration-card.test.tsx:84:    expect(screen.getByText("Upgrade to Pro")).toBeInTheDocument();
frontend/__tests__/customer-rates.test.tsx:97:    expect(screen.getByText("Customer Rate Overrides")).toBeInTheDocument();
frontend/__tests__/role-dialog.test.tsx:97:    // Project Management should not be checked
frontend/__tests__/role-dialog.test.tsx:98:    expect(screen.getByRole("checkbox", { name: /Project Management/i })).toHaveAttribute(
frontend/__tests__/role-dialog.test.tsx:112:    // Add Project Management capability
frontend/__tests__/role-dialog.test.tsx:113:    await user.click(screen.getByLabelText("Project Management"));
frontend/__tests__/billing-admin.test.tsx:93:    const badge = screen.getByText("Manual");
frontend/__tests__/settings/subscription.test.tsx:76:    expect(screen.getByText("Manual")).toBeInTheDocument();
frontend/__tests__/project-lifecycle-detail.test.tsx:84:      error: "Project has unbilled time entries.",
frontend/__tests__/compliance/LifecycleTransitionDropdown.test.tsx:53:  it("shows 'Offboard Customer' transition for ACTIVE status", async () => {
frontend/__tests__/compliance/LifecycleTransitionDropdown.test.tsx:59:    expect(screen.getByText("Offboard Customer")).toBeInTheDocument();
frontend/__tests__/settings/firm-family.test.tsx:291:        "Tariffs",
frontend/__tests__/settings/firm-family.test.tsx:303:      expect(feeTabNames()).toEqual(["Tariffs", "Tax", "Time recording", "Collections"]);
frontend/__tests__/settings/firm-family.test.tsx:306:        /don.t have access to Trust accounting — showing Tariffs instead/
frontend/__tests__/settings/time-recording-settings.test.tsx:57:    expect(screen.getByLabelText("Default Expense Markup (%)")).toBeInTheDocument();
frontend/__tests__/customer-detail-action-row.test.tsx:31:  updateCustomer: (...args: unknown[]) => mockUpdateCustomer(...args),
frontend/__tests__/customer-detail-action-row.test.tsx:32:  archiveCustomer: (...args: unknown[]) => mockArchiveCustomer(...args),
frontend/__tests__/customer-detail-action-row.test.tsx:46:const activeCustomer: Client = {
frontend/__tests__/customer-detail-action-row.test.tsx:110:      expect(screen.getByText("Edit Customer")).toBeInTheDocument();
frontend/__tests__/customer-detail-action-row.test.tsx:128:    expect(screen.queryByText("Edit Customer")).not.toBeInTheDocument();
frontend/__tests__/customer-detail-action-row.test.tsx:138:      expect(screen.getByText("Edit Customer")).toBeInTheDocument();
frontend/__tests__/customer-detail-action-row.test.tsx:150:      expect(screen.getByText("Edit Customer")).toBeInTheDocument();
frontend/__tests__/customer-detail-action-row.test.tsx:164:    expect(screen.queryByText("Edit Customer")).not.toBeInTheDocument();
frontend/__tests__/settings/connections-family.test.tsx:27:  renderXeroTab: loaders.xero,
frontend/__tests__/settings/connections-family.test.tsx:67:const ALL_CONNECTIONS_TABS = ["Xero", "MCP", "AI", "Notifications", "Packs", "Automations"];
frontend/__tests__/settings/connections-family.test.tsx:105:  it("renders all six tabs in §12.7.1 order for an owner and loads only Xero", async () => {
frontend/__tests__/settings/connections-family.test.tsx:108:    expect(screen.getByRole("tab", { name: "Xero" })).toHaveAttribute("aria-selected", "true");
frontend/__tests__/settings/connections-family.test.tsx:132:  it("falls back to Xero for an unknown tab silently — never a 404", async () => {
frontend/__tests__/settings/connections-family.test.tsx:140:  it("shows a plain member only Xero and Notifications — gated tabs are absent, not disabled", async () => {
frontend/__tests__/settings/connections-family.test.tsx:143:    expect(tabNames()).toEqual(["Xero", "Notifications"]);
frontend/__tests__/settings/connections-family.test.tsx:153:    expect(tabNames()).toEqual(["Xero", "MCP", "Notifications"]);
frontend/__tests__/settings/connections-family.test.tsx:162:    "falls back to Xero with a notice when a member asks for %s",
frontend/__tests__/settings/connections-family.test.tsx:168:        new RegExp(`don.t have access to ${label} — showing Xero instead`)
frontend/__tests__/settings/connections-family.test.tsx:192:    expect(screen.getByRole("tab", { name: "Xero" })).toHaveAttribute("aria-selected", "false");
frontend/__tests__/settings/connections-family.test.tsx:214:        title: "Connections — Xero · Settings",
frontend/__tests__/settings/connections-family.test.tsx:221:        title: "Connections — Xero · Settings",
frontend/__tests__/settings/fees-family.test.tsx:8:// module-gated tabs (Fee runs: bulk_billing, Trust accounting: trust_accounting), so the shared
frontend/__tests__/settings/fees-family.test.tsx:27:  renderTariffsTab: loaders.tariffs,
frontend/__tests__/settings/fees-family.test.tsx:61:  "Tariffs",
frontend/__tests__/settings/fees-family.test.tsx:64:  "Fee runs",
frontend/__tests__/settings/fees-family.test.tsx:109:  it("renders all six tabs in §12.7.1 order for an owner with both modules and loads only Tariffs", async () => {
frontend/__tests__/settings/fees-family.test.tsx:112:    expect(screen.getByRole("tab", { name: "Tariffs" })).toHaveAttribute("aria-selected", "true");
frontend/__tests__/settings/fees-family.test.tsx:125:    expect(tabNames()).toEqual(["Tariffs", "Tax", "Time recording", "Collections"]);
frontend/__tests__/settings/fees-family.test.tsx:128:  it("shows Fee runs but not Trust accounting to an admin with bulk_billing only", async () => {
frontend/__tests__/settings/fees-family.test.tsx:132:    expect(tabNames()).toEqual(["Tariffs", "Tax", "Time recording", "Fee runs", "Collections"]);
frontend/__tests__/settings/fees-family.test.tsx:138:    expect(tabNames()).toEqual(["Tariffs", "Tax", "Time recording", "Collections"]);
frontend/__tests__/settings/fees-family.test.tsx:142:  it("falls back to Tariffs with a notice when the module of a requested tab is off", async () => {
frontend/__tests__/settings/fees-family.test.tsx:147:      /don.t have access to Trust accounting — showing Tariffs instead/
frontend/__tests__/settings/fees-family.test.tsx:165:  it("falls back to Tariffs for an unknown tab silently — never a 404", async () => {
frontend/__tests__/settings/fees-family.test.tsx:171:  // D19 (D6/D18 rule) + D20: a member keeps only Tariffs (its loader keeps the in-page
frontend/__tests__/settings/fees-family.test.tsx:173:  it("shows a member only Tariffs — admin-only tabs are absent, not disabled", async () => {
frontend/__tests__/settings/fees-family.test.tsx:176:    expect(tabNames()).toEqual(["Tariffs"]);
frontend/__tests__/settings/fees-family.test.tsx:183:  it("falls back to Tariffs with a notice when a member asks for Tax", async () => {
frontend/__tests__/settings/fees-family.test.tsx:188:      /don.t have access to Tax — showing Tariffs instead/
frontend/__tests__/settings/fees-family.test.tsx:208:    expect(screen.getByRole("tab", { name: "Tariffs" })).toHaveAttribute("aria-selected", "false");
frontend/__tests__/settings/fees-family.test.tsx:225:        title: "Fees & Trust — Fee runs · Settings",
frontend/__tests__/settings/fees-family.test.tsx:228:        title: "Fees & Trust — Tariffs · Settings",
frontend/__tests__/settings/fees-family.test.tsx:235:        title: "Fees & Trust — Tariffs · Settings",
frontend/__tests__/app/customers/lifecycle-transition.test.tsx:85:      expect(screen.getByText("Prerequisites: Customer Activation")).toBeInTheDocument();
frontend/__tests__/app/customers/lifecycle-transition.test.tsx:111:    expect(screen.queryByText("Prerequisites: Customer Activation")).not.toBeInTheDocument();
frontend/__tests__/app/customers/lifecycle-transition.test.tsx:143:      expect(screen.getByText("Prerequisites: Customer Activation")).toBeInTheDocument();
frontend/__tests__/app/customers/lifecycle-transition.test.tsx:156:          { label: "Projects linked", complete: true },
frontend/__tests__/app/customers/lifecycle-transition.test.tsx:177:        steps={[{ label: "Projects linked", complete: true }]}
frontend/__tests__/app/settings/integrations.test.tsx:3:import { renderXeroTab } from "@/app/(app)/org/[slug]/settings/connections/_tabs/xero";
frontend/__tests__/app/settings/integrations.test.tsx:5:// Phase 87 S9 (616D, ADR-341 §12.7.1): the Connections › Xero tab merges the former integrations
frontend/__tests__/app/settings/integrations.test.tsx:6:// hub and the former Xero page — the hub for everyone, then a Xero section that keeps the former
frontend/__tests__/app/settings/integrations.test.tsx:7:// Xero page's INTEGRATION_MANAGE denial.
frontend/__tests__/app/settings/integrations.test.tsx:8:const { mockCaps, mockGetXeroConnection, mockGetXeroTaxMappings, mockGetXeroSettings } = vi.hoisted(
frontend/__tests__/app/settings/integrations.test.tsx:11:    mockGetXeroConnection: vi.fn(),
frontend/__tests__/app/settings/integrations.test.tsx:12:    mockGetXeroTaxMappings: vi.fn(),
frontend/__tests__/app/settings/integrations.test.tsx:13:    mockGetXeroSettings: vi.fn(),
frontend/__tests__/app/settings/integrations.test.tsx:30:  getXeroConnection: () => mockGetXeroConnection(),
frontend/__tests__/app/settings/integrations.test.tsx:31:  getXeroTaxMappings: () => mockGetXeroTaxMappings(),
frontend/__tests__/app/settings/integrations.test.tsx:32:  getXeroSettings: () => mockGetXeroSettings(),
frontend/__tests__/app/settings/integrations.test.tsx:47:// The former Xero page's client components — marker stubs (this test pins the loader's composition).
frontend/__tests__/app/settings/integrations.test.tsx:48:vi.mock("@/components/integrations/XeroConnectionCard", () => ({
frontend/__tests__/app/settings/integrations.test.tsx:49:  XeroConnectionCard: () => <div data-testid="xero-connection-card" />,
frontend/__tests__/app/settings/integrations.test.tsx:51:vi.mock("@/components/integrations/XeroTaxMappingEditor", () => ({
frontend/__tests__/app/settings/integrations.test.tsx:52:  XeroTaxMappingEditor: () => <div data-testid="xero-tax-mapping-editor" />,
frontend/__tests__/app/settings/integrations.test.tsx:54:vi.mock("@/components/integrations/XeroCustomerImport", () => ({
frontend/__tests__/app/settings/integrations.test.tsx:55:  XeroCustomerImport: () => <div data-testid="xero-customer-import" />,
frontend/__tests__/app/settings/integrations.test.tsx:57:vi.mock("@/components/integrations/XeroSyncSummary", () => ({
frontend/__tests__/app/settings/integrations.test.tsx:58:  XeroSyncSummary: () => <div data-testid="xero-sync-summary" />,
frontend/__tests__/app/settings/integrations.test.tsx:60:vi.mock("@/components/integrations/XeroSettingsForm", () => ({
frontend/__tests__/app/settings/integrations.test.tsx:61:  XeroSettingsForm: () => <div data-testid="xero-settings-form" />,
frontend/__tests__/app/settings/integrations.test.tsx:91:  render(await renderXeroTab({ slug: "acme", searchParams: {} }));
frontend/__tests__/app/settings/integrations.test.tsx:94:describe("Connections › Xero tab (renderXeroTab)", () => {
frontend/__tests__/app/settings/integrations.test.tsx:98:    mockGetXeroConnection.mockRejectedValue(new Error("404"));
frontend/__tests__/app/settings/integrations.test.tsx:99:    mockGetXeroTaxMappings.mockResolvedValue([]);
frontend/__tests__/app/settings/integrations.test.tsx:100:    mockGetXeroSettings.mockResolvedValue({ syncEnabled: true });
frontend/__tests__/app/settings/integrations.test.tsx:126:  it("points Configure Xero at the Xero section of this tab", async () => {
frontend/__tests__/app/settings/integrations.test.tsx:128:    expect(screen.getByRole("link", { name: /Configure Xero/ })).toHaveAttribute(
frontend/__tests__/app/settings/integrations.test.tsx:134:  it("shows an owner the Xero section with the connection card (not connected)", async () => {
frontend/__tests__/app/settings/integrations.test.tsx:136:    expect(screen.getByRole("heading", { level: 2, name: "Xero Integration" })).toBeInTheDocument();
frontend/__tests__/app/settings/integrations.test.tsx:139:    expect(mockGetXeroTaxMappings).not.toHaveBeenCalled();
frontend/__tests__/app/settings/integrations.test.tsx:142:  it("shows the full Xero management stack when connected, fetching the connection once", async () => {
frontend/__tests__/app/settings/integrations.test.tsx:143:    mockGetXeroConnection.mockResolvedValue(CONNECTED);
frontend/__tests__/app/settings/integrations.test.tsx:145:    expect(mockGetXeroConnection).toHaveBeenCalledTimes(1);
frontend/__tests__/app/settings/integrations.test.tsx:146:    expect(screen.getByText("Connected to Acme Ltd via Xero")).toBeInTheDocument();
frontend/__tests__/app/settings/integrations.test.tsx:158:  it("lets an INTEGRATION_MANAGE holder manage Xero", async () => {
frontend/__tests__/app/settings/integrations.test.tsx:164:  it("shows a member the hub and the former Xero denial instead of the management stack", async () => {
frontend/__tests__/app/settings/integrations.test.tsx:166:    mockGetXeroConnection.mockResolvedValue(CONNECTED);
frontend/__tests__/app/settings/integrations.test.tsx:169:    expect(screen.getByRole("heading", { level: 2, name: "Xero Integration" })).toBeInTheDocument();
frontend/__tests__/app/settings/integrations.test.tsx:177:    expect(mockGetXeroTaxMappings).not.toHaveBeenCalled();
frontend/__tests__/app/settings/integrations.test.tsx:178:    expect(mockGetXeroSettings).not.toHaveBeenCalled();
frontend/__tests__/app/settings/integrations/sync-log-back-link.test.tsx:5:// Phase 87 616D: the merged Connections › Xero tab opens on the integrations hub, so the sync-log
frontend/__tests__/app/settings/integrations/sync-log-back-link.test.tsx:6:// "Xero Settings" back link must anchor to the Xero section (#xero), as AccountingIntegrationCard does.
frontend/__tests__/app/settings/integrations/sync-log-back-link.test.tsx:32:describe("Xero sync log back link", () => {
frontend/__tests__/app/settings/integrations/sync-log-back-link.test.tsx:41:  it("anchors to the Xero section for a user who can view the sync log", async () => {
frontend/__tests__/app/settings/integrations/sync-log-back-link.test.tsx:44:    expect(screen.getByRole("link", { name: /Xero Settings/ })).toHaveAttribute(
frontend/__tests__/app/settings/integrations/sync-log-back-link.test.tsx:50:  it("anchors to the Xero section on the permission-denied view", async () => {
frontend/__tests__/app/settings/integrations/sync-log-back-link.test.tsx:56:    expect(screen.getByRole("link", { name: /Xero Settings/ })).toHaveAttribute(
frontend/__tests__/legal/tariff-schedule.test.tsx:223:    const tariffItem = feesZone?.items.find((i) => i.label === "Tariffs");
frontend/__tests__/lib/constants/matter-tab-groups.test.ts:57:        ["rates", "Tariffs"],
frontend/__tests__/legal/project-legal-tabs.test.tsx:367:    // Tariffs
frontend/__tests__/legal/project-legal-tabs.test.tsx:369:    const tariffItem = feesZone?.items.find((i) => i.label === "Tariffs");
frontend/__tests__/lib/constants/client-tab-groups.test.ts:53:        ["rates", "Tariffs"],
frontend/__tests__/team/member-role-management.test.tsx:225:      expect(screen.getByText("Project Management")).toBeInTheDocument();
frontend/__tests__/team/member-role-management.test.tsx:231:    expect(screen.getByText("Project Management")).toBeInTheDocument();
frontend/__tests__/team/member-role-management.test.tsx:233:    expect(screen.getByText("Customer Management")).toBeInTheDocument();
frontend/__tests__/team/member-role-management.test.tsx:272:      expect(screen.getByText("Project Management")).toBeInTheDocument();
frontend/__tests__/lib/types/integrations.test.ts:49:      providerName: "Xero",
frontend/__tests__/lib/types/integrations.test.ts:54:    expect(success.providerName).toBe("Xero");
frontend/__tests__/legal/legal-wire-bodies.test.ts:84:import { importXeroCustomers } from "@/lib/api/integrations";
frontend/__tests__/legal/legal-wire-bodies.test.ts:337:  it("importXeroCustomers POSTs /api/integrations/xero/import-clients", async () => {
frontend/__tests__/legal/legal-wire-bodies.test.ts:338:    await importXeroCustomers();
frontend/__tests__/automations/rule-list.test.tsx:119:    expect(screen.getByText("Invoice Status")).toBeInTheDocument();
frontend/__tests__/invite-role-selection.test.tsx:112:    expect(screen.queryByText("Project Management")).not.toBeInTheDocument();
frontend/__tests__/templates/TemplateList.test.tsx:43:  name: "From Project Template",
frontend/__tests__/templates/TemplateList.test.tsx:81:    expect(screen.getByText("Manual")).toBeInTheDocument();
frontend/__tests__/templates/TemplateList.test.tsx:86:    expect(screen.getByText("From Project")).toBeInTheDocument();
frontend/__tests__/customer-financials.test.tsx:87:    expect(screen.getByText("Per-Project Breakdown")).toBeInTheDocument();
frontend/components/__tests__/nav-zone.test.tsx:41:      label: "Dashboard",
frontend/components/__tests__/nav-zone.test.tsx:43:      icon: LayoutDashboard,
frontend/components/__tests__/nav-zone.test.tsx:132:    expect(screen.getByText("Dashboard")).toBeInTheDocument();
frontend/components/__tests__/nav-zone.test.tsx:143:    expect(screen.getByText("Dashboard")).toBeInTheDocument();
frontend/components/__tests__/nav-zone.test.tsx:150:    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
frontend/components/__tests__/nav-zone.test.tsx:174:    // Dashboard link should have active class
frontend/components/__tests__/nav-zone.test.tsx:237:      icon: LayoutDashboard,
frontend/components/__tests__/nav-zone.test.tsx:346:  it("highlights only Fee runs on a fee-run detail page", () => {
frontend/components/__tests__/nav-zone.test.tsx:348:    expect(activeLinks()).toEqual(["Fee runs"]);
frontend/components/__tests__/nav-zone.test.tsx:351:  it("highlights only Debtors on the collections page", () => {
frontend/components/__tests__/nav-zone.test.tsx:353:    expect(activeLinks()).toEqual(["Debtors"]);
frontend/components/__tests__/breadcrumbs.test.tsx:28:  it("labels /settings/connections/xero/sync-log as Connections > Xero > Sync log (616E)", () => {
frontend/components/__tests__/breadcrumbs.test.tsx:39:    const xero = screen.getByText("Xero");
frontend/components/__tests__/breadcrumbs.test.tsx:160:    it("renders Legal as plain text and Tariffs last on /legal/tariffs", () => {
frontend/components/__tests__/breadcrumbs.test.tsx:164:      const last = screen.getByText("Tariffs");
frontend/__tests__/field-definitions/FieldDefinitionDialog.test.tsx:104:    expect(screen.getByLabelText("Customer Activation")).toBeInTheDocument();
frontend/__tests__/field-definitions/FieldDefinitionDialog.test.tsx:105:    expect(screen.getByLabelText("Invoice Generation")).toBeInTheDocument();
frontend/__tests__/field-definitions/FieldDefinitionDialog.test.tsx:107:    expect(screen.getByLabelText("Project Creation")).toBeInTheDocument();
frontend/__tests__/field-definitions/FieldDefinitionDialog.test.tsx:125:    await user.click(screen.getByLabelText("Invoice Generation"));
frontend/__tests__/field-definitions/FieldDefinitionDialog.test.tsx:126:    await user.click(screen.getByLabelText("Customer Activation"));
frontend/components/__tests__/command-palette-dialog.test.tsx:179:      expect(entries).not.toContain("Settings › Fees & Trust › Fee runs");
frontend/components/__tests__/command-palette-dialog.test.tsx:192:      expect(entries).toContain("Settings › Fees & Trust › Fee runs");
frontend/components/__tests__/command-palette-dialog.test.tsx:211:        "Settings › Fees & Trust › Tariffs",
frontend/components/__tests__/command-palette-dialog.test.tsx:212:        "Settings › Connections › Xero",
frontend/components/__tests__/command-palette-dialog.test.tsx:217:    it("hides admin-only Fee runs from a member even with bulk_billing on (owner sees it)", () => {
frontend/components/__tests__/command-palette-dialog.test.tsx:225:      expect(screen.queryByText("Settings › Fees & Trust › Fee runs")).not.toBeInTheDocument();
frontend/components/__tests__/command-palette-dialog.test.tsx:228:      expect(screen.getByText("Settings › Fees & Trust › Fee runs")).toBeInTheDocument();
frontend/components/__tests__/command-palette-dialog.test.tsx:255:      await userEvent.setup().click(screen.getByText("Settings › Fees & Trust › Tariffs"));
frontend/components/__tests__/settings-sidebar.test.tsx:162:    expect(screen.queryByRole("link", { name: "Fee runs" })).not.toBeInTheDocument();
frontend/components/__tests__/settings-sidebar.test.tsx:177:  it("marks Connections › Xero on /settings/connections/xero/sync-log", () => {
frontend/components/__tests__/settings-sidebar.test.tsx:183:    expect(screen.getByRole("link", { name: "Xero" })).toHaveAttribute("aria-current", "true");
frontend/__tests__/components/documents/customer-documents-panel.test.tsx:102:    expect(screen.getByText("No customer documents yet")).toBeInTheDocument();
frontend/__tests__/components/prerequisite/prerequisite-modal.test.tsx:78:    expect(screen.getByText("Prerequisites: Invoice Generation")).toBeInTheDocument();
frontend/__tests__/components/customers/completeness-visibility.test.tsx:19:    { label: "Projects linked", complete: true, actionHref: "?tab=projects" },
frontend/__tests__/components/my-work/weekly-time-summary.test.tsx:72:    expect(screen.getByText("By Project")).toBeInTheDocument();
frontend/__tests__/components/my-work/weekly-time-summary.test.tsx:114:    expect(screen.queryByText("By Project")).not.toBeInTheDocument();
frontend/__tests__/components/customers/customer-contact-card.test.tsx:6:const baseCustomer: Client = {
frontend/__tests__/components/invoices/invoice-generation-prereq.test.tsx:69:      expect(screen.getByText("Prerequisites: Invoice Generation")).toBeInTheDocument();
frontend/__tests__/components/invoices/invoice-generation-prereq.test.tsx:97:    expect(screen.queryByText("Prerequisites: Invoice Generation")).not.toBeInTheDocument();
frontend/__tests__/components/projects/overview-tab-setup.test.tsx:12:      label: "Customer assigned",
frontend/__tests__/components/projects/overview-tab-setup.test.tsx:17:      label: "Rate card configured",
frontend/__tests__/components/projects/overview-tab-setup.test.tsx:23:      label: "Budget set",
frontend/__tests__/components/projects/overview-tab-setup.test.tsx:94:    expect(screen.getByText("Customer assigned")).toBeInTheDocument();
frontend/__tests__/components/projects/overview-tab-setup.test.tsx:124:      { label: "Customer assigned", complete: true, actionHref: "?tab=customers" },
frontend/__tests__/components/projects/overview-tab-setup.test.tsx:126:        label: "Rate card configured",
frontend/__tests__/components/projects/overview-tab-setup.test.tsx:131:      { label: "Budget set", complete: false, actionHref: "?tab=budget" },
frontend/__tests__/components/projects/overflow-actions-menu.test.tsx:112:  primaryCustomer: { id: "cust-1", name: "Acme Corp", email: "acme@test.com" },
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:10:  updateCustomer: (...args: unknown[]) => mockUpdateCustomer(...args),
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:13:const mockCustomer: Client = {
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:38:    render(<EditCustomerDialog customer={mockCustomer} slug="acme" triggerLabel="Edit Customer" />);
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:40:    await user.click(screen.getByRole("button", { name: "Edit Customer" }));
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:50:    render(<EditCustomerDialog customer={mockCustomer} slug="acme" triggerLabel="Edit Customer" />);
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:52:    await user.click(screen.getByRole("button", { name: "Edit Customer" }));
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:85:        triggerLabel="Edit Customer"
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:89:    await user.click(screen.getByRole("button", { name: "Edit Customer" }));
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:117:    render(<EditCustomerDialog customer={mockCustomer} slug="acme" triggerLabel="Edit Customer" />);
frontend/__tests__/components/customers/edit-customer-dialog.test.tsx:119:    await user.click(screen.getByRole("button", { name: "Edit Customer" }));
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:98:describe("KPIDashboard", () => {
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:109:    const jsx = await KPIDashboard(defaultProps);
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:119:    const jsx = await KPIDashboard(defaultProps);
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:128:    const jsx = await KPIDashboard(defaultProps);
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:136:    const jsx = await KPIDashboard(defaultProps);
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:144:    const jsx = await KPIDashboard({ ...defaultProps, disbursementsEnabled: false });
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:153:    const jsx = await KPIDashboard({ ...defaultProps, disbursementsEnabled: true });
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:163:    const jsx = await KPIDashboard(defaultProps);
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:171:    const jsx = await KPIDashboard({
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:181:        { label: "Customer assigned", complete: true, actionHref: "?tab=customers" },
frontend/__tests__/components/projects/kpi-dashboard.test.tsx:182:        { label: "Budget set", complete: true, actionHref: "?tab=budget" },
frontend/__tests__/components/dialog-family-ssr.snapshot.test.tsx:30:  updateCustomer: vi.fn(),
frontend/__tests__/components/customers/customer-detail-integration.test.tsx:45:vi.mock("@/components/customers/XeroContactBadge", () => ({
frontend/__tests__/components/customers/customer-detail-integration.test.tsx:46:  XeroContactBadge: () => <span data-testid="xero-badge">Xero</span>,
frontend/__tests__/components/customers/create-customer-dialog.test.tsx:16:  createCustomer: (...args: unknown[]) => mockCreateCustomer(...args),
frontend/__tests__/trust-dashboard.test.tsx:15:const mockFetchDashboardData = vi.fn();
frontend/__tests__/trust-dashboard.test.tsx:19:  fetchDashboardData: (...args: unknown[]) => mockFetchDashboardData(...args),
frontend/__tests__/trust-dashboard.test.tsx:43:function defaultDashboardData() {
frontend/__tests__/trust-dashboard.test.tsx:137:  mockFetchDashboardData.mockResolvedValue(defaultDashboardData());
frontend/__tests__/components/customers/client-overview-tab.test.tsx:53:          steps: [{ label: "Projects linked", complete: true }],
frontend/__tests__/components/customers/client-header-card.test.tsx:23:vi.mock("@/components/customers/XeroContactBadge", () => ({
frontend/__tests__/components/customers/client-header-card.test.tsx:24:  XeroContactBadge: () => <span data-testid="xero-badge">Xero</span>,
frontend/__tests__/components/customers/client-header-card.test.tsx:117:  it("renders KYC and Xero badges when present", () => {
frontend/__tests__/components/customers/customer-address-block.test.tsx:6:const baseCustomer: Client = {
frontend/__tests__/components/customers/archive-customer-dialog.test.tsx:10:  archiveCustomer: (...args: unknown[]) => mockArchiveCustomer(...args),
frontend/components/invoices/__tests__/unbilled-summary-expenses.test.tsx:96:    expect(screen.getByText("Expense Subtotal")).toBeInTheDocument();
frontend/components/invoices/__tests__/unbilled-summary-expenses.test.tsx:99:    const subtotalSection = screen.getByText("Expense Subtotal").closest("div");
frontend/__tests__/components/customers/customer-setup-guidance.test.tsx:13:      label: "Projects linked",
frontend/__tests__/components/customers/customer-setup-guidance.test.tsx:54:    expect(screen.getByText("Projects linked")).toBeInTheDocument();
```
