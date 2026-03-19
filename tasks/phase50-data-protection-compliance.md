# Phase 50 — Data Protection Compliance

Phase 50 equips DocTeams tenants with the tooling needed to comply with POPIA (and future jurisdictions) when handling client personal information. It extends existing packages (`datarequest/`, `retention/`, `settings/`, `customer/`) with jurisdiction awareness, richer export coverage, an `ANONYMIZED` lifecycle status, processing activity tracking, and PAIA manual generation via the Phase 12 template engine. New code is minimal: the `ProcessingActivity` entity and CRUD, the `PaiaManualContextBuilder`, a `JurisdictionDefaults` helper, a compliance template pack, and a Data Protection settings tab in the frontend.

The next Flyway tenant migration is **V76**. The next epic number starts at **373**. ADRs 193–196 are already accepted.

**Architecture doc**: `architecture/phase50-data-protection-compliance.md`

**ADRs**:
- [ADR-193](adr/ADR-193-anonymization-vs-deletion.md) — Anonymization vs. deletion (anonymize, preserve financial records, ANONYMIZED terminal status)
- [ADR-194](adr/ADR-194-retention-policy-granularity.md) — Retention policy granularity (per-entity-type, existing model extended)
- [ADR-195](adr/ADR-195-dsar-deadline-calculation.md) — DSAR deadline calculation (jurisdiction default, tenant override capped at statutory max)
- [ADR-196](adr/ADR-196-pre-anonymization-export-storage.md) — Pre-anonymization export storage (retain for `financialRetentionMonths`, key stored in audit event)

**Dependencies on prior phases**:
- Phase 6: `AuditService` / `AuditEventBuilder` — used for `DATA_SUBJECT_EXPORT`, `DATA_SUBJECT_ANONYMIZED`, `RETENTION_PURGE_EXECUTED` events
- Phase 6.5: `Notification` / `NotificationService` — used for retention warning notifications and DSAR deadline warnings
- Phase 8: `OrgSettings` entity, `OrgSettingsService.toSettingsResponse()`, `SettingsResponse` record — all extended in Epic 373
- Phase 12: `TemplateContextBuilder`, `TemplatePackSeeder`, `TemplatePackDefinition`, `GeneratedDocumentService` — used for PAIA manual generation (Epic 378)
- Phase 13: Schema-per-tenant isolation — all new entities are plain `@Entity` with no multitenancy boilerplate
- Phase 20: E2E stack — integration test infrastructure

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 373 | Foundation: V76 Migration, OrgSettings Extension, JurisdictionDefaults | Backend | — | S | 373A | **Done** (PR #768) |
| 374 | Data Export Extension | Backend | 373 | M | 374A, 374B | **Done** (PRs #769, #770) |
| 375 | Anonymization Extension | Backend | 373, 374 | M | 375A, 375B | **Done** (PRs #771, #772) |
| 376 | Retention Extension | Backend | 373 | M | 376A, 376B | **Done** (PRs #773, #774) |
| 377 | DSAR Extension | Backend | 373 | S | 377A | **Done** (PR #775) |
| 378 | Processing Activity CRUD + PAIA Manual Generation | Backend | 373 | M | 378A, 378B | 378A **Done** (PR #776) |
| 379 | Frontend: Data Protection Settings Tab + DSAR Page | Frontend | 373–378 | L | 379A, 379B, 379C | |
| 380 | Frontend: Customer Data Protection Actions | Frontend | 374, 375, 379 | M | 380A | |

---

## Dependency Graph

```
BACKEND FOUNDATION (sequential)
──────────────────────────────────────────────────────────────────

[E373A V76 migration,
 OrgSettings extension (6 new cols),
 JurisdictionDefaults helper,
 LifecycleStatus.ANONYMIZED,
 + integration tests]
        |
        +─────────────────────+───────────────────+────────────────+
        |                     |                   |                |
EXPORT EXTENSION        RETENTION EXTENSION   DSAR EXTENSION  PROCESSING ACTIVITY
(sequential)            (sequential)          (single slice)  + PAIA MANUAL
──────────────────      ──────────────────    ──────────────  (sequential)
        |                     |                   |                |
[E374A DataExportService      |             [E377A deadline        |
 extension: all time          |              calc, new fields,    [E378A ProcessingActivity
 entries, audit events,       |              deadline warning      entity + CRUD service
 custom fields, portal        |              notifications,        + controller
 contacts, structured ZIP     |              + integration tests]  + jurisdiction seeding
 layout                       |                                    + integration tests]
 + integration tests]         |                                         |
        |               [E376A RetentionService                   [E378B PaiaManualContext
[E374B Export endpoints:       extension: TIME_ENTRY,              Builder + compliance
 POST /api/customers/{id}/     notification warnings,              template pack seeder
 data-export,                  financial minimum check,            + generate endpoint
 GET /api/data-exports/{id},   lastEvaluatedAt updates,            + integration tests]
 GET /api/data-exports,        + integration tests]
 DataExportController          |
 + integration tests]    [E376B RetentionController
        |                 + endpoints: GET/PUT policies,
[E375A Anonymization       POST evaluate/execute
 extension: ANONYMIZED     + integration tests]
 status enforcement,
 notes/customFields clear,
 pre-anon export,
 preview endpoint,
 + integration tests]
        |
[E375B Anonymization
 endpoints: POST anonymize,
 GET anonymize/preview,
 AnonymizationController
 + integration tests]
        |
        +──────────────────────────────────────────────────────────+
                                                                   |
FRONTEND (requires all backend epics 373–378)
──────────────────────────────────────────────────────────────────
                                                                   |
        +──────────────────────────+────────────────+─────────────+
        |                          |                |
[E379A Data Protection         [E379B DSAR      [E379C Retention
 settings tab layout,           management       policies table,
 jurisdiction selector,         page: list,      processing register
 info officer fields,           log new request  table, PAIA manual
 + frontend tests]              dialog, status   generate button,
                                transitions,     + frontend tests]
                                deadline badges,
                                + frontend tests]
        |
[E380A Customer detail
 data protection actions:
 export button + dialog,
 anonymize button +
 two-step confirm dialog,
 anonymized state display,
 + frontend tests]
```

**Parallel opportunities**:
- After E373A: E374 (export), E376 (retention), E377 (DSAR), and E378A (processing activity entity) can all run in parallel. E375 must wait for E374A (it calls the export service as a pre-anonymization step).
- Within E379: 379A, 379B, and 379C can run in parallel once all backend epics are done. E380A can run parallel to E379B/C.
- E376A and E376B are sequential (controller depends on extended service).
- E374A and E374B are sequential (controller depends on extended service).
- E375A and E375B are sequential (controller depends on extended service).
- E378A and E378B are sequential (PAIA builder depends on processing activity entity existing).

---

## Implementation Order

### Stage 0: Backend Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 373 | 373A | V76 migration (OrgSettings cols, ProcessingActivity table, RetentionPolicy extension, DataSubjectRequest extension, Customer CHECK), `OrgSettings` entity extension, `JurisdictionDefaults` helper, `LifecycleStatus.ANONYMIZED`, `OrgSettingsService` + `SettingsResponse` extension, integration tests (~6). Backend only. | **Done** (PR #768) |

### Stage 1: Backend Domain Extensions (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 374 | 374A | Extend `DataExportService` (all time entries, custom fields, audit events, portal contacts, structured ZIP). Integration tests (~5). Backend only. | **Done** (PR #769) |
| 1b (parallel) | 376 | 376A | Extend `RetentionService` (TIME_ENTRY support, notification warnings, financial minimum enforcement, `lastEvaluatedAt` updates). Integration tests (~5). Backend only. | **Done** (PR #773) |
| 1c (parallel) | 377 | 377A | Extend `DataSubjectRequestService` (jurisdiction-aware deadline, new fields, deadline warning notifications) + endpoint changes. Integration tests (~5). Backend only. | **Done** (PR #775) |
| 1d (parallel) | 378 | 378A | `ProcessingActivity` entity + `ProcessingActivityRepository` + `ProcessingActivityService` (CRUD + jurisdiction seeding). Integration tests (~5). Backend only. | **Done** (PR #776) |

### Stage 2: Backend Controllers + Extended Services (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 374 | 374B | `DataExportController` (POST trigger, GET status, GET list), integration tests (~5). Backend only. | **Done** (PR #770) |
| 2b (parallel) | 375 | 375A | Extend `DataAnonymizationService` (ANONYMIZED enforcement, notes/customFields clearing, pre-anonymization export, `previewAnonymization()`). Integration tests (~6). Backend only. | **Done** (PR #771) |
| 2c (parallel) | 376 | 376B | `RetentionController` extension (GET list, PUT update, POST evaluate, POST execute). Integration tests (~4). Backend only. | **Done** (PR #774) |
| 2d (parallel) | 378 | 378B | `ProcessingActivityController` (CRUD endpoints) + `PaiaManualContextBuilder` + compliance template pack seeder + generate endpoint. Integration tests (~5). Backend only. | |

### Stage 3: Anonymization Endpoint

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 375 | 375B | `AnonymizationController` (POST anonymize, GET preview) + `DataRequestController` extension. Integration tests (~5). Backend only. | **Done** (PR #772) |

### Stage 4: Frontend Pages (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 379 | 379A | Data Protection settings tab layout, jurisdiction selector, information officer fields, types, actions, frontend tests (~4). Frontend only. | |
| 4b (parallel) | 379 | 379B | DSAR management page (list, log new request dialog, status transitions, deadline badges). Actions + components + frontend tests (~5). Frontend only. | |
| 4c (parallel) | 379 | 379C | Retention policies table (inline edit, validation), processing register table (CRUD), PAIA manual generate button + preview. Frontend tests (~5). Frontend only. | |

### Stage 5: Customer Data Protection Actions

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 380 | 380A | Customer detail export button + confirmation dialog, anonymize button + two-step confirm dialog (preview → type-to-confirm), anonymized state display (badge + disabled fields). Frontend tests (~5). Frontend only. | |

---

## Epic 373: Foundation — V76 Migration, OrgSettings Extension, JurisdictionDefaults

**Goal**: Add all Phase 50 DDL in a single migration (V76), extend `OrgSettings` with 6 data protection columns, create the `JurisdictionDefaults` helper with statutory values, add `ANONYMIZED` to `LifecycleStatus`, and expose the new settings fields in `SettingsResponse`. This is the foundation that all other epics depend on.

**References**: Architecture doc Sections 1.1, 1.2, 1.3. ADR-194 (retention model), ADR-195 (DSAR deadline calculation).

**Dependencies**: None (first slice).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **373A** | 373.1–373.9 | V76 migration (6 OrgSettings cols, `processing_activities` table, `retention_policies` extension, `data_subject_requests` extension, `Customer` CHECK constraint for ANONYMIZED), `OrgSettings` entity extension (+6 fields), `JurisdictionDefaults` helper, `LifecycleStatus.ANONYMIZED`, `OrgSettingsService` + `SettingsResponse` extension, integration tests (~6). Backend only. | **Done** (PR #768) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 373.1 | Create V76 tenant migration | 373A | — | New file: `backend/src/main/resources/db/migration/tenant/V76__data_protection_foundation.sql`. Must be idempotent (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`). DDL groups: (1) `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS data_protection_jurisdiction VARCHAR(10), ADD COLUMN IF NOT EXISTS retention_policy_enabled BOOLEAN DEFAULT false, ADD COLUMN IF NOT EXISTS default_retention_months INTEGER, ADD COLUMN IF NOT EXISTS financial_retention_months INTEGER DEFAULT 60, ADD COLUMN IF NOT EXISTS information_officer_name VARCHAR(255), ADD COLUMN IF NOT EXISTS information_officer_email VARCHAR(255);` (2) `CREATE TABLE IF NOT EXISTS processing_activities (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), category VARCHAR(100) NOT NULL, description TEXT, legal_basis VARCHAR(50), data_subjects VARCHAR(255), retention_period VARCHAR(100), recipients VARCHAR(255), created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now());` (3) `ALTER TABLE retention_policies ADD COLUMN IF NOT EXISTS description TEXT, ADD COLUMN IF NOT EXISTS last_evaluated_at TIMESTAMPTZ;` (4) `ALTER TABLE data_subject_requests ADD COLUMN IF NOT EXISTS jurisdiction VARCHAR(10), ADD COLUMN IF NOT EXISTS deadline_days_override INTEGER;` (5) `ALTER TABLE customers ADD CONSTRAINT IF NOT EXISTS chk_customer_lifecycle_status CHECK (lifecycle_status IN ('PROSPECT','ONBOARDING','ACTIVE','OFFBOARDED','ANONYMIZED'));` Pattern: `V75__add_vertical_modules.sql`. |
| 373.2 | Add 6 data protection fields to `OrgSettings` entity | 373A | 373.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add: `@Column(name = "data_protection_jurisdiction", length = 10) private String dataProtectionJurisdiction;`, `@Column(name = "retention_policy_enabled") private boolean retentionPolicyEnabled = false;`, `@Column(name = "default_retention_months") private Integer defaultRetentionMonths;`, `@Column(name = "financial_retention_months") private Integer financialRetentionMonths = 60;`, `@Column(name = "information_officer_name", length = 255) private String informationOfficerName;`, `@Column(name = "information_officer_email", length = 255) private String informationOfficerEmail;`. Add getters/setters. Pattern: existing field pattern in same file for `verticalProfile`. |
| 373.3 | Create `JurisdictionDefaults` utility class | 373A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/JurisdictionDefaults.java`. Static utility, no Spring bean. Fields per jurisdiction: `dsarDeadlineDays`, `financialRetentionMonthsMinimum`, `mandatoryDocumentType`, `regulatorName`. Static `JurisdictionConfig getDefaults(String jurisdictionCode)` returns a sealed record. Jurisdictions: `ZA` (30 days, 60 months, "paia_section_51_manual", "Information Regulator (South Africa)"). For unknown/null: 30 days, 60 months minimum. Pattern: the `VerticalProfileRegistry` in `verticals/` for static config lookup. |
| 373.4 | Add `ANONYMIZED` to `LifecycleStatus` enum | 373A | 373.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/LifecycleStatus.java`. Add `ANONYMIZED` value. Verify that `CustomerLifecycleGuard` blocks all transitions out of `ANONYMIZED` (it is a terminal state with no valid outgoing transitions). Check that `Customer.anonymize()` currently transitions to `OFFBOARDED` — this will remain for backward compatibility; the extended `DataAnonymizationService` (Epic 375) will set `ANONYMIZED` directly. |
| 373.5 | Extend `OrgSettingsService` with data protection methods | 373A | 373.2, 373.3 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`. Add `updateDataProtectionSettings(DataProtectionSettingsRequest request)` — updates the 6 new fields; validates `financialRetentionMonths >= JurisdictionDefaults.getDefaults(jurisdiction).financialRetentionMonthsMinimum()` if a jurisdiction is set. Add `getEffectiveDsarDeadlineDays()` — returns `min(orgSettings.deadlineOverride, jurisdictionDefault)` or `jurisdictionDefault` if no override; returns 30 if no jurisdiction. Pattern: `updateVerticalProfile()` method in same file. |
| 373.6 | Extend `SettingsResponse` record with data protection fields | 373A | 373.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java`. Add to `SettingsResponse` record: `String dataProtectionJurisdiction`, `boolean retentionPolicyEnabled`, `Integer defaultRetentionMonths`, `Integer financialRetentionMonths`, `String informationOfficerName`, `String informationOfficerEmail`. Update `toSettingsResponse()` in `OrgSettingsService` to populate all 6 fields. Also add a `PATCH /api/settings/data-protection` endpoint delegating to `updateDataProtectionSettings()`. Pattern: existing `SettingsResponse` record in the same file; `PATCH /api/settings/vertical-profile` for the patch endpoint. |
| 373.7 | Add `DataProtectionSettingsRequest` record | 373A | 373.6 | New nested record inside `OrgSettingsController.java` or extracted to the `settings/` package: `DataProtectionSettingsRequest(String dataProtectionJurisdiction, Boolean retentionPolicyEnabled, Integer defaultRetentionMonths, Integer financialRetentionMonths, String informationOfficerName, String informationOfficerEmail)`. All fields nullable for partial updates. |
| 373.8 | Write unit tests for `JurisdictionDefaults` | 373A | 373.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/JurisdictionDefaultsTest.java`. 4 tests: (1) ZA returns 30-day deadline, (2) ZA returns 60-month financial minimum, (3) unknown jurisdiction returns safe defaults (30 days, 60 months), (4) null jurisdiction returns safe defaults. |
| 373.9 | Write integration tests for OrgSettings data protection extensions | 373A | 373.5, 373.6 | Modify or extend: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsControllerTest.java`. 4 tests: (1) `GET /api/settings` response includes all 6 new fields with defaults, (2) `PATCH /api/settings/data-protection` updates jurisdiction and info officer, (3) `PATCH` rejects `financialRetentionMonths` below ZA minimum (60) when jurisdiction = "ZA" — expect 400, (4) `LifecycleStatus.ANONYMIZED` constant exists and is a terminal state (unit). |

### Key Files

**Create:** `V76__data_protection_foundation.sql`, `JurisdictionDefaults.java`, `JurisdictionDefaultsTest.java`

**Modify:** `OrgSettings.java` (+6 fields), `OrgSettingsService.java` (+2 methods, `toSettingsResponse()` update), `OrgSettingsController.java` (extend `SettingsResponse`, add PATCH endpoint), `LifecycleStatus.java` (+1 enum value), `OrgSettingsControllerTest.java` (+4 tests)

### Architecture Decisions

- **Single V76 migration for all DDL**: All Phase 50 DDL changes (OrgSettings columns, new `processing_activities` table, `retention_policies` columns, `data_subject_requests` columns, Customer CHECK constraint) go into one migration. This avoids ordering issues between epics and keeps the migration history clean.
- **`JurisdictionDefaults` is a static utility, not a Spring bean**: Jurisdiction config is code, not data. Same pattern as `VerticalProfileRegistry` for static lookups. No database table for jurisdiction packs — they're functions that return seeded records. Adding GDPR later means adding a new `case "EU"` branch.
- **`ANONYMIZED` is a terminal lifecycle status**: No outgoing transitions from `ANONYMIZED`. `CustomerLifecycleGuard` must block all operations except read for anonymized customers. The existing `Customer.anonymize()` method transitions to `OFFBOARDED` for backward compatibility; the new `DataAnonymizationService` extension (Epic 375) will set `ANONYMIZED` directly, bypassing the old `anonymize()` domain method.
- **`financialRetentionMonths` defaults to 60 (5 years)**: The column default in DDL is 60. This matches the SA Income Tax Act / VAT Act minimum and is safe to apply immediately without jurisdiction configuration.

---

## Epic 374: Data Export Extension

**Goal**: Extend `DataExportService` to produce comprehensive personal information bundles including all time entries (not just billable), custom field values, audit events referencing the customer, and portal contacts, organized in a structured ZIP directory layout. Then expose this via a new `DataExportController` with endpoints for triggering, polling, and listing exports.

**References**: Architecture doc Sections 2.1, 2.2, 2.3. ADR-196 (pre-anonymization export key stored in audit event).

**Dependencies**: Epic 373 (V76 migration, OrgSettings for `financialRetentionMonths`).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **374A** | 374.1–374.6 | Extend `DataExportService.exportCustomerData()`: collect ALL time entries, custom field values, audit events (filtered by customer entity reference), portal contacts; produce structured ZIP with directory layout; upload to S3 with `compliance-exports/` prefix; create `DATA_SUBJECT_EXPORT` audit event; return `ExportResult` record. Integration tests (~5). Backend only. | **Done** (PR #769) |
| **374B** | 374.7–374.12 | New `DataExportController`: `POST /api/customers/{customerId}/data-export`, `GET /api/data-exports/{exportId}`, `GET /api/data-exports`; `ExportStatusResponse` record; authorization checks (OWNER/ADMIN only); integration tests (~5). Backend only. | **Done** (PR #770) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 374.1 | Add `ExportResult` and `ExportStatusResponse` records | 374A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/ExportResult.java`. Records: `ExportResult(UUID exportId, String status, int estimatedFiles)` and `ExportStatusResponse(UUID exportId, String status, String downloadUrl, Instant expiresAt, int fileCount, long totalSizeBytes)`. Pattern: existing `RetentionCheckResult.java` for simple result records. |
| 374.2 | Extend `DataExportService` — add ALL time entries to export scope | 374A | 374.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportService.java`. Change the existing time entry collection to use `timeEntryRepository.findByProjectIdIn(projectIds)` (all entries, not just billable). Inject `TimeEntryRepository`. Serialize to `time-entries.json` in ZIP root. |
| 374.3 | Extend `DataExportService` — add custom field values, audit events, portal contacts | 374A | 374.2 | Modify: `DataExportService.java`. Add: (1) custom field values via `FieldDefinitionRepository.findByEntityTypeAndEntityIdIn()` → `custom-fields.json`; (2) audit events via `AuditEventRepository.findByEntityTypeAndEntityIdIn(List.of("CUSTOMER"), List.of(customerId))` → `audit-events.json`; (3) portal contacts via `PortalContactRepository.findByCustomerId(customerId)` → `portal-contacts.json`. Inject the 3 new repositories. |
| 374.4 | Restructure ZIP output with directory layout | 374A | 374.2, 374.3 | Modify: `DataExportService.java`. Restructure ZIP entries into the specified directory layout: `customer-export-{id}/customer.json`, `portal-contacts.json`, `projects/project-{id}.json`, `documents/document-{id}.json` (metadata) + `documents/document-{id}-file.{ext}` (S3 content), `time-entries.json`, `invoices.json`, `comments.json`, `custom-fields.json`, `audit-events.json`, `export-metadata.json` (export date, tenant, jurisdiction, scope). Use `ZipOutputStream` with directory entries. Pattern: existing ZIP generation in `DataExportService`. |
| 374.5 | Upload export to S3 under `compliance-exports/` prefix | 374A | 374.4 | Modify: `DataExportService.java`. Upload final ZIP to S3 key: `org/{tenantId}/compliance-exports/exports/{customerId}/export-{timestamp}.zip`. Generate 24-hour presigned URL. Create `DATA_SUBJECT_EXPORT` audit event via `AuditEventBuilder`. Return `ExportStatusResponse` with the presigned URL. Use existing `StorageService` / S3 client from `s3/` package. |
| 374.6 | Write integration tests for extended `DataExportService` | 374A | 374.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportServiceTest.java`. 5 tests: (1) export includes all time entries (not just billable), (2) export includes custom field values, (3) export includes audit events for the customer, (4) export includes portal contacts, (5) export for customer with no documents/contacts gracefully produces empty arrays in JSON rather than skipping the files. Use `@SpringBootTest` + `TestcontainersConfiguration`. |
| 374.7 | Create `DataExportController` | 374B | 374.1–374.5 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportController.java`. Pure delegation controller. Endpoints: `POST /api/customers/{customerId}/data-export` (delegates to `DataExportService.exportCustomerData(customerId)`, returns 202 with `ExportResult`), `GET /api/data-exports/{exportId}` (delegates to `DataExportService.getExportStatus(exportId)`, returns `ExportStatusResponse`), `GET /api/data-exports` (delegates to `DataExportService.listExports()`, returns `Page<ExportStatusResponse>`). Pattern: `RetentionController.java` for pure-delegation structure. |
| 374.8 | Add `@RequiresCapability` authorization to export endpoints | 374B | 374.7 | Modify: `DataExportController.java`. Both `POST` and `GET /data-exports` endpoints require `@RequiresCapability(Capability.MANAGE_COMPLIANCE)` or `ADMIN` role. Anonymize endpoint (Epic 375) requires `OWNER` only. Verify that the `MANAGE_COMPLIANCE` capability is defined in the capability registry, or add it. Pattern: `@RequiresCapability` usage in `CustomerController.java`. |
| 374.9 | Add `DataExportService.listExports()` method | 374B | 374.5 | Modify: `DataExportService.java`. Since exports are stored in S3 (not a DB table), `listExports()` is backed by listing S3 keys under `org/{tenantId}/compliance-exports/exports/` prefix. Returns a list of export metadata objects. Note: if S3 listing is not already used anywhere, add a `listObjectsV2` call via the existing `StorageService` or create a `listExportsByTenant()` method in `StorageService`. Alternatively, if a `GeneratedDocument` record pattern is preferred, reconsider — keep it S3-only for now (simpler). |
| 374.10 | Add `DataExportService.getExportStatus(exportId)` method | 374B | 374.5 | Modify: `DataExportService.java`. Retrieve the export record by `exportId` (UUID encoded in the S3 key). Generate a fresh presigned URL if the original has expired. Returns `ExportStatusResponse`. |
| 374.11 | Write integration tests for `DataExportController` | 374B | 374.7–374.10 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportControllerTest.java`. 5 tests: (1) `POST /api/customers/{id}/data-export` returns 202 with `exportId` and `status=PROCESSING`, (2) `GET /api/data-exports/{id}` returns export status, (3) `GET /api/data-exports` returns paginated list, (4) `POST` by MEMBER role returns 403, (5) `POST /api/customers/{id}/data-export` for non-existent customer returns 404. |
| 374.12 | Write security test for export authorization | 374B | 374.11 | Extend `DataExportControllerTest.java`. 1 additional test: `POST /api/customers/{id}/anonymize` is not yet wired here — but verify that `PATCH /api/settings/data-protection` requires OWNER or ADMIN (from Epic 373). |

### Key Files

**Create:** `ExportResult.java`, `DataExportController.java`, `DataExportServiceTest.java`, `DataExportControllerTest.java`

**Modify:** `DataExportService.java` (extended with all-entity collection, structured ZIP, S3 `compliance-exports/` prefix, `listExports()`, `getExportStatus()`)

### Architecture Decisions

- **No database table for export records**: Exports are S3 objects. The export ID is a UUID embedded in the S3 key. `listExports()` uses S3 `listObjectsV2` rather than a separate DB table. This keeps the entity model minimal and avoids a V77 migration just for export tracking. The `GeneratedDocument` entity (Phase 12) is not reused here — that entity is for Tiptap/PDF documents, not ZIP archives.
- **All time entries, not just billable**: The existing export only collected billable time entries. Phase 50 exports all entries — the right of access covers all records, not just invoiced ones. Billable entries are already included in invoices; the additional non-billable entries add new coverage.
- **`compliance-exports/` prefix for S3**: Separates compliance-related exports from regular generated documents. The ADR-196 S3 lifecycle rule targets this prefix specifically.

---

## Epic 375: Anonymization Extension

**Goal**: Extend `DataAnonymizationService` to enforce `ANONYMIZED` terminal status, clear `notes` and `customFields` on the Customer, trigger a pre-anonymization export (stored in the audit event details per ADR-196), and provide an `AnonymizationPreview`. Then expose this via a new `AnonymizationController` with POST anonymize and GET preview endpoints.

**References**: Architecture doc Sections 3.1–3.4. ADR-193 (anonymization strategy). ADR-196 (pre-anonymization export S3 key in audit event).

**Dependencies**: Epic 373 (`ANONYMIZED` status in `LifecycleStatus`), Epic 374 (`DataExportService.exportCustomerData()` for pre-anonymization export).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **375A** | 375.1–375.7 | Extend `DataAnonymizationService`: `ANONYMIZED` status enforcement, `notes`/`customFields` clearing, pre-anonymization export (S3 key stored in audit event details), `previewAnonymization()` returning `AnonymizationPreview`, rejection logic for already-anonymized customers, `DATA_SUBJECT_ANONYMIZED` audit event. Integration tests (~6). Backend only. | **Done** (PR #771) |
| **375B** | 375.8–375.12 | New `AnonymizationController` (POST `/{customerId}/anonymize`, GET `/{customerId}/anonymize/preview`), `AnonymizationRequest` record, OWNER-only authorization, extend `DataRequestController` to delegate anonymization to the new service. Integration tests (~5). Backend only. | **Done** (PR #772) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 375.1 | Add `AnonymizationPreview` record | 375A | — | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/AnonymizationPreview.java`. Record: `AnonymizationPreview(UUID customerId, String customerName, int portalContacts, int projects, int documents, int timeEntries, int invoices, int comments, int customFieldValues, int financialRecordsRetained, LocalDate financialRetentionExpiresAt)`. |
| 375.2 | Extend `DataAnonymizationService.anonymizeCustomer()` — ANONYMIZED status + notes/customFields clearing | 375A | 373.4 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationService.java`. After existing anonymization: (1) set `customer.setLifecycleStatus(LifecycleStatus.ANONYMIZED)` directly (not via domain method), (2) set `customer.setNotes(null)`, (3) clear `customFields` JSONB column to `null` or `{}`. Reject if customer is already `ANONYMIZED` — throw `ResourceConflictException("Customer is already anonymized")`. |
| 375.3 | Extend `DataAnonymizationService.anonymizeCustomer()` — pre-anonymization export | 375A | 374.5 | Modify: `DataAnonymizationService.java`. Before executing anonymization: call `dataExportService.exportCustomerData(customerId)` to generate the pre-anonymization export ZIP. Store the resulting S3 key in the `DATA_SUBJECT_ANONYMIZED` audit event details: `{ "preAnonymizationExportKey": "org/{tenantId}/compliance-exports/exports/{customerId}/export-{timestamp}.zip" }`. The export key is stored in the audit event — NOT on the Customer entity (per ADR-196). |
| 375.4 | Implement `DataAnonymizationService.previewAnonymization()` | 375A | 375.1 | Modify: `DataAnonymizationService.java`. New `@Transactional(readOnly = true)` method `previewAnonymization(UUID customerId)`. Counts all related entities: portal contacts, projects, documents, time entries, invoices, comments, custom field values. Calculates `financialRecordsRetained` (count of invoices in financial retention period). Calculates `financialRetentionExpiresAt` from the oldest invoice date + `OrgSettings.financialRetentionMonths`. Returns `AnonymizationPreview`. |
| 375.5 | Add confirmation name validation to `anonymizeCustomer()` | 375A | 375.2 | Modify: `DataAnonymizationService.java`. Add `String confirmationName` parameter to `anonymizeCustomer()`. Before executing: verify `confirmationName.equalsIgnoreCase(customer.getName())`. Throw `InvalidStateException("Confirmation name does not match customer name")` if it fails. This enforces the two-step confirmation at the service layer (not just UI). |
| 375.6 | Write integration tests for extended `DataAnonymizationService` | 375A | 375.2–375.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationServiceTest.java`. 6 tests: (1) anonymized customer has `ANONYMIZED` status (not `OFFBOARDED`), (2) `notes` and `customFields` are null after anonymization, (3) pre-anonymization export audit event contains `preAnonymizationExportKey`, (4) `previewAnonymization()` returns correct entity counts, (5) anonymization rejected if customer name does not match confirmation name, (6) anonymization rejected if customer is already `ANONYMIZED`. |
| 375.7 | Add financial record preservation test | 375A | 375.2 | Extend `DataAnonymizationServiceTest.java`. 1 additional test: (7) invoices retain their financial fields (amount, line items, dates, number) after anonymization; only the customer name reference on the invoice is updated to `"REF-{shortId}"`. |
| 375.8 | Create `AnonymizationController` | 375B | 375.2–375.5 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/AnonymizationController.java`. Pure delegation. Endpoints: `POST /api/customers/{customerId}/anonymize` (requires `@RequiresCapability(Capability.MANAGE_COMPLIANCE_DESTRUCTIVE)` — OWNER only; delegates to `DataAnonymizationService.anonymizeCustomer(customerId, confirmationName, reason)`), `GET /api/customers/{customerId}/anonymize/preview` (requires ADMIN+; delegates to `DataAnonymizationService.previewAnonymization(customerId)`). Pattern: `DataExportController.java` from Epic 374. |
| 375.9 | Create `AnonymizationRequest` record | 375B | 375.8 | New nested record inside `AnonymizationController.java`: `AnonymizationRequest(String confirmationName, String reason)`. Validated with `@NotBlank` on `confirmationName`. |
| 375.10 | Extend `DataRequestController` to delegate to new anonymization service | 375B | 375.8 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataRequestController.java`. If the existing controller already has an anonymize endpoint, update it to delegate to `DataAnonymizationService` (the extended version). If not, leave the new `AnonymizationController` as the primary endpoint and only add a note in existing DSAR processing flow to reference the new standalone endpoint. This task is about ensuring there is no duplicate conflicting endpoint mapping. |
| 375.11 | Write integration tests for `AnonymizationController` | 375B | 375.8–375.9 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/AnonymizationControllerTest.java`. 5 tests: (1) `POST /api/customers/{id}/anonymize` with correct name returns 200, customer status is `ANONYMIZED`, (2) `POST` with wrong confirmation name returns 400, (3) `POST` by ADMIN role returns 403 (OWNER only), (4) `GET /api/customers/{id}/anonymize/preview` returns `AnonymizationPreview` with correct counts, (5) `POST /api/customers/{id}/anonymize` for already-ANONYMIZED customer returns 409. |
| 375.12 | Write OWNER-only authorization test | 375B | 375.11 | Extend `AnonymizationControllerTest.java`. 1 additional test: (6) `GET /api/customers/{id}/anonymize/preview` is accessible by ADMIN (not OWNER-only). Contrast with the POST which is OWNER-only. |

### Key Files

**Create:** `AnonymizationPreview.java`, `AnonymizationController.java`, `DataAnonymizationServiceTest.java`, `AnonymizationControllerTest.java`

**Modify:** `DataAnonymizationService.java` (ANONYMIZED status, notes/customFields, pre-anon export, preview, confirmation validation), `DataRequestController.java` (endpoint deduplication check)

### Architecture Decisions

- **`ANONYMIZED` set directly on entity, not via domain method**: The existing `Customer.anonymize()` transitions to `OFFBOARDED`. The extended service bypasses this method to set `ANONYMIZED` directly, since `ANONYMIZED` is a Phase 50 addition and the existing domain method should not be modified (backward compatibility risk). The service is the authority for lifecycle transitions in this context.
- **Pre-anonymization export key stored in audit event details (JSONB)**: Per ADR-196, the export key is stored in the `DATA_SUBJECT_ANONYMIZED` audit event's `details` JSONB — not on the Customer entity. The customer entity is anonymized; linking it to an export containing the original PII would violate the anonymization boundary.
- **Confirmation name validation at service layer**: The two-step confirmation (type customer name) is enforced at the service layer, not just UI. This prevents API clients from bypassing the safeguard. The `InvalidStateException` is caught by the global exception handler and returned as a 400 Bad Request.
- **`Capability.MANAGE_COMPLIANCE_DESTRUCTIVE` for anonymize**: The anonymize operation is more destructive than a standard compliance action. Using a distinct capability allows OWNER-only configuration without changing the general `MANAGE_COMPLIANCE` capability that ADMIN may share for less destructive operations.

---

## Epic 376: Retention Extension

**Goal**: Extend `RetentionService` and `RetentionPolicyService` with TIME_ENTRY support, 30-day notification-based warnings before purge execution, financial minimum enforcement during policy updates, and `lastEvaluatedAt` timestamp updates. Then extend `RetentionController` with the evaluate/execute split and expose updated policy endpoints.

**References**: Architecture doc Sections 4.1–4.3. ADR-194 (per-entity-type retention, financial minimum enforcement, TIME_ENTRY as new record type).

**Dependencies**: Epic 373 (V76 migration adds `last_evaluated_at` and `description` to `retention_policies`; `financialRetentionMonths` in OrgSettings; `JurisdictionDefaults`).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **376A** | 376.1–376.7 | Extend `RetentionPolicy` entity (+2 new fields), extend `RetentionService.runCheck()` with TIME_ENTRY support + 30-day warning notifications + `lastEvaluatedAt` updates + financial minimum check, add `RetentionService.previewPurge()`, add jurisdiction-seeding method. Integration tests (~5). Backend only. | **Done** (PR #773) |
| **376B** | 376.8–376.12 | Extend `RetentionController` with updated endpoints (GET list, PUT update with validation, POST evaluate preview, POST execute), `RetentionPolicyUpdateRequest` record, `RetentionEvaluationResult` record, authorization checks. Integration tests (~4). Backend only. | **Done** (PR #774) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 376.1 | Extend `RetentionPolicy` entity with `description` and `lastEvaluatedAt` | 376A | 373.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicy.java`. Add: `@Column(name = "description") private String description;` and `@Column(name = "last_evaluated_at") private Instant lastEvaluatedAt;`. Add getters/setters. V76 migration already added these columns. |
| 376.2 | Add TIME_ENTRY support to `RetentionService.runCheck()` | 376A | 376.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionService.java`. Add a `case "TIME_ENTRY"` branch in the existing `switch` / `if-else` that selects entities for evaluation. Uses `timeEntryRepository.findByDateBefore(cutoffDate)` where `cutoffDate = now() - policy.retentionDays`. For TIME_ENTRY, `action = "delete"` deletes the time entry; there is no anonymize action for time entries. Inject `TimeEntryRepository`. |
| 376.3 | Add 30-day warning notifications to `RetentionService` | 376A | 376.2 | Modify: `RetentionService.java`. Before executing a purge action, identify entities that will reach their retention deadline within 30 days but have not yet reached it. For these, send a notification to org owners via `NotificationService`: "X {entityType}(s) will be purged by retention policy on {date}." This is the "warning phase" — entities in the 0–30 day window get notified; entities past the deadline get purged on `execute()`. `runCheck()` now separates: `evaluateWarnings()` (finds approaching) and `executePurge()` (finds expired). Inject `NotificationService`. Pattern: how Phase 6.5 sends retention-related notifications in other services. |
| 376.4 | Add financial minimum enforcement to `RetentionService` | 376A | 376.1 | Modify: `RetentionService.java`. In the existing or new policy evaluation, check if the entity type is a financially-linked type (`"customer"`, `"TIME_ENTRY"`). If so, verify that `policy.retentionDays >= JurisdictionDefaults.getDefaults(orgSettings.dataProtectionJurisdiction).financialRetentionMonthsMinimum() * 30` before executing a purge. Throw `InvalidStateException` if a policy is configured below the minimum. |
| 376.5 | Update `lastEvaluatedAt` after each policy evaluation | 376A | 376.2 | Modify: `RetentionService.java`. After evaluating (or executing) a `RetentionPolicy`, call `policy.setLastEvaluatedAt(Instant.now())` and save via `RetentionPolicyRepository`. |
| 376.6 | Add `RetentionService.seedJurisdictionDefaults(String jurisdiction)` | 376A | 376.1 | Modify: `RetentionService.java` or `RetentionPolicyService.java`. New method: creates default `RetentionPolicy` entries for the jurisdiction if they don't already exist (`ON CONFLICT DO NOTHING` equivalent via `findByRecordType()` check). ZA defaults: customer (60 months, anonymize), time_entry (60 months, delete), document (60 months, delete), comment (36 months, delete), audit_event (84 months, delete). Called by `OrgSettingsService.updateDataProtectionSettings()` when jurisdiction is first set. |
| 376.7 | Write integration tests for extended `RetentionService` | 376A | 376.2–376.6 | New or extend: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/retention/RetentionServiceTest.java`. 5 tests: (1) TIME_ENTRY entities past retention period are found for purge, (2) 30-day warning notification is sent for entities approaching retention deadline, (3) financial minimum check throws on policy below minimum, (4) `lastEvaluatedAt` is updated after evaluation, (5) `seedJurisdictionDefaults("ZA")` creates 5 default policies. |
| 376.8 | Add `RetentionEvaluationResult` record | 376B | 376.3 | New nested record inside `RetentionController.java` or extracted: `RetentionEvaluationResult(int totalPoliciesEvaluated, int entitiesApproachingDeadline, int entitiesEligibleForPurge, List<PolicySummary> policySummaries)`. |
| 376.9 | Add `RetentionPolicyUpdateRequest` record | 376B | — | New nested record: `RetentionPolicyUpdateRequest(Integer retentionDays, String action, Boolean enabled, String description)`. All nullable for partial update. |
| 376.10 | Extend `RetentionController` with new endpoints | 376B | 376.8, 376.9 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionController.java`. Verify/add endpoints: `GET /api/settings/retention-policies` (list all), `PUT /api/settings/retention-policies/{id}` (update with financial minimum validation), `POST /api/settings/retention-policies/evaluate` (returns `RetentionEvaluationResult` — preview only, no side effects), `POST /api/settings/retention-policies/execute` (executes purge for eligible entities). Add `@RequiresCapability(Capability.MANAGE_COMPLIANCE)` to all. |
| 376.11 | Add financial minimum validation to PUT endpoint | 376B | 376.10 | Modify: `RetentionController.java` / `RetentionPolicyService.java`. The PUT endpoint (via service) must validate that `retentionDays` is not below the jurisdiction minimum for financially-linked types. Throw `InvalidStateException` with a clear message: "Retention period for financial records cannot be less than {N} months (jurisdiction: ZA)." |
| 376.12 | Write integration tests for `RetentionController` | 376B | 376.10–376.11 | Extend or new: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/retention/RetentionControllerTest.java`. 4 tests: (1) `GET /api/settings/retention-policies` returns list of policies with `lastEvaluatedAt`, (2) `PUT` updates retention period and description, (3) `PUT` rejects retention period below financial minimum (400), (4) `POST /api/settings/retention-policies/evaluate` returns preview without executing. |

### Key Files

**Create:** `RetentionEvaluationResult.java` (nested record), `RetentionPolicyUpdateRequest.java` (nested record)

**Modify:** `RetentionPolicy.java` (+2 fields), `RetentionService.java` (+TIME_ENTRY, +warnings, +financial minimum, +lastEvaluatedAt, +seedJurisdictionDefaults), `RetentionController.java` (+PUT, +POST evaluate, +POST execute), `RetentionServiceTest.java` (+5 tests), `RetentionControllerTest.java` (+4 tests)

### Architecture Decisions

- **Two-phase evaluation (warn then execute)**: `runCheck()` is split into `evaluateWarnings()` (identifies entities 0–30 days from deadline, sends notifications) and `executePurge()` (identifies entities past deadline, deletes/anonymizes). `POST /api/settings/retention-policies/evaluate` calls the warning phase + returns a preview without executing. `POST /api/settings/retention-policies/execute` calls the purge phase. This matches the architecture doc's "two-phase approach" and gives operators a chance to review before destruction.
- **Financial minimum check in service layer, not controller**: `RetentionService` knows the financial minimum via `JurisdictionDefaults`. The controller delegates validation entirely to the service (thin controller rule from backend CLAUDE.md).
- **`seedJurisdictionDefaults()` is idempotent**: It uses `findByRecordType()` to check if a policy already exists before creating a default. Re-running for the same jurisdiction does not create duplicates. This allows the jurisdiction-seeding flow in `OrgSettingsService` to call it safely on every `PATCH /api/settings/data-protection`.

---

## Epic 377: DSAR Extension

**Goal**: Extend `DataSubjectRequestService` with jurisdiction-aware deadline calculation (per ADR-195: tenant override capped at jurisdiction statutory maximum), add `jurisdiction` and `deadlineDaysOverride` fields from V76 to the entity and service, and send deadline warning notifications (7-day and 2-day warnings) via the existing notification system.

**References**: Architecture doc Section 5. ADR-195 (jurisdiction-default with tenant override capped at max).

**Dependencies**: Epic 373 (V76 adds `jurisdiction` and `deadline_days_override` to `data_subject_requests`; `JurisdictionDefaults` for statutory values).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **377A** | 377.1–377.7 | Add `jurisdiction` + `deadlineDaysOverride` to `DataSubjectRequest` entity, extend `DataSubjectRequestService.create()` with jurisdiction-aware deadline calculation (cap logic from ADR-195), add `sendDeadlineWarnings()` method (7-day and 2-day notifications), update DSAR create request record, integration tests (~5). Backend only. | **Done** (PR #775) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 377.1 | Add `jurisdiction` and `deadlineDaysOverride` to `DataSubjectRequest` entity | 377A | 373.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequest.java`. Add: `@Column(name = "jurisdiction", length = 10) private String jurisdiction;` and `@Column(name = "deadline_days_override") private Integer deadlineDaysOverride;`. V76 migration already added these columns. Add getters/setters. |
| 377.2 | Implement jurisdiction-aware deadline calculation | 377A | 377.1, 373.3 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestService.java`. Update the deadline calculation in `createRequest()`: `int jurisdictionMax = JurisdictionDefaults.getDefaults(orgSettings.getDataProtectionJurisdiction()).dsarDeadlineDays()`. If tenant has a `dataRequestDeadlineDays` override on OrgSettings: `effectiveDays = Math.min(override, jurisdictionMax)`. Otherwise: `effectiveDays = jurisdictionMax`. Set `request.setDeadlineAt(receivedAt.plusDays(effectiveDays))`. Store the resolved `jurisdiction` on the request at creation time (snapshot — doesn't change if org changes jurisdiction later). |
| 377.3 | Update DSAR creation to snapshot the jurisdiction | 377A | 377.2 | Modify: `DataSubjectRequestService.java`. When creating a new `DataSubjectRequest`, set `request.setJurisdiction(orgSettings.getDataProtectionJurisdiction())`. This snapshots the jurisdiction at request-creation time — ensures the deadline calculation doesn't change retroactively if the org later changes jurisdiction. |
| 377.4 | Implement `DataSubjectRequestService.sendDeadlineWarnings()` | 377A | 377.2 | Modify: `DataSubjectRequestService.java`. New method: finds all open requests (`status` not in `COMPLETED`, `DENIED`) where `deadline_at` is within 7 days or within 2 days of `now()` AND a warning has not been sent for that threshold. Sends notification via `NotificationService` to org owners: "DSAR for {subject_name} is due in {N} days." Since there's no `warningsSent` flag on the entity, implement a simple heuristic: send the 7-day warning when `deadline_at - now() <= 7 days AND > 2 days`; send the 2-day warning when `deadline_at - now() <= 2 days`. This method is called manually from the evaluate endpoint (no scheduled cron in v1). |
| 377.5 | Add `DataSubjectRequestSummary` projection | 377A | 377.1 | New file or nested record: `DataSubjectRequestSummary` extends or wraps the existing DSAR response with `jurisdiction`, `effectiveDeadlineDays`, `deadlineStatus` (enum: `ON_TRACK`, `DUE_SOON` within 7 days, `OVERDUE`). Used in the list API response for deadline badge coloring in the frontend. |
| 377.6 | Write integration tests for extended `DataSubjectRequestService` | 377A | 377.2–377.4 | Extend or new: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestServiceTest.java`. 5 tests: (1) deadline calculation uses jurisdiction default (30 days for ZA) when no tenant override, (2) tenant override of 14 days is used (< 30), (3) tenant override of 45 days is capped to 30 (ZA max), (4) jurisdiction is snapshotted on the request at creation, (5) `sendDeadlineWarnings()` sends notification for request due within 7 days. |
| 377.7 | Write integration test for DSAR endpoint changes | 377A | 377.6 | Extend or new: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/DataRequestControllerTest.java`. 1 test: `GET /api/dsar` response includes `deadlineStatus` field in each result. Verify `OVERDUE` for past-deadline requests. |

### Key Files

**Create:** (no new files — all modifications)

**Modify:** `DataSubjectRequest.java` (+2 fields), `DataSubjectRequestService.java` (+jurisdiction deadline logic, +warning notifications, +snapshot), `DataSubjectRequestServiceTest.java` (+5 tests), `DataRequestControllerTest.java` (+1 test)

### Architecture Decisions

- **Jurisdiction snapshotted at request creation**: The `jurisdiction` column on `DataSubjectRequest` stores the value at creation time. If an org changes their jurisdiction after a DSAR is created, the existing DSAR deadline does not change. This avoids retroactive deadline changes that could create compliance confusion.
- **`Math.min(override, jurisdictionMax)` in service layer**: Per ADR-195, the cap is enforced silently. The UI displays the effective deadline clearly ("14 days — POPIA allows up to 30 days"). A future enhancement can surface the capping in the API response.
- **No `warningsSent` flag on the entity (v1)**: Sending duplicate notifications within a short period is tolerable for the v1 scope. A `warningsSent` bitmask column can be added in a future patch if needed.

---

## Epic 378: Processing Activity CRUD + PAIA Manual Generation

**Goal**: Create the `ProcessingActivity` entity and full CRUD, with jurisdiction-specific default seeding for ZA. Then create the `PaiaManualContextBuilder` that assembles the PAIA Section 51 manual context from `OrgSettings`, `RetentionPolicy`, and `ProcessingActivity` records, seed the compliance template pack, and expose the generate endpoint.

**References**: Architecture doc Sections 6 (PAIA), 7 (Processing Register). ADR-193 through ADR-196 (background context).

**Dependencies**: Epic 373 (V76 creates `processing_activities` table, OrgSettings extension for information officer fields). Epic 376A (`RetentionPolicy.description` field used in PAIA context).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **378A** | 378.1–378.7 | `ProcessingActivity` entity, `ProcessingActivityRepository`, `ProcessingActivityService` (CRUD + jurisdiction seeding), `ProcessingActivityController` (4 endpoints), integration tests (~5). Backend only. | **Done** (PR #776) |
| **378B** | 378.8–378.12 | `PaiaManualContextBuilder` implementing `TemplateContextBuilder`, compliance template pack seeder (one template: PAIA Section 51 manual, jurisdiction ZA), `POST /api/settings/paia-manual/generate` endpoint, integration tests (~4). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 378.1 | Create `ProcessingActivity` entity | 378A | 373.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/ProcessingActivity.java`. `@Entity @Table(name = "processing_activities")`. Fields: `id` (UUID), `category` (VARCHAR 100, not null), `description` (TEXT), `legalBasis` (VARCHAR 50), `dataSubjects` (VARCHAR 255), `retentionPeriod` (VARCHAR 100), `recipients` (VARCHAR 255), `createdAt` (Instant), `updatedAt` (Instant). No multitenancy boilerplate needed (schema isolation handles it). Pattern: any existing entity in `datarequest/` package. Use `@PrePersist` / `@PreUpdate` for audit timestamps. |
| 378.2 | Create `ProcessingActivityRepository` | 378A | 378.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/ProcessingActivityRepository.java`. Extends `JpaRepository<ProcessingActivity, UUID>`. Add `Page<ProcessingActivity> findAll(Pageable pageable)` (JpaRepository already provides this). Pattern: `DataSubjectRequestRepository.java`. |
| 378.3 | Create `ProcessingActivityService` with CRUD and jurisdiction seeding | 378A | 378.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/ProcessingActivityService.java`. Methods: `Page<ProcessingActivity> list(Pageable)`, `ProcessingActivity create(ProcessingActivityRequest)`, `ProcessingActivity update(UUID id, ProcessingActivityRequest)`, `void delete(UUID id)`, `void seedJurisdictionDefaults(String jurisdiction)`. The `seedJurisdictionDefaults("ZA")` method creates the 6 default entries from architecture doc Section 7.2 if they don't already exist (check by `category`). Called by `OrgSettingsService.updateDataProtectionSettings()` when jurisdiction is first set. |
| 378.4 | Create `ProcessingActivityRequest` record | 378A | 378.3 | New nested record in `ProcessingActivityController.java` or inline in service: `ProcessingActivityRequest(String category, String description, String legalBasis, String dataSubjects, String retentionPeriod, String recipients)`. `category` is `@NotBlank`. |
| 378.5 | Create `ProcessingActivityController` | 378A | 378.3, 378.4 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/ProcessingActivityController.java`. Pure delegation. Endpoints: `GET /api/settings/processing-activities` (paginated list), `POST /api/settings/processing-activities`, `PUT /api/settings/processing-activities/{id}`, `DELETE /api/settings/processing-activities/{id}`. All require `@RequiresCapability(Capability.MANAGE_COMPLIANCE)`. Pattern: `DocumentTemplateController.java` in `template/` package. |
| 378.6 | Wire `seedJurisdictionDefaults` call into OrgSettings jurisdiction update | 378A | 378.3 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`. In `updateDataProtectionSettings()`, after saving the OrgSettings, call `processingActivityService.seedJurisdictionDefaults(newJurisdiction)` and `retentionService.seedJurisdictionDefaults(newJurisdiction)` (both are idempotent). Also seed the compliance template pack via `templatePackSeeder.seedCompliancePack(newJurisdiction)` (Epic 378B). Inject the three services. |
| 378.7 | Write integration tests for `ProcessingActivityController` | 378A | 378.5–378.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/ProcessingActivityControllerTest.java`. 5 tests: (1) `GET` returns paginated list, (2) `POST` creates new activity, (3) `PUT` updates existing, (4) `DELETE` removes activity, (5) `PATCH /api/settings/data-protection` with `jurisdiction = "ZA"` seeds 6 default processing activities (integration of seed call). |
| 378.8 | Create `PaiaManualContextBuilder` | 378B | 378.3, 373.5 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/PaiaManualContextBuilder.java`. Implements `TemplateContextBuilder` from `template/` package. `@Component`. `buildContext(String tenantSchema)` assembles context map from: `OrgSettings` (org name, information officer name/email, address, contact, branding), `RetentionPolicy` list (for data retention section), `ProcessingActivity` list (for processing register section), static boilerplate text (PAIA Section 51 required clauses). Returns `Map<String, Object>` with keys matching the PAIA template variables. Pattern: `CustomerContextBuilder.java` and `ProjectContextBuilder.java` in `template/` package. |
| 378.9 | Create compliance template pack seeder | 378B | 378.8 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/CompliancePackSeeder.java`. Extends `AbstractPackSeeder` (from `seeder/`). Seeds one template: PAIA Section 51 manual (Tiptap JSON content with template variables, jurisdiction = "ZA"). Tiptap content includes the six required PAIA Section 51 sections with `{{orgName}}`, `{{informationOfficerName}}`, `{{informationOfficerEmail}}`, `{{retentionPolicies}}`, etc. placeholders. Method `seedCompliancePack(String jurisdiction)` is called from `OrgSettingsService`. Pattern: `TemplatePackSeeder.java` and its usage of `TemplatePackDefinition` / `TemplatePackTemplate`. |
| 378.10 | Add `POST /api/settings/paia-manual/generate` endpoint | 378B | 378.8, 378.9 | Add to a new `DataProtectionController.java` or to `OrgSettingsController.java` as an additional route. Delegates to `PaiaManualGenerationService` (a thin wrapper or directly to `GeneratedDocumentService.generate(templateSlug, PaiaManualContextBuilder)`). Returns a `GeneratedDocument` record with PDF download URL. Pattern: the generate document endpoint in `DocumentTemplateController.java`. Note: use a new `DataProtectionController.java` rather than adding to `OrgSettingsController` to keep concerns separated. |
| 378.11 | Write unit tests for `PaiaManualContextBuilder` | 378B | 378.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/datarequest/PaiaManualContextBuilderTest.java`. 4 tests: (1) context includes `orgName` from `OrgSettings`, (2) context includes `informationOfficerName` and `informationOfficerEmail`, (3) context includes `retentionPolicies` list from `RetentionPolicyRepository`, (4) context includes `processingActivities` list from `ProcessingActivityRepository`. Use mocked repositories. |
| 378.12 | Write integration test for PAIA generate endpoint | 378B | 378.10 | Extend `DataProtectionControllerTest.java` or create new. 1 test: `POST /api/settings/paia-manual/generate` creates a `GeneratedDocument` with `templateSlug = "paia-section-51-manual-za"` and returns a 200 with the document ID and a download URL. |

### Key Files

**Create:** `ProcessingActivity.java`, `ProcessingActivityRepository.java`, `ProcessingActivityService.java`, `ProcessingActivityController.java`, `PaiaManualContextBuilder.java`, `CompliancePackSeeder.java`, `DataProtectionController.java` (PAIA generate endpoint), `ProcessingActivityControllerTest.java`, `PaiaManualContextBuilderTest.java`

**Modify:** `OrgSettingsService.java` (wire `seedJurisdictionDefaults` calls on jurisdiction update)

### Architecture Decisions

- **`PaiaManualContextBuilder` in `datarequest/` package, not `template/`**: The builder is specific to PAIA manual generation and is not a generic template context. It lives alongside the other data protection classes (`DataExportService`, `DataAnonymizationService`). The `template/` package contains the infrastructure; `datarequest/` contains the domain-specific context assembly.
- **`CompliancePackSeeder` extends `AbstractPackSeeder`**: Follows the same pattern as `TemplatePackSeeder` for consistency. Seeding is idempotent — if the PAIA template already exists (by slug), it is not re-seeded.
- **Jurisdiction seeding is additive**: Setting `jurisdiction = "ZA"` creates default processing activities and retention policies but does not delete any existing ones. Setting it again is idempotent. This matches the architecture doc's "additive" requirement.
- **PAIA generate endpoint in `DataProtectionController`**: Separating data protection settings operations from the main `OrgSettingsController` avoids further polluting a controller that is already noted as a "known violation" (predate thin-controller rule). The new controller is thin by design.

---

## Epic 379: Frontend — Data Protection Settings Tab + DSAR Page

**Goal**: Create the **Data Protection** settings tab with all sub-sections: jurisdiction selector, information officer fields, DSAR request badge link, retention policies table, processing register table, and PAIA manual generation button. Also build the DSAR management page as a standalone route under `/settings/data-protection/`.

**References**: Architecture doc Sections 4.4, 5.4, 7.4, 8.1, 8.2.

**Dependencies**: All backend epics 373–378 (API endpoints must exist for the frontend to call).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **379A** | 379.1–379.6 | New route `/settings/data-protection/page.tsx`, jurisdiction selector component, information officer fields, `PATCH /api/settings/data-protection` action, types for data protection settings, layout in settings nav. Frontend tests (~4). Frontend only. | |
| **379B** | 379.7–379.12 | DSAR management page (`/settings/data-protection/requests/`), DSAR list table with deadline badges, `LogDsarRequestDialog`, status transition actions, `fetchDsarRequests` + `createDsarRequest` + `updateDsarStatus` server actions. Frontend tests (~5). Frontend only. | |
| **379C** | 379.13–379.18 | Retention policies table (inline editing, financial minimum validation), processing register table (CRUD), PAIA manual generate button + preview dialog, export register button. Frontend tests (~5). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 379.1 | Add Data Protection tab to settings navigation | 379A | — | Modify: `frontend/app/(app)/org/[slug]/settings/layout.tsx`. Add a "Data Protection" tab link pointing to `/settings/data-protection`. Add `ShieldCheck` icon from `lucide-react`. Pattern: how existing settings tabs (e.g., `compliance`, `tax`) are added in the same layout. |
| 379.2 | Add data protection types to `lib/types.ts` or a new `lib/types/data-protection.ts` | 379A | — | New or modify: `frontend/lib/types/data-protection.ts`. Types: `DataProtectionSettings { dataProtectionJurisdiction: string | null; retentionPolicyEnabled: boolean; defaultRetentionMonths: number | null; financialRetentionMonths: number; informationOfficerName: string | null; informationOfficerEmail: string | null; }`. Also: `DsarRequest`, `RetentionPolicy`, `ProcessingActivity` types matching backend response shapes. |
| 379.3 | Create `updateDataProtectionSettings` server action | 379A | 379.2 | New file: `frontend/app/(app)/org/[slug]/settings/data-protection/actions.ts`. Server action `updateDataProtectionSettings(slug, data)` — calls `PATCH /api/settings/data-protection` via `lib/api.ts`. Also: `fetchDataProtectionSettings(slug)` calling `GET /api/settings` (returns full settings including the 6 new fields). Pattern: `frontend/app/(app)/org/[slug]/settings/general/actions.ts`. |
| 379.4 | Create Data Protection settings page | 379A | 379.3 | New file: `frontend/app/(app)/org/[slug]/settings/data-protection/page.tsx`. RSC. Fetches settings via `fetchDataProtectionSettings()`. Renders: (1) `JurisdictionSelectorSection` (Select dropdown: Not configured, South Africa (POPIA), European Union (GDPR — coming soon), Brazil (LGPD — coming soon); disabled/grayed for future jurisdictions), (2) `InformationOfficerSection` (name + email fields), (3) link to DSAR requests page with open count badge, (4) `RetentionPoliciesSection` stub (expanded in 379C), (5) `ProcessingRegisterSection` stub (expanded in 379C), (6) `PaiaManualSection` stub (expanded in 379C). Pattern: `frontend/app/(app)/org/[slug]/settings/compliance/page.tsx`. |
| 379.5 | Create `JurisdictionSelectorSection` client component | 379A | 379.4 | New file: `frontend/components/settings/jurisdiction-selector.tsx`. `"use client"`. Select dropdown with jurisdiction options. On change: calls `updateDataProtectionSettings()` with new jurisdiction. On first selection (from null): show a confirmation dialog "Setting your jurisdiction will create default retention policies and processing register entries. Continue?" before submitting. Pattern: `frontend/components/settings/` existing components, `AlertDialog` for confirmation. |
| 379.6 | Write frontend tests for Data Protection settings tab | 379A | 379.4, 379.5 | New file: `frontend/__tests__/settings/data-protection.test.tsx`. 4 tests: (1) Data Protection settings page renders jurisdiction selector, information officer fields, (2) selecting a jurisdiction shows confirmation dialog on first selection, (3) information officer fields update on change with `PATCH` call, (4) `updateDataProtectionSettings` action calls correct API endpoint. Use `vi.mock` for API calls. Pattern: existing settings tests. |
| 379.7 | Create DSAR requests page route | 379B | 379.2 | New file: `frontend/app/(app)/org/[slug]/settings/data-protection/requests/page.tsx`. RSC. Fetches DSAR list via `fetchDsarRequests(slug)`. Renders `DsarRequestsTable` + "Log new request" button. |
| 379.8 | Create DSAR server actions | 379B | 379.2 | New file: `frontend/app/(app)/org/[slug]/settings/data-protection/requests/actions.ts`. Server actions: `fetchDsarRequests(slug)`, `createDsarRequest(slug, data)`, `updateDsarStatus(slug, id, status, resolutionNotes?)`. API calls via `lib/api.ts`. |
| 379.9 | Create `DsarRequestsTable` component | 379B | 379.8 | New file: `frontend/components/data-protection/dsar-requests-table.tsx`. `"use client"`. Columns: Subject Name, Request Type, Status, Received, Deadline, actions. Status badges: `success` variant for COMPLETED, `destructive` for OVERDUE, `warning` for DUE_SOON (within 7 days), `neutral` for others. Uses existing `Badge` component variants. Deadline column shows days remaining or "Overdue" in red. |
| 379.10 | Create `LogDsarRequestDialog` component | 379B | 379.8 | New file: `frontend/components/data-protection/log-dsar-dialog.tsx`. `"use client"`. Dialog with form: subject name, email, request type (Select: Access, Correction, Deletion, Objection), linked customer (optional combobox), date received (DatePicker defaults to today). Zod schema in `lib/schemas/data-protection.ts`. On submit: calls `createDsarRequest()`. Pattern: `components/customers/create-customer-dialog.tsx`. |
| 379.11 | Create DSAR status transition inline action | 379B | 379.9 | Add to `DsarRequestsTable` or a `DsarDetailSheet` component. Status transition buttons: "Verify", "Mark Processing", "Complete", "Deny" per lifecycle state. "Complete" and "Deny" open a small dialog for resolution notes. Calls `updateDsarStatus()` server action. Pattern: how task status transitions are handled in task detail components. |
| 379.12 | Write frontend tests for DSAR management | 379B | 379.9–379.11 | New file: `frontend/__tests__/data-protection/dsar-requests.test.tsx`. 5 tests: (1) DSAR table renders with deadline badges, (2) OVERDUE requests show red `destructive` badge, (3) DUE_SOON requests show amber `warning` badge, (4) Log new request dialog submits form correctly, (5) status transition calls correct server action. |
| 379.13 | Create retention policies table component | 379C | 379.2 | New file: `frontend/components/data-protection/retention-policies-table.tsx`. `"use client"`. Table with inline editing: columns Entity Type, Retention Period (editable number input + "months" label), Action (Select: anonymize/delete), Enabled (Toggle), Last Evaluated. Validation: retention period for financial types cannot be below OrgSettings `financialRetentionMonths`. On save: calls `updateRetentionPolicy()` server action. |
| 379.14 | Create retention policy and run-check actions | 379C | 379.2 | New file or add to existing: `frontend/app/(app)/org/[slug]/settings/data-protection/actions.ts`. Add: `fetchRetentionPolicies(slug)`, `updateRetentionPolicy(slug, id, data)`, `evaluateRetentionPolicies(slug)` (POST to `/evaluate`), `executeRetentionPurge(slug)` (POST to `/execute`). |
| 379.15 | Create processing register table component | 379C | 379.2 | New file: `frontend/components/data-protection/processing-register-table.tsx`. `"use client"`. Table with CRUD: columns Category, Description, Legal Basis, Data Subjects, Retention Period. Add/edit via dialog (`ProcessingActivityDialog`). Delete with confirm. "Export register" button calls a future endpoint (placeholder in v1 — button exists but shows "coming soon" toast). Pattern: `components/field-definitions/` for similar CRUD tables. |
| 379.16 | Create PAIA manual generate section | 379C | 379.2 | New file: `frontend/components/data-protection/paia-manual-section.tsx`. `"use client"`. Shows: last generated date (from `GeneratedDocument` list), "Generate PAIA Manual" button. On click: calls `POST /api/settings/paia-manual/generate`, shows loading state, on success opens `DocumentPreviewDialog` (reuse from Phase 12 template components) with HTML preview and "Download PDF" button. Also shows a "Jurisdiction required" warning if jurisdiction is not set. Pattern: `components/templates/generate-document-dialog.tsx` from Phase 12. |
| 379.17 | Wire retention and processing register into Data Protection page | 379C | 379.13, 379.15, 379.16 | Modify: `frontend/app/(app)/org/[slug]/settings/data-protection/page.tsx`. Replace stub sections with real components: `RetentionPoliciesSection` (renders `RetentionPoliciesTable`), `ProcessingRegisterSection` (renders `ProcessingRegisterTable` + "Run retention check" button), `PaiaManualSection` (renders `PaiaManualSection` component). Fetch retention policies and processing activities in the RSC on page load. |
| 379.18 | Write frontend tests for retention and processing sections | 379C | 379.13–379.17 | New file: `frontend/__tests__/data-protection/settings-sections.test.tsx`. 5 tests: (1) retention policies table renders with correct columns, (2) inline edit triggers `updateRetentionPolicy` action, (3) financial minimum validation rejects retention period below minimum, (4) processing register table renders seeded entries, (5) PAIA manual section shows "Generate" button and "Jurisdiction required" warning when no jurisdiction set. |

### Key Files

**Create:** `frontend/app/(app)/org/[slug]/settings/data-protection/page.tsx`, `frontend/app/(app)/org/[slug]/settings/data-protection/actions.ts`, `frontend/app/(app)/org/[slug]/settings/data-protection/requests/page.tsx`, `frontend/app/(app)/org/[slug]/settings/data-protection/requests/actions.ts`, `frontend/components/data-protection/jurisdiction-selector.tsx`, `frontend/components/data-protection/dsar-requests-table.tsx`, `frontend/components/data-protection/log-dsar-dialog.tsx`, `frontend/components/data-protection/retention-policies-table.tsx`, `frontend/components/data-protection/processing-register-table.tsx`, `frontend/components/data-protection/paia-manual-section.tsx`, `frontend/lib/types/data-protection.ts`, `frontend/lib/schemas/data-protection.ts`

**Modify:** `frontend/app/(app)/org/[slug]/settings/layout.tsx` (add Data Protection tab)

### Architecture Decisions

- **Data Protection as a settings sub-tab, not a top-level page**: Follows the existing pattern (compliance, tax, notifications all live under `/settings/`). The DSAR management page is a sub-route under `/settings/data-protection/requests/` for deeper navigation.
- **Jurisdiction selector shows future jurisdictions as disabled**: EU and BR options are shown but disabled with a "coming soon" label. This communicates the jurisdiction-agnostic architecture without requiring implementation of GDPR/LGPD packs.
- **Retention policies table uses inline editing**: Not a dialog per row. This matches the Linear-inspired "Signal Deck" design language — edit in place, save on blur or explicit save button. Pattern from rate cards table.
- **Processing register export is a v1 placeholder**: The "Export register" button exists in the UI but shows a "coming soon" toast. The backend export endpoint can be added in a follow-on phase without UI changes.
- **`"use client"` only on interactive sub-components**: The page itself (`page.tsx`) is an RSC that fetches data server-side. Only `JurisdictionSelectorSection`, `RetentionPoliciesTable`, `DsarRequestsTable`, and `PaiaManualSection` are client components (they need state/events).

---

## Epic 380: Frontend — Customer Data Protection Actions

**Goal**: Add export and anonymization actions to the Customer Detail page. The export action triggers the data export API and shows a progress indicator with download link. The anonymization action shows a preview dialog with entity counts, a financial retention warning if applicable, and a type-to-confirm step. Anonymized customers display an `ANONYMIZED` badge with disabled edit fields.

**References**: Architecture doc Sections 2.4, 3.5.

**Dependencies**: Epic 374 (export API endpoints), Epic 375 (anonymize/preview API endpoints), Epic 379A (Data Protection tab foundation — types and actions pattern established).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **380A** | 380.1–380.8 | Export confirmation dialog + progress + download link, anonymize preview dialog + financial warning + type-to-confirm, anonymized state display (badge, disabled fields), customer data protection server actions, frontend tests (~5). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 380.1 | Create customer data protection server actions | 380A | 379.2 | New file: `frontend/app/(app)/org/[slug]/customers/[id]/data-protection-actions.ts`. Server actions: `triggerDataExport(slug, customerId)` → POST `/api/customers/{id}/data-export`, `getExportStatus(slug, exportId)` → GET `/api/data-exports/{id}`, `getAnonymizationPreview(slug, customerId)` → GET `/api/customers/{id}/anonymize/preview`, `anonymizeCustomer(slug, customerId, confirmationName, reason)` → POST `/api/customers/{id}/anonymize`. Pattern: existing `actions.ts` and `lifecycle-actions.ts` in the same route. |
| 380.2 | Create `DataExportDialog` component | 380A | 380.1 | New file: `frontend/components/customers/data-export-dialog.tsx`. `"use client"`. Dialog with: step 1 = confirmation ("Export all personal data for {customerName}? The export may take a few minutes."), step 2 = processing indicator (spinner with "Preparing export…"), step 3 = download ready (download button with expiry countdown "Download available for 24 hours"). Uses SWR to poll `getExportStatus()` while `status === "PROCESSING"`. `refreshInterval: 3000`. Pattern: `components/billing-runs/` for multi-step export dialogs, SWR polling pattern from `frontend/CLAUDE.md`. |
| 380.3 | Create `AnonymizeCustomerDialog` component | 380A | 380.1 | New file: `frontend/components/customers/anonymize-customer-dialog.tsx`. `"use client"`. Multi-step dialog: step 1 = anonymization preview (entity counts from `getAnonymizationPreview()`), step 2 = financial retention warning (shown only if `financialRecordsRetained > 0`: "This customer has {N} invoices within the {X}-year financial retention period. These records will be preserved in anonymized form."), step 3 = type-to-confirm (`<Input placeholder="Type customer name to confirm" />`; submit button disabled until value matches). On confirm: calls `anonymizeCustomer()`, shows loading → success → reloads page. Zod: confirm name validation inline. Pattern: Phase 49 profile switching confirmation dialog (`frontend/components/settings/profile-switch-dialog.tsx`). |
| 380.4 | Add data protection actions to Customer Detail page | 380A | 380.2, 380.3 | Modify: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`. In the existing actions dropdown (or a dedicated "Data Protection" section), add two items: "Download all data" (opens `DataExportDialog`) and "Delete personal data" (opens `AnonymizeCustomerDialog`). Both items should be conditionally hidden for `ANONYMIZED` customers (anonymization is irreversible — no repeat action). OWNER-check: "Delete personal data" is only shown to OWNER role. Import `useAuth()` to check role in a client sub-component, or handle role visibility server-side. |
| 380.5 | Add `ANONYMIZED` state display to Customer Detail page | 380A | 379.2 | Modify: `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` and/or customer components. If `customer.lifecycleStatus === "ANONYMIZED"`: (1) show an `ANONYMIZED` badge next to the customer name (use `Badge` variant `destructive` or a new `anonymized` variant with slate-500 color), (2) disable all edit buttons and forms (the `edit-customer-dialog` should be hidden), (3) show an info banner: "This customer's personal information has been anonymized. Only financial records have been retained." Pattern: how `OFFBOARDED` customers are handled in existing customer detail page. |
| 380.6 | Add `ANONYMIZED` badge variant | 380A | 380.5 | Modify: `frontend/components/ui/badge.tsx`. Add `anonymized` variant with `bg-slate-100 text-slate-600 border-slate-300` colors (neutral, not alarming — the operation is done and expected). The existing `destructive` variant is too alarming for a terminal state display. |
| 380.7 | Add `ANONYMIZED` to lifecycle status types | 380A | 379.2 | Modify: `frontend/lib/types/` — wherever `LifecycleStatus` is defined (likely in a customer types file). Add `"ANONYMIZED"` to the union type. Update any switch/exhaustive checks that enumerate lifecycle statuses. |
| 380.8 | Write frontend tests for customer data protection actions | 380A | 380.2–380.7 | New file: `frontend/__tests__/customers/data-protection-actions.test.tsx`. 5 tests: (1) "Download all data" button opens `DataExportDialog` with confirmation step, (2) confirmation dialog progresses through steps, (3) `AnonymizeCustomerDialog` shows entity counts from preview, (4) anonymize button is disabled until customer name matches typed input, (5) ANONYMIZED customer detail shows info banner and hides edit/anonymize buttons. Use `vi.mock` for server actions. |

### Key Files

**Create:** `frontend/app/(app)/org/[slug]/customers/[id]/data-protection-actions.ts`, `frontend/components/customers/data-export-dialog.tsx`, `frontend/components/customers/anonymize-customer-dialog.tsx`, `frontend/__tests__/customers/data-protection-actions.test.tsx`

**Modify:** `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` (add action buttons + ANONYMIZED state), `frontend/components/ui/badge.tsx` (add `anonymized` variant), customer types file (add `"ANONYMIZED"`)

### Architecture Decisions

- **SWR polling for export status**: The export can take time (many documents to fetch from S3). Rather than blocking the UI, the dialog initiates the export, then polls `getExportStatus()` every 3 seconds via SWR `refreshInterval` until `status === "COMPLETED"`. On completion, SWR `mutate()` is called to update and stop polling.
- **Type-to-confirm enforced at UI layer (UX) AND service layer (security)**: The `AnonymizationController` (Epic 375) already validates `confirmationName` server-side. The UI validation is a UX affordance, not the only safeguard.
- **ANONYMIZED badge is neutral (slate), not destructive (red)**: The anonymization operation is complete and expected. A red badge suggests an error state. Slate-500 reads as "inactive/archived" — appropriate for a terminal but intentional state.
- **Data protection actions restricted by role in the UI**: "Delete personal data" button is only rendered for OWNER role (checked via `useAuth()` in the client component wrapper). This duplicates the server-side OWNER-only authorization from Epic 375 but improves UX by not showing a button that will fail. Both checks are necessary (defense in depth).

---

## Test Summary

### Backend Tests (new)

| Epic | New Test Files | Approximate Test Count |
|------|---------------|----------------------|
| 373 | `JurisdictionDefaultsTest.java`, extend `OrgSettingsControllerTest` | ~8 |
| 374 | `DataExportServiceTest.java`, `DataExportControllerTest.java` | ~11 |
| 375 | `DataAnonymizationServiceTest.java`, `AnonymizationControllerTest.java` | ~13 |
| 376 | extend `RetentionServiceTest.java`, extend `RetentionControllerTest.java` | ~9 |
| 377 | extend `DataSubjectRequestServiceTest.java`, extend `DataRequestControllerTest.java` | ~6 |
| 378 | `ProcessingActivityControllerTest.java`, `PaiaManualContextBuilderTest.java` | ~9 |
| **Total** | | **~56 new backend tests** |

### Frontend Tests (new)

| Epic | New Test Files | Approximate Test Count |
|------|---------------|----------------------|
| 379 | `data-protection.test.tsx`, `dsar-requests.test.tsx`, `settings-sections.test.tsx` | ~14 |
| 380 | `data-protection-actions.test.tsx` | ~5 |
| **Total** | | **~19 new frontend tests** |

---

## Out-of-Scope Reminders

The following items are explicitly out of scope for Phase 50 (per architecture doc) and must not be added:

- Portal self-serve DSAR submission (no portal UI changes)
- Consent capture engine
- Automated breach notification to the Information Regulator
- Cross-border transfer documentation
- DPIA templates (GDPR — future jurisdiction pack)
- Automated cron-based retention enforcement (v1 uses manual trigger)
- Member/employee PI handling
- Application-level field encryption

---

### Critical Files for Implementation

- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationService.java` - Core service to extend: ANONYMIZED status, pre-anonymization export, preview, confirmation validation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` - Extends with data protection update method and jurisdiction seeding orchestration
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/db/migration/tenant/V75__add_vertical_modules.sql` - Pattern to follow for V76 idempotent DDL
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateContextBuilder.java` - Interface that `PaiaManualContextBuilder` must implement
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` - Customer detail page to extend with data protection actions and ANONYMIZED state display
agentId: ab4f55cfcbef0c382 (use SendMessage with to: 'ab4f55cfcbef0c382' to continue this agent)
<usage>total_tokens: 87271
tool_uses: 27
duration_ms: 464848</usage>
