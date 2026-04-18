# Phase 67 — Legal Depth II: Daily Operational Loop (Disbursements, Matter Closure, Statement of Account, Conveyancing Pack)

## System Context

Kazi has three vertical profiles live; `legal-za` is the lighthouse and currently carries:

- **Trust accounting** (Phase 60) — `TrustAccount`, `TrustTransaction`, `ClientLedgerCard`, approval workflow, reconciliation, interest posting, Section 35 reports. Entities under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`.
- **Section 86 investment distinction + KYC** (Phase 61) — investment basis enum, KYC adapter + verification dialog.
- **Court calendar, conflict check, LSSA tariff, adverse parties** (Phase 55) — backend + frontend fully shipped. Pages under `frontend/app/(app)/org/[slug]/court-calendar/`, `conflict-check/`, `legal/tariffs/`, `legal/adverse-parties/`. Project tabs + dashboard widget live.
- **Legal terminology + 3 matter templates + 90-day QA script** (Phase 64).

What `legal-za` does **not** yet cover for the daily operational loop of a SA small-firm attorney:

1. **Disbursements** — sheriff fees, counsel/advocate fees, search fees, deeds-office fees. Currently only `Expense` exists (Phase 30, `backend/.../expense/`), which models standard project costs with markup, a single `ExpenseBillingStatus`, and no notion of trust-vs-office payment source, pass-through VAT, or pre-bill approval. Attorneys today would have to abuse the Expense entity for disbursements — wrong VAT treatment, wrong billing rules, no trust linkage.
2. **Matter closure workflow** — `Project` has `ACTIVE → COMPLETED → ARCHIVED` transitions (`Project.java`), but closure is purely state mutation. Legal closure under the Legal Practice Act + POPIA requires: trust balance = 0, all disbursements settled/billed, final bill issued, closure letter generated, retention clock started. None of these gates exist.
3. **Statement of Account** — SA legal billing artifact that combines fees + disbursements + trust activity + balance owing into one document. Distinct from an invoice (which is a demand for payment for a specific billing period). Common at milestone billing and at matter closure. No such document template exists.
4. **Conveyancing** — property-transfer engagements are a large segment of SA small-firm work (offer-to-purchase → draft deed → lodgement → registration → completion). Phase 64 shipped 3 matter templates but none cover conveyancing. No conveyancing custom fields, clauses, or document templates.

**Existing infrastructure this phase builds on:**

- **Expense entity** (`expense/Expense.java`, `ExpenseBillingStatus`): unbilled-summary + invoice-pipeline integration exists. Disbursements will be a sibling entity sharing a common unbilled-item contract rather than extending `Expense`, to avoid bloating the entity for non-legal tenants (see ADR).
- **Project lifecycle** (`project/Project.java`, `ProjectStatus`, `ProjectLifecycleGuard`): `complete()`, `archive()`, `reopen()` methods with `requireTransition()` guard. Closure workflow extends this with legal-specific pre-closure gates.
- **Trust accounting query surface** (`verticals/legal/trustaccounting/ledger/ClientLedgerService.java`): client ledger balance per matter is queryable today. Matter closure gate consumes this.
- **Invoice + InvoiceLine** (`invoice/`): `InvoiceLine` already has `TIME`, `EXPENSE`, `TARIFF` line sources (Phase 55 added `TARIFF`). `DISBURSEMENT` becomes a fourth source.
- **Tax engine** (`tax/`): `TaxRate` + `TaxApplication` handle VAT. Disbursements need a pass-through / zero-rated treatment override.
- **Document templates + clauses + acceptance** (Phases 12 / 27 / 28 / 31 / 42): Tiptap templates, clause library, send-for-acceptance + portal acceptance pages, DOCX pipeline. Statement of Account is a new template + data context; conveyancing pack is content only.
- **Vertical module registry** (`verticals/VerticalModuleRegistry.java`, `VerticalModuleGuard.java`): new modules (`disbursements`, `matter_closure`) register here; frontend surfaces wrap in `<ModuleGate>`.
- **Pack install pipeline** (Phase 65): conveyancing pack content (field pack, project template pack, clause pack, document template pack) ships through `PackInstaller` / `PackCatalogService` — no pipeline changes.
- **Capabilities / RBAC** (Phase 41 + 46): `@PreAuthorize` with capability strings (`disbursement.create`, `disbursement.approve`, `matter.close_override`) — new capabilities register in the capability catalogue.
- **OrgRole** (Phase 41): `owner` role is the natural holder of `matter.close_override`.
- **Retention + archival primitives** (Phase 50, `retention/`): `RetentionPolicy` + scheduled job exists. Matter closure triggers retention-clock start via this path.
- **Notifications + audit** (Phase 6 / 6.5): standard wiring; new domain events (`DISBURSEMENT_APPROVED`, `MATTER_CLOSED`, `STATEMENT_GENERATED`) ride existing infrastructure.

## Objective

Ship the daily operational loop that makes `legal-za` demo-ready and commercially plausible for a SA small law firm: real disbursements tracking with trust/office split and approval, a closure workflow that enforces compliance gates, a Statement of Account document, and a conveyancing matter-type pack. Close the phase with an updated 90-day legal QA lifecycle script run + gap report.

## Constraints & Assumptions

- **Disbursements are a sibling entity, not an Expense extension.** New entity `LegalDisbursement` under `verticals/legal/disbursement/`. Module-gated under a new `disbursements` vertical module. Non-legal tenants never see disbursement columns, endpoints, or UI.
- **Shared billing contract.** Introduce an `UnbilledBillableItem` interface (or equivalent abstraction) implemented by both `Expense` and `LegalDisbursement` so the invoice generation pipeline can treat them polymorphically without coupling. If a single interface proves invasive to retrofit, use a parallel service pattern (`DisbursementBillingService` that mirrors `ExpenseBillingService`) and unify at the invoice-generation orchestrator — architectural call deferred to builder, documented in an ADR.
- **Matter closure blocks on compliance gates; overrideable by `owner` only.** Any role with `@PreAuthorize("@capabilities.has('project.close')")` can close a matter *when all gates pass*. When one or more gates fail, only a caller with the `owner` org role (via new capability `matter.close_override`) can force closure, with a required justification string that lands in the audit log.
- **Statement of Account is a new `DocumentTemplate` + context builder, not a new entity.** Ride Phase 12/31 template infrastructure. Add a `StatementOfAccountContextBuilder` that assembles fees + disbursements + trust activity + balance owing into a variable bundle. Statement generation is a first-class action on a matter (not an invoice).
- **Conveyancing pack is pack-only (per ideation decision B).** No new backend entities, no new services specifically for conveyancing. Just JSON/Tiptap content that slots into existing field / project-template / clause / document-template pack loaders.
- **Conveyancing pre-wires e-signature acceptance.** Specific conveyancing document templates (offer to purchase, power of attorney) ship with acceptance enabled by default via Phase 28 `AcceptanceRequest`. Pack manifest metadata flags which templates are acceptance-eligible.
- **Module gating.**
  - `disbursements` is a new `VerticalModule`, auto-enabled under `legal-za` profile.
  - `matter_closure` workflow is module-gated under the same `disbursements` module OR under a sibling `matter_lifecycle` module — scope the split so the closure workflow is usable even if disbursements aren't enabled (some small criminal/litigation-only firms may not need disbursements but still need closure). **Recommended: two separate modules**, both auto-enabled under `legal-za`.
  - Statement of Account is a document-template feature, not a module. Template pack installs automatically under `legal-za`.
- **Retention clock uses existing `RetentionPolicy`.** On `MATTER_CLOSED` event, insert/advance a retention-policy row for the project with `retention_start = closure_date`. No new retention machinery.
- **All new content is en-ZA + SA regulatory context.** VAT 15%, ZAR currency, POPIA retention (typically 5 years min for legal matters), LSSA conventions for disbursements and statement formatting.
- **No changes to `legal-generic` or other vertical profiles.** This phase is strictly additive for `legal-za`.
- **Explicitly out of scope** (parked to later phases):
  - **Unified deadline calendar aggregating prescription + court + task + custom-date + auto-seeding derived deadlines from matter events.** Interesting but 15-slice phase on its own. Current separate pages (court-calendar + deadlines + My Work) stay independent.
  - **Advocate/counsel brief management** beyond disbursements (brief-to-counsel workflow, advocate selection UI, fee quotes).
  - **Fee notes / pro-forma bills** as a distinct entity from invoices.
  - **Deed-office integration** — API call to Lightstone / Deeds Office is future work.
  - **Bulk disbursement import** from bank CSVs — leverages Phase 60 bank-statement import in a later phase.
  - **Section 86(5) trust-interest distinction in statement of account** — covered by Phase 61 for trust reports; statement of account shows summarised balance only.

---

## Section 1 — Disbursements Module

### 1.1 Module Registration

Register `disbursements` in `VerticalModuleRegistry` with:
- Auto-enabled under `legal-za` profile.
- Capabilities: `disbursement.view`, `disbursement.create`, `disbursement.edit`, `disbursement.approve`, `disbursement.mark_billed`.
- Default role bindings: `owner` + `admin` get all; `member` gets `view` + `create` only.

### 1.2 Entity — `LegalDisbursement`

New package `verticals/legal/disbursement/`. Entity fields:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `projectId` | UUID (FK) | The matter the disbursement belongs to |
| `customerId` | UUID (FK) | Denormalized for query performance |
| `category` | enum `DisbursementCategory` | See 1.3 |
| `description` | TEXT | e.g. "Sheriff service fee — Edenvale" |
| `amount` | DECIMAL | ZAR, always positive |
| `vatTreatment` | enum `VatTreatment` | `STANDARD_15`, `ZERO_RATED_PASS_THROUGH`, `EXEMPT` — default per category |
| `vatAmount` | DECIMAL | Computed from amount + treatment |
| `paymentSource` | enum `PaymentSource` | `OFFICE_ACCOUNT`, `TRUST_ACCOUNT` |
| `trustTransactionId` | UUID nullable | Links to `TrustTransaction` when `paymentSource = TRUST_ACCOUNT` |
| `incurredDate` | DATE | When the disbursement was incurred |
| `supplierName` | STRING | e.g. "Sheriff Edenvale" or advocate name |
| `supplierReference` | STRING nullable | Invoice/receipt number from the supplier |
| `receiptDocumentId` | UUID nullable | Link to uploaded receipt (S3 via existing `document/`) |
| `approvalStatus` | enum `ApprovalStatus` | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED` |
| `approvedBy` | UUID nullable (FK Member) | |
| `approvedAt` | TIMESTAMP nullable | |
| `approvalNotes` | TEXT nullable | |
| `billingStatus` | enum `DisbursementBillingStatus` | `UNBILLED`, `BILLED`, `WRITTEN_OFF` |
| `invoiceLineId` | UUID nullable | Set when billed-through |
| `createdBy` / `createdAt` / `updatedAt` | standard audit columns | |

### 1.3 `DisbursementCategory` enum + default VAT treatment

| Category | Default VAT Treatment | Typical Payment Source |
|---|---|---|
| `SHERIFF_FEES` | `ZERO_RATED_PASS_THROUGH` | `OFFICE_ACCOUNT` |
| `COUNSEL_FEES` | `STANDARD_15` (if counsel is VAT-registered) | `OFFICE_ACCOUNT` or `TRUST_ACCOUNT` |
| `SEARCH_FEES` | `STANDARD_15` | `OFFICE_ACCOUNT` |
| `DEEDS_OFFICE_FEES` | `ZERO_RATED_PASS_THROUGH` | `TRUST_ACCOUNT` (common) |
| `COURT_FEES` | `ZERO_RATED_PASS_THROUGH` | `OFFICE_ACCOUNT` |
| `ADVOCATE_FEES` | `STANDARD_15` | `OFFICE_ACCOUNT` or `TRUST_ACCOUNT` |
| `EXPERT_WITNESS` | `STANDARD_15` | `OFFICE_ACCOUNT` |
| `TRAVEL` | `STANDARD_15` | `OFFICE_ACCOUNT` |
| `OTHER` | `STANDARD_15` | caller-specified |

User can override the default treatment per-disbursement.

### 1.4 Service — `DisbursementService`

Operations:
- `create(...)` — validates category, payment source, and that `trustTransactionId` is non-null iff `paymentSource = TRUST_ACCOUNT`. When payment source is `TRUST_ACCOUNT`, verifies linked `TrustTransaction` is of type `DISBURSEMENT_PAYMENT` (a new `TrustTransaction` subtype extending the enum in `verticals/legal/trustaccounting/transaction/`) and references the same matter.
- `submitForApproval(id)` — DRAFT → PENDING_APPROVAL.
- `approve(id, approverMemberId, notes)` — PENDING_APPROVAL → APPROVED. Checks `disbursement.approve` capability. Fires `DISBURSEMENT_APPROVED` event.
- `reject(id, approverMemberId, notes)` — PENDING_APPROVAL → REJECTED. Fires `DISBURSEMENT_REJECTED` event.
- `listUnbilledForProject(projectId)` — used by invoice generation + matter closure.
- `listForStatement(projectId, periodStart, periodEnd)` — used by Statement of Account context builder.
- `markBilled(id, invoiceLineId)` / `markWrittenOff(id, reason)`.

### 1.5 Shared Billing Contract

Introduce `invoice/UnbilledBillableItem` interface with:
```
UUID getId();
UUID getProjectId();
UUID getCustomerId();
BigDecimal getAmount();
String getDescription();
String getLineSourceType(); // "EXPENSE" or "DISBURSEMENT"
LocalDate getLineDate();
```

Implement on both `Expense` (adapter) and `LegalDisbursement`. Extend `InvoiceGenerationService` (or whatever the existing Phase 10/83 orchestrator is called) to accept `List<UnbilledBillableItem>` rather than `List<Expense>`, and source items from both `ExpenseRepository` and `DisbursementRepository` (legal only). If retrofit proves invasive, introduce `DisbursementBillingService` as a parallel path and unify at the generation orchestrator — document decision in an ADR.

### 1.6 InvoiceLine extension

Add new `InvoiceLineSource.DISBURSEMENT` enum value. New nullable FK `disbursementId` on `InvoiceLine` (mirroring `tariffItemId` / `expenseId`). Pass-through VAT treatment means `taxApplicationId` may be null or reference a ZERO_RATED tax rate — builder decides based on Phase 26 tax engine shape.

### 1.7 REST API

Under `/api/legal/disbursements` (module-gated):
- `POST /` — create
- `GET /?projectId=...&status=...&page=...` — list
- `GET /{id}` — detail
- `PATCH /{id}` — edit (only DRAFT / PENDING_APPROVAL; APPROVED is immutable except via `reject` or `markBilled`)
- `POST /{id}/submit` — submit for approval
- `POST /{id}/approve` — approve (requires `disbursement.approve`)
- `POST /{id}/reject` — reject
- `POST /{id}/write-off` — mark written off (reason required)
- `GET /unbilled?projectId=...` — unbilled summary by project
- `POST /{id}/receipt` — upload receipt document (multipart, routes through existing `DocumentService`)

### 1.8 Migration

`V100__create_legal_disbursements.sql` (or next available V-number) with table + indexes on `(projectId)`, `(customerId)`, `(billingStatus)`, `(approvalStatus)`. Foreign-key constraint from `disbursementId` on `invoice_line`.

### 1.9 Frontend

New pages under `frontend/app/(app)/org/[slug]/legal/disbursements/`:
- `page.tsx` — list view, filters (project, category, status, approval state, date range), "New Disbursement" button
- `[id]/page.tsx` — detail view with approval actions, receipt upload, linked invoice line

Project-detail page adds a module-gated `Disbursements` tab (same pattern as Phase 55 `Court Dates` tab), showing matter-scoped list + "Add Disbursement" dialog.

New components under `frontend/components/legal/`:
- `create-disbursement-dialog.tsx` — form with category picker, payment source toggle (trust vs office), VAT treatment override, receipt upload, supplier info
- `disbursement-approval-panel.tsx` — shown to `disbursement.approve` capability holders on PENDING_APPROVAL items
- `disbursement-list-view.tsx` — reusable list for standalone + project-tab pages
- `trust-transaction-link-dialog.tsx` — shown when `payment_source = TRUST_ACCOUNT`, picker that lists approved trust transactions for the matter

Invoice editor (Phase 10 / 26): add module-gated "Add Disbursements" button that opens a picker of approved-unbilled disbursements for the matter, converting selected items into `DISBURSEMENT` invoice lines.

Unbilled summary widget on matter detail: show breakdown `Unbilled Time / Unbilled Expenses / Unbilled Disbursements` with click-through to each.

Tests: ~8 backend integration tests (CRUD, approval workflow, trust-link validation, invoice-generation inclusion, module guard), ~6 frontend tests (dialog validation, approval button visibility by capability, trust-link dialog, list filters).

---

## Section 2 — Matter Closure Workflow

### 2.1 Module Registration

New module `matter_closure` in `VerticalModuleRegistry`. Auto-enabled under `legal-za`. Capabilities: `matter.close` (default: admin + owner), `matter.close_override` (default: owner only).

### 2.2 Service — `MatterClosureService`

New package `verticals/legal/closure/`.

```
MatterClosureReport evaluate(UUID projectId);       // runs all gates, returns pass/fail + reasons
void close(UUID projectId, ClosureRequest req);      // blocks if any gate fails unless req.override=true AND caller has matter.close_override
```

### 2.3 Closure Gates

A `ClosureGate` interface with `evaluate(Project) → GateResult {passed, code, message}`. Registered implementations:

| Gate code | Check | Failure message |
|---|---|---|
| `TRUST_BALANCE_ZERO` | `ClientLedgerService.getBalanceForMatter(projectId) == 0` | "Matter trust balance is R{balance}. Transfer to client or office before closure." |
| `ALL_DISBURSEMENTS_APPROVED` | No `LegalDisbursement` rows in `DRAFT` or `PENDING_APPROVAL` | "{count} disbursements are unapproved." |
| `ALL_DISBURSEMENTS_SETTLED` | No `LegalDisbursement` rows in `UNBILLED` (must be `BILLED` or `WRITTEN_OFF`) | "{count} disbursements are unbilled." |
| `FINAL_BILL_ISSUED` | At least one `Invoice` exists for this project with status `SENT` or `PAID`, and no unbilled time entries remain | "No final bill issued. {unbilled_hours}h of time + R{unbilled_amount} of unbilled items remain." |
| `NO_OPEN_COURT_DATES` | No `CourtDate` rows in `SCHEDULED` or `POSTPONED` for this matter | "{count} future court dates scheduled." |
| `NO_OPEN_PRESCRIPTIONS` | No `PrescriptionTracker` in `RUNNING` or `WARNED` status | "{count} prescriptions still running." |
| `ALL_TASKS_RESOLVED` | No `Task` in `IN_PROGRESS` or `BLOCKED` | "{count} tasks remain open." |
| `ALL_INFO_REQUESTS_CLOSED` | No `InformationRequest` in `PENDING` (Phase 34) | "{count} client information requests outstanding." |
| `ALL_ACCEPTANCE_REQUESTS_FINAL` | No `AcceptanceRequest` in `PENDING` (Phase 28) | "{count} document acceptances pending." |

The gates are legal-specific; accounting/consulting tenants never hit this path.

### 2.4 `ClosureRequest` DTO

```
{
  "closureReason": "Matter concluded successfully" | "Client terminated" | "Referred out" | "Other",
  "closureNotes": "...",
  "generateClosureLetter": true,
  "override": false,
  "overrideJustification": null
}
```

When `override=true`, `overrideJustification` is required and must be ≥ 20 characters; caller must have `matter.close_override` capability.

### 2.5 Closure Transaction

On successful `close(...)`:
1. Transition project to new `CLOSED` status (extend `ProjectStatus` enum — distinct from `COMPLETED` which is a horizontal concept; `CLOSED` is legal-specific post-completion state with retention tracking).
2. Insert `RetentionPolicy` row via Phase 50 `retention/` with `entityType = PROJECT`, `entityId = projectId`, `retention_start = now()`, `retention_years` configurable in `OrgSettings` (default 5 for legal).
3. If `generateClosureLetter = true`, auto-generate a closure-letter document using a new system-owned template (see Section 2.6) and attach to the matter's documents.
4. Emit `MATTER_CLOSED` domain event with reason, override-status, and gate-report payload.
5. Audit log entry with full gate evaluation + override justification (if any).
6. Notify owner + admin via Phase 6.5 notification channel.

After `CLOSED`, the existing `ARCHIVED` state remains reachable via `Project.archive()` (no change to horizontal archival semantics). `CLOSED` is the legal-specific terminal state that starts the retention clock.

### 2.6 Closure Letter Template

System-owned Tiptap template `matter-closure-letter.json` seeded with `legal-za` profile. Variables: `{{project.name}}`, `{{customer.name}}`, `{{closure.reason}}`, `{{closure.date}}`, `{{closure.notes}}`, `{{matter.total_fees_billed}}`, `{{matter.total_disbursements}}`, `{{matter.duration_months}}`, `{{org.name}}`, `{{org.principal_attorney}}`. Generated document stored via existing document pipeline.

### 2.7 REST API

- `GET /api/matters/{projectId}/closure/evaluate` — run all gates, return `MatterClosureReport`
- `POST /api/matters/{projectId}/closure/close` — attempt closure with `ClosureRequest`; 409 if gates fail and `override=false`; 403 if override=true but caller lacks capability
- `POST /api/matters/{projectId}/closure/reopen` — reopen a CLOSED matter (returns to ACTIVE, stops retention clock if not yet elapsed), owner only, notes required

### 2.8 Migration

`V101__project_closed_status_and_closure_audit.sql`:
- Extend `ProjectStatus` check constraint to include `CLOSED`.
- New `project_closure_log` table: `id`, `projectId`, `closedBy`, `closedAt`, `reason`, `notes`, `gateReport` (JSONB), `overrideUsed` (boolean), `overrideJustification`, `reopenedAt` nullable.

### 2.9 Frontend

New component `frontend/components/legal/matter-closure-dialog.tsx`:
- Step 1: Pre-closure gate report (runs `evaluate` endpoint). Shows each gate with pass (green check) / fail (red X + message). Clickable "fix this" links route to the relevant page (trust, disbursements, invoices, court dates, etc.).
- Step 2: Closure form — reason picker, notes, "generate closure letter" checkbox.
- Step 3: If any gate fails and caller has `matter.close_override`, show an "Override and close" toggle with justification textarea (≥ 20 chars). Otherwise show "Cannot close — fix gates first" state.

Matter (project) detail page: add "Close Matter" action in the action menu, visible only on `legal-za` profile + `ACTIVE`/`COMPLETED` status. Reopen action on `CLOSED` matters (owner only).

Matter list: new `CLOSED` filter chip; default view excludes closed matters.

Audit view of closure log accessible from matter detail.

Tests: ~10 backend integration tests (each gate in isolation + combined + override path + reopen), ~6 frontend tests (gate report rendering, override visibility by role, closure success flow, reopen flow).

---

## Section 3 — Statement of Account

### 3.1 Context Builder

New `StatementOfAccountContextBuilder` in `template/context/`. Assembles the following variables for a given `(projectId, periodStart, periodEnd)`:

```
statement.period_start
statement.period_end
statement.generation_date
statement.reference (auto: "SOA-{projectId-short}-{yyyymmdd}")

matter.name
matter.file_reference
matter.opened_date

customer.name
customer.address (formatted)

fees.entries[] — list of time-entry-derived line items grouped by date/member
fees.total_hours
fees.total_amount_excl_vat
fees.vat_amount
fees.total_amount_incl_vat

disbursements.entries[] — list grouped by category
disbursements.total

trust.opening_balance (period_start)
trust.deposits[] (transactions in period)
trust.payments[] (transactions in period)
trust.closing_balance (period_end)

summary.total_fees
summary.total_disbursements
summary.previous_balance_owing
summary.payments_received
summary.closing_balance_owing
summary.trust_balance_held
```

Reuses `ClientLedgerService` for trust activity, `TimeEntryService` for fees, `DisbursementService.listForStatement(...)` for disbursements.

### 3.2 Template

System-owned Tiptap template `statement-of-account.json` seeded with `legal-za` template pack. Standard SA legal format: firm header → client address → matter reference → fees section (grouped) → disbursements section → trust activity → summary → payment instructions footer.

### 3.3 Generation Endpoint + Action

- `POST /api/matters/{projectId}/statements` — body: `{ periodStart, periodEnd, templateId (optional, defaults to system SOA template) }`. Returns a `GeneratedDocument` (Phase 12 entity) with both HTML preview and PDF.
- Statement generation does NOT create an invoice; it is an informational document.
- Audit event `STATEMENT_GENERATED` with period + matter.

### 3.4 Frontend

Matter detail page action: "Generate Statement of Account" (module-gated). Dialog fields:
- Period start / period end (defaults: last statement date or matter opening; today)
- Template selector (defaults to system SOA)
- Preview button → HTML preview → "Generate PDF & Download" or "Save to Matter Documents"

List of previously generated statements on matter detail (sub-tab under Documents or its own mini-list).

Tests: ~4 backend tests (context assembly correctness, period filtering, trust activity inclusion, empty-period handling), ~3 frontend tests (dialog defaults, preview, save flow).

---

## Section 4 — Conveyancing Pack

Pack-only. All content drops into existing pack loaders; no backend code changes.

### 4.1 Field Pack — `field-packs/conveyancing-za-project.json`

Customer field pack NOT needed (reuse horizontal customer fields). Project field pack adds:

| slug | label | type | options / notes |
|---|---|---|---|
| `conveyancing_type` | Conveyancing Type | ENUM | `TRANSFER`, `BOND_REGISTRATION`, `BOND_CANCELLATION`, `SECTIONAL_TITLE`, `ESTATE_TRANSFER` |
| `property_address` | Property Address | TEXT | |
| `erf_number` | Erf Number | STRING | |
| `deeds_office` | Deeds Office | ENUM | All 10 SA deeds offices (Johannesburg, Pretoria, Cape Town, Bloemfontein, Pietermaritzburg, King William's Town, Kimberley, Mahikeng, Mthatha, Vryburg) |
| `lodgement_date` | Lodgement Date | DATE | |
| `registration_date` | Registration Date | DATE | Conditionally visible when `lodgement_date` is set |
| `deed_number` | Deed Number | STRING | Conditionally visible when `registration_date` is set |
| `purchase_price` | Purchase Price | CURRENCY | |
| `transfer_duty` | Transfer Duty | CURRENCY | |
| `bond_institution` | Bond Institution | ENUM | `ABSA`, `FNB`, `STANDARD_BANK`, `NEDBANK`, `INVESTEC`, `SA_HOME_LOANS`, `OTHER`, `NONE` |

Auto-apply this field group when `matter_type = CONVEYANCING` is set on a project (follow Phase 23 auto-apply pattern).

### 4.2 Project Template Pack — extend `project-template-packs/legal-za.json`

Add a single new matter template:

**Property Transfer (Conveyancing)**
- `matter_type`: `CONVEYANCING`
- `conveyancing_type`: `TRANSFER` (default, overridable)
- Suggested budget: 40 hours / fixed-fee option
- Tasks (stages):
  1. Receive instruction & conflict check (HIGH, Attorney)
  2. Draft offer to purchase / review OTP (HIGH, Attorney)
  3. Obtain FICA documentation from parties (HIGH, Paralegal)
  4. Obtain rates clearance figures (MEDIUM, Paralegal)
  5. Obtain transfer duty receipt (MEDIUM, Paralegal)
  6. Draft deed of transfer (HIGH, Conveyancer)
  7. Draft power of attorney to pass transfer (HIGH, Conveyancer)
  8. Lodge documents at Deeds Office (HIGH, Attorney) — set `lodgement_date` on completion
  9. Respond to Deeds Office notes (if raised) (HIGH, Attorney)
  10. Registration & collection of title deed (HIGH, Attorney) — set `registration_date` and `deed_number` on completion
  11. Finalise financial statement & disburse (HIGH, Attorney)
  12. Close matter (HIGH, Attorney)

### 4.3 Clause Pack — `clause-packs/conveyancing-za-clauses/`

10 standard SA conveyancing clauses, each a Tiptap JSON document:

| Clause slug | Purpose |
|---|---|
| `voetstoots.json` | Voetstoots ("as is") clause |
| `occupation-date.json` | Occupation rent + possession date |
| `suspensive-bond.json` | Suspensive condition — bond approval within N days |
| `transfer-duty-liability.json` | Who pays transfer duty |
| `fica-compliance.json` | FICA verification requirement |
| `sectional-title-levies.json` | Levy apportionment on sectional title |
| `body-corporate-clearance.json` | Clearance certificate requirement |
| `rates-clearance.json` | Municipal rates clearance |
| `cost-of-cancellation.json` | Breach + cancellation cost allocation |
| `jurisdiction-za.json` | SA law + courts jurisdiction |

### 4.4 Document Template Pack — extend `template-packs/legal-za/`

Add 4 new Tiptap templates (with manifest entries marking `acceptanceEligible: true` where applicable):

| Template | Acceptance-Eligible | Variables |
|---|---|---|
| `offer-to-purchase.json` | **Yes** (pre-wired for Phase 28 acceptance) | `{{customer.name}}` (buyer), `{{seller.name}}`, `{{property_address}}`, `{{erf_number}}`, `{{purchase_price}}`, `{{occupation_date}}`, inserted clauses |
| `deed-of-transfer.json` | No | `{{property_address}}`, `{{erf_number}}`, `{{deeds_office}}`, `{{purchase_price}}`, `{{transfer_duty}}`, party details |
| `power-of-attorney-transfer.json` | **Yes** (pre-wired) | `{{customer.name}}` (transferor), `{{attorney.name}}`, `{{property_address}}`, `{{deeds_office}}` |
| `bond-cancellation-instruction.json` | No | `{{bond_institution}}`, `{{customer.name}}`, `{{property_address}}`, `{{bond_number}}` |

Manifest `acceptanceEligible` flag: new optional field on template-pack manifest entries. Template service reads it and, when set, the Send for Acceptance UI (Phase 28) surfaces the template in the acceptance-request flow by default.

### 4.5 Request Pack — extend `request-packs/`

New request pack `conveyancing-intake-za.json` — questionnaire for conveyancing intake:
- Party details (ID numbers, contact info)
- Property address + erf + deeds office
- Purchase price + bond amount + bond institution
- Occupation date + possession date
- FICA supporting document upload (multiple files)
- Marital status + ANC/community-of-property (affects transfer)
- Rates + levy contact details

### 4.6 Profile Manifest Update — `vertical-profiles/legal-za.json`

Add to existing profile `packs`:
- `field`: append `conveyancing-za-project`
- `clause`: append `conveyancing-za-clauses`
- `request`: append `conveyancing-intake-za`

Template pack `legal-za` already referenced; extend its manifest entries in-place.

### 4.7 Terminology

No new terminology key. Existing `en-ZA-legal` terminology is sufficient.

---

## Section 5 — QA Lifecycle Script Retarget + Screenshot Baselines

### 5.1 Extend 90-Day Legal QA Script

Update `qa/testplan/demos/legal-small-firm-90day-keycloak.md` (or the file from Phase 64) with new checkpoints:

- **Day 5**: Disbursement workflow — create a sheriff fee (OFFICE source), create a deeds-office fee (TRUST source with trust transaction link), submit for approval, approve as admin.
- **Day 14**: Conveyancing matter — create a new matter using the Property Transfer template, verify conveyancing custom fields present, fill in property address + erf + deeds office, generate Offer to Purchase document, send for acceptance via Phase 28.
- **Day 30**: Generate first Statement of Account for the oldest active matter; verify fees + disbursements + trust activity + balance.
- **Day 45**: Write-off one disbursement; verify audit + billing exclusion.
- **Day 75**: Matter closure attempt on a matter with open gates → verify blocked + gate report shown. Resolve gates. Attempt closure as admin → succeeds. Verify closure letter generated, retention clock started, matter visible in CLOSED filter.
- **Day 85**: Matter closure override path — attempt closure on a matter with an intentionally unresolved gate, as owner, with override justification. Verify audit log captures override.

### 5.2 Screenshot Baselines

Follow Phase 64 pattern:
- `e2e/screenshots/legal-depth-ii/` for Playwright visual regression.
- `documentation/screenshots/legal-vertical/` curated shots: disbursement list, approval UI, trust-link dialog, matter closure dialog (pre-closure gates), closure letter preview, statement of account preview, conveyancing matter detail, offer to purchase document.

### 5.3 Gap Report

Deliverable: `tasks/phase67-gap-report.md` documenting any UX rough edges, missing variables, or adjustments needed after the live run — fuel for a Phase 68 polish slice (not this phase).

---

## Section 6 — Proposed Epic / Slice Breakdown

Rough shape for `/breakdown` — builder should sanity-check and resequence.

| Epic | Title | Scope | Slices |
|---|---|---|---|
| A | Disbursement entity + service + module registration | Backend | A1 (entity + migration + module), A2 (service CRUD + approval) |
| B | Disbursement invoicing integration + shared billing contract | Backend | B1 (`UnbilledBillableItem` + retrofit), B2 (invoice line + generation integration) |
| C | Disbursement frontend | Frontend | C1 (list + create dialog + project tab), C2 (approval panel + trust link + invoice editor) |
| D | Matter closure workflow | Backend | D1 (gates + service + closure log migration), D2 (closure letter template + retention wiring + REST) |
| E | Matter closure frontend | Frontend | E1 (closure dialog + gate report), E2 (reopen + matter list CLOSED filter) |
| F | Statement of Account | Both | F1 (context builder + template + endpoint), F2 (frontend generate dialog + document list) |
| G | Conveyancing pack | Backend | G1 (field pack + project template + clause pack), G2 (document template pack + request pack + profile manifest update) |
| H | QA lifecycle run + screenshots + gap report | Process | H1 (retarget script + run + artifacts) |

**~14 slices total.**

---

## Out of Scope

- **Unified deadline calendar aggregating prescription + court + task + custom-date + auto-seeding derived deadlines from matter events.** Future phase.
- **Advocate/counsel brief management** beyond the disbursement entry. No brief-to-counsel workflow, advocate selection UI, or fee quote lifecycle.
- **Fee notes / pro-forma bills** as a distinct entity — current invoice drafts cover this.
- **Deed-office API integration** (Lightstone / LexisNexis CourtOnline / e-Lodgement).
- **Bulk disbursement CSV import**.
- **Section 86(5) trust interest distinction in statement-of-account** — statement shows summarised trust balance; Phase 61 investment-register handles the Section 86 nuance separately.
- **Mobile-specific closure flow** — uses existing responsive breakpoints.
- **Disbursement markup modes** — SA legal practice forbids marking up disbursements; the entity intentionally has no markup field.
- **Office-account bank reconciliation for disbursements** — trust reconciliation is Phase 60; office-account reconciliation is a later integrations-phase topic.
- **Statement of Account scheduled auto-generation** — manual generation only this phase.
- **Conveyancing calculator** (transfer duty + bond cost calculators) — calculators are content/UI rather than infra; defer to a polish phase.
- **Multi-currency disbursements** — ZAR only.

---

## ADR Topics

- **ADR-??? — Sibling entity for legal disbursements vs Expense extension.** Decision: sibling entity (`LegalDisbursement`) with shared `UnbilledBillableItem` contract. Records the trade-off (clean separation vs. marginal duplication) and the rule: vertical-specific billable items get their own entity when their billing rules diverge materially from the horizontal `Expense` entity (markup, VAT, approval, payment source).
- **ADR-??? — Matter closure as a distinct terminal state with compliance gates.** Documents: why `CLOSED` is a legal-specific addition to `ProjectStatus` rather than overloading `COMPLETED`/`ARCHIVED`; why gates are enforced (not warnings); why `owner` is the only role with override capability; how the gate result flows into the audit log.
- **ADR-??? — Retention clock starts on matter closure, not on archival.** Horizontal `ARCHIVED` is user-visibility only; legal retention is a compliance timer. Codifies the wiring from `MATTER_CLOSED` event → `RetentionPolicy` row insert.
- **ADR-??? — Statement of Account as a template + context builder, not a new entity.** Rationale: it is an informational document, not a domain fact. Rides Phase 12/31 document-template primitives. Avoids introducing a `Statement` entity when `GeneratedDocument` already captures the artifact.
- **ADR-??? — Acceptance-eligible template manifest flag.** New `acceptanceEligible` metadata field on template pack manifest entries. Surfaces templates in the Phase 28 send-for-acceptance UI as first-class options. Generalises beyond conveyancing.

---

## Style & Boundaries

- Follow all conventions in `frontend/CLAUDE.md` and `backend/CLAUDE.md`.
- All new backend code lives under `verticals/legal/` except the `UnbilledBillableItem` interface (which belongs under `invoice/` as it is a shared contract).
- Module gating on every new frontend surface: `<ModuleGate module="disbursements">` or `<ModuleGate module="matter_closure">` as appropriate.
- Capabilities on every new controller endpoint: `@PreAuthorize("@capabilities.has('...')")`. No role-name checks.
- Migrations use the next available V-number sequence (check latest before coding; Phase 60/61/62/63/64/65/66 added many).
- Domain events (`DISBURSEMENT_APPROVED`, `MATTER_CLOSED`, `STATEMENT_GENERATED`) extend the existing event taxonomy under `event/` and trigger notifications + audit via existing handlers.
- All pack content (field, project template, clause, document template, request) validates against existing pack loaders; no new loader types.
- Tiptap documents follow Phase 31 conventions (no legacy HTML/Thymeleaf).
- Variable placeholders match Phase 31 resolver syntax (`{{ dot.notation }}`).
- No changes to `legal-generic`, `accounting-za`, `consulting-za`, or any other profile.
- Disbursement VAT treatments interoperate with Phase 26 tax engine — no new tax-calc code.
- Multi-vertical coexistence: new legal-only entities and endpoints must be invisible to `accounting-za` and `consulting-za` tenants (module guard + `enabledModules` check). Extend Phase 55 `MultiVerticalCoexistenceTest` with assertions for `disbursements`, `matter_closure`, and the conveyancing pack content.
