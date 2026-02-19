# Future: Retainer ↔ Recurring Schedule Integration

**Status**: Deferred (from Phase 17)
**Prerequisite**: Phase 16 (Project Templates & Recurring Schedules) must be implemented first.

---

## Context

Phase 17 (Retainer Agreements & Billing) originally specified an optional `schedule_id` FK on `RetainerAgreement` linking to `RecurringSchedule` from Phase 16. Since Phase 16 has not been implemented yet, this link was deferred to keep Phase 17 self-contained.

## What Was Deferred

### 1. RetainerAgreement.schedule_id

A nullable FK from `retainer_agreements` to `recurring_schedules`. When linked:

- Projects auto-created by the recurring schedule are automatically associated with the retainer for consumption tracking.
- The retainer dashboard shows which schedule feeds into the retainer.
- Schedule frequency and retainer frequency can be validated for alignment (e.g., a monthly retainer linked to a monthly schedule).

### 2. Schedule-scoped consumption (optional refinement)

The current Phase 17 implementation counts **all billable time entries for the customer's projects** toward retainer consumption. With schedule integration, an optional mode could restrict consumption to **only projects created by the linked schedule** (via `ScheduleExecution.project_id`).

This is a refinement, not a requirement — the "all customer projects" default matches how most firms think about retainers. Schedule-scoped consumption would serve firms that have multiple project streams for a single customer and want to isolate retainer hours to one stream.

### 3. Retainer create/edit UI — schedule selector

The retainer creation dialog would include an optional dropdown: "Link to recurring schedule" showing schedules for the selected customer. This was omitted from Phase 17's UI since no schedules exist yet.

## Implementation When Ready

1. **Migration**: `ALTER TABLE retainer_agreements ADD COLUMN schedule_id UUID REFERENCES recurring_schedules(id);`
2. **Entity**: Add `scheduleId` field to `RetainerAgreement` (nullable UUID, `@ManyToOne` lazy).
3. **Service**: Update `RetainerAgreementService.create/update` to validate schedule belongs to the same customer.
4. **Consumption** (optional): Add a flag or config to `RetainerAgreement` — `consumptionScope` enum (`ALL_CUSTOMER_PROJECTS`, `SCHEDULE_PROJECTS_ONLY`). Default: `ALL_CUSTOMER_PROJECTS`.
5. **Frontend**: Add schedule selector to retainer create/edit dialog. Show linked schedule name on retainer detail.

## Estimated Effort

1 slice (backend migration + entity change + service validation + frontend dropdown). If schedule-scoped consumption is included, add half a slice for the query filtering logic and tests.

## Dependencies

- Phase 16 entities: `RecurringSchedule`, `ScheduleExecution` (with `project_id` FK)
- Phase 17 entities: `RetainerAgreement`, `RetainerPeriod` (must exist first)
