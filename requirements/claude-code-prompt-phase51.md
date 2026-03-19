# Phase 51 — Accounting Practice Management Essentials

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 50 phases of functionality. The platform has a vertical architecture (Phase 49) with module guards, profile system, and tenant-gated modules. An `accounting-za` vertical profile already exists (Phase 47) with field packs (financial year-end, SARS references, CIPC number, engagement type), FICA compliance checklists, engagement letter templates, SA invoice templates, automation templates (SARS deadline approaching, proposal follow-up, FICA reminders), year-end info request templates, and terminology overrides ("Projects" → "Engagements", etc.).

**The existing infrastructure that this phase builds on**:
- **Vertical profile system** (Phase 49): `VerticalModuleGuard`, `OrgProfileProvider`, `ModuleGate`, profile registry, module registry. Tenant-gated module access.
- **Custom fields** (Phase 11, extended Phase 23): `FieldDefinition`, `FieldValue` entities. The `accounting-za-customer` field pack includes `financial_year_end` (DATE type), `sars_tax_reference`, `sars_efiling_profile`, `vat_number`, `cipc_registration_number`. The `accounting-za-project` field pack includes `engagement_type` (DROPDOWN), `tax_year`, `sars_submission_deadline` (DATE).
- **Automation engine** (Phase 37, extended Phase 48): `AutomationRule`, `TriggerType`, `ActionType`, `AutomationEventListener`. The `FIELD_DATE_APPROACHING` trigger with `FieldDateScannerJob` runs daily, scanning custom date fields for approaching deadlines. Accounting-specific automation templates already seeded.
- **Recurring schedules** (Phase 16): `RecurringSchedule`, `RecurringScheduleExecutor`, `ProjectTemplateService`. Executor creates projects from templates on schedule. Does NOT auto-generate documents — that's a gap this phase fills.
- **Calendar view** (Phase 30): `CalendarController` with month/list views showing tasks and time entries by date. Existing calendar infrastructure can be extended.
- **Document template engine** (Phase 12, redesigned Phase 31): Tiptap + Word templates, context builders, PDF generation. Template packs include `accounting-za` with engagement letters and SA invoice template.
- **Rate cards** (Phase 8): `BillingRate`, `CostRate` with 3-level hierarchy (org → project → customer). `OrgSettings` has default rates. No profile-based auto-seeding.
- **Notification system** (Phase 6.5): `Notification` entity with in-app and email channels.

**The problem**: The accounting profile has excellent seed data (field packs, templates, automations) but lacks three capabilities that turn configuration into workflow:

1. **No firm-wide deadline visibility.** The `FIELD_DATE_APPROACHING` trigger fires notifications for individual deadlines, but there's no calendar/timeline showing ALL regulatory deadlines across ALL clients. An accounting firm needs "February: 12 provisional tax payments, 3 annual returns" at a glance. They currently use spreadsheets alongside the platform.

2. **No post-schedule automation.** When a recurring schedule creates a project (e.g., annual tax return engagement), the firm manually generates the engagement letter and sends the year-end info request. These are predictable, template-driven actions that should fire automatically.

3. **No onboarding acceleration.** Setting up rate cards, seeding common project templates, and configuring recurring schedules is manual for every new accounting org. The vertical profile seeds field packs and compliance packs but not operational config.

**The fix**: Build three focused capabilities that close the gap between "configured for accounting" and "works like an accounting practice management tool."

## Objective

1. **Regulatory Deadline Calendar** — A dedicated view showing all regulatory filing deadlines across all clients, auto-calculated from each client's `financial_year_end` custom field. Firm-wide calendar with month/quarter views, filterable by deadline type (SARS provisional, SARS annual, VAT return, CIPC annual return). Status tracking (pending/filed/overdue). Links to the relevant project/task. This is the accounting firm's operational command center.

2. **Post-Schedule Automation (Engagement Kickoff)** — When `RecurringScheduleExecutor` creates a project, optionally trigger a chain of actions: (a) auto-generate the engagement letter document from the project's template, (b) auto-send a client information request via the portal. Configuration is per-schedule — not all schedules need auto-generation. This eliminates 3-5 manual steps per recurring engagement.

3. **Profile-Based Onboarding Seeding** — When a tenant selects the `accounting-za` vertical profile (or is provisioned with it), seed operational defaults: standard rate card tiers (partner/manager/clerk), common project templates with linked document templates, and recommended recurring schedule configurations. This is additive seeding — same pattern as field packs and compliance packs but for operational config.

## Constraints & Assumptions

- **Accounting-specific but extensible.** The deadline calendar is implemented as a "regulatory deadline" module that any vertical can use. The data model uses generic terms (`RegulatoryDeadline`, not `SarsDeadline`). Accounting-specific deadline types (provisional tax, VAT, CIPC) are seed data, not hardcoded. A legal firm could later use the same module for court filing deadlines.
- **Custom fields are the source of truth for dates.** The `financial_year_end` custom field on customers drives all deadline calculations. No new entity for financial year-end — the custom field system already handles this. The deadline calculation engine reads custom field values, not a separate date column.
- **Deadlines are calculated, not stored.** Regulatory deadlines are computed on-the-fly from the client's financial year-end + deadline type rules. No `RegulatoryDeadline` entity stored per client per year — that creates a synchronization problem. Instead, a service calculates what's due for a given date range. Filing status (filed/not-filed) IS stored because it's user-entered state.
- **Post-schedule actions are opt-in per schedule.** Adding `postCreateActions` configuration to `RecurringSchedule` (JSONB or related entity). Not all schedules want auto-generation. The default is no post-create actions.
- **Profile seeding is additive and idempotent.** Running the seeder twice doesn't duplicate data. Uses the existing pack seeder infrastructure. Rate cards, project templates, and schedule configs are suggestions — the tenant can modify or delete them.
- **No SARS integration.** No eFiling API calls, no automated submission. Filing status is manually tracked by staff. SARS integration is a separate, much larger effort.
- **Module-gated.** The regulatory deadline calendar is gated behind a `regulatory_deadlines` module. Enabled by default for `accounting-za` profile. Other profiles can opt in.

---

## Section 1 — Regulatory Deadline Calendar

### 1.1 Deadline Type Registry

A static registry (same pattern as `JurisdictionDefaults` from Phase 50) defining regulatory deadline types per jurisdiction:

```
DeadlineType:
  slug: String                    — e.g., "sars_provisional_tax_1", "sars_annual_return"
  name: String                    — "SARS Provisional Tax (1st Payment)"
  jurisdiction: String            — "ZA"
  category: String                — "tax", "corporate", "vat"
  calculationRule: Function       — how to compute the due date from financial year-end
  applicabilityRule: Function     — which clients this applies to (e.g., VAT only if VAT-registered)
```

**SA Accounting deadline types (seed data):**

| Slug | Name | Category | Calculation from FYE | Applicability |
|------|------|----------|---------------------|---------------|
| `sars_provisional_1` | Provisional Tax — 1st Payment | tax | FYE + 6 months (end of first half) | All companies |
| `sars_provisional_2` | Provisional Tax — 2nd Payment | tax | FYE month (end of year) | All companies |
| `sars_provisional_3` | Provisional Tax — Top-Up (Voluntary) | tax | FYE + 7 months | Companies with underpayment |
| `sars_annual_return` | Income Tax Return (ITR14/IT12) | tax | FYE + 12 months (varies by filing season) | All entities |
| `sars_vat_return` | VAT Return (bi-monthly) | vat | Every 2 months from VAT registration | Only if `vat_number` custom field is populated |
| `cipc_annual_return` | CIPC Annual Return | corporate | Anniversary of registration date | Only Pty Ltd / CC entities (`entity_type` custom field) |
| `sars_paye_monthly` | PAYE/UIF/SDL Monthly Return | payroll | 7th of following month | Only if firm has employees (always applicable for the firm itself) |
| `afs_submission` | Annual Financial Statements | corporate | FYE + 6 months (Companies Act) | All companies |

### 1.2 Deadline Calculation Service

```
DeadlineCalculationService
  + calculateDeadlines(dateRange: DateRange, filters: DeadlineFilters) → List<CalculatedDeadline>
  + calculateDeadlinesForCustomer(customerId: UUID, dateRange: DateRange) → List<CalculatedDeadline>
```

**`CalculatedDeadline`** (computed, not persisted):
```
  customerId: UUID
  customerName: String
  deadlineTypeSlug: String
  deadlineTypeName: String
  category: String
  dueDate: LocalDate
  status: "pending" | "filed" | "overdue" | "not_applicable"
  linkedProjectId: UUID (nullable)    — if a project exists for this period
  linkedTaskId: UUID (nullable)       — if a task tracks this filing
```

**Behavior:**
- For each client with a `financial_year_end` custom field value, apply each applicable deadline type's calculation rule to produce due dates within the requested range.
- Cross-reference with filing status records to determine status.
- Cross-reference with existing projects (by customer + engagement type + tax year) to find linked projects.
- Return sorted by due date ascending.

### 1.3 Filing Status Tracking

A lightweight entity tracking whether a specific filing has been completed:

```
FilingStatus (tenant-scoped)
  id: UUID
  customer_id: UUID FK
  deadline_type_slug: VARCHAR(50)      — references the deadline type registry
  period_key: VARCHAR(20)              — "2026", "2026-01", "2026-Q1" (identifies the specific period)
  status: VARCHAR(20)                  — "pending", "filed", "overdue", "not_applicable"
  filed_at: TIMESTAMP (nullable)       — when it was marked as filed
  filed_by: UUID (nullable)            — member who marked it
  notes: TEXT (nullable)               — e.g., "Filed via eFiling, ref 12345"
  linked_project_id: UUID (nullable)   — project handling this engagement
  created_at / updated_at: TIMESTAMP
```

**Unique constraint**: `(customer_id, deadline_type_slug, period_key)` — one status per client per deadline per period.

Filing status is created lazily — when the deadline calculation service finds a deadline with no matching filing status record, the status is "pending" by default (no record needed). Records are created when a user explicitly marks a deadline as "filed" or "not applicable."

### 1.4 Deadline Calendar Endpoints

```
GET  /api/deadlines                           — list all deadlines for date range + filters
GET  /api/deadlines/summary                   — aggregate counts by month/category
GET  /api/customers/{id}/deadlines            — deadlines for a specific customer
PUT  /api/deadlines/filing-status             — update filing status (batch endpoint)
```

**Query parameters for GET /api/deadlines:**
- `from` / `to` — date range (required)
- `category` — filter by category (tax, corporate, vat, payroll)
- `status` — filter by status (pending, filed, overdue)
- `customerId` — filter by customer

**Authorization**: ADMIN or OWNER. Members can view but not update filing status.

### 1.5 Frontend — Deadline Calendar Page

Add a **"Deadlines"** page to the sidebar (in the Clients zone, after Compliance). Module-gated behind `regulatory_deadlines`.

**Views:**
- **Month view**: Calendar grid showing deadline counts per day, colored by status (green = filed, red = overdue, amber = approaching). Click a day to see the list.
- **List view**: Table of all deadlines in range. Columns: Client, Deadline Type, Due Date, Status, Linked Engagement, Actions. Sortable and filterable.
- **Summary view**: Cards showing counts by category for the current quarter. "12 tax deadlines (8 filed, 2 pending, 2 overdue)."

**Actions:**
- Mark as filed (single or batch) — opens a dialog to record filing date, notes, reference number
- Mark as not applicable — with reason
- Link to engagement — associate with an existing project
- Create engagement — create a new project for this client/deadline (using the appropriate template)

**Dashboard widget**: A compact version showing "Upcoming deadlines this month" for the company dashboard.

---

## Section 2 — Post-Schedule Automation (Engagement Kickoff)

### 2.1 Schedule Post-Create Actions

Extend the `RecurringSchedule` entity with optional post-creation actions:

```
RecurringSchedule (existing entity, extended)
  + post_create_actions: JSONB (nullable)
```

**post_create_actions schema:**
```json
{
  "generateDocument": {
    "templateSlug": "engagement-letter-tax-return",
    "autoSend": false
  },
  "sendInfoRequest": {
    "requestTemplateSlug": "year-end-info-request-za",
    "dueDays": 14
  }
}
```

Both actions are optional. A schedule can have neither, either, or both.

### 2.2 Post-Create Action Executor

After `RecurringScheduleExecutor` creates a project, if the schedule has `post_create_actions`:

1. **generateDocument**: Call `GeneratedDocumentService.generate()` with the project as the primary entity and the specified template. The generated document is linked to the project. If `autoSend` is true and the customer has a portal contact, also trigger document acceptance (Phase 28 flow). For v1, `autoSend` is always false — the firm reviews the generated document before sending.

2. **sendInfoRequest**: Call `InformationRequestService.createFromTemplate()` with the customer, the specified request template slug, and the due date calculated from today + `dueDays`. This creates the info request and (if email is configured) notifies the portal contact.

**Error handling**: Post-create actions are best-effort. If document generation fails (e.g., missing template variables), the project is still created. An audit event records the failure. A notification is sent to the project creator: "Engagement created but automatic document generation failed — generate manually."

### 2.3 Schedule Settings UI Extension

Extend the recurring schedule create/edit dialog with an optional "After Creation" section:

- **Generate document**: Toggle + template selector dropdown (filtered by the schedule's project template's entity type)
- **Send information request**: Toggle + request template selector dropdown + due days input
- Helper text: "These actions run automatically each time this schedule creates an engagement."

---

## Section 3 — Profile-Based Onboarding Seeding

### 3.1 Operational Pack Seeder

Extend the existing pack seeder infrastructure with two new pack types:

**Rate card pack** (`rate-packs/accounting-za.json`):
```json
{
  "packId": "rate-pack-accounting-za",
  "rates": [
    { "description": "Partner", "hourlyRate": 2500.00, "currency": "ZAR" },
    { "description": "Manager", "hourlyRate": 1800.00, "currency": "ZAR" },
    { "description": "Senior Accountant", "hourlyRate": 1200.00, "currency": "ZAR" },
    { "description": "Clerk / Trainee", "hourlyRate": 650.00, "currency": "ZAR" }
  ]
}
```

These are seeded as org-level `BillingRate` entries (the top of the rate hierarchy). Tenants can adjust amounts, add/remove tiers, and override per-project or per-customer as usual.

**Schedule configuration pack** (`schedule-packs/accounting-za.json`):
```json
{
  "packId": "schedule-pack-accounting-za",
  "schedules": [
    {
      "name": "Annual Tax Return Preparation",
      "projectTemplateName": "Tax Return Engagement",
      "recurrence": "YEARLY",
      "description": "Creates annual tax return engagements for each client. Runs after financial year-end.",
      "postCreateActions": {
        "generateDocument": { "templateSlug": "engagement-letter-tax-return" },
        "sendInfoRequest": { "requestTemplateSlug": "year-end-info-request-za", "dueDays": 14 }
      }
    },
    {
      "name": "Monthly Bookkeeping",
      "projectTemplateName": "Monthly Bookkeeping",
      "recurrence": "MONTHLY",
      "description": "Creates monthly bookkeeping projects for retainer clients."
    }
  ]
}
```

These are seeded as `RecurringSchedule` entries but in a **disabled/draft state** — the tenant must review, assign to specific customers, and activate. This avoids auto-creating projects for customers the firm hasn't set up yet.

### 3.2 Seeding Trigger

Seeding happens in two scenarios:
1. **Profile selection** — when a tenant switches to `accounting-za` profile via Settings → Profile Switching (Phase 49). The existing `ProfileSwitchService` calls pack seeders. Add rate and schedule pack seeders to the chain.
2. **Org provisioning** — when a new org is approved with the `accounting-za` industry selection (Phase 39). The provisioning flow already calls pack seeders.

Both scenarios use the same idempotent seeders. Running twice is safe.

### 3.3 Seeding UI Feedback

After profile selection, show a summary: "Accounting profile applied. Seeded: 4 rate card tiers, 2 schedule templates (inactive). Review in Settings → Rates and Settings → Recurring Schedules."

---

## Section 4 — Module Registration

### 4.1 New Module: `regulatory_deadlines`

Register in the module registry (Phase 49 `ModuleRegistry`):

```
Module:
  id: "regulatory_deadlines"
  name: "Regulatory Deadlines"
  description: "Firm-wide calendar of regulatory filing deadlines with status tracking"
  defaultEnabledFor: ["accounting-za"]
  navItems: [{ path: "/deadlines", label: "Deadlines", zone: "clients" }]
```

The module guard gates the `/api/deadlines` endpoints and the sidebar nav item.

---

## Section 5 — API Summary

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET` | `/api/deadlines` | List calculated deadlines for date range |
| `GET` | `/api/deadlines/summary` | Aggregate deadline counts by month/category |
| `GET` | `/api/customers/{id}/deadlines` | Customer-specific deadlines |
| `PUT` | `/api/deadlines/filing-status` | Batch update filing status |
| `GET` | `/api/filing-statuses` | List filing status records with filters |
| `GET` | `/api/settings/rate-packs` | List available rate packs for current profile |
| `GET` | `/api/settings/schedule-packs` | List available schedule packs for current profile |

Existing endpoints extended:
- `POST/PUT /api/schedules` — extended with `postCreateActions` field
- `GET /api/schedules/{id}` — returns `postCreateActions` in response

---

## Section 6 — Testing

### 6.1 Backend Tests

| Test | What it verifies |
|------|-----------------|
| `DeadlineCalculationServiceTest` | Correctly calculates provisional tax dates from FYE |
| `DeadlineCalculationServiceTest` | VAT deadlines only generated for VAT-registered clients |
| `DeadlineCalculationServiceTest` | CIPC deadlines only for Pty Ltd entities |
| `DeadlineCalculationServiceTest` | Filing status correctly overlaid on calculated deadlines |
| `DeadlineCalculationServiceTest` | Date range filtering works correctly |
| `DeadlineCalculationServiceTest` | Links to existing projects by customer + engagement type + tax year |
| `FilingStatusServiceTest` | Create/update filing status with unique constraint |
| `FilingStatusServiceTest` | Batch update works correctly |
| `DeadlineControllerTest` | Authorization: ADMIN/OWNER can update, MEMBER can view |
| `DeadlineControllerTest` | Module guard blocks access when module disabled |
| `RecurringScheduleExecutorTest` | Post-create actions: document generated after project creation |
| `RecurringScheduleExecutorTest` | Post-create actions: info request sent after project creation |
| `RecurringScheduleExecutorTest` | Post-create actions: failure doesn't block project creation |
| `RecurringScheduleExecutorTest` | No post-create actions: existing behavior unchanged |
| `RatePackSeederTest` | Seeds org-level billing rates from pack |
| `RatePackSeederTest` | Idempotent: second run doesn't duplicate |
| `SchedulePackSeederTest` | Seeds recurring schedules in disabled state |
| `SchedulePackSeederTest` | Idempotent: second run doesn't duplicate |

### 6.2 Frontend Tests

| Test | What it verifies |
|------|-----------------|
| Deadline calendar page | Renders month view with deadline counts |
| Deadline calendar page | List view shows all deadlines in range |
| Deadline calendar page | Summary cards show correct counts |
| Deadline calendar page | Filter by category, status, customer works |
| Filing status dialog | Mark as filed with date, notes, reference |
| Filing status dialog | Batch selection and update |
| Schedule post-create actions | Toggle shows/hides action config |
| Schedule post-create actions | Template selector filters by entity type |
| Dashboard deadline widget | Shows upcoming deadlines for current month |
| Module gate | Deadlines nav hidden when module disabled |

---

## Out of Scope

- **SARS eFiling integration.** No API calls to SARS. Filing status is manually tracked. eFiling integration is a separate, much larger effort involving SARS's SOAP/XML APIs and security certificates.
- **Automated deadline recalculation on FYE change.** If a client's financial year-end changes, existing filing statuses are not automatically recalculated. The firm would need to manually review. This is an edge case — FYE changes are rare and require SARS approval.
- **Client-specific deadline customization.** All clients with the same FYE get the same deadline types. Per-client deadline overrides (e.g., "this client has an extension") are not in scope. Staff can use notes on the filing status.
- **VAT return period customization.** Currently bi-monthly (the standard in SA). Some large businesses file monthly. Per-client VAT period is future work.
- **Multi-jurisdiction deadline types.** Only ZA deadlines in this phase. The registry is jurisdiction-aware for future expansion.
- **Push notifications for deadlines.** The existing `FIELD_DATE_APPROACHING` automation already handles notifications for individual deadlines. The calendar is a visibility tool, not a notification system.
- **Project template seeding.** The schedule pack references project templates by name, but doesn't create them. Project templates are expected to exist from the `accounting-za` template pack (Phase 47) or be created manually. If the referenced template doesn't exist, the schedule remains inactive with a warning.

## ADR Topics

- **Calculated vs. stored deadlines** — why deadlines are computed on-the-fly rather than stored as entities. Calculated deadlines always reflect the current client FYE and deadline rules. Stored deadlines create synchronization problems when FYE changes or rules are updated. Filing status is the only stored state.
- **Post-create action execution model** — synchronous within the schedule execution transaction vs. async via event. Recommend synchronous-in-transaction for v1 (simpler, uses existing service calls). If performance becomes an issue with many schedules, can be moved to async later.
- **Filing status lazy creation** — why no pre-population of filing status records. Creating records for every client × deadline type × period would generate thousands of rows. Lazy creation (record only when status changes from "pending") keeps the table small and avoids synchronization issues.

## Style & Boundaries

- The deadline calendar page follows the existing calendar page pattern (Phase 30) for layout and view switching (month/list).
- The deadline type registry is static Java code (like `JurisdictionDefaults`), not a database entity. Deadline types are not tenant-configurable in v1.
- Filing status is a simple entity — no lifecycle state machine, no workflow. Just a status enum and metadata.
- Post-schedule actions use direct service calls (not the automation engine). The automation engine is event-driven and rules-based; post-schedule actions are deterministic and schedule-specific.
- Pack seeders follow the exact pattern of existing seeders (`FieldPackSeeder`, `CompliancePackSeeder`): JSON classpath resources, idempotent application, profile-filtered.
- The deadline calendar is the first module-gated page that's not a legal stub — it's a real feature. Ensure the module guard integration is clean.
