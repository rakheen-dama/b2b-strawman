# Phase 55 -- Legal Foundations: Court Calendar, Conflict Check & LSSA Tariff

Phase 55 is the multi-vertical architecture stress test. It builds three real legal modules (`court_calendar`, `conflict_check`, `lssa_tariff`) replacing two stubs, populates all legal pack content, introduces two new RBAC capabilities (`VIEW_LEGAL`, `MANAGE_LEGAL`), and proves multi-vertical coexistence with dedicated integration tests. Seven new tables, one InvoiceLine extension, six pack seeders, and four new frontend pages.

**Architecture doc**: `architecture/phase55-legal-foundations.md`

**ADRs**:
- [ADR-209](adr/ADR-209-court-date-vs-deadline-architecture.md) -- Separate entity model for court dates (event-based, not calculation-based)
- [ADR-210](adr/ADR-210-conflict-search-strategy.md) -- PostgreSQL pg_trgm with GIN index for fuzzy conflict search
- [ADR-211](adr/ADR-211-tariff-rate-integration-approach.md) -- Separate TariffItem entity with optional FK on InvoiceLine
- [ADR-212](adr/ADR-212-module-capability-mapping.md) -- Module-specific pair (VIEW_LEGAL, MANAGE_LEGAL)

**Dependencies on prior phases**:
- Phase 49: `VerticalModuleGuard`, `VerticalModuleRegistry`, `VerticalProfileRegistry`, `VerticalProfileService`, `ModuleGate` component, `OrgProfileProvider` -- module/profile infrastructure
- Phase 10: `Invoice`, `InvoiceLine`, `InvoiceLineType`, `InvoiceService` -- tariff line integration
- Phase 6.5: `NotificationService` -- court date reminders, prescription warnings
- Phase 6: `AuditEventBuilder`, `AuditEventService` -- audit events for all legal operations
- Phase 13: Schema-per-tenant isolation -- all new entities are plain `@Entity` with no multitenancy boilerplate
- Phase 51: `DeadlineTypeRegistry` pattern -- reference for `PrescriptionRuleRegistry` static utility

**Migration note**: The architecture doc specifies V74, but V74 through V82 are already taken. The actual tenant migration for this phase is **V83**. The global migration for pg_trgm is **V16**.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 397 | Foundation: V83 Migration + V16 Global + RBAC Capabilities + Module Registration | Backend | -- | M | 397A, 397B | **Done** (PR #841) |
| 398 | Court Date Entity + Service + Controller | Backend | 397 | M | 398A, 398B | **Done** (PR #842) |
| 399 | Prescription Tracker + Reminder Job | Backend | 398 | M | 399A, 399B | **Done** (PR #843) |
| 400 | Adverse Party Registry + CRUD | Backend | 397 | M | 400A, 400B | **Done** (PR #844) |
| 401 | Conflict Check Service + Search Algorithm | Backend | 400 | M | 401A, 401B | **Done** (PR #845) |
| 402 | Tariff Schedule Entity + Service + CRUD | Backend | 397 | M | 402A, 402B | **Done** (PR #846) |
| 403 | Invoice Tariff Integration + InvoiceLine Extension | Backend | 402 | S | 403A | |
| 404 | Legal Pack Content + Tariff Seed Data | Backend | 397 | M | 404A, 404B | |
| 405 | Frontend: Court Calendar + Prescription Pages | Frontend | 398, 399 | L | 405A, 405B | |
| 406 | Frontend: Conflict Check + Adverse Party Pages | Frontend | 400, 401 | L | 406A, 406B | |
| 407 | Frontend: Tariff Pages + Invoice Tariff Selector | Frontend | 402, 403 | M | 407A, 407B | |
| 408 | Frontend: Project Detail Tabs + Sidebar + Dashboard Widget | Frontend | 405, 406, 407 | M | 408A | |
| 409 | Multi-Vertical Coexistence Tests | Backend + Frontend | 397-408 | S | 409A | |

---

## Dependency Graph

```
BACKEND FOUNDATION
──────────────────────────────────────────────────────────────────

[E397A V83 tenant migration (7 tables + InvoiceLine extension),
 V16 global migration (pg_trgm), VIEW_LEGAL + MANAGE_LEGAL
 capabilities + default role mapping]
        |
[E397B Module registry updates (court_calendar, conflict_check
 active; lssa_tariff registered), legal-za profile update,
 stub controllers updated to new capabilities]
        |
        +──────────────────+──────────────────+──────────────────+
        |                  |                  |                  |
COURT CALENDAR         CONFLICT CHECK    TARIFF SCHEDULE   PACK CONTENT
(sequential)           (sequential)       (sequential)      (sequential)
────────────────       ────────────       ────────────      ─────────────
        |                  |                  |                  |
[E398A CourtDate       [E400A Adverse     [E402A Tariff     [E404A Field +
 entity + repo +        Party + Link       Schedule +        template +
 CourtCalendarService   entities + repos   TariffItem        clause +
 (CRUD + lifecycle)     + AdverseParty     entities + repos  compliance +
 + integration tests]   Service (CRUD)     + TariffService   automation
        |               + integration       (CRUD + clone)    packs + JSON
[E398B CourtCalendar    tests]             + integration      resources +
 Controller (7                              tests]            integration
 endpoints) +               |                  |              tests]
 integration tests]    [E400B Adverse     [E402B Tariff          |
        |              PartyController     Controller        [E404B Tariff
[E399A Prescription    (8 endpoints) +    (11 endpoints) +   seed data
 Tracker entity +      integration         integration       (LSSA 2024/25)
 PrescriptionRule      tests]              tests]            + seeder class
 Registry +                 |                  |             + integration
 PrescriptionTracker        |             [E403A InvoiceLine  tests]
 Service + integration [E401A Conflict     extension +
 tests]                 Check entity +     TARIFF line type
        |               ConflictCheck     + InvoiceService
[E399B CourtDate        Service (pg_trgm   extension +
 ReminderJob +          search algo) +     integration
 /upcoming endpoint     integration         tests]
 + integration tests]   tests]
                             |
                        [E401B Conflict
                         CheckController
                         (4 endpoints) +
                         integration
                         tests]
        |                    |                 |                  |
        +────────────────────+─────────────────+──────────────────+
                                    |
FRONTEND (requires backend epics)
──────────────────────────────────────────────────────────────────
                                    |
     +────────────────+────────────────+────────────────+
     |                |                |                |
[E405A Court         [E406A Conflict  [E407A Tariff    |
 Calendar page        Check page       Schedule page   |
 (calendar + list     (run-check       (browse + clone  |
 views), types,       form, result     + custom edit),  |
 actions, nav item,   display, check   types, actions,  |
 schemas, tests]      history),        nav item,        |
     |                types, actions,  schemas, tests]  |
[E405B Prescription   nav item,             |           |
 tab, court date      schemas, tests] [E407B Invoice    |
 dialog, PostponeDialog     |          tariff selector, |
 + CancelDialog +     [E406B Adverse   TariffLineDialog |
 OutcomeDialog +       Party page       component,      |
 tests]                (registry CRUD,   tests]         |
     |                 link/unlink),        |           |
     |                 tests]              |           |
     +────────────────+────────────────+───+           |
                           |                           |
                    [E408A Project detail               |
                     court dates + adverse              |
                     parties tabs (module-gated),       |
                     sidebar nav items,                 |
                     dashboard court dates widget,      |
                     tests]                             |
                           |                           |
                    [E409A Multi-vertical               |
                     coexistence tests                  |
                     (7 backend + 4 frontend)]          |
```

**Parallel opportunities**:
- After E397B: E398 (court calendar), E400 (adverse party), E402 (tariff), and E404 (pack content) can all run in parallel. They share only the V83 migration from E397A.
- E398A and E398B are sequential (controller depends on service).
- E399A depends on E398A (prescription tracker is part of court calendar module). E399B depends on E399A.
- E400A and E400B are sequential. E401A depends on E400A (conflict check searches adverse parties). E401B depends on E401A.
- E402A and E402B are sequential. E403A depends on E402A (invoice integration references tariff items).
- E404A and E404B are sequential (tariff seeder in 404B depends on pack infrastructure in 404A).
- Frontend epics: E405, E406, and E407 can run in parallel once their respective backend dependencies are met. E408A depends on E405, E406, E407. E409A depends on all.

---

## Implementation Order

### Stage 0: Backend Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 397 | 397A | V83 tenant migration (7 tables: `court_dates`, `prescription_trackers`, `adverse_parties`, `adverse_party_links`, `conflict_checks`, `tariff_schedules`, `tariff_items` + InvoiceLine extension), V16 global migration (`pg_trgm`), `VIEW_LEGAL` + `MANAGE_LEGAL` capability registration, default role mapping updates, unit tests (~4). Backend only. | **Done** (PR #841) |
| 0b | 397 | 397B | Update `VerticalModuleRegistry`: `court_calendar` and `conflict_check` status to `active`, register `lssa_tariff` module. Update `VerticalProfileRegistry`: `legal-za` profile `enabled_modules` to `["court_calendar", "conflict_check", "lssa_tariff"]`. Update stub controllers to use `VIEW_LEGAL`/`MANAGE_LEGAL`. Integration tests (~4). Backend only. | **Done** (PR #841) |

### Stage 1: Backend Domain Services (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 398 | 398A | `CourtDate` entity + `CourtDateRepository` + `CourtCalendarService` (create, update, postpone, cancel, recordOutcome, list, getById, module guard integration) + audit events. Integration tests (~8). Backend only. | **Done** (PR #842) |
| 1b (parallel) | 400 | 400A | `AdverseParty` entity + `AdversePartyLink` entity + repos + `AdversePartyService` (CRUD, link/unlink, listForProject, search by name, module guard) + audit events. Integration tests (~6). Backend only. | **Done** (PR #844) |
| 1c (parallel) | 402 | 402A | `TariffSchedule` entity + `TariffItem` entity + repos + `TariffService` (schedule CRUD, item CRUD, clone, search, active lookup, module guard, isSystem protection) + audit events. Integration tests (~6). Backend only. | **Done** (PR #846) |
| 1d (parallel) | 404 | 404A | `LegalFieldPackSeeder` + `LegalTemplatePackSeeder` + `LegalClausePackSeeder` + `LegalCompliancePackSeeder` + `LegalAutomationPackSeeder`. JSON resources: `field-packs/legal-za-customer.json`, `field-packs/legal-za-project.json`, `template-packs/legal-za/pack.json`, `clause-packs/legal-za-clauses/pack.json`, `compliance-packs/legal-za-onboarding/pack.json`, `automation-templates/legal-za.json`. Integration tests (~4). Backend only. |  |

### Stage 2: Backend Controllers + Advanced Features (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 398 | 398B | `CourtCalendarController` (7 endpoints: list, get, create, update, postpone, cancel, outcome) replacing stub. `@RequiresCapability` with `VIEW_LEGAL`/`MANAGE_LEGAL`. Integration tests (~6). Backend only. | **Done** (PR #842) |
| 2b (parallel) | 400 | 400B | `AdversePartyController` (8 endpoints: list, get, create, update, delete, link, unlink, listForProject) replacing stub. Integration tests (~5). Backend only. | **Done** (PR #844) |
| 2c (parallel) | 402 | 402B | `TariffController` (11 endpoints: schedule list/get/create/update/clone/active, item list/get/create/update/delete). Integration tests (~5). Backend only. | **Done** (PR #846) |
| 2d (parallel) | 404 | 404B | `LegalTariffSeeder` (LSSA 2024/2025 High Court P&P schedule, 18 items, `isSystem = true`). JSON resource: `tariff-seed/lssa-2024-2025-hc-pp.json`. Provisioning integration (legal-za profile triggers tariff seeding). Integration tests (~3). Backend only. |  |

### Stage 3: Backend Dependent Features (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 399 | 399A | `PrescriptionTracker` entity + `PrescriptionTrackerRepository` + `PrescriptionRuleRegistry` static utility + `PrescriptionTrackerService` (create with date calculation, update, interrupt, list) + `PrescriptionTrackerController` (5 endpoints) + audit events. Integration tests (~8). Backend only. | **Done** (PR #843) |
| 3b (parallel) | 401 | 401A | `ConflictCheck` entity + `ConflictCheckRepository` + `ConflictCheckService` (performCheck with pg_trgm fuzzy search + exact ID match + result classification + JSONB conflict details + audit trail). Native SQL queries for `similarity()`. Integration tests (~8). Backend only. | **Done** (PR #845) |
| 3c (parallel) | 403 | 403A | Extend `InvoiceLine` entity with `tariffItemId` (UUID) + `lineSource` (String). Add `TARIFF` to `InvoiceLineType` enum. Extend `InvoiceService.addLine()` for tariff lines with module guard. Extend `InvoiceLineResponse` DTO. Integration tests (~5). Backend only. |  |

### Stage 4: Backend Remaining Features

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 399 | 399B | `CourtDateReminderJob` (daily scheduled: iterate tenants, check module enabled, query upcoming court dates + expiring prescriptions, create notifications, update tracker status). `/api/court-calendar/upcoming` endpoint (combined dashboard data). Integration tests (~5). Backend only. | **Done** (PR #843) |
| 4b (parallel) | 401 | 401B | `ConflictCheckController` (4 endpoints: perform, list, get, resolve). Integration tests (~4). Backend only. | **Done** (PR #845) |

### Stage 5: Frontend Court Calendar + Prescription (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 405 | 405A | Court calendar page (`court-calendar/page.tsx`) replacing stub. Calendar/list views, TypeScript types, Zod schemas, server actions, nav item update in `nav-items.ts` with `requiredModule: "court_calendar"`. Frontend tests (~5). Frontend only. |  |
| 5b | 405 | 405B | `CreateCourtDateDialog`, `PostponeDialog`, `CancelDialog`, `OutcomeDialog`, `PrescriptionTab` (list + create + interrupt). Frontend tests (~5). Frontend only. |  |

### Stage 6: Frontend Conflict Check + Adverse Party + Tariffs (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a (parallel) | 406 | 406A | Conflict check page (`conflict-check/page.tsx`) replacing stub. Run-check form, result display (green/amber/red), check history list. TypeScript types, Zod schemas, server actions, nav item. Frontend tests (~5). Frontend only. |  |
| 6b (parallel) | 407 | 407A | Tariff schedule browser page (new: `legal/tariffs/page.tsx`). Schedule list, item browser with section grouping, search. TypeScript types, Zod schemas, server actions, nav item. Frontend tests (~4). Frontend only. |  |

### Stage 7: Frontend Remaining Pages (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 7a (parallel) | 406 | 406B | Adverse party registry page (new: `legal/adverse-parties/page.tsx`). CRUD table, link/unlink dialog, party detail view with linked matters. Frontend tests (~4). Frontend only. |  |
| 7b (parallel) | 407 | 407B | Invoice tariff selector: `TariffLineDialog` component (schedule picker, item browser, quantity input), "Add Tariff Items" button in invoice editor (module-gated). `ResolveConflictDialog` component for conflict check resolution. Frontend tests (~4). Frontend only. |  |

### Stage 8: Frontend Integration + Dashboard

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 8a | 408 | 408A | Project detail page: module-gated "Court Dates" and "Adverse Parties" tabs. Sidebar nav: module-gated legal items in Clients group. Dashboard: "Upcoming Court Dates" widget (module-gated). Frontend tests (~5). Frontend only. |  |

### Stage 9: Coexistence Tests

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 9a | 409 | 409A | 7 backend integration tests (accounting + legal tenant coexistence: independent provisioning, no cross-contamination, module guard isolation, InvoiceLine tariff FK null for accounting). 4 frontend tests (module-gated UI visibility, no legal nav for accounting profile, no accounting nav for legal profile, profile switch updates visible modules). Tests only. |  |

### Timeline

```
Stage 0:  [397A] -> [397B]                                              <- foundation (sequential)
Stage 1:  [398A]  //  [400A]  //  [402A]  //  [404A]                    <- domain services (parallel)
Stage 2:  [398B]  //  [400B]  //  [402B]  //  [404B]                    <- controllers (parallel)
Stage 3:  [399A]  //  [401A]  //  [403A]                                <- dependent services (parallel)
Stage 4:  [399B]  //  [401B]                                            <- remaining backend (parallel)
Stage 5:  [405A] -> [405B]                                              <- court calendar FE (sequential)
Stage 6:  [406A]  //  [407A]                                            <- conflict + tariff FE (parallel)
Stage 7:  [406B]  //  [407B]                                            <- remaining FE pages (parallel)
Stage 8:  [408A]                                                        <- integration FE
Stage 9:  [409A]                                                        <- coexistence tests
```

---

## Epic 397: Foundation -- V83 Migration + V16 Global + RBAC Capabilities + Module Registration

**Goal**: Lay the database and infrastructure foundation for all Phase 55 features. Create the V83 tenant migration with all 7 new tables and InvoiceLine extension. Create V16 global migration to enable pg_trgm. Register `VIEW_LEGAL` and `MANAGE_LEGAL` capabilities. Update module and profile registries. Update stub controllers to use new capabilities.

**References**: Architecture doc Sections 6 (migration), 9 (permission model), 3.6 (module guard); ADR-210 (pg_trgm), ADR-212 (capability mapping).

**Dependencies**: None (first epic).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **397A** | 397.1--397.6 | V83 tenant migration (7 tables + InvoiceLine extension + all indexes + constraints), V16 global migration (`CREATE EXTENSION IF NOT EXISTS pg_trgm`), `VIEW_LEGAL` and `MANAGE_LEGAL` added to `Capability` enum, default role mapping (Owner+Admin: both, Member: VIEW_LEGAL only), unit tests (~4). Backend only. | **Done** (PR #841) |
| **397B** | 397.7--397.12 | `VerticalModuleRegistry`: `court_calendar` + `conflict_check` set to active, `lssa_tariff` registered. `VerticalProfileRegistry`: `legal-za` profile `enabled_modules` updated. Stub controllers updated: `@RequiresCapability("VIEW_LEGAL")` on read, `@RequiresCapability("MANAGE_LEGAL")` on write. Integration tests (~4). Backend only. | **Done** (PR #841) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 397.1 | Create V83 tenant migration | 397A | -- | New file: `backend/src/main/resources/db/migration/tenant/V83__create_legal_foundation_tables.sql`. 8 DDL groups: (1) `court_dates` table with 4 indexes, (2) `prescription_trackers` table with 3 indexes, (3) `adverse_parties` table with GIN trigram indexes + B-tree partial indexes, (4) `adverse_party_links` table with unique constraint + 3 indexes, (5) `conflict_checks` table with 4 indexes, (6) `tariff_schedules` table with partial active index, (7) `tariff_items` table with schedule FK (CASCADE) + GIN trigram index on description, (8) `ALTER TABLE invoice_lines ADD COLUMN tariff_item_id UUID, ADD COLUMN line_source VARCHAR(20)` + partial index. Full SQL in architecture doc Section 6.1. Must be idempotent. Pattern: `V76__data_protection_foundation.sql`. |
| 397.2 | Create V16 global migration | 397A | -- | New file: `backend/src/main/resources/db/migration/global/V16__enable_pg_trgm.sql`. Content: `CREATE EXTENSION IF NOT EXISTS pg_trgm;`. This enables trigram similarity for fuzzy name matching in conflict checks (ADR-210). Pattern: existing global migrations in `db/migration/global/`. |
| 397.3 | Add `VIEW_LEGAL` and `MANAGE_LEGAL` to Capability enum | 397A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java`. Add two new enum values: `VIEW_LEGAL` (description: "View legal module data"), `MANAGE_LEGAL` (description: "Manage legal module data"). Pattern: existing capabilities in same file (e.g., `VIEW_COMPLIANCE`, `MANAGE_COMPLIANCE`). |
| 397.4 | Update default role capability mappings | 397A | 397.3 | Modify: role seed/configuration to include `VIEW_LEGAL` for Owner + Admin + Member, `MANAGE_LEGAL` for Owner + Admin only. The exact mechanism depends on how capabilities are mapped to default roles -- check `OrgRoleService` or the role seeder. Pattern: how `VIEW_COMPLIANCE`/`MANAGE_COMPLIANCE` were added (Phase 50). |
| 397.5 | Write unit test for capability registration | 397A | 397.3 | New test in existing test file or new `CapabilityTest.java`. 2 tests: (1) `Capability.VIEW_LEGAL` exists and is resolvable, (2) `Capability.MANAGE_LEGAL` exists and is resolvable. Pure unit tests, no Spring context. |
| 397.6 | Write migration smoke test | 397A | 397.1, 397.2 | Extend existing migration test or add test verifying V83 migration runs without error on a fresh tenant schema. 2 tests: (1) V83 creates all 7 tables, (2) V16 enables pg_trgm extension. Integration test with Testcontainers. Pattern: existing migration tests. |
| 397.7 | Update `VerticalModuleRegistry` -- activate court_calendar and conflict_check | 397B | 397.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java`. Change `court_calendar` and `conflict_check` status from `"stub"` to `"active"`. Pattern: how `regulatory_deadlines` was registered as `"active"` in Phase 51 (381.10). |
| 397.8 | Register `lssa_tariff` module in `VerticalModuleRegistry` | 397B | 397.1 | Modify: `VerticalModuleRegistry.java`. Add module: `id = "lssa_tariff"`, `name = "LSSA Tariff"`, `description = "LSSA tariff schedule management for legal billing"`, `defaultEnabledFor = ["legal-za"]`, `navItems = [{ path: "/legal/tariffs", label: "Tariffs", zone: "finance" }]`, `status = "active"`. |
| 397.9 | Update `legal-za` profile in `VerticalProfileRegistry` | 397B | 397.7, 397.8 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java`. Update `legal-za` profile `enabled_modules` to `["court_calendar", "conflict_check", "lssa_tariff"]` (removing `trust_accounting` stub from default enabled set). |
| 397.10 | Update stub controllers to use new capabilities | 397B | 397.3 | Modify: `CourtCalendarController.java` -- change `@RequiresCapability("PROJECT_MANAGEMENT")` to `@RequiresCapability("VIEW_LEGAL")` on status endpoint. Modify: `ConflictCheckController.java` -- same change. Modify: `TrustAccountingController.java` -- change to `@RequiresCapability("VIEW_LEGAL")` (remains stub). These controllers will be fully replaced by later epics but the capability must be correct for integration tests. |
| 397.11 | Write integration test for module registration | 397B | 397.7, 397.8 | 2 tests: (1) `VerticalModuleRegistry.getModule("lssa_tariff")` returns active module with correct nav items, (2) `VerticalModuleRegistry.getModule("court_calendar")` returns active (not stub). Pattern: Phase 51 module registration test (381.13). |
| 397.12 | Write integration test for profile update | 397B | 397.9 | 2 tests: (1) `legal-za` profile includes `court_calendar`, `conflict_check`, `lssa_tariff` in enabled modules, (2) `legal-za` profile does NOT include `trust_accounting` in default enabled modules. |

### Key Files

**Slice 397A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V83__create_legal_foundation_tables.sql`
- `backend/src/main/resources/db/migration/global/V16__enable_pg_trgm.sql`

**Slice 397A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` -- Add VIEW_LEGAL, MANAGE_LEGAL
- Default role capability mapping (OrgRoleService or seeder) -- Add new capabilities to default roles

**Slice 397B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java` -- Activate 2 modules, register 1 new
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java` -- Update legal-za profile
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarController.java` -- Update capability
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckController.java` -- Update capability
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingController.java` -- Update capability

**Slice 397A/397B -- Read for context:**
- `backend/src/main/resources/db/migration/tenant/V76__data_protection_foundation.sql` -- Migration pattern reference
- `backend/src/main/resources/db/migration/global/V15__create_access_requests.sql` -- Global migration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java` -- Module registration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` -- Capability enum pattern

### Architecture Decisions

- **Single V83 migration for all DDL**: All Phase 55 DDL changes go into one tenant migration. This avoids ordering issues between parallel epics. The architecture doc specifies V74 but V74-V82 are taken -- actual migration is V83.
- **V16 global migration for pg_trgm**: The pg_trgm extension must be enabled at the database level (not per-schema) before tenant migrations create GIN trigram indexes. Global V16 runs before V83.
- **Capabilities registered before module activation**: The capability enum additions and default role mappings must be in place before any endpoint uses `@RequiresCapability("VIEW_LEGAL")`. This is why 397A (capabilities) precedes 397B (module activation + controller updates).

---

## Epic 398: Court Date Entity + Service + Controller

**Goal**: Build the core court date CRUD with full lifecycle management (create, update, postpone, cancel, record outcome). Replace the `CourtCalendarController` stub with a real implementation.

**References**: Architecture doc Sections 2.1 (CourtDate entity), 3.1 (CRUD + lifecycle flows), 4.1 (endpoints); ADR-209 (separate entity model).

**Dependencies**: Epic 397 (V83 migration, capabilities, module registration).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **398A** | 398.1--398.6 | `CourtDate` entity + `CourtDateRepository` (with custom queries for date range, status, project, customer filtering) + `CourtCalendarService` (create, update, postpone, cancel, recordOutcome, list with filters, getById, module guard, audit events). Integration tests (~8). Backend only. | **Done** (PR #842) |
| **398B** | 398.7--398.11 | Replace stub `CourtCalendarController` with full implementation (7 endpoints: GET list, GET by id, POST create, PUT update, POST postpone, POST cancel, POST outcome). `@RequiresCapability` on all endpoints. Integration tests (~6). Backend only. | **Done** (PR #842) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 398.1 | Create `CourtDate` entity | 398A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDate.java`. Plain `@Entity` + `@Table(name = "court_dates")`. Fields per architecture doc Section 2.1: id (UUID), projectId, customerId, dateType (String), scheduledDate (LocalDate), scheduledTime (LocalTime), courtName, courtReference, judgeMagistrate, description, status (String, default "SCHEDULED"), outcome, reminderDays (int, default 7), createdBy, createdAt, updatedAt. No `@Filter`, no `tenant_id`. Pattern: `deadline/FilingStatus.java`. |
| 398.2 | Create `CourtDateRepository` | 398A | 398.1 | New file: `verticals/legal/courtcalendar/CourtDateRepository.java`. Extends `JpaRepository<CourtDate, UUID>`. Custom queries: (1) `findByProjectIdOrderByScheduledDateAsc(UUID)` -- matter detail tab, (2) `findByCustomerIdOrderByScheduledDateAsc(UUID)` -- customer filter, (3) `@Query` for date range + status + dateType filtering (paginated, all params optional), (4) `findByStatusInAndScheduledDateBetween(List<String>, LocalDate, LocalDate)` -- for reminder job. Pattern: `deadline/FilingStatusRepository.java`. |
| 398.3 | Create `CourtCalendarService` | 398A | 398.1, 398.2 | New file: `verticals/legal/courtcalendar/CourtCalendarService.java`. Spring `@Service`. Constructor-injected: `CourtDateRepository`, `VerticalModuleGuard`, `ProjectRepository` (to resolve customerId from projectId), `AuditEventService`. Methods: (1) `createCourtDate(request, memberId)` -- moduleGuard.requireModule("court_calendar"), resolve customerId, validate dateType enum, save, audit COURT_DATE_CREATED, (2) `updateCourtDate(id, request)` -- basic field update, (3) `postponeCourtDate(id, newDate, reason)` -- validate status is SCHEDULED, set POSTPONED, audit, (4) `cancelCourtDate(id, reason)` -- validate SCHEDULED/POSTPONED, set CANCELLED, audit, (5) `recordOutcome(id, outcome)` -- validate SCHEDULED/POSTPONED, set HEARD, audit, (6) `list(filters, pageable)` -- delegated repo query, (7) `getById(id)` -- find or throw ResourceNotFoundException. DTO records: `CreateCourtDateRequest`, `UpdateCourtDateRequest`, `PostponeRequest`, `CancelRequest`, `OutcomeRequest`, `CourtDateResponse`, `CourtDateFilters`. Pattern: `deadline/FilingStatusService.java`. |
| 398.4 | Implement state transition validation | 398A | 398.3 | Within `CourtCalendarService`. Valid transitions per architecture doc: SCHEDULED -> POSTPONED/HEARD/CANCELLED, POSTPONED -> HEARD/CANCELLED. Throw `InvalidStateException` for invalid transitions (e.g., HEARD -> CANCELLED). Private method `validateTransition(currentStatus, targetStatus)`. |
| 398.5 | Write integration tests for CourtCalendarService | 398A | 398.3, 398.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarServiceTest.java`. 8 tests: (1) create court date saves with SCHEDULED status, (2) create resolves customerId from project, (3) postpone updates date and sets POSTPONED, (4) postpone fails on HEARD status, (5) cancel sets CANCELLED, (6) recordOutcome sets HEARD, (7) list filters by date range, (8) create emits COURT_DATE_CREATED audit event. Use `@SpringBootTest` + `TestcontainersConfiguration`. Bind `RequestScopes.TENANT_ID` via `ScopedValue.where()`. Create test project + customer via test factories. |
| 398.6 | Write integration test for module guard | 398A | 398.3 | 1 additional test in same file: service throws `ModuleNotEnabledException` when `court_calendar` module is not enabled for the tenant. |
| 398.7 | Replace stub `CourtCalendarController` with full implementation | 398B | 398A | Modify: `verticals/legal/courtcalendar/CourtCalendarController.java`. Remove stub `/status` endpoint. Add 7 endpoints: `GET /api/court-dates` (@RequiresCapability("VIEW_LEGAL"), paginated, filter params), `GET /api/court-dates/{id}` (VIEW_LEGAL), `POST /api/court-dates` (MANAGE_LEGAL), `PUT /api/court-dates/{id}` (MANAGE_LEGAL), `POST /api/court-dates/{id}/postpone` (MANAGE_LEGAL), `POST /api/court-dates/{id}/cancel` (MANAGE_LEGAL), `POST /api/court-dates/{id}/outcome` (MANAGE_LEGAL). Each method is a one-liner delegating to `CourtCalendarService`. Pattern: `deadline/DeadlineController` (Phase 51). **Thin controller discipline**: no business logic, no conditional logic, pure delegation. |
| 398.8 | Define request/response DTO records | 398B | 398.7 | DTO records can be nested in controller or in `dto/` sub-package. `CreateCourtDateRequest(UUID projectId, String dateType, LocalDate scheduledDate, LocalTime scheduledTime, String courtName, String courtReference, String judgeMagistrate, String description, Integer reminderDays)`. `CourtDateResponse` with all fields + projectName + customerName. `PostponeRequest(LocalDate newDate, String reason)`. `CancelRequest(String reason)`. `OutcomeRequest(String outcome)`. Pattern: existing controller DTOs. |
| 398.9 | Write controller integration tests -- CRUD | 398B | 398.7 | New file: `verticals/legal/courtcalendar/CourtCalendarControllerTest.java`. 4 tests: (1) POST creates court date and returns 201, (2) GET list returns paginated results, (3) GET by id returns court date detail, (4) PUT updates court date fields. Use MockMvc + JWT mock with member having MANAGE_LEGAL capability. |
| 398.10 | Write controller integration tests -- lifecycle | 398B | 398.7 | 2 additional tests: (1) POST postpone returns updated date with POSTPONED status, (2) POST outcome returns HEARD status with outcome text. |
| 398.11 | Write controller authorization test | 398B | 398.7 | 1 test: POST /api/court-dates with a member lacking MANAGE_LEGAL returns 403. Verifies `@RequiresCapability` is correctly applied. |

### Key Files

**Slice 398A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarServiceTest.java`

**Slice 398B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarController.java` -- Full replacement of stub

**Slice 398B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarControllerTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/FilingStatus.java` -- Entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/FilingStatusService.java` -- Service pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectRepository.java` -- For resolving customerId from project

### Architecture Decisions

- **`customerId` denormalized from project**: Court dates store `customerId` directly to avoid three-table joins in firm-wide calendar queries. Service resolves `customerId` from the project on creation.
- **State transition validation in service, not entity**: The service validates transitions because the rules involve cross-cutting concerns (audit events, notifications). The entity is a plain data holder.
- **No reverse transitions**: Once HEARD or CANCELLED, a court date is final. A new court date must be created for subsequent appearances.

---

## Epic 399: Prescription Tracker + Reminder Job

**Goal**: Build prescription tracking with statutory period calculation and a daily scheduled job for court date reminders and prescription expiry warnings.

**References**: Architecture doc Sections 2.2 (PrescriptionTracker entity), 3.2 (tracking + warning flow), 3.3 (CourtDateReminderJob), 4.1 (endpoints); ADR-209.

**Dependencies**: Epic 398 (CourtDate entity -- reminder job queries both court dates and prescription trackers).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **399A** | 399.1--399.7 | `PrescriptionTracker` entity + `PrescriptionTrackerRepository` + `PrescriptionRuleRegistry` static utility (6 prescription types with period years) + `PrescriptionTrackerService` (create with date calculation, update, interrupt, list) + `PrescriptionTrackerController` (5 endpoints). Integration tests (~8). Backend only. | **Done** (PR #843) |
| **399B** | 399.8--399.12 | `CourtDateReminderJob` (daily scheduled: iterate tenants, query upcoming court dates + expiring prescriptions, create notifications, update tracker status WARNED/EXPIRED). `/api/court-calendar/upcoming` combined dashboard endpoint. Integration tests (~5). Backend only. | **Done** (PR #843) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 399.1 | Create `PrescriptionTracker` entity | 399A | -- | New file: `verticals/legal/courtcalendar/PrescriptionTracker.java`. Plain `@Entity` + `@Table(name = "prescription_trackers")`. Fields per architecture doc Section 2.2: id, projectId, customerId, causeOfActionDate, prescriptionType, customYears, prescriptionDate, interruptionDate, interruptionReason, status (default "RUNNING"), notes, createdBy, createdAt, updatedAt. Pattern: `CourtDate.java` (just created in 398A). |
| 399.2 | Create `PrescriptionTrackerRepository` | 399A | 399.1 | New file: `verticals/legal/courtcalendar/PrescriptionTrackerRepository.java`. Custom queries: (1) `findByProjectIdOrderByPrescriptionDateAsc(UUID)`, (2) `findByStatusInAndPrescriptionDateBetween(List<String>, LocalDate, LocalDate)` -- for warning job, (3) filtered list query (status, customerId, projectId). |
| 399.3 | Create `PrescriptionRuleRegistry` static utility | 399A | -- | New file: `verticals/legal/courtcalendar/PrescriptionRuleRegistry.java`. Static utility (not a Spring bean). `getPeriodYears(String prescriptionType)` switch expression: GENERAL_3Y/DELICT_3Y/CONTRACT_3Y -> 3, DEBT_6Y -> 6, MORTGAGE_30Y -> 30, CUSTOM -> throws. `calculatePrescriptionDate(LocalDate causeOfActionDate, String type, Integer customYears)`. Full implementation in architecture doc Section 3.2. Pattern: `deadline/DeadlineTypeRegistry.java`. |
| 399.4 | Create `PrescriptionTrackerService` | 399A | 399.1-399.3 | New file: `verticals/legal/courtcalendar/PrescriptionTrackerService.java`. Methods: (1) `create(request, memberId)` -- moduleGuard("court_calendar"), resolve customerId, calculate prescriptionDate via PrescriptionRuleRegistry, save, audit, (2) `update(id, request)` -- basic field update, (3) `interrupt(id, date, reason)` -- validate status RUNNING, set INTERRUPTED, audit, (4) `list(filters, pageable)`, (5) `getById(id)`. DTO records for request/response. |
| 399.5 | Create `PrescriptionTrackerController` | 399A | 399.4 | New file: `verticals/legal/courtcalendar/PrescriptionTrackerController.java`. 5 endpoints: `GET /api/prescription-trackers` (VIEW_LEGAL, paginated), `GET /api/prescription-trackers/{id}` (VIEW_LEGAL), `POST /api/prescription-trackers` (MANAGE_LEGAL), `PUT /api/prescription-trackers/{id}` (MANAGE_LEGAL), `POST /api/prescription-trackers/{id}/interrupt` (MANAGE_LEGAL). Thin delegation to service. |
| 399.6 | Write unit tests for PrescriptionRuleRegistry | 399A | 399.3 | New file: `PrescriptionRuleRegistryTest.java`. 4 unit tests: (1) GENERAL_3Y returns 3, (2) MORTGAGE_30Y returns 30, (3) CUSTOM with 5 years calculates correctly, (4) CUSTOM with null years throws. No Spring context. |
| 399.7 | Write integration tests for PrescriptionTrackerService | 399A | 399.4, 399.5 | New file: `PrescriptionTrackerServiceTest.java`. 4 integration tests: (1) create calculates prescription date from cause of action + type, (2) interrupt sets INTERRUPTED status, (3) interrupt fails on already-interrupted tracker, (4) controller POST returns 201 with calculated date. |
| 399.8 | Create `CourtDateReminderJob` | 399B | -- | New file: `verticals/legal/courtcalendar/CourtDateReminderJob.java`. `@Component` with `@Scheduled(cron = "${court.reminder.cron:0 0 6 * * *}")`. Iterate all tenant schemas via `OrgSchemaMappingRepository.findAll()`. For each: bind `RequestScopes.TENANT_ID`, check module enabled, query upcoming court dates needing reminders, query prescription trackers approaching expiry (90d/30d/7d thresholds). Create notifications via `NotificationService`. Update prescription tracker status (RUNNING -> WARNED on first 90d warning, RUNNING/WARNED -> EXPIRED when past due). Pattern: `automation/FieldDateScannerJob.java`. |
| 399.9 | Add reminder idempotency check | 399B | 399.8 | Within `CourtDateReminderJob`. Check if a notification with matching reference_type + reference_id has already been dispatched for a specific court_date/date combination. Skip if already sent. Query `NotificationRepository` or use a lightweight marker. |
| 399.10 | Create `/api/court-calendar/upcoming` endpoint | 399B | 399.8 | Add to `CourtCalendarController`: `GET /api/court-calendar/upcoming` (VIEW_LEGAL). Returns combined data: upcoming court dates (next 30 days, status SCHEDULED/POSTPONED) + prescription warnings (trackers with status RUNNING/WARNED where prescriptionDate within 90 days). Service method `getUpcoming()` in `CourtCalendarService`. For dashboard widget consumption. |
| 399.11 | Write integration tests for reminder job | 399B | 399.8, 399.9 | 3 tests: (1) job creates notification for court date within reminder window, (2) job skips court date that already had a reminder, (3) job updates prescription tracker status to WARNED at 90 days. |
| 399.12 | Write integration test for upcoming endpoint | 399B | 399.10 | 2 tests: (1) GET /api/court-calendar/upcoming returns court dates within 30 days, (2) returns prescription warnings within 90 days. |

### Key Files

**Slice 399A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionTracker.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionTrackerRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionRuleRegistry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionTrackerService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionTrackerController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionRuleRegistryTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionTrackerServiceTest.java`

**Slice 399B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDateReminderJob.java`

**Slice 399B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarController.java` -- Add `/upcoming` endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarService.java` -- Add `getUpcoming()` method

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/FieldDateScannerJob.java` -- Scheduled job pattern (tenant iteration, ScopedValue binding)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineTypeRegistry.java` -- Static utility pattern for PrescriptionRuleRegistry
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- Creating notifications

### Architecture Decisions

- **PrescriptionRuleRegistry is a static utility, not a Spring bean**: Prescription periods are statutory constants defined in the Prescription Act. They are not tenant-configurable. Same pattern as DeadlineTypeRegistry (Phase 51).
- **prescriptionDate is stored, not calculated on-the-fly**: Unlike accounting deadlines, each prescription tracker is explicitly created for a specific claim. Storing the date enables efficient "upcoming expiry" queries across all matters without recalculation.
- **Reminder job iterates all tenants**: Same pattern as `FieldDateScannerJob`. Checks module enabled per tenant to skip non-legal tenants.
- **Interruption does not recalculate**: Marking a prescription as interrupted is informational. If the firm needs to track a restarted prescription, they create a new tracker. This avoids complex recalculation logic in Phase 55.

---

## Epic 400: Adverse Party Registry + CRUD

**Goal**: Build the adverse party registry for conflict-of-interest tracking. CRUD operations for adverse parties and their links to matters.

**References**: Architecture doc Sections 2.3 (AdverseParty), 2.4 (AdversePartyLink), 4.3 (endpoints).

**Dependencies**: Epic 397 (V83 migration).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **400A** | 400.1--400.6 | `AdverseParty` entity + `AdversePartyLink` entity + repos + `AdversePartyService` (CRUD, link/unlink, listForProject, fuzzy name search, module guard, delete protection) + audit events. Integration tests (~6). Backend only. | **Done** (PR #844) |
| **400B** | 400.7--400.11 | `AdversePartyController` (8 endpoints: list, get, create, update, delete, link, unlink, listForProject) replacing stub. Integration tests (~5). Backend only. | **Done** (PR #844) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 400.1 | Create `AdverseParty` entity | 400A | -- | New file: `verticals/legal/conflictcheck/AdverseParty.java`. Plain `@Entity` + `@Table(name = "adverse_parties")`. Fields: id, name, idNumber, registrationNumber, partyType, aliases, notes, createdAt, updatedAt. Pattern: `CourtDate.java`. |
| 400.2 | Create `AdversePartyLink` entity | 400A | 400.1 | New file: `verticals/legal/conflictcheck/AdversePartyLink.java`. Fields: id, adversePartyId, projectId, customerId, relationship, description, createdAt. `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"adverse_party_id", "project_id"}))`. |
| 400.3 | Create repositories | 400A | 400.1, 400.2 | `AdversePartyRepository.java`: extends `JpaRepository<AdverseParty, UUID>`. Custom queries: `findByIdNumber(String)`, `findByRegistrationNumber(String)`, native query `findBySimilarName(String name, double threshold)` using `similarity()` function, native query `findByAliasContaining(String name, double threshold)`. `AdversePartyLinkRepository.java`: `findByProjectId(UUID)`, `findByAdversePartyId(UUID)`, `existsByAdversePartyId(UUID)` (for delete protection), `findByAdversePartyIdAndProjectId(UUID, UUID)`. |
| 400.4 | Create `AdversePartyService` | 400A | 400.3 | New file: `verticals/legal/conflictcheck/AdversePartyService.java`. Methods: (1) `create(request)` -- moduleGuard("conflict_check"), save, audit, (2) `update(id, request)`, (3) `delete(id)` -- reject if active links exist via `existsByAdversePartyId()`, (4) `link(adversePartyId, projectId, customerId, relationship, description)` -- create link, audit, (5) `unlink(linkId)` -- delete link, audit, (6) `listForProject(projectId)` -- returns adverse parties linked to a project with relationship details, (7) `list(search, partyType, pageable)` -- paginated with optional fuzzy search. DTO records for request/response. |
| 400.5 | Write integration tests for AdversePartyService | 400A | 400.4 | New file: `AdversePartyServiceTest.java`. 6 tests: (1) create saves adverse party, (2) link creates adverse_party_link record, (3) link duplicate (same party + project) throws ResourceConflictException, (4) delete fails when active links exist, (5) delete succeeds when no links, (6) fuzzy name search returns parties above threshold. Use `@SpringBootTest` + `TestcontainersConfiguration`. |
| 400.6 | Write integration test for module guard | 400A | 400.4 | 1 test: service throws when `conflict_check` module is not enabled. |
| 400.7 | Create `AdversePartyController` | 400B | 400A | New file: `verticals/legal/conflictcheck/AdversePartyController.java`. 8 endpoints replacing/extending the stub: `GET /api/adverse-parties` (VIEW_LEGAL, paginated, search param), `GET /api/adverse-parties/{id}` (VIEW_LEGAL), `POST /api/adverse-parties` (MANAGE_LEGAL), `PUT /api/adverse-parties/{id}` (MANAGE_LEGAL), `DELETE /api/adverse-parties/{id}` (MANAGE_LEGAL), `POST /api/adverse-parties/{id}/links` (MANAGE_LEGAL), `DELETE /api/adverse-party-links/{linkId}` (MANAGE_LEGAL), `GET /api/projects/{id}/adverse-parties` (VIEW_LEGAL). Thin delegation. |
| 400.8 | Define request/response DTOs | 400B | 400.7 | `CreateAdversePartyRequest(String name, String idNumber, String registrationNumber, String partyType, String aliases, String notes)`. `AdversePartyResponse` with all fields + linked matter count. `LinkRequest(UUID projectId, UUID customerId, String relationship, String description)`. `AdversePartyLinkResponse` with party name + project name + customer name. |
| 400.9 | Write controller integration tests -- CRUD | 400B | 400.7 | 3 tests: (1) POST creates adverse party, (2) GET list with search returns fuzzy matches, (3) DELETE with active links returns 409. |
| 400.10 | Write controller integration tests -- links | 400B | 400.7 | 2 tests: (1) POST link creates relationship, (2) GET /api/projects/{id}/adverse-parties returns linked parties. |
| 400.11 | Write controller authorization test | 400B | 400.7 | 1 test: DELETE /api/adverse-parties/{id} with VIEW_LEGAL only returns 403. |

### Key Files

**Slice 400A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdverseParty.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdversePartyLink.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdversePartyRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdversePartyLinkRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdversePartyService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdversePartyServiceTest.java`

**Slice 400B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdversePartyController.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckController.java` -- Existing stub to reference (will be modified in Epic 401)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` -- For understanding customer search pattern

### Architecture Decisions

- **Delete protection for adverse parties with active links**: An adverse party cannot be deleted while it has active `AdversePartyLink` records. This prevents orphaned references in conflict check audit trails. The firm must unlink the party from all matters first.
- **Native SQL for pg_trgm queries**: JPA/JPQL does not support the `similarity()` function. Use `@Query(nativeQuery = true)` with `SELECT *, similarity(lower(name), lower(:search)) AS score FROM adverse_parties WHERE similarity(lower(name), lower(:search)) > :threshold ORDER BY score DESC`.
- **Unique constraint on (adverse_party_id, project_id)**: Prevents linking the same adverse party to the same matter twice. Different matters can link the same party.

---

## Epic 401: Conflict Check Service + Search Algorithm

**Goal**: Build the conflict-of-interest check system with pg_trgm fuzzy name search, exact ID matching, result classification, JSONB conflict detail storage, and audit trail.

**References**: Architecture doc Sections 2.5 (ConflictCheck entity), 3.4 (search algorithm), 4.2 (endpoints), 5.1 (sequence diagram); ADR-210 (pg_trgm strategy).

**Dependencies**: Epic 400 (AdverseParty + AdversePartyLink entities and service).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **401A** | 401.1--401.7 | `ConflictCheck` entity + `ConflictCheckRepository` + `ConflictCheckService` (performCheck with pg_trgm fuzzy search, exact ID matching, result classification CONFLICT_FOUND/POTENTIAL_CONFLICT/NO_CONFLICT, JSONB conflict detail assembly, resolve with resolution tracking, list history). Native SQL queries for `similarity()`. Integration tests (~8). Backend only. | **Done** (PR #845) |
| **401B** | 401.8--401.12 | Replace stub `ConflictCheckController` (4 endpoints: POST perform, GET list, GET by id, POST resolve). `@RequiresCapability` on all endpoints. Integration tests (~4). Backend only. | **Done** (PR #845) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 401.1 | Create `ConflictCheck` entity | 401A | -- | New file: `verticals/legal/conflictcheck/ConflictCheck.java`. Plain `@Entity` + `@Table(name = "conflict_checks")`. Fields per architecture doc Section 2.5: id, checkedName, checkedIdNumber, checkedRegistrationNumber, checkType, result, conflictsFound (JSONB via `@JdbcTypeCode(SqlTypes.JSON)`), resolution, resolutionNotes, waiverDocumentId, checkedBy, resolvedBy, checkedAt, resolvedAt, customerId, projectId. Pattern: entities with JSONB fields. |
| 401.2 | Create `ConflictCheckRepository` | 401A | 401.1 | Custom queries: (1) paginated list with filters (result, checkType, checkedBy, dateFrom, dateTo), (2) `findById(UUID)`. |
| 401.3 | Add fuzzy search queries to `AdversePartyRepository` | 401A | -- | Modify: `AdversePartyRepository.java` (if not already done in 400A). Ensure native queries exist: `findBySimilarName(String, double)`, `findByAliasContaining(String, double)`, `findByIdNumber(String)`, `findByRegistrationNumber(String)`. Also need `CustomerRepository` native queries for `findBySimilarName(String, double)`, `findByIdNumber(String)`, `findByRegistrationNumber(String)`. |
| 401.4 | Create `ConflictCheckService` | 401A | 401.1-401.3 | New file: `verticals/legal/conflictcheck/ConflictCheckService.java`. Core method `performCheck(request, memberId)`: (1) moduleGuard("conflict_check"), (2) Step 1: exact ID matches (AdversePartyRepo + CustomerRepo), (3) Step 2: fuzzy name matches (pg_trgm similarity > 0.3), (4) Step 3: alias matches, (5) Step 4: classify result (exact match or score > 0.6 = CONFLICT_FOUND, 0.3-0.6 = POTENTIAL_CONFLICT, else NO_CONFLICT), (6) Step 5: build `conflictsFound` JSONB array with adversePartyId, name, projectId, projectName, customerId, customerName, relationship, matchType, similarityScore, explanation, (7) persist ConflictCheck, (8) audit CONFLICT_CHECK_PERFORMED. Also: `resolve(id, resolution, notes, memberId)` -- update resolution fields, audit. `list(filters, pageable)`, `getById(id)`. Architecture doc Section 3.4 has full algorithm. |
| 401.5 | Write integration tests for conflict check -- fuzzy match | 401A | 401.4 | 4 tests: (1) exact ID number match returns CONFLICT_FOUND, (2) name similarity > 0.6 returns CONFLICT_FOUND, (3) name similarity 0.3-0.6 returns POTENTIAL_CONFLICT, (4) no match above 0.3 returns NO_CONFLICT. Setup: create adverse parties with known names, perform checks with variations. |
| 401.6 | Write integration tests for conflict check -- details | 401A | 401.4 | 2 tests: (1) conflictsFound JSONB contains correct adversePartyId and matchType, (2) check against customer table (not just adverse parties) also finds matches. |
| 401.7 | Write integration tests for conflict check -- audit and resolution | 401A | 401.4 | 2 tests: (1) performCheck creates audit event CONFLICT_CHECK_PERFORMED, (2) resolve updates resolution and resolvedBy fields. |
| 401.8 | Replace stub `ConflictCheckController` | 401B | 401A | Modify: `verticals/legal/conflictcheck/ConflictCheckController.java`. Remove stub. Add 4 endpoints: `POST /api/conflict-checks` (MANAGE_LEGAL -- performs check and returns result), `GET /api/conflict-checks` (VIEW_LEGAL -- paginated history), `GET /api/conflict-checks/{id}` (VIEW_LEGAL), `POST /api/conflict-checks/{id}/resolve` (MANAGE_LEGAL). Thin delegation. |
| 401.9 | Define DTOs | 401B | 401.8 | `PerformConflictCheckRequest(String checkedName, String checkedIdNumber, String checkedRegistrationNumber, String checkType, UUID customerId, UUID projectId)`. `ConflictCheckResponse` with all fields. `ResolveRequest(String resolution, String resolutionNotes, UUID waiverDocumentId)`. |
| 401.10 | Write controller integration tests | 401B | 401.8 | 3 tests: (1) POST /api/conflict-checks performs check and returns result with conflicts, (2) GET list returns paginated check history, (3) POST resolve updates conflict check resolution. |
| 401.11 | Write controller authorization test | 401B | 401.8 | 1 test: POST /api/conflict-checks with VIEW_LEGAL only returns 403 (requires MANAGE_LEGAL). |
| 401.12 | Add customer fuzzy search queries | 401B | 401.3 | If not done in 401.3: Modify `CustomerRepository.java` to add native `@Query` methods for `findBySimilarName(String, double)` and `findByIdNumberExact(String)`. These are used by ConflictCheckService to search existing clients for conflicts. |

### Key Files

**Slice 401A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheck.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckServiceTest.java`

**Slice 401A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdversePartyRepository.java` -- Ensure fuzzy search queries exist
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` -- Add fuzzy search queries

**Slice 401B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckController.java` -- Replace stub

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- Audit event pattern
- `adr/ADR-210-conflict-search-strategy.md` -- Search algorithm and threshold decisions

### Architecture Decisions

- **pg_trgm native SQL queries**: The `similarity()` function is PostgreSQL-specific and not available in JPQL. All fuzzy search queries use `@Query(nativeQuery = true)`.
- **Threshold 0.3 for broad matching, 0.6 for definitive conflict**: These thresholds balance false positives (too many matches) vs. false negatives (missed conflicts). The firm can adjust behavior by reviewing POTENTIAL_CONFLICT results.
- **ConflictCheck is immutable after creation**: The check record captures a snapshot of what was found. Only the `resolution` fields are mutable (to record the firm's decision).
- **Search across both adverse parties AND customers**: A new client check must verify the prospective client is not already an adverse party in an existing matter. This requires searching the customer table as well.

---

## Epic 402: Tariff Schedule Entity + Service + CRUD

**Goal**: Build the LSSA tariff schedule and item management system with full CRUD, clone-and-edit pattern, and system/custom schedule distinction.

**References**: Architecture doc Sections 2.6 (TariffSchedule), 2.7 (TariffItem), 4.4 (endpoints); ADR-211 (separate TariffItem entity).

**Dependencies**: Epic 397 (V83 migration).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **402A** | 402.1--402.6 | `TariffSchedule` entity + `TariffItem` entity + repos + `TariffService` (schedule CRUD, item CRUD, clone-and-edit, active lookup by category+courtLevel, search items by description, isSystem protection, module guard). Integration tests (~6). Backend only. | **Done** (PR #846) |
| **402B** | 402.7--402.11 | `TariffController` (11 endpoints: schedule list/get/create/update/clone/active, item list/get/create/update/delete). Integration tests (~5). Backend only. | **Done** (PR #846) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 402.1 | Create `TariffSchedule` entity | 402A | -- | New file: `verticals/legal/tariff/TariffSchedule.java`. Plain `@Entity` + `@Table(name = "tariff_schedules")`. Fields: id, name, category, courtLevel, effectiveFrom (LocalDate), effectiveTo (LocalDate, nullable), isActive (boolean, default true), isSystem (boolean, default false), source, createdAt, updatedAt. `@OneToMany(mappedBy = "schedule", cascade = ALL, orphanRemoval = true)` for items. Pattern: `template/DocumentTemplate.java` (has isSystem + clone). |
| 402.2 | Create `TariffItem` entity | 402A | 402.1 | New file: `verticals/legal/tariff/TariffItem.java`. Fields: id, scheduleId (UUID FK), itemNumber, section, description, amount (BigDecimal), unit, notes, sortOrder (int). `@ManyToOne(fetch = LAZY) @JoinColumn(name = "schedule_id")` back-reference. Pattern: existing child entities with parent FK. |
| 402.3 | Create repositories | 402A | 402.1, 402.2 | `TariffScheduleRepository.java`: `findByCategoryAndCourtLevelAndIsActiveTrue(String, String)` -- active schedule lookup. `TariffItemRepository.java`: `findByScheduleIdOrderBySortOrderAsc(UUID)`, native query for description search using `similarity()`. |
| 402.4 | Create `TariffService` | 402A | 402.1-402.3 | New file: `verticals/legal/tariff/TariffService.java`. Methods: (1) `listSchedules()`, (2) `getSchedule(id)` -- with items, (3) `getActiveSchedule(category, courtLevel)`, (4) `createSchedule(request)` -- moduleGuard("lssa_tariff"), (5) `updateSchedule(id, request)` -- reject if isSystem, (6) `cloneSchedule(id)` -- deep copy as custom (isSystem=false), (7) `createItem(scheduleId, request)` -- reject if isSystem, (8) `updateItem(id, request)` -- reject if isSystem, (9) `deleteItem(id)` -- reject if isSystem, (10) `searchItems(scheduleId, search, section)`. DTO records. Audit events on create/update/clone. |
| 402.5 | Write integration tests for TariffService | 402A | 402.4 | 6 tests: (1) createSchedule saves with isSystem=false, (2) updateSchedule on system schedule throws, (3) cloneSchedule creates deep copy with isSystem=false, (4) createItem on system schedule throws, (5) getActiveSchedule returns correct schedule, (6) searchItems returns items matching description. |
| 402.6 | Write integration test for module guard | 402A | 402.4 | 1 test: service throws when `lssa_tariff` not enabled. |
| 402.7 | Create `TariffController` | 402B | 402A | New file: `verticals/legal/tariff/TariffController.java`. 11 endpoints: `GET /api/tariff-schedules` (VIEW_LEGAL), `GET /api/tariff-schedules/{id}` (VIEW_LEGAL), `GET /api/tariff-schedules/active` (VIEW_LEGAL, query params: category, courtLevel), `POST /api/tariff-schedules` (MANAGE_LEGAL), `PUT /api/tariff-schedules/{id}` (MANAGE_LEGAL), `POST /api/tariff-schedules/{id}/clone` (MANAGE_LEGAL), `GET /api/tariff-items` (VIEW_LEGAL, query params: scheduleId, search, section), `GET /api/tariff-items/{id}` (VIEW_LEGAL), `POST /api/tariff-schedules/{id}/items` (MANAGE_LEGAL), `PUT /api/tariff-items/{id}` (MANAGE_LEGAL), `DELETE /api/tariff-items/{id}` (MANAGE_LEGAL). Thin delegation. |
| 402.8 | Define DTOs | 402B | 402.7 | `CreateScheduleRequest`, `UpdateScheduleRequest`, `ScheduleResponse` (with items count), `CreateItemRequest`, `UpdateItemRequest`, `TariffItemResponse`. |
| 402.9 | Write controller tests -- schedules | 402B | 402.7 | 3 tests: (1) POST creates schedule, (2) POST clone creates custom copy, (3) PUT on system schedule returns 400/409. |
| 402.10 | Write controller tests -- items | 402B | 402.7 | 2 tests: (1) POST item adds to schedule, (2) GET items with search returns matching items. |
| 402.11 | Write controller authorization test | 402B | 402.7 | 1 test: POST /api/tariff-schedules with VIEW_LEGAL only returns 403. |

### Key Files

**Slice 402A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffSchedule.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffItem.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffScheduleRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffItemRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffServiceTest.java`

**Slice 402B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffController.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` -- Clone-and-edit pattern (isSystem, clone method)
- `adr/ADR-211-tariff-rate-integration-approach.md` -- Design rationale

### Architecture Decisions

- **isSystem protection**: System schedules (seeded with `isSystem = true`) are read-only. The clone-and-edit pattern allows firms to customize tariffs without modifying the seed data. Same pattern as `DocumentTemplate`.
- **Description trigram index for search**: Tariff items have descriptive text that users search by activity name. The GIN trigram index enables partial match search on descriptions.
- **CASCADE delete from schedule to items**: Deleting a custom schedule removes all its items. System schedules cannot be deleted.

---

## Epic 403: Invoice Tariff Integration + InvoiceLine Extension

**Goal**: Extend the InvoiceLine entity with tariff support and add TARIFF line type handling to InvoiceService.

**References**: Architecture doc Sections 2.8 (InvoiceLine extension), 3.5 (tariff item selection + invoice line creation), 4.5 (invoice tariff endpoint); ADR-211.

**Dependencies**: Epic 402 (TariffSchedule + TariffItem entities).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **403A** | 403.1--403.6 | Extend `InvoiceLine` entity with `tariffItemId` + `lineSource`. Add `TARIFF` to `InvoiceLineType`. Extend `InvoiceService.addLine()` for tariff lines with module guard + tariff item resolution. Extend `InvoiceLineResponse` DTO. Integration tests (~5). Backend only. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 403.1 | Add `tariffItemId` and `lineSource` to `InvoiceLine` entity | 403A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java`. Add: `@Column(name = "tariff_item_id") private UUID tariffItemId;` and `@Column(name = "line_source", length = 20) private String lineSource;`. Both nullable. Pattern: existing nullable FK fields in InvoiceLine. |
| 403.2 | Add `TARIFF` to `InvoiceLineType` enum | 403A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLineType.java`. Add `TARIFF` value. |
| 403.3 | Extend `InvoiceService` for tariff lines | 403A | 403.1, 403.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`. In the `addLine()` method (or create a new method `addTariffLine()`): when `tariffItemId` is provided and `lineType` is TARIFF, call `moduleGuard.requireModule("lssa_tariff")`, resolve tariff item via `TariffService.getItem(tariffItemId)`, set `lineSource = "TARIFF"`, compute amount as `tariffItem.amount * quantity`. Allow description and amount overrides. Inject `TariffService` and `VerticalModuleGuard`. |
| 403.4 | Extend response DTO | 403A | 403.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/InvoiceLineResponse.java` (or equivalent DTO). Add `tariffItemId` (UUID), `lineSource` (String), `tariffItemNumber` (String, resolved from TariffItem if FK is set). |
| 403.5 | Write integration tests for tariff line creation | 403A | 403.3 | 3 tests: (1) addTariffLine creates InvoiceLine with TARIFF type and tariffItemId set, (2) tariff line amount is calculated from tariff item x quantity, (3) tariff line with amount override uses override value but preserves tariffItemId FK. |
| 403.6 | Write integration tests for module guard and regression | 403A | 403.3 | 2 tests: (1) addTariffLine throws when lssa_tariff module not enabled, (2) adding a TIME line still works correctly (regression: existing pipeline untouched). |

### Key Files

**Slice 403A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java` -- Add tariffItemId, lineSource
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLineType.java` -- Add TARIFF
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Extend addLine for tariff
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/InvoiceLineResponse.java` -- Add tariff fields

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java` -- Current entity structure
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Current addLine logic
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffService.java` -- To inject for item resolution

### Architecture Decisions

- **Additive changes only**: The existing InvoiceLine entity gets two new nullable columns and one new enum value. Existing TIME/EXPENSE/MANUAL lines are completely unaffected. The tariffItemId FK is null for all non-tariff lines.
- **Module guard on tariff line creation**: Even though the InvoiceLine entity is a shared entity, creating a TARIFF line requires the `lssa_tariff` module to be enabled. This prevents non-legal tenants from accidentally creating tariff lines.
- **Amount override preserves FK**: The firm can override the tariff amount for a specific line (e.g., discounted rate), but the `tariffItemId` FK is preserved for audit traceability.

---

## Epic 404: Legal Pack Content + Tariff Seed Data

**Goal**: Create all legal pack seeders and JSON resources for field packs, template packs, clause packs, compliance packs, automation packs, and LSSA tariff seed data.

**References**: Architecture doc Section 7 (pack content specification).

**Dependencies**: Epic 397 (V83 migration, module registration).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **404A** | 404.1--404.7 | 5 pack seeders + 6 JSON resource files for legal-za packs (field, template, clause, compliance, automation). Provisioning integration. Integration tests (~4). Backend only. |  |
| **404B** | 404.8--404.12 | `LegalTariffSeeder` + LSSA 2024/2025 tariff JSON seed data (18 items). Provisioning integration (legal-za profile triggers tariff seeding). Integration tests (~3). Backend only. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 404.1 | Create `legal-za-customer` field pack JSON | 404A | -- | New file: `backend/src/main/resources/field-packs/legal-za-customer.json`. 7 fields per architecture doc Section 7.1: client_type (DROPDOWN), id_passport_number (TEXT), registration_number (TEXT), physical_address (TEXTAREA), postal_address (TEXTAREA), preferred_correspondence (DROPDOWN), referred_by (TEXT). Pattern: `field-packs/accounting-za-customer.json`. |
| 404.2 | Create `legal-za-project` field pack JSON | 404A | -- | New file: `backend/src/main/resources/field-packs/legal-za-project.json`. 8 fields: matter_type (DROPDOWN), case_number (TEXT), court_name (TEXT), opposing_party (TEXT), opposing_attorney (TEXT), advocate_name (TEXT), date_of_instruction (DATE), estimated_value (NUMBER). Pattern: `field-packs/accounting-za-project.json`. |
| 404.3 | Create remaining pack JSONs | 404A | -- | New files: `template-packs/legal-za/pack.json` (7 templates per Section 7.2), `clause-packs/legal-za-clauses/pack.json` (10 clauses per Section 7.3), `compliance-packs/legal-za-onboarding/pack.json` (11 items per Section 7.4), `automation-templates/legal-za.json` (2 rules per Section 7.5). Pattern: existing `accounting-za` pack JSONs in same directories. |
| 404.4 | Create legal pack seeder classes | 404A | 404.1-404.3 | Create seeders in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/` (or `verticals/legal/seeder/`): `LegalFieldPackSeeder`, `LegalTemplatePackSeeder`, `LegalClausePackSeeder`, `LegalCompliancePackSeeder`, `LegalAutomationPackSeeder`. Each extends `AbstractPackSeeder` or follows existing seeder pattern. Load from classpath resources. Pattern: existing accounting pack seeders (check `seeder/` package and how accounting packs are loaded). |
| 404.5 | Integrate seeders with provisioning | 404A | 404.4 | Modify provisioning/profile activation to invoke legal pack seeders when `legal-za` profile is applied. Check `TenantProvisioningService` and `VerticalProfileService.applyProfile()` for where accounting seeders are triggered. Add legal seeders in the same hooks, conditioned on the `legal-za` profile. Pattern: how accounting pack seeders are invoked during provisioning (Phase 47/49). |
| 404.6 | Write integration tests for pack seeders | 404A | 404.4, 404.5 | 3 tests: (1) provisioning with legal-za profile creates field definitions from legal-za-customer pack, (2) legal-za template pack creates 7 templates, (3) legal-za compliance pack creates 11 checklist items. Use `@SpringBootTest` + tenant provisioning test setup. |
| 404.7 | Write integration test for pack isolation | 404A | 404.5 | 1 test: provisioning with accounting-za profile does NOT create legal field packs (no cross-contamination). |
| 404.8 | Create LSSA tariff seed JSON | 404B | -- | New file: `backend/src/main/resources/tariff-seed/lssa-2024-2025-hc-pp.json`. 18 items per architecture doc Section 7.6. Structure: `{ "name": "LSSA 2024/2025 High Court", "category": "PARTY_AND_PARTY", "courtLevel": "HIGH_COURT", "effectiveFrom": "2024-04-01", "source": "LSSA Gazette 2024", "items": [ ... ] }`. Each item: itemNumber, section, description, amount, unit, notes, sortOrder. |
| 404.9 | Create `LegalTariffSeeder` | 404B | 404.8 | New file: seeder class that loads the LSSA tariff JSON, creates a `TariffSchedule` with `isSystem = true` + `isActive = true`, and creates all `TariffItem` records. Idempotent: skip if schedule with same name already exists. Pattern: how `DocumentTemplate` system templates are seeded. |
| 404.10 | Integrate tariff seeder with provisioning | 404B | 404.9 | Modify provisioning hooks: when `legal-za` profile is applied, invoke `LegalTariffSeeder` in addition to other pack seeders. The tariff seeder must run after V83 migration creates the `tariff_schedules` and `tariff_items` tables. |
| 404.11 | Write integration tests for tariff seeder | 404B | 404.9, 404.10 | 2 tests: (1) tariff seeder creates schedule with 18 items and isSystem=true, (2) tariff seeder is idempotent (running twice does not duplicate). |
| 404.12 | Write integration test for tariff seed isolation | 404B | 404.10 | 1 test: provisioning with accounting-za profile does NOT create tariff schedules. |

### Key Files

**Slice 404A -- Create:**
- `backend/src/main/resources/field-packs/legal-za-customer.json`
- `backend/src/main/resources/field-packs/legal-za-project.json`
- `backend/src/main/resources/template-packs/legal-za/pack.json`
- `backend/src/main/resources/clause-packs/legal-za-clauses/pack.json`
- `backend/src/main/resources/compliance-packs/legal-za-onboarding/pack.json`
- `backend/src/main/resources/automation-templates/legal-za.json`
- Seeder classes (5 files)

**Slice 404B -- Create:**
- `backend/src/main/resources/tariff-seed/lssa-2024-2025-hc-pp.json`
- Tariff seeder class

**Modify (both slices):**
- Provisioning/profile activation hooks (TenantProvisioningService or VerticalProfileService)

**Read for context:**
- `backend/src/main/resources/field-packs/accounting-za-customer.json` -- Pack JSON format
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java` -- Seeder base class
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileService.java` -- Profile activation hooks

### Architecture Decisions

- **Pack seeders follow existing pattern**: Each pack seeder extends `AbstractPackSeeder` or follows the pattern established by accounting pack seeders. JSON resources are in the same directory structure.
- **Tariff seed is separate from other packs**: The tariff seeder creates `TariffSchedule` + `TariffItem` entities (not field definitions or templates). It has its own JSON format and seeder class.
- **Idempotent seeding**: All seeders check if the pack has already been applied before creating records. This allows re-running provisioning without duplicates.

---

## Epic 405: Frontend -- Court Calendar + Prescription Pages

**Goal**: Replace the court calendar stub page with a full implementation including calendar/list views, and add prescription tracking UI with create/interrupt dialogs.

**References**: Architecture doc Sections 8.2 (frontend changes).

**Dependencies**: Epics 398, 399 (court date + prescription backend).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **405A** | 405.1--405.7 | Court calendar page (`court-calendar/page.tsx`) replacing stub. Calendar month view + list view with filters (date range, type, status, customer). TypeScript types, Zod schemas, server actions for court date CRUD + upcoming data. Nav item update with `requiredModule: "court_calendar"`. Frontend tests (~5). Frontend only. |  |
| **405B** | 405.8--405.13 | `CreateCourtDateDialog`, `PostponeDialog`, `CancelDialog`, `OutcomeDialog`. `PrescriptionTab` component (list + `CreatePrescriptionDialog` + `InterruptDialog`). Court date detail view. Frontend tests (~5). Frontend only. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 405.1 | Create TypeScript types for court dates and prescriptions | 405A | -- | New file: `frontend/lib/types/legal.ts`. Types: `CourtDate` (id, projectId, projectName, customerId, customerName, dateType, scheduledDate, scheduledTime, courtName, courtReference, judgeMagistrate, description, status, outcome, reminderDays, createdBy, createdAt, updatedAt), `PrescriptionTracker` (id, projectId, projectName, customerId, causeOfActionDate, prescriptionType, customYears, prescriptionDate, interruptionDate, interruptionReason, status, notes, createdBy, createdAt, updatedAt), `ConflictCheck`, `AdverseParty`, `AdversePartyLink`, `TariffSchedule`, `TariffItem`. Enums: `CourtDateType`, `CourtDateStatus`, `PrescriptionType`, `PrescriptionStatus`, etc. |
| 405.2 | Create Zod schemas for court dates | 405A | 405.1 | New file: `frontend/lib/schemas/legal.ts`. Schemas: `createCourtDateSchema`, `postponeCourtDateSchema`, `cancelCourtDateSchema`, `outcomeSchema`, `createPrescriptionTrackerSchema`, `interruptPrescriptionSchema`. Pattern: `lib/schemas/customer.ts`. |
| 405.3 | Create server actions for court calendar | 405A | -- | New file: `frontend/app/(app)/org/[slug]/court-calendar/actions.ts`. Server actions: `fetchCourtDates(filters)`, `fetchCourtDate(id)`, `createCourtDate(data)`, `updateCourtDate(id, data)`, `postponeCourtDate(id, data)`, `cancelCourtDate(id, data)`, `recordOutcome(id, data)`, `fetchPrescriptionTrackers(filters)`, `createPrescriptionTracker(data)`, `interruptPrescription(id, data)`, `fetchUpcoming()`. Use `apiClient` from `lib/api.ts`. Pattern: `app/(app)/org/[slug]/deadlines/actions.ts`. |
| 405.4 | Replace court calendar page stub | 405A | 405.1-405.3 | Modify: `frontend/app/(app)/org/[slug]/court-calendar/page.tsx`. Replace stub with server component that fetches court dates. Tab layout: "Calendar" (month grid view), "List" (table with filters), "Prescriptions" (separate tab for prescription trackers). Filter bar: date range picker, status dropdown, dateType dropdown, customer search. Pass data to client components. Pattern: `app/(app)/org/[slug]/deadlines/page.tsx`. |
| 405.5 | Create `CourtDateListView` component | 405A | 405.4 | New file: `frontend/components/legal/court-date-list-view.tsx`. "use client". Table with columns: Date, Time, Type (badge), Court, Matter, Client, Status (color-coded badge), Actions (dropdown). Sortable by date. Color coding: SCHEDULED=blue, POSTPONED=amber, HEARD=green, CANCELLED=slate. `data-testid="court-date-list"`. |
| 405.6 | Create `CourtCalendarView` component | 405A | 405.4 | New file: `frontend/components/legal/court-calendar-view.tsx`. "use client". Month grid calendar showing court dates as colored dots/chips on their scheduled dates. Click a date to see details panel. Navigate months. `data-testid="court-calendar-view"`. Pattern: follow a simple calendar grid implementation (no external library). |
| 405.7 | Update nav-items + write tests | 405A | 405.4 | Modify: `frontend/lib/nav-items.ts` -- update court-calendar nav item with `requiredModule: "court_calendar"` and remove any `comingSoon` flag if present. 5 tests: (1) court calendar page renders list view, (2) filters update list, (3) status badges have correct colors, (4) calendar view renders month grid, (5) nav item visible when module enabled. Pattern: `__tests__/dashboard/company-dashboard.test.tsx`. |
| 405.8 | Create `CreateCourtDateDialog` | 405B | 405A | New file: `frontend/components/legal/create-court-date-dialog.tsx`. "use client". Form fields: project selector, dateType dropdown, scheduledDate (date picker), scheduledTime (time input), courtName, courtReference, judgeMagistrate, description, reminderDays. Uses `createCourtDateSchema`. `data-testid="create-court-date-dialog"`. Pattern: `components/projects/create-project-dialog.tsx`. |
| 405.9 | Create `PostponeDialog`, `CancelDialog`, `OutcomeDialog` | 405B | 405A | Three small dialog components for court date lifecycle actions. `PostponeDialog`: new date picker + reason textarea. `CancelDialog`: reason textarea. `OutcomeDialog`: outcome textarea. Each calls the corresponding server action. `data-testid="postpone-dialog"`, `"cancel-court-date-dialog"`, `"outcome-dialog"`. |
| 405.10 | Create `PrescriptionTab` component | 405B | 405A | New file: `frontend/components/legal/prescription-tab.tsx`. "use client". List of prescription trackers with columns: Matter, Client, Type, Cause of Action Date, Prescription Date, Status (color-coded: RUNNING=blue, WARNED=amber, INTERRUPTED=slate, EXPIRED=red), Days Remaining. "Add Tracker" button opens `CreatePrescriptionDialog`. Row action: "Interrupt" opens `InterruptDialog`. `data-testid="prescription-tab"`. |
| 405.11 | Create `CreatePrescriptionDialog` and `InterruptDialog` | 405B | 405.10 | Two dialog components. `CreatePrescriptionDialog`: project selector, causeOfActionDate (date picker), prescriptionType dropdown (GENERAL_3Y, DEBT_6Y, MORTGAGE_30Y, DELICT_3Y, CONTRACT_3Y, CUSTOM), customYears (visible only when CUSTOM), notes. `InterruptDialog`: interruptionDate (date picker), interruptionReason. |
| 405.12 | Create court date detail view | 405B | 405A | Court date detail: either expand row or side panel showing all fields + timeline of status changes + action buttons (Postpone, Cancel, Record Outcome depending on current status). Integrate lifecycle dialogs. |
| 405.13 | Write frontend tests | 405B | 405.8-405.12 | 5 tests: (1) CreateCourtDateDialog renders form fields, (2) PostponeDialog submits new date, (3) PrescriptionTab renders tracker list, (4) CreatePrescriptionDialog shows customYears only for CUSTOM type, (5) court date detail shows correct action buttons based on status. |

### Key Files

**Slice 405A -- Create:**
- `frontend/lib/types/legal.ts`
- `frontend/lib/schemas/legal.ts`
- `frontend/app/(app)/org/[slug]/court-calendar/actions.ts`
- `frontend/components/legal/court-date-list-view.tsx`
- `frontend/components/legal/court-calendar-view.tsx`

**Slice 405A -- Modify:**
- `frontend/app/(app)/org/[slug]/court-calendar/page.tsx` -- Replace stub
- `frontend/lib/nav-items.ts` -- Update court-calendar nav item

**Slice 405B -- Create:**
- `frontend/components/legal/create-court-date-dialog.tsx`
- `frontend/components/legal/postpone-dialog.tsx`
- `frontend/components/legal/cancel-court-date-dialog.tsx`
- `frontend/components/legal/outcome-dialog.tsx`
- `frontend/components/legal/prescription-tab.tsx`
- `frontend/components/legal/create-prescription-dialog.tsx`
- `frontend/components/legal/interrupt-dialog.tsx`

### Architecture Decisions

- **Tab layout for calendar/list/prescriptions**: Three concerns on one page. Calendar view is visual overview, list view is for management/filtering, prescriptions are a separate domain but within the same module.
- **Color coding matches status semantics**: SCHEDULED=blue (normal), POSTPONED=amber (needs attention), HEARD=green (complete), CANCELLED=slate (inactive). Same pattern used for other status badges in the platform.

---

## Epic 406: Frontend -- Conflict Check + Adverse Party Pages

**Goal**: Replace the conflict check stub page with run-check form, result display, and check history. Add adverse party registry page with CRUD.

**References**: Architecture doc Section 8.2.

**Dependencies**: Epics 400, 401 (adverse party + conflict check backend).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **406A** | 406.1--406.6 | Conflict check page (`conflict-check/page.tsx`) replacing stub. Run-check form (name, ID, registration number, check type), result display (green/amber/red with match details), check history list with filters. Server actions, nav item, tests (~5). Frontend only. |  |
| **406B** | 406.7--406.12 | Adverse party registry page (new: `legal/adverse-parties/page.tsx`). CRUD table, create/edit dialog, link/unlink dialog, party detail with linked matters list. Server actions, nav item, tests (~4). Frontend only. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 406.1 | Create server actions for conflict check | 406A | -- | New file: `frontend/app/(app)/org/[slug]/conflict-check/actions.ts`. Server actions: `performConflictCheck(data)`, `fetchConflictChecks(filters)`, `fetchConflictCheck(id)`, `resolveConflict(id, data)`. Pattern: existing server action files. |
| 406.2 | Replace conflict check page stub | 406A | 406.1 | Modify: `frontend/app/(app)/org/[slug]/conflict-check/page.tsx`. Tab layout: "Run Check" (form + result display), "History" (list of previous checks). Server component. |
| 406.3 | Create `ConflictCheckForm` component | 406A | 406.2 | New file: `frontend/components/legal/conflict-check-form.tsx`. "use client". Form: checkedName (required), checkedIdNumber (optional), checkedRegistrationNumber (optional), checkType dropdown (NEW_CLIENT, NEW_MATTER, PERIODIC_REVIEW), optional customer/project selectors. Submit calls `performConflictCheck()`. Displays result inline below form. `data-testid="conflict-check-form"`. |
| 406.4 | Create `ConflictCheckResult` component | 406A | 406.3 | New file: `frontend/components/legal/conflict-check-result.tsx`. "use client". Result display: large status indicator (green circle = NO_CONFLICT, amber = POTENTIAL_CONFLICT, red = CONFLICT_FOUND). Match details table: party name, match type, similarity score, linked matter, relationship. Action buttons: Proceed, Decline, Obtain Waiver, Refer. `data-testid="conflict-check-result"`. |
| 406.5 | Create `ConflictCheckHistory` and `ResolveConflictDialog` | 406A | 406.2 | History: table with columns: Date, Checked Name, Type, Result (badge), Resolution, Checked By. Filterable. `ResolveConflictDialog`: resolution dropdown + notes textarea + optional waiver document upload. `data-testid="conflict-check-history"`. |
| 406.6 | Update nav-items + write tests | 406A | 406.2 | Modify: `frontend/lib/nav-items.ts` -- update conflict-check nav item. 5 tests: (1) form renders required fields, (2) result displays correct color for CONFLICT_FOUND, (3) result displays NO_CONFLICT green, (4) history list renders check records, (5) resolve dialog submits resolution. |
| 406.7 | Create server actions for adverse parties | 406B | -- | New file: `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts`. Server actions: `fetchAdverseParties(search, partyType, page)`, `fetchAdverseParty(id)`, `createAdverseParty(data)`, `updateAdverseParty(id, data)`, `deleteAdverseParty(id)`, `linkAdverseParty(id, data)`, `unlinkAdverseParty(linkId)`, `fetchProjectAdverseParties(projectId)`. |
| 406.8 | Create adverse party registry page | 406B | 406.7 | New files: `frontend/app/(app)/org/[slug]/legal/adverse-parties/page.tsx` (server component), `frontend/app/(app)/org/[slug]/legal/adverse-parties/layout.tsx` (optional). Table: Name, ID Number, Registration Number, Type (badge), Linked Matters (count), Actions. Search bar with fuzzy match. "Add Party" button. |
| 406.9 | Create `AdversePartyDialog` | 406B | 406.8 | New file: `frontend/components/legal/adverse-party-dialog.tsx`. "use client". Create/edit form: name (required), idNumber, registrationNumber, partyType dropdown, aliases, notes. Reused for both create and edit. `data-testid="adverse-party-dialog"`. |
| 406.10 | Create `LinkAdversePartyDialog` | 406B | 406.8 | New file: `frontend/components/legal/link-adverse-party-dialog.tsx`. "use client". Form: project/matter selector, customer selector, relationship dropdown (OPPOSING_PARTY, WITNESS, CO_ACCUSED, RELATED_ENTITY, GUARANTOR), description. `data-testid="link-adverse-party-dialog"`. |
| 406.11 | Update nav-items for adverse parties | 406B | 406.8 | Modify: `frontend/lib/nav-items.ts` -- add adverse parties nav item with `requiredModule: "conflict_check"` in Clients group. |
| 406.12 | Write frontend tests | 406B | 406.8-406.10 | 4 tests: (1) adverse party list renders table, (2) create dialog submits new party, (3) delete button disabled when party has active links, (4) link dialog renders relationship options. |

### Key Files

**Slice 406A -- Create:**
- `frontend/app/(app)/org/[slug]/conflict-check/actions.ts`
- `frontend/components/legal/conflict-check-form.tsx`
- `frontend/components/legal/conflict-check-result.tsx`
- `frontend/components/legal/conflict-check-history.tsx`
- `frontend/components/legal/resolve-conflict-dialog.tsx`

**Slice 406A -- Modify:**
- `frontend/app/(app)/org/[slug]/conflict-check/page.tsx` -- Replace stub
- `frontend/lib/nav-items.ts` -- Update conflict-check nav item

**Slice 406B -- Create:**
- `frontend/app/(app)/org/[slug]/legal/adverse-parties/page.tsx`
- `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts`
- `frontend/components/legal/adverse-party-dialog.tsx`
- `frontend/components/legal/link-adverse-party-dialog.tsx`

### Architecture Decisions

- **Conflict check result displayed inline below form**: The user submits the check and sees the result immediately without navigating away. This mirrors the real-world workflow where the check is performed before proceeding with client intake.
- **Adverse party registry is a standalone page**: Not embedded in the conflict check page because adverse party management is a separate administrative concern.

---

## Epic 407: Frontend -- Tariff Pages + Invoice Tariff Selector

**Goal**: Build the tariff schedule browser page and add a tariff item selector to the invoice editor for creating TARIFF invoice lines.

**References**: Architecture doc Sections 8.2, 5.2 (tariff invoice line creation).

**Dependencies**: Epics 402, 403 (tariff + invoice backend).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **407A** | 407.1--407.5 | Tariff schedule browser page (new: `legal/tariffs/page.tsx`). Schedule list, item browser with section grouping, search by description. Clone button. Custom schedule editor. Server actions, nav item, tests (~4). Frontend only. |  |
| **407B** | 407.6--407.10 | `TariffLineDialog` component (schedule picker, item browser, quantity input). "Add Tariff Items" button in invoice editor (module-gated via `<ModuleGate module="lssa_tariff">`). Tests (~4). Frontend only. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 407.1 | Create server actions for tariffs | 407A | -- | New file: `frontend/app/(app)/org/[slug]/legal/tariffs/actions.ts`. Server actions: `fetchTariffSchedules()`, `fetchTariffSchedule(id)`, `fetchActiveSchedule(category, courtLevel)`, `createSchedule(data)`, `updateSchedule(id, data)`, `cloneSchedule(id)`, `fetchTariffItems(scheduleId, search, section)`, `createItem(scheduleId, data)`, `updateItem(id, data)`, `deleteItem(id)`. |
| 407.2 | Create tariff schedules page | 407A | 407.1 | New files: `frontend/app/(app)/org/[slug]/legal/tariffs/page.tsx`. Server component. Schedule list: Name, Category (badge), Court Level (badge), Effective From, Active (indicator), System (indicator). Click schedule to expand/navigate to item view. "Clone" button on system schedules. "Create Custom" button. |
| 407.3 | Create `TariffItemBrowser` component | 407A | 407.2 | New file: `frontend/components/legal/tariff-item-browser.tsx`. "use client". Items grouped by section with collapsible section headers. Each item row: item number, description, amount (formatted ZAR), unit badge. Search input filters items by description. `data-testid="tariff-item-browser"`. |
| 407.4 | Add nav item and Zod schemas for tariffs | 407A | 407.2 | Modify: `frontend/lib/nav-items.ts` -- add tariffs nav item with `requiredModule: "lssa_tariff"` in Finance group. Add tariff schemas to `lib/schemas/legal.ts`: `createTariffScheduleSchema`, `createTariffItemSchema`. |
| 407.5 | Write frontend tests | 407A | 407.2, 407.3 | 4 tests: (1) tariff page renders schedule list, (2) item browser groups items by section, (3) search filters items by description, (4) system schedule shows clone button. |
| 407.6 | Create `TariffLineDialog` component | 407B | 407A | New file: `frontend/components/legal/tariff-line-dialog.tsx`. "use client". Multi-step: (1) select schedule (or auto-select active), (2) browse/search items using TariffItemBrowser, (3) select items with quantity input per item. Bottom bar shows selected count + total amount. "Add to Invoice" button. Props: `invoiceId`, `onSuccess` callback. `data-testid="tariff-line-dialog"`. |
| 407.7 | Create invoice tariff action | 407B | 407.6 | Add to existing invoice actions file or create new: `addTariffLine(invoiceId, tariffItemId, quantity, descriptionOverride, amountOverride)`. Calls `POST /api/invoices/{invoiceId}/lines` with `lineType: "TARIFF"`. |
| 407.8 | Integrate tariff selector into invoice editor | 407B | 407.6, 407.7 | Modify: invoice editor component (find in `frontend/app/(app)/org/[slug]/invoices/` or `components/invoices/`). Add module-gated "Add Tariff Items" button: `<ModuleGate module="lssa_tariff"><Button onClick={openTariffDialog}>Add Tariff Items</Button></ModuleGate>`. Opens `TariffLineDialog`. On success, refresh invoice lines. |
| 407.9 | Display tariff info on invoice lines | 407B | 407.8 | Modify invoice line display component: when `lineSource === "TARIFF"`, show tariff item number badge and unit label alongside the description. Differentiate visually from TIME/EXPENSE lines. |
| 407.10 | Write frontend tests | 407B | 407.6-407.9 | 4 tests: (1) TariffLineDialog renders item browser, (2) selecting item updates total amount, (3) "Add Tariff Items" button hidden when module not enabled, (4) invoice line displays tariff badge for TARIFF lines. |

### Key Files

**Slice 407A -- Create:**
- `frontend/app/(app)/org/[slug]/legal/tariffs/page.tsx`
- `frontend/app/(app)/org/[slug]/legal/tariffs/actions.ts`
- `frontend/components/legal/tariff-item-browser.tsx`

**Slice 407A -- Modify:**
- `frontend/lib/nav-items.ts` -- Add tariffs nav item
- `frontend/lib/schemas/legal.ts` -- Add tariff schemas

**Slice 407B -- Create:**
- `frontend/components/legal/tariff-line-dialog.tsx`

**Slice 407B -- Modify:**
- Invoice editor component -- Add "Add Tariff Items" button (module-gated)
- Invoice line display component -- Show tariff info for TARIFF lines

**Read for context:**
- `frontend/app/(app)/org/[slug]/invoices/` -- Invoice page structure
- `frontend/components/legal/` (from 405/406) -- Legal component patterns
- `frontend/lib/nav-items.ts` -- Nav item structure with requiredModule

### Architecture Decisions

- **TariffLineDialog is multi-step within a single dialog**: No page navigation. Schedule selection, item browsing, and quantity setting happen in one dialog to minimize friction in the invoice editing workflow.
- **Module-gated "Add Tariff Items" button**: The `<ModuleGate>` component conditionally renders the button only for tenants with `lssa_tariff` enabled. Accounting tenants never see it.

---

## Epic 408: Frontend -- Project Detail Tabs + Sidebar + Dashboard Widget

**Goal**: Add module-gated "Court Dates" and "Adverse Parties" tabs to the project detail page, add legal nav items to the sidebar, and add an "Upcoming Court Dates" widget to the dashboard.

**References**: Architecture doc Section 8.2.

**Dependencies**: Epics 405, 406, 407 (all frontend legal pages must be built first).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **408A** | 408.1--408.7 | Project detail: module-gated "Court Dates" tab (reusing CourtDateListView filtered by projectId) + "Adverse Parties" tab (list with link/unlink actions). Sidebar: module-gated legal nav items. Dashboard: "Upcoming Court Dates" widget (compact list from `/api/court-calendar/upcoming`). Frontend tests (~5). Frontend only. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 408.1 | Add "Court Dates" tab to project detail | 408A | -- | Modify: project detail page/tabs component. Add `<ModuleGate module="court_calendar"><Tab value="court-dates" label="Court Dates"><ProjectCourtDatesTab projectId={id} /></Tab></ModuleGate>`. `ProjectCourtDatesTab` reuses `CourtDateListView` with `projectId` filter pre-applied. "Add Court Date" button opens `CreateCourtDateDialog` with projectId pre-filled. |
| 408.2 | Add "Adverse Parties" tab to project detail | 408A | -- | Modify: project detail page/tabs component. Add `<ModuleGate module="conflict_check"><Tab value="adverse-parties" label="Adverse Parties"><ProjectAdversePartiesTab projectId={id} /></Tab></ModuleGate>`. Table of adverse parties linked to this matter with relationship badges. "Link Party" button. "Unlink" action per row. |
| 408.3 | Create `ProjectCourtDatesTab` component | 408A | 408.1 | New file: `frontend/components/legal/project-court-dates-tab.tsx`. "use client". Fetches court dates for the project. Compact list view. "Add Court Date" button. Uses SWR for client-side data fetching (dialog is client-side). `data-testid="project-court-dates-tab"`. |
| 408.4 | Create `ProjectAdversePartiesTab` component | 408A | 408.2 | New file: `frontend/components/legal/project-adverse-parties-tab.tsx`. "use client". Fetches adverse parties linked to the project. Table with party name, type, relationship badge, linked date. Link/unlink actions. `data-testid="project-adverse-parties-tab"`. |
| 408.5 | Create `UpcomingCourtDatesWidget` dashboard component | 408A | -- | New file: `frontend/components/legal/upcoming-court-dates-widget.tsx`. "use client". Compact widget: next 5 court dates (date, type badge, matter name, court). Color-coded by urgency (red < 3 days, amber < 7 days, green otherwise). "View All" link to court calendar page. Module-gated rendering. `data-testid="upcoming-court-dates-widget"`. |
| 408.6 | Integrate dashboard widget + sidebar nav | 408A | 408.5 | Modify: dashboard page -- add `<ModuleGate module="court_calendar"><UpcomingCourtDatesWidget /></ModuleGate>` in the secondary area. Sidebar nav: verify module-gated items from nav-items.ts render correctly (court-calendar, conflict-check, adverse-parties, tariffs). These should already be handled by the `ModuleGate`/`requiredModule` system in nav-items.ts from slices 405-407. |
| 408.7 | Write frontend tests | 408A | 408.1-408.6 | 5 tests: (1) project detail shows "Court Dates" tab when court_calendar enabled, (2) project detail hides "Court Dates" tab when module disabled, (3) "Adverse Parties" tab renders linked parties, (4) dashboard widget renders upcoming court dates, (5) sidebar shows legal nav items for legal profile. |

### Key Files

**Slice 408A -- Create:**
- `frontend/components/legal/project-court-dates-tab.tsx`
- `frontend/components/legal/project-adverse-parties-tab.tsx`
- `frontend/components/legal/upcoming-court-dates-widget.tsx`

**Slice 408A -- Modify:**
- Project detail page/tabs component (likely `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` or a tabs sub-component)
- Dashboard page (likely `frontend/app/(app)/org/[slug]/dashboard/page.tsx`)

**Read for context:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` -- Project detail page structure
- `frontend/components/projects/overview-tab.tsx` -- Tab pattern
- `frontend/app/(app)/org/[slug]/dashboard/page.tsx` -- Dashboard widget area

### Architecture Decisions

- **Module-gated tabs use `<ModuleGate>` component**: The existing `ModuleGate` component from Phase 49 conditionally renders children based on `OrgSettings.enabled_modules`. No new gating mechanism needed.
- **Project tabs reuse list components with pre-applied filters**: The `ProjectCourtDatesTab` reuses the same `CourtDateListView` from the standalone court calendar page, just with `projectId` pre-filtered. Avoids duplicating list/table UI.
- **Dashboard widget is compact**: 5 items max, no pagination. Links to the full court calendar page for more detail.

---

## Epic 409: Multi-Vertical Coexistence Tests

**Goal**: Prove that accounting and legal tenants can operate in the same deployment without interference. Dedicated test slice for cross-vertical isolation verification.

**References**: Architecture doc Section 8.3 (testing strategy).

**Dependencies**: All previous epics (397-408).

**Scope**: Backend + Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **409A** | 409.1--409.5 | 7 backend integration tests (independent provisioning, module isolation, InvoiceLine FK null for accounting, no pack cross-contamination, concurrent tenant operations) + 4 frontend tests (UI visibility by profile, nav isolation, profile switch). Tests only. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 409.1 | Write backend coexistence tests -- provisioning | 409A | -- | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/MultiVerticalCoexistenceTest.java`. 3 tests: (1) Provisioning an accounting-za tenant and a legal-za tenant in the same database creates separate schemas with correct pack content (accounting has rate packs, legal has tariff schedules), (2) Legal tenant's court_dates table is accessible; accounting tenant's court_dates table exists but has zero rows, (3) Module guard on accounting tenant blocks court calendar access (moduleGuard.requireModule throws). |
| 409.2 | Write backend coexistence tests -- data isolation | 409A | -- | 2 tests: (1) Creating an adverse party in legal tenant is invisible from accounting tenant context, (2) InvoiceLine with tariffItemId in legal tenant works; InvoiceLine in accounting tenant has null tariffItemId and TARIFF line type is not used (no regressions). |
| 409.3 | Write backend coexistence tests -- concurrent operations | 409A | -- | 2 tests: (1) Legal tenant creates court date while accounting tenant creates deadline -- no interference, (2) Conflict check in legal tenant does not search accounting tenant's customer data (tenant schema isolation). |
| 409.4 | Write frontend coexistence tests | 409A | -- | 4 frontend tests (Vitest): (1) Legal profile renders court calendar, conflict check, tariffs in sidebar nav, (2) Accounting profile does NOT render legal nav items, (3) Legal profile does NOT render accounting-specific nav items (deadlines shows only for accounting), (4) Profile switch from accounting to legal updates visible modules. Mock `OrgProfileProvider` context for different profiles. |
| 409.5 | Document coexistence test results | 409A | 409.1-409.4 | Add a comment block at the top of the test file explaining the 3 questions this test answers (from architecture doc Section 1: schema coexistence, InvoiceLine compatibility, pack orthogonality). |

### Key Files

**Slice 409A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/MultiVerticalCoexistenceTest.java`
- `frontend/__tests__/verticals/multi-vertical-coexistence.test.tsx`

**Read for context:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/` -- Existing vertical test patterns
- `frontend/lib/nav-items.ts` -- Nav item module gating

### Architecture Decisions

- **Test-only slice**: This epic produces zero production code. It exists solely to verify the multi-vertical architecture claim.
- **7 backend + 4 frontend tests**: The test count is deliberately comprehensive because this is the first real coexistence test in the platform. Future verticals can run these tests to verify they do not break existing verticals.
- **Tests use real provisioning**: Backend tests provision actual tenant schemas via the provisioning service, not mocks. This proves the full provisioning pipeline works for both profiles simultaneously.

---
