# Phase 38 -- Resource Planning & Capacity

Phase 38 adds resource planning and capacity management to the DocTeams platform. Three new entities -- `MemberCapacity`, `ResourceAllocation`, and `LeaveBlock` -- model how much time a member has (capacity), where that time is planned to go (allocations), and when a member is unavailable (leave). A `CapacityService` centralises all capacity resolution logic, a `ResourceAllocationService` handles allocation CRUD with over-allocation warnings, and a `UtilizationService` combines planned allocations with actual time entries to produce utilization metrics. The existing `ProfitabilityReportService` (Phase 8) is extended with projected revenue and cost calculations based on forward-looking allocations. The signature UI is an allocation grid -- a members-by-weeks matrix with colour-coded utilization cells. Dashboard widgets ("Team Capacity" and "My Schedule") surface key metrics, and a new "Staffing" tab on the project detail page shows per-project allocation breakdowns.

**Architecture doc**: `architecture/phase38-resource-planning-capacity.md`

**ADRs**:
- [ADR-150](../adr/ADR-150-weekly-vs-daily-allocation-granularity.md) -- Weekly vs. Daily Allocation Granularity (weekly with ISO Monday start)
- [ADR-151](../adr/ADR-151-planned-vs-actual-separation.md) -- Planned vs. Actual Separation (full independence, comparison in reports only)
- [ADR-152](../adr/ADR-152-capacity-model-design.md) -- Capacity Model Design (single weeklyHours with effective dates + org default)
- [ADR-153](../adr/ADR-153-over-allocation-policy.md) -- Over-Allocation Policy (warning with save, domain event)

**Migrations**: V58 (tenant schema) -- Note: Phase 37 also targets V58 but is not yet merged. Whichever merges second uses V59.

**Dependencies on prior phases**: Phase 4 (Project, Member entities), Phase 5 (TimeEntry for utilization), Phase 6 (AuditService), Phase 6.5 (NotificationService), Phase 8 (BillingRate, CostRate, ProjectBudget, ProfitabilityReportService, RateResolutionService, OrgSettings).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 289 | Entity Foundation & Migration | Backend | -- | M | 289A, 289B | **Done** (PRs #569, #570) |
| 290 | Capacity & Allocation Services | Backend | 289 | L | 290A, 290B, 290C | |
| 291 | Utilization Service & Profitability Integration | Backend | 290 | M | 291A, 291B | |
| 292 | Allocation Grid UI | Frontend | 290 | L | 292A, 292B | |
| 293 | Utilization, Dashboard & Project Staffing UI | Frontend | 291, 292 | M | 293A, 293B | |
| 294 | Notifications, Audit Events & Settings UI | Backend + Frontend | 290 | M | 294A, 294B | |

---

## Dependency Graph

```
BACKEND TRACK
─────────────
[E289A V58 migration:
 member_capacities +
 resource_allocations +
 leave_blocks tables +
 OrgSettings extension +
 all indexes + constraints]
        |
[E289B MemberCapacity +
 ResourceAllocation +
 LeaveBlock entities +
 repositories +
 MemberOverAllocatedEvent +
 persistence tests]
        |
        +──────────────────────────────────+
        |                                  |
[E290A CapacityService +                 [E290B LeaveBlockService +
 MemberCapacityController +               LeaveBlockController +
 capacity resolution +                    leave CRUD +
 effective capacity with leave +           self-service + admin RBAC +
 OrgSettings extension +                   tests]
 tests]                                    |
        |                                  |
        +──────────────────────────────────+
        |
[E290C ResourceAllocationService +
 ResourceAllocationController +
 allocation CRUD + bulk upsert +
 over-allocation check +
 auto-add ProjectMember +
 MemberOverAllocatedEvent publish +
 tests]
        |
        +──────────────────────────────────+──────────────+
        |                                  |              |
[E291A UtilizationService +              [E294A          |
 CapacityController (team grid,           Notifications + |
 member detail, project staffing) +       audit events +  |
 utilization endpoints + tests]           tests]          |
        |                                                 |
[E291B ProfitabilityReportService                         |
 extension (includeProjections) +                         |
 allocation-based projections +                           |
 tests]                                                   |

FRONTEND TRACK (after respective backend epics)
────────────────────────────────────────────────
                                               [E292A resources/page.tsx +
                                                AllocationGrid +
                                                CapacityCell +
                                                WeekRangeSelector +
                                                sidebar nav +
                                                API client + tests]
                                                       |
                                               [E292B AllocationPopover +
                                                MemberDetailPanel +
                                                LeaveDialog +
                                                grid filters +
                                                tests]
                                                       |
        +──────────────────────────────────────────────+
        |                                              |
[E293A resources/utilization/page.tsx +         [E293B Project "Staffing" tab +
 UtilizationTable +                              dashboard widgets
 team utilization charts +                       (TeamCapacityWidget +
 tests]                                          MyScheduleWidget) +
                                                 profitability projections toggle +
[E294B Settings capacity section +               tests]
 notification rendering +
 tests]
```

**Parallel opportunities**:
- After E290C: E291A, E292A, and E294A can all start in parallel (utilization service, grid UI, and notifications are independent).
- After E292A: E292B can proceed (popover/detail components).
- After E291A + E292B: E293A and E293B can start in parallel.
- E294B (frontend settings/notifications) can start after E294A.

---

## Implementation Order

### Stage 0: Database Migration

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 289 | 289A | V62 tenant migration: CREATE TABLE member_capacities, resource_allocations, leave_blocks + ALTER TABLE org_settings ADD COLUMN default_weekly_capacity_hours + all indexes + constraints. ~1 new migration file. Backend only. | **Done** (PR #569) |

### Stage 1: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 289 | 289B | MemberCapacity + ResourceAllocation + LeaveBlock entities + JPA repositories with JPQL queries + MemberOverAllocatedEvent domain event record + OrgSettings extension (defaultWeeklyCapacityHours field) + persistence integration tests (~12 tests). ~10 new files + ~1 modified file. Backend only. | **Done** (PR #570) |

### Stage 2: Core Services (sequential with parallelism)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 290 | 290A | CapacityService (capacity resolution chain, effective capacity with leave reduction) + MemberCapacityController (CRUD for capacity records) + DTOs + OrgSettingsService extension (expose defaultWeeklyCapacityHours) + integration tests (~12 tests). ~6 new files + ~2 modified files. Backend only. | **Done** (PR #571) |
| 2b (parallel) | 290 | 290B | LeaveBlockService (leave CRUD with date validation) + LeaveBlockController (CRUD endpoints, self-service + admin RBAC) + DTOs + integration tests (~10 tests). ~5 new files. Backend only. | |

### Stage 3: Allocation Service

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 290 | 290C | ResourceAllocationService (allocation CRUD, bulk upsert, over-allocation check + event, auto-add ProjectMember) + ResourceAllocationController (CRUD + bulk endpoints) + DTOs + integration tests (~15 tests). ~6 new files. Backend only. | |

### Stage 4: Utilization & Profitability + Notifications (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 291 | 291A | UtilizationService (planned vs actual combination) + CapacityController (team grid, member detail, project staffing, utilization endpoints) + DTOs + integration tests (~15 tests). ~5 new files. Backend only. | |
| 4b (parallel) | 294 | 294A | Notification types (ALLOCATION_CHANGED, MEMBER_OVER_ALLOCATED, LEAVE_CREATED) + audit events (7 types) wired into capacity/allocation/leave services + integration tests (~12 tests). ~1 new file + ~3 modified files. Backend only. | |

### Stage 5: Profitability Extension

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 291 | 291B | ProfitabilityReportService extension (includeProjections parameter, allocation-based revenue/cost projections via RateResolutionService) + integration tests (~8 tests). ~0 new files + ~2 modified files. Backend only. | |

### Stage 6: Allocation Grid UI

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a | 292 | 292A | resources/page.tsx + AllocationGrid component + CapacityCell component + WeekRangeSelector component + "Resources" sidebar nav item + lib/api/capacity.ts API client + server actions + tests (~8 tests). ~8 new files + ~1 modified file. Frontend only. | |
| 6b | 292 | 292B | AllocationPopover (view/edit/add allocations per cell) + MemberDetailPanel (slide-over for capacity config + timeline) + LeaveDialog (add/edit leave block) + grid filters (member search, project filter, "show only over-allocated") + tests (~8 tests). ~6 new files + ~1 modified file. Frontend only. | |

### Stage 7: Utilization UI + Dashboard + Settings (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 7a (parallel) | 293 | 293A | resources/utilization/page.tsx + UtilizationTable component + utilization bar charts + tests (~6 tests). ~5 new files. Frontend only. | |
| 7b (parallel) | 293 | 293B | Project detail "Staffing" tab + TeamCapacityWidget (dashboard) + MyScheduleWidget (dashboard) + profitability "Include Projections" toggle + tests (~8 tests). ~6 new files + ~2 modified files. Frontend only. | |
| 7c (parallel) | 294 | 294B | Settings capacity section (org default weekly capacity hours) + capacity notification rendering in NotificationBell/Notifications page + tests (~4 tests). ~3 new files + ~2 modified files. Frontend only. | |

### Timeline

```
Stage 0: [289A]                                                    (sequential)
Stage 1: [289B]                                                    (sequential)
Stage 2: [290A] // [290B]                                          (parallel)
Stage 3: [290C]                                                    (sequential, after 290A + 290B)
Stage 4: [291A] // [294A]                                          (parallel)
Stage 5: [291B]                                                    (sequential, after 291A)
Stage 6: [292A] → [292B]                                           (sequential)
Stage 7: [293A] // [293B] // [294B]                                (parallel)
```

**Critical path**: 289A -> 289B -> 290A -> 290C -> 291A -> 291B (6 backend slices sequential) + 292A -> 292B -> 293B (3 frontend slices sequential).

**Fastest path with parallelism**: 14 slices total. Backend critical path is 6 slices. Frontend can start at Stage 6 (after 290C). Stages 2, 4, and 7 have parallel opportunities.

---

## Epic 289: Entity Foundation & Migration

**Goal**: Create the V58 tenant migration for all resource planning tables, build the three core entities with JPA mappings, define repositories with JPQL queries, extend OrgSettings, and establish the MemberOverAllocatedEvent domain event.

**References**: Architecture doc Sections 38.2 (domain model), 38.7 (V58 migration), 38.8 (entity code patterns).

**Dependencies**: None -- this is the greenfield foundation epic.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **289A** | 289.1--289.4 | V62 tenant migration: CREATE TABLE member_capacities, resource_allocations, leave_blocks + ALTER TABLE org_settings + all indexes + constraints + CHECK constraints. ~1 new migration file. Backend only. | **Done** (PR #569) |
| **289B** | 289.5--289.14 | MemberCapacity + ResourceAllocation + LeaveBlock entities + 3 JPA repositories with JPQL queries + MemberOverAllocatedEvent record + OrgSettings extension (defaultWeeklyCapacityHours) + persistence integration tests (~12 tests). ~10 new files + ~1 modified file. Backend only. | **Done** (PR #570) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 289.1 | Create V58 tenant migration -- member_capacities table | 289A | | New file: `backend/src/main/resources/db/migration/tenant/V58__create_resource_planning_tables.sql`. CREATE TABLE member_capacities (id UUID PK DEFAULT gen_random_uuid(), member_id UUID NOT NULL FK CASCADE, weekly_hours NUMERIC(5,2) NOT NULL CHECK > 0, effective_from DATE NOT NULL CHECK ISODOW = 1, effective_to DATE, note VARCHAR(500), created_by UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()). Index: idx_member_capacities_member_effective ON (member_id, effective_from DESC). See architecture doc Section 38.7. |
| 289.2 | V58 migration -- resource_allocations table | 289A | 289.1 | Same file. CREATE TABLE resource_allocations (id UUID PK, member_id UUID NOT NULL FK CASCADE, project_id UUID NOT NULL FK CASCADE, week_start DATE NOT NULL CHECK ISODOW = 1, allocated_hours NUMERIC(5,2) NOT NULL CHECK > 0 AND <= 168, note VARCHAR(500), created_by UUID NOT NULL, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ). UNIQUE(member_id, project_id, week_start). Indexes: idx_allocations_member_week, idx_allocations_project_week, idx_allocations_week. |
| 289.3 | V58 migration -- leave_blocks table | 289A | 289.1 | Same file. CREATE TABLE leave_blocks (id UUID PK, member_id UUID NOT NULL FK CASCADE, start_date DATE NOT NULL, end_date DATE NOT NULL CHECK >= start_date, note VARCHAR(500), created_by UUID NOT NULL, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ). Index: idx_leave_blocks_member_dates ON (member_id, start_date, end_date). |
| 289.4 | V58 migration -- OrgSettings extension | 289A | | Same file. ALTER TABLE org_settings ADD COLUMN default_weekly_capacity_hours NUMERIC(5,2) DEFAULT 40.00. COMMENT on column. |
| 289.5 | Create MemberCapacity entity | 289B | | New file: `capacity/MemberCapacity.java`. @Entity @Table("member_capacities"). Fields per architecture doc Section 38.2.1. Protected no-arg constructor. Constructor with memberId, weeklyHours, effectiveFrom, effectiveTo, note, createdBy. update() method. Pattern: `billingrate/BillingRate.java` (effective-date entity pattern). |
| 289.6 | Create ResourceAllocation entity | 289B | | New file: `capacity/ResourceAllocation.java`. @Entity @Table("resource_allocations"). Fields per Section 38.2.2. Constructor with memberId, projectId, weekStart, allocatedHours, note, createdBy. update(allocatedHours, note) method. Pattern: `budget/ProjectBudget.java`. |
| 289.7 | Create LeaveBlock entity | 289B | | New file: `capacity/LeaveBlock.java`. @Entity @Table("leave_blocks"). Fields per Section 38.2.3. Constructor with memberId, startDate, endDate, note, createdBy. update(startDate, endDate, note) method. Simple entity with date range. |
| 289.8 | Create MemberCapacityRepository | 289B | 289.5 | New file: `capacity/MemberCapacityRepository.java`. JpaRepository<MemberCapacity, UUID>. Custom queries: findByMemberIdAndEffectiveFromLessThanEqualAndEffectiveToIsNullOrEffectiveToGreaterThanEqual (capacity resolution), findByMemberIdOrderByEffectiveFromDesc (list all for member). JPQL pattern per architecture doc Section 38.8. |
| 289.9 | Create ResourceAllocationRepository | 289B | 289.6 | New file: `capacity/ResourceAllocationRepository.java`. JpaRepository<ResourceAllocation, UUID>. Custom JPQL: findByMemberAndDateRange, findByProjectAndDateRange, findAllInDateRange, sumAllocatedHoursForMemberWeek, findByMemberIdAndProjectIdAndWeekStart. See architecture doc Section 38.8 repository code pattern. |
| 289.10 | Create LeaveBlockRepository | 289B | 289.7 | New file: `capacity/LeaveBlockRepository.java`. JpaRepository<LeaveBlock, UUID>. Custom queries: findByMemberIdOrderByStartDateDesc (list for member), findByMemberIdAndOverlapping(memberId, start, end) -- overlapping date range query, findAllOverlapping(start, end) -- team calendar. |
| 289.11 | Create MemberOverAllocatedEvent | 289B | | New file: `capacity/MemberOverAllocatedEvent.java`. Record: MemberOverAllocatedEvent(UUID memberId, LocalDate weekStart, BigDecimal totalAllocated, BigDecimal effectiveCapacity, BigDecimal overageHours). Published via ApplicationEventPublisher. Forward-compatible with Phase 37 automation engine. |
| 289.12 | Extend OrgSettings entity | 289B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add field: defaultWeeklyCapacityHours (BigDecimal, @Column precision=5, scale=2). Add getter and setter. Pattern: existing OrgSettings fields (defaultCurrency, etc.). |
| 289.13 | Write entity persistence integration tests | 289B | 289.8, 289.9, 289.10 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityEntityTest.java`. Tests (~12): MemberCapacity save + find + effective date resolution, ResourceAllocation save + unique constraint violation (409), ResourceAllocation sumAllocatedHoursForMemberWeek, LeaveBlock save + overlapping query, OrgSettings defaultWeeklyCapacityHours round-trip, CHECK constraint on weekStart (must be Monday), CHECK constraint on allocated_hours (> 0, <= 168), CHECK constraint on end_date >= start_date, CHECK on weekly_hours > 0, cascade delete (member deleted -> allocations/leave/capacity deleted), findByMemberAndDateRange, findByProjectAndDateRange. |
| 289.14 | Write repository query tests | 289B | 289.13 | In same test file. Tests: findAllInDateRange returns correct allocations, findByMemberIdAndProjectIdAndWeekStart for upsert lookup, findByMemberIdAndOverlapping for leave overlap. |

### Key Files

**Slice 289A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V58__create_resource_planning_tables.sql`

**Slice 289B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/MemberCapacity.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocation.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/LeaveBlock.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/MemberCapacityRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocationRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/LeaveBlockRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/MemberOverAllocatedEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityEntityTest.java`

**Slice 289B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` -- add defaultWeeklyCapacityHours field

**Slice 289B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRate.java` -- effective-date entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudget.java` -- mutable entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateRepository.java` -- repository JPQL pattern

### Architecture Decisions

- **V58 migration (shared with Phase 37)**: Phase 37 also targets V58. Whichever phase merges first takes V58; the second must renumber to V59. Both are pure CREATE TABLE/ALTER TABLE with no dependencies on each other.
- **All 3 tables + ALTER in a single V58 file**: All resource planning tables are new with no conflicts. Single migration keeps the schema creation atomic.
- **No overlap validation on MemberCapacity**: If multiple records cover the same week, the one with the latest effectiveFrom wins. Simpler than enforcing non-overlapping ranges.
- **MemberOverAllocatedEvent as a standalone record**: Not implementing DomainEvent interface -- this is a Spring application event published via ApplicationEventPublisher.publishEvent(). Forward-compatible with Phase 37 automation triggers.

---

## Epic 290: Capacity & Allocation Services

**Goal**: Build the core business logic services for capacity resolution, leave management, and resource allocation CRUD, including over-allocation detection, auto-add ProjectMember, bulk upsert, and REST controllers with RBAC.

**References**: Architecture doc Sections 38.3.1 (capacity resolution), 38.3.2 (allocation CRUD), 38.3.3 (leave management), 38.4 (API surface), 38.3.8 (RBAC).

**Dependencies**: Epic 289 (entities, repositories, migration).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **290A** | 290.1--290.8 | CapacityService (capacity resolution chain, effective capacity with leave) + MemberCapacityController (4 endpoints) + capacity DTOs + OrgSettingsService extension + integration tests (~12 tests). ~6 new files + ~2 modified files. Backend only. | **Done** (PR #571) |
| **290B** | 290.9--290.14 | LeaveBlockService (leave CRUD, date validation) + LeaveBlockController (5 endpoints, self-service + admin RBAC) + leave DTOs + integration tests (~10 tests). ~5 new files. Backend only. | |
| **290C** | 290.15--290.23 | ResourceAllocationService (allocation CRUD, bulk upsert, over-allocation check + event, auto-add ProjectMember) + ResourceAllocationController (5 endpoints) + allocation DTOs + integration tests (~15 tests). ~6 new files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 290.1 | Create CapacityService | 290A | | New file: `capacity/CapacityService.java`. @Service. Methods: getMemberCapacity(memberId, weekStart) -- resolution chain: MemberCapacity record -> OrgSettings.defaultWeeklyCapacityHours -> hard default 40.0. getMemberEffectiveCapacity(memberId, weekStart) -- base capacity minus leave reduction: weeklyHours * (5 - leaveDaysInWeek) / 5. Private method: countLeaveDaysInWeek(memberId, weekStart) -- uses Set<LocalDate> for deduplication, weekdays only. See architecture doc Section 38.3.1 pseudo-code. Pattern: `billingrate/BillingRateService.java` for resolution chain. |
| 290.2 | Create capacity DTOs | 290A | | New file: `capacity/dto/CapacityDtos.java`. Records: CreateCapacityRequest(BigDecimal weeklyHours, LocalDate effectiveFrom, LocalDate effectiveTo, String note), UpdateCapacityRequest(BigDecimal weeklyHours, LocalDate effectiveTo, String note), MemberCapacityResponse(UUID id, UUID memberId, BigDecimal weeklyHours, LocalDate effectiveFrom, LocalDate effectiveTo, String note, Instant createdAt). |
| 290.3 | Create MemberCapacityController | 290A | 290.1, 290.2 | New file: `capacity/MemberCapacityController.java`. Endpoints: GET /api/members/{memberId}/capacity (list records), POST (create), PUT /{id} (update), DELETE /{id} (delete). @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')"). Pure delegation to service methods. Pattern: `billingrate/BillingRateController.java`. |
| 290.4 | Implement capacity record CRUD in CapacityService | 290A | 290.1 | In CapacityService. Methods: listCapacityRecords(memberId), createCapacityRecord(memberId, request, createdBy), updateCapacityRecord(id, request), deleteCapacityRecord(id). Validation: effectiveFrom must be Monday, weeklyHours > 0. |
| 290.5 | Extend OrgSettingsService for defaultWeeklyCapacityHours | 290A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`. Add getDefaultWeeklyCapacityHours() and updateDefaultWeeklyCapacityHours(BigDecimal) methods. Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` -- expose in existing settings endpoint. |
| 290.6 | Write CapacityService integration tests | 290A | 290.1 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityServiceTest.java`. Tests (~12): getMemberCapacity with matching record, getMemberCapacity fallback to OrgSettings, getMemberCapacity fallback to hard default 40.0, overlapping effective dates (latest effectiveFrom wins), effectiveTo in past (record skipped), getMemberEffectiveCapacity no leave (full capacity), getMemberEffectiveCapacity with 2 leave days (capacity * 3/5), full week leave (0 capacity), overlapping leave blocks (deduplicated), weekend-only leave (no reduction), capacity record CRUD (create, update, delete), effectiveFrom must be Monday (400 on violation). |
| 290.7 | Write MemberCapacityController integration tests | 290A | 290.3 | In same or separate test file. Tests: POST creates record (201), GET lists records, PUT updates record, DELETE removes record, RBAC (member gets 403), validation (effectiveFrom not Monday -> 400, weeklyHours <= 0 -> 400). |
| 290.8 | Write OrgSettings extension test | 290A | 290.5 | Add test to existing OrgSettings test file or create new. Test: default value is 40.0, update to 32.0, read back 32.0. |
| 290.9 | Create LeaveBlockService | 290B | | New file: `capacity/LeaveBlockService.java`. @Service. Methods: listLeaveForMember(memberId), listAllLeave(startDate, endDate), createLeaveBlock(memberId, request, createdBy), updateLeaveBlock(id, request), deleteLeaveBlock(id). Validation: endDate >= startDate. Self-service check: if actor is not admin/owner, must be own memberId. |
| 290.10 | Create leave DTOs | 290B | | New file: `capacity/dto/LeaveDtos.java`. Records: CreateLeaveRequest(LocalDate startDate, LocalDate endDate, String note), UpdateLeaveRequest(LocalDate startDate, LocalDate endDate, String note), LeaveBlockResponse(UUID id, UUID memberId, LocalDate startDate, LocalDate endDate, String note, UUID createdBy, Instant createdAt). |
| 290.11 | Create LeaveBlockController | 290B | 290.9, 290.10 | New file: `capacity/LeaveBlockController.java`. Endpoints: GET /api/members/{memberId}/leave, POST, PUT /{id}, DELETE /{id}, GET /api/leave (team calendar). Self-service endpoints: member can manage own leave. Admin/owner can manage any. Pattern: thin controller discipline. |
| 290.12 | Implement self-service RBAC for leave | 290B | 290.11 | In LeaveBlockService. Check: if current user role is MEMBER, verify memberId == current memberId (from RequestScopes.MEMBER_ID). Admins and owners can manage any member's leave. For GET /api/leave (team calendar): all members can view. |
| 290.13 | Write LeaveBlockService integration tests | 290B | 290.9 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/LeaveBlockServiceTest.java`. Tests (~10): create leave block, update leave block, delete leave block, endDate < startDate rejected (400), list for member (ordered by startDate DESC), team calendar list (date range filter), self-service create own leave (member role), self-service create other's leave rejected (403), admin create any member's leave, overlap allowed (two blocks for same dates). |
| 290.14 | Write LeaveBlockController integration tests | 290B | 290.11 | In same or separate test file. Tests: POST 201, GET list, PUT update, DELETE, GET /api/leave team calendar, RBAC tests (member self-service, member blocked on others). |
| 290.15 | Create ResourceAllocationService | 290C | | New file: `capacity/ResourceAllocationService.java`. @Service. Injects: ResourceAllocationRepository, ProjectRepository (for status check), ProjectMemberService (for auto-add), CapacityService (for over-allocation check), ApplicationEventPublisher. |
| 290.16 | Implement createAllocation | 290C | 290.15 | In ResourceAllocationService. Steps per architecture doc Section 38.3.2: (1) validate weekStart is Monday, (2) validate project not ARCHIVED/COMPLETED, (3) validate allocatedHours > 0 and <= 168, (4) check uniqueness (409 if exists), (5) auto-add ProjectMember if missing (role = CONTRIBUTOR), (6) persist, (7) over-allocation check (sum allocated hours for member+week, compare to effective capacity), (8) if over-allocated: publish MemberOverAllocatedEvent, (9) return DTO with overAllocated + overageHours. |
| 290.17 | Implement updateAllocation and deleteAllocation | 290C | 290.16 | In ResourceAllocationService. Update: load by ID, validate, update hours/note, re-run over-allocation check. Delete: simple removal, no cascading effects per ADR-151. |
| 290.18 | Implement bulkUpsertAllocations | 290C | 290.16 | In ResourceAllocationService. Accepts List<AllocationRequest>. For each: check existence via findByMemberIdAndProjectIdAndWeekStart, create or update. Deduplicate over-allocation checks per member+week. Returns list of results with overAllocated/overageHours per item. |
| 290.19 | Create allocation DTOs | 290C | | New file: `capacity/dto/AllocationDtos.java`. Records: CreateAllocationRequest(UUID memberId, UUID projectId, LocalDate weekStart, BigDecimal allocatedHours, String note), UpdateAllocationRequest(BigDecimal allocatedHours, String note), BulkAllocationRequest(List<CreateAllocationRequest> allocations), AllocationResponse(UUID id, UUID memberId, UUID projectId, LocalDate weekStart, BigDecimal allocatedHours, String note, boolean overAllocated, BigDecimal overageHours, Instant createdAt), BulkAllocationResponse(List<AllocationResultItem> results), AllocationResultItem(AllocationResponse allocation, boolean created). |
| 290.20 | Create ResourceAllocationController | 290C | 290.15, 290.19 | New file: `capacity/ResourceAllocationController.java`. Endpoints: GET /api/resource-allocations (query: memberId, projectId, weekStart, weekEnd), POST, PUT /{id}, DELETE /{id}, POST /bulk. @PreAuthorize admin/owner for write operations; all members for read. Pure delegation. |
| 290.21 | Write ResourceAllocationService integration tests | 290C | 290.16, 290.17, 290.18 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocationServiceTest.java`. Tests (~15): createAllocation happy path, weekStart not Monday rejected (400), ARCHIVED project rejected, duplicate allocation rejected (409), auto-add ProjectMember on first allocation, over-allocation detected (overAllocated=true, overageHours correct), under-capacity (overAllocated=false), MemberOverAllocatedEvent published, updateAllocation changes hours + re-checks over-allocation, deleteAllocation removes record, bulkUpsert creates new, bulkUpsert updates existing, bulkUpsert mixed create/update, bulkUpsert deduplicates over-allocation check per member+week, allocatedHours <= 0 rejected (400). |
| 290.22 | Write ResourceAllocationController integration tests | 290C | 290.20 | In same or separate test file. Tests: POST 201 with overAllocated response, GET with filters, PUT update, DELETE, POST /bulk, RBAC (member cannot create allocation, member can read own allocations). |
| 290.23 | Write over-allocation event integration test | 290C | 290.21 | Test: create allocation that causes over-allocation, verify MemberOverAllocatedEvent published with correct payload (memberId, weekStart, totalAllocated, effectiveCapacity, overageHours). Use @EventListener test capture. |

### Key Files

**Slice 290A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/dto/CapacityDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/MemberCapacityController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityServiceTest.java`

**Slice 290A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` -- expose defaultWeeklyCapacityHours
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` -- expose in settings API

**Slice 290B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/LeaveBlockService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/dto/LeaveDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/LeaveBlockController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/LeaveBlockServiceTest.java`

**Slice 290C -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocationService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/dto/AllocationDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocationController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocationServiceTest.java`

**Read for context (all slices):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java` -- resolution chain pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateController.java` -- controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudgetService.java` -- service CRUD pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMemberService.java` -- auto-add delegation target
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectRepository.java` -- project status lookup

### Architecture Decisions

- **Three slices for Epic 290**: Capacity resolution (290A) and leave (290B) are independent and can run in parallel. Allocation CRUD (290C) depends on both because the over-allocation check uses CapacityService (which includes leave reduction) and the allocation validation needs project lookups. This keeps each slice under 8 new files.
- **CapacityService separate from ResourceAllocationService**: Capacity is read-only logic (resolution + leave reduction); allocation is write-heavy logic (CRUD, bulk, events). Separating them follows the single-responsibility pattern and avoids a 500-line mega-service.
- **Auto-add ProjectMember in allocation service**: When a member is allocated to a project they are not yet a member of, a ProjectMember record (role=CONTRIBUTOR) is created automatically. This removes friction from the allocation workflow -- managers allocate first, membership follows.
- **Self-service leave RBAC**: Members can manage their own leave; admins/owners can manage any member's leave. This is checked in the service layer, not via @PreAuthorize, because the logic depends on comparing the actor's memberId with the target memberId.

---

## Epic 291: Utilization Service & Profitability Integration

**Goal**: Build the UtilizationService that combines planned allocations with actual time entries, the CapacityController endpoints for the team grid and utilization views, and extend ProfitabilityReportService with allocation-based projections.

**References**: Architecture doc Sections 38.3.4 (team capacity grid), 38.3.5 (utilization calculation), 38.3.6 (projected profitability), 38.4 (capacity & utilization API).

**Dependencies**: Epic 290 (CapacityService, ResourceAllocationService, LeaveBlockService).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **291A** | 291.1--291.8 | UtilizationService (planned vs actual combination, per-member per-week metrics) + CapacityController (5 endpoints: team grid, member detail, project staffing, team utilization, member utilization) + grid/utilization DTOs + integration tests (~15 tests). ~5 new files. Backend only. | |
| **291B** | 291.9--291.13 | ProfitabilityReportService extension (includeProjections parameter, allocation-based revenue/cost via RateResolutionService and CostRateService) + integration tests (~8 tests). ~0 new files + ~2 modified files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 291.1 | Create UtilizationService | 291A | | New file: `capacity/UtilizationService.java`. @Service. Injects: ResourceAllocationRepository, TimeEntryRepository (Phase 5), CapacityService. Method: getMemberUtilization(memberId, weekStart, weekEnd) -- for each week: query planned hours (SUM allocations), query actual hours + billable hours (SUM time entries grouped by week via date_trunc), resolve effective capacity, compute planned/actual/billable utilization %. See architecture doc Section 38.3.5. |
| 291.2 | Implement getTeamUtilization | 291A | 291.1 | In UtilizationService. Method: getTeamUtilization(weekStart, weekEnd) -- for all org members: aggregate utilization per member + team averages. Sortable by any metric. Returns MemberUtilizationSummary per member with weekly breakdown + averages. |
| 291.3 | Implement getTeamCapacityGrid | 291A | 291.1 | In CapacityService (or a new GridAssemblyService). Method: getTeamCapacityGrid(weekStart, weekEnd) -- assembles the full members x weeks grid. 5 queries: members, allocations, leave, capacity records, org settings. Assembly in service layer. Returns TeamCapacityGrid per architecture doc Section 38.3.4. |
| 291.4 | Create grid and utilization DTOs | 291A | | New file: `capacity/dto/GridDtos.java`. Records: TeamCapacityGrid(List<MemberRow> members, List<WeekSummary> weekSummaries), MemberRow(UUID memberId, String memberName, List<WeekCell> weeks, BigDecimal totalAllocated, BigDecimal totalCapacity, BigDecimal avgUtilizationPct), WeekCell(LocalDate weekStart, List<AllocationSlot> allocations, BigDecimal totalAllocated, BigDecimal effectiveCapacity, BigDecimal remainingCapacity, BigDecimal utilizationPct, boolean overAllocated, int leaveDays), AllocationSlot(UUID projectId, String projectName, BigDecimal hours), WeekSummary(...). New file: `capacity/dto/UtilizationDtos.java`. Records: MemberUtilizationSummary(...), WeekUtilization(...), TeamUtilizationResponse(...). |
| 291.5 | Create CapacityController | 291A | 291.3, 291.4 | New file: `capacity/CapacityController.java`. Endpoints: GET /api/capacity/team (team grid), GET /api/capacity/members/{memberId} (member detail), GET /api/capacity/projects/{projectId} (project staffing), GET /api/utilization/team, GET /api/utilization/members/{memberId}. Query params: weekStart, weekEnd. @PreAuthorize all members for read access. Pure delegation. |
| 291.6 | Implement project staffing view | 291A | 291.5 | In CapacityService or UtilizationService. Method: getProjectStaffing(projectId, weekStart, weekEnd) -- allocated members for the project, weekly breakdown per member, total planned vs budget. Uses ProjectBudget for budget comparison. |
| 291.7 | Write UtilizationService integration tests | 291A | 291.1, 291.2 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/UtilizationServiceTest.java`. Tests (~10): member utilization with planned only (future week), member utilization with actual only (past week), member utilization with both (planned + actual), billable vs total actual hours, zero capacity edge case (returns 0% or infinity guard), team utilization aggregation, team averages calculation, over-allocated week count, empty date range (no data), utilization with leave reduction. |
| 291.8 | Write CapacityController + grid assembly integration tests | 291A | 291.3, 291.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityControllerTest.java`. Tests (~5): GET /api/capacity/team returns grid structure, grid cells have correct utilization calculations, project staffing view returns allocated members, RBAC (all members can read), week range filtering works. |
| 291.9 | Extend ProfitabilityReportService with includeProjections parameter | 291B | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java`. Add optional `includeProjections` boolean parameter to existing report methods. When true, future periods include projected revenue/cost from allocations. |
| 291.10 | Implement allocation-based projection algorithm | 291B | 291.9 | In ReportService. For future weeks: query ResourceAllocation records, for each (member + project + hours): resolve billing rate via existing rate resolution (RateResolutionService), resolve cost rate via CostRateService, compute projectedRevenue += hours * billingRate, projectedCost += hours * costRate. Missing billing rate -> revenue excluded (conservative). |
| 291.11 | Add projection DTOs | 291B | | Extend existing report DTOs or create new records: ProjectionData(BigDecimal projectedRevenue, BigDecimal projectedCost, BigDecimal projectedMargin). Add to existing profitability response shape alongside actual data. |
| 291.12 | Write profitability projection integration tests | 291B | 291.10 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/ProfitabilityProjectionTest.java`. Tests (~8): future allocation with billing rate + cost rate -> correct projection, missing billing rate -> revenue excluded, missing cost rate -> cost excluded, past week uses actuals not projections, current week shows both, multiple allocations aggregate correctly, projection with rate hierarchy resolution, empty allocations -> zero projections. |
| 291.13 | Write ReportController extension test | 291B | 291.12 | Add to existing report controller test or new file. Test: GET report with includeProjections=true includes projection data, includeProjections=false (default) omits projections. |

### Key Files

**Slice 291A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/UtilizationService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/dto/GridDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/dto/UtilizationDtos.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/UtilizationServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityControllerTest.java`

**Slice 291B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java` -- add includeProjections + projection algorithm
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportController.java` -- expose includeProjections query param

**Slice 291B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/ProfitabilityProjectionTest.java`

**Read for context (both slices):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/` -- TimeEntryRepository for actual hours queries
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java` -- existing profitability calculations
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java` -- rate resolution for projections
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/` -- CostRateService for cost projections
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudget.java` -- budget comparison for staffing view

### Architecture Decisions

- **UtilizationService in capacity/ package**: Despite querying TimeEntry (Phase 5), the utilization service lives in the capacity/ package because its primary purpose is capacity/allocation analysis. It imports from timeentry/ but does not modify it.
- **Grid assembly in service, not controller**: The team grid requires 5 queries and in-memory assembly. This logic belongs in the service layer, not the controller. The controller delegates to a single service call that returns the fully assembled grid.
- **Profitability projections computed on the fly**: Projections change whenever allocations or rates change. Caching would introduce staleness. The on-the-fly calculation is acceptable because projection queries are bounded by date range and member count.
- **Two slices for Epic 291**: 291A covers utilization + grid (the core read-side logic). 291B covers profitability extension (modifying existing code). This keeps the profitability changes isolated for easier review.

---

## Epic 292: Allocation Grid UI

**Goal**: Build the allocation grid page -- the signature UI of Phase 38 -- including the members-by-weeks matrix, colour-coded cells, week navigation, cell popover for editing allocations, member detail panel, leave dialog, and grid filters.

**References**: Architecture doc Sections 38.8 (frontend changes), 38.10 Slices D (allocation grid UI).

**Dependencies**: Epic 290 (backend allocation/capacity/leave APIs available).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **292A** | 292.1--292.9 | resources/page.tsx (Resources page) + AllocationGrid component + CapacityCell component + WeekRangeSelector component + "Resources" sidebar nav item + lib/api/capacity.ts API client + server actions + tests (~8 tests). ~8 new files + ~1 modified file. Frontend only. | |
| **292B** | 292.10--292.17 | AllocationPopover (click-to-edit per cell) + MemberDetailPanel (slide-over for capacity config + timeline) + LeaveDialog (add/edit leave block) + grid filters (member search, project filter, "show only over-allocated") + tests (~8 tests). ~6 new files + ~1 modified file. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 292.1 | Create capacity API client | 292A | | New file: `frontend/lib/api/capacity.ts`. Functions: getTeamCapacityGrid(weekStart, weekEnd), getMemberCapacityDetail(memberId, weekStart, weekEnd), getProjectStaffing(projectId, weekStart, weekEnd), getTeamUtilization(weekStart, weekEnd), getMemberUtilization(memberId, weekStart, weekEnd), listCapacityRecords(memberId), createCapacityRecord(memberId, data), updateCapacityRecord(memberId, id, data), deleteCapacityRecord(memberId, id), listAllocations(params), createAllocation(data), updateAllocation(id, data), deleteAllocation(id), bulkUpsertAllocations(data), listLeaveForMember(memberId), createLeaveBlock(memberId, data), updateLeaveBlock(memberId, id, data), deleteLeaveBlock(memberId, id), listAllLeave(startDate, endDate). Uses lib/api.ts pattern. Pattern: `frontend/lib/api/reports.ts`. |
| 292.2 | Create WeekRangeSelector component | 292A | | New file: `frontend/components/capacity/week-range-selector.tsx`. "use client". Controls: "4 weeks" / "8 weeks" / "12 weeks" toggle (Button group), prev/next arrow buttons (shift by visible week count), "This Week" jump button. Emits weekStart and weekEnd as URL search params. Pattern: `frontend/components/dashboard/date-range-selector.tsx`. |
| 292.3 | Create CapacityCell component | 292A | | New file: `frontend/components/capacity/capacity-cell.tsx`. "use client". Renders a single grid cell. Shows: total hours / effective capacity (e.g., "35/40"), project-coloured bars proportional to allocation, colour band: green (< 80%), amber (80-100%), red (> 100%). Leave overlay (hatched/dimmed) if leaveDays > 0. Empty cell shows "+" for adding. On-click handler (wired in 292B). |
| 292.4 | Create AllocationGrid component | 292A | 292.2, 292.3 | New file: `frontend/components/capacity/allocation-grid.tsx`. "use client". Members (rows) x weeks (columns) matrix. Row headers: member name + avatar. Column headers: week start dates. Each cell renders CapacityCell. Footer row: team totals per week (WeekSummary data). Sticky first column (member names) on horizontal scroll. |
| 292.5 | Create Resources page | 292A | 292.4 | New file: `frontend/app/(app)/org/[slug]/resources/page.tsx`. Server component. Fetches team capacity grid via API client. Renders: page header ("Resources"), WeekRangeSelector, AllocationGrid. Breadcrumbs: Resources. |
| 292.6 | Create server actions for resources | 292A | 292.1 | New file: `frontend/app/(app)/org/[slug]/resources/actions.ts`. "use server". Actions: createAllocationAction, updateAllocationAction, deleteAllocationAction, bulkUpsertAction. Calls API client, revalidates path. |
| 292.7 | Add "Resources" to sidebar navigation | 292A | | Modify: `frontend/lib/nav-items.ts`. Add "Resources" nav item with Users icon (from lucide-react). Position: between "My Work" and "Profitability" (after line ~20, before line ~59 in current nav-items.ts). Href: `/org/${slug}/resources`. |
| 292.8 | Create UtilizationBadge component | 292A | | New file: `frontend/components/capacity/utilization-badge.tsx`. Maps utilization % to colour variant: green (< 80%), amber (80-100%), red (> 100%), gray (no data). Used in grid cells, member rows, and utilization tables. |
| 292.9 | Write grid + cell tests | 292A | 292.4 | New file: `frontend/__tests__/capacity/allocation-grid.test.tsx`. Tests (~8): grid renders member rows, grid renders week columns, cell colour green when < 80% utilization, cell colour amber when 80-100%, cell colour red when > 100%, leave overlay shown when leaveDays > 0, week range selector changes visible weeks, empty cell shows add button. Pattern: existing component tests. |
| 292.10 | Create AllocationPopover component | 292B | | New file: `frontend/components/capacity/allocation-popover.tsx`. "use client". Shadcn Popover triggered by cell click. Shows: list of allocations for this member+week (project name, hours, edit/delete), "Add Allocation" form (project Select, hours Input, note Input, Save button). On save: calls createAllocation or updateAllocation action. Shows over-allocation warning if response.overAllocated is true. |
| 292.11 | Create MemberDetailPanel component | 292B | | New file: `frontend/components/capacity/member-detail-panel.tsx`. "use client". Shadcn Sheet (slide-over). Triggered by clicking member name in grid. Shows: member info (name, role), capacity config (current weeklyHours, edit form, effective dates history), allocation timeline (upcoming 4 weeks with per-project breakdown), leave blocks (list with add/edit/delete), utilization summary. |
| 292.12 | Create LeaveDialog component | 292B | | New file: `frontend/components/capacity/leave-dialog.tsx`. "use client". Shadcn Dialog for creating/editing leave blocks. Fields: start date (DatePicker), end date (DatePicker), note (Input). Validates endDate >= startDate on client side. On save: calls createLeaveBlock or updateLeaveBlock action. |
| 292.13 | Create grid filter controls | 292B | | New file: `frontend/components/capacity/grid-filters.tsx`. "use client". Controls: member name search (Input with search icon), project filter (multi-select Combobox), "Show only over-allocated" toggle (Switch). Filters applied client-side on the grid data. |
| 292.14 | Wire popover into AllocationGrid cells | 292B | 292.10 | Modify: `frontend/components/capacity/allocation-grid.tsx`. CapacityCell onClick opens AllocationPopover with the cell's member+week data. Popover positioned relative to the clicked cell. |
| 292.15 | Wire MemberDetailPanel into grid | 292B | 292.11 | Modify (if needed) or wire in resources/page.tsx. Member name click opens MemberDetailPanel Sheet with full member data. |
| 292.16 | Create leave server actions | 292B | | Extend `frontend/app/(app)/org/[slug]/resources/actions.ts`. Add: createLeaveAction, updateLeaveAction, deleteLeaveAction, createCapacityRecordAction, updateCapacityRecordAction, deleteCapacityRecordAction. |
| 292.17 | Write popover + panel + dialog tests | 292B | 292.10, 292.11, 292.12, 292.13 | New file: `frontend/__tests__/capacity/allocation-popover.test.tsx`. Tests (~8): popover opens on cell click, popover shows allocation list, add allocation form submits, over-allocation warning shown, member detail panel opens on name click, leave dialog opens and validates dates, filter by member name works, "show only over-allocated" toggle filters grid. |

### Key Files

**Slice 292A -- Create:**
- `frontend/lib/api/capacity.ts`
- `frontend/components/capacity/week-range-selector.tsx`
- `frontend/components/capacity/capacity-cell.tsx`
- `frontend/components/capacity/allocation-grid.tsx`
- `frontend/components/capacity/utilization-badge.tsx`
- `frontend/app/(app)/org/[slug]/resources/page.tsx`
- `frontend/app/(app)/org/[slug]/resources/actions.ts`
- `frontend/__tests__/capacity/allocation-grid.test.tsx`

**Slice 292A -- Modify:**
- `frontend/lib/nav-items.ts` -- add Resources nav item

**Slice 292B -- Create:**
- `frontend/components/capacity/allocation-popover.tsx`
- `frontend/components/capacity/member-detail-panel.tsx`
- `frontend/components/capacity/leave-dialog.tsx`
- `frontend/components/capacity/grid-filters.tsx`
- `frontend/__tests__/capacity/allocation-popover.test.tsx`

**Slice 292B -- Modify:**
- `frontend/components/capacity/allocation-grid.tsx` -- wire popover click handler
- `frontend/app/(app)/org/[slug]/resources/actions.ts` -- add leave/capacity server actions

**Read for context (both slices):**
- `frontend/components/dashboard/date-range-selector.tsx` -- date navigation pattern
- `frontend/components/ui/popover.tsx` -- Popover component
- `frontend/components/ui/sheet.tsx` -- Sheet component (for MemberDetailPanel)
- `frontend/components/ui/dialog.tsx` -- Dialog component (for LeaveDialog)
- `frontend/app/(app)/org/[slug]/profitability/page.tsx` -- data page pattern
- `frontend/lib/api/reports.ts` -- API client pattern

### Architecture Decisions

- **Two slices for grid UI**: 292A establishes the page shell, grid component, cell rendering, and navigation. 292B adds the interactive elements (popover, panel, dialog, filters). This keeps each slice under 9 new files.
- **Client-side filtering**: Grid data is fetched server-side in the page component. Filtering (member search, project filter, over-allocated toggle) is applied client-side on the already-loaded grid data. At 20 members x 12 weeks, the data set is small enough for in-memory filtering.
- **Popover over dialog for cell editing**: The AllocationPopover uses Shadcn Popover (positioned near the cell) rather than a full-screen dialog. This keeps the grid visible and the editing context clear.
- **"Resources" nav placement**: Between "My Work" and "Profitability" in the sidebar. This groups the capacity-related pages near existing work/financial views.

---

## Epic 293: Utilization, Dashboard & Project Staffing UI

**Goal**: Build the team utilization page, dashboard capacity widgets, project staffing tab, and profitability projections toggle.

**References**: Architecture doc Sections 38.8 (frontend changes), 38.10 Slices D-E.

**Dependencies**: Epic 291 (utilization + profitability APIs), Epic 292 (resources page shell).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **293A** | 293.1--293.5 | resources/utilization/page.tsx + UtilizationTable component + utilization bar charts + tests (~6 tests). ~5 new files. Frontend only. | |
| **293B** | 293.6--293.13 | Project detail "Staffing" tab + TeamCapacityWidget (dashboard) + MyScheduleWidget (dashboard) + profitability "Include Projections" toggle + tests (~8 tests). ~6 new files + ~2 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 293.1 | Create UtilizationTable component | 293A | | New file: `frontend/components/capacity/utilization-table.tsx`. "use client". DataTable with columns: Member Name, Weekly Capacity, Planned Hours, Actual Hours, Billable Hours, Planned Util %, Actual Util %, Billable Util %, Over-Allocated Weeks. Sortable by any column (default: actual util % DESC). Row click navigates to member detail. Colour-coded utilization columns (UtilizationBadge from 292A). |
| 293.2 | Create utilization bar chart | 293A | | New file: `frontend/components/capacity/utilization-chart.tsx`. "use client". Horizontal bar chart showing planned vs actual utilization per member. Uses recharts or simple CSS bars (matching existing chart patterns in the codebase). Team average line overlay. |
| 293.3 | Create utilization page | 293A | 293.1, 293.2 | New file: `frontend/app/(app)/org/[slug]/resources/utilization/page.tsx`. Server component. Fetches team utilization data. Renders: page header, date range selector (reuse WeekRangeSelector from 292A), UtilizationTable, utilization chart. Breadcrumbs: Resources > Utilization. |
| 293.4 | Add utilization link to resources page | 293A | | Add "Utilization" link/tab to the resources page header or as a sub-navigation item. Simple link to /resources/utilization. |
| 293.5 | Write utilization UI tests | 293A | 293.1 | New file: `frontend/__tests__/capacity/utilization-table.test.tsx`. Tests (~6): table renders member rows, sort by actual utilization, colour coding on utilization columns, chart renders bars, date range selector changes data, empty state when no utilization data. |
| 293.6 | Create TeamCapacityWidget component | 293B | | New file: `frontend/components/dashboard/team-capacity-widget.tsx`. Card showing: "Team Capacity" heading, utilization donut chart (planned % fill, actual % ring), total team hours (planned/capacity), over-allocated member count (red Badge if > 0), under-utilized member count. Click-through to /resources. Visible to all roles. Pattern: `frontend/components/dashboard/team-workload-widget.tsx`. |
| 293.7 | Create MyScheduleWidget component | 293B | | New file: `frontend/components/dashboard/my-schedule-widget.tsx`. Card showing: "My Schedule" heading, this week's allocations (project names + hours), capacity remaining, upcoming leave blocks (next 4 weeks). Personal widget -- uses current member's data. Pattern: existing dashboard KPI cards. |
| 293.8 | Add widgets to dashboard | 293B | 293.6, 293.7 | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx`. Add TeamCapacityWidget and MyScheduleWidget to the dashboard grid. Fetch capacity summary data (team grid for current week, member utilization for current user). |
| 293.9 | Create project Staffing tab | 293B | | New file: `frontend/components/capacity/project-staffing-tab.tsx`. "use client". Tab content for project detail page. Shows: allocated members table (member name, allocated hours per week, total), staffing timeline (simplified grid: members x weeks for this project only), planned hours vs budget comparison (if ProjectBudget exists). |
| 293.10 | Add Staffing tab to project detail page | 293B | 293.9 | Modify: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Add "Staffing" tab alongside existing tabs (Tasks, Documents, Time, Activity, etc.). Fetch project staffing data. Server action for staffing data fetch. |
| 293.11 | Add "Include Projections" toggle to profitability | 293B | | Modify: `frontend/app/(app)/org/[slug]/profitability/page.tsx` or relevant profitability component. Add Switch toggle "Include Projections". When enabled, passes includeProjections=true to report API. Projected values shown in lighter colour or with a "Projected" label. |
| 293.12 | Create staffing server actions | 293B | | New file: `frontend/app/(app)/org/[slug]/projects/[id]/staffing-actions.ts`. "use server". Actions: getProjectStaffing(projectId, weekStart, weekEnd). Calls capacity API client. |
| 293.13 | Write dashboard + staffing tests | 293B | 293.6, 293.9 | New file: `frontend/__tests__/capacity/dashboard-widgets.test.tsx`. Tests (~8): TeamCapacityWidget renders utilization donut, TeamCapacityWidget shows over-allocated count, MyScheduleWidget shows this week's allocations, MyScheduleWidget shows upcoming leave, Staffing tab renders allocated members, Staffing tab shows budget comparison, projections toggle changes report data, empty states for all widgets. |

### Key Files

**Slice 293A -- Create:**
- `frontend/components/capacity/utilization-table.tsx`
- `frontend/components/capacity/utilization-chart.tsx`
- `frontend/app/(app)/org/[slug]/resources/utilization/page.tsx`
- `frontend/__tests__/capacity/utilization-table.test.tsx`

**Slice 293B -- Create:**
- `frontend/components/dashboard/team-capacity-widget.tsx`
- `frontend/components/dashboard/my-schedule-widget.tsx`
- `frontend/components/capacity/project-staffing-tab.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/staffing-actions.ts`
- `frontend/__tests__/capacity/dashboard-widgets.test.tsx`

**Slice 293B -- Modify:**
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` -- add capacity widgets
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` -- add Staffing tab
- `frontend/app/(app)/org/[slug]/profitability/page.tsx` -- add projections toggle

**Read for context (both slices):**
- `frontend/components/dashboard/team-workload-widget.tsx` -- dashboard widget pattern
- `frontend/components/dashboard/kpi-card.tsx` -- KPI card pattern
- `frontend/components/profitability/utilization-table.tsx` -- existing utilization table (Phase 8)
- `frontend/components/profitability/project-financials-tab.tsx` -- project financials pattern
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` -- project detail tab structure

### Architecture Decisions

- **Reuse WeekRangeSelector**: The utilization page reuses the same WeekRangeSelector component from the resources page (292A). Consistent navigation across capacity views.
- **Staffing tab on project detail, not a separate page**: The staffing view for a specific project is a tab on the project detail page, alongside existing tabs (Tasks, Documents, Time). This keeps project-related data co-located.
- **Dashboard widgets visible to all roles**: Both TeamCapacityWidget and MyScheduleWidget are visible to all org members. Members see their own data; admins/owners see team data. This is different from the automation widget (admin-only) because capacity awareness benefits everyone.
- **Two slices**: 293A covers the utilization page (self-contained). 293B covers the distributed UI changes (dashboard, project detail, profitability). This keeps modifications to existing pages grouped in one slice for easier review.

---

## Epic 294: Notifications, Audit Events & Settings UI

**Goal**: Wire notification types and audit events into the capacity/allocation/leave services, build the settings section for org default capacity hours, and ensure capacity notifications render correctly in the existing notification UI.

**References**: Architecture doc Sections 38.6 (notification, audit, automation details), 38.9 (permission model).

**Dependencies**: Epic 290 (services must exist to wire into).

**Scope**: Backend + Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **294A** | 294.1--294.8 | Notification types (ALLOCATION_CHANGED, MEMBER_OVER_ALLOCATED, LEAVE_CREATED) + 7 audit event types wired into services + integration tests (~12 tests). ~1 new file + ~3 modified files. Backend only. | |
| **294B** | 294.9--294.14 | Settings capacity section (org default weekly capacity) + notification rendering for capacity types in NotificationBell/Notifications page + tests (~4 tests). ~3 new files + ~2 modified files. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 294.1 | Add ALLOCATION_CHANGED notification | 294A | | Modify: `capacity/ResourceAllocationService.java`. After createAllocation and updateAllocation: send notification to the allocated member (not the creator) via NotificationService. Include project name, week, hours in notification metadata. Pattern: existing notification creation in `informationrequest/InformationRequestService.java`. |
| 294.2 | Add MEMBER_OVER_ALLOCATED notification | 294A | | Modify: `capacity/ResourceAllocationService.java`. When over-allocation detected: send notification to both the allocated member AND all org admins/owners. Include member name, week, totalAllocated, effectiveCapacity, overageHours. |
| 294.3 | Add LEAVE_CREATED notification | 294A | | Modify: `capacity/LeaveBlockService.java`. When admin creates leave for another member: send notification to that member. NOT sent when member creates own leave. Include start date, end date, note. |
| 294.4 | Add MEMBER_CAPACITY_UPDATED audit event | 294A | | Modify: `capacity/CapacityService.java`. On createCapacityRecord, updateCapacityRecord, deleteCapacityRecord: publish audit event via AuditEventBuilder. Payload: memberId, memberName, weeklyHours, effectiveFrom. Pattern: `audit/AuditEventBuilder.java`. |
| 294.5 | Add ALLOCATION_CREATED/UPDATED/DELETED audit events | 294A | | Modify: `capacity/ResourceAllocationService.java`. On create/update/delete: publish audit event. Payload: memberId, memberName, projectId, projectName, weekStart, allocatedHours (oldHours for update). |
| 294.6 | Add LEAVE_CREATED/UPDATED/DELETED audit events | 294A | | Modify: `capacity/LeaveBlockService.java`. On create/update/delete: publish audit event. Payload: memberId, memberName, startDate, endDate, note. |
| 294.7 | Write notification integration tests | 294A | 294.1, 294.2, 294.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityNotificationTest.java`. Tests (~7): ALLOCATION_CHANGED sent to member on create, ALLOCATION_CHANGED sent on update, MEMBER_OVER_ALLOCATED sent to member + admins, LEAVE_CREATED sent when admin creates for member, LEAVE_CREATED NOT sent for self-service, notification metadata includes correct fields, no notification on delete (silent operation). |
| 294.8 | Write audit event integration tests | 294A | 294.4, 294.5, 294.6 | In same or separate test file. Tests (~5): MEMBER_CAPACITY_UPDATED audit on create, ALLOCATION_CREATED audit event with correct payload, ALLOCATION_UPDATED audit includes old and new hours, LEAVE_DELETED audit event, audit events have correct actor (createdBy). |
| 294.9 | Create settings capacity section | 294B | | New file: `frontend/components/capacity/default-capacity-settings.tsx`. "use client". Card in org settings page. Shows current default weekly capacity hours. Edit form: number Input (min 1, max 168, step 0.5). Save button calls updateSettings action. Label: "Default Weekly Capacity Hours" with helper text "Applied to team members without custom capacity settings". |
| 294.10 | Add capacity settings to settings page | 294B | 294.9 | Modify: settings page or layout (likely `frontend/app/(app)/org/[slug]/settings/page.tsx`). Add "Capacity" section with DefaultCapacitySettings component. Position: after existing settings sections. |
| 294.11 | Create capacity settings server action | 294B | | New file: `frontend/app/(app)/org/[slug]/settings/capacity-actions.ts`. "use server". Action: updateDefaultCapacityAction(hours: number). Calls OrgSettings API via backend, revalidates path. |
| 294.12 | Add capacity notification rendering | 294B | | Modify: notification rendering components (likely in `frontend/components/notifications/`). Add rendering for ALLOCATION_CHANGED, MEMBER_OVER_ALLOCATED, LEAVE_CREATED notification types. Display: icon, title, body text with project/member/date context. Pattern: existing notification type renderers. |
| 294.13 | Write settings + notification rendering tests | 294B | 294.9, 294.12 | New file: `frontend/__tests__/capacity/capacity-settings.test.tsx`. Tests (~4): default capacity settings renders current value, save updates value, ALLOCATION_CHANGED notification renders with project name, MEMBER_OVER_ALLOCATED notification renders with warning icon. |
| 294.14 | Add "Capacity" link to settings nav | 294B | | Modify settings navigation (if applicable). Add "Capacity" settings link. May be part of the existing settings page sections rather than a separate nav item. |

### Key Files

**Slice 294A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityNotificationTest.java`

**Slice 294A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocationService.java` -- add notifications + audit events
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/LeaveBlockService.java` -- add notifications + audit events
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/CapacityService.java` -- add audit events

**Slice 294B -- Create:**
- `frontend/components/capacity/default-capacity-settings.tsx`
- `frontend/app/(app)/org/[slug]/settings/capacity-actions.ts`
- `frontend/__tests__/capacity/capacity-settings.test.tsx`

**Slice 294B -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` -- add capacity settings section
- Notification rendering components (in `frontend/components/notifications/`) -- add capacity notification types

**Read for context (both slices):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- notification creation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- audit event creation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestService.java` -- notification wiring pattern
- `frontend/components/notifications/` -- notification type rendering pattern
- `frontend/app/(app)/org/[slug]/settings/page.tsx` -- settings page pattern

### Architecture Decisions

- **Notifications and audit in a separate epic**: Wiring notifications and audit events into existing services is a cross-cutting concern. Grouping it in its own epic keeps the core CRUD slices (Epic 290) focused on business logic.
- **Backend (294A) and frontend (294B) split**: The backend wiring must exist before the frontend can render notifications. 294A can run in parallel with the grid UI (292) since they are independent.
- **ALLOCATION_CHANGED not sent on delete**: Deleting an allocation is a "silent" operation -- no notification to the member. This avoids notification noise when managers are reorganizing allocations. The audit event still records the deletion.
- **LEAVE_CREATED only for admin-created leave**: Self-service leave creation does not trigger a notification (the member already knows). Only when an admin creates leave on behalf of a member is a notification sent.

---

### Critical Files for Implementation
List 3-5 files most critical for implementing this plan:
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase38-resource-planning-capacity.md` - Full architecture specification with entity models, API surface, sequence diagrams, migration SQL, and entity code patterns
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java` - Pattern to follow for CapacityService capacity resolution chain (effective-date resolution with fallback)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudgetService.java` - Pattern to follow for ResourceAllocationService CRUD with validation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` - Entity to extend with defaultWeeklyCapacityHours column
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/dashboard/team-workload-widget.tsx` - Pattern to follow for TeamCapacityWidget dashboard component