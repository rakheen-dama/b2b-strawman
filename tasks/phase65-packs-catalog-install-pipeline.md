# Phase 65 — Kazi Packs Catalog & Install Pipeline

Phase 65 introduces Kazi Packs as a first-class product surface: a unified catalog of pre-built content packs, a tenant-visible install/uninstall flow exposed in Settings, and a refactoring of profile-provisioning so that all pack application flows through a single pipeline. This phase migrates Document Template and Automation Template packs to the new pipeline. The remaining 11 pack types keep their existing direct-seeder paths unchanged.

**Architecture doc**: `architecture/phase65-packs-catalog-install-pipeline.md`

**ADRs**:
- [ADR-240](adr/ADR-240-unified-pack-catalog-install-pipeline.md) -- Unified Pack Catalog & Install Pipeline (PackInstall entity + PackInstaller interface with classpath-scanned catalog)
- [ADR-241](adr/ADR-241-add-only-pack-semantics.md) -- Add-Only Pack Semantics (new version = new catalog entry, no update/diff/merge)
- [ADR-242](adr/ADR-242-never-used-uninstall-rule.md) -- "Never Used" Uninstall Rule (hard delete gated by content hash, reference checks, clone checks)
- [ADR-243](adr/ADR-243-scope-two-pack-types-for-v1.md) -- Scope: Two Pack Types for v1 (Document Templates + Automation Templates)

**Dependencies on prior phases**:
- **Phase 12** (Document Templates): `DocumentTemplate` entity, `TemplatePackSeeder`, `GeneratedDocument`, `source_template_id` clone tracking
- **Phase 6** (Audit): `AuditService` and `AuditEventBuilder` for `pack.installed` / `pack.uninstalled` events
- **Phase 6.5** (Notifications): Notification pipeline for install/uninstall confirmations
- **Phase 13** (Dedicated Schema): No `@Filter`, no `tenant_id` column -- schema isolation handles multitenancy
- **Phase 44** (Settings Layout): Settings sidebar structure and `@RequiresCapability("TEAM_OVERSIGHT")` pattern
- **Phase 8** (OrgSettings): `OrgSettings` entity with JSONB pack status fields

**Migration note**: V93 is the latest existing tenant migration. This phase uses V94 and V95.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 473 | PackInstall Entity + Migrations | Backend | -- (Phase 12, 13, 8 complete) | M | 473A, 473B | **Done** (PR #1040) |
| 474 | PackInstaller Interface + Implementations | Backend | 473 | M | 474A, 474B | **Done** (PR #1041) |
| 475 | PackCatalogService + PackInstallService | Backend | 474 | M | 475A | |
| 476 | Profile Provisioning Refactor | Backend | 475 | M | 476A | |
| 477 | PackCatalogController + REST API | Backend | 475 | M | 477A | |
| 478 | Frontend: Settings > Packs Page | Frontend | 477 | M | 478A, 478B | |
| 479 | Integration Tests + E2E | E2E | 477, 478 | S | 479A | |

---

## Dependency Graph

```
PHASE 12 (Document Templates), PHASE 13 (Dedicated Schema),
PHASE 8 (OrgSettings), PHASE 6 (Audit), PHASE 44 (Settings Layout)
— all complete
        |
[E473A  V94 migration (pack_install table +
        FK columns + content_hash columns) +
        PackInstall entity + PackType enum +
        PackCatalogEntry record + UninstallCheck
        record + ContentHashUtil + PackInstallRepository]
        |
[E473B  V95 backfill migration +
        DocumentTemplate entity additions
        (sourcePackInstallId, contentHash) +
        AutomationRule entity additions +
        Repository method additions]
        |
        +──────────────────────────────────+
        |                                  |
[E474A  PackInstaller interface +    [E474A continued]
 TemplatePackInstaller (install +
 availablePacks, wraps existing
 TemplatePackSeeder.applyPack)]
        |
[E474B  AutomationPackInstaller
 (install + availablePacks, wraps
 AutomationTemplateSeeder.applyPack)
 + checkUninstallable for both
 installers + uninstall for both]
        |
[E475A  PackCatalogService (catalog
 aggregation, profile filtering,
 install-state enrichment,
 getPackIdsForProfile) +
 PackInstallService (install,
 internalInstall, uninstall,
 checkUninstallable, audit,
 notification, legacy compat) +
 integration tests (~8)]
        |
        +──────────────────────────────────+
        |                                  |
[E476A  TenantProvisioningService    [E477A  PackCatalogController
 refactor (route doc template +       (5 endpoints) + PackInstallResponse
 automation template packs             DTO + @RequiresCapability +
 through PackInstallService) +         HTTP integration tests (~6)]
 PackReconciliationRunner                      |
 refactor + tests (~4)]               [E478A  API client (lib/api/packs.ts)
        |                              + PackCard component +
        |                              Available tab + page.tsx +
        |                              settings nav update]
        |                                      |
        |                              [E478B  Installed tab +
        |                              uninstall flow (precheck,
        |                              disabled button + tooltip,
        |                              confirmation dialog) +
        |                              empty states + tests (~6)]
        |                                      |
        +──────────────────────────────────────+
                        |
                [E479A  Playwright E2E test:
                 install > verify > edit >
                 blocked uninstall > revert >
                 uninstall succeeds]
```

**Parallel opportunities**:
- After E475A: E476A (provisioning refactor) and E477A (REST controller) are independent and can run in parallel.
- E478A depends only on E477A (needs the API). E478B depends on E478A.
- E479A depends on both E477A and E478B (needs full stack deployed).
- E476A has zero dependency on E477A/E478 -- they can run simultaneously.

---

## Implementation Order

### Stage 0: Foundation (Entity + Migration V94)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 473 | 473A | V94 tenant migration (create `pack_install` table + add `source_pack_install_id` FK and `content_hash` columns to `document_templates` and `automation_rules`). `PackInstall` entity, `PackType` enum, `PackCatalogEntry` record, `UninstallCheck` record, `ContentHashUtil` utility, `PackInstallRepository`. Backend only. | **Done** (PR #1040) |

### Stage 1: Backfill Migration + Entity Extensions

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 473 | 473B | V95 backfill migration (synthetic `PackInstall` rows from OrgSettings JSONB). Add `sourcePackInstallId` and `contentHash` fields to `DocumentTemplate` and `AutomationRule` entities. Add repository methods to `DocumentTemplateRepository`, `AutomationRuleRepository`, `GeneratedDocumentRepository`, `AutomationExecutionRepository`. Backend only. | **Done** (PR #1040) |

### Stage 2: PackInstaller Interface + TemplatePackInstaller

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 474 | 474A | `PackInstaller` interface. `TemplatePackInstaller` implementation (install, availablePacks, wraps `TemplatePackSeeder.applyPack()`). Refactor `TemplatePackSeeder` to expose `applyPack()` as package-private. Integration tests (~4). Backend only. | **Done** (PR #1041) |

### Stage 3: AutomationPackInstaller + Uninstall Checks

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 474 | 474B | `AutomationPackInstaller` implementation (install, availablePacks, wraps `AutomationTemplateSeeder.applyPack()`). `checkUninstallable()` + `uninstall()` for both `TemplatePackInstaller` and `AutomationPackInstaller`. Refactor `AutomationTemplateSeeder` to expose `applyPack()`. Integration tests (~4). Backend only. | **Done** (PR #1041) |

### Stage 4: Services (Catalog + Install)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 475 | 475A | `PackCatalogService` (catalog aggregation, profile filtering, install-state enrichment, `getPackIdsForProfile()`). `PackInstallService` (`install()`, `internalInstall()`, `uninstall()`, `checkUninstallable()`, audit event emission, notification, legacy OrgSettings shim updates). Integration tests (~8). Backend only. | |

### Stage 5: Provisioning Refactor + REST Controller (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 476 | 476A | Modify `TenantProvisioningService` to route document template and automation template packs through `PackInstallService.internalInstall()`. Modify `PackReconciliationRunner` similarly. Keep other 11 pack types unchanged. Integration tests (~4). Backend only. | |
| 5b (parallel) | 477 | 477A | `PackCatalogController` with 5 endpoints (catalog list, installed list, uninstall check, install, uninstall). `PackInstallResponse` record. `@RequiresCapability("TEAM_OVERSIGHT")`. HTTP integration tests (~6). Backend only. | |

### Stage 6: Frontend Available Tab + API Client

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a | 478 | 478A | `lib/api/packs.ts` API client. `PackCard` component. Settings > Packs page with Available tab. Settings nav update (`settings-nav-groups.ts`). Frontend only. | |

### Stage 7: Frontend Installed Tab + Uninstall Flow

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 7a | 478 | 478B | Installed tab. Uninstall precheck flow (disabled button + tooltip with blocking reason). Confirmation dialog. Empty states for both tabs. Frontend tests (~6). Frontend only. | |

### Stage 8: E2E Test

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 8a | 479 | 479A | Playwright E2E test: login > Settings > Packs > install pack > verify content > edit template > blocked uninstall > revert edit > uninstall succeeds. E2E only. | |

### Timeline

```
Stage 0:  [473A]                                          <- entity + V94 migration
Stage 1:  [473B]                                          <- V95 backfill + entity extensions
Stage 2:  [474A]                                          <- PackInstaller + TemplatePackInstaller
Stage 3:  [474B]                                          <- AutomationPackInstaller + uninstall checks
Stage 4:  [475A]                                          <- services (catalog + install)
Stage 5:  [476A]  //  [477A]                              <- provisioning refactor + REST controller (parallel)
Stage 6:  [478A]                                          <- frontend Available tab
Stage 7:  [478B]                                          <- frontend Installed tab + uninstall
Stage 8:  [479A]                                          <- E2E test
```

---

## Epic 473: PackInstall Entity + Migrations

**Goal**: Lay the database foundation for the entire phase by creating the `pack_install` table, adding FK and content hash columns to `document_templates` and `automation_rules`, creating the `PackInstall` entity and supporting types, and backfilling synthetic install records for existing tenants. This is the foundational epic -- every other epic depends on it.

**References**: Architecture doc Sections 65.2 (Domain Model), 65.7 (Database Migrations), 65.8.3 (Entity Code Pattern), 65.8.4 (Repository Pattern).

**Dependencies**: Phase 12 complete (DocumentTemplate entity exists). Phase 13 complete (schema isolation pattern). Phase 8 complete (OrgSettings exists with JSONB pack status fields).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **473A** | 473.1--473.8 | V94 tenant migration (create `pack_install` table, add `source_pack_install_id` FK and `content_hash` columns to `document_templates` and `automation_rules`). `PackType` enum. `PackInstall` entity with `@Entity`, `@Table`, all 8 fields. `PackInstallRepository` with `findByPackId()`. `PackCatalogEntry` record. `UninstallCheck` record. `ContentHashUtil` (SHA-256 over canonical JSON). Migration smoke test + entity CRUD test + ContentHashUtil unit test (~4 tests). Backend only. | **Done** (PR #1040) |
| **473B** | 473.9--473.14 | V95 backfill migration (synthetic `PackInstall` rows from OrgSettings JSONB for document template and automation template packs). Add `sourcePackInstallId` (UUID) and `contentHash` (String) fields to `DocumentTemplate` entity. Add same fields to `AutomationRule` entity. Add `findBySourcePackInstallId()` and `countBySourcePackInstallId()` to `DocumentTemplateRepository` and `AutomationRuleRepository`. Add `existsByTemplateIdIn()` to `GeneratedDocumentRepository`. Add `existsByRuleIdIn()` to `AutomationExecutionRepository`. Backfill integration test (~3 tests). Backend only. | **Done** (PR #1040) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 473.1 | Create V94 tenant migration | 473A | -- | New file: `backend/src/main/resources/db/migration/tenant/V94__create_pack_install.sql`. Full SQL from architecture doc Section 65.7: CREATE TABLE `pack_install` (8 columns, PK on `id`, UNIQUE on `pack_id`). ALTER TABLE `document_templates` ADD `source_pack_install_id UUID FK` + index. ALTER TABLE `document_templates` ADD `content_hash VARCHAR(64)`. ALTER TABLE `automation_rules` ADD `source_pack_install_id UUID FK` + index. ALTER TABLE `automation_rules` ADD `content_hash VARCHAR(64)`. FK uses `ON DELETE SET NULL`. Pattern: `V93__deactivate_legacy_legal_client_onboarding_all_variants.sql` for naming convention. |
| 473.2 | Create `PackType` enum | 473A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackType.java`. Two values: `DOCUMENT_TEMPLATE`, `AUTOMATION_TEMPLATE`. Simple enum with comment about future extensibility. Pattern: `invoice/TaxType.java` or any simple enum in the codebase. |
| 473.3 | Create `PackInstall` entity | 473A | 473.1, 473.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstall.java`. `@Entity`, `@Table(name = "pack_install")`. 8 fields per architecture doc Section 65.2.1: `id` UUID PK, `packId` VARCHAR(128) NOT NULL, `packType` PackType with `@Enumerated(EnumType.STRING)`, `packVersion` VARCHAR(32), `packName` VARCHAR(256), `installedAt` Instant, `installedByMemberId` UUID nullable, `itemCount` int. No `@Filter`, no `tenant_id` -- schema isolation (Phase 13 convention). Protected no-arg constructor + public constructor per architecture doc Section 65.8.3. Pattern: any entity in `customer/Customer.java` for field annotation style. |
| 473.4 | Create `PackInstallRepository` | 473A | 473.3 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallRepository.java`. `JpaRepository<PackInstall, UUID>` with `Optional<PackInstall> findByPackId(String packId)`. Pattern: architecture doc Section 65.8.4. Reference: `customer/CustomerRepository.java`. |
| 473.5 | Create `PackCatalogEntry` record | 473A | 473.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogEntry.java`. Java record with 9 fields per architecture doc Section 65.2.3: `packId`, `name`, `description`, `version`, `type` (PackType), `verticalProfile` (nullable), `itemCount`, `installed`, `installedAt` (String, ISO-8601 or null). |
| 473.6 | Create `UninstallCheck` record | 473A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/UninstallCheck.java`. Java record with 2 fields per architecture doc Section 65.2.5: `canUninstall` (boolean), `blockingReason` (String, null if canUninstall). |
| 473.7 | Create `ContentHashUtil` utility | 473A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/ContentHashUtil.java`. Static utility class. `public static String computeHash(String canonicalJson)` -- SHA-256 hex digest. `public static String canonicalizeJson(JsonNode node)` -- sort keys alphabetically, no formatting whitespace. Uses Jackson `ObjectMapper` with `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS`. Pattern: no existing utility -- create standalone with no Spring dependencies. |
| 473.8 | Write migration + entity + hash tests | 473A | 473.1--473.7 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallFoundationTest.java`. 4 tests: (1) V94 migration applies without error, (2) `PackInstall` CRUD: create, findByPackId, verify all fields, (3) `ContentHashUtil.computeHash` produces consistent SHA-256 (same input same output), (4) `ContentHashUtil.canonicalizeJson` sorts keys and strips whitespace. Integration test with `@SpringBootTest` + embedded Postgres. Pattern: `customer/V87MigrationTest.java` (from Phase 63). |
| 473.9 | Create V95 backfill migration | 473B | 473A | New file: `backend/src/main/resources/db/migration/tenant/V95__backfill_pack_installs.sql`. Full SQL from architecture doc Section 65.7 (V95 block). Part 1: iterate `OrgSettings.template_pack_status` JSONB, create synthetic `PackInstall` rows for each pack, link `document_templates` via `pack_id` column. Part 2: iterate `OrgSettings.automation_pack_status` JSONB, create synthetic `PackInstall` rows, link `automation_rules` via 60-second timestamp heuristic on `created_at` + `source = 'TEMPLATE'`. Leading comment block documenting the best-effort tradeoff for automation rules. |
| 473.10 | Add `sourcePackInstallId` and `contentHash` to `DocumentTemplate` entity | 473B | 473.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java`. Add 2 fields: `sourcePackInstallId` UUID with `@Column(name = "source_pack_install_id")` (nullable), `contentHash` String with `@Column(name = "content_hash", length = 64)` (nullable). Add getters/setters. |
| 473.11 | Add `sourcePackInstallId` and `contentHash` to `AutomationRule` entity | 473B | 473.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRule.java`. Add 2 fields: `sourcePackInstallId` UUID, `contentHash` String. Same pattern as 473.10. |
| 473.12 | Add repository methods to `DocumentTemplateRepository` and `GeneratedDocumentRepository` | 473B | 473.10 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateRepository.java`. Add: `List<DocumentTemplate> findBySourcePackInstallId(UUID sourcePackInstallId)`, `int countBySourcePackInstallId(UUID sourcePackInstallId)`. Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/GeneratedDocumentRepository.java` (or equivalent). Add: `boolean existsByTemplateIdIn(List<UUID> templateIds)`. |
| 473.13 | Add repository methods to `AutomationRuleRepository` and `AutomationExecutionRepository` | 473B | 473.11 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleRepository.java`. Add: `List<AutomationRule> findBySourcePackInstallId(UUID sourcePackInstallId)`, `int countBySourcePackInstallId(UUID sourcePackInstallId)`. Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationExecutionRepository.java`. Add: `boolean existsByRuleIdIn(List<UUID> ruleIds)`. |
| 473.14 | Write backfill + entity extension tests | 473B | 473.9--473.13 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallBackfillTest.java`. 3 tests: (1) V95 migration on tenant with pre-existing template packs: verify `PackInstall` rows created, `source_pack_install_id` populated on `document_templates`, (2) V95 migration on tenant with automation packs: verify `PackInstall` rows created, rules within timestamp window attributed, (3) repository methods (`findBySourcePackInstallId`, `existsByTemplateIdIn`, `existsByRuleIdIn`) return correct results. Requires a tenant provisioned with packs via existing seeders before V95 is applied. Pattern: see existing provisioning tests. |

### Key Files

**Slice 473A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V94__create_pack_install.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstall.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/UninstallCheck.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/ContentHashUtil.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallFoundationTest.java`

**Slice 473A -- Read for context:**
- `backend/src/main/resources/db/migration/tenant/V93__deactivate_legacy_legal_client_onboarding_all_variants.sql` -- Migration naming reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` -- Entity annotation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` -- Repository pattern

**Slice 473B -- Create:**
- `backend/src/main/resources/db/migration/tenant/V95__backfill_pack_installs.sql`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallBackfillTest.java`

**Slice 473B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` -- Add 2 fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRule.java` -- Add 2 fields (in `automation/` package, may be nested under `automation/template/`)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplateRepository.java` -- Add query methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRuleRepository.java` -- Add query methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/GeneratedDocumentRepository.java` -- Add `existsByTemplateIdIn`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationExecutionRepository.java` -- Add `existsByRuleIdIn`

### Architecture Decisions

- **Two migrations (V94 + V95) in separate slices**: V94 is DDL-only (table creation, column additions). V95 is DML (backfill). Separating them allows V94 to be validated independently and avoids a single mega-migration. The backfill migration depends on the schema changes from V94.
- **FK ON DELETE SET NULL**: If a `PackInstall` row is ever deleted without going through the proper uninstall flow (e.g., manual DB intervention), content rows are preserved with a null FK rather than being silently cascade-deleted. This is the conservative choice for a production system.
- **Automation rule backfill is best-effort**: The 60-second timestamp heuristic is documented in the migration's leading comment block. Unattributed rules cannot be uninstalled (safe default).
- **No `@Filter`, no `tenant_id`**: `PackInstall` follows the Phase 13 schema isolation convention. Standard `JpaRepository` with no multitenancy boilerplate.
- **ContentHashUtil as a static utility**: No Spring dependency -- pure function for SHA-256 over canonical JSON. Uses Jackson's `ORDER_MAP_ENTRIES_BY_KEYS` for deterministic key ordering.

---

## Epic 474: PackInstaller Interface + Implementations

**Goal**: Define the `PackInstaller` interface that each pack type implements, and create the two concrete implementations for this phase: `TemplatePackInstaller` and `AutomationPackInstaller`. Each wraps the existing seeder's `applyPack()` logic with `PackInstall` row creation, `source_pack_install_id` tagging, content hash computation, and strict uninstall gate logic.

**References**: Architecture doc Sections 65.2.4 (PackInstaller Interface), 65.3.2 (Install Flow steps 4-5), 65.3.3 (Uninstall Flow steps 2-4), 65.3.4 (Uninstall Check Details), 65.10 Slice 65B.

**Dependencies**: Epic 473 (PackInstall entity, repositories, ContentHashUtil, entity field additions on DocumentTemplate/AutomationRule).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **474A** | 474.1--474.5 | `PackInstaller` interface (5 methods: `type()`, `availablePacks()`, `install()`, `checkUninstallable()`, `uninstall()`). `TemplatePackInstaller` `@Service` implementation: `install()` wraps `TemplatePackSeeder.applyPack()` with PackInstall creation, source_pack_install_id tagging, content_hash computation. `availablePacks()` delegates to classpath scan. Refactor `TemplatePackSeeder` to expose `applyPack()` as package-private. Integration tests (~4). Backend only. | **Done** (PR #1041) |
| **474B** | 474.6--474.10 | `AutomationPackInstaller` `@Service` implementation: same pattern as TemplatePackInstaller but wraps `AutomationTemplateSeeder.applyPack()`. Hash includes trigger_config + conditions + serialized actions. Refactor `AutomationTemplateSeeder` to expose `applyPack()`. Implement `checkUninstallable()` for `TemplatePackInstaller` (hash check, generated doc refs, clone refs). Implement `checkUninstallable()` for `AutomationPackInstaller` (hash check, execution refs). Implement `uninstall()` for both. Integration tests (~4). Backend only. | **Done** (PR #1041) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 474.1 | Create `PackInstaller` interface | 474A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstaller.java`. Interface with 5 methods per architecture doc Section 65.2.4: `PackType type()`, `List<PackCatalogEntry> availablePacks()`, `void install(String packId, String tenantId, String memberId)`, `UninstallCheck checkUninstallable(String packId, String tenantId)`, `void uninstall(String packId, String tenantId, String memberId)`. Javadoc: `install()` is idempotent; `uninstall()` MUST call `checkUninstallable()` first. |
| 474.2 | Refactor `TemplatePackSeeder` to expose `applyPack()` | 474A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java`. Change `applyPack()` visibility from private to package-private (default access). Add a package-private method or ensure the seeder's pack-application logic (JSON parsing, template creation) is callable by `TemplatePackInstaller` in the `packs/` package. If cross-package access is needed, consider extracting a `TemplatePackApplier` helper or making the method `protected`. Verify existing tests still pass. |
| 474.3 | Create `TemplatePackInstaller` | 474A | 474.1, 474.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/TemplatePackInstaller.java`. `@Service` implementing `PackInstaller`. `type()` returns `DOCUMENT_TEMPLATE`. `availablePacks()` delegates to classpath scan via `TemplatePackSeeder`'s existing infrastructure, maps to `PackCatalogEntry` records. `install()`: creates `PackInstall` row, delegates content creation to seeder logic, sets `source_pack_install_id` on each created `DocumentTemplate`, computes `content_hash` via `ContentHashUtil` on canonical JSON of template `content` JSONB, updates `itemCount` on `PackInstall`. Inject `PackInstallRepository`, `DocumentTemplateRepository`, and the seeder/applier. |
| 474.4 | Write TemplatePackInstaller install tests | 474A | 474.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/TemplatePackInstallerTest.java`. 4 tests: (1) install creates `PackInstall` row with correct `packType`, `packName`, `itemCount`, (2) all installed `DocumentTemplate` rows have non-null `source_pack_install_id` matching the install ID, (3) all installed `DocumentTemplate` rows have non-null `content_hash`, (4) idempotency: second install of same pack is a no-op (no duplicate templates). `@SpringBootTest` + embedded Postgres. Provision a test tenant first. |
| 474.5 | Verify existing TemplatePackSeeder tests still pass | 474A | 474.2 | Not a new file -- run existing tests: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeederTest.java` and `AccountingTemplatePackSeederTest.java`. Ensure the refactoring in 474.2 does not break existing seeder behavior. Fix any compilation issues. |
| 474.6 | Refactor `AutomationTemplateSeeder` to expose `applyPack()` | 474B | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateSeeder.java`. Same pattern as 474.2: expose pack-application logic for `AutomationPackInstaller` to call. Verify existing tests pass. |
| 474.7 | Create `AutomationPackInstaller` | 474B | 474.1, 474.6 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/AutomationPackInstaller.java`. `@Service` implementing `PackInstaller`. `type()` returns `AUTOMATION_TEMPLATE`. Same structure as `TemplatePackInstaller` but wraps `AutomationTemplateSeeder`. Content hash: SHA-256 over canonical JSON of `trigger_config` + `conditions` + serialized `AutomationAction` list (sorted by action order). Uses `ContentHashUtil`. Sets `source_pack_install_id` and `content_hash` on each `AutomationRule`. |
| 474.8 | Implement `checkUninstallable()` for `TemplatePackInstaller` | 474B | 474.3 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/TemplatePackInstaller.java`. Implement three gate checks per architecture doc Section 65.3.4: (1) content hash mismatch count, (2) `existsByTemplateIdIn()` on `GeneratedDocumentRepository`, (3) clone refs via `SELECT COUNT(*) FROM document_templates WHERE source_template_id IN (...)` (add repo method if needed). Build blocking reason string per format in architecture doc. Return `UninstallCheck`. |
| 474.9 | Implement `checkUninstallable()` and `uninstall()` for both installers | 474B | 474.7, 474.8 | Modify `AutomationPackInstaller`: implement `checkUninstallable()` with two gate checks per architecture doc Section 65.3.4: (1) content hash mismatch, (2) `existsByRuleIdIn()` on `AutomationExecutionRepository`. No clone check (automation_rule has no clone column). Implement `uninstall()` on both installers: `DELETE all content rows WHERE source_pack_install_id = install.id`, then `DELETE PackInstall row`. Both must call `checkUninstallable()` first and throw `ResourceConflictException` if blocked. |
| 474.10 | Write checkUninstallable + uninstall tests | 474B | 474.8, 474.9 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackUninstallGateTest.java`. 4 tests: (1) clean install + immediate uninstall succeeds -- all content and PackInstall removed, (2) edit a template's content (change content_hash) then checkUninstallable returns `{canUninstall: false, blockingReason: "1 of N templates have been edited"}`, (3) create a GeneratedDocument referencing a pack template then checkUninstallable returns blocked, (4) for automation: trigger an execution then checkUninstallable returns blocked. |

### Key Files

**Slice 474A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstaller.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/TemplatePackInstaller.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/TemplatePackInstallerTest.java`

**Slice 474A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` -- Expose `applyPack()` as package-private or protected

**Slice 474A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java` -- Understand classpath scan + JSON deserialization
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeederTest.java` -- Existing seeder test pattern

**Slice 474B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/AutomationPackInstaller.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackUninstallGateTest.java`

**Slice 474B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateSeeder.java` -- Expose `applyPack()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/TemplatePackInstaller.java` -- Add `checkUninstallable()` + `uninstall()` implementations

### Architecture Decisions

- **Installers in `packs/` package, seeders in domain packages**: The `PackInstaller` implementations live in the new `packs/` package and call into the seeder's `applyPack()` logic via package-private or protected access. This avoids moving seeders and keeps the domain packages stable.
- **Content hash for automation rules includes actions**: Because `AutomationAction` is a child entity, the hash must include the serialized actions list (sorted by action order) so that action edits are detected even though actions are a separate entity.
- **checkUninstallable is a read-only precheck**: The UI calls it to enable/disable the uninstall button. The uninstall endpoint calls it again before deletion (TOCTOU-safe because the uninstall runs in a single transaction).
- **ResourceConflictException for blocked uninstall**: Maps to 409 via `GlobalExceptionHandler`. Not `InvalidStateException` (which maps to 400) because the semantics are a state conflict (content is in use).

---

## Epic 475: PackCatalogService + PackInstallService

**Goal**: Create the two core service classes that sit between the controller and the installers. `PackCatalogService` aggregates catalog entries from all registered `PackInstaller` beans, applies profile filtering, and enriches entries with install state. `PackInstallService` provides the single entry point for install/uninstall with audit events, notifications, and legacy OrgSettings compatibility shim updates.

**References**: Architecture doc Sections 65.3.1 (Catalog Discovery), 65.3.2 (Install Flow), 65.3.3 (Uninstall Flow), 65.3.5 (Profile Provisioning -- `internalInstall`), 65.3.6 (Backfill Strategy -- legacy shim), 65.3.8 (Legacy Compatibility), 65.10 Slice 65C.

**Dependencies**: Epic 474 (PackInstaller implementations).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **475A** | 475.1--475.8 | `PackCatalogService` (aggregate entries from all `PackInstaller` beans via Spring `List<PackInstaller>` injection, profile filtering by `OrgSettings.verticalProfile`, install-state enrichment via `PackInstallRepository`, `getPackIdsForProfile()`, `findCatalogEntry()`). `PackInstallService` (`install()` with profile affinity + idempotency + installer delegation + OrgSettings shim + audit + notification, `internalInstall()` with explicit tenantId and no profile check, `uninstall()` with gate + content deletion + OrgSettings removal + audit, `checkUninstallable()` delegation). Integration tests (~8). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 475.1 | Create `PackCatalogService` | 475A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogService.java`. `@Service`. Inject `List<PackInstaller>` (Spring auto-collects all implementations). Methods: (1) `listCatalog(boolean showAll)`: collect `availablePacks()` from each installer, if `showAll = false` then filter by `verticalProfile` == null OR matches tenant's `OrgSettings.verticalProfile`, enrich with install state via `PackInstallRepository.findByPackId()`. (2) `listInstalled()`: query all `PackInstall` rows, map to `PackCatalogEntry` with `installed = true`. (3) `getPackIdsForProfile(String verticalProfile, PackType type)`: filter entries for profile and type, return pack IDs. (4) `findCatalogEntry(String packId)`: find by packId across all installers. Build an internal `Map<PackType, PackInstaller>` in constructor for O(1) lookup. Pattern: other aggregate services in the codebase (e.g., `DashboardService`). |
| 475.2 | Create `PackInstallService` | 475A | 475.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallService.java`. `@Service`. Inject `PackCatalogService`, `PackInstallRepository`, `Map<PackType, PackInstaller>` installer registry, `AuditService`, `NotificationService` (or equivalent), `OrgSettingsService`. Methods per architecture doc Section 65.3.2 and 65.3.3: `install(String packId, String memberId)` -- reads tenantId from `RequestScopes.TENANT_ID`, profile affinity check, idempotency check, delegate to installer, update legacy OrgSettings, emit audit `pack.installed`, emit notification, return `PackInstall`. `internalInstall(String packId, String tenantId)` -- explicit tenantId, no profile check, `installedByMemberId = null`. `uninstall(String packId, String memberId)` -- find install, gate check, delegate uninstall, remove from OrgSettings, emit audit `pack.uninstalled`. `checkUninstallable(String packId)` -- delegate to installer. |
| 475.3 | Implement legacy OrgSettings shim update in `PackInstallService` | 475A | 475.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallService.java`. On install: update `OrgSettings.templatePackStatus` or `automationPackStatus` JSONB field (add entry). On uninstall: remove entry from same field. Also populate `document_template.pack_id` and `pack_template_key` columns via `TemplatePackInstaller` for template packs (legacy compatibility shim). Inject `OrgSettingsService` or `OrgSettingsRepository`. Pattern: see existing code in `TemplatePackSeeder` that populates these fields. |
| 475.4 | Implement audit event emission | 475A | 475.2 | Modify: `PackInstallService`. Emit `pack.installed` audit event (include packId, packType, itemCount, memberId). Emit `pack.uninstalled` audit event. Emit `pack.uninstall_blocked` on every blocked attempt (include blockingReason). Use existing `AuditService` and `AuditEventBuilder` pattern from Phase 6. |
| 475.5 | Implement notification emission | 475A | 475.2 | Modify: `PackInstallService`. Emit info-level notification to installing member on successful install. Emit info-level notification on successful uninstall. Use existing notification pipeline from Phase 6.5. Skip notification for `internalInstall` (no member to notify). |
| 475.6 | Write install flow integration tests | 475A | 475.2 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallServiceTest.java`. 4 tests: (1) install creates `PackInstall` row + content rows with source_pack_install_id, (2) double-install returns existing install (idempotency), (3) `OrgSettings.templatePackStatus` updated after install, (4) profile affinity: install() rejects cross-profile pack, internalInstall() accepts it. |
| 475.7 | Write uninstall flow integration tests | 475A | 475.2 | Extend or add to `PackInstallServiceTest.java`. 3 tests: (1) install + immediate uninstall: all content + PackInstall removed, OrgSettings field cleared, (2) install + edit template + uninstall returns 409 with blocking reason, (3) audit events emitted for install and uninstall. |
| 475.8 | Write catalog listing tests | 475A | 475.1 | Extend or add to `PackInstallServiceTest.java`. 1 test: `listCatalog(false)` returns only profile-matched + universal packs; `listCatalog(true)` returns all packs. Install-state enrichment: installed packs show `installed = true` and `installedAt` set. |

### Key Files

**Slice 475A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallServiceTest.java`

**Slice 475A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` -- JSONB fields `templatePackStatus`, `automationPackStatus`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` -- How to read/update OrgSettings
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` -- Audit event emission pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- Notification emission pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` -- ScopedValue access

### Architecture Decisions

- **Installer registry map**: `PackInstallService` builds a `Map<PackType, PackInstaller>` in its constructor from the injected `List<PackInstaller>`. This provides O(1) lookup by type and fails fast (at boot time) if two installers register for the same type.
- **Separate `install()` vs `internalInstall()`**: `install()` reads tenantId from `RequestScopes.TENANT_ID` and enforces profile affinity. `internalInstall()` accepts an explicit tenantId and skips profile checks. This separation keeps the HTTP path clean while allowing provisioning/reconciliation to call from system context.
- **Dual source of truth (transition period)**: Both `PackInstall` rows and OrgSettings JSONB fields are populated on install. This ensures `PackReconciliationRunner` (which still runs for all 13 pack types) correctly skips already-installed packs.
- **Audit event on every blocked uninstall attempt**: `pack.uninstall_blocked` event with the blocking reason provides admin visibility into why uninstalls fail, useful for support.

---

## Epic 476: Profile Provisioning Refactor

**Goal**: Modify `TenantProvisioningService` and `PackReconciliationRunner` to route document template and automation template pack installation through the new `PackInstallService.internalInstall()` pipeline instead of calling seeders directly. All other 11 pack types remain on their existing direct seeder calls. After this change, newly provisioned tenants have `PackInstall` rows and full content attribution for these two pack types.

**References**: Architecture doc Sections 65.3.5 (Profile Provisioning Refactor), 65.10 Slice 65D.

**Dependencies**: Epic 475 (PackInstallService with `internalInstall()`).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **476A** | 476.1--476.5 | Modify `TenantProvisioningService`: replace direct `TemplatePackSeeder.seedPacksForTenant()` and `AutomationTemplateSeeder.seedPacksForTenant()` calls with `PackCatalogService.getPackIdsForProfile()` + `PackInstallService.internalInstall(packId, tenantId)` loop. Modify `PackReconciliationRunner`: same replacement for these two pack types. All other 11 seeder calls unchanged. Integration tests (~4). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 476.1 | Modify `TenantProvisioningService` to use new pipeline | 476A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`. Replace `templatePackSeeder.seedPacksForTenant(schemaName, clerkOrgId)` call with: `List<String> templatePackIds = packCatalogService.getPackIdsForProfile(verticalProfile, PackType.DOCUMENT_TEMPLATE); for (String packId : templatePackIds) { packInstallService.internalInstall(packId, tenantId); }`. Same for `automationTemplateSeeder.seedPacksForTenant()` replaced with `getPackIdsForProfile(..., AUTOMATION_TEMPLATE)` + `internalInstall()` loop. Keep all other 11 seeder calls unchanged. Inject `PackCatalogService` and `PackInstallService` (constructor injection). Verify the call ordering: schema creation + Flyway migrations must complete before pack installs. |
| 476.2 | Modify `PackReconciliationRunner` to use new pipeline | 476A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PackReconciliationRunner.java`. Same replacement as 476.1 but in the startup reconciliation loop. For each tenant schema, replace `templatePackSeeder.seedPacksForTenant()` and `automationTemplateSeeder.seedPacksForTenant()` with `packCatalogService.getPackIdsForProfile()` + `packInstallService.internalInstall(packId, tenantId)`. Keep all other 11 seeder calls unchanged. `internalInstall` is idempotent (already-installed packs are no-ops), matching existing seeder idempotency. |
| 476.3 | Write provisioning integration test | 476A | 476.1 | Extend: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningServiceTest.java`. 2 tests: (1) provision tenant with `legal-za` profile: verify `PackInstall` rows exist for each template and automation template pack in the profile, verify all document_templates have non-null `source_pack_install_id`, verify all automation_rules from packs have non-null `source_pack_install_id`, (2) verify legacy OrgSettings `templatePackStatus` is still populated (compatibility shim). |
| 476.4 | Write reconciliation runner test | 476A | 476.2 | Extend: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/provisioning/PackReconciliationRunnerTest.java`. 1 test: provision tenant, call reconciliation runner, verify no duplicate PackInstall rows (idempotency). Verify other pack types (e.g., field packs, clause packs) are still seeded via direct seeder calls. |
| 476.5 | Verify existing provisioning tests pass | 476A | 476.1, 476.2 | Run existing tests: `TenantProvisioningServiceTest.java`, `PackReconciliationRunnerTest.java`. The refactoring must not break existing behavior. End state: newly provisioned tenants have identical content to today, plus non-null `source_pack_install_id` and `PackInstall` rows for template and automation packs. |

### Key Files

**Slice 476A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` -- Replace 2 seeder calls with PackInstallService pipeline
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PackReconciliationRunner.java` -- Same replacement

**Slice 476A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` -- Understand the seeder call being replaced
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/template/AutomationTemplateSeeder.java` -- Same
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` -- Understand how vertical profile is resolved

### Architecture Decisions

- **Replace, not wrap**: The direct seeder calls are replaced, not wrapped. `PackInstallService.internalInstall()` calls into the installer which calls the seeder's `applyPack()` internally. This avoids double execution.
- **Profile resolution moved to `PackCatalogService`**: Previously the provisioning service hardcoded which packs to apply for each profile. Now it asks `PackCatalogService.getPackIdsForProfile()`, which derives the list from the classpath-scanned catalog filtered by profile. This is more maintainable -- adding a new pack to a profile only requires adding the JSON file with the correct `verticalProfile` metadata.
- **Other 11 pack types untouched**: This is a scoped migration. Direct seeder calls for field packs, clause packs, compliance packs, etc. remain exactly as they are.

---

## Epic 477: PackCatalogController + REST API

**Goal**: Create the REST controller that exposes the 5 pack endpoints (catalog list, installed list, uninstall check, install, uninstall). This is a thin controller that delegates all logic to `PackCatalogService` and `PackInstallService`. All endpoints require `TEAM_OVERSIGHT` capability.

**References**: Architecture doc Sections 65.4 (API Surface), 65.9 (Permission Model Summary), 65.10 Slice 65E.

**Dependencies**: Epic 475 (services).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **477A** | 477.1--477.6 | `PackCatalogController` with 5 endpoints. `PackInstallResponse` record (API response DTO mapped from `PackInstall` entity). `@RequiresCapability("TEAM_OVERSIGHT")` on all endpoints. HTTP integration tests covering all 5 endpoints, error paths (404, 409), profile filtering, `?all=true` parameter. (~6 tests). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 477.1 | Create `PackInstallResponse` record | 477A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallResponse.java`. Java record with 8 fields per architecture doc Section 65.4: `id` UUID, `packId` String, `packType` String, `packVersion` String, `packName` String, `installedAt` String (ISO-8601), `installedByMemberId` UUID (nullable), `itemCount` int. Add a static factory method `from(PackInstall entity)` to map entity to DTO. |
| 477.2 | Create `PackCatalogController` | 477A | 477.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogController.java`. `@RestController`, `@RequestMapping("/api/packs")`, `@RequiresCapability("TEAM_OVERSIGHT")`. 5 endpoints per architecture doc Section 65.4: `GET /api/packs/catalog` (accepts `@RequestParam(defaultValue = "false") boolean all`), `GET /api/packs/installed`, `GET /api/packs/{packId}/uninstall-check`, `POST /api/packs/{packId}/install`, `DELETE /api/packs/{packId}`. Each is a one-liner delegation per backend CLAUDE.md controller discipline. Install returns `ResponseEntity.ok(PackInstallResponse.from(result))`. Uninstall returns `ResponseEntity.noContent().build()`. Inject `PackCatalogService` and `PackInstallService`. |
| 477.3 | Write catalog GET integration tests | 477A | 477.2 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogControllerIntegrationTest.java`. 2 tests: (1) `GET /api/packs/catalog` returns 200 with JSON array of `PackCatalogEntry` objects filtered by profile, (2) `GET /api/packs/catalog?all=true` returns all packs across all profiles. Verify response shape matches architecture doc. Use `TestJwtFactory.ownerJwt()`. |
| 477.4 | Write install/uninstall endpoint integration tests | 477A | 477.2 | Extend: `PackCatalogControllerIntegrationTest.java`. 2 tests: (1) `POST /api/packs/{packId}/install` returns 200 with `PackInstallResponse`, verify `itemCount > 0`, (2) `DELETE /api/packs/{packId}` on a freshly installed (unedited, unused) pack returns 204. |
| 477.5 | Write error path integration tests | 477A | 477.2 | Extend: `PackCatalogControllerIntegrationTest.java`. 2 tests: (1) `POST /api/packs/nonexistent-pack/install` returns 404 ProblemDetail, (2) install a pack, edit a template, then `DELETE /api/packs/{packId}` returns 409 ProblemDetail with `detail` containing "templates have been edited". Verify the ProblemDetail `status`, `title`, `detail` fields. |
| 477.6 | Write uninstall-check endpoint test | 477A | 477.2 | Extend: `PackCatalogControllerIntegrationTest.java`. 1 test: `GET /api/packs/{packId}/uninstall-check` returns 200 with `{canUninstall: true/false, blockingReason: null/"..."}`. Install a pack, check returns `canUninstall: true`. Edit a template, check returns `canUninstall: false` with reason. |

### Key Files

**Slice 477A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstallResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogControllerIntegrationTest.java`

**Slice 477A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` -- Controller with `@RequiresCapability("TEAM_OVERSIGHT")` pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/ModuleSettingsController.java` -- Another settings controller reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ResourceConflictException.java` -- 409 mapping pattern

### Architecture Decisions

- **Thin controller -- all one-liners**: Consistent with backend CLAUDE.md controller discipline. No `if/else`, no orchestration, no multiple service calls.
- **`@RequiresCapability("TEAM_OVERSIGHT")` at class level**: All 5 endpoints share the same capability requirement. Class-level annotation avoids repetition.
- **packId in URL path**: `{packId}` is a slug-like string (e.g., `litigation-templates-2026-v1`), not a UUID. This matches the catalog's pack identifier and makes URLs readable.
- **409 for blocked uninstall, not 400**: The semantics are a state conflict (content is in use), which aligns with `ResourceConflictException`'s 409 status.

---

## Epic 478: Frontend -- Settings > Packs Page

**Goal**: Build the Settings > Packs page with Available and Installed tabs, pack card components, install flow with success feedback, uninstall flow with precheck/disabled button/tooltip/confirmation dialog, and empty states. Add the "Packs" entry to the settings sidebar navigation.

**References**: Architecture doc Sections 65.6 (Frontend), 65.6.1-65.6.5.

**Dependencies**: Epic 477 (REST API endpoints must be available).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **478A** | 478.1--478.6 | API client (`lib/api/packs.ts`). TypeScript types (`PackCatalogEntry`, `UninstallCheck`, `PackInstallResponse`). `PackCard` component (reusable card for Available + Installed views). Settings > Packs page (`page.tsx`) with Available tab (header, "Show all packs" toggle, responsive grid, install button). Settings nav update (`settings-nav-groups.ts` -- add "Packs" entry). Frontend only. | |
| **478B** | 478.7--478.12 | Installed tab (grid of installed pack cards with install date/member info). Uninstall precheck flow (call `checkPackUninstallable` on mount, disable button with `Tooltip` showing blocking reason if blocked). `AlertDialog` confirmation for uninstall. Empty states for both tabs. Server actions for install/uninstall. Frontend tests (~6). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 478.1 | Create TypeScript types for packs | 478A | -- | New file: `frontend/lib/types/packs.ts` (or inline in `lib/api/packs.ts` if the project inlines types with API clients). Define `PackCatalogEntry`, `UninstallCheck`, `PackInstallResponse` interfaces per architecture doc Section 65.6.3. Use `type` for unions (`'DOCUMENT_TEMPLATE' \| 'AUTOMATION_TEMPLATE'`). Pattern: check existing type definitions in `lib/api/` files (e.g., `lib/api/automations.ts` or `lib/api/templates.ts`). |
| 478.2 | Create API client functions | 478A | 478.1 | New file: `frontend/lib/api/packs.ts`. 5 functions per architecture doc Section 65.6.3: `listPackCatalog(opts?)`, `listInstalledPacks()`, `checkPackUninstallable(packId)`, `installPack(packId)`, `uninstallPack(packId)`. Use existing `apiClient` from `lib/api/client.ts`. Pattern: `lib/api/automations.ts` or `lib/api/templates.ts` for fetch wrapper usage. |
| 478.3 | Create `PackCard` component | 478A | 478.1 | New file: `frontend/components/settings/pack-card.tsx`. `"use client"` component. Props: `pack: PackCatalogEntry`, `onInstall?: (packId: string) => void`, `variant: 'available' \| 'installed'`, `uninstallCheck?: UninstallCheck`, `onUninstall?: (packId: string) => void`. Renders Shadcn `Card` with: pack name + version (title), description (body), `Badge` for item count (e.g., "6 templates"), `Badge` for type, `Badge` for profile affinity. Available variant: "Install" button (primary) or "Installed" (disabled + check icon). Installed variant: install date + member info, "Uninstall" button. Pattern: existing card usage in `components/settings/features-settings-form.tsx` for card layout in settings context. |
| 478.4 | Create Settings > Packs page with Available tab | 478A | 478.2, 478.3 | New file: `frontend/app/(app)/org/[slug]/settings/packs/page.tsx`. Server component that fetches `listPackCatalog()` on the server. Renders Shadcn `Tabs` with two tabs: Available and Installed. Available tab: header "Kazi Packs -- extend your workspace with pre-built content", `Switch` for "Show all packs" (client-side toggle, re-fetches with `{all: true}`), responsive grid of `PackCard` components in "available" variant. For now, Installed tab renders placeholder; completed in 478B. Page metadata: `export const metadata = { title: "Packs" }`. Pattern: `frontend/app/(app)/org/[slug]/settings/features/page.tsx` for settings page structure. |
| 478.5 | Add "Packs" to settings sidebar navigation | 478A | -- | Modify: `frontend/components/settings/settings-nav-groups.ts`. Add a new item to an appropriate group. Suggested: add to the "Features" group or create a new "Content" group: `{ label: "Packs", href: "packs", adminOnly: true }`. The `adminOnly: true` flag matches `TEAM_OVERSIGHT` requirement. Verify the href resolves correctly to `/org/[slug]/settings/packs`. |
| 478.6 | Create server actions for install | 478A | 478.2 | New file: `frontend/app/(app)/org/[slug]/settings/packs/actions.ts`. `"use server"` module. `installPackAction(packId: string)` -- calls `installPack(packId)` from API client, returns `PackInstallResponse`. Pattern: `frontend/app/(app)/org/[slug]/settings/features/page.tsx` or another settings page's `actions.ts`. |
| 478.7 | Implement Installed tab | 478B | 478A | Modify: `frontend/app/(app)/org/[slug]/settings/packs/page.tsx` (or extract to a client component). Installed tab: header "Installed Packs", fetch `listInstalledPacks()`, render grid of `PackCard` components in "installed" variant. Each card shows: `Installed on <date>` formatted, `by <member name>` (or "by system" if `installedByMemberId` is null). |
| 478.8 | Implement uninstall precheck flow | 478B | 478.3 | Modify: `frontend/components/settings/pack-card.tsx` (or create a wrapper). For installed cards, on component mount (or on "Installed" tab activation), call `checkPackUninstallable(packId)` via SWR. If `canUninstall = false`, disable the "Uninstall" button and wrap it in a `Tooltip` showing `blockingReason`. If `canUninstall = true`, enable the button. Pattern: existing tooltip usage on disabled buttons in `components/settings/`. |
| 478.9 | Implement uninstall confirmation dialog | 478B | 478.8 | Modify: `frontend/components/settings/pack-card.tsx`. Clicking enabled "Uninstall" button shows Shadcn `AlertDialog` with: title "Uninstall Pack", description "This will remove <item_count> items created by this pack. Only allowed because none have been used or edited. Continue?", "Cancel" and "Uninstall" actions. Confirm calls `uninstallPackAction(packId)`. On success, revalidate the page (or call `router.refresh()`). Pattern: existing `AlertDialog` usage in `components/settings/`. |
| 478.10 | Create server action for uninstall | 478B | 478.2 | Modify: `frontend/app/(app)/org/[slug]/settings/packs/actions.ts`. Add `uninstallPackAction(packId: string)` -- calls `uninstallPack(packId)` from API client. Returns void on success. On 409, throws error with the blocking reason. |
| 478.11 | Implement empty states | 478B | 478.4, 478.7 | Modify: `page.tsx` or tab components. Available tab empty state: "No Kazi Packs available for your current profile. Toggle 'Show all packs' to browse everything." Installed tab empty state: "No packs installed yet. Browse the Available tab to add templates and workflow automations to your workspace." Use existing empty state patterns in the codebase. |
| 478.12 | Write frontend component tests | 478B | 478.3--478.11 | New file: `frontend/__tests__/settings/PacksPage.test.tsx`. 6 tests: (1) Available tab renders pack cards with name, version, badge, (2) "Show all packs" toggle re-renders with unfiltered list, (3) Install button calls `installPackAction` and transitions to "Installed" state, (4) Installed tab renders installed packs with date/member info, (5) Uninstall button disabled with tooltip when `canUninstall = false`, (6) Empty state text for each tab. Use `@testing-library/react`. Mock API client functions. Add `afterEach(() => cleanup())` for Radix UI component leak prevention. |

### Key Files

**Slice 478A -- Create:**
- `frontend/lib/api/packs.ts`
- `frontend/components/settings/pack-card.tsx`
- `frontend/app/(app)/org/[slug]/settings/packs/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/packs/actions.ts`

**Slice 478A -- Modify:**
- `frontend/components/settings/settings-nav-groups.ts` -- Add "Packs" nav item

**Slice 478A -- Read for context:**
- `frontend/lib/api/client.ts` -- API client base
- `frontend/lib/api/automations.ts` -- API client function pattern
- `frontend/app/(app)/org/[slug]/settings/features/page.tsx` -- Settings page pattern
- `frontend/components/settings/settings-sidebar.tsx` -- How nav groups render
- `frontend/components/settings/features-settings-form.tsx` -- Settings card layout reference

**Slice 478B -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/packs/page.tsx` -- Add Installed tab content + empty states
- `frontend/components/settings/pack-card.tsx` -- Add uninstall precheck + tooltip + AlertDialog
- `frontend/app/(app)/org/[slug]/settings/packs/actions.ts` -- Add uninstall action

**Slice 478B -- Create:**
- `frontend/__tests__/settings/PacksPage.test.tsx`

### Architecture Decisions

- **Server component for page, client component for cards**: The page fetches catalog data on the server. Pack cards need `onClick` handlers and SWR for uninstall checks, so they are `"use client"` components. The "Show all packs" toggle requires client-side state.
- **SWR for uninstall checks**: The precheck API call happens lazily when the Installed tab is active. SWR provides caching and automatic revalidation.
- **`adminOnly: true` on nav item**: This ensures only admin/owner roles see the Packs settings entry, matching the `TEAM_OVERSIGHT` capability requirement on the API.
- **AlertDialog for destructive action**: Uninstall is a hard delete. The confirmation dialog follows the existing pattern for destructive actions in the settings area.

---

## Epic 479: Integration Tests + E2E

**Goal**: Create a Playwright E2E test that exercises the full pack lifecycle through the browser: install a pack via the Settings UI, verify the installed content appears in the Templates page, edit a template, attempt uninstall (blocked), revert the edit, and successfully uninstall.

**References**: Architecture doc Sections 65.8.5 (Testing Strategy), 65.10 Slice 65G.

**Dependencies**: Epics 477 (backend deployed) and 478 (frontend deployed).

**Scope**: E2E

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **479A** | 479.1--479.3 | Playwright E2E test (`packs.spec.ts`): full lifecycle flow. Login as admin > navigate to Settings > Packs > verify Available tab shows packs > install a document template pack > verify card moves to Installed state > navigate to Templates page and verify new templates appear > navigate back to Packs > Installed tab > edit one template (change content) > attempt uninstall (verify button disabled, tooltip shows reason) > revert edit (delete the template or restore content) > uninstall succeeds > verify templates removed. E2E only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 479.1 | Create E2E test file | 479A | -- | New file: `frontend/e2e/tests/settings/packs.spec.ts`. Import existing E2E helpers for login and navigation. Set up test: login as admin/owner user, navigate to `/org/{slug}/settings/packs`. Pattern: `frontend/e2e/tests/automations/` or `frontend/e2e/tests/customers/` for test file structure, login flow, and navigation helpers. |
| 479.2 | Write install + verify content test | 479A | 479.1 | In `packs.spec.ts`, test "install pack and verify content": (1) verify Available tab shows at least one document template pack, (2) click "Install" on a pack, (3) verify the card transitions to "Installed" state (button changes to "Installed" or card moves to Installed tab), (4) navigate to Settings > Templates page, (5) verify new templates from the pack are listed (check for template names from the pack), (6) navigate back to Settings > Packs > Installed tab, (7) verify the pack appears in the Installed tab with correct item count. |
| 479.3 | Write edit + blocked uninstall + revert + uninstall test | 479A | 479.2 | In `packs.spec.ts`, test "uninstall blocked when template edited, succeeds after revert": (1) from Installed tab, note the pack's item count, (2) navigate to Templates, edit one template from the pack (change its name or content), (3) navigate back to Settings > Packs > Installed tab, (4) hover over the "Uninstall" button, verify it is disabled, (5) verify tooltip or message shows "templates have been edited", (6) navigate back to Templates, revert the edit (restore original name/content or delete the template), (7) navigate back to Packs > Installed tab, (8) click "Uninstall", (9) confirm in the AlertDialog, (10) verify the pack is removed from the Installed tab, (11) verify the templates are removed from the Templates page. |

### Key Files

**Slice 479A -- Create:**
- `frontend/e2e/tests/settings/packs.spec.ts`

**Slice 479A -- Read for context:**
- `frontend/e2e/tests/automations/` -- E2E test structure pattern
- `frontend/e2e/tests/customers/` -- Another E2E test reference
- `frontend/e2e/tests/documents/` -- Navigation + content verification pattern
- `frontend/e2e/tests/demo-recording.spec.ts` -- Full lifecycle test reference

### Architecture Decisions

- **Single E2E test file**: The pack lifecycle is a coherent flow. One spec file with 2 tests covers the happy path (install + verify) and the blocked/revert/success path. This avoids E2E test proliferation while covering the critical user journey.
- **Template edit as the uninstall gate trigger**: Editing a template's content changes its hash, which triggers the "templates have been edited" blocking reason. This is the most common real-world scenario and the easiest to test in the browser.
- **E2E runs against the full stack**: The test assumes backend + frontend are deployed and running. It uses the mock auth provider for login (consistent with existing E2E infrastructure).

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase65-packs-catalog-install-pipeline.md`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/settings/settings-nav-groups.ts`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`
