# Phase 48 — QA Gap Closure (Automation Wiring, SA Invoice, Org Settings, Bulk UX)

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. Phase 47 ran a 90-day accelerated QA cycle simulating a 3-person Johannesburg accounting firm ("Thornton & Associates") against the `accounting-za` vertical profile. The cycle found 31 gaps; 10 were fixed during the cycle (PRs #687–#695), 6 disproved (features already existed), and 4 remain open as P1 bugs being addressed separately.

**The remaining 11 gaps** cluster into three categories:
1. **Automation wiring** — domain events fire but aren't mapped into the automation engine. The infrastructure exists at 60-80%; only `TriggerTypeMapping` entries and event-type conversions are missing.
2. **SA vertical polish** — invoice PDF template, terminology overrides, and an org settings hub page. Backend APIs are largely ready; frontend pages or templates are missing.
3. **Bulk UX** — time entry creation is tedious (6-8 clicks per entry) with no batch operation or weekly copy.

**The fix**: A single phase that closes all 11 gaps — fast wiring fixes first, then frontend pages on existing backends, then the two genuine new features (date-field trigger and bulk time entry).

## Objective

1. **Wire existing domain events into the automation engine** — `PROPOSAL_SENT` and `CUSTOMER_STATUS_CHANGED` triggers already fire events but aren't mapped in `TriggerTypeMapping`. Add mappings, create automation templates, and convert `CustomerStatusChangedEvent` from `ApplicationEvent` to `DomainEvent`.
2. **Add the `FIELD_DATE_APPROACHING` scheduled trigger** — a new scheduled job that scans custom field date values and fires events when dates are within a configurable threshold. Powers SARS deadline reminders and financial year-end alerts.
3. **Create an SA-specific invoice PDF template** — `invoice-za` Tiptap template with SARS-compliant formatting (seller/buyer VAT numbers, tax amounts separately stated). Wire it into the invoice send/download flow.
4. **Build the org settings hub page** — central page for org name, default currency, logo, and branding. Backend `OrgSettingsController` already exposes the API; frontend page is missing.
5. **Build a proposal lifecycle dashboard** — the `Proposal` entity already tracks full lifecycle (DRAFT → SENT → ACCEPTED/DECLINED/EXPIRED) with timestamps. Add a frontend dashboard showing pending proposals, days since sent, and conversion metrics.
6. **Add terminology override support** — load vertical-specific terminology (e.g., "Projects" → "Engagements", "Customers" → "Clients") at runtime via the existing vertical profile system.
7. **Add bulk time entry creation** — "copy previous week" and CSV import for time entries to reduce the per-entry click cost.

## Constraints & Assumptions

- **No new backend entities** for the automation wiring (GAP-001, GAP-003) — these are mapping/conversion changes to existing infrastructure.
- **`FIELD_DATE_APPROACHING` (GAP-002)** requires a new scheduled job but reuses the existing `TriggerType` enum, `AutomationEventListener`, and `DomainEvent` infrastructure.
- **The SA invoice template (GAP-016)** uses the existing Tiptap JSON + `TiptapRenderer` + OpenHTMLToPDF rendering pipeline (Phase 31 replaced Thymeleaf). The `InvoiceContextBuilder` already includes `customer.customFields` and `orgSettings.taxRegistrationNumber` — the template just needs to render them via Tiptap `variable` and `loopTable` nodes.
- **Org settings hub (GAP-008A)** is frontend-only. The backend `OrgSettingsController` already exposes GET/PUT endpoints for org name, currency, logo URL, brand color, etc.
- **Proposal dashboard (GAP-013)** is frontend-only. The backend `ProposalController` already supports listing proposals with status filters and includes `sentAt`, `acceptedAt`, `declinedAt`, `expiresAt` timestamps.
- **Terminology overrides (GAP-005)** — the platform does NOT currently have `next-intl` or any i18n framework in the frontend. This section introduces lightweight terminology switching via the vertical profile, NOT full i18n. The approach is a React context that maps platform terms to vertical terms, driven by the org's `verticalProfile` field in `OrgSettings`.
- **Bulk time entry (GAP-015)** needs both backend (batch creation endpoint) and frontend (weekly grid UI + CSV import).
- **E2E stack** (`compose/docker-compose.e2e.yml`) is the test environment for verification.

---

## Section 1 — Automation Trigger Wiring (GAP-001, GAP-003)

### 1.1 PROPOSAL_SENT Trigger (GAP-001)

**Current state**: `ProposalSentEvent` is published by `ProposalService` and handled by `NotificationEventHandler.onProposalSent()` which creates notifications. However, `TriggerTypeMapping` does not map this event to a `TriggerType`, so the automation engine ignores it.

**Changes**:

1. Add `PROPOSAL_SENT` to the `TriggerType` enum.
2. Add mapping in `TriggerTypeMapping.MAPPINGS`: `ProposalSentEvent.class → TriggerType.PROPOSAL_SENT`.
3. Ensure `ProposalSentEvent` implements `DomainEvent` (verify — it may already).
4. Add `AutomationContext.buildProposalSent(ProposalSentEvent event)` to populate context variables (`proposalId`, `customerId`, `projectId`, `sentAt`).
5. Add an automation template to `accounting-za.json`:
   - Trigger: `PROPOSAL_SENT`
   - Condition: none (fires for all proposals)
   - Action: `SEND_NOTIFICATION` to owner after 5-day delay ("Engagement letter for {{customerName}} not yet accepted")
   - Also add to `common.json` pack (generic version without accounting terminology).

### 1.2 CHECKLIST_COMPLETED / CUSTOMER_STATUS_CHANGED Wiring (GAP-003)

**Current state**: `ChecklistInstanceService.checkInstanceCompletion()` detects when all items are complete and calls `checkLifecycleAdvance()`, which transitions the customer and publishes `CustomerStatusChangedEvent`. But this event is a Spring `ApplicationEvent`, not a `DomainEvent`, so `AutomationEventListener` (which listens to `DomainEvent`) never sees it.

**Changes**:

1. Convert `CustomerStatusChangedEvent` to implement `DomainEvent` interface (or create a new `CustomerStatusChangedDomainEvent` that wraps it — architect decides which is cleaner).
2. Add mapping in `TriggerTypeMapping.MAPPINGS`: the new domain event → `TriggerType.CUSTOMER_STATUS_CHANGED`.
3. Add `AutomationContext.buildCustomerStatusChanged(event)` — this method already exists as a stub with a comment saying "No event is currently mapped." Fill it in with `customerId`, `oldStatus`, `newStatus`.
4. Verify the existing automation template in `accounting-za.json` that references `CUSTOMER_STATUS_CHANGED` + `LifecycleStatus.PROSPECT` (the "fica-reminder" rule) now fires correctly.

### Testing

- Integration test: publish `ProposalSentEvent`, verify automation engine creates a delayed action execution.
- Integration test: complete all checklist items for a customer, verify `CustomerStatusChangedEvent` triggers automation rules.
- Verify existing `NotificationEventHandler` still fires (no regression — both notification and automation paths should work).

---

## Section 2 — FIELD_DATE_APPROACHING Trigger (GAP-002)

### Data Model

No new entities. Uses existing `TriggerType` enum + `AutomationTemplate` + `DomainEvent`.

Add to `TriggerType` enum: `FIELD_DATE_APPROACHING`.

### New Domain Event

```
FieldDateApproachingEvent implements DomainEvent
  - entityType: String ("customer" | "project")
  - entityId: UUID
  - fieldName: String (e.g., "sars_submission_deadline")
  - fieldValue: LocalDate
  - daysUntil: int
  - tenantSchema: String
```

### Scheduled Job

`FieldDateScannerJob` — a Spring `@Scheduled` job that runs daily (configurable via `app.automation.field-date-scan-cron`, default `0 0 6 * * *` — 6 AM daily):

1. Iterate all tenant schemas (existing `TenantSchemaService.listAll()`).
2. For each schema, query customers and projects that have date-type custom field values.
3. For each date field value, calculate `daysUntil = fieldDate - today`.
4. If `daysUntil` matches any configured threshold (from automation template conditions — e.g., 14 days, 7 days, 1 day), publish a `FieldDateApproachingEvent`.
5. The automation engine picks up the event via `TriggerTypeMapping` and executes configured actions.

**Deduplication**: Track `(entityId, fieldName, daysUntil)` tuples in a simple `field_date_notifications_sent` table (or reuse `AutomationExecutionLog` with a dedup key) to avoid re-firing the same alert daily.

### Automation Template

Add to `accounting-za.json`:
- Trigger: `FIELD_DATE_APPROACHING`
- Condition: `fieldName == "sars_submission_deadline" AND daysUntil <= 14`
- Action: `SEND_NOTIFICATION` to assigned member + owner ("SARS deadline for {{customerName}} is in {{daysUntil}} days")

### Context Builder

`AutomationContext.buildFieldDateApproaching(FieldDateApproachingEvent event)` populates: `entityType`, `entityId`, `entityName` (looked up), `fieldName`, `fieldLabel` (looked up from field pack), `fieldValue`, `daysUntil`.

### Testing

- Integration test: create a customer with a date field 14 days in the future, run the scanner job, verify event is published and automation fires.
- Integration test: verify deduplication — running the scanner twice on the same day doesn't fire duplicate notifications.
- Integration test: verify the scanner respects tenant isolation (only scans within the current tenant's schema).

---

## Section 3 — SA Invoice PDF Template (GAP-016)

### Current State

- `OrgSettings` has `taxRegistrationNumber`, `taxRegistrationLabel`, `taxLabel`, `taxInclusive`.
- `Customer.customFields` JSONB includes `vat_number` (from `accounting-za-customer.json` field pack).
- `InvoiceContextBuilder` assembles render context with invoice, customer, org settings, and line items.
- `TaxRate` entity exists with 15% standard VAT rate.
- Per-line tax fields: `tax_rate_percent`, `tax_amount`, `tax_exempt`.
- Generic invoice templates exist but don't render SA-specific fields.

### Changes

1. **Create Tiptap JSON template**: Seed an `invoice-za` document template with Tiptap JSON content (`content_json` column) containing:
   - SARS-compliant layout using Tiptap nodes (`variable`, `loopTable`, `conditionalBlock`):
     - Seller section: `{{org.name}}`, `{{org.address}}`, `{{org.taxRegistrationNumber}}` variables
     - Buyer section: `{{customer.name}}`, `{{customer.address}}`, `{{customer.customFields.vat_number}}` variables
     - Invoice details: `{{invoice.number}}`, `{{invoice.date}}`, `{{invoice.dueDate}}`, `{{invoice.paymentTerms}}`
     - Line items: `loopTable` node over `invoice.lines` with columns for description, quantity, unit price (excl. VAT), VAT amount, total (incl. VAT)
     - Subtotal (excl. VAT), total VAT, total (incl. VAT) — separately stated as required by SARS
     - Banking details: `{{org.bankName}}`, `{{org.bankAccountNumber}}`, `{{org.bankBranchCode}}`
     - Footer: `{{org.documentFooterText}}`
   - Rendered via existing `TiptapRenderer` → `OpenHTMLToPDF` pipeline.

2. **Extract customer VAT into context**: Update `InvoiceContextBuilder` to explicitly extract `vat_number` from `customer.customFields` and add it as a top-level context variable (`customerVatNumber`) for easy template access.

3. **Wire template to vertical profile**: The `accounting-za.json` vertical profile should reference `invoice-za` as the default invoice template slug. When generating/sending an invoice for an org with this profile, use the SA template.

4. **Seed the template**: Add `invoice-za` to the accounting template pack seeder (existing `DocumentTemplatePackSeeder`), or seed it via the `accounting-za-templates.json` pack.

### Testing

- Integration test: render an invoice PDF with the `invoice-za` template, verify VAT numbers appear for both seller and buyer.
- Integration test: verify tax subtotals are separately stated (excl. VAT, VAT amount, incl. VAT).
- Visual verification: generate a PDF and manually inspect layout.

---

## Section 4 — Org Settings Hub Page (GAP-008A)

### Current State

- Backend: `OrgSettingsController` exposes `GET/PUT /api/settings` with fields for org name, default currency, logo URL, brand color, document footer, tax registration, etc.
- Frontend: 20+ settings sub-pages exist (`/settings/billing`, `/settings/rates`, `/settings/templates`, etc.) but no central hub page. `/settings` redirects to `/settings/billing`.

### Changes

1. **Create settings hub page**: `frontend/src/app/(app)/org/[slug]/settings/general/page.tsx`
   - Form fields:
     - Org name (text input)
     - Default currency (select dropdown — ZAR, USD, EUR, GBP, etc.)
     - Tax registration number (text input, label from `taxRegistrationLabel`)
     - Tax label (text input, e.g., "VAT")
     - Tax-inclusive pricing toggle
     - Logo upload (image upload → S3 → URL stored in org settings)
     - Brand color (color picker — already exists on templates page, reuse component)
     - Document footer text (textarea)
   - Save button calls `PUT /api/settings`.

2. **Update settings redirect**: Change `/settings` default redirect from `/settings/billing` to `/settings/general`.

3. **Update sidebar**: Ensure "General" appears as the first item in the settings sidebar nav, above "Billing".

### Testing

- Frontend component test: verify form renders with existing org settings values.
- Frontend component test: verify form submission calls PUT endpoint with correct payload.
- E2E: navigate to settings, change org name and currency, verify persisted.

---

## Section 5 — Proposal Lifecycle Dashboard (GAP-013)

### Current State

- Backend: `Proposal` entity has `status` (DRAFT/SENT/ACCEPTED/DECLINED/EXPIRED), `sentAt`, `acceptedAt`, `declinedAt`, `expiresAt` timestamps.
- Backend: `ProposalController` supports listing proposals with status filter.
- Backend: `ProposalExpiryProcessor` handles auto-expiry.
- Frontend: Proposal CRUD components exist but no overview/dashboard page.

### Changes

1. **Backend — add summary endpoint**: `GET /api/proposals/summary` returns:
   ```json
   {
     "total": 12,
     "byStatus": { "DRAFT": 2, "SENT": 5, "ACCEPTED": 3, "DECLINED": 1, "EXPIRED": 1 },
     "avgDaysToAcceptance": 4.2,
     "conversionRate": 0.6,
     "pendingOverdue": [
       { "id": "...", "customerName": "...", "projectName": "...", "sentAt": "...", "daysSinceSent": 8 }
     ]
   }
   ```

2. **Frontend — dashboard page**: `frontend/src/app/(app)/org/[slug]/proposals/page.tsx` (or add a "Proposals" section to an existing page — architect decides placement)
   - Summary cards: Total proposals, Pending (sent), Accepted, Conversion rate
   - "Needs Attention" list: proposals sent > 5 days ago without response, sorted by days overdue
   - Status breakdown table: all proposals with columns for customer, project, status, sent date, days since sent / days to expiry
   - Filter by status, date range
   - Click-through to proposal detail

3. **Sidebar nav**: Add "Proposals" or "Engagement Letters" to the sidebar (likely under the Clients zone — architect decides).

### Testing

- Backend integration test: create proposals in various states, verify summary endpoint returns correct counts and metrics.
- Frontend component test: verify dashboard renders summary cards and proposal list.
- Frontend component test: verify "Needs Attention" list sorts correctly.

---

## Section 6 — Terminology Overrides (GAP-005)

### Current State

- `OrgSettings.verticalProfile` stores the vertical identifier (e.g., `"accounting-za"`).
- The frontend has NO i18n framework — all UI text is hardcoded in English.
- The backend vertical profile JSON (`accounting-za.json`) exists but only drives field packs, template packs, and automation templates — not terminology.

### Approach

Introduce a lightweight terminology layer — NOT full i18n. This replaces ~15 platform terms with vertical-specific alternatives at runtime.

### Changes

1. **Define terminology maps**: Create `frontend/src/lib/terminology.ts`:
   ```typescript
   const TERMINOLOGY: Record<string, Record<string, string>> = {
     'accounting-za': {
       'Project': 'Engagement',
       'Projects': 'Engagements',
       'project': 'engagement',
       'projects': 'engagements',
       'Customer': 'Client',
       'Customers': 'Clients',
       'customer': 'client',
       'customers': 'clients',
       'Proposal': 'Engagement Letter',
       'Proposals': 'Engagement Letters',
       'Rate Card': 'Fee Schedule',
       'Rate Cards': 'Fee Schedules',
     },
     // other verticals can be added here
   };
   ```

2. **React context**: `TerminologyProvider` wraps the app, reads the org's `verticalProfile` from the auth context / org settings API, and provides a `t(term)` function that looks up the override or returns the original term.

3. **Apply to navigation + headings**: Update sidebar nav items, page titles, breadcrumbs, and major headings to use `t()`. This is NOT a find-and-replace of every string — only the ~30-40 most visible surface-level terms (nav labels, page headings, empty states, button labels).

4. **Expose vertical profile to frontend**: If `verticalProfile` isn't already available in the frontend auth context, add it to the org settings response that the frontend fetches at app init.

### Scope Limitation

- Only sidebar nav, page titles/headings, breadcrumbs, major button labels, and empty state messages.
- NOT form labels, tooltips, error messages, or deeply nested component text. Those are follow-up work if/when full i18n is introduced.
- The terminology map is a static object in the frontend bundle — no runtime API call to fetch terms.

### Testing

- Frontend component test: verify `t('Projects')` returns `'Engagements'` when vertical profile is `'accounting-za'`.
- Frontend component test: verify `t('Projects')` returns `'Projects'` when no vertical profile is set.
- E2E: verify sidebar shows "Engagements" not "Projects" for an accounting-za org.

---

## Section 7 — Bulk Time Entry Creation (GAP-015)

### 7.1 Backend — Batch Creation Endpoint

Add `POST /api/time-entries/batch`:

```json
{
  "entries": [
    {
      "taskId": "...",
      "date": "2026-03-10",
      "durationMinutes": 180,
      "description": "Bank reconciliation",
      "billable": true
    },
    ...
  ]
}
```

Response: array of created time entry IDs + any validation errors per entry (partial success allowed — valid entries are created, invalid ones return errors).

**Validation**: Same rules as single-entry creation (task exists, user has access, date is valid, duration > 0). Rate snapshot applied per entry at creation time.

**Limit**: Max 50 entries per batch request.

### 7.2 Frontend — Weekly Time Grid

Add a "Week View" tab or toggle to the time tracking page (My Work or dedicated timesheet page):

- **Grid layout**: Rows = tasks the user has logged time against recently (or is assigned to). Columns = Mon–Sun for the selected week.
- **Cell input**: Click a cell to enter hours (e.g., "3" or "3:00"). Tab between cells.
- **Row totals**: Sum of hours per task for the week.
- **Column totals**: Sum of hours per day.
- **Week total**: Grand total in bottom-right corner.
- **Save**: Submits all new/changed entries via the batch endpoint.
- **Week navigation**: Forward/back arrows to move between weeks.

### 7.3 Frontend — Copy Previous Week

Button: "Copy Previous Week" on the weekly grid.

Behavior:
1. Fetch time entries for the previous week (Mon–Sun).
2. Pre-fill the current week's grid with the same task/hours/description pattern.
3. User reviews and adjusts before saving.
4. Only copies structure — not IDs or timestamps.

### 7.4 Frontend — CSV Import (Stretch)

A secondary import option — lower priority than the weekly grid:

- Upload CSV with columns: `date, task_name, project_name, hours, description, billable`
- Preview table showing parsed entries with validation status
- "Import" button sends batch to the backend endpoint
- Template CSV download link for the correct format

### Testing

- Backend integration test: batch create 10 time entries, verify all created with correct rate snapshots.
- Backend integration test: batch with 2 invalid + 3 valid entries, verify partial success.
- Frontend component test: weekly grid renders with correct day columns and task rows.
- Frontend component test: "Copy Previous Week" populates grid with previous week's pattern.

---

## Out of Scope

- **Full i18n / `next-intl` integration.** This phase adds lightweight terminology switching, not a full internationalization framework. Full i18n (date formats, number formats, pluralization, right-to-left) is a separate phase if needed.
- **Rate card auto-seeding from profile (GAP-006).** Manual setup works. Nice-to-have for onboarding friction.
- **SARS integration or eFiling export (GAP-021).** Future vertical-specific enhancement. Large effort.
- **Engagement letter auto-creation from template (GAP-022).** Rendering pipeline exists; auto-trigger on project creation is a follow-up.
- **Trust accounting.** Vertical-specific, not foundation material.
- **Recurring engagement improvements beyond what RecurringSchedule already provides (GAP-017).** Feature exists — may need UX surfacing but that's a polish task, not a phase.

## ADR Topics

1. **Terminology switching approach** — static map in frontend bundle vs. runtime API lookup vs. `next-intl` overlay. Recommend static map for v1 given the narrow scope (~15 terms). If full i18n is needed later, the `t()` function signature is compatible with `next-intl`'s `useTranslations()`.
2. **Date-field scanner isolation** — per-tenant sequential scan vs. parallel tenant scan vs. event-driven (trigger on field update). Recommend sequential scan with tenant batching for simplicity. The daily 6 AM run processes all tenants serially — at current scale (< 100 tenants), this completes in seconds.
3. **Bulk time entry UX pattern** — weekly grid (Harvest/Toggl style) vs. spreadsheet paste vs. CSV-only. Recommend weekly grid as primary (highest daily-use value), CSV as secondary (for bulk imports from other systems).
4. **CustomerStatusChangedEvent conversion** — convert existing `ApplicationEvent` to `DomainEvent` vs. create a parallel `DomainEvent` and keep both. Recommend converting the existing event to avoid two event types for the same concept. Audit any other `ApplicationEvent` listeners to ensure compatibility.

## Style & Boundaries

- Follow existing patterns: backend changes in the relevant domain packages (`automation/`, `invoice/`, `settings/`, `timeentry/`, `proposal/`), frontend in existing component directories.
- The SA invoice template follows existing Tiptap JSON template patterns — `content_json` JSONB, rendered by `TiptapRenderer` → OpenHTMLToPDF.
- The weekly time grid follows the existing time tracking UI patterns (LogTimeDialog, TimeEntryList).
- The org settings page follows the existing settings page patterns (form + save button, using the existing settings layout).
- The proposal dashboard follows existing list page patterns (summary cards + filterable table).
- All new automation templates are added to existing pack JSON files — no new seeder classes.
- Test coverage: backend integration tests for all wiring changes and new endpoints; frontend component tests for all new pages/components.
