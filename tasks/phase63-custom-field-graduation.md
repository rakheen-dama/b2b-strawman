# Phase 63 -- Custom Field Graduation

Phase 63 promotes approximately 21 custom fields across Customer, Project, Task, and Invoice to proper entity columns. It removes the corresponding field definitions from pack JSON files, updates services (ConflictCheckService, DeadlineCalculationService, PrerequisiteService), updates template context builders, and restructures frontend forms to render promoted fields as purpose-built typed inputs rather than generic custom field entries.

**Architecture doc**: `architecture/phase63-custom-field-graduation.md`

**ADRs**:
- [ADR-237](adr/ADR-237-structural-vs-custom-field-boundary.md) -- Structural vs. Custom Field Boundary (scoring rubric: service-layer read, query performance, core flow dependency, cross-vertical presence)
- [ADR-238](adr/ADR-238-entity-type-varchar-vs-enum.md) -- Entity Type and Work Type as VARCHAR with Service-Layer Validation (not Java enums, because value sets are vertical-dependent)

**Dependencies on prior phases**:
- **Phase 11** (Tags, Custom Fields & Views): `FieldDefinition`, `FieldGroup`, `FieldPackSeeder`, `CustomFieldSection` -- all must exist
- **Phase 33** (Data Completeness & Prerequisites): `PrerequisiteService`, `PrerequisiteContext`, `PrerequisiteCheck` -- structural prerequisite checks build on this
- **Phase 51** (Accounting Practice Essentials): `DeadlineCalculationService`, `DeadlineTypeRegistry` -- service reads from JSONB that will be switched to entity getters
- **Phase 55** (Legal Foundations): `ConflictCheckService` -- reads `registration_number` from JSONB
- **Phase 12** (Document Templates): `CustomerContextBuilder`, `ProjectContextBuilder`, `InvoiceContextBuilder`, `VariableMetadataRegistry` -- template context updates

**Migration note**: V86 tenant migration is reserved for Phase 61. This phase uses the next available migration number (V87 or later, verify at implementation time). V85 is the latest existing migration in the codebase.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 459 | Foundation: Migration + Customer Entity/DTO Updates | Backend | -- (Phase 11, 33, 51, 55 complete) | M | 459A, 459B | **Done** (PR #984) |
| 460 | Project/Task/Invoice Entity/DTO Updates + Enums | Backend | 459A | S | 460A | **Done** (PR #985) |
| 461 | Service Layer Updates: Conflict Check, Deadline, Prerequisite | Backend | 459, 460 | M | 461A, 461B | **Done** (PR #986) |
| 462 | Template Context Builders + Variable Metadata + Pack JSON Cleanup | Backend | 459, 460 | M | 462A, 462B | **Done** (PR #987) |
| 463 | Frontend: Customer Form Restructuring + Detail Page | Frontend | 459B, 461 | M | 463A, 463B | |
| 464 | Frontend: Project/Task/Invoice Form + Detail Updates | Frontend | 460, 461 | M | 464A, 464B | |

---

## Dependency Graph

```
PHASE 11 (Custom Fields), PHASE 33 (Prerequisites),
PHASE 51 (Deadlines), PHASE 55 (Conflict Check),
PHASE 12 (Templates) — all complete
        |
[E459A  V87 migration (21 columns across 4 tables
        + 4 indexes) + Customer entity (13 new fields)
        + CustomerRequest/CustomerResponse DTOs +
        CustomerService create/update extensions +
        integration tests (~6)]
        |
        +──────────────────────────────────────────────+
        |                                              |
[E459B  Customer controller                     [E460A  Project entity (3 fields)
 endpoint updates (accept                        + ProjectPriority enum + Task entity
 new fields in create/edit)                      (1 field) + Invoice entity (4 fields)
 + controller integration                        + TaxType enum + DTOs + service
 tests (~4)]                                     create/update extensions + tests (~6)]
        |                                              |
        +──────────+───────────────────────────────────+
        |          |
  DOMAIN SERVICES               TEMPLATE & PACKS
  (sequential)                  (independent track)
  ──────────────                ────────────────────
        |                              |
[E461A  ConflictCheckService    [E462A  CustomerContextBuilder +
 + DeadlineCalculationService    ProjectContextBuilder +
 + CustomFieldFilterUtil          InvoiceContextBuilder +
 switch from JSONB to entity      VariableMetadataRegistry
 getters + tests (~8)]            backward-compat aliases +
        |                         tests (~6)]
[E461B  PrerequisiteService            |
 + StructuralPrerequisite       [E462B  Pack JSON cleanup:
 checks (invoice generation,     delete common-customer.json +
 proposal send) + tests (~6)]    common-invoice.json, slim 6
        |                         other packs + FieldPackSeeder
        +──────────+──────────────update + tests (~4)]
        |          |
  FRONTEND                 FRONTEND
  (Customer)               (Project/Task/Invoice)
  ──────────               ────────────────────────
        |                          |
[E463A  Customer form:       [E464A  Project form
 Address/Contact/Business     (3 fields) + Task form
 Details sections +           (estimatedHours) + Invoice
 Zod schema + server          form (4 fields) + Zod
 actions + types +            schemas + types + server
 tests (~6)]                  actions + tests (~6)]
        |                          |
[E463B  Customer detail       [E464B  Project/Invoice
 page: address block +         detail page updates +
 contact card + business       CustomFieldSection scope
 details section +             reduction + tests (~4)]
 CustomFieldSection scope
 reduction + tests (~4)]
```

**Parallel opportunities**:
- After E459A: E459B (customer controller) and E460A (project/task/invoice entities) are independent and can run in parallel.
- After E459B + E460A complete: E461 (services) and E462 (templates/packs) are independent tracks and can run in parallel.
- After E461 complete: E463 (customer frontend) and E464 (project/task/invoice frontend) can run in parallel.
- E462 (templates/packs) has zero dependency on E461 (services) -- they can run simultaneously.

---

## Implementation Order

### Stage 0: Foundation (Migration + Customer Entity)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 459 | 459A | V87 tenant migration (21 columns across 4 tables + 4 indexes). Customer entity extension (13 new fields). CustomerRequest/CustomerResponse DTO updates. CustomerService create/update extensions. Integration tests (~6). Backend only. | **Done** (PR #984) |

### Stage 1: Customer Controller + Project/Task/Invoice Entities (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 459 | 459B | Customer controller endpoint updates to accept/return 13 new fields. Controller integration tests (~4). Backend only. | **Done** (PR #984) |
| 1b (parallel) | 460 | 460A | Project entity (3 fields) + `ProjectPriority` enum. Task entity (1 field). Invoice entity (4 fields) + `TaxType` enum. All DTOs. Service create/update extensions. Integration tests (~6). Backend only. | **Done** (PR #985) |

### Stage 2: Service Layer + Template Context (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 461 | 461A | ConflictCheckService switch from JSONB to `customer.getRegistrationNumber()`. DeadlineCalculationService switch from JSONB to entity getters. CustomFieldFilterUtil switch for promoted fields. Integration tests (~8). Backend only. | **Done** (PR #986) |
| 2b (parallel) | 462 | 462A | CustomerContextBuilder, ProjectContextBuilder, InvoiceContextBuilder: expose promoted fields as direct template variables with backward-compat `customFields.slug` aliases. VariableMetadataRegistry update. Integration tests (~6). Backend only. | **Done** (PR #987) |

### Stage 3: Prerequisite Service + Pack JSON Cleanup (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 461 | 461B | PrerequisiteService extension with structural prerequisite checks (invoice generation, proposal send). `StructuralPrerequisiteCheck` class. Integration tests (~6). Backend only. | **Done** (PR #986) |
| 3b (parallel) | 462 | 462B | Pack JSON cleanup: delete `common-customer.json` and `common-invoice.json`. Slim 6 other pack files (remove promoted field entries). Update FieldPackSeeder to skip deleted files. Tests (~4). Backend only. | **Done** (PR #987) |

### Stage 4: Frontend Customer + Frontend Project/Task/Invoice (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 463 | 463A | Customer create/edit form restructuring: Address section, Contact section, Business Details section. TypeScript types + Zod schema updates. Server actions. Frontend tests (~6). Frontend only. | |
| 4b (parallel) | 464 | 464A | Project form (referenceNumber, priority, workType). Task form (estimatedHours). Invoice form (poNumber, taxType, billingPeriodStart, billingPeriodEnd). Types + schemas + actions. Frontend tests (~6). Frontend only. | |

### Stage 5: Frontend Detail Pages (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 463 | 463B | Customer detail page: formatted address block, contact card, business details section. CustomFieldSection scope reduction for customer. Frontend tests (~4). Frontend only. | |
| 5b (parallel) | 464 | 464B | Project detail page + invoice detail page updates for promoted fields. CustomFieldSection scope reduction across all entities. Frontend tests (~4). Frontend only. | |

### Timeline

```
Stage 0:  [459A]                                                <- migration + customer entity
Stage 1:  [459B]  //  [460A]                                    <- customer controller + P/T/I entities (parallel)
Stage 2:  [461A]  //  [462A]                                    <- services + template context (parallel)
Stage 3:  [461B]  //  [462B]                                    <- prerequisites + pack cleanup (parallel)
Stage 4:  [463A]  //  [464A]                                    <- customer form + P/T/I forms (parallel)
Stage 5:  [463B]  //  [464B]                                    <- customer detail + P/T/I detail (parallel)
```

---

## Epic 459: Foundation -- Migration + Customer Entity/DTO Updates

**Goal**: Lay the database foundation for the entire phase by adding 21 columns across 4 tables with 4 indexes in a single migration. Extend the Customer entity with 13 new fields, update the Customer DTOs (request and response records), and extend CustomerService to accept and persist the new fields on create and update. This is the largest entity change (13 of 21 promoted fields are on Customer) and must be completed before any other epic starts.

**References**: Architecture doc Sections 1.1 (Customer columns), 1.5 (migration SQL), 2.1 (Customer entity/DTO); ADR-238 (entity_type as VARCHAR, not enum).

**Dependencies**: Phase 11 complete (custom fields infrastructure exists). Phase 61 V86 migration applied (if Phase 61 runs before Phase 63).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **459A** | 459.1--459.7 | V87 tenant migration (ALTER TABLE adding 21 columns across customers, projects, tasks, invoices + 4 indexes). Customer entity extension (13 new fields: registrationNumber, addressLine1, addressLine2, city, stateProvince, postalCode, country, taxNumber, contactName, contactEmail, contactPhone, entityType, financialYearEnd). CustomerRequest/CustomerResponse DTO updates. CustomerService create/update extensions. Integration tests (~6). Backend only. | **Done** (PR #984) |
| **459B** | 459.8--459.11 | Customer controller endpoint updates: create and update endpoints accept the 13 new fields. Controller integration tests verifying round-trip persistence and response shape (~4). Backend only. | **Done** (PR #984) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 459.1 | Create V87 tenant migration | 459A | -- | New file: `backend/src/main/resources/db/migration/tenant/V87__phase63_custom_field_graduation.sql`. Full SQL from architecture doc Section 1.5: ALTER TABLE `customers` ADD 13 columns + 3 indexes (`idx_customers_registration_number`, `idx_customers_tax_number`, `idx_customers_entity_type`). ALTER TABLE `projects` ADD 3 columns + 1 index (`idx_projects_work_type`). ALTER TABLE `tasks` ADD `estimated_hours` DECIMAL(8,2). ALTER TABLE `invoices` ADD 4 columns. All columns nullable, no NOT NULL constraints. Verify migration number at implementation time (V86 may already exist from Phase 61). Pattern: `V85__create_trust_accounting_tables.sql` for syntax conventions. |
| 459.2 | Extend Customer entity with 13 new fields | 459A | 459.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java`. Add 13 fields with `@Column` annotations: `registrationNumber` VARCHAR(100), `addressLine1` VARCHAR(255), `addressLine2` VARCHAR(255), `city` VARCHAR(100), `stateProvince` VARCHAR(100), `postalCode` VARCHAR(20), `country` VARCHAR(2), `taxNumber` VARCHAR(100), `contactName` VARCHAR(255), `contactEmail` VARCHAR(255), `contactPhone` VARCHAR(50), `entityType` VARCHAR(30) (String, not enum -- per ADR-238), `financialYearEnd` LocalDate. All nullable. Add getters/setters. Pattern: existing fields on `Customer.java` (e.g., `name`, `email`, `phone`, `idNumber`). |
| 459.3 | Update CustomerRequest DTO | 459A | 459.2 | Modify: CustomerRequest record (likely in `customer/` package or nested in controller). Add all 13 fields as optional parameters. Use Java `Optional` or nullable types consistent with existing DTO conventions in the codebase. Pattern: existing CustomerRequest fields. |
| 459.4 | Update CustomerResponse DTO | 459A | 459.2 | Modify: CustomerResponse record. Add all 13 fields. Continue to include `customFields` map for remaining genuinely custom fields. Pattern: existing CustomerResponse fields. |
| 459.5 | Extend CustomerService create/update to persist new fields | 459A | 459.2, 459.3 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java`. In create method: map all 13 new request fields to entity setters. In update method: same. Standard setter calls -- no special business logic. EntityType and workType validation (service-layer validation per ADR-238) is deferred to Epic 461. |
| 459.6 | Write migration smoke test | 459A | 459.1 | Extend existing migration test pattern or create: `backend/src/test/java/.../customer/V87MigrationTest.java`. 2 tests: (1) V87 applies without error on a schema with all prior migrations applied, (2) verify all new columns exist with correct types (query `information_schema.columns`). Integration test with Testcontainers. Pattern: Phase 61 V86 migration tests (epic 452 task 452.4). |
| 459.7 | Write Customer entity/service integration tests | 459A | 459.5 | Extend or create: `backend/src/test/java/.../customer/CustomerServiceIntegrationTest.java`. 4 tests: (1) create customer with all 13 new fields, verify persistence, (2) create customer with no new fields (all null), verify backward compat, (3) update customer to add address fields, verify persistence, (4) verify financialYearEnd persists as correct LocalDate. Use `TestCustomerFactory` -- extend it if needed. |
| 459.8 | Update Customer controller create endpoint | 459B | 459A | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java`. The `POST /api/customers` endpoint already accepts CustomerRequest -- the DTO changes from 459.3 flow through automatically. Verify the controller remains a pure one-liner delegation to CustomerService. No business logic in controller. |
| 459.9 | Update Customer controller update endpoint | 459B | 459A | Modify: same file. `PUT /api/customers/{id}` -- same verification. The DTO changes propagate through the thin controller. |
| 459.10 | Write controller integration tests -- create with new fields | 459B | 459.8 | Extend or create: `backend/src/test/java/.../customer/CustomerControllerIntegrationTest.java`. 2 tests: (1) POST with all 13 new fields returns 201 with all fields in response, (2) POST with only core fields (no new fields) returns 201 with null values for new fields. Use MockMvc with JWT mock. |
| 459.11 | Write controller integration tests -- update with new fields | 459B | 459.9 | 2 tests: (1) PUT with address fields on existing customer returns 200 with updated fields, (2) PUT clearing a field (setting to null) returns 200 with null. |

### Key Files

**Slice 459A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V87__phase63_custom_field_graduation.sql`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/customer/V87MigrationTest.java`

**Slice 459A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` -- Add 13 fields with @Column annotations
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` -- Extend create/update to persist new fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` -- DTO changes (CustomerRequest/CustomerResponse records, likely nested or in dto sub-package)

**Slice 459A -- Read for context:**
- `backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql` -- Migration syntax reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerType.java` -- Existing enum pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/LifecycleStatus.java` -- Existing enum pattern

**Slice 459B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` -- Verify thin delegation after DTO changes

**Slice 459B -- Create:**
- Controller integration tests for new customer fields

### Architecture Decisions

- **Single migration for all 4 tables**: All 21 columns and 4 indexes go into one V87 migration. Splitting per entity would create ordering dependencies between epics and slow down the pipeline. The migration is purely additive (ALTER TABLE ADD COLUMN) and backward-compatible -- no data backfill, no NOT NULL constraints.
- **Customer entity first**: Customer has 13 of 21 promoted fields, making it the largest and most critical entity change. Other entities (Project, Task, Invoice) are handled in a separate, smaller epic (460) that can run in parallel with 459B.
- **entity_type as String, not enum (ADR-238)**: Customer.entityType is `String` with `@Column(name = "entity_type", length = 30)`. No `@Enumerated`. Valid values differ per vertical profile (PTY_LTD for accounting-za, INDIVIDUAL for legal-za). Service-layer validation is deferred to Epic 461.
- **financial_year_end as LocalDate**: The JSONB version stored this as a string. The new column is `DATE` type, mapped to `java.time.LocalDate`. No string parsing needed downstream.
- **Two slices for Customer**: Entity + service + migration (459A) is separated from controller + endpoint tests (459B) to keep each slice under the 10-file limit. 459A is the foundation slice; 459B is a lightweight follow-up.

---

## Epic 460: Project/Task/Invoice Entity/DTO Updates + Enums

**Goal**: Extend the remaining three entities (Project, Task, Invoice) with their promoted fields and create the two new Java enums (`ProjectPriority`, `TaxType`). Update all DTOs and service create/update methods to accept and persist the new fields. This is a smaller epic than 459 because it covers only 8 fields across 3 entities (vs. 13 fields on Customer alone).

**References**: Architecture doc Sections 1.2 (Project), 1.3 (Task), 1.4 (Invoice), 2.2-2.4 (entity/DTO updates); ADR-238 (workType as VARCHAR).

**Dependencies**: Epic 459A (migration must be applied -- all columns exist).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **460A** | 460.1--460.8 | Create `ProjectPriority` enum (LOW, MEDIUM, HIGH). Create `TaxType` enum (VAT, GST, SALES_TAX, NONE). Extend Project entity (3 fields: referenceNumber, priority, workType). Extend Task entity (1 field: estimatedHours). Extend Invoice entity (4 fields: poNumber, taxType, billingPeriodStart, billingPeriodEnd). Update all DTOs. Extend services. Integration tests (~6). Backend only. | **Done** (PR #985) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 460.1 | Create `ProjectPriority` enum | 460A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectPriority.java`. Three values: `LOW`, `MEDIUM`, `HIGH`. Simple enum. Note: intentionally 3 values while existing `TaskPriority` has 4 (LOW, MEDIUM, HIGH, URGENT) -- project priority is a planning signal, not an operational triage. Pattern: `TaskPriority.java` in the `task/` package. |
| 460.2 | Create `TaxType` enum | 460A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/TaxType.java`. Four values: `VAT`, `GST`, `SALES_TAX`, `NONE`. Simple enum. Pattern: `InvoiceStatus.java` or `InvoiceLineType.java` in the `invoice/` package. |
| 460.3 | Extend Project entity with 3 new fields | 460A | 460.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java`. Add: `referenceNumber` VARCHAR(100), `priority` ProjectPriority with `@Enumerated(EnumType.STRING)`, `workType` VARCHAR(50) (String, not enum -- per ADR-238). All nullable. Pattern: existing fields on Project (e.g., `name`, `description`, `status`). |
| 460.4 | Extend Task entity with estimatedHours | 460A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java`. Add: `estimatedHours` BigDecimal with `@Column(name = "estimated_hours", precision = 8, scale = 2)` and `@DecimalMin("0")`. Nullable. Pattern: existing Task fields. |
| 460.5 | Extend Invoice entity with 4 new fields | 460A | 460.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java`. Add: `poNumber` VARCHAR(100), `taxType` TaxType with `@Enumerated(EnumType.STRING)`, `billingPeriodStart` LocalDate, `billingPeriodEnd` LocalDate. All nullable. Pattern: existing Invoice fields (e.g., `issueDate`, `dueDate`, `paymentTerms`). |
| 460.6 | Update Project/Task/Invoice DTOs and services | 460A | 460.3, 460.4, 460.5 | Modify: ProjectService + ProjectController DTOs (request/response records), TaskService + TaskController DTOs, InvoiceService + InvoiceController DTOs (in `invoice/dto/`). Add new fields to request and response records. Extend service create/update methods with standard setter calls. Pattern: how CustomerService was extended in 459.5. |
| 460.7 | Write Project/Task entity integration tests | 460A | 460.6 | Extend or create test files. 3 tests: (1) create project with all 3 new fields, (2) create task with estimatedHours, verify BigDecimal precision, (3) update project workType, verify persistence. |
| 460.8 | Write Invoice entity integration tests | 460A | 460.6 | 3 tests: (1) create invoice context with taxType and poNumber, (2) create invoice with billing period dates, verify LocalDate persistence, (3) verify backward compat -- create invoice without new fields. |

### Key Files

**Slice 460A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectPriority.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/TaxType.java`

**Slice 460A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java` -- Add 3 fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectService.java` -- Extend create/update
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java` -- Add estimatedHours
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` -- Extend create/update
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` -- Add 4 fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Extend create/update

**Slice 460A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskPriority.java` -- Enum pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceStatus.java` -- Enum pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java` -- DTO location reference

### Architecture Decisions

- **Single slice for 3 entities**: Project (3 fields), Task (1 field), and Invoice (4 fields) total 8 fields -- small enough for one slice. The entity changes are mechanical (add fields, add to DTOs, add setter calls in services). File count: 2 new enums + 6 modified files (3 entities + 3 services) + 2 test files = 10 files, within limits.
- **ProjectPriority as Java enum**: Unlike `entity_type` and `work_type`, project priority has a fixed, universal value set (LOW, MEDIUM, HIGH) that does not vary by vertical. Java enum with `@Enumerated(EnumType.STRING)` is the correct choice per ADR-238's boundary rule.
- **TaxType as Java enum**: Same rationale -- VAT, GST, SALES_TAX, NONE are universal tax categories, not vertical-specific. The actual tax rate percentage is handled by the existing TaxRate entity (Phase 26); TaxType just categorizes the type.
- **workType as String**: `Project.workType` unifies accounting-za's `engagement_type` and legal-za's `matter_type` -- completely disjoint value sets. VARCHAR with service-layer validation per ADR-238.

---

## Epic 461: Service Layer Updates -- Conflict Check, Deadline, Prerequisite

**Goal**: Update the three service classes that currently read promoted fields from JSONB (`customer.getCustomFields().get("slug")`) to use the new entity getter calls (`customer.getRegistrationNumber()`). Also update `CustomFieldFilterUtil` to generate proper column-based WHERE clauses for promoted fields. Add structural prerequisite checks that run alongside existing custom field prerequisite checks.

**References**: Architecture doc Sections 3.1-3.5 (service updates), 7 (prerequisite context migration).

**Dependencies**: Epics 459 and 460 (all entities must have the new fields).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **461A** | 461.1--461.5 | ConflictCheckService: switch `registration_number` JSONB read to `customer.getRegistrationNumber()`. DeadlineCalculationService: switch `financial_year_end` and `engagement_type` JSONB reads to entity getters. CustomFieldFilterUtil: add promoted field column mappings. Integration tests (~8). Backend only. | **Done** (PR #986) |
| **461B** | 461.6--461.10 | PrerequisiteService extension: `StructuralPrerequisiteCheck` class mapping prerequisite contexts to entity column null-checks. Invoice generation checks: addressLine1, city, country, taxNumber. Proposal send checks: contactName, contactEmail. Integration tests (~6). Backend only. | **Done** (PR #986) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 461.1 | Update ConflictCheckService to use entity getter | 461A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckService.java`. Replace `customer.getCustomFields().get("registration_number")` with `customer.getRegistrationNumber()`. This enables `WHERE registration_number = ?` in the conflict query instead of JSONB extraction. May need to update `ConflictCheckRepository` query if it currently uses JSONB extraction in JPQL/native SQL. |
| 461.2 | Update DeadlineCalculationService to use entity getters | 461A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineCalculationService.java`. Replace `customer.getCustomFields().get("financial_year_end")` with `customer.getFinancialYearEnd()` (now a proper `LocalDate`, no string parsing). Replace `project.getCustomFields().get("engagement_type")` with `project.getWorkType()`. Note: `tax_year` stays in JSONB (accounting-specific, not promoted). |
| 461.3 | Update CustomFieldFilterUtil for promoted fields | 461A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/CustomFieldFilterUtil.java`. For promoted field slugs (registration_number, city, country, entity_type, work_type, etc.), generate proper column-based WHERE clauses instead of JSONB extraction. Add a mapping from promoted field slugs to their actual column names. This is a significant query performance improvement. May also need to update `CustomFieldFilterHandler.java`. |
| 461.4 | Write ConflictCheckService integration tests | 461A | 461.1 | Extend or create: conflict check test file. 4 tests: (1) conflict check finds match by registration_number on entity column, (2) conflict check with null registration_number returns no match, (3) conflict check still works for non-promoted custom fields via JSONB, (4) performance -- verify query plan uses `idx_customers_registration_number` index (optional, informational). |
| 461.5 | Write DeadlineCalculationService integration tests | 461A | 461.2 | Extend or create: deadline calculation test file. 4 tests: (1) deadline calculation uses financialYearEnd from entity column (LocalDate), (2) deadline calculation uses workType from entity column, (3) deadline calculation still reads tax_year from JSONB (not promoted), (4) backward compat -- customer with null financialYearEnd does not crash calculation. |
| 461.6 | Create StructuralPrerequisiteCheck class | 461B | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/StructuralPrerequisiteCheck.java`. A record or class that maps `PrerequisiteContext` values to a list of entity field null-checks. E.g., `INVOICE_GENERATION -> [(Customer, "addressLine1"), (Customer, "city"), (Customer, "country"), (Customer, "taxNumber")]`. `PROPOSAL_SEND -> [(Customer, "contactName"), (Customer, "contactEmail")]`. The check method accepts an entity and a context, returns a list of `PrerequisiteViolation` for null fields. Pattern: existing `PrerequisiteCheck.java` and `PrerequisiteViolation.java` in the `prerequisite/` package. |
| 461.7 | Integrate StructuralPrerequisiteCheck into PrerequisiteService | 461B | 461.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java`. After running existing custom field prerequisite checks, also run structural prerequisite checks. Merge violations from both sources into the same `PrerequisiteCheck` response. The caller does not know whether a violation came from a structural field or a custom field. |
| 461.8 | Update PrerequisiteService for invoice generation context | 461B | 461.7 | Verify: the `INVOICE_GENERATION` context now checks `customer.getAddressLine1() != null`, `customer.getCity() != null`, `customer.getCountry() != null`, `customer.getTaxNumber() != null` via structural checks. These should replace the equivalent JSONB-based `requiredForContexts` checks on the old FieldDefinitions. |
| 461.9 | Write StructuralPrerequisiteCheck unit tests | 461B | 461.6 | New test file. 3 tests: (1) customer with all required fields returns empty violations for INVOICE_GENERATION, (2) customer missing addressLine1 returns violation with field name "addressLine1", (3) customer missing contactEmail returns violation for PROPOSAL_SEND context. Pure unit tests, no Spring context. |
| 461.10 | Write PrerequisiteService integration tests | 461B | 461.7 | Extend prerequisite integration tests. 3 tests: (1) INVOICE_GENERATION prerequisite check on customer with complete address returns pass, (2) INVOICE_GENERATION on customer missing city returns structural violation, (3) PROPOSAL_SEND on customer missing contactEmail returns structural violation alongside any custom field violations. |

### Key Files

**Slice 461A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckService.java` -- Switch from JSONB to entity getter
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineCalculationService.java` -- Switch from JSONB to entity getters
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/CustomFieldFilterUtil.java` -- Add promoted field column mappings

**Slice 461A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheckRepository.java` -- Query patterns
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/CustomFieldFilterHandler.java` -- Filter handler patterns

**Slice 461B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/StructuralPrerequisiteCheck.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/prerequisite/StructuralPrerequisiteCheckTest.java`

**Slice 461B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java` -- Integrate structural checks

**Slice 461B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteCheck.java` -- Response format
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteViolation.java` -- Violation format
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteContext.java` -- Context enum values

### Architecture Decisions

- **Two slices for service layer**: ConflictCheck + Deadline + Filter updates (461A) are distinct from Prerequisite updates (461B). The prerequisite work involves creating a new class (`StructuralPrerequisiteCheck`) and modifying PrerequisiteService, while 461A is about switching existing JSONB reads. Separating keeps each slice focused.
- **Structural checks alongside, not replacing, custom field checks**: The `StructuralPrerequisiteCheck` runs in addition to existing `requiredForContexts` custom field checks. This means existing tenants with old FieldDefinitions that still have `requiredForContexts` entries for promoted fields will get duplicate checks -- harmless because both check the same thing (the entity field, which is null for old entities). New tenants won't have these FieldDefinitions (pack cleanup removes them in 462B).
- **tax_year stays in JSONB**: `DeadlineCalculationService` continues to read `tax_year` from JSONB. It scores 1/4 on the ADR-237 rubric (only a vertical-specific service read) and is not promoted.
- **CustomFieldFilterUtil promoted field mapping**: The util needs a static mapping from promoted slugs to column names (e.g., `"city" -> "city"`, `"entity_type" -> "entity_type"`, `"registration_number" -> "registration_number"`). When a saved view filters by a promoted field, the util generates a direct column WHERE clause instead of JSONB extraction.

---

## Epic 462: Template Context Builders + Variable Metadata + Pack JSON Cleanup

**Goal**: Update the three template context builders to expose promoted fields as direct template variables (e.g., `${customer.taxNumber}`) with backward-compatible aliases for the old `customFields.slug` access pattern. Update the VariableMetadataRegistry. Clean up pack JSON files by removing promoted field entries and deleting empty pack files.

**References**: Architecture doc Sections 4.1-4.4 (template context), 6.1-6.2 (pack cleanup).

**Dependencies**: Epics 459 and 460 (entities must have the new fields for context builders to read them).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **462A** | 462.1--462.5 | CustomerContextBuilder, ProjectContextBuilder, InvoiceContextBuilder: add promoted fields as direct template variables. Add backward-compat aliases (`customFields.tax_number`, `customFields.vat_number -> taxNumber`, `customFields.primary_contact_name -> contactName`, etc.). Update VariableMetadataRegistry to list promoted fields in entity field groups. Integration tests (~6). Backend only. | **Done** (PR #987) |
| **462B** | 462.6--462.10 | Delete `common-customer.json` (after removing promoted fields, it may be empty or near-empty -- verify). Delete `common-invoice.json` (empty after removing promoted fields). Slim 6 other pack files by removing promoted field entries. Update FieldPackSeeder to handle deleted files. Tests (~4). Backend only. | **Done** (PR #987) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 462.1 | Update CustomerContextBuilder | 462A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java`. Add direct template variables: `taxNumber`, `addressLine1`, `addressLine2`, `city`, `stateProvince`, `postalCode`, `country`, `contactName`, `contactEmail`, `contactPhone`, `entityType`, `financialYearEnd`, `registrationNumber`. Add backward-compat aliases in the `customFields` map: `customFields.tax_number -> customer.getTaxNumber()`, `customFields.vat_number -> customer.getTaxNumber()` (renamed slug), `customFields.primary_contact_name -> customer.getContactName()`, `customFields.primary_contact_email -> customer.getContactEmail()`, `customFields.primary_contact_phone -> customer.getContactPhone()`, `customFields.acct_company_registration_number -> customer.getRegistrationNumber()`, `customFields.client_type -> customer.getEntityType()`, `customFields.acct_entity_type -> customer.getEntityType()`. Pattern: existing context builder methods. |
| 462.2 | Update ProjectContextBuilder | 462A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilder.java`. Add: `referenceNumber`, `priority`, `workType`. Backward-compat aliases: `customFields.reference_number`, `customFields.priority`, `customFields.engagement_type -> workType`, `customFields.matter_type -> workType`. |
| 462.3 | Update InvoiceContextBuilder | 462A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/InvoiceContextBuilder.java`. Add: `poNumber`, `taxType`, `billingPeriodStart`, `billingPeriodEnd`. Backward-compat aliases: `customFields.purchase_order_number -> poNumber`, `customFields.tax_type`, `customFields.billing_period_start`, `customFields.billing_period_end`. |
| 462.4 | Update VariableMetadataRegistry | 462A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java`. Move promoted fields from the "Custom Fields" group to the "Customer Fields" / "Project Fields" / "Invoice Fields" groups. This affects the template editor variable picker in the frontend. |
| 462.5 | Write template context integration tests | 462A | 462.1, 462.2, 462.3 | Extend or create: template context test file. 6 tests: (1) customer context includes `taxNumber` directly, (2) customer context includes `customFields.vat_number` alias pointing to same value, (3) customer context includes `customFields.primary_contact_name` alias, (4) project context includes `workType` and `customFields.engagement_type` alias, (5) invoice context includes `poNumber` and `customFields.purchase_order_number` alias, (6) VariableMetadataRegistry lists promoted fields in entity groups, not custom fields group. |
| 462.6 | Remove promoted fields from `common-customer.json` | 462B | -- | Modify: `backend/src/main/resources/field-packs/common-customer.json`. Remove entire "Contact & Address" group entries: `address_line1`, `address_line2`, `city`, `state_province`, `postal_code`, `country`, `tax_number`, `phone`. If the file becomes empty (no remaining fields), delete it entirely. If fields remain (verify against architecture doc Section 6.2), keep the file with just the remaining fields. |
| 462.7 | Remove promoted fields from vertical customer packs | 462B | -- | Modify: `backend/src/main/resources/field-packs/accounting-za-customer.json` -- remove `acct_company_registration_number`, `vat_number`, `acct_entity_type`, `financial_year_end`, `primary_contact_name`, `primary_contact_email`, `primary_contact_phone`, `registered_address`. Keep `postal_address` and all trust-specific fields. Modify: `backend/src/main/resources/field-packs/legal-za-customer.json` -- remove `client_type`, `id_passport_number`, `registration_number`, `physical_address`. Keep `postal_address`. |
| 462.8 | Remove promoted fields from project/task/invoice packs | 462B | -- | Modify: `backend/src/main/resources/field-packs/common-project.json` -- remove `reference_number`, `priority`. Keep `category`. Modify: `backend/src/main/resources/field-packs/accounting-za-project.json` -- remove `engagement_type`. Keep `tax_year`, `sars_submission_deadline`, `assigned_reviewer`, `complexity`. Modify: `backend/src/main/resources/field-packs/legal-za-project.json` -- remove `matter_type`. Keep `case_number`, `court_name`, etc. Modify: `backend/src/main/resources/field-packs/common-task.json` -- remove `priority`, `estimated_hours`. Keep `category`. Delete: `backend/src/main/resources/field-packs/common-invoice.json` (empty after removing `purchase_order_number`, `payment_reference`, `tax_type`, `billing_period_start`, `billing_period_end`). |
| 462.9 | Update FieldPackSeeder to handle deleted files | 462B | 462.8 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`. Remove references to deleted pack files (`common-invoice.json`, and possibly `common-customer.json` if deleted). Verify the seeder does not crash when a previously referenced pack file is absent. |
| 462.10 | Write pack cleanup verification tests | 462B | 462.8, 462.9 | 4 tests: (1) FieldPackSeeder runs without error with updated pack files, (2) after seeding, no FieldDefinitions exist for promoted slugs (registration_number, entity_type, etc.) on new tenants, (3) `common-invoice.json` pack reference is removed -- no invoice custom field definitions seeded for new tenants (only remaining custom invoice fields from vertical packs, if any), (4) accounting-za-customer pack still seeds `postal_address` and trust fields. |

### Key Files

**Slice 462A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/ProjectContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/InvoiceContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/VariableMetadataRegistry.java`

**Slice 462A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateContextBuilder.java` -- Base interface/abstract class
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateContextHelper.java` -- Helper patterns

**Slice 462B -- Modify:**
- `backend/src/main/resources/field-packs/common-customer.json`
- `backend/src/main/resources/field-packs/accounting-za-customer.json`
- `backend/src/main/resources/field-packs/legal-za-customer.json`
- `backend/src/main/resources/field-packs/common-project.json`
- `backend/src/main/resources/field-packs/accounting-za-project.json`
- `backend/src/main/resources/field-packs/legal-za-project.json`
- `backend/src/main/resources/field-packs/common-task.json`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackSeeder.java`

**Slice 462B -- Delete:**
- `backend/src/main/resources/field-packs/common-invoice.json`

**Slice 462B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackDefinition.java` -- Pack definition model
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldPackField.java` -- Field definition within pack

### Architecture Decisions

- **Two slices: context builders vs pack cleanup**: Context builders (462A) are Java code modifications with alias logic. Pack cleanup (462B) is JSON file editing and seeder updates. Separating keeps each slice focused and allows parallel execution.
- **Backward-compatible aliases for one phase**: Context builders add `customFields.slug` entries that point to the same values as the new direct variables. This ensures existing document templates that use `${customer.customFields.tax_number}` continue to work. The aliases are marked for removal in a follow-up phase (noted in architecture doc "Style & Boundaries" section).
- **Renamed slug aliases**: Some packs used different slugs for the same concept: `vat_number` (accounting-za) and `tax_number` (common) both map to `customer.taxNumber`. `primary_contact_name` maps to `customer.contactName`. `engagement_type` and `matter_type` both map to `project.workType`. All old slugs get aliases.
- **common-invoice.json deletion**: After removing all promoted fields, the common-invoice pack has no remaining entries. The file is deleted and the FieldPackSeeder is updated to not reference it. Invoice custom fields are extinct -- the pack file section in architecture doc Section 6.2 confirms "Invoice: None remaining."

---

## Epic 463: Frontend -- Customer Form Restructuring + Detail Page

**Goal**: Move the 13 promoted customer fields out of the generic `CustomFieldSection` and into the main customer create/edit form as purpose-built typed inputs organized into three logical sections: Address, Contact, and Business Details. Update the customer detail page to display promoted fields in formatted sections (address block, contact card) rather than in the custom fields panel. Reduce the `CustomFieldSection` scope for customers to only show genuinely custom fields.

**References**: Architecture doc Sections 5.1 (customer form), 5.5 (customer detail page).

**Dependencies**: Epic 459B (API accepts/returns new fields). Epic 461 (prerequisite checks reference structural fields, affecting form validation hints).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **463A** | 463.1--463.7 | Customer create/edit form: Address section (addressLine1, addressLine2, city, stateProvince, postalCode, country select). Contact section (contactName, contactEmail, contactPhone). Business Details section (registrationNumber, taxNumber, entityType select, financialYearEnd date picker). Update TypeScript types + Zod schema. Server actions. Frontend tests (~6). Frontend only. | |
| **463B** | 463.8--463.12 | Customer detail page: formatted address block component, contact card component, business details section. Reduce CustomFieldSection to only render genuinely custom fields (exclude promoted slugs). Frontend tests (~4). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 463.1 | Update Customer TypeScript types | 463A | -- | Modify: `frontend/lib/schemas/customer.ts` or types file. Add all 13 new fields to Customer interface/type. Add to create/edit Zod schemas: `addressLine1` z.string().max(255).optional(), `country` z.string().length(2).optional() (ISO 3166-1 alpha-2), `contactEmail` z.string().email().optional(), `financialYearEnd` z.string() or z.date() (date picker), etc. All optional (nullable). Pattern: existing customer schema fields. |
| 463.2 | Create Address section for customer form | 463A | 463.1 | Modify: `frontend/components/customers/create-customer-dialog.tsx` and `frontend/components/customers/edit-customer-dialog.tsx`. Add an "Address" section with 6 fields: addressLine1 (Input), addressLine2 (Input), city (Input), stateProvince (Input), postalCode (Input), country (Select -- ISO 3166-1 alpha-2 dropdown). Use Shadcn Form components (FormField, FormItem, FormLabel, FormControl, FormMessage). Pattern: existing form sections in create-customer-dialog. |
| 463.3 | Create Contact section for customer form | 463A | 463.1 | Same files as 463.2. Add a "Contact" section with 3 fields: contactName (Input), contactEmail (Input type="email"), contactPhone (Input type="tel"). |
| 463.4 | Create Business Details section for customer form | 463A | 463.1 | Same files as 463.2. Add a "Business Details" section with 4 fields: registrationNumber (Input), taxNumber (Input), entityType (Select -- values dependent on vertical profile, or all values combined), financialYearEnd (DatePicker). |
| 463.5 | Update customer server actions to send new fields | 463A | 463.1 | Modify: `frontend/app/(app)/org/[slug]/customers/actions.ts`. Update create and update server actions to include the 13 new fields in API request payloads. Map form data to API request format. |
| 463.6 | Update intake-actions if applicable | 463A | 463.5 | Review: `frontend/app/(app)/org/[slug]/customers/intake-actions.ts`. If the smart customer intake dialog (Phase 33 epic 246) also creates customers, update it to support the new fields. |
| 463.7 | Write frontend tests for customer form | 463A | 463.2, 463.3, 463.4 | 6 tests: (1) Address section renders all 6 fields, (2) country dropdown shows ISO codes, (3) Contact section validates email format, (4) Business Details section renders entityType select, (5) form submits with all new fields, (6) form submits with no new fields (backward compat). Use Vitest + testing-library. Pattern: existing customer test files. |
| 463.8 | Create CustomerAddressBlock component | 463B | -- | New file: `frontend/components/customers/customer-address-block.tsx`. Renders a formatted address: line1, line2 (if present), city + stateProvince + postalCode, country. Display-only component for detail page. Use Shadcn Card for containment. Pattern: existing detail section components. |
| 463.9 | Create CustomerContactCard component | 463B | -- | New file: `frontend/components/customers/customer-contact-card.tsx`. Renders contact info: name, email (with mailto link), phone (with tel link). Display-only. Pattern: similar display components in the codebase. |
| 463.10 | Update customer detail page to show promoted fields | 463B | 463.8, 463.9 | Modify: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`. Add the AddressBlock, ContactCard, and Business Details section (registrationNumber, taxNumber, entityType, financialYearEnd) to the main content area. These replace the equivalent entries that were previously rendered by CustomFieldSection. |
| 463.11 | Reduce CustomFieldSection scope for customers | 463B | 463.10 | Modify: the component or configuration that feeds fields to `CustomFieldSection` for customers. Exclude promoted field slugs (address_line1, tax_number, entity_type, registration_number, contact_name, etc.) so they are not rendered twice. This may involve passing a `promotedSlugs` exclusion list or filtering at the data-fetching layer. The exact mechanism depends on how CustomFieldSection receives its field list. |
| 463.12 | Write frontend tests for customer detail sections | 463B | 463.10, 463.11 | 4 tests: (1) AddressBlock renders formatted address, (2) ContactCard renders email as mailto link, (3) detail page shows promoted fields in proper sections, (4) CustomFieldSection no longer renders promoted fields. |

### Key Files

**Slice 463A -- Modify:**
- `frontend/lib/schemas/customer.ts` -- Add 13 fields to Zod schema
- `frontend/components/customers/create-customer-dialog.tsx` -- Add Address/Contact/Business sections
- `frontend/components/customers/edit-customer-dialog.tsx` -- Same sections
- `frontend/app/(app)/org/[slug]/customers/actions.ts` -- Update server actions
- `frontend/app/(app)/org/[slug]/customers/intake-actions.ts` -- Review/update

**Slice 463B -- Create:**
- `frontend/components/customers/customer-address-block.tsx`
- `frontend/components/customers/customer-contact-card.tsx`

**Slice 463B -- Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` -- Add promoted field sections
- CustomFieldSection data feeding (mechanism TBD based on current implementation)

**Slice 463B -- Read for context:**
- `frontend/components/customers/customer-tabs.tsx` -- Tab structure for customer detail
- `frontend/components/customers/intake-fields-section.tsx` -- Existing section pattern

### Architecture Decisions

- **Two slices: form vs detail page**: Form restructuring (463A) is a significant change affecting create and edit dialogs with schema updates. Detail page updates (463B) are display-only components. Separating allows the form work to land first, enabling data entry before the detail page catches up.
- **Three form sections for Customer**: Address, Contact, and Business Details mirror the logical grouping in the architecture doc. This is more organized than a flat list of 13 fields. Each section has a clear heading.
- **Country dropdown with ISO 3166-1 alpha-2**: Ship with the same country list as the existing common-customer pack's `country` dropdown options. This avoids adding a new dependency for a country list library.
- **CustomFieldSection exclusion via promoted slugs**: Rather than modifying CustomFieldSection itself (which would affect all entities), the approach is to filter out promoted slugs at the data level before passing to CustomFieldSection. This keeps the generic component unchanged.

---

## Epic 464: Frontend -- Project/Task/Invoice Form + Detail Updates

**Goal**: Add promoted fields to Project, Task, and Invoice create/edit forms as purpose-built typed inputs. Update Project and Invoice detail pages to display the new fields. Reduce `CustomFieldSection` scope across all entities to exclude promoted field slugs.

**References**: Architecture doc Sections 5.2 (project form), 5.3 (task form), 5.4 (invoice form), 5.5 (detail pages).

**Dependencies**: Epic 460 (API accepts/returns new fields for project/task/invoice). Epic 461 (prerequisite checks in place).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **464A** | 464.1--464.7 | Project form: referenceNumber, priority select, workType select. Task form: estimatedHours number input. Invoice form: poNumber, taxType select, billingPeriodStart/End date pickers. TypeScript types + Zod schemas. Server actions. Frontend tests (~6). Frontend only. | |
| **464B** | 464.8--464.12 | Project detail page: display referenceNumber, priority badge, workType. Invoice detail page: display poNumber, taxType, billing period. CustomFieldSection promoted slug exclusion across project, task, and invoice. Frontend tests (~4). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 464.1 | Update Project TypeScript types and Zod schema | 464A | -- | Modify: `frontend/lib/schemas/project.ts`. Add `referenceNumber` z.string().max(100).optional(), `priority` z.enum(["LOW", "MEDIUM", "HIGH"]).optional(), `workType` z.string().max(50).optional(). Add to Project interface/type. |
| 464.2 | Update Task TypeScript types and Zod schema | 464A | -- | Modify: existing task types/schema file (check for `lib/schemas/task.ts` or inline in component). Add `estimatedHours` z.number().min(0).optional() or z.string() with number coercion (step 0.25). |
| 464.3 | Update Invoice TypeScript types and Zod schema | 464A | -- | Modify: existing invoice types/schema file. Add `poNumber` z.string().max(100).optional(), `taxType` z.enum(["VAT", "GST", "SALES_TAX", "NONE"]).optional(), `billingPeriodStart` z.string().optional() (date), `billingPeriodEnd` z.string().optional() (date). |
| 464.4 | Add promoted fields to Project create/edit dialogs | 464A | 464.1 | Modify: `frontend/components/projects/create-project-dialog.tsx` and `frontend/components/projects/edit-project-dialog.tsx`. Add: `referenceNumber` (Input), `priority` (Select: Low, Medium, High), `workType` (Select -- values context-dependent on vertical profile). Use Shadcn Form components. |
| 464.5 | Add estimatedHours to Task create dialog | 464A | 464.2 | Modify: `frontend/components/tasks/create-task-dialog.tsx`. Add: `estimatedHours` (Input type="number", min=0, step=0.25). Also check `task-detail-sheet.tsx` and `task-detail-metadata.tsx` for edit capability. |
| 464.6 | Add promoted fields to Invoice forms | 464A | 464.3 | Modify: `frontend/components/invoices/invoice-draft-form.tsx` (or equivalent invoice create/edit component). Add: `poNumber` (Input), `taxType` (Select: VAT, GST, Sales Tax, None), `billingPeriodStart` (DatePicker), `billingPeriodEnd` (DatePicker). Check also `create-invoice-button.tsx` or `invoice-generation-dialog.tsx` for where new fields should be accepted. |
| 464.7 | Update Project/Task/Invoice server actions | 464A | 464.1, 464.2, 464.3 | Modify: `frontend/app/(app)/org/[slug]/projects/actions.ts`, `frontend/app/(app)/org/[slug]/projects/[id]/task-actions.ts`, `frontend/app/(app)/org/[slug]/invoices/invoice-crud-actions.ts`. Include new fields in API request payloads. |
| 464.8 | Update Project detail page for promoted fields | 464B | -- | Modify: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` and relevant tab components (e.g., `overview-tab.tsx`). Display referenceNumber, priority (as badge with color), workType in the project header or overview section. Pattern: existing project metadata display. |
| 464.9 | Update Invoice detail page for promoted fields | 464B | -- | Modify: `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` and `frontend/components/invoices/invoice-details-readonly.tsx`. Display poNumber, taxType, billingPeriodStart/End in the invoice details section. |
| 464.10 | Reduce CustomFieldSection scope for project/task/invoice | 464B | -- | Apply the same promoted slug exclusion approach from 463.11 to Project, Task, and Invoice entities. Create a shared `PROMOTED_FIELD_SLUGS` constant mapping entity types to their promoted slugs. Use this to filter CustomFieldSection data across all four entity types. |
| 464.11 | Write frontend tests for form updates | 464A | 464.4, 464.5, 464.6 | 6 tests: (1) Project form renders priority select with 3 options, (2) Project form renders workType select, (3) Task form renders estimatedHours as number input with step 0.25, (4) Invoice form renders taxType select with 4 options, (5) Invoice form renders billing period date pickers, (6) forms submit with new fields in payload. |
| 464.12 | Write frontend tests for detail page updates | 464B | 464.8, 464.9, 464.10 | 4 tests: (1) Project detail shows priority as colored badge, (2) Invoice detail shows billing period, (3) CustomFieldSection for project does not render `priority` or `reference_number`, (4) CustomFieldSection for invoice renders no fields (all were promoted or pack deleted). |

### Key Files

**Slice 464A -- Modify:**
- `frontend/lib/schemas/project.ts` -- Add 3 fields
- `frontend/components/projects/create-project-dialog.tsx` -- Add form fields
- `frontend/components/projects/edit-project-dialog.tsx` -- Add form fields
- `frontend/components/tasks/create-task-dialog.tsx` -- Add estimatedHours
- `frontend/components/invoices/invoice-draft-form.tsx` -- Add 4 fields
- `frontend/app/(app)/org/[slug]/projects/actions.ts` -- Update actions
- `frontend/app/(app)/org/[slug]/invoices/invoice-crud-actions.ts` -- Update actions

**Slice 464B -- Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` -- Display promoted fields
- `frontend/components/projects/overview-tab.tsx` -- Project overview metadata
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx` -- Display promoted fields
- `frontend/components/invoices/invoice-details-readonly.tsx` -- Invoice details

**Slice 464B -- Read for context:**
- `frontend/components/tasks/task-detail-metadata.tsx` -- Task metadata display pattern
- `frontend/components/invoices/invoice-totals-section.tsx` -- Invoice section layout pattern

### Architecture Decisions

- **Two slices: forms vs detail pages**: Form updates (464A) enable data entry. Detail page updates (464B) are display-only and can follow. This mirrors the 463A/463B split for Customer.
- **Shared PROMOTED_FIELD_SLUGS constant**: Rather than hardcoding promoted slugs in each entity's CustomFieldSection filter, a shared constant maps entity types to their promoted slugs. This is defined once and used by all four entity types, keeping the exclusion list maintainable.
- **workType select values from vertical profile**: The workType dropdown options depend on the tenant's vertical profile (accounting-za engagement types vs legal-za matter types). The frontend reads these from the org profile context (Phase 49 introduced `OrgProfileProvider`). If no vertical profile is set, show all values or a free-text input.
- **Task priority already structural**: `Task.priority` is already a structural column with its own enum. The only task-related change is adding `estimatedHours`. The `priority` field is removed from common-task.json in 462B but needs no frontend form change (it is already a proper form field).

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/CustomerContextBuilder.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/field-packs/common-customer.json`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/customers/create-customer-dialog.tsx`
