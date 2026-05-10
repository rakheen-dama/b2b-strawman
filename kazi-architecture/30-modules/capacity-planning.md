# Capacity Planning

**Bounded context:** see [`10-bounded-contexts.md` § capacity-planning](../10-bounded-contexts.md#capacity-planning) `→ 10-bounded-contexts.md:270`.
**Backend package:** `capacity/` `→ _discovery/A1-backend-map.md:20`.
**Phase doc:** `architecture/phase38-resource-planning-capacity.md` `→ architecture/phase38-resource-planning-capacity.md:1`.

## 1. Purpose

Resource planning for service-firm staffing: per-member weekly capacity, per-project allocations, and utilization analytics combining planned hours with actual time entries. Module-gated `resource_planning` `→ _discovery/A2-frontend-map.md:187`. Capability-gated `RESOURCE_PLANNING` (alias `RES_PLAN`) per `glossary.md:67`. Profile-leaning consulting (the dashboard `TeamUtilizationWidget` self-gates to `consulting-za`).

## 2. Entities owned

| Entity | Table | Anchor |
|---|---|---|
| `MemberCapacity` | `member_capacities` | `→ backend/.../capacity/MemberCapacity.java:16` |
| `ResourceAllocation` | `resource_allocations` | `→ backend/.../capacity/ResourceAllocation.java:16` |
| `LeaveBlock` | `leave_blocks` | `→ backend/.../capacity/LeaveBlock.java:15` |

### MemberCapacity (effective-dated)

Weekly available hours per member. Effective-from / effective-to dating supports change-over-time without history loss (e.g., FT → 4 days/week). Fields: `memberId`, `weeklyHours (NUMERIC(5,2))`, `effectiveFrom (LocalDate)`, `effectiveTo (LocalDate, nullable)`, `note`, `createdBy`. `→ backend/.../capacity/MemberCapacity.java:22-38`.

- `effectiveFrom` must be a Monday — aligns with weekly allocation grain (ADR-150). `→ architecture/phase38-resource-planning-capacity.md:72`.
- Latest applicable record wins on lookup (mirrors `BillingRate` per ADR-039). `→ architecture/phase38-resource-planning-capacity.md:71`.

### ResourceAllocation (week × project × member)

Planned hours commitment. Fields: `memberId`, `projectId`, `weekStart (LocalDate)`, `allocatedHours (NUMERIC(5,2))`, `note`, `createdBy`. `→ backend/.../capacity/ResourceAllocation.java:22-38`. Distinct from Capacity: capacity is *available*, allocation is *committed* `→ glossary.md:42`.

### LeaveBlock (unavailability marker)

Date-range marker that reduces effective capacity. No leave types, accrual, or approval workflow — pure visibility primitive. `→ backend/.../capacity/LeaveBlock.java:14`. Weekday-only counting: `effectiveCapacity = weeklyHours * (5 - leaveDaysInWeek) / 5` `→ architecture/phase38-resource-planning-capacity.md:120`.

## 3. REST surface

All write endpoints gated by `@RequiresCapability("RESOURCE_PLANNING")` `→ backend/.../capacity/MemberCapacityController.java:33` ; `→ backend/.../capacity/ResourceAllocationController.java:45`.

### Member capacity (`MemberCapacityController` — nested under member)

| Verb + path | Purpose | Anchor |
|---|---|---|
| `GET /api/members/{memberId}/capacity` | List capacity records | `MemberCapacityController.java:32` |
| `POST /api/members/{memberId}/capacity` | Create capacity | `MemberCapacityController.java:38` |
| `PUT /api/members/{memberId}/capacity/{id}` | Update capacity | `MemberCapacityController.java:47` |
| `DELETE /api/members/{memberId}/capacity/{id}` | Delete capacity | `MemberCapacityController.java:56` |

### Resource allocations (`ResourceAllocationController`)

| Verb + path | Purpose | Anchor |
|---|---|---|
| `GET /api/resource-allocations` | List allocations (filtered) | `ResourceAllocationController.java:34` |
| `POST /api/resource-allocations` | Create allocation | `ResourceAllocationController.java:44` |
| `PUT /api/resource-allocations/{id}` | Update allocation | `ResourceAllocationController.java:52` |
| `DELETE /api/resource-allocations/{id}` | Delete allocation | `ResourceAllocationController.java:59` |
| `POST /api/resource-allocations/bulk` | Bulk upsert | `ResourceAllocationController.java:66` |

### Capacity grid (`CapacityController`)

| Verb + path | Purpose | Anchor |
|---|---|---|
| `GET /api/capacity/team` | Team capacity grid (weekStart…weekEnd) | `CapacityController.java:25` |
| `GET /api/capacity/members/{memberId}` | Per-member grid | `CapacityController.java:32` |
| `GET /api/capacity/projects/{projectId}` | Project staffing view | `CapacityController.java:42` |

All three enforce `weekStart.dayOfWeek == MONDAY` (else `InvalidStateException`) `→ CapacityController.java:51`.

### Utilization (`UtilizationController`)

| Verb + path | Purpose | Anchor |
|---|---|---|
| `GET /api/utilization/team` | Team utilization summary + averages | `UtilizationController.java:25` |
| `GET /api/utilization/members/{memberId}` | Per-member weekly utilization | `UtilizationController.java:32` |

### Leave (`LeaveBlockController`)

| Verb + path | Purpose | Anchor |
|---|---|---|
| `GET /api/members/{memberId}/leave` | List leave for member | `LeaveBlockController.java:31` |
| `POST /api/members/{memberId}/leave` | Create leave block | `LeaveBlockController.java:36` |
| `PUT /api/members/{memberId}/leave/{id}` | Update leave block | `LeaveBlockController.java:44` |
| `DELETE /api/members/{memberId}/leave/{id}` | Delete leave block | `LeaveBlockController.java:52` |
| `GET /api/leave` | Org-wide leave list | `LeaveBlockController.java:58` |

> **Note:** `_discovery/A1-backend-map.md:415` records `~8` endpoints under `/api/capacity` + `/api/allocations`. The actual surface (post-Phase 38) spans **5 controllers / ~16 endpoints** across `/api/members/{id}/capacity`, `/api/resource-allocations`, `/api/capacity/*`, `/api/utilization/*`, and `/api/members/{id}/leave`. The `/api/allocations` path noted in the discovery doc is in fact `/api/resource-allocations` `→ ResourceAllocationController.java:34`.

## 4. Frontend pages / components

Module-gated by `resource_planning`; capability `RES_PLAN` required.

| Path | Purpose | Anchor |
|---|---|---|
| `app/(app)/resources/page.tsx` | Team capacity allocation grid | `→ _discovery/A2-frontend-map.md:188` |
| `app/(app)/resources/utilization/page.tsx` | Team utilization charts + table | `→ _discovery/A2-frontend-map.md:190` |
| `app/(app)/settings/capacity/page.tsx` | Default weekly hours setting | `→ _discovery/A2-frontend-map.md:236` |
| `components/capacity/` | `AllocationGrid`, `UtilizationTable`, `UtilizationChart` | `→ _discovery/A2-frontend-map.md:440` |
| `components/dashboard/team-utilization-widget.tsx` | Dashboard widget — profile self-gates `consulting-za` | `→ frontend/components/dashboard/team-utilization-widget.tsx:18` |

API client: `lib/api/capacity.ts` — exposes `/api/capacity/team` `→ _discovery/A2-frontend-map.md:302`.

## 5. Domain events

One emitted event:

- `MemberOverAllocatedEvent(memberId, weekStart, totalAllocated, effectiveCapacity, overageHours)` `→ backend/.../capacity/MemberOverAllocatedEvent.java:7`. Published by `ResourceAllocationService.checkOverAllocation()` whenever total weekly allocations exceed effective capacity `→ ResourceAllocationService.java:373`.

No external subscribers in the capacity package — the same code path also calls `sendOverAllocationNotifications()` synchronously, so the event is currently published but unsubscribed (potential cleanup) `→ ResourceAllocationService.java:378, 385`.

## 6. Cross-cutting touchpoints

- **Capability gate:** `@RequiresCapability("RESOURCE_PLANNING")` on every mutating endpoint `→ MemberCapacityController.java:33, 39, 48, 57` ; `ResourceAllocationController.java:45, 53, 60, 67`.
- **Module gate:** `resource_planning` slug `→ glossary.md:174`.
- **Audit:** allocation/capacity entities carry `createdBy` + `created_at` + `updated_at` columns; standard audit trail applies.
- **Notifications:** over-allocation → in-app notifications to the affected member + admins/owners (excluding the actor) `→ ResourceAllocationService.java:385-399`.
- **Auto-add project members:** creating an allocation auto-adds the member to the project (idempotent) `→ ResourceAllocationService.java:350`.
- **Time-entry coupling:** `UtilizationService` joins `ResourceAllocation` with billable-hour rollups from `time-entry` per week `→ UtilizationService.java:72-96`.
- **Weekly granularity** is enforced everywhere: capacity `effectiveFrom` must be Monday, allocations key on `weekStart`, capacity grid endpoints reject non-Monday `weekStart` `→ CapacityController.java:51`.

## 7. Vertical specifics

- **Module-gated `resource_planning`** — module slug enumerated in `ModuleCategory` `→ glossary.md:174`.
- **Profile-leaning consulting:** the dashboard `TeamUtilizationWidget` is self-gated — `shouldFetch = profile === "consulting-za"` `→ frontend/components/dashboard/team-utilization-widget.tsx:18`, and renders `null` for any other profile `→ frontend/components/dashboard/team-utilization-widget.tsx:31`. Underlying entities are core (not legal-only / not accounting-only).
- Legal/accounting verticals can enable the module if they want allocation tracking, but the dashboard surfacing is consulting-only.

## 8. Active ADRs

| ADR | Title | Anchor |
|---|---|---|
| ADR-150 | Weekly vs daily allocation granularity | `→ 90-adr-index.md:148` |
| ADR-151 | Planned vs actual separation | `→ 90-adr-index.md:149` |
| ADR-152 | Capacity model design | `→ 90-adr-index.md:150` |
| ADR-153 | Over-allocation policy (warn-not-block) | `→ 90-adr-index.md:151` |

## 9. Key flows

No dedicated flow page in `50-flows/` — cross-module orchestration is light, capacity is largely a self-contained planning surface. Internal flows of note:

- **Allocation create** → over-allocation check → `MemberOverAllocatedEvent` + notifications `→ ResourceAllocationService.java:98, 366-383`.
- **Utilization calc** (per member) → resolve `effectiveCapacity` per week → join billable hours from `time-entry` → `pct = billable*100 / effectiveCapacity` (HALF_UP, scale 2) `→ CapacityMathUtil.java:12` ; `→ UtilizationService.java:56-99`.

## 10. Open questions / known fragility

- **Allocation conflicts (over-allocation): warn, do not block.** Per ADR-153 `→ architecture/phase38-resource-planning-capacity.md:100`. The allocation persists; the response carries an `overAllocated` flag + `overageHours`; an event is published and notifications are sent. Open question: whether bulk upsert aggregates notifications correctly — `ResourceAllocationService` deduplicates over-allocation checks per member-week `→ ResourceAllocationService.java:275`, but per-call notification dispatch behaviour for multi-week bulk batches is not anchored.
- **MemberCapacity history rollup:** "Latest applicable record wins" but the resolution semantics on overlapping `effectiveFrom`/`effectiveTo` ranges are not documented at the controller layer. `CapacityService.getMemberEffectiveCapacity(memberId, weekStart)` is the single point of truth `→ ResourceAllocationService.java:369`. No anchor confirms behaviour when two records have overlapping windows.
- **Utilization formula scope (billable vs non-billable):** `UtilizationService` joins **billable hours only** from `time-entry`, reporting `billablePct = billable / effectiveCapacity` `→ UtilizationService.java:72, 96`. There is no "total utilization" (billable + non-billable) variant on the controller — `WeekUtilization` carries `billable` per `:88-96`. Open question: do consulting customers expect a separate "billable utilization %" vs "total utilization %" split? Currently they get only the billable view.
- **`MemberOverAllocatedEvent` has no listeners** — published but unsubscribed (notifications are sent inline, not via the event). Either remove the event or migrate notifications to a listener for consistency with the rest of the event-driven surface.
- **Discovery/code drift:** `_discovery/A1-backend-map.md:415` undercounts the REST surface (says ~8 endpoints; actual is ~16 across 5 controllers) and lists `/api/allocations` instead of `/api/resource-allocations`. The discovery doc predates Phase 38's `LeaveBlock`, `UtilizationController`, and `CapacityController` split.
