# Time Entry

**Bounded context:** see [`10-bounded-contexts.md` § time-entry](../10-bounded-contexts.md).

## Purpose

Time recording with point-in-time billing/cost rate snapshots. This is the seam from work-done to money: every billable hour acquires its dollar value here at creation, and the resulting `TimeEntry` row is the unit `invoicing` consumes when an invoice line is created (`lineSource = TIME_ENTRY`, `timeEntryId` FK). Without this module, `tasks` and `invoicing` cannot connect — A1 explicitly split it out of "Tasks & Time" because bundling hid the seam `→ ../10-bounded-contexts.md:63`.

The aggregate is intentionally narrow: one entity (`TimeEntry`), one event (`TimeEntryChangedEvent`), no internal state machine beyond `billable` toggle and `invoiceId` linkage. Rate **cards** (`BillingRate`, `CostRate`) are **not** owned here — see Open Questions for the ownership trail.

## Entities owned

- `TimeEntry` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java:17` — table `time_entries`. Columns:
  - `taskId` (NOT NULL FK→tasks) — every entry attributes to a task; ADR-021 rejected the project-only and hybrid options.
  - `memberId` (NOT NULL FK→members), `date` (LocalDate), `durationMinutes` (int), `description` (TEXT).
  - `billable` (boolean, NOT NULL) — distinct from "billed". Toggleable until invoiced; see `BillingStatus` enum below.
  - `billingRateSnapshot` (BigDecimal 12,2), `billingRateCurrency` (char(3)) — frozen at creation by `RateSnapshotService`; ADR-040.
  - `costRateSnapshot` (BigDecimal 12,2), `costRateCurrency` (char(3)) — frozen at creation; survives even on non-billable entries (cost is recorded regardless of bill).
  - `invoiceId` (nullable FK→invoices) — set when consumed by `InvoiceCreationService.markBilled` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCreationService.java:912`; cleared on invoice void/delete `→ InvoiceCreationService.java:400, :639`.
  - `rateCents` (`@Deprecated`) — Phase 5 manual-rate field, retained for backward compatibility; superseded by snapshot fields per ADR-039 / ADR-040.
- `BillingStatus` enum `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/BillingStatus.java:14` — `UNBILLED, BILLED, NON_BILLABLE`. Computed from `(billable, invoiceId)` and used as a query filter, not a column. **One of three coexisting `BillingStatus` enums** (see Open Questions).
- Computed values on the entity (no column storage): `getBillableValue()` and `getCostValue()` `→ TimeEntry.java:175, :189` — `(durationMinutes / 60) * snapshot`, half-up to scale 2. Returns null when snapshot or `billable` is missing.

`BillingRate` and `CostRate` are **not** owned by this module. They live in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRate.java` and `.../costrate/CostRate.java` as their own packages — see Open Questions §1.

## REST surface

`TimeEntryController` and `AdminTimeEntryController` together expose ~10 endpoints across two URL families. The path family is mixed: writes are nested under `/api/tasks/{taskId}/time-entries` (creation requires a task), updates/deletes are flat under `/api/time-entries/{id}`, and project-level ops are nested under `/api/projects/{projectId}/time-entries`. This shape is documented in A1 `→ ../_discovery/A1-backend-map.md:394`.

| Verb + path | Anchor | Notes |
|---|---|---|
| `POST /api/tasks/{taskId}/time-entries` | `→ TimeEntryController.java:39` | Creates one entry; `RateSnapshotService` resolves and freezes both snapshots in the same transaction. Response carries `rateWarning` when no rate was resolvable (see ADR-040). |
| `GET /api/tasks/{taskId}/time-entries` | `→ TimeEntryController.java:60` | List by task; filters `billable` (boolean) + `billingStatus` (`UNBILLED`/`BILLED`/`NON_BILLABLE`). |
| `PUT /api/time-entries/{id}` | `→ TimeEntryController.java:88` | Edit duration/date/billable/description. Date change triggers `RateSnapshotService.reSnapshotOnDateChange` `→ TimeEntryService.java:286`. |
| `PATCH /api/projects/{projectId}/time-entries/{id}/billable` | `→ TimeEntryController.java:75` | Toggle `billable` flag; project-scoped path so capability check resolves project access. |
| `POST /api/time-entries/batch` | `→ TimeEntryController.java:109` | **Bulk weekly grid** ingest. Accepts 1–50 entries (`@Size(min=1, max=50)`); per-entry success/error map; partial-success semantics (returns `BatchTimeEntryResult{created, errors, totalCreated, totalErrors}`). Each item still fires `TimeEntryChangedEvent` on success `→ TimeEntryBatchService.java:151`. |
| `DELETE /api/time-entries/{id}` | `→ TimeEntryController.java:117` | Hard delete; emits `TimeEntryChangedEvent action=DELETED`. |
| `POST /api/admin/time-entries/re-snapshot` | `→ AdminTimeEntryController.java:24` | Admin-only (`@RequiresCapability("TEAM_OVERSIGHT")`) bulk re-snapshot for rate corrections; requires at least one of `projectId/memberId/fromDate/toDate`. The escape hatch ADR-040 mandates. Skips invoiced entries. |
| `GET /api/projects/{id}/time-summary` | `→ ProjectTimeSummaryController.java:23` | On-the-fly aggregation per ADR-022 (no materialised summaries). |
| `GET /api/projects/{id}/time-summary/by-member` | `→ ProjectTimeSummaryController.java:34` | Same. |
| `GET /api/projects/{id}/time-summary/by-task` | `→ ProjectTimeSummaryController.java:46` | Same. |

The bounded-contexts entry's "`/api/projects/{projectId}/time/*`" wording `→ ../10-bounded-contexts.md:141` is approximate; the real prefixes are `/api/projects/{id}/time-summary` and `/api/projects/{projectId}/time-entries/{id}/billable`.

Cross-project read: there is no `/api/time-entries` list endpoint. `my-work` queries via the cross-project pattern in ADR-023 — JPQL filters by `memberId` directly through `TimeEntryRepository`, with membership-implied authorisation rather than a `ProjectAccessService` round-trip.

## Frontend pages / components

Time-tracking UI is embedded across three surfaces, all consuming the unified API client:

- **Project detail tab** — `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` carries the time tab `→ ../_discovery/A2-frontend-map.md:114-115`. Entry list + log dialog live in `frontend/components/tasks/time-entry-list.tsx`, `log-time-dialog.tsx`, `edit-time-entry-dialog.tsx`, `delete-time-entry-dialog.tsx`.
- **Weekly grid** — `frontend/components/time-tracking/weekly-time-grid.tsx` plus `time-cell.tsx`. The grid is the primary consumer of `POST /api/time-entries/batch`. `frontend/components/time-tracking/csv-import-dialog.tsx` is a parallel ingest path that targets the same batch endpoint.
- **My Work** — `frontend/app/(app)/org/[slug]/my-work/page.tsx` `→ ../_discovery/A2-frontend-map.md:101-102` shows assigned tasks + member time entries cross-project. Subroute `my-work/timesheet/page.tsx` hosts the personal timesheet view (server actions in `my-work/timesheet/actions.ts`). Headers + weekly rhythm strip in `my-work-header.tsx`, `weekly-rhythm-strip-client.tsx`.
- **Settings — time tracking** — `frontend/app/(app)/org/[slug]/settings/time-tracking/page.tsx` `→ ../_discovery/A2-frontend-map.md:210-211` (time reminder day-of-week, default expense markup). Form component: `frontend/components/settings/time-tracking-settings-form.tsx`. Persists to `OrgSettings` via `PATCH /api/settings/time-reminder` `→ ../_discovery/A1-backend-map.md:402`.
- **Settings — rates** — `frontend/app/(app)/org/[slug]/settings/rates/page.tsx` `→ ../_discovery/A2-frontend-map.md:206-207` (billing rates, cost rates, default currency). The page lives under `/settings/` but the rate-card entities themselves do not belong to `settings-navigation` — see Open Questions §1.
- **Status badges** — `frontend/components/time-entries/billing-status-badge.tsx` (`UNBILLED`/`BILLED`/`NON_BILLABLE`), `retainer-indicator.tsx` (signals when a project's time draws against an active retainer period — `retainers` module dependency).

## Domain events

Single emitted event:

- `TimeEntryChangedEvent` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TimeEntryChangedEvent.java:7` — fields `(eventType, entityType, entityId, projectId, action, actorMemberId, actorName, tenantId, orgId, occurredAt, details)`. The `action` discriminator is a string: `CREATED | UPDATED | DELETED` — published from `TimeEntryService.publishTimeEntryChangedEvent` `→ TimeEntryService.java:589` at five sites (create, update, date-change re-snapshot, batch-update wrapper, billable toggle, delete) and from `TimeEntryBatchService.java:151` for each batched entry. A1 lists exactly one publisher in this package `→ ../_discovery/A1-backend-map.md:464`.

No `@EventListener` lives in `timeentry/` — this module is a pure publisher. Listeners that care:
- `audit/AuditService` (in-transaction audit-trail emission, not event-driven — every mutation calls `auditService.log(...)` directly; the event is a separate notification path).
- `portal` read-model listeners pick up time changes for portal-side counters where applicable `→ ../_discovery/A1-backend-map.md:476` (general pattern; specific consumption to be confirmed in `customer-portal.md`).
- Notifications: there is **no** direct `TimeEntryChangedEvent` listener in `notification/NotificationService` `→ ../_discovery/A1-backend-map.md:475`. The weekly time-logging reminder is scheduler-driven, not event-driven (see below).

## Cross-cutting touchpoints

### Capability gates

All write endpoints are capability-gated. The admin re-snapshot endpoint requires `TEAM_OVERSIGHT` `→ AdminTimeEntryController.java:25`. Read summaries are member-accessible. Cross-project access on `my-work` follows ADR-023 — the `WHERE member_id = :memberId` clause is its own authorisation boundary; no `ProjectAccessService` call is made.

### Audit

Every create/update/delete and the billable toggle writes an `AuditEvent` row in the same transaction as the `TimeEntry` mutation. Audit is in-transaction (not via the event bus) so a rollback cannot leave a stale audit row — see the audit-coupling note in `10-bounded-contexts.md:451`.

### Point-in-time rate snapshot (ADR-040)

`RateSnapshotService` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/RateSnapshotService.java` runs the resolution hierarchy from ADR-039 (project override → customer override via `CustomerProject` first row → member default) and writes both `(billingRateSnapshot, billingRateCurrency)` and `(costRateSnapshot, costRateCurrency)` onto the `TimeEntry` at create time. After creation those fields are immutable except via:
1. **Date change** — `reSnapshotOnDateChange` re-resolves against the new effective date `→ TimeEntryService.java:286`.
2. **Admin re-snapshot** — bulk correction for rate-table fixes `→ AdminTimeEntryController.java:24`. Always skips entries with non-null `invoiceId` (already invoiced; immutable financial record).

A `null` snapshot is permitted (no rate resolvable for that member/project/date); the API surfaces a `rateWarning` string on the response so the UI can prompt to set a rate. `getBillableValue()` returns `null` rather than zero in this case to keep "missing rate" distinguishable from "zero-rate".

### Invoice linking

Setting `invoiceId` is owned by `invoicing`, not by this module. `InvoiceCreationService.markBilled` writes `timeEntry.setInvoiceId(invoice.getId())` `→ InvoiceCreationService.java:912`; void/delete clears it back to null `→ InvoiceCreationService.java:400, :639`. Once `invoiceId` is non-null, the entry is `BILLED` for the `BillingStatus` filter and is skipped by the admin re-snapshot job.

### Schedulers (not owned, but adjacent)

`TimeReminderScheduler` `→ ../_discovery/A1-backend-map.md:496` lives in `notification/` (not here). It runs weekly per `OrgSettings.timeReminderDay`, queries `TimeEntryRepository` for missing daily entries `→ TimeEntryRepository.java:474`, and emits notifications. Module ownership: scheduler in `notifications`, query in this module's repository.

## Vertical specifics

The `TimeEntry` entity, snapshot fields, and event are identical across all vertical profiles. Verticality enters at the **terminology** layer only — frontend labels swap via `lib/terminology-map.ts`:

| Profile | Label |
|---|---|
| `legal-za` | Time Recording |
| `accounting-za` | Time Entry |
| `consulting-za` | Time Log |
| `consulting-generic` | Time Entry |

Glossary anchors `→ ../glossary.md:268, :320`.

No vertical-specific module-gates around time-entry (it's core for every profile). The legal vertical's tariff lines on invoices (`InvoiceLineType.TARIFF`) `→ ../10-bounded-contexts.md:189` are a sibling line-source, not a tariff representation of time — time entries do not become tariff lines.

## Active ADRs

- **ADR-021** — Time Tracking Model: time attached to tasks (NOT NULL `taskId`), date + duration (not start/stop timer). Foundational shape.
- **ADR-022** — Time Aggregation Strategy: on-the-fly SQL aggregation, no materialised summary tables. Why all `time-summary` endpoints are GROUP BY queries against `time_entries` rather than reads from a denormalised cache.
- **ADR-023** — My Work Cross-Project Query Pattern: direct JPQL queries scoped by `memberId` / `project_members` membership; the query *is* the authorisation. Why there's no global `/api/time-entries` list.
- **ADR-039** — Rate Resolution Hierarchy: three-level cascade (project override → customer override → member default). The first-customer-by-`CustomerProject.createdAt` simplification for multi-customer projects.
- **ADR-040** — Point-in-Time Rate Snapshotting: snapshots frozen at creation; re-snapshot on date change and via the admin endpoint; null permitted with warning. Reason invoices and historical reports are auditable.

## Key flows

This module is the central act of `50-flows/matter-to-cash.md` (to be written): task created → member logs time (snapshot frozen) → unbilled time accumulates → `InvoiceCreationService.markBilled` consumes entries into invoice lines → invoice approved/sent/paid. The seam is the `TimeEntry → InvoiceLine` join via `lineSource = TIME_ENTRY`, `timeEntryId` `→ ../_discovery/A1-backend-map.md:206`.

## Open questions / known fragility

1. **Rate-card ownership is unassigned.** `BillingRate` (`backend/.../billingrate/BillingRate.java`) and `CostRate` (`backend/.../costrate/CostRate.java`) live in their own top-level packages but are not surfaced as owned entities in any module page — `settings-navigation.md` claims only `OrgSettings`, and `invoicing.md` is a stub. The bounded-contexts dependency line says "rate snapshots come from rate cards in settings" `→ ../10-bounded-contexts.md:142`, but the rate-card entities are not part of the `settings/` Java package. The `/settings/rates` URL is a **frontend co-location**, not a backend ownership claim. Resolution: Phase C must create either a `30-modules/billing-rates.md` (cleanest) or attach rate cards to `invoicing.md` (matches the URL family `/api/billing-rates` if it sits under invoicing) or `settings-navigation.md`. Picking arbitrarily would entrench a wrong choice.
2. **Three coexisting `BillingStatus` enums.** `timeentry/BillingStatus.java:13` (`UNBILLED, BILLED, NON_BILLABLE`), `expense/ExpenseBillingStatus.java:4`, `verticals/legal/disbursement/DisbursementBillingStatus.java:9` `→ ../glossary.md:62, :106, :124, :332`. They share a name and a conceptual meaning but diverge in values and are not interchangeable. Any reporting layer that aggregates "unbilled work" must enumerate all three. Consolidation candidate, but each enum lives in a different aggregate so the merge isn't free.
3. **Bulk weekly endpoint scaling cap.** `POST /api/time-entries/batch` is hard-capped at 50 entries per request `→ TimeEntryController.java:153`. A 7-day × 10-task grid for a single member is already 70 cells; the UI must split into multiple round-trips. Each entry runs full rate resolution + audit + event publish, so even within the 50-cap there is no batch optimisation — N inserts, N audits, N events. For org-wide week-end timesheet rushes this is a write-amplification hot-spot worth monitoring.
4. **`rateCents` deprecated field still present.** `TimeEntry.rateCents` is `@Deprecated` `→ TimeEntry.java:40` but still accepted on `CreateTimeEntryRequest` and `UpdateTimeEntryRequest` `→ TimeEntryController.java:134, :141`. It is shadowed by the snapshot fields for any entry created post-Phase-8 but never removed; old rows pre-migration may still carry it. Cleanup gated on a data-fill migration.
5. **Date-change re-snapshot can change historical money silently.** When a member edits the date on an existing entry, `reSnapshotOnDateChange` re-resolves rates `→ TimeEntryService.java:286`. If rates changed between the old and new dates, the `billableValue` shifts. ADR-040 accepts this trade — date corrections are real corrections. Worth flagging because the change is silent (no `rateWarning` shown for a mere shift, only for an unresolvable rate) and the row's `invoiceId` does not have to be null for the edit path to be reachable in non-billed states. Confirm guard rails on edits to invoiced entries.
6. **No global list endpoint.** Operational queries ("all time entries this week across the org") have no canonical REST path. `my-work` is member-scoped; project summaries are project-scoped; admin re-snapshot is a write. Reporting reads `TimeEntryRepository` directly. Documenting as a deliberate ADR-023 consequence rather than a gap.
