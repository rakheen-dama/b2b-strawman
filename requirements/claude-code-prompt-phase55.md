# Phase 55 — Legal Foundations: Court Calendar, Conflict Check & LSSA Tariff

## System Context

DocTeams (Kazi) is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 53 phases of functionality. The platform has a vertical architecture (Phase 49) with module guards, profile system, and tenant-gated modules. An `accounting-za` vertical profile is fully operational with field packs, template packs, compliance packs, clause packs, automation packs, a regulatory deadline calendar (Phase 51), and post-schedule automation.

**The existing infrastructure that this phase builds on**:

- **Vertical profile system** (Phase 49): `VerticalModuleGuard`, `OrgProfileProvider`, `ModuleGate`, `VerticalProfileRegistry`, `VerticalProfileService`. Module IDs are snake_case strings. `OrgSettings.enabled_modules` (JSONB) gates access per tenant. Legal profile already defined with stubs: `enabled_modules: ["trust_accounting", "court_calendar", "conflict_check"]`.
- **Legal stubs** (Phase 49): Stub controllers in `verticals.legal.trustaccounting`, `verticals.legal.courtcalendar`, `verticals.legal.conflictcheck` packages. Stub frontend pages at `/legal/trust-accounting`, `/legal/court-calendar`, `/legal/conflict-check`. These return placeholder responses — this phase replaces the court calendar and conflict check stubs with real implementations.
- **Deadline calendar pattern** (Phase 51): `DeadlineTypeRegistry`, `DeadlineCalculationService`, `FilingStatus` entity. The accounting deadline calendar is gated behind `regulatory_deadlines` module. The same architectural pattern (type registry + calculation service + status entity) can inform the court calendar design, but legal deadlines have fundamentally different characteristics (externally set dates, not calculated from fiscal year-end).
- **Rate card system** (Phase 8): `BillingRate`, `CostRate` with 3-level hierarchy (org → project → customer). `OrgSettings` has default rates. Rate pack seeders (Phase 51) seed org-level rates at profile provisioning.
- **Custom fields** (Phase 11, extended Phase 23): `FieldDefinition`, `FieldValue` entities with pack seeders. The `legal-za` field pack was declared in Phase 49 profile definition but not yet populated with content.
- **Pack seeder infrastructure**: `FieldPackSeeder`, `TemplatePackSeeder`, `CompliancePackSeeder`, `ClausePackSeeder`, `AutomationPackSeeder`, `RatePackSeeder`, `SchedulePackSeeder`. All follow the same pattern — JSON resource files loaded at profile application.
- **Module registration** (Phase 49): `ModuleRegistry` with nav items, description, and `defaultEnabledFor` profiles.
- **Customer entity**: `Customer` with lifecycle (PROSPECT → ONBOARDING → ACTIVE → DORMANT → ARCHIVED), custom fields, linked projects, compliance checklists.
- **Project entity**: Projects with tasks, time entries, documents, proposals, invoices. Legal terminology maps "Projects" → "Matters" via the i18n message catalog.
- **Frontend module gating**: `ModuleGate` component wraps UI sections, `useOrgProfile()` hook for conditional rendering, sidebar nav items conditionally shown per module.
- **Automation engine** (Phase 37): `AutomationRule`, trigger types, action types. Can be extended with legal-specific triggers.

**The problem**: The legal profile has stubs but no real domain logic. This means:
1. The multi-vertical architecture has never been stress-tested with two real verticals coexisting — only accounting has real modules.
2. A law firm tenant can be provisioned with the `legal-za` profile, but the legal-specific pages are placeholders. The promise of module-gated verticals is unproven.
3. The rate card system has never been extended with a vertical-specific rate source (LSSA tariff). It's unclear whether shared systems can accommodate vertical logic without breaking other verticals.
4. No legal-specific pack content exists (field packs, template packs, clause packs, compliance packs). Provisioning a legal tenant seeds nothing useful.

**The fix**: Build three real legal modules that replace the stubs, populate legal pack content, and — critically — prove that two real verticals coexist cleanly in the same codebase, same database, same deployment.

## Objective

1. **Court Calendar & Legal Deadlines** — Replace the `court_calendar` stub with a real module. Track court dates (hearing, trial, motion, mediation), prescription periods (calculated from statutory rules), and filing deadlines (court-imposed). Integrate with the existing calendar view. Unlike accounting deadlines (calculated from fiscal year-end), legal deadlines are a mix: some are externally set (court dates), some are calculated (prescription), some are relative (filing deadlines from court orders). The module must handle all three patterns.

2. **Conflict-of-Interest Check** — Replace the `conflict_check` stub with a real module. Maintain an adverse party registry linked to matters/customers. Before accepting a new client or matter, search for conflicts (acting against existing clients, related parties). Log all conflict checks as an audit trail (required by the Legal Practice Act). Support conflict waivers with signed acknowledgement tracking.

3. **LSSA Tariff Rates** — Add a new module (`lssa_tariff`) that extends the rate card system with published Law Society tariff schedules. Support party-and-party and attorney-and-client tariff categories. Allow firms to use tariff rates as a rate source alongside custom hourly rates. Tariff rates are per-item (not hourly) — the rate card system must accommodate this without breaking the hourly rate model used by accounting.

4. **Legal Pack Content** — Populate the `legal-za` field packs, template packs, clause packs, and compliance packs with real SA legal practice content. This turns the legal profile from "empty shell" to "ready for use."

5. **Multi-Vertical Coexistence Validation** — Explicit integration tests proving that an accounting tenant and a legal tenant can coexist: migrations work, module guards gate correctly, pack seeders don't interfere, shared endpoints return correct data per tenant, and frontend routing works for both profiles.

## Constraints & Assumptions

- **Trust accounting is explicitly OUT.** The `trust_accounting` module remains a stub. This phase builds court calendar and conflict check only. Trust accounting is a separate future phase.
- **Section 35 reporting is OUT.** Depends on trust accounting.
- **No court system integrations.** No CaseLines, no e-filing. All court dates are manually entered by the firm.
- **LSSA tariff data is seeded, not scraped.** The 2024/2025 tariff schedule is hardcoded as seed data. Tariff updates are manual (admin uploads or future admin UI). No LSSA API exists.
- **Prescription rules are SA-specific.** The Prescription Act 68 of 1969 defines general (3 years), debt (6 years), and mortgage bond (30 years) periods. The calculator is jurisdiction-specific but modular — other jurisdictions can define their own rules later, following the same pattern as `JurisdictionDefaults` from Phase 50.
- **Legal terminology overrides exist.** Phase 49 defined the `en-ZA-legal` terminology namespace: "Projects" → "Matters", "Customers" → "Clients", etc. This phase does NOT change the terminology system — it uses what's already there.
- **Conflict check is advisory, not blocking.** Finding a conflict flags it and logs the check but does NOT prevent the firm from proceeding. The firm may choose to get a waiver. This is how SA law firms operate — conflicts are managed, not automatically rejected.
- **Flyway migrations are non-conditional.** Legal-specific tables (court dates, adverse parties, conflicts, tariff schedules) are created in every tenant schema. Module guard controls access, not table presence. This is the existing pattern — accounting tables exist in legal schemas too.
- **This phase is the architecture stress test.** Every design decision should be evaluated against: "does this work when both accounting and legal tenants exist in the same deployment?" If a pattern requires vertical-specific conditionals in shared code, that's a red flag.

---

## Section 1 — Court Calendar & Legal Deadlines

### 1.1 Data Model

**CourtDate entity** (tenant-scoped, new table `court_dates`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `project_id` | `UUID FK` | The matter this court date belongs to |
| `customer_id` | `UUID FK` | The client (denormalized for cross-matter queries) |
| `date_type` | `VARCHAR(30)` | Enum: `HEARING`, `TRIAL`, `MOTION`, `MEDIATION`, `ARBITRATION`, `PRE_TRIAL`, `CASE_MANAGEMENT`, `TAXATION`, `OTHER` |
| `scheduled_date` | `DATE` | The date of the court event |
| `scheduled_time` | `TIME` | Optional — time of hearing |
| `court_name` | `VARCHAR(200)` | E.g., "Johannesburg High Court", "Randburg Magistrate's Court" |
| `court_reference` | `VARCHAR(100)` | Case number at court, e.g., "2026/12345" |
| `judge_magistrate` | `VARCHAR(200)` | Optional — assigned judge or magistrate |
| `description` | `TEXT` | What the appearance is about |
| `status` | `VARCHAR(20)` | Enum: `SCHEDULED`, `POSTPONED`, `HEARD`, `CANCELLED` |
| `outcome` | `TEXT` | Optional — result of the hearing |
| `reminder_days` | `INTEGER` | Default 7 — days before the date to send a reminder |
| `created_by` | `UUID FK` | Member who created it |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**Unique constraint**: None — a matter can have multiple court dates of the same type.

**PrescriptionTracker entity** (tenant-scoped, new table `prescription_trackers`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `project_id` | `UUID FK` | The matter this tracks |
| `customer_id` | `UUID FK` | Denormalized for cross-matter queries |
| `cause_of_action_date` | `DATE` | When the cause of action arose |
| `prescription_type` | `VARCHAR(30)` | Enum: `GENERAL_3Y`, `DEBT_6Y`, `MORTGAGE_30Y`, `DELICT_3Y`, `CONTRACT_3Y`, `CUSTOM` |
| `custom_years` | `INTEGER` | Only used when type is `CUSTOM` |
| `prescription_date` | `DATE` | Calculated: `cause_of_action_date` + period. Stored for query efficiency. |
| `interruption_date` | `DATE` | Optional — date prescription was interrupted (e.g., by service of summons) |
| `interruption_reason` | `VARCHAR(200)` | E.g., "Service of combined summons" |
| `status` | `VARCHAR(20)` | Enum: `RUNNING`, `INTERRUPTED`, `EXPIRED`, `WARNED` |
| `notes` | `TEXT` | |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**Prescription rules** (static, not stored — same pattern as `DeadlineTypeRegistry`):

| Type | Period | Reference |
|------|--------|-----------|
| `GENERAL_3Y` | 3 years | Prescription Act s11(d) — general |
| `DEBT_6Y` | 6 years | Prescription Act s11(a) — debt acknowledged in writing |
| `MORTGAGE_30Y` | 30 years | Prescription Act s11(b) — mortgage bonds, judgments |
| `DELICT_3Y` | 3 years | Prescription Act s11(d) — delictual claims |
| `CONTRACT_3Y` | 3 years | Prescription Act s11(d) — contractual claims |
| `CUSTOM` | N years | Firm-defined for special statutes |

**Important**: Prescription is interrupted (reset) by judicial process (service of process, acknowledgment of debt). When `interruption_date` is set, prescription restarts from that date — but for purposes of this phase, interrupted prescriptions are simply marked as `INTERRUPTED` status. Full restart calculation is a complexity we defer.

### 1.2 Court Calendar Service

```
CourtCalendarService
  + listCourtDates(dateRange, filters) → Page<CourtDate>
  + getCourtDate(id) → CourtDate
  + createCourtDate(projectId, dto) → CourtDate
  + updateCourtDate(id, dto) → CourtDate
  + cancelCourtDate(id, reason) → CourtDate
  + postponeCourtDate(id, newDate, reason) → CourtDate
  + listPrescriptionTrackers(filters) → Page<PrescriptionTracker>
  + createPrescriptionTracker(projectId, dto) → PrescriptionTracker
  + updatePrescriptionTracker(id, dto) → PrescriptionTracker
  + interruptPrescription(id, interruptionDate, reason) → PrescriptionTracker
  + getUpcomingPrescriptions(daysAhead) → List<PrescriptionTracker>
  + getUpcomingCourtDates(daysAhead) → List<CourtDate>
```

**Module gating**: Every method starts with `moduleGuard.requireModule("court_calendar")`.

**Audit events**: Court date creation, update, cancellation, postponement. Prescription tracker creation and interruption. Uses existing `AuditEventService`.

**Notifications**: Court date reminders sent `reminder_days` before the scheduled date. Prescription warnings sent at 90 days, 30 days, and 7 days before expiry. Uses existing notification infrastructure. A `CourtDateReminderJob` (scheduled, same pattern as `FieldDateScannerJob`) scans daily for upcoming court dates and prescription dates.

### 1.3 Court Calendar Endpoints

```
GET    /api/court-dates                     — list court dates with filters (date range, type, status, customer, project)
GET    /api/court-dates/{id}                — single court date detail
POST   /api/court-dates                     — create a court date (requires project_id)
PUT    /api/court-dates/{id}                — update a court date
POST   /api/court-dates/{id}/postpone       — postpone to new date with reason
POST   /api/court-dates/{id}/cancel         — cancel with reason
POST   /api/court-dates/{id}/outcome        — record hearing outcome

GET    /api/prescription-trackers           — list prescription trackers with filters
GET    /api/prescription-trackers/{id}      — single tracker detail
POST   /api/prescription-trackers           — create a tracker (requires project_id)
PUT    /api/prescription-trackers/{id}      — update a tracker
POST   /api/prescription-trackers/{id}/interrupt  — record interruption

GET    /api/court-calendar/upcoming         — combined: upcoming court dates + prescription warnings (dashboard widget)
```

**Authorization**: All endpoints require `VIEW_LEGAL` capability (new capability). Write operations require `MANAGE_LEGAL` capability. These map to existing RBAC capability system (Phase 41/46).

### 1.4 Frontend — Court Calendar Page

Replace the `/legal/court-calendar` stub page with a real implementation. Module-gated behind `court_calendar`.

**Views:**
- **Calendar view**: Month grid showing court dates (colored by type: blue = hearing, red = trial, amber = motion). Click a date to see details. Prescription expiry dates shown as warning markers. Reuses the chart/calendar components from Phase 53 where possible.
- **List view**: Table of all court dates. Columns: Matter, Client, Type, Date, Court, Status, Actions. Sortable, filterable, paginated.
- **Prescription view**: Dedicated list of prescription trackers. Columns: Matter, Client, Type, Cause of Action Date, Prescription Date, Days Remaining, Status. Sorted by days remaining (urgent first). Color-coded: red (<30 days), amber (<90 days), green (>90 days).

**Actions:**
- Create court date — dialog with matter selector, type, date, time, court name, court reference, judge, description
- Postpone — dialog with new date and reason
- Cancel — dialog with reason
- Record outcome — dialog with outcome text, status update
- Create prescription tracker — dialog with matter selector, cause of action date, prescription type
- Record interruption — dialog with date and reason

**Sidebar navigation**: "Court Calendar" in the Clients zone (after Compliance, same zone as Deadlines for accounting). Conditionally shown via `ModuleGate`.

**Dashboard widget**: "Upcoming Court Dates" compact card on the company dashboard — next 5 court dates with countdown. Module-gated.

### 1.5 Matter Detail Integration

On the project (matter) detail page, add a **"Court Dates" tab** (module-gated). Shows:
- List of court dates for this matter, with create/edit/postpone/cancel actions
- Prescription tracker(s) for this matter, with create/edit/interrupt actions
- Timeline visualization of the matter's court history

This tab only renders for tenants with `court_calendar` enabled.

---

## Section 2 — Conflict-of-Interest Check

### 2.1 Data Model

**AdverseParty entity** (tenant-scoped, new table `adverse_parties`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `name` | `VARCHAR(300)` | Name of the adverse party (person or entity) |
| `id_number` | `VARCHAR(20)` | Optional — SA ID number or passport number |
| `registration_number` | `VARCHAR(30)` | Optional — company registration number |
| `party_type` | `VARCHAR(20)` | Enum: `NATURAL_PERSON`, `COMPANY`, `TRUST`, `CLOSE_CORPORATION`, `PARTNERSHIP`, `OTHER` |
| `aliases` | `TEXT` | Optional — comma-separated aliases or trading names |
| `notes` | `TEXT` | |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**AdversePartyLink entity** (tenant-scoped, new table `adverse_party_links`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `adverse_party_id` | `UUID FK` | |
| `project_id` | `UUID FK` | The matter this party is linked to |
| `customer_id` | `UUID FK` | The client on the other side |
| `relationship` | `VARCHAR(30)` | Enum: `OPPOSING_PARTY`, `WITNESS`, `CO_ACCUSED`, `RELATED_ENTITY`, `GUARANTOR` |
| `description` | `TEXT` | Context about the relationship |
| `created_at` | `TIMESTAMP` | |

**Unique constraint on links**: `(adverse_party_id, project_id)` — an adverse party is linked to a matter once.

**ConflictCheck entity** (tenant-scoped, new table `conflict_checks`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `checked_name` | `VARCHAR(300)` | The name that was searched |
| `checked_id_number` | `VARCHAR(20)` | Optional — ID number searched |
| `checked_registration_number` | `VARCHAR(30)` | Optional — registration number searched |
| `check_type` | `VARCHAR(20)` | Enum: `NEW_CLIENT`, `NEW_MATTER`, `PERIODIC_REVIEW` |
| `result` | `VARCHAR(20)` | Enum: `NO_CONFLICT`, `CONFLICT_FOUND`, `POTENTIAL_CONFLICT` |
| `conflicts_found` | `JSONB` | Array of conflict details: `[{ adversePartyId, projectId, customerId, relationship, explanation }]` |
| `resolution` | `VARCHAR(30)` | Nullable. Enum: `PROCEED`, `DECLINED`, `WAIVER_OBTAINED`, `REFERRED` |
| `resolution_notes` | `TEXT` | |
| `waiver_document_id` | `UUID FK` | Optional — link to a generated/uploaded waiver document |
| `checked_by` | `UUID FK` | Member who performed the check |
| `resolved_by` | `UUID FK` | Member who resolved the conflict |
| `checked_at` | `TIMESTAMP` | When the check was performed |
| `resolved_at` | `TIMESTAMP` | When the resolution was recorded |
| `customer_id` | `UUID FK` | Optional — the client being checked (for NEW_CLIENT checks) |
| `project_id` | `UUID FK` | Optional — the matter being checked (for NEW_MATTER checks) |

### 2.2 Conflict Search Algorithm

The conflict search is a **fuzzy name match + exact ID match** across three data sources:

1. **Existing clients** (Customer table) — is the checked name/ID already a client? If so, check if we're acting for AND against them in different matters.
2. **Adverse party registry** (AdverseParty table) — is the checked name/ID an adverse party in any existing matter?
3. **Adverse party links** — if a match is found, which matters and clients are involved?

**Name matching**: Case-insensitive, trimmed, with trigram similarity (PostgreSQL `pg_trgm` extension). Threshold: 0.3 similarity score. This catches common misspellings and name variations. The `pg_trgm` extension should already be available in the tenant schema — if not, the migration enables it.

**Exact ID matching**: If `id_number` or `registration_number` is provided, exact match takes priority over name similarity.

**Result classification**:
- `NO_CONFLICT` — no matches found
- `CONFLICT_FOUND` — exact match on ID number or high-similarity name match (>0.6) with active matters
- `POTENTIAL_CONFLICT` — moderate similarity (0.3-0.6) or match on aliases

### 2.3 Conflict Check Service

```
ConflictCheckService
  + performCheck(dto: ConflictCheckRequest) → ConflictCheckResult
  + resolveConflict(checkId, resolution, notes, waiverId?) → ConflictCheck
  + listChecks(filters) → Page<ConflictCheck>
  + getCheck(id) → ConflictCheck
  + getChecksForCustomer(customerId) → List<ConflictCheck>
  + getChecksForProject(projectId) → List<ConflictCheck>
```

```
AdversePartyService
  + create(dto) → AdverseParty
  + update(id, dto) → AdverseParty
  + delete(id) → void (soft delete or unlink)
  + list(filters) → Page<AdverseParty>
  + get(id) → AdverseParty
  + linkToProject(adversePartyId, projectId, customerId, relationship) → AdversePartyLink
  + unlinkFromProject(linkId) → void
  + getLinksForProject(projectId) → List<AdversePartyLink>
```

**Module gating**: `moduleGuard.requireModule("conflict_check")`.

**Audit events**: Every conflict check is logged as an audit event (regardless of result). Resolution changes are logged. This is a regulatory requirement — the firm must demonstrate it performs conflict checks.

### 2.4 Conflict Check Endpoints

```
POST   /api/conflict-checks                 — perform a conflict check (returns result immediately)
GET    /api/conflict-checks                  — list conflict check history (paginated, filterable)
GET    /api/conflict-checks/{id}             — single check detail
POST   /api/conflict-checks/{id}/resolve     — resolve a conflict (proceed/decline/waiver/refer)

GET    /api/adverse-parties                  — list adverse parties (paginated, searchable)
GET    /api/adverse-parties/{id}             — single adverse party detail
POST   /api/adverse-parties                  — create adverse party
PUT    /api/adverse-parties/{id}             — update adverse party
DELETE /api/adverse-parties/{id}             — remove adverse party (only if no active links)

POST   /api/adverse-parties/{id}/links       — link to a matter
DELETE /api/adverse-party-links/{linkId}      — unlink from a matter
GET    /api/projects/{id}/adverse-parties    — adverse parties for a specific matter
```

**Authorization**: `VIEW_LEGAL` for reads, `MANAGE_LEGAL` for writes.

### 2.5 Frontend — Conflict Check

Replace the `/legal/conflict-check` stub with a real implementation. Module-gated behind `conflict_check`.

**Pages:**

1. **Conflict Check page** (`/legal/conflict-check`):
   - **Run Check** section at top: Name input, optional ID number, optional registration number, "Check" button. Results displayed inline: green (no conflict), red (conflict found), amber (potential conflict). Each match shows the adverse party, linked matter, client, and relationship.
   - **Resolution** section (when conflict found): Radio buttons for resolution (proceed, decline, waiver, refer) + notes + optional waiver document upload.
   - **Check History** section: Paginated table of past checks. Columns: Date, Checked Name, Type, Result, Resolution, Checked By.

2. **Adverse Party Registry** (`/legal/adverse-parties`):
   - Table of all adverse parties. Columns: Name, Type, ID Number, Registration Number, Linked Matters (count), Actions.
   - Create/Edit dialog for adverse party management.
   - Click a party to see all linked matters and relationships.

**Sidebar navigation**: "Conflict Check" and "Adverse Parties" in the Clients zone. Module-gated.

### 2.6 Matter Detail Integration

On the project (matter) detail page, add an **"Adverse Parties" tab** (module-gated behind `conflict_check`). Shows:
- List of adverse parties linked to this matter, with relationship type
- "Add Adverse Party" action (create new or link existing)
- "Run Conflict Check" action (pre-fills the matter's parties)

---

## Section 3 — LSSA Tariff Rates

### 3.1 Concept

The Law Society of South Africa publishes tariff schedules that define standard fees for legal work. These are fundamentally different from hourly rates:

- **Hourly rates**: Amount per hour worked (existing `BillingRate` system)
- **Tariff rates**: Fixed amount per activity/item (e.g., "Drawing of summons — R1,250", "Consultation per quarter-hour — R780")

There are two tariff categories:
- **Party-and-party** (P&P): Recoverable costs — what the losing party pays the winning party's attorneys. Set by the court.
- **Attorney-and-client** (A&C): What the attorney charges their own client. Typically higher than P&P. Set by agreement but guided by the tariff.

### 3.2 Data Model

**TariffSchedule entity** (tenant-scoped, new table `tariff_schedules`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `name` | `VARCHAR(100)` | E.g., "LSSA 2024/2025 High Court" |
| `category` | `VARCHAR(20)` | Enum: `PARTY_AND_PARTY`, `ATTORNEY_AND_CLIENT` |
| `court_level` | `VARCHAR(30)` | Enum: `HIGH_COURT`, `MAGISTRATE_COURT`, `CONSTITUTIONAL_COURT` |
| `effective_from` | `DATE` | Start of validity period |
| `effective_to` | `DATE` | End of validity period (nullable for current schedule) |
| `is_active` | `BOOLEAN` | Whether this is the current schedule |
| `is_system` | `BOOLEAN` | Whether this was seeded (read-only) vs custom (editable) |
| `source` | `VARCHAR(100)` | "LSSA Gazette 2024" |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**TariffItem entity** (tenant-scoped, new table `tariff_items`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `schedule_id` | `UUID FK` | Parent tariff schedule |
| `item_number` | `VARCHAR(20)` | Tariff item reference, e.g., "1(a)", "2(b)(ii)" |
| `section` | `VARCHAR(100)` | Grouping header, e.g., "Instructions and consultations", "Pleadings and documents" |
| `description` | `TEXT` | Full description of the activity |
| `amount` | `DECIMAL(12,2)` | Amount in ZAR |
| `unit` | `VARCHAR(30)` | What the amount is per — `PER_ITEM`, `PER_PAGE`, `PER_FOLIO`, `PER_QUARTER_HOUR`, `PER_HOUR`, `PER_DAY` |
| `notes` | `TEXT` | Optional — conditions or explanations |
| `sort_order` | `INTEGER` | Display ordering within the section |

### 3.3 Integration with Rate Cards

The tariff system extends the existing rate card system without modifying the `BillingRate` entity or the core rate resolution logic. Instead:

1. **Invoice line items** can reference a tariff item instead of (or in addition to) an hourly rate. The existing `InvoiceLine` entity gets an optional `tariff_item_id` FK column. When a tariff item is used, the line amount comes from the tariff, not from hours x rate.

2. **Rate source indicator**: When generating an invoice for a matter (module-gated: only when `lssa_tariff` is enabled), the user can choose between:
   - **Time-based billing**: Normal hourly rate x hours (existing flow)
   - **Tariff-based billing**: Select tariff items and quantities (new flow)
   - **Hybrid**: Some lines are time-based, some are tariff-based (most common in practice)

3. **No changes to `BillingRate`**: The tariff system is additive. Accounting tenants never see tariff-related UI or API responses. The tariff endpoints are module-gated behind `lssa_tariff`.

### 3.4 Tariff Service

```
TariffService
  + listSchedules(filters) → List<TariffSchedule>
  + getSchedule(id) → TariffSchedule (with items)
  + getActiveSchedule(category, courtLevel) → TariffSchedule
  + listItems(scheduleId, filters) → List<TariffItem>
  + getItem(id) → TariffItem
  + searchItems(query, scheduleId?) → List<TariffItem>  // full-text search on description
```

Tariff schedules are **read-only for regular users** — they're seeded from LSSA published data. An admin/owner can create custom tariff schedules (e.g., "Firm Standard Rates 2025") but cannot modify the LSSA-seeded schedules.

Admin operations (for custom schedules only):
```
  + createSchedule(dto) → TariffSchedule
  + updateSchedule(id, dto) → TariffSchedule
  + createItem(scheduleId, dto) → TariffItem
  + updateItem(id, dto) → TariffItem
  + deleteItem(id) → void
  + cloneSchedule(id, newName) → TariffSchedule  // clone LSSA schedule as editable custom
```

### 3.5 Tariff Endpoints

```
GET    /api/tariff-schedules                 — list all tariff schedules
GET    /api/tariff-schedules/{id}            — schedule detail with items
GET    /api/tariff-schedules/active          — get the current active schedule for given category + court level
GET    /api/tariff-items                     — list/search tariff items across schedules
GET    /api/tariff-items/{id}                — single item detail

POST   /api/tariff-schedules                 — create custom schedule (admin)
PUT    /api/tariff-schedules/{id}            — update custom schedule (admin)
POST   /api/tariff-schedules/{id}/clone      — clone schedule as custom (admin)
POST   /api/tariff-schedules/{id}/items      — add item to custom schedule (admin)
PUT    /api/tariff-items/{id}                — update item in custom schedule (admin)
DELETE /api/tariff-items/{id}                — delete item from custom schedule (admin)
```

**Authorization**: `VIEW_LEGAL` for reads, `MANAGE_LEGAL` for write operations.

### 3.6 Invoice Integration

Extend the invoice line item creation flow to support tariff items:

1. When creating an invoice for a matter (module-gated: only when `lssa_tariff` is enabled):
   - A **"Add Tariff Items"** button appears alongside the existing "Add Time Entries" and "Add Expense" options
   - Opens a tariff item selector: browse by schedule/section or search by description
   - Selected items create `InvoiceLine` entries with `tariff_item_id` set, quantity, and amount from the tariff

2. **InvoiceLine extension**:
   - Add `tariff_item_id` (UUID FK, nullable) column to `invoice_lines`
   - Add `line_source` (VARCHAR(20), nullable) column: `TIME_ENTRY`, `EXPENSE`, `TARIFF`, `MANUAL` — for display purposes
   - When `tariff_item_id` is set: description comes from the tariff item, amount from tariff x quantity
   - When `tariff_item_id` is null: existing behavior (hourly rate x hours, or manual entry)

3. **No changes to invoice totals logic** — the total is still `SUM(line_amount)`. Tariff items just provide a different source for the line amount.

### 3.7 Frontend — Tariff Management

**Tariff Schedules page** (`/legal/tariffs`): Module-gated behind `lssa_tariff`.

- List of tariff schedules. Columns: Name, Category, Court Level, Effective Period, Active, Source.
- Click a schedule to see items grouped by section. Searchable, filterable by section.
- "Clone as Custom" action on LSSA schedules — creates an editable copy with firm-specific adjustments.
- Create/edit custom schedule with items.

**Invoice creation extension**: When creating an invoice for a matter with `lssa_tariff` enabled, the invoice line item form includes:
- A "Tariff Item" tab alongside "Time Entry" and "Expense" tabs
- Tariff item selector with schedule filter, section filter, and text search
- Quantity input (default 1, adjustable for per-page/per-folio items)
- Amount auto-populated from tariff, overridable

---

## Section 4 — Legal Pack Content

### 4.1 Field Packs

**`legal-za-customer` field pack** (new):

| Field Slug | Label | Type | Notes |
|------------|-------|------|-------|
| `client_type` | Client Type | DROPDOWN | Options: Individual, Company, Trust, Close Corporation, Partnership, Estate, Government |
| `id_passport_number` | ID / Passport Number | TEXT | For natural persons |
| `registration_number` | Registration Number | TEXT | For companies, CCs, trusts |
| `physical_address` | Physical Address | TEXTAREA | Domicilium address for service |
| `postal_address` | Postal Address | TEXTAREA | |
| `preferred_correspondence` | Preferred Correspondence | DROPDOWN | Options: Email, Post, Hand Delivery |
| `referred_by` | Referred By | TEXT | Referral source |

**`legal-za-project` field pack** (new):

| Field Slug | Label | Type | Notes |
|------------|-------|------|-------|
| `matter_type` | Matter Type | DROPDOWN | Options: Litigation, Conveyancing, Commercial, Family Law, Estates, Labour, Criminal, Collections, Notarial |
| `case_number` | Case Number | TEXT | Court case number |
| `court_name` | Court | TEXT | Which court the matter is in |
| `opposing_party` | Opposing Party | TEXT | Primary opposing party name |
| `opposing_attorney` | Opposing Attorney | TEXT | Opposing attorney/firm name |
| `advocate_name` | Advocate | TEXT | Instructed advocate (if briefed) |
| `date_of_instruction` | Date of Instruction | DATE | When the firm was instructed |
| `estimated_value` | Estimated Value | NUMBER | Estimated value of the claim/matter |

### 4.2 Template Packs

**`legal-za` template pack** — Tiptap-format document templates:

| Template Slug | Name | Entity Type | Description |
|---------------|------|-------------|-------------|
| `engagement-letter-litigation` | Engagement Letter — Litigation | PROJECT | Standard litigation engagement letter |
| `engagement-letter-conveyancing` | Engagement Letter — Conveyancing | PROJECT | Conveyancing engagement letter |
| `engagement-letter-general` | Engagement Letter — General | PROJECT | General legal engagement letter |
| `power-of-attorney` | Power of Attorney | CUSTOMER | Standard power of attorney template |
| `notice-of-motion` | Notice of Motion | PROJECT | Court filing — notice of motion |
| `founding-affidavit` | Founding Affidavit | PROJECT | Court filing — affidavit template |
| `letter-of-demand` | Letter of Demand | CUSTOMER | Demand letter for collections |

Templates use the existing `{{entity.field}}` variable syntax, pulling from both standard entity fields and custom fields (e.g., `{{project.case_number}}`, `{{customer.id_passport_number}}`).

### 4.3 Clause Packs

**`legal-za` clause pack** — clauses attachable to document templates:

| Clause Slug | Name | Category | Description |
|-------------|------|----------|-------------|
| `mandate-scope` | Scope of Mandate | engagement | Defines the scope of the legal engagement |
| `fees-hourly` | Fees — Hourly Basis | fees | Fee clause for hourly billing arrangements |
| `fees-tariff` | Fees — Tariff Basis | fees | Fee clause referencing LSSA tariff |
| `fees-contingency` | Fees — Contingency | fees | Contingency fee arrangement (Contingency Fees Act 66 of 1997) |
| `trust-deposits` | Trust Account Deposits | trust | Requirement for deposits into trust account |
| `jurisdiction` | Jurisdiction & Domicilium | general | Governing law and chosen domicilium |
| `termination` | Termination of Mandate | general | How either party can terminate the engagement |
| `fica-consent` | FICA Consent | compliance | Client consent for FICA verification |
| `data-protection` | Data Protection (POPIA) | compliance | Data processing consent and obligations |
| `conflict-waiver` | Conflict of Interest Waiver | compliance | Template for conflict waiver acknowledgement |

### 4.4 Compliance Packs

**`legal-za` compliance pack** — checklist templates for client onboarding:

The legal FICA compliance checklist is similar to the accounting one (law firms are also "accountable institutions" under FICA) but includes additional legal-specific items:

| Item | Category | Description |
|------|----------|-------------|
| Proof of Identity | KYC | SA ID / passport certified copy |
| Proof of Address | KYC | Not older than 3 months |
| Company Registration Docs | KYC | CIPC registration certificate (companies/CCs) |
| Trust Deed | KYC | For trust clients |
| Beneficial Ownership Declaration | KYC | Per PCC 59 requirements |
| Source of Funds Declaration | CDD | Origin of funds for the transaction |
| Engagement Letter Signed | Onboarding | Signed by client |
| Conflict Check Performed | Onboarding | Documented conflict check completed |
| Power of Attorney Signed | Onboarding | If applicable to the matter type |
| FICA Risk Assessment | Risk | Client risk rating (low/medium/high) |
| Sanctions Screening | Risk | Screened against sanctions lists |

### 4.5 Automation Pack

**`legal-za` automation pack** — automation rule templates:

| Rule Name | Trigger | Condition | Action |
|-----------|---------|-----------|--------|
| Matter Onboarding Reminder | `CUSTOMER_STATUS_CHANGED` → ONBOARDING | Matter type is set | Send notification "Complete onboarding checklist" |
| Engagement Letter Follow-up | `DOCUMENT_SENT` | Template is engagement letter | Create task "Follow up on engagement letter" due in 7 days |

Note: Court date and prescription reminders are handled by the `CourtDateReminderJob` (Section 1.2), not by the automation engine. The automation engine's trigger types may not support "N days before a date on a custom entity." Only include rules here that existing trigger types support.

---

## Section 5 — Module Registration

### 5.1 Module Registry Updates

Register the following in `ModuleRegistry` (updating existing stubs where applicable):

```
court_calendar:
  id: "court_calendar"
  name: "Court Calendar"
  description: "Track court dates, hearings, prescription periods, and legal filing deadlines"
  defaultEnabledFor: ["legal-za"]
  navItems:
    - { path: "/legal/court-calendar", label: "Court Calendar", zone: "clients" }

conflict_check:
  id: "conflict_check"
  name: "Conflict Check"
  description: "Adverse party registry and conflict-of-interest checking"
  defaultEnabledFor: ["legal-za"]
  navItems:
    - { path: "/legal/conflict-check", label: "Conflict Check", zone: "clients" }
    - { path: "/legal/adverse-parties", label: "Adverse Parties", zone: "clients" }

lssa_tariff:
  id: "lssa_tariff"
  name: "LSSA Tariff"
  description: "Law Society tariff schedules for standard legal fees"
  defaultEnabledFor: ["legal-za"]
  navItems:
    - { path: "/legal/tariffs", label: "Tariff Schedules", zone: "finance" }
```

### 5.2 Capability Registration

Add two new capabilities to the RBAC system (Phase 41/46):

| Capability | Description | Default Roles |
|------------|-------------|---------------|
| `VIEW_LEGAL` | View legal module data (court dates, conflicts, tariffs) | Owner, Admin, Member |
| `MANAGE_LEGAL` | Create, edit, delete legal module data | Owner, Admin |

These capabilities are registered in the capability registry and available for custom role configuration.

### 5.3 Legal Profile Update

Update the `legal-za` profile definition in `VerticalProfileRegistry`:

```
LEGAL_ZA:
  vertical_profile: "legal-za"
  enabled_modules: ["court_calendar", "conflict_check", "lssa_tariff"]
  terminology_namespace: "en-ZA-legal"
  packs: ["legal-za"]  (field pack, template pack, compliance pack, clause pack, automation pack)
  defaults: { currency: "ZAR", taxRate: 15.0, taxLabel: "VAT" }
```

Note: `trust_accounting` is removed from `enabled_modules` until that module is actually built. The stub remains in the codebase but is not enabled by default.

---

## Section 6 — Multi-Vertical Coexistence Testing

This section is as important as the feature code. The whole point of this phase is proving multi-vertical works.

### 6.1 Integration Tests

| Test | What it proves |
|------|---------------|
| `MultiVerticalMigrationTest` | Flyway migrations succeed when both accounting and legal tables exist in the same schema |
| `MultiVerticalModuleGuardTest` | Accounting tenant cannot access `/api/court-dates` (403); legal tenant can. Legal tenant cannot access `/api/deadlines` (403); accounting tenant can. Generic tenant gets 403 on both. |
| `MultiVerticalPackSeederTest` | Provisioning an accounting tenant seeds accounting packs only. Provisioning a legal tenant seeds legal packs only. No cross-contamination. |
| `MultiVerticalSharedEndpointTest` | `/api/projects` returns projects for both accounting and legal tenants. `/api/customers` works for both. Shared endpoints are profile-agnostic. |
| `MultiVerticalInvoiceTest` | Accounting tenant creates time-based invoices normally. Legal tenant can create invoices with tariff line items. Accounting tenant's invoice creation does NOT show tariff options. |
| `MultiVerticalDashboardTest` | Accounting tenant's dashboard shows deadline widget, not court date widget. Legal tenant's dashboard shows court date widget, not deadline widget. |
| `MultiVerticalProfileSwitchTest` | Switching a tenant from generic to accounting enables accounting modules and disables legal modules. Switching to legal does the reverse. |

### 6.2 Frontend Coexistence Tests

| Test | What it proves |
|------|---------------|
| Sidebar rendering — accounting tenant | Shows "Deadlines" nav item, does NOT show "Court Calendar", "Conflict Check", "Adverse Parties", "Tariff Schedules" |
| Sidebar rendering — legal tenant | Shows "Court Calendar", "Conflict Check", "Adverse Parties", "Tariff Schedules", does NOT show "Deadlines" |
| Page routing — accounting tenant visits /legal/* | Shows 404 or module-disabled message, not a blank page or error |
| Page routing — legal tenant visits /deadlines | Shows 404 or module-disabled message |

---

## Section 7 — Flyway Migration

**Migration version**: Check the latest migration number in the codebase and use the next available. Expected to be in the V83+ range.

**New tables created by this migration:**
1. `court_dates` — court dates for legal matters
2. `prescription_trackers` — prescription period tracking
3. `adverse_parties` — adverse party registry
4. `adverse_party_links` — links between adverse parties and matters
5. `conflict_checks` — conflict check audit trail
6. `tariff_schedules` — LSSA tariff schedules
7. `tariff_items` — individual tariff line items

**Modified tables:**
1. `invoice_lines` — add `tariff_item_id` (UUID FK nullable), `line_source` (VARCHAR(20) nullable)

**Indexes:**
- `court_dates`: composite on `(project_id, scheduled_date)`, index on `customer_id`, index on `status`
- `prescription_trackers`: composite on `(project_id)`, index on `prescription_date` (for warnings query)
- `adverse_parties`: trigram GIN index on `name` (for fuzzy search), index on `id_number`, index on `registration_number`
- `adverse_party_links`: composite on `(adverse_party_id, project_id)` unique, index on `customer_id`
- `conflict_checks`: index on `checked_by`, index on `checked_at`, index on `customer_id`, index on `project_id`
- `tariff_schedules`: composite on `(category, court_level, is_active)`
- `tariff_items`: index on `schedule_id`, full-text search index on `description`

**pg_trgm extension**: The migration should include `CREATE EXTENSION IF NOT EXISTS pg_trgm;` for the fuzzy name matching in conflict checks. This is idempotent and safe to run in every schema.

---

## Section 8 — Out of Scope

- **Trust accounting** — remains a stub. Separate phase.
- **Court system integrations** — no CaseLines, no e-filing APIs
- **LSSA tariff auto-updates** — seed data only, manual updates
- **Legal aid rates** — separate from LSSA tariff, not included
- **Prescription restart calculation** — interrupted prescriptions are marked but restart logic is deferred
- **E-signature for conflict waivers** — uses existing document acceptance (Phase 28) if needed, no new e-signature infrastructure
- **Legal-specific reports** — deferred to a reporting extension phase
- **Matter numbering system** — firms use court case numbers; no internal matter numbering scheme needed (project IDs suffice)
- **Mobile app** — no mobile-specific considerations

---

## Section 9 — ADR Topics

The following ADR topics should be addressed during architecture:

1. **Court date vs. deadline architecture** — Should the court calendar reuse the `DeadlineTypeRegistry` + `DeadlineCalculationService` pattern from accounting, or use a separate entity model? The accounting pattern is calculation-based (dates derived from fiscal year-end), while court dates are event-based (dates set by external authority). Prescription dates are a hybrid (calculated, but with interruption). Document the decision.

2. **Conflict search strategy** — Trigram similarity vs. full-text search vs. exact match only. Performance implications at scale (10,000+ adverse parties). Whether to use PostgreSQL `pg_trgm` or application-level matching.

3. **Tariff rate integration** — How tariff rates interact with the existing `BillingRate` → `InvoiceLine` pipeline. Whether tariff items should be stored as a new entity or as a special type of `BillingRate`. The chosen approach (separate entity, optional FK on InvoiceLine) keeps the systems loosely coupled.

4. **Module capability mapping** — How the new `VIEW_LEGAL` / `MANAGE_LEGAL` capabilities interact with existing RBAC. Whether legal modules should use module-specific capabilities or reuse existing ones (e.g., `MANAGE_PROJECTS` for court dates on matters).

---

## Section 10 — Style & Boundaries

- Follow existing code conventions: Spring Boot 4 + Java 25, Hibernate 7, tenant-scoped entities with no `tenant_id` column (schema isolation handles it).
- Backend package structure: `verticals/legal/courtcalendar/`, `verticals/legal/conflictcheck/`, `verticals/legal/tariff/`. Replaces existing stubs.
- Frontend follows existing patterns: Shadcn UI components, Tailwind v4, App Router pages, `ModuleGate` wrapping, `useOrgProfile()` for conditional rendering.
- Pack content (field packs, template packs, etc.) follows existing JSON resource file patterns in `src/main/resources/packs/`.
- Tests follow existing patterns: MockMvc integration tests with `@WithMockUser`, `ScopedValue.where()` for tenant context.
- No new infrastructure dependencies. PostgreSQL `pg_trgm` is the only extension needed, and it's lightweight.
- Keep the LSSA tariff seed data focused — include the most commonly used items (consultations, pleadings, trial preparation, correspondence), not the exhaustive 100+ item schedule. Enough to demonstrate the system works. Firms can clone and extend.
