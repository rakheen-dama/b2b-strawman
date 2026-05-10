# Retainers

**Status:** filled — Phase C.
**Bounded context:** see [`10-bounded-contexts.md` § retainers](../10-bounded-contexts.md).
**Glossary:** Retainer, RetainerAgreement, RetainerPeriod, Mandate, Hour Bank, Period Close — see [`../glossary.md`](../glossary.md).

## 1. Purpose

Retainer billing for periodic, pre-paid engagements. Two style axes: (a) **fee shape** — `HOUR_BANK` (allocated hours, possibly with overage) or `FIXED_FEE` (period fee only) `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerType.java:4`; (b) **frequency** — `MONTHLY | QUARTERLY | ANNUALLY` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerFrequency.java:8`. Each agreement spawns a series of periods; each period tracks consumption (query-based, derived from time entries — ADR-074), gets closed by an admin at period end (ADR-072), and emits a draft invoice with a base-fee line plus optional overage line at the standard billing rate (ADR-073). At most one active-or-paused retainer exists per customer (ADR-075). UI label is "Mandate" in legal-za (terminology overlay only — same backend) per `frontend/lib/terminology-map.ts:79`.

## 2. Entities

### `RetainerAgreement`  `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreement.java:18`

Recurring agreement. Fields: `id, customerId, scheduleId, name, type, status, frequency, startDate, endDate, allocatedHours, periodFee, rolloverPolicy, rolloverCapHours, notes, createdBy, createdAt, updatedAt` (`:20-74`). Status machine `ACTIVE → PAUSED ⇄ ACTIVE → TERMINATED` (`:108-130`); statuses defined `→ retainer/RetainerStatus.java:4`. `pause()` requires ACTIVE; `resume()` requires PAUSED; `terminate()` is one-way. `updateTerms()` mutates name, hours, fee, rollover, end date, notes (`:132-148`). `rolloverPolicy` is one of `FORFEIT | CARRY_FORWARD | CARRY_FORWARD_CAPPED` `→ retainer/RolloverPolicy.java:4` and defaults to `FORFEIT` when null at construction (`:100`).

### `RetainerPeriod`  `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriod.java:18`

Per-period instance. Fields: `id, agreementId, periodStart, periodEnd, status, allocatedHours, baseAllocatedHours, rolloverHoursIn, consumedHours, overageHours, remainingHours, rolloverHoursOut, invoiceId, closedAt, closedBy, createdAt, updatedAt` (`:22-71`). Status `OPEN | CLOSED | INVOICED` `→ retainer/PeriodStatus.java:4` (note: closer code only sets `CLOSED` on close — `:124`; `INVOICED` is reserved). `updateConsumption(newConsumedHours)` recomputes `remainingHours = MAX(0, allocatedHours − consumedHours)` for HOUR_BANK; for FIXED_FEE (`allocatedHours == null`) it just stores consumed hours and leaves remaining at 0 (`:103-113`). `close(invoiceId, closedBy, overageHours, rolloverHoursOut)` flips status to CLOSED and records close metadata (`:119-131`). Construction seeds `consumedHours = 0`, `rolloverHoursIn` from previous period, `remainingHours = allocatedHours` for HOUR_BANK and 0 for FIXED_FEE (`:75-96`).

## 3. REST surface — `/api/retainers/*` (12 endpoints)

`RetainerAgreementController` `→ retainer/RetainerAgreementController.java:24`:

| Verb + Path | Method | Capability | Notes |
|---|---|---|---|
| `GET /api/retainers` | `listRetainers` | `FINANCIAL_VISIBILITY` | Optional `status`, `customerId` filters; side-effect: triggers `checkAndNotifyReadyToClose` (`:42`). |
| `GET /api/retainers/{id}` | `getRetainer` | (authenticated) | `:46-49`. |
| `POST /api/retainers` | `createRetainer` | `FINANCIAL_VISIBILITY` | `:51-58`. |
| `PUT /api/retainers/{id}` | `updateRetainer` | `FINANCIAL_VISIBILITY` | `:60-66`. |
| `POST /api/retainers/{id}/pause` | `pauseRetainer` | `FINANCIAL_VISIBILITY` | `:68-73`. |
| `POST /api/retainers/{id}/resume` | `resumeRetainer` | `FINANCIAL_VISIBILITY` | `:75-80`. |
| `POST /api/retainers/{id}/terminate` | `terminateRetainer` | `FINANCIAL_VISIBILITY` | `:82-87`. |

`RetainerPeriodController` `→ retainer/RetainerPeriodController.java:21`:

| Verb + Path | Method | Capability | Notes |
|---|---|---|---|
| `GET /api/retainers/{id}/periods` | `listPeriods` | (authenticated) | Paginated; resolves member display names (`:29-36`). |
| `GET /api/retainers/{id}/periods/current` | `getCurrentPeriod` | (authenticated) | `:38-43`. |
| `POST /api/retainers/{id}/periods/current/close` | `closePeriod` | `FINANCIAL_VISIBILITY` | Admin-triggered (ADR-072); generates invoice + opens next period or auto-terminates. `:45-51`. |

`RetainerSummaryController` (mounted under `/api/customers`) `→ retainer/RetainerSummaryController.java:14`:

| Verb + Path | Method | Capability | Notes |
|---|---|---|---|
| `GET /api/customers/{customerId}/retainer-summary` | `getRetainerSummary` | (authenticated) | Customer-tab summary (`:22-25`). |

Total: 11 controller methods across three controllers + 1 listener-driven side effect on list. Counted as the "~12 endpoints" referenced in `_discovery/A1-backend-map.md`. Note: `RetainerAgreementController` predates the thin-controller rule (logged as a known violation in `backend/CLAUDE.md` TD-009) — the `listRetainers` method has a side-effect on a GET (`checkAndNotifyReadyToClose`) and `RetainerPeriodController.listPeriods` orchestrates two service calls.

## 4. Frontend

### Staff app (`frontend/`)

- `app/(app)/org/[slug]/retainers/page.tsx` — list + summary cards. Capability gate: requires `INVOICING` capability OR admin/owner; otherwise 404 (`:41-43`). Computes `activeCount`, `readyToCloseCount`, `totalOverageHours` from current-period flags (`:14-28`).
- `app/(app)/org/[slug]/retainers/actions.ts` — server actions.
- `app/(app)/org/[slug]/retainers/[id]/page.tsx` — agreement detail, period history, close-period dialog.
- `app/(app)/org/[slug]/retainers/[id]/actions.ts` — server actions.
- Components under `components/retainers/`: `retainer-list.tsx`, `period-history-table.tsx`, `retainer-status-badge.tsx`, `edit-retainer-dialog.tsx`, `retainer-detail-actions.tsx`, `create-retainer-dialog.tsx`, `close-period-dialog.tsx`, `retainer-summary-cards.tsx`.
- Shared: `lib/api/retainers.ts`, `lib/retainer-constants.ts`.
- Embedded views: `components/customers/customer-retainer-tab.tsx` (customer detail tab; calls `/api/customers/{id}/retainer-summary`); `components/time-entries/retainer-indicator.tsx`; `components/billing-runs/cherry-pick-retainer-section.tsx`.

### Customer portal (`portal/`)

- `app/(authenticated)/retainer/page.tsx` — index of retainers visible to portal contact. Client component; performs an additional client-side module gate (`enabledModules.includes("retainer_agreements")` → redirect to `/home`); backend remains source of truth (`:14-40`).
- `app/(authenticated)/retainer/[id]/page.tsx` — single-retainer view.
- Components under `portal/components/retainer/`: `hour-bank-card.tsx` (the "Hour Bank" UI per glossary `:140`), `consumption-list.tsx`.
- API client: `portal/lib/api/retainer.ts`.

## 5. Domain events

Three direct events, all in `retainer/event/`:

- `RetainerAgreementCreatedEvent` — published on `createRetainer` (`RetainerAgreementService.java:156-157`).
- `RetainerAgreementUpdatedEvent` — published on `updateRetainer`, `pauseRetainer`, `resumeRetainer`, `terminateRetainer` (`RetainerAgreementService.java:238, 282, 331, 387`).
- `RetainerPeriodRolloverEvent` — published when a period close opens a successor period; primary subscriber is the portal read-model (Epic 496A) (`RetainerPeriodService.java:351-358`).

The 10-bounded-contexts entry incorrectly states "no events of its own" — the truth is three direct events. Period-close also produces an `Invoice` aggregate via direct repository writes (not via `InvoiceService` — see ADR rationale comment at `RetainerPeriodService.java:216`); downstream invoice-lifecycle events come from `invoicing` (see [`invoicing.md`](invoicing.md)).

## 6. Cross-cutting touchpoints

- **Capability gates.** Mutations require `FINANCIAL_VISIBILITY` (controller `@RequiresCapability` annotations, `RetainerAgreementController.java:37,52,61,69,76,83`; period close at `RetainerPeriodController.java:46`). Frontend list page additionally accepts `INVOICING` or admin/owner (`retainers/page.tsx:41-43`).
- **Audit on lifecycle.** `AuditService` is invoked on every mutation: 6 sites in `RetainerAgreementService.java` (`:126, 142, 222, 268, 317, 366`) and 3 in `RetainerPeriodService.java` (`:315, 323, 336`) emitting `retainer.period.closed`, `retainer.invoice.generated`, `retainer.period.opened`.
- **Period close is admin-triggered (ADR-072).** No scheduled auto-close; `listRetainers` calls `checkAndNotifyReadyToClose` on every list to surface "ready to close" notifications (`RetainerAgreementController.java:42`).
- **Consumption is query-based (ADR-074).** `RetainerConsumptionListener` listens to `TimeEntryChangedEvent` (`:43`), re-runs `sumConsumedMinutes(customerId, periodStart, periodEnd)` (`:87-88`), and persists the new total via `period.updateConsumption()`. Failures are caught and logged (self-healing — the next time-entry change re-runs the query and corrects state, `:46-55`). Threshold notifications fire on 80% and 100% crossings (HOUR_BANK only, `:111-178`).
- **One-active-per-customer (ADR-075).** Enforced at `RetainerAgreementRepository.findActiveOrPausedByCustomerId` and at create/resume sites in `RetainerAgreementService`.
- **Module gating (`retainer_agreements`).** Defined in `VerticalModuleRegistry.java:182-192` with category `VERTICAL`, available to `["legal-za", "consulting-za"]`, single nav item `("/retainers", "Retainers", "finance")`. Note: backend service methods do **not** appear to call `verticalModuleGuard.requireModule("retainer_agreements")` directly — the gate is delivered via nav/page absence and capability checks. Portal index page does its own client-side check before calling the API (`retainer/page.tsx:14, 38-40`). See open question #4 below.
- **Invoice generation.** `RetainerPeriodService.closePeriod` writes an `Invoice` + lines directly via repositories (not via `InvoiceService`, because the invoice service requires `RequestScopes` — see comment at `:216`). Lines are tagged `InvoiceLineType.RETAINER` and carry `retainerPeriodId` (`:235, 249`). Default tax is applied via `applyDefaultTaxToRetainerLines` (`:254`). Overage lines are billed at the resolved standard hourly rate (ADR-073) and only added when `overageHours > 0` and a rate resolved (`:239-251`).
- **Auto-termination on closing the last period.** When `closePeriod` runs and the next period would start on/after `agreement.endDate`, the agreement is auto-terminated (idempotent via try/catch on `IllegalStateException`) and an admin notification is sent (`RetainerPeriodService.java:287-301`).

## 7. Vertical specifics

- **Terminology overlay.** `Retainer → Mandate` for legal-za only. UI-only — backend table remains `retainer_agreements`. Glossary `:167, 309`; mapping at `frontend/lib/terminology-map.ts:79`.
- **Module visibility.** `retainer_agreements` is a `VERTICAL` module enabled for `legal-za` and `consulting-za` profiles (`VerticalModuleRegistry.java:182-192`). The `consulting-za` profile description explicitly mentions retainer/SOW content (`backend/src/main/resources/vertical-profiles/consulting-za.json:4`). Non-listed profiles (e.g., `accounting-za`, `consulting-generic`) have no `/retainers` nav item and the portal client-redirects.
- **Portal member-name display.** `OrgSettings.PortalRetainerMemberDisplay` controls whether portal exposes member names on consumption (per glossary `:208` → `backend/.../settings/PortalRetainerMemberDisplay.java:8`).
- **Vertical detail.** See [`../60-verticals/legal-za.md`](../60-verticals/legal-za.md), [`../60-verticals/consulting-za.md`](../60-verticals/consulting-za.md).

## 8. Active ADRs

- [ADR-072 — admin-triggered period close](../adr/ADR-072-admin-triggered-period-close.md): explicit human action over scheduled auto-close; accommodates late time entries and matches accounting-firm month-end practice.
- [ADR-073 — standard billing rate for overage](../adr/ADR-073-standard-billing-rate-for-overage.md): overage is billed at the org/customer/project standard hourly rate, not at a retainer-specific rate.
- [ADR-074 — query-based consumption](../adr/ADR-074-query-based-consumption.md): `consumedHours` is recomputed from time entries on every change, not stored as an event-sourced counter; failures are self-healing.
- [ADR-075 — one active retainer per customer](../adr/ADR-075-one-active-retainer-per-customer.md): exactly one ACTIVE-or-PAUSED retainer per customer; eliminates allocation logic in time-entry consumption.

## 9. Key flows

- **Matter-to-cash** (retainer-billed engagement): customer → project → time entries → consumption listener updates current period → admin closes period → draft invoice generated → invoice flows through `invoicing` lifecycle. See [`../50-flows/matter-to-cash.md`](../50-flows/matter-to-cash.md).
- **Hour-bank threshold notifications**: `RetainerConsumptionListener.checkThresholds` at 80%/100% (`RetainerConsumptionListener.java:111-178`).
- **Period rollover**: `closePeriod` → `period.close(...)` → open next period with `rolloverHoursIn = rolloverHoursOut` and `allocated = baseAllocated + rolloverHoursOut` (HOUR_BANK only) → emit `RetainerPeriodRolloverEvent` (`RetainerPeriodService.java:264-358`).

## 10. Open questions / known fragility

1. **Rollover policy semantics — partially implicit.** `RolloverPolicy` is `FORFEIT | CARRY_FORWARD | CARRY_FORWARD_CAPPED` `→ retainer/RolloverPolicy.java:3` with `rolloverCapHours` for the capped variant. The actual computation of `rolloverHoursOut` from policy + `remainingHours` + `rolloverCapHours` lives inside `RetainerPeriodService.closePeriod` and is not surfaced via a unit-named helper. Worth a docs pass to confirm: does FORFEIT zero out `rolloverHoursOut`? Does CARRY_FORWARD_CAPPED clamp to `rolloverCapHours` even if `remainingHours` exceeds it? — verify and codify.
2. **Overage rate resolution chain — under-documented.** ADR-073 fixes the policy ("standard billing rate") but the resolver order (org → customer → project → member?) is in code (`RetainerPeriodService` `resolveBillingRate` neighbourhood) and not documented here. Capture in `30-modules/invoicing.md` rate-card section and back-link.
3. **Period close timing — what if it's missed?** ADR-072 explicitly accepts "periods stay open indefinitely" as a trade. The system surfaces `readyToClose` via list endpoint side-effect (`checkAndNotifyReadyToClose`) and dashboard counter. There is no SLA, no auto-close fallback, no escalation. If a firm forgets for two months, the consumption listener keeps appending hours into the open period and a single close will produce one invoice covering both periods. Confirm behaviour and document — possibly call out as a Phase 71 follow-up.
4. **`PeriodStatus.INVOICED` is defined but never set.** `PeriodStatus.java:3` lists `OPEN, CLOSED, INVOICED` (per glossary `:201`), but `RetainerPeriod.close(...)` only sets `CLOSED` (`RetainerPeriod.java:124`). Either dead code (drop the value) or an unwired transition that was meant to fire on invoice payment — clarify intent.
5. **Module gate not enforced at backend service entry.** Unlike `trust-accounting`, retainer services do not call `verticalModuleGuard.requireModule("retainer_agreements")` on every method. Defense relies on (a) the `/retainers` nav being absent and (b) the portal client-side redirect. A direct API call from a non-enabled tenant would still succeed if the tenant has `FINANCIAL_VISIBILITY`. Tracked as a defense-in-depth gap; align with the verticalisation-seam patterns in [`../20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md).
6. **Controller violations of thin-controller rule.** `RetainerAgreementController.listRetainers` mutates state on a GET (notification trigger) and `RetainerPeriodController.listPeriods` orchestrates two service calls + maps results. Tracked under `documentation/tech-debt.md` TD-009 (per `backend/CLAUDE.md` "Known violations").
7. **Bounded-contexts entry inconsistency.** `10-bounded-contexts.md:199` says "no events of its own; uses invoice events." This page corrects to: three direct events (`RetainerAgreementCreatedEvent`, `RetainerAgreementUpdatedEvent`, `RetainerPeriodRolloverEvent`) plus indirect invoice events from period close. Update the parent doc on next sweep.
