# Phase 48 -- QA Gap Closure (Automation Wiring, SA Invoice, Org Settings, Bulk UX)

Phase 47's QA cycle surfaced 31 gaps; 10 were fixed during the cycle (PRs #687-#695), 6 disproved, and 6 more P1 bugs were fixed on the current branch (PRs #696-#702). Phase 48 closes the remaining **11 gaps** that require new feature work: automation trigger wiring (GAP-001, GAP-002, GAP-003), SA vertical polish (GAP-005, GAP-008A, GAP-013, GAP-016), and bulk time entry UX (GAP-015).

**Architecture doc**: `architecture/phase48-qa-gap-closure.md`

**ADRs**:
- [ADR-185](adr/ADR-185-terminology-switching-approach.md) -- Terminology Switching Approach (static map in frontend bundle)
- [ADR-186](adr/ADR-186-date-field-scanner-isolation.md) -- Date-Field Scanner Isolation (sequential per-tenant scan)
- [ADR-187](adr/ADR-187-bulk-time-entry-ux-pattern.md) -- Bulk Time Entry UX Pattern (weekly grid, Harvest/Toggl style)
- [ADR-188](adr/ADR-188-customer-status-changed-event-conversion.md) -- CustomerStatusChangedEvent Conversion (convert to DomainEvent record)

**Dependencies on prior phases**:
- Phase 37 (Automations): `AutomationEventListener`, `TriggerType`, `TriggerTypeMapping`, `AutomationContext`, `AutomationRuleRepository`
- Phase 31 (Tiptap Templates): `TiptapRenderer`, `InvoiceContextBuilder`, `DocumentTemplatePackSeeder`, template pack JSON format
- Phase 15 (Compliance Lifecycle): `CustomerStatusChangedEvent`, `ChecklistInstanceService`, `CustomerLifecycleService`
- Phase 8 (Rate Cards): `RateSnapshotService`, `TimeEntryService`, `OrgSettings`
- Phase 5 (Time Tracking): `TimeEntry`, `TimeEntryController`, `TimeEntryRepository`
- Phase 47 (Vertical QA): `accounting-za.json` automation templates, `invoice-za.json` template, `OrgSettings.verticalProfile`

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 359 | Automation Wiring: PROPOSAL_SENT + CUSTOMER_STATUS_CHANGED (GAP-001, GAP-003) | Backend | -- | M | 359A | **Done** (PR #704) |
| 360 | Field Date Scanner + FIELD_DATE_APPROACHING Trigger (GAP-002) | Backend | 359 | M | 360A, 360B | |
| 361 | SA Invoice Template Wiring (GAP-016) | Backend | -- | S | 361A | |
| 362 | Proposal Summary Backend + Dashboard Frontend (GAP-013) | Both | -- | M | 362A, 362B | |
| 363 | Org Settings Hub Page (GAP-008A) | Frontend | -- | S | 363A | |
| 364 | Terminology Overrides (GAP-005) | Frontend | -- | M | 364A, 364B | |
| 365 | Bulk Time Entry Backend (GAP-015) | Backend | -- | M | 365A | |
| 366 | Bulk Time Entry Frontend -- Weekly Grid (GAP-015) | Frontend | 365 | L | 366A, 366B | |

---

## Dependency Graph

```
AUTOMATION WIRING (sequential)        SA VERTICAL POLISH (parallel)         BULK UX (sequential)
──────────────────────────────        ─────────────────────────────         ─────────────────────

[E359A Automation trigger              [E361A SA invoice template            [E365A Batch endpoint
 wiring: PROPOSAL_SENT                  context + pack wiring                 + service + tests]
 + CustomerStatusChanged                + render tests]                           |
 DomainEvent conversion                                                      [E366A Weekly grid
 + TriggerType mappings                [E362A Proposal summary                component + state]
 + AutomationContext stubs              backend endpoint + tests]                  |
 + template seeding                          |                               [E366B Copy previous
 + integration tests]                  [E362B Proposal dashboard              week + CSV import
       |                                frontend page + tests]                stretch + tests]
[E360A FieldDateNotificationLog
 entity + migration + event           [E363A Org settings hub page
 + TriggerType + mapping               + sidebar update + tests]
 + tests]
       |                              [E364A Terminology provider
[E360B FieldDateScannerJob              + map + context + tests]
 + tenant iteration + dedup                   |
 + template seeding + tests]          [E364B Apply t() to sidebar
                                       + headings + breadcrumbs
                                       + empty states + tests]
```

**Parallel opportunities**:
- E359A, E361A, E362A, E363A, E364A, and E365A can all start immediately (independent packages/domains).
- E360A depends on E359A (both modify `DomainEvent` permits clause and `TriggerType` enum -- merge conflict if parallel).
- E360B depends on E360A (scanner job needs entity + event + mapping from 360A).
- E362B depends on E362A (frontend needs summary API endpoint).
- E364B depends on E364A (needs `TerminologyProvider` and `t()` function).
- E366A depends on E365A (frontend grid needs batch endpoint).
- E366B depends on E366A (copy previous week extends the grid).

---

## Implementation Order

### Stage 0: Independent Backend Slices (5 parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a (parallel) | 359 | 359A | `PROPOSAL_SENT` + `CUSTOMER_STATUS_CHANGED` trigger wiring, `CustomerStatusChangedEvent` DomainEvent conversion, `AutomationContext` stubs, automation templates, integration tests (~6). Backend only. | **Done** (PR #704) |
| 0b (parallel) | 361 | 361A | `InvoiceContextBuilder` VAT extraction, `invoice-za` template pack wiring, vertical-to-template slug lookup, render integration test (~3). Backend only. | |
| 0c (parallel) | 362 | 362A | `ProposalService.getProposalSummary()`, `GET /api/proposals/summary` endpoint, DTO records, integration tests (~4). Backend only. | |
| 0d (parallel) | 365 | 365A | `TimeEntryBatchService`, `POST /api/time-entries/batch` endpoint, partial success logic, rate snapshots, integration tests (~5). Backend only. | |

### Stage 1: Dependent Backend Slices + Independent Frontend Slices (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 360 | 360A | V73 migration, `FieldDateNotificationLog` entity + repo, `FieldDateApproachingEvent` record, `DomainEvent` permits update, `TriggerType.FIELD_DATE_APPROACHING`, `TriggerTypeMapping` entry, `AutomationContext.buildFieldDateApproaching()`, integration tests (~4). Backend only. | |
| 1b (parallel) | 363 | 363A | `settings/general/page.tsx`, form fields, `ColorPicker` reuse, settings layout/sidebar update, redirect change, frontend tests (~4). Frontend only. | |
| 1c (parallel) | 364 | 364A | `lib/terminology.ts` map, `lib/terminology.tsx` provider + hook, wrap app layout, frontend tests (~3). Frontend only. | |

### Stage 2: Remaining Dependent Slices (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 360 | 360B | `FieldDateScannerJob` with `@Scheduled`, per-tenant iteration, dedup check, `sars-deadline-reminder` automation template, integration tests (~4). Backend only. | |
| 2b (parallel) | 362 | 362B | `proposals/page.tsx` dashboard, summary cards, needs-attention list, proposal table, sidebar nav update, frontend tests (~4). Frontend only. | |
| 2c (parallel) | 364 | 364B | Apply `t()` to ~30-40 locations: sidebar nav labels, page headings, breadcrumbs, empty states, button labels, frontend tests (~3). Frontend only. | |
| 2d (parallel) | 366 | 366A | `WeeklyTimeGrid` component, grid state management, editable cells, row/column/grand totals, week navigation, batch save integration, frontend tests (~4). Frontend only. | |

### Stage 3: Final Dependent Slices

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 366 | 366B | "Copy Previous Week" logic, CSV import (stretch), template download, preview table, frontend tests (~3). Frontend only. | |

---

## Epic 359: Automation Wiring -- PROPOSAL_SENT + CUSTOMER_STATUS_CHANGED (GAP-001, GAP-003)

**Goal**: Wire the existing `ProposalSentEvent` and `CustomerStatusChangedEvent` into the automation engine by adding `TriggerType` mappings, `AutomationContext` builders, and converting `CustomerStatusChangedEvent` from an `ApplicationEvent` class to a `DomainEvent` record per ADR-188. Seed automation templates for both triggers.

**References**: Architecture doc Sections 48.3.1, 48.3.2, 48.8.1. ADR-188.

**Dependencies**: None (first slice, touches `automation/`, `event/`, `compliance/` packages).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **359A** | 359.1--359.12 | Convert `CustomerStatusChangedEvent` to `DomainEvent` record, add `PROPOSAL_SENT` to `TriggerType`, add trigger mappings, fill in `AutomationContext` builders, update event publishers, seed automation templates in `accounting-za.json` and `common.json`, integration tests (~6). Backend only. | **Done** (PR #704) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 359.1 | Convert `CustomerStatusChangedEvent` to `DomainEvent` record | 359A | -- | Delete `compliance/CustomerStatusChangedEvent.java` (class extending `ApplicationEvent`). Create `event/CustomerStatusChangedEvent.java` as a record implementing `DomainEvent` with all 10 standard fields. `details` map carries `old_status`, `new_status`, `customer_name`. Pattern: `event/ProposalSentEvent.java`. |
| 359.2 | Add `CustomerStatusChangedEvent` to `DomainEvent` permits clause | 359A | 359.1 | Modify: `event/DomainEvent.java`. Add `CustomerStatusChangedEvent` to the `permits` list (grows from 35 to 36). |
| 359.3 | Update `ChecklistInstanceService` to publish new record constructor | 359A | 359.1 | Modify: `checklist/ChecklistInstanceService.java`. Find where `CustomerStatusChangedEvent` is constructed and published. Replace `new CustomerStatusChangedEvent(source, customerId, old, new)` with record constructor using all `DomainEvent` fields. Use `RequestScopes` to get `tenantId`, `orgId`, `actorMemberId`. |
| 359.4 | Update `CustomerLifecycleService` event publishing (if applicable) | 359A | 359.1 | Modify: `compliance/CustomerLifecycleService.java`. Search for other publishers of `CustomerStatusChangedEvent`. Update to new record constructor. If only `ChecklistInstanceService` publishes, mark this task N/A. |
| 359.5 | Update existing listeners for new event type | 359A | 359.1 | Modify: `CustomerLifecycleEventHandler` (if it exists as a separate listener) and `AuditEventListener` (search for `onCustomerStatusChanged`). Update `@EventListener` method signatures to accept the record type. Read `oldStatus`/`newStatus` from `event.details().get("old_status")` etc. |
| 359.6 | Add `PROPOSAL_SENT` to `TriggerType` enum | 359A | -- | Modify: `automation/TriggerType.java`. Add `PROPOSAL_SENT` value. Verify `CUSTOMER_STATUS_CHANGED` already exists (architecture doc says it was added in Phase 37). |
| 359.7 | Add trigger mappings to `TriggerTypeMapping` | 359A | 359.1, 359.6 | Modify: `automation/TriggerTypeMapping.java`. Add two entries to `MAPPINGS`: `ProposalSentEvent.class -> TriggerType.PROPOSAL_SENT` and `CustomerStatusChangedEvent.class -> TriggerType.CUSTOMER_STATUS_CHANGED`. Pattern: existing entries in the same map. |
| 359.8 | Fill in `AutomationContext.buildProposalSent()` | 359A | 359.6 | Modify: `automation/AutomationContext.java`. Add a `case PROPOSAL_SENT` branch (or switch on event class). Populate context with `proposal.id`, `proposal.sentAt`, `customer.id`, `customer.name`, `project.id`, `project.name` from event fields and `details()` map. |
| 359.9 | Fill in `AutomationContext.buildCustomerStatusChanged()` stub | 359A | 359.1 | Modify: `automation/AutomationContext.java`. The stub method exists with comment "No event is currently mapped." Fill in: `customer.id` from `entityId`, `customer.oldStatus` from `details.old_status`, `customer.newStatus` from `details.new_status`, `customer.name` from `details.customer_name`. |
| 359.10 | Seed automation templates for both triggers | 359A | 359.6 | Modify: `automation-templates/accounting-za.json` -- add `proposal-follow-up` rule (trigger: `PROPOSAL_SENT`, action: `SEND_NOTIFICATION` with 5-day delay). Verify existing `fica-reminder` rule references `CUSTOMER_STATUS_CHANGED` correctly. Also add generic `proposal-follow-up` to `automation-templates/common.json`. Pattern: existing rules in `accounting-za.json`. |
| 359.11 | Write integration test: PROPOSAL_SENT trigger wiring | 359A | 359.7, 359.8 | New file: `automation/ProposalSentTriggerWiringTest.java`. 3 tests: (1) publish `ProposalSentEvent`, verify `AutomationExecution` created with `PROPOSAL_SENT` trigger type. (2) Verify `AutomationContext` contains expected proposal/customer/project variables. (3) Verify `NotificationEventHandler` still receives the event (no regression). Pattern: existing automation integration tests. |
| 359.12 | Write integration test: CUSTOMER_STATUS_CHANGED trigger wiring | 359A | 359.3, 359.7, 359.9 | New file: `automation/CustomerStatusChangedTriggerWiringTest.java`. 3 tests: (1) Complete all checklist items, verify `CustomerStatusChangedEvent` (now a `DomainEvent`) reaches `AutomationEventListener`. (2) Verify context contains `oldStatus`, `newStatus`, `customerName`. (3) Verify existing `AuditEventListener` still logs the event. Pattern: existing automation integration tests. |

### Key Files

**Create:** `event/CustomerStatusChangedEvent.java` (record), `automation/ProposalSentTriggerWiringTest.java`, `automation/CustomerStatusChangedTriggerWiringTest.java`

**Delete:** `compliance/CustomerStatusChangedEvent.java` (old class)

**Modify:** `event/DomainEvent.java`, `automation/TriggerType.java`, `automation/TriggerTypeMapping.java`, `automation/AutomationContext.java`, `checklist/ChecklistInstanceService.java`, `compliance/CustomerLifecycleService.java` (if applicable), existing event listeners, `automation-templates/accounting-za.json`, `automation-templates/common.json`

### Architecture Decisions

- **Convert, don't bridge**: Per ADR-188, convert the existing `ApplicationEvent` to a `DomainEvent` record rather than creating a parallel event or adapter. Spring's `ApplicationEventPublisher` publishes any object, so existing `@EventListener` methods still receive it.
- **`PROPOSAL_SENT` already in DomainEvent permits**: Architecture doc notes that `ProposalSentEvent` is already in the permits clause. Only `CustomerStatusChangedEvent` needs to be added.
- **`CUSTOMER_STATUS_CHANGED` already in TriggerType**: Phase 37 added the enum value anticipating this wiring. Only the mapping entry is missing.

---

## Epic 360: Field Date Scanner + FIELD_DATE_APPROACHING Trigger (GAP-002)

**Goal**: Create a daily scheduled job that scans custom field date values across all tenants and fires `FieldDateApproachingEvent` when dates are within configurable thresholds. Includes the deduplication entity, new event type, trigger wiring, and SARS deadline reminder automation template.

**References**: Architecture doc Sections 48.2.1, 48.2.5, 48.3.3, 48.5.1, 48.6. ADR-186.

**Dependencies**: Epic 359 (both modify `DomainEvent` permits clause and `TriggerType` enum -- must merge sequentially to avoid conflicts).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **360A** | 360.1--360.8 | V73 migration, `FieldDateNotificationLog` entity + repo, `FieldDateApproachingEvent` record, `DomainEvent` permits update, `FIELD_DATE_APPROACHING` TriggerType + mapping, `AutomationContext.buildFieldDateApproaching()`, integration tests (~4). Backend only. | |
| **360B** | 360.9--360.14 | `FieldDateScannerJob` with `@Scheduled` annotation, per-tenant iteration via `ScopedValue`, dedup check logic, `sars-deadline-reminder` automation template seeding, integration tests (~4). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 360.1 | Create V73 tenant migration: `field_date_notification_log` | 360A | -- | New file: `db/migration/tenant/V73__create_field_date_notification_log.sql`. DDL per architecture doc Section 48.6: table with `id`, `entity_type`, `entity_id`, `field_name`, `days_until`, `fired_at`. Unique index on `(entity_type, entity_id, field_name, days_until)`. Lookup index on `(entity_type, entity_id)`. Pattern: `V60__create_automation_tables.sql`. |
| 360.2 | Create `FieldDateNotificationLog` entity | 360A | 360.1 | New file: `automation/FieldDateNotificationLog.java`. JPA entity with `@Entity`, `@Table(name = "field_date_notification_log")`. Fields: `id` (UUID, PK), `entityType` (String), `entityId` (UUID), `fieldName` (String), `daysUntil` (int), `firedAt` (Instant). No multitenancy boilerplate (schema boundary per ADR-064). Pattern: `automation/AutomationExecution.java`. |
| 360.3 | Create `FieldDateNotificationLogRepository` | 360A | 360.2 | New file: `automation/FieldDateNotificationLogRepository.java`. `JpaRepository<FieldDateNotificationLog, UUID>`. Method: `boolean existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(String, UUID, String, int)`. Pattern: `automation/AutomationExecutionRepository.java`. |
| 360.4 | Create `FieldDateApproachingEvent` record | 360A | -- | New file: `event/FieldDateApproachingEvent.java`. Record implementing `DomainEvent` with standard 10 fields. `details` map carries: `field_name`, `field_label`, `field_value` (ISO date), `days_until` (int), `entity_name`. `actorMemberId` is null; `actorName` is `"system"`. Pattern: `event/BudgetThresholdEvent.java`. |
| 360.5 | Add `FieldDateApproachingEvent` to `DomainEvent` permits clause | 360A | 360.4 | Modify: `event/DomainEvent.java`. Add to permits list (grows from 36 to 37 after Epic 359). |
| 360.6 | Add `FIELD_DATE_APPROACHING` to `TriggerType` enum + mapping | 360A | 360.4 | Modify: `automation/TriggerType.java` -- add `FIELD_DATE_APPROACHING`. Modify: `automation/TriggerTypeMapping.java` -- add `FieldDateApproachingEvent.class -> TriggerType.FIELD_DATE_APPROACHING`. |
| 360.7 | Add `AutomationContext.buildFieldDateApproaching()` | 360A | 360.4 | Modify: `automation/AutomationContext.java`. New switch branch for `FIELD_DATE_APPROACHING`. Populate: `entity.type`, `entity.id`, `entity.name` (from `details.entity_name`), `field.name`, `field.label`, `field.value`, `field.daysUntil`. |
| 360.8 | Write integration test: entity + event + mapping | 360A | 360.2, 360.6 | New file: `automation/FieldDateNotificationLogTest.java`. 4 tests: (1) Entity saves and loads correctly. (2) Unique constraint prevents duplicate `(entityType, entityId, fieldName, daysUntil)`. (3) `existsBy...` query returns true for existing record, false for new threshold. (4) `FieldDateApproachingEvent` is accepted by `TriggerTypeMapping`. |
| 360.9 | Create `FieldDateScannerJob` -- scheduled job skeleton | 360B | 360.2 | New file: `automation/FieldDateScannerJob.java`. Spring `@Component` with `@Scheduled(cron = "${app.automation.field-date-scan-cron:0 0 6 * * *}")`. Inject `OrgSchemaMappingRepository`, `FieldDefinitionRepository`, `CustomerRepository`, `ProjectRepository`, `FieldDateNotificationLogRepository`, `ApplicationEventPublisher`. Main method: `public void execute()`. |
| 360.10 | Implement per-tenant iteration in scanner | 360B | 360.9 | Modify: `automation/FieldDateScannerJob.java`. Iterate `OrgSchemaMappingRepository.findAll()`. For each tenant, `ScopedValue.where(RequestScopes.TENANT_ID, schema).run(() -> scanTenant(schema))`. Pattern: `automation/AutomationScheduler.java` (existing scheduled job). |
| 360.11 | Implement date field scanning + dedup logic | 360B | 360.10 | Modify: `automation/FieldDateScannerJob.java`. In `scanTenant()`: query `FieldDefinitionRepository` for DATE-type fields, query customers/projects with those field values from `custom_fields` JSONB, calculate `daysUntil`, check thresholds (14, 7, 1), check `FieldDateNotificationLogRepository.existsBy...()`, insert dedup record BEFORE publishing event. |
| 360.12 | Seed `sars-deadline-reminder` automation template | 360B | 360.6 | Modify: `automation-templates/accounting-za.json`. Add rule: trigger `FIELD_DATE_APPROACHING`, condition `fieldName == "sars_submission_deadline" AND daysUntil <= 14`, action `SEND_NOTIFICATION` to assigned member + owner. Pattern: existing rules in the file. |
| 360.13 | Write integration test: scanner fires events correctly | 360B | 360.11 | New file: `automation/FieldDateScannerJobTest.java`. 3 tests: (1) Create customer with date field 14 days out, run `execute()`, verify `FieldDateApproachingEvent` published and dedup log created. (2) Run again same day, verify no duplicate event. (3) Create customer with date field 30 days out (no matching threshold), verify no event published. |
| 360.14 | Write integration test: tenant isolation | 360B | 360.10 | Add to `FieldDateScannerJobTest.java`. 1 test: Create date fields in two different tenant schemas, run scanner, verify each tenant's events only reference that tenant's entities. |

### Key Files

**Create:** `V73__create_field_date_notification_log.sql`, `automation/FieldDateNotificationLog.java`, `automation/FieldDateNotificationLogRepository.java`, `event/FieldDateApproachingEvent.java`, `automation/FieldDateScannerJob.java`, `automation/FieldDateNotificationLogTest.java`, `automation/FieldDateScannerJobTest.java`

**Modify:** `event/DomainEvent.java`, `automation/TriggerType.java`, `automation/TriggerTypeMapping.java`, `automation/AutomationContext.java`, `automation-templates/accounting-za.json`

### Architecture Decisions

- **Sequential per-tenant scan**: Per ADR-186. One database connection at a time, follows `AutomationScheduler` pattern. Under 10 seconds for <100 tenants.
- **Dedup record before event**: Insert `FieldDateNotificationLog` within the per-tenant transaction before publishing the event. If process crashes after insert but before publish, worst case is a missed notification (not a duplicate).
- **Configurable cron**: `app.automation.field-date-scan-cron` property with default `0 0 6 * * *` (6 AM daily).
- **Thresholds hardcoded initially**: 14, 7, 1 days. Future enhancement could read thresholds from automation template conditions.

---

## Epic 361: SA Invoice Template Wiring (GAP-016)

**Goal**: Wire the existing `invoice-za` Tiptap JSON template into the invoice rendering pipeline by extracting customer VAT number into the render context, adding vertical-profile-based template slug lookup, and verifying the template renders correctly with SARS-compliant formatting.

**References**: Architecture doc Sections 48.2.6, 48.3.4, 48.8.1.

**Dependencies**: None (touches `template/` and `seeder/` packages only).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **361A** | 361.1--361.6 | `InvoiceContextBuilder` VAT extraction, per-line tax fields in context, vertical-to-template slug lookup in `DocumentGenerationService`, verify `invoice-za.json` content renders, integration tests (~3). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 361.1 | Extract `customerVatNumber` in `InvoiceContextBuilder` | 361A | -- | Modify: `template/InvoiceContextBuilder.java`. In `buildContext()`, extract `customer.customFields.get("vat_number")` and add as top-level context variable `customerVatNumber`. Also add `customer.address` if not already present. Pattern: existing variable extraction in the same builder. |
| 361.2 | Add per-line tax fields to context | 361A | 361.1 | Modify: `template/InvoiceContextBuilder.java`. Ensure each line item in the `lines` collection includes `taxAmount` (per-line VAT) and `lineTotal` (unit price * qty + tax). Also add invoice-level `subtotal` (sum excl. VAT), `taxAmount` (total VAT), `total` (sum incl. VAT). Check if these already exist; if so, mark N/A. |
| 361.3 | Wire vertical profile to default invoice template slug | 361A | -- | Modify: `template/GeneratedDocumentService.java` (or `DocumentGenerationService` -- whichever orchestrates invoice PDF generation). When resolving the template for an invoice, check `OrgSettings.verticalProfile`. If `"accounting-za"`, use slug `"invoice-za"`. Otherwise fall back to `"invoice"`. Pattern: `TemplatePackSeeder` vertical-profile-aware logic. |
| 361.4 | Verify `invoice-za.json` content in template pack | 361A | -- | Read: `template-packs/accounting-za/invoice-za.json`. Verify it contains the Tiptap JSON structure from architecture doc Section 48.2.6 (seller VAT, buyer VAT, loopTable with tax columns, subtotal/VAT/total, banking details). If content is a stub or incomplete, update it. |
| 361.5 | Write integration test: SA invoice template renders VAT fields | 361A | 361.1, 361.2 | New file: `template/InvoiceZaTemplateRenderTest.java`. 2 tests: (1) Render invoice with `invoice-za` template for a customer with `vat_number` custom field, verify rendered HTML contains seller VAT number (`org.taxRegistrationNumber`) and buyer VAT number (`customerVatNumber`). (2) Verify tax subtotals are separately stated (excl. VAT line, VAT amount line, incl. VAT total). |
| 361.6 | Write integration test: vertical profile template selection | 361A | 361.3 | Add to same test file or new `template/VerticalTemplateSelectionTest.java`. 1 test: Set org's `verticalProfile` to `"accounting-za"`, generate invoice PDF, verify `invoice-za` template is used (not generic `"invoice"`). |

### Key Files

**Create:** `template/InvoiceZaTemplateRenderTest.java` (or `template/VerticalTemplateSelectionTest.java`)

**Modify:** `template/InvoiceContextBuilder.java`, `template/GeneratedDocumentService.java` (or equivalent), possibly `template-packs/accounting-za/invoice-za.json` (if content needs updates)

### Architecture Decisions

- **Existing template pack**: `invoice-za.json` already exists in `template-packs/accounting-za/`. Phase 47 (Epic 355) created it. This epic wires the context builder and template selection, not the template content itself.
- **Tiptap rendering pipeline unchanged**: Uses existing `TiptapRenderer` -> `OpenHTMLToPDF` pipeline from Phase 31. No new Tiptap node types needed.
- **Vertical-to-slug mapping is simple**: Hardcoded `"accounting-za" -> "invoice-za"` in the generation service. If more verticals need custom invoice templates, the mapping can be externalized to a config file.

---

## Epic 362: Proposal Summary Backend + Dashboard Frontend (GAP-013)

**Goal**: Add a backend summary endpoint for proposal lifecycle metrics and build a frontend dashboard page showing status cards, conversion rate, overdue tracking, and a filterable proposal table.

**References**: Architecture doc Sections 48.4.1, 48.7.2, 48.8.1, 48.8.2.

**Dependencies**: None (backend touches `proposal/` package; frontend creates new `proposals/` route).

**Scope**: Both (backend slice first, then frontend slice)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **362A** | 362.1--362.5 | `ProposalService.getProposalSummary()` method, `ProposalSummaryDto` record, `ProposalController.getSummary()` endpoint, integration tests (~4). Backend only. | |
| **362B** | 362.6--362.11 | `proposals/page.tsx` dashboard, summary cards component, needs-attention list, proposal table with filters, sidebar nav update, frontend tests (~4). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 362.1 | Create `ProposalSummaryDto` record | 362A | -- | New file (or inner record in controller): `proposal/dto/ProposalSummaryDto.java`. Fields: `total` (int), `byStatus` (Map<ProposalStatus, Integer>), `avgDaysToAcceptance` (double), `conversionRate` (double), `pendingOverdue` (List<OverdueProposalDto>). `OverdueProposalDto`: `id`, `title`, `customerName`, `projectName`, `sentAt`, `daysSinceSent`. |
| 362.2 | Implement `ProposalService.getProposalSummary()` | 362A | 362.1 | Modify: `proposal/ProposalService.java`. Query all proposals for the tenant. Group by status for counts. Calculate `avgDaysToAcceptance` from `ACCEPTED` proposals with `sentAt` and `acceptedAt` timestamps. Calculate `conversionRate` as accepted / (accepted + declined + expired). Filter `SENT` proposals where `daysSinceSent > 5` for `pendingOverdue`, sorted by `daysSinceSent` desc. |
| 362.3 | Add `GET /api/proposals/summary` endpoint | 362A | 362.2 | Modify: `proposal/ProposalController.java`. Add `@GetMapping("/summary") @RequiresCapability(INVOICING)` method. One-liner delegation to `proposalService.getProposalSummary()`. Return `ResponseEntity.ok(result)`. Pattern: existing thin controller methods per backend CLAUDE.md. |
| 362.4 | Write integration test: summary endpoint with various proposal states | 362A | 362.3 | New file: `proposal/ProposalSummaryIntegrationTest.java`. 3 tests: (1) Create 5 proposals in different states, verify `byStatus` counts correct. (2) Create 2 accepted proposals with known `sentAt`/`acceptedAt`, verify `avgDaysToAcceptance` calculation. (3) Create 3 SENT proposals (2 overdue, 1 recent), verify `pendingOverdue` list has 2 entries sorted by `daysSinceSent` desc. |
| 362.5 | Write integration test: permission enforcement | 362A | 362.3 | Add to same test file. 1 test: Call `GET /api/proposals/summary` as member (no INVOICING capability), verify 403 response. |
| 362.6 | Create proposal dashboard page | 362B | 362A | New file: `frontend/app/(app)/org/[slug]/proposals/page.tsx`. Server component that fetches `GET /api/proposals/summary` and `GET /api/proposals` (existing list endpoint). Passes data to client components. Pattern: `invoices/page.tsx`. |
| 362.7 | Create summary cards component | 362B | 362.6 | New file: `frontend/components/proposals/proposal-summary-cards.tsx`. Client component. 4 cards: Total proposals, Pending (SENT count), Accepted count, Conversion rate (as percentage). Use existing `Card` Shadcn component. Pattern: `components/dashboard/` stat cards. |
| 362.8 | Create needs-attention list component | 362B | 362.6 | New file: `frontend/components/proposals/proposals-attention-list.tsx`. Client component. Shows overdue proposals (sent > 5 days, no response). Columns: customer, project, days overdue. Click navigates to proposal detail. Use existing `Table` Shadcn component. |
| 362.9 | Create proposal table component | 362B | 362.6 | New file: `frontend/components/proposals/proposal-table.tsx`. Client component with status filter dropdown and date range. Columns: title, customer, status (badge), sent date, days since sent. Row click navigates to proposal detail (existing route if available). Pattern: `components/invoices/` table. |
| 362.10 | Add "Proposals" to sidebar nav | 362B | -- | Modify: `frontend/lib/nav-items.ts`. Add `{ name: "Proposals", href: "/proposals", icon: FileText }` (or appropriate icon) in the work management section, after "Invoices". |
| 362.11 | Write frontend tests | 362B | 362.7, 362.8, 362.9 | New file: `frontend/__tests__/proposals-dashboard.test.tsx`. 3 tests: (1) Summary cards render correct counts from mocked summary data. (2) Needs-attention list renders overdue proposals sorted by days. (3) Proposal table renders all proposals with correct status badges. |

### Key Files

**Create:** `proposal/dto/ProposalSummaryDto.java`, `proposal/ProposalSummaryIntegrationTest.java`, `frontend/app/(app)/org/[slug]/proposals/page.tsx`, `frontend/components/proposals/proposal-summary-cards.tsx`, `frontend/components/proposals/proposals-attention-list.tsx`, `frontend/components/proposals/proposal-table.tsx`, `frontend/__tests__/proposals-dashboard.test.tsx`

**Modify:** `proposal/ProposalService.java`, `proposal/ProposalController.java`, `frontend/lib/nav-items.ts`

### Architecture Decisions

- **Permission model**: `INVOICING` capability (admin/owner only), consistent with existing proposal CRUD permissions.
- **Summary calculation in service**: All aggregation done in Java, not SQL. The proposal count per tenant is small enough (<100) that in-memory calculation is fine. A SQL-based aggregation can be added if performance becomes an issue.
- **`pendingOverdue` threshold**: 5 days hardcoded in the service. Could be made configurable via `application.yml` in a future phase.

---

## Epic 363: Org Settings Hub Page (GAP-008A)

**Goal**: Create the missing frontend settings hub page that exposes org name, default currency, tax registration, logo, branding, and document footer. Backend API already exists (`GET/PUT /api/settings`).

**References**: Architecture doc Sections 48.7.1, 48.8.2.

**Dependencies**: None (frontend-only, backend API exists).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **363A** | 363.1--363.7 | `settings/general/page.tsx` with form fields (org name, currency, tax reg, tax label, tax-inclusive toggle, logo upload, brand color, footer), settings layout/sidebar update, redirect change, frontend tests (~4). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 363.1 | Create general settings page | 363A | -- | New file: `frontend/app/(app)/org/[slug]/settings/general/page.tsx`. Server component that fetches `GET /api/settings` and passes data to client form component. Pattern: `settings/billing/page.tsx`. |
| 363.2 | Create general settings form component | 363A | 363.1 | New file: `frontend/components/settings/general-settings-form.tsx`. Client component (`"use client"`). Uses `react-hook-form` + Zod schema. Fields: org name (Input), default currency (Select: ZAR, USD, EUR, GBP), tax registration number (Input, label from `taxRegistrationLabel`), tax label (Input), tax-inclusive toggle (Switch), brand color (ColorPicker -- reuse from templates), document footer (Textarea). Save button calls `PUT /api/settings`. Pattern: existing settings form components. |
| 363.3 | Create Zod schema for general settings | 363A | -- | New file: `frontend/lib/schemas/general-settings.ts`. Schema: `orgName` (string, min 1, max 255), `defaultCurrency` (enum ZAR/USD/EUR/GBP), `taxRegistrationNumber` (string, optional), `taxLabel` (string, optional), `taxInclusive` (boolean), `brandColor` (string, optional), `documentFooterText` (string, optional). Pattern: `lib/schemas/customer.ts`. |
| 363.4 | Implement logo upload flow | 363A | 363.2 | Within `general-settings-form.tsx`. File input for logo. On file select, call existing S3 presigned URL upload flow (pattern from document upload). Store returned URL in form state. Display current logo as preview image. |
| 363.5 | Add server action for settings update | 363A | 363.2 | New file: `frontend/app/(app)/org/[slug]/settings/general/actions.ts`. Server action: `updateGeneralSettings(formData)` calls `PUT /api/settings` with the form payload. Pattern: `settings/billing/actions.ts`. |
| 363.6 | Update settings layout + redirect | 363A | -- | Modify: `frontend/app/(app)/org/[slug]/settings/layout.tsx` -- add "General" as the first item in the settings sidebar nav (before "Billing"). Modify: `frontend/app/(app)/org/[slug]/settings/page.tsx` -- change redirect from `/settings/billing` to `/settings/general`. |
| 363.7 | Write frontend tests | 363A | 363.2 | New file: `frontend/__tests__/general-settings.test.tsx`. 3 tests: (1) Form renders with existing org settings values pre-filled. (2) Form submission calls PUT endpoint with correct payload. (3) Currency select shows correct options. (4) Logo preview displays when URL is present. |

### Key Files

**Create:** `frontend/app/(app)/org/[slug]/settings/general/page.tsx`, `frontend/app/(app)/org/[slug]/settings/general/actions.ts`, `frontend/components/settings/general-settings-form.tsx`, `frontend/lib/schemas/general-settings.ts`, `frontend/__tests__/general-settings.test.tsx`

**Modify:** `frontend/app/(app)/org/[slug]/settings/layout.tsx`, `frontend/app/(app)/org/[slug]/settings/page.tsx`

### Architecture Decisions

- **Frontend only**: Backend `OrgSettingsController` already exposes `GET/PUT /api/settings` with all necessary fields. No backend changes needed.
- **ColorPicker reuse**: The existing `ColorPicker` component from the templates page is reused for brand color selection.
- **Logo upload**: Uses the existing S3 presigned URL flow (same pattern as document uploads). Logo URL is stored in `OrgSettings.logoUrl`.

---

## Epic 364: Terminology Overrides (GAP-005)

**Goal**: Introduce a lightweight terminology switching layer that maps ~15 platform terms to vertical-specific alternatives (e.g., "Projects" to "Engagements" for `accounting-za`). Implemented as a static map in the frontend bundle with a React context provider, applied to sidebar nav, page headings, breadcrumbs, and major button labels.

**References**: Architecture doc Sections 48.2.7, 48.3.5, 48.7.4. ADR-185.

**Dependencies**: None (frontend-only; `verticalProfile` already available in settings API response).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **364A** | 364.1--364.5 | `lib/terminology.ts` map, `lib/terminology.tsx` TerminologyProvider + useTerminology hook, wrap app layout with provider, unit tests (~3). Frontend only. | |
| **364B** | 364.6--364.10 | Apply `t()` to ~30-40 locations: sidebar nav labels, page headings, breadcrumbs, empty states, major button labels, integration tests (~3). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 364.1 | Create terminology map | 364A | -- | New file: `frontend/lib/terminology.ts`. Export `TERMINOLOGY: Record<string, Record<string, string>>` with `accounting-za` key mapping ~15 term pairs per architecture doc Section 48.2.7: Project/Engagement, Customer/Client, Proposal/Engagement Letter, Rate Card/Fee Schedule (both singular and plural, both cases). |
| 364.2 | Create TerminologyProvider + hook | 364A | 364.1 | New file: `frontend/lib/terminology.tsx`. Client component (`"use client"`). `TerminologyContext` with `{ t: (term: string) => string, verticalProfile: string | null }`. `TerminologyProvider` reads `verticalProfile` from props (passed from server component). `t(term)` does `TERMINOLOGY[verticalProfile]?.[term] ?? term`. Export `useTerminology()` hook. Pattern: `lib/auth/client/auth-provider.tsx`. |
| 364.3 | Wrap app layout with TerminologyProvider | 364A | 364.2 | Modify: `frontend/app/(app)/org/[slug]/layout.tsx`. Fetch `verticalProfile` from settings (may already be fetched). Wrap children with `<TerminologyProvider verticalProfile={settings.verticalProfile}>`. |
| 364.4 | Write unit tests for t() function | 364A | 364.1, 364.2 | New file: `frontend/__tests__/terminology.test.tsx`. 4 tests: (1) `t('Projects')` returns `'Engagements'` for `accounting-za`. (2) `t('Projects')` returns `'Projects'` for null profile. (3) `t('Unknown')` returns `'Unknown'` (passthrough for unmapped terms). (4) All case variants work: `t('project')` returns `'engagement'`, `t('Customers')` returns `'Clients'`. |
| 364.5 | Write integration test: provider in component tree | 364A | 364.2 | Add to test file. 1 test: Render a component tree with `TerminologyProvider` wrapping a child that calls `useTerminology().t('Project')`, verify it returns `'Engagement'`. |
| 364.6 | Apply `t()` to sidebar nav labels | 364B | 364A | Modify: `frontend/lib/nav-items.ts` and `frontend/components/desktop-sidebar.tsx` (and `mobile-sidebar.tsx` if it renders nav items separately). Nav items that need `t()`: Projects, Customers, Proposals, Rate Cards. The sidebar must use `useTerminology()` to resolve labels. If nav items are defined as static data in `nav-items.ts`, they may need to be converted to a function that accepts `t()` or the sidebar must wrap labels at render time. |
| 364.7 | Apply `t()` to page headings | 364B | 364A | Modify ~8-10 page files: `projects/page.tsx`, `customers/page.tsx`, `invoices/page.tsx`, `settings/rates/page.tsx`, etc. Replace hardcoded heading text with `t()` calls. Only the main `<h1>` or page title -- NOT all text on the page. If page is RSC, the `t()` call needs to happen in a client wrapper or the heading must be a client component. |
| 364.8 | Apply `t()` to breadcrumbs | 364B | 364A | Modify: `frontend/components/breadcrumbs.tsx`. Breadcrumb labels for route segments (`projects`, `customers`, etc.) should pass through `t()`. The breadcrumb component may need `"use client"` if it doesn't already have it. |
| 364.9 | Apply `t()` to empty states + button labels | 364B | 364A | Modify ~10-15 files: empty state messages ("No projects yet" -> "No engagements yet"), "New Project" buttons, etc. Only major visible surface -- NOT form labels, tooltips, or error messages per ADR-185 scope limitation. |
| 364.10 | Write integration tests for terminology in UI | 364B | 364.6, 364.7, 364.8 | New file: `frontend/__tests__/terminology-integration.test.tsx`. 3 tests: (1) Render sidebar with `accounting-za` profile, verify "Engagements" appears instead of "Projects". (2) Render breadcrumbs for `/projects`, verify "Engagements" label. (3) Render page heading for projects page, verify "Engagements" heading. |

### Key Files

**Create:** `frontend/lib/terminology.ts`, `frontend/lib/terminology.tsx`, `frontend/__tests__/terminology.test.tsx`, `frontend/__tests__/terminology-integration.test.tsx`

**Modify:** `frontend/app/(app)/org/[slug]/layout.tsx`, `frontend/lib/nav-items.ts`, `frontend/components/desktop-sidebar.tsx`, `frontend/components/mobile-sidebar.tsx`, `frontend/components/breadcrumbs.tsx`, ~15-20 page files for headings/empty states/buttons

### Architecture Decisions

- **Static map, not runtime API**: Per ADR-185. Zero latency, zero backend changes, `t()` signature compatible with future `next-intl` migration.
- **Scope limitation**: Only ~30-40 high-visibility locations. NOT form labels, tooltips, error messages, or deeply nested component text.
- **Slice split**: 364A creates the infrastructure (provider + map + tests). 364B applies it across the UI. Split because 364B touches many files but each change is trivial (wrapping a string with `t()`), and 364A establishes the contract that 364B depends on.

---

## Epic 365: Bulk Time Entry Backend (GAP-015)

**Goal**: Create the batch time entry creation endpoint (`POST /api/time-entries/batch`) with partial success semantics, rate snapshot application, budget threshold checks, and validation per entry.

**References**: Architecture doc Sections 48.3.6, 48.4.1, 48.5.2, 48.8.1. ADR-187.

**Dependencies**: None (touches `timeentry/` package only).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **365A** | 365.1--365.7 | `TimeEntryBatchService` with partial success, `BatchTimeEntryRequest`/`BatchTimeEntryResult` DTOs, `TimeEntryController.createBatch()` endpoint, rate snapshot per entry, budget threshold checks, integration tests (~5). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 365.1 | Create batch request/response DTOs | 365A | -- | New records (can be inner records in controller or in `timeentry/dto/`): `BatchTimeEntryRequest(List<BatchTimeEntryItem> entries)` where `BatchTimeEntryItem(UUID taskId, LocalDate date, int durationMinutes, String description, boolean billable)`. `BatchTimeEntryResult(List<CreatedEntry> created, List<EntryError> errors, int totalCreated, int totalErrors)`. `CreatedEntry(UUID id, UUID taskId, LocalDate date)`. `EntryError(int index, UUID taskId, String message)`. |
| 365.2 | Create `TimeEntryBatchService` | 365A | 365.1 | New file: `timeentry/TimeEntryBatchService.java`. Inject `TimeEntryService` (reuse single-entry creation logic), `TimeEntryValidationService`, `RateSnapshotService`, `TimeEntryRepository`, `ApplicationEventPublisher`. Method: `createBatch(BatchTimeEntryRequest request, UUID memberId)`. |
| 365.3 | Implement batch creation logic with partial success | 365A | 365.2 | In `TimeEntryBatchService.createBatch()`: Validate request size <= 50 entries. Loop entries: try validate (task exists, user has access, date valid, duration > 0), apply rate snapshot, save, check budget thresholds. Catch validation errors per entry, collect into `errors` list. Return `BatchTimeEntryResult`. Each valid entry processed independently -- failure of one does not roll back others. |
| 365.4 | Add `POST /api/time-entries/batch` endpoint | 365A | 365.2 | Modify: `timeentry/TimeEntryController.java`. Add `@PostMapping("/batch")` method. One-liner delegation to `timeEntryBatchService.createBatch(request, RequestScopes.requireMemberId())`. Same capability check as single-entry creation (TIME_TRACKING). |
| 365.5 | Write integration test: successful batch creation | 365A | 365.4 | New file: `timeentry/TimeEntryBatchIntegrationTest.java`. 3 tests: (1) Batch create 5 valid entries, verify all 5 created with correct rate snapshots. (2) Verify `totalCreated = 5`, `totalErrors = 0`. (3) Verify each entry has correct task, date, duration, billable flag. |
| 365.6 | Write integration test: partial success | 365A | 365.4 | Add to test file. 2 tests: (1) Batch with 3 valid + 2 invalid (nonexistent task, negative duration), verify 3 created + 2 errors with correct index and message. (2) Batch with all invalid entries, verify 0 created + all errors. |
| 365.7 | Write integration test: batch limit + budget threshold | 365A | 365.4 | Add to test file. 2 tests: (1) Send batch with 51 entries, verify 400 response (exceeds limit). (2) Create entries that push project over budget threshold, verify `BudgetThresholdEvent` published. |

### Key Files

**Create:** `timeentry/TimeEntryBatchService.java`, `timeentry/TimeEntryBatchIntegrationTest.java`, DTO records (in controller or `timeentry/dto/`)

**Modify:** `timeentry/TimeEntryController.java`

### Architecture Decisions

- **Partial success**: Valid entries are created even if some entries in the batch fail validation. This matches the architecture doc's specification and is the expected UX behavior for weekly grid saves.
- **Rate snapshot per entry**: Each entry gets its own rate snapshot at creation time, consistent with single-entry creation. Not batched -- rate could theoretically differ if rate card changes mid-batch (extremely unlikely in practice).
- **50-entry limit**: Prevents abuse and keeps request processing time bounded. A typical weekly grid submission is 7-35 entries.
- **Reuse existing validation**: Delegates to `TimeEntryValidationService` for per-entry validation rather than duplicating validation logic.

---

## Epic 366: Bulk Time Entry Frontend -- Weekly Grid (GAP-015)

**Goal**: Build the weekly timesheet grid UI with editable cells, batch save via `POST /api/time-entries/batch`, week navigation, "Copy Previous Week," and stretch-goal CSV import.

**References**: Architecture doc Sections 48.7.3, 48.8.2. ADR-187.

**Dependencies**: Epic 365 (backend batch endpoint must exist first).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **366A** | 366.1--366.8 | `WeeklyTimeGrid` component, grid state management, editable cells, row/column/grand totals, week navigation, batch save integration, add-task-row autocomplete, frontend tests (~4). Frontend only. | |
| **366B** | 366.9--366.14 | "Copy Previous Week" logic (fetch + date shift + pre-fill), CSV import stretch (file upload + parser + preview + validation), template download, frontend tests (~3). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 366.1 | Create weekly time grid page/tab | 366A | -- | New file: `frontend/app/(app)/org/[slug]/my-work/timesheet/page.tsx` (or add as a tab in existing my-work page). Server component that fetches user's recent tasks + current week time entries. Pattern: `my-work/page.tsx`. Also add "Timesheet" tab/link to my-work navigation if using tab approach. |
| 366.2 | Create WeeklyTimeGrid component | 366A | 366.1 | New file: `frontend/components/time-tracking/weekly-time-grid.tsx`. Client component (`"use client"`). Props: `tasks` (list of task rows), `existingEntries` (current week entries), `weekStart` (ISO date). State: `gridData: Map<taskId, Map<dayIndex, hours>>`, `dirty: boolean`. Renders table: header row (Mon-Sun + Total), task rows with editable cells, footer row (daily totals + grand total). |
| 366.3 | Create editable cell component | 366A | 366.2 | New file: `frontend/components/time-tracking/time-cell.tsx`. Client component. Renders hours input (number, step 0.25). On blur, updates parent grid state. Tab moves to next cell. Displays "3:00" or "3h" format. Empty cell = 0 hours. Visual indicator for cells with descriptions (small dot). Pattern: controlled input with `onChange` + `onBlur`. |
| 366.4 | Implement week navigation | 366A | 366.2 | Within `WeeklyTimeGrid` or parent page. Left/right arrows to move between weeks. "This Week" button to jump to current ISO week. On week change, re-fetch existing entries for the new week. Use `startOfWeek(date, { weekStartsOn: 1 })` for Monday start. |
| 366.5 | Create batch save server action | 366A | -- | New file: `frontend/app/(app)/org/[slug]/my-work/timesheet/actions.ts`. Server action: `saveWeeklyEntries(entries: BatchEntry[])` calls `POST /api/time-entries/batch`. Returns `{ created: [...], errors: [...] }`. Pattern: `my-work/actions.ts`. |
| 366.6 | Implement save flow with result display | 366A | 366.2, 366.5 | Within `WeeklyTimeGrid`. Save button enabled when `dirty`. On save: collect all new/changed cells, map to `BatchTimeEntryItem` format, call `saveWeeklyEntries()`. Display toast with success count. Display inline error indicators on cells that failed validation. Reset dirty state on success. |
| 366.7 | Implement add-task-row | 366A | 366.2 | Within `WeeklyTimeGrid`. "Add Task" row at bottom of grid with task autocomplete (search user's assigned tasks + recently used tasks). On select, adds new row to grid. Pattern: task selection in `LogTimeDialog`. |
| 366.8 | Write frontend tests for grid | 366A | 366.2, 366.3 | New file: `frontend/__tests__/weekly-time-grid.test.tsx`. 4 tests: (1) Grid renders correct day columns (Mon-Sun) and task rows. (2) Editing a cell updates row total and column total. (3) Save button sends correct batch payload. (4) Error display shows inline markers on failed cells. |
| 366.9 | Implement "Copy Previous Week" | 366B | 366A | New file: `frontend/components/time-tracking/copy-previous-week.tsx` (or logic within grid). Button component. On click: fetch time entries for `weekStart - 7 days` via existing `GET /api/tasks/{taskId}/time-entries?startDate=...&endDate=...`. Map entries to current week (shift dates by +7 days, keep task/hours/description). Pre-fill grid. Mark grid as dirty. User reviews before saving. |
| 366.10 | Create fetch action for previous week entries | 366B | -- | In `timesheet/actions.ts`. Server action: `fetchPreviousWeekEntries(weekStart: string)` calls existing time entry list endpoints for each visible task, date range = previous Monday to Sunday. Returns grouped entries by task + day. |
| 366.11 | Create CSV import component (stretch) | 366B | -- | New file: `frontend/components/time-tracking/csv-import-dialog.tsx`. Dialog with: file upload input (accept `.csv`), preview table showing parsed rows with validation status (green/red), "Import" button sends to batch endpoint. Template CSV download link. Lower priority than weekly grid -- defer if grid takes full budget. |
| 366.12 | Implement CSV parser | 366B | 366.11 | Within `csv-import-dialog.tsx` or separate utility. Parse CSV with columns: `date, task_name, project_name, hours, description, billable`. Validate: date format, hours > 0, required fields present. Match task_name + project_name to existing tasks (fuzzy match or exact). Display validation errors inline in preview table. |
| 366.13 | Write frontend tests for copy previous week | 366B | 366.9 | Add to test file or new `frontend/__tests__/copy-previous-week.test.tsx`. 3 tests: (1) Button triggers fetch of previous week entries. (2) Grid pre-fills with previous week's task/hours pattern shifted to current week dates. (3) Copied entries are marked as unsaved (dirty state). |
| 366.14 | Write frontend tests for CSV import (if implemented) | 366B | 366.11 | New file: `frontend/__tests__/csv-import.test.tsx`. 2 tests: (1) Valid CSV parsed correctly with preview table. (2) Invalid rows show error indicators. Skip if CSV is deferred as stretch goal. |

### Key Files

**Create:** `frontend/app/(app)/org/[slug]/my-work/timesheet/page.tsx`, `frontend/app/(app)/org/[slug]/my-work/timesheet/actions.ts`, `frontend/components/time-tracking/weekly-time-grid.tsx`, `frontend/components/time-tracking/time-cell.tsx`, `frontend/components/time-tracking/copy-previous-week.tsx`, `frontend/components/time-tracking/csv-import-dialog.tsx` (stretch), `frontend/__tests__/weekly-time-grid.test.tsx`, `frontend/__tests__/copy-previous-week.test.tsx`

**Modify:** `frontend/app/(app)/org/[slug]/my-work/page.tsx` (add timesheet tab/link), `frontend/lib/nav-items.ts` (if adding dedicated sidebar entry)

### Architecture Decisions

- **Weekly grid as primary**: Per ADR-187. Highest daily-use value. CSV import is stretch goal.
- **Monday start (ISO week)**: `weekStartsOn: 1` for consistency with international business week conventions.
- **Grid state in React**: `Map<taskId, Map<dayIndex, hours>>` for O(1) cell lookup. Dirty tracking per cell to minimize batch size on save.
- **Description via popover**: Each cell shows hours only. Click/hover reveals a popover with optional description field. Keeps grid compact.
- **Slice split rationale**: 366A is the core grid (most complex frontend component in this phase). 366B adds copy-forward and CSV import. Split because 366A is already at the upper bound of slice size (~8 files, complex state management), and CSV import can be deferred as stretch.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `CustomerStatusChangedEvent` conversion breaks existing listeners | Low | High | ADR-188 analysis shows only 2-3 listeners. Spring publishes any object via `ApplicationEventPublisher`, so `@EventListener` methods still receive the record. Integration tests verify all listeners. |
| `DomainEvent` permits clause merge conflict between E359A and E360A | Medium | Low | E360A depends on E359A -- sequential execution prevents conflict. If running on different branches, rebase before merge. |
| Weekly grid state management complexity exceeds slice budget | Medium | Medium | 366A focuses on grid + save. Copy-forward and CSV deferred to 366B. If 366A still too large, description popover can be deferred. |
| `FieldDateScannerJob` tenant iteration fails on specific tenant | Low | Medium | Scanner catches per-tenant exceptions and continues to next tenant. Failed tenant retried on next daily run. Log warning for manual investigation. |
| Terminology `t()` calls in RSC (Server Components) | Medium | Low | `t()` requires React context (client-only). Server Components that render headings must delegate to a client wrapper component or pass the heading as a prop to a client component. 364B handles this. |
| `invoice-za.json` template content incomplete or malformed | Low | Medium | Template was created in Phase 47 (Epic 355). Integration test in 361A verifies rendering produces expected HTML with VAT fields. |
| Proposal summary query performance on large datasets | Low | Low | Current proposal count per tenant is <100. In-memory aggregation is fine. Can add SQL-based aggregation if needed. |

---

## Test Summary

| Epic | Slice | New Tests | Coverage |
|------|-------|-----------|----------|
| 359 | 359A | ~6 | ProposalSent trigger wiring (3), CustomerStatusChanged trigger wiring (3) |
| 360 | 360A | ~4 | FieldDateNotificationLog entity (2), unique constraint (1), TriggerTypeMapping (1) |
| 360 | 360B | ~4 | Scanner fires event (1), dedup prevents duplicates (1), no-threshold no-event (1), tenant isolation (1) |
| 361 | 361A | ~3 | VAT fields in rendered HTML (1), tax subtotals separately stated (1), vertical template selection (1) |
| 362 | 362A | ~4 | Summary counts (1), avg days calculation (1), overdue list (1), permission enforcement (1) |
| 362 | 362B | ~4 | Summary cards render (1), attention list sorted (1), proposal table renders (1), additional UI test (1) |
| 363 | 363A | ~4 | Form pre-fills (1), submission payload (1), currency options (1), logo preview (1) |
| 364 | 364A | ~5 | t() returns override (1), t() passthrough (1), unknown term passthrough (1), case variants (1), provider integration (1) |
| 364 | 364B | ~3 | Sidebar labels (1), breadcrumbs (1), page heading (1) |
| 365 | 365A | ~7 | Successful batch (3), partial success (2), batch limit (1), budget threshold (1) |
| 366 | 366A | ~4 | Grid columns/rows (1), cell edit totals (1), save payload (1), error display (1) |
| 366 | 366B | ~5 | Copy fetch (1), grid pre-fill (1), dirty state (1), CSV parse (1), CSV errors (1) |
| **Total** | | **~53** | Full automation wiring + SA template + org settings + proposals + terminology + bulk time entry |

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java` - Core sealed interface that needs permits clause updates for 2 new event types
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationContext.java` - Needs 3 new context builder methods for PROPOSAL_SENT, CUSTOMER_STATUS_CHANGED, and FIELD_DATE_APPROACHING triggers
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerStatusChangedEvent.java` - Must be converted from ApplicationEvent class to DomainEvent record and moved to event/ package per ADR-188
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java` - Entry point for new batch endpoint; existing single-entry patterns to follow
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/lib/nav-items.ts` - Sidebar navigation definitions that multiple epics modify (proposals nav item, terminology overrides)